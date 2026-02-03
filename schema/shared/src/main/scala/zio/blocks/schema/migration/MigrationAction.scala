package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaExpr}

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  final case class AddField(
    at: DynamicOptic,
    default: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class RenameField(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameField(at, to, from)
  }

  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class Mandate(
    at: DynamicOptic,
    default: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combiner)
  }

  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter)
  }

  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.map(_.reverse))
  }

  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[?, ?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }
}
