package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.{Schema, ToStructural}

/**
 * Scala 3 extensions for creating structural schemas.
 */
trait StructuralSchemaOps {
  /**
   * Create a structural schema from a structural type.
   * 
   * Example:
   * {{{
   * type PersonV0 = { def firstName: String; def lastName: String }
   * val schema = StructuralSchemaOps.structural[PersonV0]
   * }}}
   */
  inline def structural[T]: Schema[T] = ${ StructuralSchemaOpsImpl.structuralImpl[T] }
}

object StructuralSchemaOpsImpl {
  def structuralImpl[T: Type](using Quotes): Expr[Schema[T]] = {
    // For structural types, Schema.derived should handle them
    '{ Schema.derived[T] }
  }
}

object StructuralSchemaOps extends StructuralSchemaOps
