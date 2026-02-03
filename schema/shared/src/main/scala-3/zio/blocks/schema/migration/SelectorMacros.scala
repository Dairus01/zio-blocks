package zio.blocks.schema.migration

import scala.quoted.*
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
  def extractOpticImpl[A: Type](selector: Expr[A => Any])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    val path = extractPath(selector)
    buildOpticExpr(path)
  }

  /**
   * Extract a field path from a selector function.
   */
  private def extractPath[A: Type](selector: Expr[A => Any])(using Quotes): List[String] = {
    import quotes.reflect.*

    def extractFromTerm(term: Term): List[String] = term match {
      // Simple field access: _.field
      case Select(Ident(_), fieldName) =>
        List(fieldName)

      // Nested field access: _.field1.field2
      case Select(qual, fieldName) =>
        extractFromTerm(qual) :+ fieldName

      // Identifier (the parameter itself)
      case Ident(_) =>
        Nil

      case other =>
        report.errorAndAbort(s"Unsupported selector expression. Expected _.field or _.field.nested, got: ${other.show}")
    }

    selector.asTerm match {
      // Lambda: (x: A) => x.field
      case Lambda(List(ValDef(paramName, _, _)), body) =>
        val path = extractFromTerm(body)
        if (path.isEmpty) {
          report.errorAndAbort("Selector must access at least one field")
        }
        path

      // Inlined lambda
      case Inlined(_, _, Lambda(List(ValDef(paramName, _, _)), body)) =>
        val path = extractFromTerm(body)
        if (path.isEmpty) {
          report.errorAndAbort("Selector must access at least one field")
        }
        path

      case other =>
        report.errorAndAbort(s"Selector must be a function literal like _.field or _.field.nested, got: ${other.show}")
    }
  }

  /**
   * Build a DynamicOptic expression from a field path.
   */
  private def buildOpticExpr(path: List[String])(using Quotes): Expr[DynamicOptic] = {
    val nodeExprs = path.map { fieldName =>
      val nameExpr = Expr(fieldName)
      '{ DynamicOptic.Node.Field($nameExpr) }
    }

    val vectorExpr = Expr.ofSeq(nodeExprs)
    '{ DynamicOptic(Vector($vectorExpr: _*)) }
  }
}
