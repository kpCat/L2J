# Goal 022 Checkpoint 1 — architecture

## Dependency direction

```text
Goal / acquisition RecipePlan
→ Economy quote and risk policy
→ durable operation + reservations
→ active canonical adapter OR background atomic adapter
→ exact observation
→ economy audit + Goal/acquisition/background reconciliation
```

Canonical RecipeManager and enchant service do not depend on Phantom packages.

## Truth owners

| Truth | Owner |
|---|---|
| recipe definition and stat use | RecipeData / RecipeManager |
| known recipe and skill | canonical Player or locked DB rows |
| active craft timing/result | RecipeManager |
| enchant scroll/support facts | EnchantItemData |
| active enchant mutation | packet-independent enchant service |
| background RNG | Background state |
| item/adena/vitals | canonical Player inventory or locked DB rows |
| operation identity/reservations | economy operation ledger |
| acquisition baseline/progress | Goal 021 acquisition state |
| economic terminal audit | economy audit row |

## Planned checkpoint boundary

C1 creates a participant-neutral ledger but executes only one-character
SELF_CRAFT and ITEM_ENCHANT.

C2 reuses that ledger for multiple participants and owns direct trade, private
stores and player manufacture.

## Conservation invariant

For each committed operation:

```text
after canonical resources
= before canonical resources
+ one exact canonical outcome
```

No retry may apply an outcome twice.

## Dispatch invariant

```text
durable RESERVED
→ durable DISPATCHING
→ canonical action starts
```

The destructive call never precedes the durable dispatch marker.

## Ambiguity invariant

When current facts match neither exact before nor one exact terminal outcome:

```text
operation = INCONSISTENT
profile economic action admission = fail stopped
```

No heuristic compensation and no blind redispatch.

## Disabled invariant

With Phantom World disabled, economy policy, operation tables and services are
not loaded or queried, and ordinary player craft/enchant/NPC commerce remains
unchanged.
