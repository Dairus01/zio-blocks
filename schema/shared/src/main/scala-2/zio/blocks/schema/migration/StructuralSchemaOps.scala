package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import zio.blocks.schema.Schema

/**
 * Scala 2 extensions for creating structural schemas.
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
  def structural[T]: Schema[T] = macro StructuralSchemaOpsImpl.structuralImpl[T]
}

object StructuralSchemaOpsImpl {
  def structuralImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Schema[T]] = {
    import c.universe._
    
    // For structural types, Schema.derived should handle them
    c.Expr[Schema[T]](
      q"_root_.zio.blocks.schema.Schema.derived[${weakTypeOf[T]}]"
    )
  }
}

object StructuralSchemaOps extends StructuralSchemaOps
