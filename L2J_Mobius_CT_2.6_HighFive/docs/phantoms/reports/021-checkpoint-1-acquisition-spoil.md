# Goal 021 Checkpoint 1 — acquisition/spoil

## Статус

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required parent: `d48dccb42dcfe5993f1c852e021086e498c0622d`
Branch: `feature/phantom-world`
Seed: `21002101`
Foundation subject: `feat(phantoms): add acquisition planning and spoil chains`
Safety completion subject: `fix(phantoms): complete acquisition eligibility and recovery`

## Summary

Goal 020 финально зафиксирован как `ACCEPT`; verifier 020c2 стал historical и
descendant-compatible. Реализован первый из двух checkpoint Goal 021:
bounded `acquire.item` planning/execution kernel, active spoil → existing Combat
kill → canonical sweep и background death-drop/spoil parity.

## Architecture decisions

- Game Knowledge, Topology и Progression остаются authority источниками.
- `acquisition.state` writer использует schema 2, legacy schema 1 читается; declared
  worst case остаётся меньше 4096 bytes, а progress выводится только из immutable
  baseline и canonical current item count.
- Recipe DAG ограничен и ничего не исполняет.
- Один Decision step сохраняет максимум один acquisition transition.
- Active acquisition переиспользует существующий Combat ownership/lifecycle.
- Background item/state/Goal/acquisition mutation выполняется одной существующей
  Background transaction с `VERIFY_PENDING` recovery и exact durable replay.
- Capacity проверяется до acquisition progress.
- Новых workers, schema и direct inventory mutation из acquisition package нет.

## Scope boundaries

- Checkpoint 2: manor и quest collection — `DEFERRED_CHECKPOINT_2`.
- Goal 022: craft/trade/private store/enchant execution отсутствует.
- Не изменялись `Player.java`, `Party.java`, skill handlers и quest handlers.
- Другие хроники не изменялись.
- Phantom World остаётся выключенным по умолчанию.

## DB и migrations

Schema/migrations не изменялись. Все DB-focused тесты использовали только
`l2jmobiush5_phantom_test`; рабочая DB не изменялась.

## Config/data

Добавлен strict acquisition XML. В progression data добавлены отсутствовавшие
rules `profession.spoil` и planning-only `profession.craft`; существующий
`profession.sweep` переиспользован. Craft execution отсутствует. Новых config
keys нет.

## Tests and commands

Focused development results:

- `phantom-acquisition-catalog-codec-test` — PASS.
- `phantom-acquisition-source-planner-test` — PASS.
- `phantom-acquisition-recipe-planning-test` — PASS.
- `phantom-acquisition-active-spoil-test` — PASS (3/3).
- `phantom-acquisition-background-parity-test` — PASS (4/4).
- `phantom-acquisition-atomic-restart-test` — PASS (foundation 4/4; safety
  completion 6/6).
- `phantom-acquisition-source-switching-test` — PASS.
- `phantom-acquisition-lifecycle-performance-smoke` — PASS (4/4).

Background parity использует временно повышенный test-only drop/spoil chance
multiplier для детерминированного положительного delta с seed `21002101` и
восстанавливает production config после capture; authority facts и модель не
подменяются.

## Performance

- 100k indexed source plans — PASS.
- 10k bounded recipe DAGs — PASS.
- 10k acquisition Decision advances — PASS.
- Service-owned workers: 0.
- Retained transition/external/navigation claims after stop: 0.

## Atomicity and recovery

Проверены fault points после profile/Goal/acquisition/background locks, после
canonical item write, после background/Goal/acquisition writes, перед commit и
после commit до публикации. Pre-commit результат all-or-none; post-commit unknown
восстанавливается и exact replay не дублирует item/progress/Goal.

## Deviations and limitations

- Goal использовал больше soft token usage, чем планировалось; hard file/scope и
  architectural bounds не расширялись.
- Полноценный pathfinding без geodata этим checkpoint не подтверждается.
- Независимое принятие checkpoint ещё не выполнено.

## Terminal verification

После freeze-гейтов production/data/test/build/verifier файлы не изменялись.

- Восемь focused routes: PASS; exact affected-набор из 12 целей: PASS.
- Verifier 020c2: `TASK020C2_VERIFIER_OK`; working verifier 021c1:
  `GOAL_021C1_VERIFIED`.
- Единственный final `phantom-acquisition-checkpoint1-test`: PASS, seed
  `21002101`.
- Единственный полный `ant verify`: PASS (`BUILD SUCCESSFUL`, 13 min 21 s).
- Единственный standalone `ant jar`: PASS (`BUILD SUCCESSFUL`, 16 s).
- Scope guard: 44 total, 23 production/data/config, 10 new production/data;
  лимиты 54/30/16 соблюдены, forbidden paths отсутствуют.
- Mojibake-маркеры в изменённых файлах проверены отдельно: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.
- Commit: этот атомарный implementation commit с subject
  `feat(phantoms): add acquisition planning and spoil chains`; точный SHA
  определяется после создания commit.
- Push и два byte-identical post-commit verifier 021c1 запускаются после
  фиксации этого отчёта; их SHA-256 и remote equality приводятся в финальном
  результате Goal без изменения frozen artifacts или второго commit.

## Next step

Независимое ревью Goal 021 Checkpoint 1; Checkpoint 2 не начинать до gate.

## Bounded safety completion

Safety completion выполнен как один direct ordinary child опубликованного foundation
`bf0cc37b2af7023f3709f635ae4350306b892597`, без переписывания истории.

- Exact Dwarf lineage rules используют canonical Spoil 254, Sweeper 42 и planning-only
  Create Item 172; runtime сохраняет фактически известный уровень skill. Spoil Crush
  348 не принимается как generic Spoil.
- Background eligibility читает не более восьми exact `character_skills` rows для
  active class index через read-only transaction boundary. `autoGetSkills` не
  используется как learned-skill ledger; eligibility повторно проверяется под
  locks до общей item/background/Goal/acquisition mutation.
- Persisted dispatch attempt ограничен catalog policy. `UNAVAILABLE`, `REJECTED`,
  restart с active exact cast, observed effect и terminal uncertainty не приводят
  к blind recast и освобождают external ownership.
- Kill phase использует persisted `COMBAT_PREPARED`; только exact существующая или
  успешно принятая session переводит state в `COMBAT_SUBMITTED`.
- Acquisition background operation identity v2 включает source ID и expected
  acquisition row version. Ordinary Goal 015 digest остался byte-identical.
- Cross-method ambiguity, topology/resource/switch/recipe evidence, отсутствие
  quest authority и очистка stale Goal source закреплены focused tests.

Safety completion scope перед freeze: 22 файла всего, 15 production/data, новых
production/data файлов 0. `Player.java`, `Party.java`, schema, skill/quest handlers,
manor/quest execution и Goal 022 execution не затронуты.

Focused safety gates: capability 1/1, learned eligibility 1/1, dispatch recovery
1/1, Combat recovery 1/1, operation identity 1/1, scoring evidence 1/1, recipe
planning 3/3, background atomic/restart 6/6, background parity 4/4, ordinary Goal
015 regression 1/1, active spoil 3/3 и lifecycle/performance 4/4 — PASS с seed
`21002101`. Verifier 020c2 и working verifier 021c1 прошли в PowerShell 5.1 и 7.6.3.

Результаты единственного completion final aggregate, одного additional full
`ant verify`, standalone `ant jar`, push и двух byte-identical accepted verifier
runs передаются в финальном handoff после freeze без изменения frozen artifacts.
