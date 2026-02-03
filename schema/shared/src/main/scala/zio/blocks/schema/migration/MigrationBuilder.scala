package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * A type-safe builder for constructing migrations between schema versions.
 *
 * `MigrationBuilder` provides a fluent API for composing migration actions while
 * maintaining type safety through the source and target schema types.
 *
 * Example with macro selectors:
 * {{{
 * val migration = Migration.newBuilder[PersonV1, PersonV2]
 *   .addField(_.age, SchemaExpr.Literal(30, Schema[Int]))
 *   .renameField(_.name, _.fullName)
 *   .build
 * }}}
 *
 * Example with DynamicOptic (lower-level API):
 * {{{
 * val migration = Migration.newBuilder[PersonV1, PersonV2]
 *   .addField(DynamicOptic.empty.field("age"), SchemaExpr.Literal(30, Schema[Int]))
 *   .renameField("name", "fullName")
 *   .build
 * }}}
 *
 * @tparam A The source schema type
 * @tparam B The target schema type
 */
final class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) extends MigrationBuilderMacros[A, B] with MigrationValidation[A, B] {

  /**
   * Add a new field with a default value.
   */
  def addField(fieldPath: DynamicOptic, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(fieldPath, default))

  /**
   * Drop an existing field.
   */
  def dropField(fieldPath: DynamicOptic, defaultForReverse: SchemaExpr[B, ?] = SchemaExpr.Literal((), Schema[Unit])): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.DropField(fieldPath, defaultForReverse))

  /**
   * Rename a field.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(DynamicOptic(IndexedSeq.empty), from, to)
    )

  /**
   * Rename a field at a nested path.
   */
  def renameFieldAt(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(at, from, to)
    )

  /**
   * Transform a field's value using a SchemaExpr.
   */
  def transformField(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValue(fieldPath, transform))

  /**
   * Make an optional field mandatory.
   */
  def mandateField(fieldPath: DynamicOptic, default: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(fieldPath, default))

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField(fieldPath: DynamicOptic): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(fieldPath))

  /**
   * Change the type of a field.
   */
  def changeFieldType(fieldPath: DynamicOptic, converter: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(fieldPath, converter))

  /**
   * Rename an enum/variant case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(DynamicOptic(IndexedSeq.empty), from, to)
    )

  /**
   * Transform a variant case.
   */
  def transformCase(caseName: String, caseActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic(IndexedSeq.empty), caseName, caseActions)
    )

  /**
   * Transform all elements in a collection.
   */
  def transformElements(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformElements(fieldPath, transform))

  /**
   * Transform all keys in a map.
   */
  def transformKeys(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(fieldPath, transform))

  /**
   * Transform all values in a map.
   */
  def transformValues(fieldPath: DynamicOptic, transform: SchemaExpr[A, ?]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(fieldPath, transform))

  /**
   * Build the final migration without validation.
   * 
   * This is the default build method that creates a migration without
   * compile-time validation. Use `buildValidated` for validation warnings.
   */
  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build a partial migration without validation.
   * 
   * Alias for `build` - both skip compile-time validation.
   * Use `buildValidated` for compile-time checks.
   */
  def buildPartial: Migration[A, B] =
    build
}
