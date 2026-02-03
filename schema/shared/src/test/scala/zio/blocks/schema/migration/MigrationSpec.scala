package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object MigrationSpec extends SchemaBaseSpec {

  def spec = suite("MigrationSpec")(
    suite("DynamicMigration")(
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
      test("add field adds a new field with default value") {
        val schema = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        val person = PersonV1("John")
        val dynamicValue = schema.toDynamicValue(person)

        val action = MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(30, Schema[Int])
        )
        val migration = DynamicMigration(Vector(action))
        val result = migration(dynamicValue)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].values.contains("age"))
      },
      test("drop field removes an existing field") {
        val schema = Schema.record[PersonV2](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
        )
        val person = PersonV2("John", 30)
        val dynamicValue = schema.toDynamicValue(person)

        val action = MigrationAction.DropField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(0, Schema[Int])
        )
        val migration = DynamicMigration(Vector(action))
        val result = migration(dynamicValue)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.asInstanceOf[DynamicValue.Record].values.contains("age"))
      },
      test("rename field changes field name") {
        val schema = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        val person = PersonV1("John")
        val dynamicValue = schema.toDynamicValue(person)

        val action = MigrationAction.RenameField(
          DynamicOptic(IndexedSeq.empty),
          "name",
          "fullName"
        )
        val migration = DynamicMigration(Vector(action))
        val result = migration(dynamicValue)

        assertTrue(result.isRight) &&
        assertTrue(!result.toOption.get.asInstanceOf[DynamicValue.Record].values.contains("name")) &&
        assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].values.contains("fullName"))
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

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].values.contains("age")) &&
        assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].values.contains("country"))
      },
      test("reverse reverses the migration") {
        val schema = Schema.record[PersonV2](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
        )
        val person = PersonV2("John", 30)
        val dynamicValue = schema.toDynamicValue(person)

        val action = MigrationAction.DropField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(0, Schema[Int])
        )
        val migration = DynamicMigration(Vector(action))
        val reversed = migration.reverse

        assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.AddField])
      }
    ),
    suite("Migration")(
      test("identity migration preserves value") {
        implicit val schema: Schema[PersonV1] = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        val person = PersonV1("John")

        val migration = Migration.identity[PersonV1]
        val result = migration(person)

        assertTrue(result == Right(person))
      },
      test("typed migration adds field successfully") {
        implicit val sourceSchema: Schema[PersonV1] = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        implicit val targetSchema: Schema[PersonV2] = Schema.record[PersonV2](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
        )

        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic(IndexedSeq.empty).field("age"), SchemaExpr.Literal(30, Schema[Int]))
          .build

        val person = PersonV1("John")
        val result = migration(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.name == "John") &&
        assertTrue(result.toOption.get.age == 30)
      },
      test("composition chains migrations") {
        implicit val v1Schema: Schema[PersonV1] = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        implicit val v2Schema: Schema[PersonV2] = Schema.record[PersonV2](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
        )
        implicit val v3Schema: Schema[PersonV3] = Schema.record[PersonV3](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v)),
          Schema.Field("country", Schema[String], get0 = _.country, set0 = (p, v) => p.copy(country = v))
        )

        val migration1 = Migration.newBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic(IndexedSeq.empty).field("age"), SchemaExpr.Literal(30, Schema[Int]))
          .build

        val migration2 = Migration.newBuilder[PersonV2, PersonV3]
          .addField(DynamicOptic(IndexedSeq.empty).field("country"), SchemaExpr.Literal("USA", Schema[String]))
          .build

        val composed = migration1 ++ migration2
        val person = PersonV1("John")
        val result = composed(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.name == "John") &&
        assertTrue(result.toOption.get.age == 30) &&
        assertTrue(result.toOption.get.country == "USA")
      }
    ),
    suite("Laws")(
      test("identity law") {
        implicit val schema: Schema[PersonV1] = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        val person = PersonV1("John")

        val migration = Migration.identity[PersonV1]
        val result = migration(person)

        assertTrue(result == Right(person))
      },
      test("associativity law") {
        implicit val v1Schema: Schema[PersonV1] = Schema.record[PersonV1](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
        )
        implicit val v2Schema: Schema[PersonV2] = Schema.record[PersonV2](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
        )
        implicit val v3Schema: Schema[PersonV3] = Schema.record[PersonV3](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v)),
          Schema.Field("country", Schema[String], get0 = _.country, set0 = (p, v) => p.copy(country = v))
        )
        implicit val v4Schema: Schema[PersonV4] = Schema.record[PersonV4](
          Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
          Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v)),
          Schema.Field("country", Schema[String], get0 = _.country, set0 = (p, v) => p.copy(country = v)),
          Schema.Field("email", Schema[String], get0 = _.email, set0 = (p, v) => p.copy(email = v))
        )

        val m1 = Migration.newBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic(IndexedSeq.empty).field("age"), SchemaExpr.Literal(30, Schema[Int]))
          .build

        val m2 = Migration.newBuilder[PersonV2, PersonV3]
          .addField(DynamicOptic(IndexedSeq.empty).field("country"), SchemaExpr.Literal("USA", Schema[String]))
          .build

        val m3 = Migration.newBuilder[PersonV3, PersonV4]
          .addField(DynamicOptic(IndexedSeq.empty).field("email"), SchemaExpr.Literal("test@example.com", Schema[String]))
          .build

        val left = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)

        val person = PersonV1("John")
        val leftResult = left(person)
        val rightResult = right(person)

        assertTrue(leftResult == rightResult)
      },
      test("structural reverse law") {
        val migration = DynamicMigration(Vector(
          MigrationAction.AddField(
            DynamicOptic(IndexedSeq.empty).field("age"),
            SchemaExpr.Literal(30, Schema[Int])
          )
        ))

        val reversed = migration.reverse.reverse

        assertTrue(reversed.actions.length == migration.actions.length) &&
        assertTrue(reversed.actions.head.getClass == migration.actions.head.getClass)
      }
    )
  )

  final case class PersonV1(name: String)
  final case class PersonV2(name: String, age: Int)
  final case class PersonV3(name: String, age: Int, country: String)
  final case class PersonV4(name: String, age: Int, country: String, email: String)
}
