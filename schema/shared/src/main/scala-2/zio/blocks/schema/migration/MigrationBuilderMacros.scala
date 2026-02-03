package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * Scala 2 specific extensions for MigrationBuilder with macro-based selectors.
 */
trait MigrationBuilderMacros[A, B] {
  self: MigrationBuilder[A, B] =>

  /**
   * Add a field using a selector function.
   * 
   * Example: `.addField(_.age, SchemaExpr.Literal(30, Schema[Int]))`
   */
  def addField(selector: B => Any, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.addFieldImpl[A, B]

  /**
   * Drop a field using a selector function.
   * 
   * Example: `.dropField(_.oldField)`
   */
  def dropField(selector: A => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.dropFieldImpl[A, B]

  /**
   * Drop a field using a selector function with a default for reverse.
   */
  def dropField(selector: A => Any, defaultForReverse: SchemaExpr[B, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.dropFieldWithDefaultImpl[A, B]

  /**
   * Rename a field using selector functions.
   * 
   * Example: `.renameField(_.firstName, _.fullName)`
   */
  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.renameFieldImpl[A, B]

  /**
   * Transform a field using a selector function.
   * 
   * Example: `.transformField(_.age, transform)`
   */
  def transformField(selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.transformFieldImpl[A, B]

  /**
   * Make an optional field mandatory using a selector function.
   */
  def mandateField(selector: A => Any, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.mandateFieldImpl[A, B]

  /**
   * Make a field optional using a selector function.
   */
  def optionalizeField(selector: A => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B]

  /**
   * Change the type of a field using a selector function.
   */
  def changeFieldType(selector: A => Any, converter: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B]

  /**
   * Transform collection elements using a selector function.
   */
  def transformElements(selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.transformElementsImpl[A, B]

  /**
   * Transform map keys using a selector function.
   */
  def transformKeys(selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.transformKeysImpl[A, B]

  /**
   * Transform map values using a selector function.
   */
  def transformMapValues(selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = macro MigrationBuilderMacrosImpl.transformMapValuesImpl[A, B]
}

object MigrationBuilderMacrosImpl {
  import scala.reflect.macros.blackbox

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[B](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.addField($optic, $default)"
    )
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.dropField($optic)"
    )
  }

  def dropFieldWithDefaultImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[B, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.dropField($optic, $defaultForReverse)"
    )
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromOptic = SelectorMacros.extractOpticImpl[A](c)(from)
    val toOptic = SelectorMacros.extractOpticImpl[B](c)(to)
    
    // Extract field names from optics
    c.Expr[MigrationBuilder[A, B]](
      q"""
        val fromPath = $fromOptic.nodes
        val toPath = $toOptic.nodes
        val fromName = fromPath.last match {
          case _root_.zio.blocks.schema.DynamicOptic.Node.Field(name) => name
          case _ => throw new IllegalArgumentException("Expected field selector")
        }
        val toName = toPath.last match {
          case _root_.zio.blocks.schema.DynamicOptic.Node.Field(name) => name
          case _ => throw new IllegalArgumentException("Expected field selector")
        }
        ${c.prefix}.renameField(fromName, toName)
      """
    )
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.transformField($optic, $transform)"
    )
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    default: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.mandateField($optic, $default)"
    )
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.optionalizeField($optic)"
    )
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    converter: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.changeFieldType($optic, $converter)"
    )
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.transformElements($optic, $transform)"
    )
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.transformKeys($optic, $transform)"
    )
  }

  def transformMapValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[A, ?]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacros.extractOpticImpl[A](c)(selector)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.transformValues($optic, $transform)"
    )
  }
}
