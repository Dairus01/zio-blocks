package zio.blocks.schema

/**
 * Package object for schema migration functionality.
 * 
 * Provides convenient access to:
 * - Migration builders
 * - Structural schema creation
 * - Macro-based selector API
 * 
 * Example usage:
 * {{{
 * import zio.blocks.schema.migration._
 * 
 * // Define structural types for old versions
 * type PersonV0 = { def firstName: String; def lastName: String }
 * type PersonV1 = { def fullName: String; def age: Int }
 * 
 * // Create schemas
 * implicit val v0Schema = StructuralSchemaOps.structural[PersonV0]
 * implicit val v1Schema = StructuralSchemaOps.structural[PersonV1]
 * 
 * // Build migration with macro selectors
 * val migration = Migration.newBuilder[PersonV0, PersonV1]
 *   .addField(_.age, SchemaExpr.Literal(0, Schema[Int]))
 *   .addField(_.fullName, SchemaExpr.Literal("", Schema[String]))
 *   .dropField(_.firstName)
 *   .dropField(_.lastName)
 *   .build
 * }}}
 */
package object migration {
  
  /**
   * Helper for creating structural schemas.
   * 
   * Example:
   * {{{
   * type PersonV0 = { def name: String; def age: Int }
   * val schema = structural[PersonV0]
   * }}}
   */
  def structural[T](implicit ev: zio.blocks.schema.Schema[T]): zio.blocks.schema.Schema[T] = ev
  
  /**
   * Type alias for convenience.
   */
  type MigrationError = zio.blocks.schema.migration.MigrationError
  
  /**
   * Type alias for convenience.
   */
  type MigrationAction = zio.blocks.schema.migration.MigrationAction
  
  /**
   * Companion object for MigrationError.
   */
  val MigrationError = zio.blocks.schema.migration.MigrationError
  
  /**
   * Companion object for MigrationAction.
   */
  val MigrationAction = zio.blocks.schema.migration.MigrationAction
}
