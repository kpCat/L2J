# Goal 015 production loot disposition unblock — review evidence

## Review status

`PENDING_INDEPENDENT_REVIEW`

Этот файл фиксирует evidence для внешнего reviewer и не принимает Goal 015
самостоятельно.

## Graph and scope

- Branch: `feature/phantom-world`.
- Required parent: `32be3bbc320bc3a054aab8c5d39001910f35e4b8`.
- Expected subject: `fix(phantoms): support ground-loss production drops`.
- Expected graph: один ordinary direct child commit.
- Goal 015A/015B не создавались.
- Player, Attackable, Item, Inventory, Player.ini, topology, datapack, geodata,
  loaders, schema, другие хроники и Goal 016/017/025 не изменялись.

## Production evidence

- Exact pair: `22859@giran.farming.22859`.
- Supported production pair count: `1`.
- Shipped policy: `AutoLootHerbs=False`, `AutoLoot=False`,
  `AutoLootSlotLimit=True`, `AutoLootItemIds=0`.
- Ordinary drops: `ACQUIRE`.
- Immediate/time-limited drops: `LEAVE_ON_GROUND`.
- Ground-loss IDs: `8600–8614`, `10655–10657`, `13028`.
- Ground losses участвуют в canonical grouped/ungrouped RNG и occurrence
  budgets, но не создают inventory/effect/timer/object-ID mutation.
- `LOOT_POLICY_V1` входит в authority hash; config drift и auto-loot paths
  доказаны negative controls.
- Focused seed: `15001502`.
- Real Player batch: one successful encounter, atomic transaction, exact
  EXP/SP/HP/MP/acquired deltas, receipt/RNG/hash.
- Exact duplicate: `IDEMPOTENT`, без reroll/regrant/reservation.
- Materialization/dematerialization/reload: byte-conservation.

## Verification evidence

- Новый focused mode: PASS, 3/3.
- Все 13 historical Goal 015 modes: PASS.
- Static verifier, final focused aggregate, `ant verify`, standalone `ant jar`
  и два post-commit byte-identical verifier run должны быть подтверждены в
  финальном handoff.

## Reviewer decision

Независимый reviewer должен отдельно проверить graph/scope, disposition/RNG
semantics, loot-policy hash drift, real transaction evidence и conservation.
До внешнего решения статус остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
