package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

final class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  def addField(fieldPath: DynamicOptic, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(fieldPath, default))

  def dropField(fieldPath: DynamicOptic, defaultForReverse: SchemaExpr[B, ?] = SchemaExpr.Literal((), Schema[Unit])): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.DropField(fieldPath, defaultForReverse))

  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(DynamicOptic(IndexedSeq.empty), from, to)
    )

  def transformField(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValue(fieldPath, transform))

  def mandateField(fieldPath: DynamicOptic, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(fieldPath, default))

  def optionalizeField(fieldPath: DynamicOptic): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(fieldPath))

  def changeFieldType(fieldPath: DynamicOptic, converter: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(fieldPath, converter))

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(DynamicOptic(IndexedSeq.empty), from, to)
    )

  def transformCase(caseName: String, caseActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic(IndexedSeq.empty), caseName, caseActions)
    )

  def transformElements(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformElements(fieldPath, transform))

  def transformKeys(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(fieldPath, transform))

  def transformValues(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(fieldPath, transform))

  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  def buildPartial: Migration[A, B] =
    build
}
