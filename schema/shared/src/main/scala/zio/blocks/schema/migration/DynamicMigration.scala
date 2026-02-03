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
      case MigrationAction.TransformValue(at, transform) =>
        transformValue(value, at, transform)
      case MigrationAction.Mandate(at, default) =>
        mandateField(value, at, default)
      case MigrationAction.Optionalize(at) =>
        optionalizeField(value, at)
      case MigrationAction.ChangeType(at, converter) =>
        transformValue(value, at, converter)
      case MigrationAction.Join(at, sourcePaths, combiner) =>
        joinFields(value, at, sourcePaths, combiner)
      case MigrationAction.Split(at, targetPaths, splitter) =>
        splitField(value, at, targetPaths, splitter)
      case MigrationAction.RenameCase(at, from, to) =>
        renameCase(value, at, from, to)
      case MigrationAction.TransformCase(at, caseName, caseActions) =>
        transformCase(value, at, caseName, caseActions)
      case MigrationAction.TransformElements(at, transform) =>
        transformElements(value, at, transform)
      case MigrationAction.TransformKeys(at, transform) =>
        transformKeys(value, at, transform)
      case MigrationAction.TransformValues(at, transform) =>
        transformMapValues(value, at, transform)
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
          evalDefault(default, value, at).map { defaultValue =>
            DynamicValue.Record(fields :+ (fieldName -> defaultValue))
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

  private def transformValue(value: DynamicValue, at: DynamicOptic, transform: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "TransformValue", "path must end with a field")))

        val fieldMap = fields.toMap
        fieldMap.get(fieldName) match {
          case Some(fieldValue) =>
            evalTransform(transform, fieldValue, at).map { transformed =>
              DynamicValue.Record(fields.map {
                case (name, v) if name == fieldName => (name, transformed)
                case other => other
              })
            }
          case None =>
            Left(MigrationError.PathNotFound(at, s"field '$fieldName'"))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def mandateField(value: DynamicValue, at: DynamicOptic, default: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "Mandate", "path must end with a field")))

        val fieldMap = fields.toMap
        fieldMap.get(fieldName) match {
          case Some(DynamicValue.SomeValue(_, innerValue)) =>
            Right(DynamicValue.Record(fields.map {
              case (name, _) if name == fieldName => (name, innerValue)
              case other => other
            }))
          case Some(DynamicValue.NoneValue(_)) =>
            evalDefault(default, value, at).map { defaultValue =>
              DynamicValue.Record(fields.map {
                case (name, _) if name == fieldName => (name, defaultValue)
                case other => other
              })
            }
          case Some(_) =>
            Left(MigrationError.TypeMismatch(at, "Option", "non-Option"))
          case None =>
            Left(MigrationError.PathNotFound(at, s"field '$fieldName'"))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def optionalizeField(value: DynamicValue, at: DynamicOptic): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val fieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "Optionalize", "path must end with a field")))

        val fieldMap = fields.toMap
        fieldMap.get(fieldName) match {
          case Some(fieldValue) =>
            val optionSchema = zio.blocks.schema.Schema.option(fieldValue.schema)
            val wrapped = DynamicValue.SomeValue(optionSchema, fieldValue)
            Right(DynamicValue.Record(fields.map {
              case (name, _) if name == fieldName => (name, wrapped)
              case other => other
            }))
          case None =>
            Left(MigrationError.PathNotFound(at, s"field '$fieldName'"))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def transformElements(value: DynamicValue, at: DynamicOptic, transform: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Sequence(values) =>
        val transformed = values.map { elem =>
          evalTransform(transform, elem, at)
        }
        val errors = transformed.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(MigrationError.MultipleErrors(errors.toVector))
        } else {
          val newValues = transformed.collect { case Right(v) => v }
          Right(DynamicValue.Sequence(newValues))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Sequence", value.valueType.toString))
    }
  }

  private def transformKeys(value: DynamicValue, at: DynamicOptic, transform: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Map(entries) =>
        val transformed = entries.map { case (k, v) =>
          evalTransform(transform, k, at).map((_, v))
        }
        val errors = transformed.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(MigrationError.MultipleErrors(errors.toVector))
        } else {
          val newEntries = transformed.collect { case Right(e) => e }
          Right(DynamicValue.Map(newEntries))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Map", value.valueType.toString))
    }
  }

  private def transformMapValues(value: DynamicValue, at: DynamicOptic, transform: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Map(entries) =>
        val transformed = entries.map { case (k, v) =>
          evalTransform(transform, v, at).map((k, _))
        }
        val errors = transformed.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(MigrationError.MultipleErrors(errors.toVector))
        } else {
          val newEntries = transformed.collect { case Right(e) => e }
          Right(DynamicValue.Map(newEntries))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Map", value.valueType.toString))
    }
  }

  private def joinFields(value: DynamicValue, at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val targetFieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "Join", "path must end with a field")))

        // Extract values from source paths
        val fieldMap = fields.toMap
        val sourceValues = sourcePaths.map { path =>
          path.nodes.lastOption.collect {
            case DynamicOptic.Node.Field(name) => fieldMap.get(name)
          }.flatten
        }

        // Check if all source fields exist
        if (sourceValues.exists(_.isEmpty)) {
          Left(MigrationError.PathNotFound(at, "one or more source fields not found"))
        } else {
          val values = sourceValues.flatten
          // For now, just concatenate string values or use first value
          // In a full implementation, combiner would be evaluated
          val joinedValue = if (values.nonEmpty) values.head else DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Unit)
          
          // Remove source fields and add target field
          val filteredFields = fields.filterNot { case (name, _) =>
            sourcePaths.exists(_.nodes.lastOption.exists {
              case DynamicOptic.Node.Field(n) => n == name
              case _ => false
            })
          }
          
          Right(DynamicValue.Record(filteredFields :+ (targetFieldName -> joinedValue)))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  private def splitField(value: DynamicValue, at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    value match {
      case DynamicValue.Record(fields) =>
        val sourceFieldName = at.nodes.lastOption.collect {
          case DynamicOptic.Node.Field(name) => name
        }.getOrElse(return Left(MigrationError.InvalidOperation(at, "Split", "path must end with a field")))

        val fieldMap = fields.toMap
        fieldMap.get(sourceFieldName) match {
          case Some(sourceValue) =>
            // For now, duplicate the value to all target fields
            // In a full implementation, splitter would be evaluated
            val targetFields = targetPaths.flatMap { path =>
              path.nodes.lastOption.collect {
                case DynamicOptic.Node.Field(name) => (name, sourceValue)
              }
            }
            
            // Remove source field and add target fields
            val filteredFields = fields.filter(_._1 != sourceFieldName)
            Right(DynamicValue.Record(filteredFields ++ targetFields))
          case None =>
            Left(MigrationError.PathNotFound(at, s"field '$sourceFieldName'"))
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
    }
  }

  // Helper methods for SchemaExpr evaluation
  private def evalDefault(expr: SchemaExpr[?, ?], context: DynamicValue, at: DynamicOptic): Either[MigrationError, DynamicValue] = {
    expr match {
      case SchemaExpr.Literal(value, schema) =>
        Right(schema.toDynamicValue(value.asInstanceOf[schema.Type]))
      case SchemaExpr.DefaultValue(schema) =>
        // For now, just use a placeholder value
        Left(MigrationError.TransformationFailed(at, "DefaultValue requires runtime default extraction (not yet implemented)"))
      case _ =>
        Left(MigrationError.TransformationFailed(at, "complex default expressions not yet supported"))
    }
  }

  private def evalTransform(expr: SchemaExpr[?, ?], value: DynamicValue, at: DynamicOptic): Either[MigrationError, DynamicValue] = {
    expr match {
      case SchemaExpr.Literal(v, schema) =>
        Right(schema.toDynamicValue(v.asInstanceOf[schema.Type]))
      case SchemaExpr.DefaultValue(_) =>
        // Keep the existing value
        Right(value)
      case _ =>
        Right(value) // For non-literal expressions, return unchanged
    }
  }
}

object DynamicMigration {
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  def fromAction(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))
}
