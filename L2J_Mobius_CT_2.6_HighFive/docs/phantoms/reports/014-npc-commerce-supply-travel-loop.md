# Goal 014 — NPC commerce, supplies, travel and sell loop

## Status

`PARTIAL`

## Summary

Реализован bounded explicit loop: persisted typed goal → immutable authoritative
catalog → exact quote → durable `commerce.operation` receipt → одна canonical
buy/sell/teleport operation → conservative exact reconciliation.

Multisell остаётся query-only. Server core, packet/loaders, accepted progression,
Game Knowledge, config/schema, другие хроники и geodata не менялись.

## Catalog и выбранные fixtures

- buy offers: `28219`;
- multisell offers: `17165`;
- teleport routes: `1483`;
- mechanically classified supplies: `215`;
- selected buy: list `382`, item `1463`, vendor NPC `31380`, exact XML price
  `14`, unlimited, stackable supply;
- selected sell item: exact owned object/count item `1463`;
- selected teleport: NPC `30006`, list `NORMAL`, ordinal `0`, fee item `57`,
  fee count `18000`, instance `0`.

SHA-256: buy `2e0c50d714f0cb9c904fafd323f6d0bd5059ac2fb909a41de242661abc1f29fd`;
multisell `ac508af3fc1a6c6e8fe418ec425437368f172ee842476f652fc3a4ef8fae85fb`;
teleport `3a6db2b6d2d0958b7cae84f6703e1707aaccb8ec1c16f37bc624e095c97bc5bf`;
supply `4eb7d9c7a379558df23b9f3dccf90257317067c59681d65efe0405af0d7c370d`;
combined `1f8767f91e71b3a074fd8dfedb451be4739ac82e0b728e678a66840d243c18d0`.

## CP Potion 5591/5592

Current item/skill data:

- `5591`: skill `2166/1`, CP restore `50`, item reference price `200`,
  weight `25`, reuse `500`, stackable, Olympiad restricted;
- `5592`: skill `2166/2`, CP restore `200`, item reference price `500`,
  weight `100`, reuse `500`, stackable, Olympiad restricted.

Current buylist `9928` содержит `5591/5592` с XML price `0`, но не имеет NPC
assignment; поэтому он не выбран как исполняемый NPC buy.

Current multisell `500` назначен NPC
`31078–31091, 31168, 31169, 31692–31695, 31997, 31998`. Для `5591` exact
ingredient — item `5575` count `240`, для `5592` — item `5575` count `600`.
Именно current data подтверждает ID `5575`; название валюты не предполагалось.
Исполнение multisell отложено за пределы Goal 014.

## Architecture decisions

- Catalog строится один раз, parser XXE-safe, parsed IDs сверяются с текущими
  loaders, `ItemData` и `NpcData`; query page ограничена `256`.
- Quote и каждый side effect повторно валидируют exact actor/NPC/list/route,
  range, instance, price/fee/tax, budget, load/capacity и item ownership.
- Buy поддерживает только unlimited product; nonzero castle treasury side effect
  отклоняется как `CASTLE_TREASURY_UNSUPPORTED`.
- Sell поддерживает один object/count; refund и zero-sell-price semantics
  отклоняются.
- Teleport поддерживает одну NORMAL route и использует injected clock для
  discount rules.
- Decision candidates принимают только explicit persisted sources, создают не
  более одного mutating step и не подключают skill-learning action.
- Commerce service не создаёт worker, thread, scan или unbounded retry.

## Writer audit

Reachable canonical writers используют обычные `Player`/`PlayerInventory`/
`Item` APIs. Inventory/adena/position также могут менять reward/loot/trade/mail,
autosave, teleport/event и другие server paths вне commerce `ActionLease`.
Java monitor/striped lock этого service не является общей блокировкой этих
writers.

Поэтому cross-server atomicity не заявляется. Receipt сохраняет `COMMITTING` до
первого side effect, а затем сравнивает exact before/partial/after facts.
Посторонняя same-item/adena/position delta даёт durable `INCONSISTENT` без
replay, guessed compensation или следующей commerce mutation.

Authoritative loaders сохраняют своё штатное error handling. Runtime
`RuntimeException` преобразуется в typed retry только до доказанной mutation;
неоднозначный durable state не проглатывается и fail-stops profile commerce.

## Receipt matrix

| Restart/state | Exact facts | Result |
|---|---|---|
| PREPARED | before | один переход в COMMITTING и resume |
| COMMITTING | before | один разрешённый resume |
| COMMITTING | first effect | только отсутствующий второй effect |
| COMMITTING | after | COMMITTED/idempotent success |
| COMMITTED | after | same-key idempotent success |
| любой | mixed/third-party delta | INCONSISTENT, без replay |
| terminal old revision | новый revision | новая операция только после safe terminal |

Payload integration test: `217` bytes при лимите `4096`.

## Tests and performance

Seed: `140014`. Database: только `l2jmobiush5_phantom_test`.

- `commerce-catalog`: `6/6`;
- `commerce-supply`: `5/5`;
- `commerce-quote`: `4/4`;
- `commerce-receipt`: `7/7`;
- `commerce-decision`: `5/5`;
- `commerce-server-integration`: `2/2`;
- `commerce-performance`: `3/3`;
- aggregated focused final: `32/32`, `BUILD SUCCESSFUL`, `1m22s`;
- 100k static queries: `34,384,600 ns`;
- 10k receipt reconciliations: `8,309,900 ns`;
- deterministic rebuild hash совпал; workers/leaks: `0`.

Оба cumulative `ant verify` выполнили runtime/DB/scenario/performance gates.
Первый остановил legacy Goal 013B allowlist; разрешённый repeat — различие `/`
и `\` в новом verifier. Path normalization исправлен; третий run запрещён.

## READ_SET and efficiency telemetry

Initial READ_SET: 12 task-defined entries (13 physical files, поскольку entry 1
явно объединяет TASK и efficiency standard): TASK + standard; roadmap только
Goal 013B/014; wiring ranges `PhantomSystem`; четыре decision records; component
methods `PhantomProfileRepository`; bounded buy/sell/multisell packet paths;
bounded `TeleportHolder` fee path.

Additional exact files: ровно 5:

1. `BuyListData` — product/list/NPC enumeration;
2. `MultisellData` — current list flags/entry semantics;
3. `TeleporterData` — holder/list/location parity;
4. `PlayerInventory` — capacity/adena canonical mutations;
5. `Item` — exact object/count/sellability writers.

Repository searches: `6` до первой patch; после — только targeted symbol,
failure и current-data evidence searches. Старые Goal packages и большие server
files полностью не перечитывались.

Focused final: `1` green aggregated run; affected modes запускались отдельно.
Full `ant verify`: `2` attempts (initial + maximum repeat), оба aggregate
failures описаны выше. Standalone `ant jar`: `1`, green. Final verifier:
ровно `2` runs выполняются против этого frozen report с byte comparison.

Usage snapshot: `622320` tokens, `3923s`; превышение ориентира `500k` вызвано
двумя cumulative verify (~13m34s, десятки historical gates), четырьмя
authoritative-data headless initializations и сохранением полных логов. Логи:
`.phantom-local/logs/014/`.

Intermediate failures: два shell timeouts без test result; ranged item/skill
filenames и nullable `ItemTemplate.getSkills()` потребовали точечных fixes;
current NPC data заменил type `Gatekeeper` на `Teleporter`; cumulative verify
остановил legacy allowlist; initial verifier attempts исправили static warnings.

## Changed artifacts

Production: `phantoms/commerce/**`, `PhantomSystem.java`. Tests/build/tools:
`PhantomCommerceSuite.java`, `PhantomTestLauncher.java`, `build.xml`,
`verify-task-014.ps1`. Docs: contract, review, roadmap, report; task package и
efficiency standard добавляются без архитектурного расширения.
Bounded exception для количества файлов необходим из-за единого task-mandated
artifact family: catalog, receipt/service/backend, decision wiring, focused
tests, verifier и обязательные docs. Независимые подсистемы не добавлялись.

## DB, config, limitations and risk

- DB/schema/migrations: без изменений; production DB не использовалась.
- Config: без изменений; disabled mode не создаёт commerce objects/DB/log/thread.
- Ограничения: no limited stock, castle treasury, refund/zero-price sell,
  multisell execution, private stores, crafting, enchant или tactical potion use.
- Риск: ordinary external writers не участвуют в общей транзакции; policy —
  conservative durable fail-stop, а не optimistic replay.

## Git

- branch: `feature/phantom-world`;
- required parent: `e9b98a243a68a710425a062155b9197ee6692b17`;
- commit: этот единственный ordinary Goal 014 commit; SHA сообщается после
  создания;
- subject: `feat(phantoms): add npc commerce supply and travel loop`;
- push: результат фиксируется во внешнем final handoff после immutable commit.
