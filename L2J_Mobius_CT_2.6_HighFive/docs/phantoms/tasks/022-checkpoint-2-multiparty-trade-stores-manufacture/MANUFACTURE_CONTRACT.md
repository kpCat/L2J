# Player-manufacture contract

## Listing

```text
manufacturer object/profile
recipe list ID
price
recipe/skill authority hash
expiry
```

## Customer reservation

```text
exact ingredient counts
Adena fee
capacity
normal and rare output counts
```

## Manufacturer evidence

```text
known recipe
craft skill/class
exact listing and price
store type MANUFACTURE
not already crafting
```

## Observer

Events are immutable and include manufacturer/customer identities, fee,
ingredients, product, HP/MP and current EXP/SP consequences.

## Result

- normal or rare success commits exact product and fee;
- canonical failure commits exact ingredients/vitals/fee behavior;
- pre-effect abort releases reservations;
- partial/ambiguous result is INCONSISTENT;
- no second fee or product on retry/restart.

Execution is active only and owned by RecipeManager.
