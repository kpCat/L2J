# Goal 029A — production materialization cap proof

## Status

- Delivery status: `BLOCKED`.
- Blocker: `BLOCKED_029A_GUARDED_MATERIALIZATION_TEST_DB_UNAVAILABLE`.
- Goal 029A: blocked before `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` because the mandatory guarded production target did not start its cases.
- Goal 029 CP1: `CHANGES_REQUIRED`; the corrective source/wiring bridge is implemented locally, but Goal 029A cannot advance it to review status without the guarded target.
- Goal 029 overall: `IN_PROGRESS`.
- Required parent: exact `7b34d614a8c2991f6a58abb860a8b69c29bcd7ba`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Scenario 04 больше не использует local `MaterializationSnapshotFixture`: fixture и его imports удалены полностью, replacement fake не создан. Scenario 04 теперь является bounded source/wiring bridge к существующим production/config/test sources.

Focused CP1 прошёл `6/6`, но обязательный `phantom-production-materialization-test` был остановлен current guard в `before-all`: schema manifest признан stale. Поэтому whole target не PASS, cases 05/12/13 не исполнялись, SUCCESS не заявляется. Guard/schema manifest не ослаблялись и не изменялись; production DB не использовалась.

## Exact changed files

1. `test/java/org/l2jmobius/tests/phantoms/PhantomScaleEnvelopeGoal029Checkpoint1Suite.java` — удалён fake fixture, Scenario 04 заменён bounded production source/wiring bridge; pure assessment использует прямой empty immutable production snapshot, а не behavioral fixture.
2. `docs/phantoms/reports/029a-production-materialization-cap-proof.md` — этот BLOCKED-отчёт.

Production Java, config, schema, roadmap и build не изменялись. User-owned untracked task packages оставались read-only.

## No-fake and production wiring proof

Focused Scenario 04 читает bounded exact sources и проверяет:

- `PhantomPlayersConfig.DEFAULT_MAX_MATERIALIZED_PHANTOMS = 32`;
- `PhantomSystem` передаёт `_settings.maxMaterializedPhantoms()` в real `PhantomMaterializationService`;
- real service создаёт `new Semaphore(maximumMaterialized, true)`;
- admission использует `_permits.tryAcquire()` и возвращает `CAPACITY_REACHED` при отказе;
- canonical `releaseStoredEntry(Entry)` требует `State.STORED`, снимает held flag и вызывает `_permits.release()`;
- existing production suite регистрирует exact cases 05/12/13;
- bounded `service(int capacity)` factory region делегирует capacity и создаёт real `PhantomMaterializationService` с тем же capacity.

`MaterializationSnapshotFixture` отсутствует во всём изменённом CP1 suite. Reflection/custom shell harness/replacement fake не добавлялись.

## Guarded DB identity

Использован только current guarded config/manifest:

- host: `127.0.0.1`;
- port: `3308`;
- database: `l2jmobiush5_phantom_test`;
- dedicated login: `l2j_phantom_test`;
- config: `.phantom-local/Database.test.ini`;
- manifest: `.phantom-local/schema-manifest.properties`.

Credentials не выдумывались и в отчёт не копировались. Production database `l2jmobiush5` запрещена и не использовалась. Guard и schema manifest не менялись и не ослаблялись.

## Commands and test results

Baseline read-only Git:

- `git status --short --branch` — exact branch/upstream и user-owned untracked packages подтверждены;
- `git rev-parse HEAD` — exact required parent PASS;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`.

Focused CP1:

1. `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-scale-envelope-goal029cp1-test` — first run `5/6`; единственный fail был слишком узкий non-production end anchor source bridge.
2. После минимальной correction только assertion anchor тот же command — PASS `6/6`, seed `29002901`; Scenario 04 `PASS`.

Mandatory guarded production materialization:

- `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-production-materialization-test` — FAIL before suite cases;
- `before-all`: `PhantomTestConfigurationException: Phantom test schema manifest is stale`;
- whole target: FAIL, total reported `2`, passed `0`, failed `2` (before/after lifecycle failures);
- case 05 `cap-release-and-readmission`: NOT RUN;
- case 12 `action-timeout-retains-cap-and-retries`: NOT RUN;
- case 13 `operation-failure-retains-and-retries`: NOT RUN.

По hard stop дальнейшее DB provisioning, fake substitute и final `jar` не выполнялись. Ровно один jar gate: NOT RUN because its required predecessor failed.

## DB, config, schema, architecture and performance

DB writes из test cases не начинались: suite остановилась на manifest freshness gate до `beforeAll` initialization. Production/config/schema/roadmap/build diffs отсутствуют. Performance measurements отсутствуют и не требовались для blocker delivery.

## Editing, encoding and scope

- `apply_patch` не вызывался;
- suite и report изменены exact-anchor / UTF-8-no-BOM temp + same-directory atomic move;
- temporary Goal029A files отсутствуют;
- mojibake-маркеры в изменённых файлах проверяются отдельным final gate;
- escaped Cyrillic в изменённых файлах проверяются отдельным final gate;
- Git diff/scope/diff-check фиксируются после создания отчёта.

## Deviations, limitations and risks

Mandatory cases 05/12/13 и whole guarded target не доказаны на current parent из-за stale schema manifest. Ослабление freshness guard или самостоятельное provisioning вышло бы за narrow task и нарушило явный STOP. До восстановления current guarded allowlisted test DB Goal 029A не может получить SUCCESS и independent-review statuses.

## Git and delivery

TASK разрешает bounded Git inspection, exact allowlist staging, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не используются. Commit subject: `test(phantoms): prove production materialization cap`.

Commit SHA и push result указываются в финальном сообщении после ordinary commit/push; SHA не может быть самоссылочно записан в тот же report-bearing commit.

## Next step

Отдельно восстановить/reprovision current allowlisted Phantom test DB так, чтобы schema manifest соответствовал exact current schema, не ослабляя guard. Затем повторить Goal 029A с required whole target PASS и exact cases 05/12/13 PASS; только после этого возможны статусы `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` / `CHANGES_REQUIRED_PENDING_029A_INDEPENDENT_REVIEW`.