# Goal 021 Checkpoint 1 — acquisition/spoil

## Статус

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required parent: `d48dccb42dcfe5993f1c852e021086e498c0622d`
Branch: `feature/phantom-world`
Seed: `21002101`
Commit subject: `feat(phantoms): add acquisition planning and spoil chains`

## Summary

Goal 020 финально зафиксирован как `ACCEPT`; verifier 020c2 стал historical и
descendant-compatible. Реализован первый из двух checkpoint Goal 021:
bounded `acquire.item` planning/execution kernel, active spoil → existing Combat
kill → canonical sweep и background death-drop/spoil parity.

## Architecture decisions

- Game Knowledge, Topology и Progression остаются authority источниками.
- `acquisition.state` schema 1 ограничен 4096 bytes; progress выводится только
  из immutable baseline и canonical current item count.
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
- `phantom-acquisition-atomic-restart-test` — PASS (4/4).
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
