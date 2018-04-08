package xmlpro
import org.apache.hadoop.streaming.StreamInputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.conf.Configuration;
import org.apache.spark.rdd.RDD
import org.apache.hadoop.io.Text
import org.apache.hadoop.io.IntWritable
import org.apache.hadoop.streaming.StreamXmlRecordReader
import org.apache.hadoop.mapred.JobConf
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.types.{ LongType, DoubleType, StringType, IntegerType, StructField, StructType }
import org.apache.spark.sql.Row
import org.apache.spark.sql
import org.apache.spark.SparkContext._
import org.apache.spark.sql._
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import Array._
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.io;
import org.apache.commons.lang3.StringUtils;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.ModelFactory
import org.apache.spark.rdd.RDD
import java.io.ByteArrayInputStream;
import java.util.Scanner;
import java.util._
// ML : 2.11
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Row
import java.util.Arrays.asList
import collection.JavaConversions;
import collection.Seq;
import org.apache.spark.ml.linalg.SQLDataTypes.VectorType
import org.apache.spark.mllib.classification.{ LogisticRegressionModel, LogisticRegressionWithLBFGS }
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorIndexer
import org.apache.spark.ml.regression.{ RandomForestRegressionModel, RandomForestRegressor }
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.classification.BinaryLogisticRegressionSummary
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.feature.StringIndexer

import org.apache.spark.ml.linalg.DenseVector
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorIndexer
import org.apache.spark.ml.regression.{ RandomForestRegressionModel, RandomForestRegressor }
import org.apache.spark.broadcast
import java.lang.NumberFormatException
import org.apache.spark.sql._
import java.util.Objects
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser
import java.util.List;
//import scala.util.parsing.json._
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind._
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.PairRDDFunctions
import org.apache.spark.Partitioner._
import org.apache.spark.RangePartitioner
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{ col, udf }

import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.linalg.Vector

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.apache.spark.sql.functions.col

object Main extends App {

  val start = new VandalismDetection()
  val sparkConf = new SparkConf().setMaster("local[*]").setAppName("VandalismDetector")
  val sc = new SparkContext(sparkConf)

  start.Triger(sc)

}



