package zio.blocks.schema.migration

import scala.reflect.macros.blackbox
import zio.blocks.schema.DynamicOptic

private[migration] object SelectorMacros {
  def extractOpticImpl[A](c: blackbox.Context)(selector: c.Expr[A => Any]): c.Expr[DynamicOptic] = {
    import c.universe._
    
    def extractFromBody(body: c.Tree, paramName: c.TermName): List[String] = {
      body match {
        case Select(Ident(name), fieldName) if name == paramName =>
          List(fieldName.toString)
        case Select(qual, fieldName) =>
          extractFromBody(qual, paramName) :+ fieldName.toString
        case Ident(name) if name == paramName =>
          Nil
        case _ =>
          c.abort(c.enclosingPosition, s"Unsupported selector expression. Expected _.field or _.field.nested")
      }
    }

    selector.tree match {
      case Function(List(ValDef(_, paramName, _, _)), body) =>
        val path = extractFromBody(body, paramName)
        if (path.isEmpty) {
          c.abort(c.enclosingPosition, "Selector must access at least one field")
        }
        val nodes = path.map { fieldName =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"
        }
        val vectorTree = q"_root_.scala.collection.immutable.Vector(..$nodes)"
        c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic($vectorTree)")
      case _ =>
        c.abort(c.enclosingPosition, s"Selector must be a function literal like _.field or _.field.nested")
    }
  }
}
