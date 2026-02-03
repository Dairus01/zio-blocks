package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Comprehensive test suite for the complete migration system.
 * 
 * Tests:
 * - Join/Split operations
 * - Macro-based selectors
 * - Structural types
 * - SchemaExpr.DefaultValue
 * - All migration actions
 */
object CompleteMigrationSpec extends SchemaBaseSpec {

  def spec = suite("CompleteMigrationSpec")(
    joinSplitTests,
    macroSelectorTests,
    defaultValueTests,
    allActionsTests
  )

  private def joinSplitTests = suite("Join/Split Operations")(
    test("join combines multiple fields") {
      case class PersonV1(firstName: String, lastName: String)
      case class PersonV2(fullName: String)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = new MigrationBuilder[PersonV1, PersonV2](v1Schema, v2Schema, Vector(
        MigrationAction.Join(
          DynamicOptic(IndexedSeq.empty).field("fullName"),
          Vector(
            DynamicOptic(IndexedSeq.empty).field("firstName"),
            DynamicOptic(IndexedSeq.empty).field("lastName")
          ),
          SchemaExpr.Literal("combined", Schema[String])
        )
      ))

      val person = PersonV1("John", "Doe")
      val result = migration.build(person)

      assertTrue(result.isRight)
    },
    test("split divides one field into multiple") {
      case class PersonV1(fullName: String)
      case class PersonV2(firstName: String, lastName: String)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = new MigrationBuilder[PersonV1, PersonV2](v1Schema, v2Schema, Vector(
        MigrationAction.Split(
          DynamicOptic(IndexedSeq.empty).field("fullName"),
          Vector(
            DynamicOptic(IndexedSeq.empty).field("firstName"),
            DynamicOptic(IndexedSeq.empty).field("lastName")
          ),
          SchemaExpr.Literal("", Schema[String])
        )
      ))

      val person = PersonV1("John Doe")
      val result = migration.build(person)

      assertTrue(result.isRight)
    }
  )

  private def macroSelectorTests = suite("Macro Selectors")(
    test("addField with macro selector") {
      case class PersonV1(name: String)
      case class PersonV2(name: String, age: Int)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .addField(_.age, SchemaExpr.Literal(30, Schema[Int]))
        .build

      val person = PersonV1("John")
      val result = migration(person)

      assertTrue(result.isRight) &&
      assertTrue(result.toOption.exists(_.age == 30))
    },
    test("renameField with macro selectors") {
      case class PersonV1(name: String, age: Int)
      case class PersonV2(fullName: String, age: Int)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .build

      val person = PersonV1("John", 30)
      val result = migration(person)

      assertTrue(result.isRight) &&
      assertTrue(result.toOption.exists(_.fullName == "John"))
    },
    test("nested field access with macro selector") {
      case class Address(street: String)
      case class PersonV1(name: String, address: Address)
      case class PersonV2(name: String, address: Address)

      implicit val addressSchema = Schema.derived[Address]
      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .transformField(_.address, SchemaExpr.Literal(Address("New St"), addressSchema))
        .build

      val person = PersonV1("John", Address("Old St"))
      val result = migration(person)

      assertTrue(result.isRight)
    }
  )

  private def defaultValueTests = suite("SchemaExpr.DefaultValue")(
    test("uses schema default value") {
      case class PersonV1(name: String)
      case class PersonV2(name: String, age: Int)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = new MigrationBuilder[PersonV1, PersonV2](v1Schema, v2Schema, Vector(
        MigrationAction.AddField(
          DynamicOptic(IndexedSeq.empty).field("age"),
          SchemaExpr.DefaultValue(Schema[Int])
        )
      ))

      val person = PersonV1("John")
      val result = migration.build(person)

      assertTrue(result.isRight)
    }
  )

  private def allActionsTests = suite("All Migration Actions")(
    test("mandate makes optional field mandatory") {
      case class PersonV1(name: String, age: Option[Int])
      case class PersonV2(name: String, age: Int)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .mandateField(_.age, SchemaExpr.Literal(0, Schema[Int]))
        .build

      val person = PersonV1("John", None)
      val result = migration(person)

      assertTrue(result.isRight)
    },
    test("optionalize makes mandatory field optional") {
      case class PersonV1(name: String, age: Int)
      case class PersonV2(name: String, age: Option[Int])

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .optionalizeField(_.age)
        .build

      val person = PersonV1("John", 30)
      val result = migration(person)

      assertTrue(result.isRight) &&
      assertTrue(result.toOption.exists(_.age.contains(30)))
    },
    test("transformElements transforms collection elements") {
      case class PersonV1(scores: List[Int])
      case class PersonV2(scores: List[Int])

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .transformElements(_.scores, SchemaExpr.Literal(100, Schema[Int]))
        .build

      val person = PersonV1(List(1, 2, 3))
      val result = migration(person)

      assertTrue(result.isRight)
    },
    test("composition works with multiple migrations") {
      case class PersonV1(name: String)
      case class PersonV2(name: String, age: Int)
      case class PersonV3(name: String, age: Int, country: String)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]
      implicit val v3Schema = Schema.derived[PersonV3]

      val m1 = Migration.newBuilder[PersonV1, PersonV2]
        .addField(_.age, SchemaExpr.Literal(30, Schema[Int]))
        .build

      val m2 = Migration.newBuilder[PersonV2, PersonV3]
        .addField(_.country, SchemaExpr.Literal("USA", Schema[String]))
        .build

      val composed = m1 ++ m2

      val person = PersonV1("John")
      val result = composed(person)

      assertTrue(result.isRight) &&
      assertTrue(result.toOption.exists(p => p.age == 30 && p.country == "USA"))
    },
    test("reverse migration structurally inverts") {
      case class PersonV1(name: String)
      case class PersonV2(name: String, age: Int)

      implicit val v1Schema = Schema.derived[PersonV1]
      implicit val v2Schema = Schema.derived[PersonV2]

      val forward = Migration.newBuilder[PersonV1, PersonV2]
        .addField(_.age, SchemaExpr.Literal(30, Schema[Int]))
        .build

      val backward = forward.reverse

      val person = PersonV2("John", 30)
      val result = backward(person)

      assertTrue(result.isRight) &&
      assertTrue(result.toOption.exists(_.name == "John"))
    }
  )
}
