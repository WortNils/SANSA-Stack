package net.sansa_stack.ml.spark.evaluation.models

import net.sansa_stack.ml.spark.similarity.similarityEstimationModels._
import net.sansa_stack.ml.spark.utils.{FeatureExtractorModel, SimilarityExperimentMetaGraphFactory}
import net.sansa_stack.rdf.spark.io._
import net.sansa_stack.ml.spark.similarity.similarityEstimationModels.GenericSimilarityEstimatorModel
import org.apache.jena.graph
import org.apache.jena.riot.Lang
import org.apache.jena.sys.JenaSystem
import org.apache.spark.ml.feature.{CountVectorizer, CountVectorizerModel}
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.apache.spark.sql.functions._
import net.sansa_stack.ml.spark.evaluation.utils.FeatureExtractorEval
import org.apache.spark.sql.Row

class ResnikModel extends GenericSimilarityModel {
  val spark = SparkSession.builder.getOrCreate()
  private val _availableModes = Array("res")
  private var _mode: String = "res"
  private var _depth: Int = 1
  private var _outputCol: String = "extractedFeatures"

  override val estimatorName: String = "ResnikSimilarityEstimator"
  override val estimatorMeasureType: String = "similarity"

  override def transform(dataset: Dataset[_], target: DataFrame): DataFrame = {
    val t0 = System.nanoTime()
    import spark.implicits._
    val featureExtractorModel = new FeatureExtractorEval()
      .setMode("par").setDepth(_depth)
    val parents = featureExtractorModel
      .transform(dataset, target)

    val frame = target.withColumn("Resnik", lit(0)).withColumn("ResnikTime", lit(0))

    frame.map(row: Row =>
      val a = parents.filter("uri" == row(0)).drop("uri").toDF
      val b = parents.filter("uri" == row(1)).drop("uri").toDF
      val common: DataFrame = a.intersect(b)

      featureExtractorModel.setMode("ic")
      val informationContent = featureExtractorModel
      .transform(dataset, common)
      val resnik = informationContent.sort(desc(columnName = "extractedFeatures")).head()
      val t1 = System.nanoTime()
      val t_diff = t1-t0
      row("Resnik") = resnik
      row("ResnikTime") = t_diff
      row
    ).toDF
  }
}