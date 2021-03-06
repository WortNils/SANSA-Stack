package net.sansa_stack.query.spark.ontop

import it.unibz.inf.ontop.dbschema.MetadataProvider
import it.unibz.inf.ontop.dbschema.impl.OfflineMetadataProviderBuilder
import it.unibz.inf.ontop.injection.OntopModelConfiguration
import net.sansa_stack.rdf.common.partition.core.{RdfPartitionStateDefault, RdfPartitioner}
import net.sansa_stack.rdf.common.partition.schema.{SchemaStringDate, SchemaStringDouble, SchemaStringStringType}

import scala.reflect.runtime.universe.typeOf
/**
 * @author Lorenz Buehmann
 */
class MetadataProviderH2(defaultConfiguration: OntopModelConfiguration) {

  val logger = com.typesafe.scalalogging.Logger("MetadataProviderH2")


  def generate(partitioner: RdfPartitioner[RdfPartitionStateDefault], partitions: Seq[RdfPartitionStateDefault], blankNodeStrategy: BlankNodeStrategy.Value): MetadataProvider = {
    val builder = new OfflineMetadataProviderBuilder(defaultConfiguration.getTypeFactory)
    partitions.foreach(p => generate(partitioner, p, blankNodeStrategy, builder))
    builder.build()
  }

  private def generate(partitioner: RdfPartitioner[RdfPartitionStateDefault], p: RdfPartitionStateDefault, blankNodeStrategy: BlankNodeStrategy.Value,
                       builder: OfflineMetadataProviderBuilder): Unit = {
    val schema = partitioner.determineLayout(p).schema

    val name = SQLUtils.createTableName(p, blankNodeStrategy)
    p match {
      case RdfPartitionStateDefault(subjectType, predicate, objectType, datatype, langTagPresent, lang) =>
        objectType match {
          case 1 =>
            builder.createDatabaseRelation(SQLUtils.escapeTablename(name),
              "s", builder.getDBTypeFactory.getDBStringType, false,
              "o", builder.getDBTypeFactory.getDBStringType, false)
          case 2 => if (langTagPresent) {
            builder.createDatabaseRelation(SQLUtils.escapeTablename(name),
              "s", builder.getDBTypeFactory.getDBStringType, false,
              "o", builder.getDBTypeFactory.getDBStringType, false,
              "l", builder.getDBTypeFactory.getDBStringType, false)
          } else {
            if (schema == typeOf[SchemaStringStringType]) {
              builder.createDatabaseRelation(SQLUtils.escapeTablename(name),
                "s", builder.getDBTypeFactory.getDBStringType, false,
                "o", builder.getDBTypeFactory.getDBStringType, false,
                "t", builder.getDBTypeFactory.getDBStringType, false)
            } else {
              schema match {
                case t if t =:= typeOf[SchemaStringDouble] => builder.createDatabaseRelation(SQLUtils.escapeTablename(name),
                  "s", builder.getDBTypeFactory.getDBStringType, false,
                  "o", builder.getDBTypeFactory.getDBDoubleType, false)
                case t if t =:= typeOf[SchemaStringDouble] => builder.createDatabaseRelation(SQLUtils.escapeTablename(name),
                  "s", builder.getDBTypeFactory.getDBStringType, false,
                  "o", builder.getDBTypeFactory.getDBDecimalType, false)
                case t if t =:= typeOf[SchemaStringDate] => builder.createDatabaseRelation(SQLUtils.escapeTablename(name),
                  "s", builder.getDBTypeFactory.getDBStringType, false,
                  "o", builder.getDBTypeFactory.getDBDateType, false)
                case _ => logger.error(s"Error: couldn't create Spark table for property $predicate with schema ${schema}")
              }
            }
          }
          case _ => logger.error("TODO: bnode Spark SQL table for Ontop mappings")
        }
      case _ => logger.error("wrong partition type")
    }

  }

}
