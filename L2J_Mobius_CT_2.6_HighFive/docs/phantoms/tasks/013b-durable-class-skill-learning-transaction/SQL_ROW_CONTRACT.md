# SQL and row-guard contract

This file fixes required semantics, not exact local constant names.

## Main SP

```sql
SELECT sp
FROM characters
WHERE charId = ?
FOR UPDATE
```

Write exact post-cost SP to the same row. Require exactly one affected row.

## Subclass SP

```sql
SELECT sp, class_id
FROM character_subclasses
WHERE charId = ? AND class_index = ?
FOR UPDATE
```

Verify exact active subclass identity. Write exact post-cost SP. Require exactly
one affected row.

## Skill

```sql
SELECT skill_level
FROM character_skills
WHERE charId = ? AND skill_id = ? AND class_index = ?
FOR UPDATE
```

First level:

- require no contradictory row;
- guarded INSERT;
- exactly one affected row.

Upgrade:

- require exact previous level;
- guarded UPDATE from exact previous level;
- exactly one affected row.

Do not use an unconditional `REPLACE`.

## Item

```sql
SELECT owner_id, item_id, count, loc
FROM items
WHERE object_id = ?
FOR UPDATE
```

Verify:

- owner is exact Player;
- item ID exact;
- supported inventory location;
- durable count is compatible with exact runtime baseline.

Partial consumption uses guarded count UPDATE. Full consumption uses guarded
DELETE. Require exactly one affected row.

## Transaction

```text
connection.setAutoCommit(false)
locks/read/validation
item mutation
SP mutation
skill mutation
commit
```

Any exception/mismatch:

```text
rollback
fresh read proves baseline
```

Connection state is restored/closed safely.

## Fresh postcondition read

Use a new connection after commit. Do not reuse cached runtime objects as
durability evidence.
