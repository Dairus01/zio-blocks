package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  def spec = suite("MigrationSpec")(
    test("identity migration returns unchanged value") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration.identity
      val result = migration(dynamicValue)

      assertTrue(result == Right(dynamicValue))
    },
    test("composition applies actions sequentially") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration1 = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(30, Schema[Int])
        )
      ))
      val migration2 = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("country"),
          SchemaExpr.Literal("USA", Schema[String])
        )
      ))

      val composed = migration1 ++ migration2
      val result = composed(dynamicValue)

      assertTrue(result.isRight)
    },
    test("reverse reverses the migration") {
      val action = MigrationAction.DropField(
        DynamicOptic(IndexedSeq.empty).field("age"),
        SchemaExpr.Literal(0, Schema[Int])
      )
      val migration = DynamicMigration(Vector(action))
      val reversed = migration.reverse

      assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.AddField])
    }
  )

  final case class PersonV1(name: String)
}
