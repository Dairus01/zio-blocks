package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Gen

/**
 * Comprehensive test suite for the migration system.
 * 
 * Tests cover:
 * - Basic migration operations
 * - Nested path navigation  
 * - SchemaExpr transformations
 * - Migration laws (identity, associativity, reverse)
 * - Error cases
 */
object MigrationSpec extends SchemaBaseSpec {

  def spec = suite("MigrationSpec")(
    basicOperationsTests,
    nestedPathTests,
    lawsTests,
    schemaExprTests,
    errorHandlingTests
  )

  private def basicOperationsTests = suite("Basic Operations")(
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
    test("addField adds a new field with literal default") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(30, Schema[Int])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].fields.toMap.contains("age"))
    },
    test("dropField removes an existing field") {
      val schema = Schema.record[PersonV2](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
        Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
      )
      val person = PersonV2("John", 30)
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.DropField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(0, Schema[Int])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight) &&
      assertTrue(!result.toOption.get.asInstanceOf[DynamicValue.Record].fields.toMap.contains("age"))
    },
    test("renameField changes field name") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.RenameField(
          DynamicOptic(IndexedSeq.empty),
          "name",
          "fullName"
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight) &&
      assertTrue(!result.toOption.get.asInstanceOf[DynamicValue.Record].fields.toMap.contains("name")) &&
      assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].fields.toMap.contains("fullName"))
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
      assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].fields.toMap.contains("age")) &&
      assertTrue(result.toOption.get.asInstanceOf[DynamicValue.Record].fields.toMap.contains("country"))
    }
  )

  private def nestedPathTests = suite("Nested Path Navigation")(
    test("addField at nested path") {
      val addressSchema = Schema.record[Address](
        Schema.Field("street", Schema[String], get0 = _.street, set0 = (a, v) => a.copy(street = v))
      )
      val personSchema = Schema.record[PersonWithAddress](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
        Schema.Field("address", addressSchema, get0 = _.address, set0 = (p, v) => p.copy(address = v))
      )

      val person = PersonWithAddress("John", Address("Main St"))
      val dynamicValue = personSchema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("address").field("city"),
          SchemaExpr.Literal("NYC", Schema[String])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight)
    },
    test("renameField at nested path") {
      val addressSchema = Schema.record[Address](
        Schema.Field("street", Schema[String], get0 = _.street, set0 = (a, v) => a.copy(street = v))
      )
      val personSchema = Schema.record[PersonWithAddress](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
        Schema.Field("address", addressSchema, get0 = _.address, set0 = (p, v) => p.copy(address = v))
      )

      val person = PersonWithAddress("John", Address("Main St"))
      val dynamicValue = personSchema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.RenameField(
          DynamicOptic(IndexedSeq.empty).field("address"),
          "street",
          "streetName"
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight)
    }
  )

  private def lawsTests = suite("Migration Laws")(
    test("identity law") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration.identity
      val result = migration(dynamicValue)

      assertTrue(result == Right(dynamicValue))
    },
    test("associativity law") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val m1 = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(30, Schema[Int])
        )
      ))
      val m2 = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("country"),
          SchemaExpr.Literal("USA", Schema[String])
        )
      ))
      val m3 = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("email"),
          SchemaExpr.Literal("test@example.com", Schema[String])
        )
      ))

      val left = (m1 ++ m2) ++ m3
      val right = m1 ++ (m2 ++ m3)

      val leftResult = left(dynamicValue)
      val rightResult = right(dynamicValue)

      assertTrue(leftResult.isRight) &&
      assertTrue(rightResult.isRight) &&
      assertTrue(leftResult.toOption.get.asInstanceOf[DynamicValue.Record].fields.length ==
                 rightResult.toOption.get.asInstanceOf[DynamicValue.Record].fields.length)
    },
    test("structural reverse law") {
      val action = MigrationAction.AddField(
        DynamicOptic(IndexedSeq.empty).field("age"),
        SchemaExpr.Literal(30, Schema[Int])
      )
      val migration = DynamicMigration(Vector(action))
      val reversed = migration.reverse.reverse

      assertTrue(reversed.actions.length == migration.actions.length) &&
      assertTrue(reversed.actions.head.getClass == migration.actions.head.getClass)
    },
    test("reverse migration structurally undoes forward migration") {
      val action = MigrationAction.DropField(
        DynamicOptic(IndexedSeq.empty).field("age"),
        SchemaExpr.Literal(0, Schema[Int])
      )
      val migration = DynamicMigration(Vector(action))
      val reversed = migration.reverse

      assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.AddField])
    }
  )

  private def schemaExprTests = suite("SchemaExpr Integration")(
    test("literal default value is used") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(42, Schema[Int])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight) &&
      assertTrue {
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val ageValue = record.fields.toMap.get("age")
        ageValue.exists {
          case DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Int(42)) => true
          case _ => false
        }
      }
    },
    test("transformField with literal expression") {
      val schema = Schema.record[PersonV2](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
        Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
      )
      val person = PersonV2("John", 30)
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.TransformValue(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.Literal(35, Schema[Int])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isRight)
    }
  )

  private def errorHandlingTests = suite("Error Handling")(
    test("addField to non-existent parent path fails") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("address").field("street"),
          SchemaExpr.Literal("Main St", Schema[String])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isLeft)
    },
    test("dropField on non-existent field fails") {
      val schema = Schema.record[PersonV1](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v))
      )
      val person = PersonV1("John")
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.DropField(
          DynamicOptic(IndexedSeq.empty).field("nonexistent"),
          SchemaExpr.Literal((), Schema[Unit])
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isLeft)
    },
    test("renameField when target exists fails") {
      val schema = Schema.record[PersonV2](
        Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
        Schema.Field("age", Schema[Int], get0 = _.age, set0 = (p, v) => p.copy(age = v))
      )
      val person = PersonV2("John", 30)
      val dynamicValue = schema.toDynamicValue(person)

      val migration = DynamicMigration(Vector(
        MigrationAction.RenameField(
          DynamicOptic(IndexedSeq.empty),
          "name",
          "age"
        )
      ))
      val result = migration(dynamicValue)

      assertTrue(result.isLeft)
    }
  )

  // Test data types
  final case class PersonV1(name: String)
  final case class PersonV2(name: String, age: Int)
  final case class Address(street: String)
  final case class PersonWithAddress(name: String, address: Address)
}
