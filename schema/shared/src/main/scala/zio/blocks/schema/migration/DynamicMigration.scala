package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, DynamicOptic, SchemaExpr}

final case class DynamicMigration(actions: Vector[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => applyAction(v, action)
      case (left, _)          => left
    }
  }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    action match {
      case MigrationAction.AddField(at, default) =>
        addField(value, at, default)
      case MigrationAction.DropField(at, _) =>
        dropField(value, at)
      case MigrationAction.RenameField(at, from, to) =>
        renameField(value, at, from, to)
      case MigrationAction.RenameCase(at, from, to) =>
        renameCase(value, at, from, to)
      case MigrationAction.TransformCase(at, caseName, caseActions) =>
        transformCase(value, at, caseName, caseActions)
      case _ =>
        Right(value)
    }
  }

  private def addField(value: DynamicValue, at: DynamicOptic, default: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "AddField", "path must end with a field")))

        val fieldMap = fields.toMap
        if (fieldMap.contains(fieldName)) {
          Left(MigrationError.InvalidOperation(at, "AddField", s"field '$fieldName' already exists"))
        } else {
          default.evalDynamic(value) match {
            case Right(values) if values.nonEmpty =>
              Right(DynamicValue.Record(fields :+ (fieldName -> values.head)))
            case Right(_) =>
              Left(MigrationError.TransformationFailed(at, "default expression returned no value"))
            case Left(opticError) =>
              Left(MigrationError.TransformationFailed(at, s"default evaluation failed: ${opticError.message}"))
          }
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def dropField(value: DynamicValue, at: DynamicOptic): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "DropField", "path must end with a field")))

        val fieldMap = fields.toMap
        if (fieldMap.contains(fieldName)) {
          Right(DynamicValue.Record(fields.filter(_._1 != fieldName)))
        } else {
          Left(MigrationError.PathNotFound(at, s"field '$fieldName'"))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def renameField(value: DynamicValue, at: DynamicOptic, from: String, to: String): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val fieldMap = fields.toMap
        fieldMap.get(from) match {
          case Some(_) =>
            if (fieldMap.contains(to)) {
              Left(MigrationError.InvalidOperation(at, "RenameField", s"target field '$to' already exists"))
            } else {
              Right(DynamicValue.Record(fields.map {
                case (name, v) if name == from => (to, v)
                case other => other
              }))
            }
          case None =>
            Left(MigrationError.PathNotFound(at, s"field '$from'"))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def renameCase(value: DynamicValue, at: DynamicOptic, from: String, to: String): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Variant(caseName, caseValue) if caseName == from =>
        Right(DynamicValue.Variant(to, caseValue))
      case DynamicValue.Variant(_, _) =>
        Right(value)
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Variant", value.valueType.toString))
    }
  }

  private def transformCase(value: DynamicValue, at: DynamicOptic, caseName: String, caseActions: Vector[MigrationAction]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Variant(name, caseValue) if name == caseName =>
        val caseMigration = DynamicMigration(caseActions)
        caseMigration(caseValue).map(transformed => DynamicValue.Variant(name, transformed))
      case DynamicValue.Variant(_, _) =>
        Right(value)
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Variant", value.valueType.toString))
    }
  }
}

object DynamicMigration {
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  def fromAction(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))
}
