package net.sansa_stack.ml.spark.utils

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import net.sansa_stack.ml.spark.utils.FeatureExtractingSparqlGenerator.autoPrepo
import net.sansa_stack.rdf.spark.io._
import org.apache.jena.graph.Node
import org.apache.jena.riot.Lang
import org.apache.jena.sys.JenaSystem
import org.apache.spark.sql.{Encoders, SparkSession}
import org.scalatest.FunSuite

class FeatureExtractingSparqlGeneratorTest extends FunSuite with DataFrameSuiteBase{

  override def beforeAll(): Unit = {
    super.beforeAll()

    JenaSystem.init();
  }

    /**
   * tests small creation of sparwl query and tests for created projection variables
   */
  test("Test auto SPARQL generation based on sample file") {

    val inputFilePath: String = this.getClass.getClassLoader.getResource("utils/test.ttl").getPath
    println(inputFilePath)
    val seedVarName = "?seed"
    val whereClauseForSeed = "?seed a <http://dig.isi.edu/Person>"
    val maxUp: Int = 5
    val maxDown: Int = 5
    val seedNumber: Int = 0
    val seedNumberAsRatio: Double = 1.0

    // setup spark session
    val spark = SparkSession.builder
      .appName(s"tryout sparql query transformer")
      .master("local[*]")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.crossJoin.enabled", true)
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    implicit val nodeTupleEncoder = Encoders.kryo(classOf[(Node, Node, Node)])

    // first mini file:
    val df = spark.read.rdf(Lang.TURTLE)(inputFilePath)

    val (totalSparqlQuery: String, var_names: List[String]) = autoPrepo(
      df = df,
      seedVarName = seedVarName,
      seedWhereClause = whereClauseForSeed,
      maxUp = maxUp,
      maxDown = maxDown,
      numberSeeds = seedNumber,
      ratioNumberSeeds = seedNumberAsRatio
    )

    val assumedProjectionVars = "?seed__down_age ?seed__down_name ?seed__down_hasSpouse__down_age ?seed__down_hasParent__down_age ?seed__down_hasSpouse__down_name ?seed__down_hasParent__down_name".split(" ")
    assert(assumedProjectionVars.toSet.diff(var_names.toSet).size == 0)
    spark.stop()
  }
}
