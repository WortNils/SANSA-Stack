package net.sansa_stack.ml.spark.evaluation

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import net.sansa_stack.ml.spark.evaluation.models._
import net.sansa_stack.ml.spark.evaluation.utils.{FeatureExtractorEval, SimilaritySampler}
import net.sansa_stack.rdf.common.io.riot.error.{ErrorParseMode, WarningParseMode}
import net.sansa_stack.rdf.spark.io._
import net.sansa_stack.rdf.spark.model.TripleOperations
import org.apache.jena.graph
import org.apache.jena.riot.Lang
import org.apache.jena.sys.JenaSystem
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.feature.{CountVectorizer, CountVectorizerModel}
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.scalactic.TolerantNumerics
import org.scalatest.FunSuite

class EvaluationUnitTest extends FunSuite with DataFrameSuiteBase {

  // define inputpath if it is not parameter
  private val inputPath = "./sansa-ml/sansa-ml-spark/src/test/resources/similarity/movie.ttl"

  // var triplesDf: DataFrame = spark.read.rdf(Lang.NTRIPLES)(inputPath).cache()

  // for value comparison we want to allow some minor differences in number comparison
  val epsilon = 1e-4f

  implicit val doubleEq = TolerantNumerics.tolerantDoubleEquality(epsilon)

  override def beforeAll(): Unit = {
    super.beforeAll()

    val spark = SparkSession.builder
      .appName(s"Semantic Similarity Evaluator Tester")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    JenaSystem.init()
  }

  // TODO: add more tests for each part
  test("Test DistSim Modules") {

    // read in data as DataFrame
    println("Read in RDF Data as DataFrame")
    /*
    val triplesDf = NTripleReader
      .load(
        spark,
        inputPath,
        stopOnBadTerm = ErrorParseMode.SKIP,
        stopOnWarnings = WarningParseMode.IGNORE)
      .toDF().cache()
    */
    val triplesDf = spark.rdf(Lang.TTL)(inputPath).toDF().cache()

    triplesDf.show(false)

    // test sampler
    println("Test Sampler")
    val sampleModesToTest = List("cross", "limit", "rand")

    for (mode <- sampleModesToTest) {
      val sampler = new SimilaritySampler()
        .setMode(mode)
        .setLimit(10)
        .setSeed(10)
      val sampledDataFrame = sampler
        .transform(triplesDf)

      println(" Test Sampling mode: " + mode)

      sampledDataFrame.show(false)
      // TODO: add tests for the sampling with a List of pairs
    }
    // TODO: add tests for literalremoval

    val sample = new SimilaritySampler()
      .setMode("cross")
    val target = sample.transform(triplesDf)

    val featureTarget = target.drop("entityA")
      .withColumnRenamed("entityB", "uri")
      .union(target.drop("entityB")
        .withColumnRenamed("entityA", "uri"))
      .distinct()

    // test feature extractor
    println("Test Feature Extractor")
    val modesToTest = List("par", "par2", "ic", "root", "feat", "path")

    for (mode <- modesToTest) {
      val featureExtractor = new FeatureExtractorEval()
        .setMode(mode)
        .setDepth(5)

      if (mode == "path") {
        featureExtractor.setTarget(target)
      }
      else {
        featureExtractor.setTarget(featureTarget)
      }

      val extractedFeaturesDataFrame = featureExtractor
        .transform(triplesDf)

      println("  Test Feature Extraction mode: " + mode)

      extractedFeaturesDataFrame.show(false)
      // TODO: test features of m1 and m2
    }

    val modelNames = List("ResnikModel", "WuAndPalmerModelJoin", "WuAndPalmerModelBreadth", "TverskyModel")

    // evaluate all models
    for (modelName <- modelNames) {
      println("Test model: " + modelName)

      // model setup
      val result = modelName match {
        case "ResnikModel" => new ResnikModel()
          .setTarget(target)
          .setDepth(5)
          .transform(triplesDf)
          .withColumnRenamed("Resnik", "distCol")
        case "WuAndPalmerModelJoin" => new WuAndPalmerModel()
          .setTarget(target)
          .setDepth(5)
          .setMode("join")
          .transform(triplesDf)
          .withColumnRenamed("WuAndPalmer", "distCol")
        case "WuAndPalmerModelBreadth" => new WuAndPalmerModel()
          .setTarget(target)
          .setDepth(5)
          .setMode("breadth")
          .transform(triplesDf)
          .withColumnRenamed("WuAndPalmer", "distCol")
        case "TverskyModel" => new TverskyModel()
          .setTarget(target)
          .setAlpha(1.0)
          .setBeta(1.0)
          .transform(triplesDf)
          .withColumnRenamed("Tversky", "distCol")
      }

      result.show(false)

      val valueP1P2 = result.filter((result("entityA") === "urn:p1" && result("entityB") === "urn:p2") || result("entityB") === "urn:p1" && result("entityA") === "urn:p2")
        .select("distCol").rdd.map(r => r.getAs[Double]("distCol")).collect().take(1)(0)

      val valueM1M2 = result.filter((result("entityA") === "urn:m1" && result("entityB") === "urn:m2") || result("entityB") === "urn:m1" && result("entityA") === "urn:m2")
        .select("distCol").rdd.map(r => r.getAs[Double]("distCol")).collect().take(1)(0)

      if (modelName == "ResnikModel") {
        val desiredValueP = 0.166666666666666
        assert(valueP1P2 === desiredValueP)
        val desiredValueM = 0.166666666666666
        assert(valueM1M2 === desiredValueM)
      }
      else if (modelName == "WuAndPalmerModelJoin") {
        val desiredValue = 0.25
        assert(valueP1P2 === desiredValue)
        val desiredValueM = 0.5
        assert(valueM1M2 === desiredValueM)
      }
      else if (modelName == "WuAndPalmerModelBreadth") {
        val desiredValue = 0.25
        assert(valueP1P2 === desiredValue)
        val desiredValueM = 0.5
        assert(valueM1M2 === desiredValueM)
      }
      else if (modelName == "TverskyModel") {
        val desiredValue = 0
        assert(valueP1P2 === desiredValue)
        val desiredValueM = 0.125
        assert(valueM1M2 === desiredValueM)
      }
    }
  }
}
