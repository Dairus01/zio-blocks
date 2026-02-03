# Schema Migration System

The ZIO Schema Migration system provides a pure, algebraic approach to evolving data structures across schema versions.

## Overview

The migration system consists of three main components:

1. **`DynamicMigration`**: A fully serializable, untyped migration that operates on `DynamicValue`
2. **`Migration[A, B]`**: A type-safe wrapper that provides compile-time guarantees
3. **`MigrationBuilder[A, B]`**: A fluent API for constructing migrations

## Key Features

- **Pure & Serializable**: Migrations are first-class data that can be stored and transmitted
- **Composable**: Migrations compose via the `++` operator
- **Reversible**: Structural reverse transformations via the `reverse` method
- **Type-Safe**: The typed API catches errors at compile time
- **Nested Paths**: Support for deep field access using `DynamicOptic`
- **SchemaExpr Integration**: Transform values using expressions

## Basic Usage

### Simple Field Operations

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Define schema versions
case class PersonV1(name: String)
case class PersonV2(name: String, age: Int)

implicit val v1Schema = Schema[PersonV1]
implicit val v2Schema = Schema[PersonV2]

// Build a migration
val migration = Migration.newBuilder[PersonV1, PersonV2]
  .addField(
    DynamicOptic(IndexedSeq.empty).field("age"),
    SchemaExpr.Literal(30, Schema[Int])
  )
  .build

// Apply the migration
val person = PersonV1("John")
val result: Either[MigrationError, PersonV2] = migration(person)
```

### Composing Migrations

```scala
val v1ToV2: Migration[PersonV1, PersonV2] = ...
val v2ToV3: Migration[PersonV2, PersonV3] = ...

// Compose migrations
val v1ToV3 = v1ToV2 ++ v2ToV3

// Satisfies associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
```

### Reversing Migrations

```scala
val forward: Migration[PersonV1, PersonV2] = ...

// Create a best-effort reverse migration
val backward: Migration[PersonV2, PersonV1] = forward.reverse

// Satisfies: m.reverse.reverse == m (structurally)
```

## Advanced Features

### Nested Path Navigation

Access and modify deeply nested fields:

```scala
case class Address(street: String, city: String)
case class Person(name: String, address: Address)

val migration = Migration.newBuilder[PersonV1, PersonV2]
  .addField(
    DynamicOptic(IndexedSeq.empty).field("address").field("zipCode"),
    SchemaExpr.Literal("00000", Schema[String])
  )
  .renameFieldAt(
    DynamicOptic(IndexedSeq.empty).field("address"),
    "street",
    "streetName"
  )
  .build
```

### Field Path Syntax

Use `DynamicOptic` to construct field paths:

```scala
// Top-level field
DynamicOptic(IndexedSeq.empty).field("age")

// Nested field
DynamicOptic(IndexedSeq.empty).field("address").field("street")

// Alternatively use the empty constructor
val path = DynamicOptic.empty.field("user").field("email")
```

### SchemaExpr Transformations

Transform field values using `SchemaExpr`:

```scala
// Add a field with a literal default
.addField(
  DynamicOptic.empty.field("status"),
  SchemaExpr.Literal("active", Schema[String])
)

// Transform a field value
.transformField(
  DynamicOptic.empty.field("age"),
  SchemaExpr.Literal(age + 1, Schema[Int])
)
```

### Optional Field Transformations

```scala
// Make an optional field mandatory
.mandateField(
  DynamicOptic.empty.field("email"),
  SchemaExpr.Literal("no-email@example.com", Schema[String])
)

// Make a mandatory field optional
.optionalizeField(
  DynamicOptic.empty.field("middleName")
)
```

### Collection Transformations

```scala
// Transform all elements in a sequence
.transformElements(
  DynamicOptic.empty.field("items"),
  SchemaExpr.Literal(/* transform */, Schema[Item])
)

// Transform map values
.transformValues(
  DynamicOptic.empty.field("metadata"),
  SchemaExpr.Literal(/* transform */, Schema[String])
)
```

### Variant/Enum Migrations

```scala
sealed trait Status
case object Active extends Status
case object Inactive extends Status

// Rename a case
.renameCase("Active", "Enabled")

// Transform a case's payload
.transformCase("User", Vector(
  MigrationAction.AddField(
    DynamicOptic.empty.field("verified"),
    SchemaExpr.Literal(false, Schema[Boolean])
  )
))
```

## Migration Laws

The migration system satisfies several algebraic laws:

### Identity Law

```scala
Migration.identity[A].apply(a) == Right(a)
```

### Associativity Law

```scala
(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
```

### Structural Reverse Law

```scala
m.reverse.reverse == m  // structurally identical
```

## Error Handling

Migrations return `Either[MigrationError, B]`:

```scala
val result = migration(value)

result match {
  case Right(migrated) => 
    // Success
  case Left(error) =>
    error match {
      case MigrationError.PathNotFound(path, expected) =>
        // Handle path not found
      case MigrationError.TypeMismatch(path, expected, actual) =>
        // Handle type mismatch
      case MigrationError.TransformationFailed(path, reason) =>
        // Handle transformation failure
      case _ =>
        // Handle other errors
    }
}
```

## Best Practices

1. **Keep Migrations Small**: Compose many small migrations rather than one large one
2. **Test Migrations**: Write tests for each migration step
3. **Version Your Schemas**: Use explicit version types (V1, V2, etc.)
4. **Document Breaking Changes**: Comment why fields were added/removed
5. **Use Literal Defaults**: Prefer `SchemaExpr.Literal` for default values
6. **Test Reverses**: Ensure reverse migrations work as expected

## Performance Considerations

- Migrations operate on `DynamicValue`, which has some overhead
- Compose migrations before applying rather than applying sequentially
- Consider caching compiled migrations for frequently-used transformations

## Limitations

- **SchemaExpr**: Currently limited to literal expressions
- **Join/Split**: Not yet implemented
- **Semantic Reverse**: Reverse is structural only, not semantic

## Future Enhancements

- Full SchemaExpr evaluation with context
- Macro-based selector syntax (`_.field` instead of string literals)
- Join/Split operations for combining/splitting fields
- Migration validation at compile time
- Automated migration generation from schema differences

## Examples

See `MigrationSpec.scala` for comprehensive examples covering:
- Basic field operations
- Nested path navigation
- SchemaExpr integration
- Error handling
- Migration laws
