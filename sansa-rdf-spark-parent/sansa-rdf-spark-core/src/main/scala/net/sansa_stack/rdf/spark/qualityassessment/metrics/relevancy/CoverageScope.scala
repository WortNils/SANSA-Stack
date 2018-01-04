package net.sansa_stack.rdf.spark.qualityassessment.metrics.relevancy

import org.apache.spark.rdd.RDD
import org.apache.jena.graph.{ Triple, Node }
import net.sansa_stack.rdf.spark.utils.StatsPrefixes._

/**
 * This metric calculate the coverage of a dataset referring to the covered scope.
 * This covered scope is expressed as the number of 'instances' statements are made about.
 */
object CoverageScope {
  implicit class CoverageScopeFunctions(dataset: RDD[Triple]) extends Serializable {
    def assessCoverageScope() = {

      val triples = dataset.count().toDouble

      //?o a rdfs:Class UNION ?o a owl:Class
      val instances = dataset.filter(f => f.getPredicate.getLiteralLexicalForm.equals(RDFS_CLASS)).map(_.getObject).distinct()
        .union(dataset.filter(f => f.getPredicate.getLiteralLexicalForm.equals(OWL_CLASS)).map(_.getObject).distinct())
        .count().toDouble

      val value = if (triples > 0.0)
        instances / triples;
      else 0

      value
    }
  }
}