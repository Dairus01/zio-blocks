package zio.blocks.schema.migration

import scala.reflect.macros.blackbox
import zio.blocks.schema.DynamicOptic

/**
 * Macros for extracting DynamicOptic from selector functions.
 * 
 * Supports:
 * - Simple field access: _.field
 * - Nested field access: _.address.street
 * - Collection traversal: _.items.each (not yet implemented)
 * - Case selection: _.when[CaseName] (not yet implemented)
 */
private[migration] object SelectorMacros {

  /**
   * Extract a DynamicOptic from a selector function of the form `_.field` or `_.field.nested`.
   */
  def extractOpticImpl[A: c.WeakTypeTag](c: blackbox.Context)(selector: c.Expr[A => Any]): c.Expr[DynamicOptic] = {
    import c.universe._

    val path = extractPath(c)(selector.tree)
    buildOpticExpr(c)(path)
  }

  /**
   * Extract a field path from a selector function tree.
   */
  private def extractPath(c: blackbox.Context)(tree: c.Tree): List[String] = {
    import c.universe._

    def extractFromBody(body: Tree, paramName: TermName): List[String] = {
      body match {
        // Simple field access: _.field
        case Select(Ident(name), fieldName) if name == paramName =>
          List(fieldName.toString)

        // Nested field access: _.field1.field2
        case Select(qual, fieldName) =>
          extractFromBody(qual, paramName) :+ fieldName.toString

        case Ident(name) if name == paramName =>
          Nil

        case _ =>
          c.abort(c.enclosingPosition, s"Unsupported selector expression. Expected _.field or _.field.nested, got: ${show(body)}")
      }
    }

    tree match {
      // Function literal: _ => _.field or x => x.field
      case Function(List(ValDef(_, paramName, _, _)), body) =>
        val path = extractFromBody(body, paramName)
        if (path.isEmpty) {
          c.abort(c.enclosingPosition, "Selector must access at least one field")
        }
        path

      case _ =>
        c.abort(c.enclosingPosition, s"Selector must be a function literal like _.field or _.field.nested")
    }
  }

  /**
   * Build a DynamicOptic expression from a field path.
   */
  private def buildOpticExpr(c: blackbox.Context)(path: List[String]): c.Expr[DynamicOptic] = {
    import c.universe._

    val nodes = path.map { fieldName =>
      q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"
    }

    val vectorTree = q"_root_.scala.collection.immutable.Vector(..$nodes)"
    c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic($vectorTree)")
  }
}
