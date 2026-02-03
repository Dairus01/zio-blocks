package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * Scala 3 specific extensions for MigrationBuilder with macro-based selectors.
 */
trait MigrationBuilderMacros[A, B] {
  self: MigrationBuilder[A, B] =>

  /**
   * Add a field using a selector function.
   * 
   * Example: `.addField(_.age, SchemaExpr.Literal(30, Schema[Int]))`
   */
  inline def addField(inline selector: B => Any, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.addFieldImpl[A, B]('self, 'selector, 'default) }

  /**
   * Drop a field using a selector function.
   * 
   * Example: `.dropField(_.oldField)`
   */
  inline def dropField(inline selector: A => Any): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.dropFieldImpl[A, B]('self, 'selector) }

  /**
   * Drop a field using a selector function with a default for reverse.
   */
  inline def dropField(inline selector: A => Any, defaultForReverse: SchemaExpr[B, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.dropFieldWithDefaultImpl[A, B]('self, 'selector, 'defaultForReverse) }

  /**
   * Rename a field using selector functions.
   * 
   * Example: `.renameField(_.firstName, _.fullName)`
   */
  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.renameFieldImpl[A, B]('self, 'from, 'to) }

  /**
   * Transform a field using a selector function.
   * 
   * Example: `.transformField(_.age, transform)`
   */
  inline def transformField(inline selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.transformFieldImpl[A, B]('self, 'selector, 'transform) }

  /**
   * Make an optional field mandatory using a selector function.
   */
  inline def mandateField(inline selector: A => Any, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.mandateFieldImpl[A, B]('self, 'selector, 'default) }

  /**
   * Make a field optional using a selector function.
   */
  inline def optionalizeField(inline selector: A => Any): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B]('self, 'selector) }

  /**
   * Change the type of a field using a selector function.
   */
  inline def changeFieldType(inline selector: A => Any, converter: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B]('self, 'selector, 'converter) }

  /**
   * Transform collection elements using a selector function.
   */
  inline def transformElements(inline selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.transformElementsImpl[A, B]('self, 'selector, 'transform) }

  /**
   * Transform map keys using a selector function.
   */
  inline def transformKeys(inline selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.transformKeysImpl[A, B]('self, 'selector, 'transform) }

  /**
   * Transform map values using a selector function.
   */
  inline def transformMapValues(inline selector: A => Any, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] = 
    ${ MigrationBuilderMacrosImpl.transformMapValuesImpl[A, B]('self, 'selector, 'transform) }
}

object MigrationBuilderMacrosImpl {
  def addFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[B => Any],
    default: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[B](selector)
    '{ $self.addField($optic, $default) }
  }

  def dropFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.dropField($optic) }
  }

  def dropFieldWithDefaultImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[B, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.dropField($optic, $defaultForReverse) }
  }

  def renameFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val fromOptic = SelectorMacros.extractOpticImpl[A](from)
    val toOptic = SelectorMacros.extractOpticImpl[B](to)
    
    '{
      val fromPath = $fromOptic.nodes
      val toPath = $toOptic.nodes
      val fromName = fromPath.last match {
        case DynamicOptic.Node.Field(name) => name
        case _ => throw new IllegalArgumentException("Expected field selector")
      }
      val toName = toPath.last match {
        case DynamicOptic.Node.Field(name) => name
        case _ => throw new IllegalArgumentException("Expected field selector")
      }
      $self.renameField(fromName, toName)
    }
  }

  def transformFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    transform: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.transformField($optic, $transform) }
  }

  def mandateFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    default: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.mandateField($optic, $default) }
  }

  def optionalizeFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.optionalizeField($optic) }
  }

  def changeFieldTypeImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    converter: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.changeFieldType($optic, $converter) }
  }

  def transformElementsImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    transform: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.transformElements($optic, $transform) }
  }

  def transformKeysImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    transform: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.transformKeys($optic, $transform) }
  }

  def transformMapValuesImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    transform: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = SelectorMacros.extractOpticImpl[A](selector)
    '{ $self.transformValues($optic, $transform) }
  }
}
