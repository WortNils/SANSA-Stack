package net.sansa_stack.rdf.common.io.hadoop

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, SequenceInputStream}
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.{Matcher, Pattern}

import io.reactivex.Flowable
import io.reactivex.functions.Predicate
import org.aksw.jena_sparql_api.common.DefaultPrefixes
import org.aksw.jena_sparql_api.io.binseach._
import org.aksw.jena_sparql_api.rx.RDFDataMgrRx
import org.apache.commons.io.IOUtils
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.mapreduce.lib.input.FileSplit
import org.apache.hadoop.mapreduce.{InputSplit, RecordReader, TaskAttemptContext}
import org.apache.jena.ext.com.google.common.primitives.Ints
import org.apache.jena.query.{Dataset, DatasetFactory}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat}

import scala.collection.JavaConverters._


/**
 * A record reader for Trig RDF files.
 *
 * @author Lorenz Buehmann
 * @author Claus Stadler
 */
class TrigRecordReader(prefixMapping: Model = ModelFactory.createDefaultModel().setNsPrefixes(DefaultPrefixes.prefixes))
  extends RecordReader[LongWritable, Dataset] {

  private val trigFwdPattern: Pattern = Pattern.compile("@?base|@?prefix|(graph)?\\s*(<[^>]*>|_:[^-\\s]+)\\s*\\{", Pattern.CASE_INSENSITIVE)
  private val trigBwdPattern: Pattern = Pattern.compile("esab@?|xiferp@?|\\{\\s*(>[^<]*<|[^-\\s]+:_)\\s*(hparg)?", Pattern.CASE_INSENSITIVE)

  // private var start, end, position = 0L

  private val currentKey = new AtomicLong
  private var currentValue: Dataset = DatasetFactory.create()

  private var datasetFlow: util.Iterator[Dataset] = _


  /**
    * Uses the matcher to find candidate probing positions, and returns the first positoin
    * where probing succeeds.
    * Matching ranges are part of the matcher configuration
    *
    * @param rawSeekable
    * @param m
    * @param isFwd
    * @param prober
    * @return
    */
  def findPosition(rawSeekable: Seekable, m: Matcher, isFwd: Boolean, prober: Seekable => Boolean): Long = {

    val seekable = rawSeekable.cloneObject
    val absMatcherStartPos = seekable.getPos

    while (m.find) {
      val start = m.start
      val end = m.end
      // The matcher yields absolute byte positions from the beginning of the byte sequence
      val matchPos = if (isFwd) {
        start
      } else {
        -end + 1
      }
      val absPos = (absMatcherStartPos + matchPos).asInstanceOf[Int]
      // Artificially create errors
      // absPos += 5;
      seekable.setPos(absPos)
      val probeSeek = seekable.cloneObject

      // val by = seekable.get()
      // println("by " + by)
      val probeResult = prober.apply(probeSeek)
      System.err.println(s"Probe result for matching at pos $absPos with fwd=$isFwd: $probeResult")

      if(probeResult) {
        return absPos
      }
    }

    return -1L
  }


  override def initialize(inputSplit: InputSplit, context: TaskAttemptContext): Unit = {
    val tmp = createDatasetFlow(inputSplit, context)

    datasetFlow = tmp.blockingIterable().iterator()
  }


  def createDatasetFlow(inputSplit: InputSplit, context: TaskAttemptContext): Flowable[Dataset] = {
    val maxRecordLength = 200 // 10 * 1024
    val probeRecordCount = 2

    val twiceMaxRecordLengthMinusOne = (2 * maxRecordLength - 1)

    // we have to prepend prefixes to help the parser as there is no other way to make it aware of those
    val baos = new ByteArrayOutputStream()
    RDFDataMgr.write(baos, prefixMapping, RDFFormat.TURTLE_PRETTY)
    val prefixBytes = baos.toByteArray

    // Clones the provided seekable!
    val effectiveInputStreamSupp: Seekable => InputStream = seekable => {
      val r = new SequenceInputStream(
        new ByteArrayInputStream(prefixBytes),
        Channels.newInputStream(seekable.cloneObject))
      r
    }

    val parser: Seekable => Flowable[Dataset] = seekable => {
      // TODO Close the cloned seekable
      val task = new java.util.concurrent.Callable[InputStream]() {
        def call(): InputStream = effectiveInputStreamSupp.apply(seekable)
      }

      val r = RDFDataMgrRx.createFlowableDatasets(task, Lang.TRIG, null)
      r
    }

    val prober: Seekable => Boolean = seekable => {
      val quadCount = parser(seekable)
        .limit(probeRecordCount)
        .count
        .onErrorReturnItem(-1L)
        .blockingGet() > 0
      quadCount
    }

    // Check whether the prior split ended exactly at its boundary
    // For that, we check for a start position between [splitStart-2*mrl, splitStart-mrl]
    // If there is a position that can parse to the end, then all records of the prior
    // record will have been emitted
    // Otherwise, we search backwards from the offset of the current chunk
    // until we find a position from which probing over the chunk boundary works

    // split position in data (start one byte earlier to detect if
    // the split starts in the middle of a previous record)
    val split = inputSplit.asInstanceOf[FileSplit]
    // val start = 0L.max(split.getStart - 1)
    // val end = start + split.getLength
    val splitStart = split.getStart
    val splitLength = split.getLength
    val splitEnd = splitStart + splitLength

    // Block length is the maximum amound of data we need for processing of
    // an input split w.r.t. records crossing split boundaries
    // and extends over the start of the designated split region
    val blockStart = Math.max(splitStart - twiceMaxRecordLengthMinusOne, 0L)
    val blockLength = splitEnd - blockStart

    System.err.println("Processing split " + blockStart + " <--| " + splitStart + " - " + splitEnd)

    // open a stream to the data, pointing to the start of the split
    val stream = split.getPath.getFileSystem(context.getConfiguration)
      .open(split.getPath)


    // Note we could init the buffer from 0 to blocklength,
    // with 0 corresponding to blockstart
    // but then we would have to do more relative positioning
    val bufferSize = splitEnd // blockLength // inputSplit.getLength.toInt
    val arr = new Array[Byte](Ints.checkedCast(bufferSize))
    stream.readFully(0, arr)
    val buffer = ByteBuffer.wrap(arr)
    // buffer.position(split.getStart)
    // buffer.limit(split.getStart + split.getLength)

    val pageManager = new PageManagerForByteBuffer(buffer)
    val nav = new PageNavigator(pageManager)

    // Initially position at splitStart within the block (i.e. the byte buffer)
    val initNavPos = splitStart // splitStart - blockStart
    nav.setPos(initNavPos)

    // Check the prior chunk
    var priorRecordEndsOnSplitBoundary = false;

    // FIXME Creating splits has to ensure splits don't end on block/chunk boundaries
    // TODO Clarify terminology once more
    if (blockStart == 0) {
      priorRecordEndsOnSplitBoundary = true
    }


    var probeSuccessPos = 0L
    if (!priorRecordEndsOnSplitBoundary) {
      // Set up absolute positions
      val absProbeRegionStart = blockStart // Math.min(splitStart - twiceMaxRecordLengthMinusOne, 0L)
      val absProbeRegionEnd = Math.max(splitStart - maxRecordLength, 0L)
      // val absChunkEnd = splitStart
      val relProbeRegionStart = splitStart - absProbeRegionStart
      if (relProbeRegionStart < twiceMaxRecordLengthMinusOne && absProbeRegionStart != 0) {
        System.err.println("WARNING: Insufficient preceding bytes before split start")
      }

      // Set up the matcher using relative positions
      val relProbeRegionEnd = Ints.checkedCast(absProbeRegionEnd - absProbeRegionStart)

      val seekable = nav.clone
      seekable.setPos(absProbeRegionStart)
      seekable.limitNext(relProbeRegionEnd)
      val charSequence = new CharSequenceFromSeekable(seekable)
      val fwdMatcher = trigFwdPattern.matcher(charSequence)
      fwdMatcher.region(0, relProbeRegionEnd)

      probeSuccessPos = findPosition(seekable, fwdMatcher, true, prober)

      priorRecordEndsOnSplitBoundary = probeSuccessPos >= 0
    }

    if (priorRecordEndsOnSplitBoundary) {
      /* we start at position 0 of the split */
      /* TODO We should still probe for an offset between
        0 and maxRecordLength for robustness
       */
      // TODO If the split is too small, we may not even get a single record
    }
    else {
      // Reverse search until probing into our chunk succeeds

      val absProbeRegionStart = splitStart
      val absProbeRegionEnd = Math.max(splitStart - (maxRecordLength - 1), 0L)
      // val chunkEnd = splitEnd

      // Set up the matcher using relative positions
      val relProbeRegionEnd = Ints.checkedCast(absProbeRegionStart - absProbeRegionEnd)

      val seekable = nav.clone
      seekable.setPos(absProbeRegionStart)
      seekable.limitNext(splitLength)
      val reverseCharSequence = new ReverseCharSequenceFromSeekable(seekable.clone)
      val bwdMatcher = trigBwdPattern.matcher(reverseCharSequence)
      bwdMatcher.region(0, relProbeRegionEnd)

      probeSuccessPos = findPosition(seekable, bwdMatcher, false, prober)

      if (probeSuccessPos < 0) {
        throw new RuntimeException("No suitable start found")
      }

      // Not sure whether or why we need +1 here
      // probeSuccessPos = probeSuccessPos + 1
      // nav.setPos(probeSuccessPos)
    }

    val parseLength = splitEnd - probeSuccessPos
    nav.setPos(probeSuccessPos)
    nav.limitNext(parseLength)

    /*
    val str = IOUtils.toString(effectiveInputStreamSupp.apply(nav))
    System.err.println("Parser base data: "
      + str
      + "\nEND OF PARSER BASE DATA")
    */

    /*
    System.err.println("Parser base data 2: "
      + IOUtils.toString(effectiveInputStreamSupp.apply(nav))
      + "\nEND OF PARSER BASE DATA 2")

    if (probeSuccessPos > 0) {
      val ds = DatasetFactory.create
      RDFDataMgr.read(
        ds,
        new ByteArrayInputStream(str.getBytes()),
        Lang.TRIG)

      System.err.println("Got ds: " + ds.asDatasetGraph().size())
    }
    */

    val baseResult: Flowable[Dataset] = parser(nav)


    val pred = new Predicate[Dataset] {
      override def test(t: Dataset): Boolean = {
        // System.err.println("Dataset filter saw graphs: " + t.listNames().asScala.toList)
        !t.isEmpty
      }
    }

    val cnt = baseResult
      .onErrorReturnItem(DatasetFactory.create())
      .filter(pred)
      .count()
      .blockingGet()

    System.err.println("Got " + cnt + " datasets")

    val result = baseResult
          .onErrorReturnItem(DatasetFactory.create())
          .filter(pred)

    result
    /*
        // Lets start from this position
        nav.setPos(0)
        val absMatcherStartPos = nav.getPos

        // The charSequence has a clone of nav so it has independent relative positioning


        var matchCount = 0
        while (m.find && matchCount < 10) {
          val start = m.start
          val end = m.end
          // The matcher yields absolute byte positions from the beginning of the byte sequence
          val matchPos = if (isFwd) start else -end + 1
          val absPos = (absMatcherStartPos + matchPos).asInstanceOf[Int]
          // Artificially create errors
          // absPos += 5;
          nav.setPos(absPos)
          println(s"Attempting pos: $absPos")
          val navClone = nav.clone


          // if success, parse to Dataset
          if (quadCount >= 0) {
            matchCount += 1
            println(s"Candidate start pos $absPos yield $quadCount / $maxQuadCount quads")

            datasetFlow = RDFDataMgrRx.createFlowableDatasets(task, Lang.TRIG, null)
               // .doOnError(t => println(t))
              .blockingIterable()
              .iterator()

            return
          }
        }

    */
  }

  override def nextKeyValue(): Boolean = {
    if (datasetFlow == null || !datasetFlow.hasNext) {
      System.err.println("nextKeyValue: No more datasets")
      false
    }
    else {
      currentValue = datasetFlow.next()
      System.err.println("nextKeyValue: Got dataset value: " + currentValue.listNames().asScala.toList)
      RDFDataMgr.write(System.err, currentValue, RDFFormat.TRIG_PRETTY)
      System.err.println("nextKeyValue: Done getting dataset value")
      currentKey.incrementAndGet
      currentValue != null
    }
  }

  override def getCurrentKey: LongWritable = if (currentValue == null) null else new LongWritable(currentKey.get)

  override def getCurrentValue: Dataset = currentValue

  override def getProgress: Float = 0

  override def close(): Unit = {
    if (datasetFlow != null) {
      datasetFlow = null
    }
  }
}
