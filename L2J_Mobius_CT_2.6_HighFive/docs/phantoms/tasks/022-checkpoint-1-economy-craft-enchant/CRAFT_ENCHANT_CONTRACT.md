# Craft and enchant contract

## Self craft

Supported:

```text
crafter == target
known common or dwarven recipe
canonical craft skill
current recipe ingredients/stat use
current success and rare/masterwork rules
```

Deferred to C2:

```text
player manufacture
manufacture fee
private workshop
```

Active craft is owned by RecipeManager. Background craft is an exact projection
of the same current data and configuration.

## Enchant outcomes

```text
SUCCESS
SAFE_FAILURE
BLESSED_RESET
DESTROYED_WITH_CRYSTALS
ERROR
```

Scroll and optional support consumption is part of every non-ERROR attempt.

Background enchant requires an unequipped INVENTORY target. An equipped target
must materialize and use the canonical active service.

## Risk

An enchant intent is executable only when:

```text
target is exact
desired level is above current level
attempt and expense budgets remain
scroll/support are exact and allowed
ordinary destruction is explicitly allowed when applicable
replacement reserve policy is satisfied
no ambiguous previous attempt exists
```

## Goal 021 handoff

A craft operation consumes exactly the selected Goal 021 RecipePlan. It cannot
silently switch recipe alternatives. Ingredient drift causes replan before
dispatch.

Successful product observation updates the same acquire.item baseline. Failed
craft preserves consumed ingredients as a committed economic consequence.
