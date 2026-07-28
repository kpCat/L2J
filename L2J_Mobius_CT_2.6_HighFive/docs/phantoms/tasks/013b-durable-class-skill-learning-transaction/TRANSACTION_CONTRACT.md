# Durable class-skill transaction contract

## Authority

Runtime state is owned by the exact materialized `Player`. Persistent state is
owned by current High Five MariaDB tables.

A successful operation requires both to agree.

## Transaction identity

```text
characterObjectId
classIndex
activeClassId
skillId
previousSkillLevel
targetSkillLevel
spBefore
spCost
requiredItemObjectId?
requiredItemId?
requiredItemCount?
```

No display name participates.

## Durable rows

```text
classIndex 0:
  characters(charId).sp
  character_skills(charId, skill_id, class_index=0)

classIndex >0:
  character_subclasses(charId, class_index).sp
  character_skills(charId, skill_id, class_index)

optional item:
  items(object_id, owner_id, item_id, count, loc)
```

## Atomicity

All durable cost and skill mutations commit or roll back together on one
connection.

Runtime is not mutated until commit.

## Exact item support

Goal 013B supports:

- no required item; or
- one distinct item ID satisfied by one exact owned item object.

Unsupported shapes fail before transaction:

- several distinct item IDs;
- required count split over several item objects;
- foreign owner;
- wrong location;
- count drift.

## Runtime apply

After commit:

```text
exact inventory object decrement/remove
runtime SP set
Player.addSkill(skill, false)
shortcut update
```

No persistent call is delegated to APIs that swallow database exceptions.

## Success

SUCCESS requires a fresh durable read and a fresh runtime read proving the same
expected state.

## Failure

Pre-commit failure:

```text
rollback
runtime unchanged
event absent
```

Post-commit runtime invariant failure:

```text
no compensation
no false SUCCESS
service fail-stop
durable DB remains authoritative for reload
```

## Event

`OnPlayerSkillLearn` occurs only after successful durable and runtime
postconditions. Repeated idempotent request does not emit it again.
