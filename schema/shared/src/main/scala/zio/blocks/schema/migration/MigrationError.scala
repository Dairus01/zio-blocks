package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

sealed trait MigrationError {
  def message: String
  def path: DynamicOptic
}

object MigrationError {
  final case class PathNotFound(path: DynamicOptic, expected: String) extends MigrationError {
    def message: String = s"Path not found at ${path.toString}: expected $expected"
  }

  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch at ${path.toString}: expected $expected, got $actual"
  }

  final case class TransformationFailed(path: DynamicOptic, reason: String) extends MigrationError {
    def message: String = s"Transformation failed at ${path.toString}: $reason"
  }

  final case class MandatoryFieldMissing(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Mandatory field missing at ${path.toString}: $fieldName"
  }

  final case class InvalidOperation(path: DynamicOptic, operation: String, reason: String) extends MigrationError {
    def message: String = s"Invalid operation '$operation' at ${path.toString}: $reason"
  }

  final case class MultipleErrors(errors: Vector[MigrationError]) extends MigrationError {
    def path: DynamicOptic = DynamicOptic(IndexedSeq.empty)
    def message: String = errors.map(_.message).mkString("; ")
  }
}
