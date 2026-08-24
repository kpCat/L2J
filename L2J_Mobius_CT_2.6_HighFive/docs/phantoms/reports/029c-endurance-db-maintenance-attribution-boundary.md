# Goal 029C — endurance DB maintenance attribution boundary

## Статус

- Delivery status: `BLOCKED`.
- Primary blocker: `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`.
- Goal 029C: `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`.
- Goal 029 Checkpoint 2: `ACCEPT` (не перезапускался после CP3 failure).
- Goal 029 Checkpoint 3: `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`.
- Goal 029 overall: `IN_PROGRESS_BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`; Goal 029 не `ACCEPT`.
- Required parent: exact `3824e74c8c8a07bb3b90b49a7dc17c1ed1dd06fb`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.
- Goal token usage at pre-delivery report update: `302290`; elapsed goal time: `4720` seconds.
- `apply_patch` invocation count: `0`.

## Summary

CP3 scheduler/navigation DB gate заменён test-only двухслойной attribution boundary. Suite сохраняет глобальные `Com_*` counters, включает `mysql.general_log` только на exact local admin endpoint, записывает server timestamps exact soak window, читает только SQL rows dedicated test user, классифицирует normalized statements и всегда восстанавливает исходные `general_log`/`log_output` в `finally`. Production `DatabaseFactory`, Hikari settings, config, schema и production Java не изменялись.

Полный exact 30-minute run завершил measured interval и опубликовал все measurements до assertions. Global `Com_select=12`, global DML `0`, application/table SQL `0`. Captured rows доказали maintenance SELECT boundary, но также обнаружили 12 Connector/J non-SELECT statements: `SET autocommit=1` и `SET character_set_results = NULL`, по 6 каждого. Контракт разрешает только SELECT maintenance shapes; эти rows являются unknown non-table statements и требуют prescribed blocker `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`.

Full run одновременно выявил сохранённые CP3 heap failures: last 5-minute epoch minimum превысил first post-warmup epoch minimum +128 MiB, а raw heap peak превысил baseline +512 MiB. Gate и budgets не ослаблялись. CP2, CP1 и final `jar` не запускались из-за hard predecessor failure.

## Exact changed files

1. `test/java/org/l2jmobius/tests/phantoms/PhantomScaleEnduranceGoal029Checkpoint3Suite.java` — test-only general-log lifecycle, exact window attribution, statement classification, measurements-before-assertions и prescribed blocker order.
2. `docs/PHANTOM_BOTS_ROADMAP.md` — Goal029C/CP3/Goal029 переведены в честный `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`; CP2 остаётся `ACCEPT`, Goal029 не принят.
3. `docs/phantoms/reports/029c-endurance-db-maintenance-attribution-boundary.md` — этот отчёт.

`build.xml` и `PhantomTestLauncher.java` не менялись: существующие CP3/CP2/CP1 targets и launcher wiring достаточны. Production Java/config/schema, другие хроники и user-owned untracked task packages не изменялись.

## Architecture and process decisions

- Переиспользован CP2/CP3 `AdminStatusProbe`: exact local URL guard, global status counters и production processlist fence.
- Admin URL для CP3 ужесточён до credential-free exact `jdbc:mysql://127.0.0.1:3308/`; значения credential env не выводятся и не записываются.
- General-log helper является nested test-only owner; arbitrary SQL API не добавлялся.
- Helper сохраняет оба server values, включает `TABLE`/`ON`, проверяет table access, записывает start/end server timestamps, читает `mysql.general_log` с exact dedicated username predicate и восстанавливает оба values в `finally`.
- `mysql.general_log` не очищался, не truncation/deletion не выполнялись.
- Normalized exact statement text сохраняется для отчёта; bounded known Connector/J leading comment удаляется только из classification view.
- Maintenance SELECT допускается только как comma-separated reads `@@[session.]variable` с optional `AS alias`, без `FROM/JOIN/INTO`, schema/table names, DML/DDL, backticks или multi-statement separator.
- Любой table/schema/DML/DDL row классифицируется как application; любой другой non-table statement — unclassified blocker.
- Measurement collection отделена от aggregate assertions: exact evidence теперь публикуется даже при resource/SQL failure; budgets и final gates остались прежними.

## General-log save/enable/restore proof

Exact full-run lifecycle measurement:

- saved: `general_log=OFF`, `log_output=FILE`;
- enabled: `general_log=ON`, `log_output=TABLE`;
- exact window start: `2026-08-24 19:36:08.375548`;
- exact window end: `2026-08-24 20:06:08.429297`;
- restored: `general_log=OFF`, `log_output=FILE`;
- restoration occurred in `finally` before result assertions;
- cleanup: `profiles=0,components=0,accounts=0,characters=0`.

No credential values were printed or tracked. Admin env names were set and removed in the same PowerShell process that invoked the CP3 Ant target.

## DB attribution

Global scheduler/navigation delta:

- `Com_select +12` (reported directly; nothing subtracted);
- `Com_insert +0`;
- `Com_update +0`;
- `Com_delete +0`.

Final deterministic classification of captured normalized exact rows:

Maintenance SELECT count: exact `12`, bound `<=12`.

1. `SELECT @@session.transaction_read_only` — count `6`.
2. `/* mysql-connector-j-9.5.0 (Revision: a7b3c94f50efbddb9f0dd69b3e0d1aaa25305cd6) */SELECT @@session.auto_increment_increment AS auto_increment_increment, @@character_set_client AS character_set_client, @@character_set_connection AS character_set_connection, @@character_set_results AS character_set_results, @@character_set_server AS character_set_server, @@collation_server AS collation_server, @@collation_connection AS collation_connection, @@init_connect AS init_connect, @@interactive_timeout AS interactive_timeout, @@license AS license, @@lower_case_table_names AS lower_case_table_names, @@max_allowed_packet AS max_allowed_packet, @@net_write_timeout AS net_write_timeout, @@performance_schema AS performance_schema, @@sql_mode AS sql_mode, @@system_time_zone AS system_time_zone, @@time_zone AS time_zone, @@transaction_isolation AS transaction_isolation, @@wait_timeout AS wait_timeout` — count `6`.

Application/table SQL:

- count `0`;
- exact texts `{}`.

Unknown non-table statements:

1. `SET autocommit=1` — count `6`.
2. `SET character_set_results = NULL` — count `6`.

Total unclassified: exact `12`; prescribed result: `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`.

The executed full-run report was produced immediately before the final generalized maintenance classifier correction: it recorded the long system-variable SELECT as unclassified (`maintenance=6`, `unclassified=18`). The final source deterministically reclassifies that SELECT-only system-variable shape as maintenance and leaves both non-SELECT `SET` shapes unclassified. Final source compiled successfully; another 30-minute run was not performed because the already captured exact rows prove the prescribed hard blocker and later gates are forbidden after CP3 failure.
## CP3 full gates and measurements

Final full target:

- Ant target total: `31 minutes 10 seconds`;
- main case elapsed: `1800982733400 ns`;
- exact measured duration: `1800051 ms` — exact 30m wall gate met and below 31m;
- samples: `86`;
- maximum sample gap: `25001 ms`.

Pre-soak real population restart:

- bootstrap DB: `select=40,insert=0,update=0,delete=0`;
- drain DB: `0/0/0/0`;
- ownership calls: `30000`;
- productive/total pulses: `469/469`;
- max operations: `64`;
- heap baseline/loaded/recovered: `1425973584 / 1440024648 / 1426024928` bytes;
- Hikari peak: `active=1,idle=2,total=2,awaiting=0`;
- after stop: `active=0,idle=2,total=2,awaiting=0`.

Post-soak real population restart:

- bootstrap DB: `select=40,insert=0,update=0,delete=0`;
- drain DB: `0/0/0/0`;
- ownership calls: `30000`;
- productive/total pulses: `469/469`;
- max operations: `64`;
- heap baseline/loaded/recovered: `1426224776 / 1440246192 / 1426226208` bytes;
- Hikari peak: `active=1,idle=2,total=2,awaiting=0`;
- after stop: `active=0,idle=2,total=2,awaiting=0`;
- recovered ratchet versus pre: `201280` bytes, within +64 MiB.

Scheduler:

- six CRITICAL→NORMAL spikes completed;
- recovery times: `11842, 11845, 11843, 11843, 11841, 11845 ms`, all below 60s;
- pulses started/completed: `18081/18081`;
- overruns/work failures/ready backpressure: `0/0/0`;
- work delivered: `1795296`;
- max work per pulse: `128`;
- structural maxima: `registered=10000,ready=9872,due=10000`;
- max pulse execution: `3694200 ns`;
- max scheduling lateness: `2738800 ns`.

Navigation:

- six saturation/recovery cycles completed;
- each cycle: queue peak `256`, worker peak `2`, extra request `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted;
- cache/completed after cycles: `257/257`, `514/514`, `771/771`, `1024/1028`, `1024/1285`, `1024/1542`;
- final Navigation retained structures: zero by stop assertions.

Hikari/JVM/GC:

- Hikari peak/final: `active=0,idle=2,total=2,awaiting=0`;
- registered baseline: `used=1431316176`, committed `2495610880`, max `4294967296`, live threads `9`;
- 5-minute epoch minima: `[1431316176, 1536173776, 1647322832, 1752180432, 1859135184, 1961895632]`;
- raw heap peak: `2070947536` bytes;
- final settled: `used=1426178608`, live threads `9`;
- GC delta: `count=2,millis=353`;
- live thread peak: `11`.

Preserved resource failures:

- epoch 5 minimum `1961895632` exceeded epoch 1 `1536173776` +128 MiB (`1670391504`) by `291504128` bytes;
- raw peak `2070947536` exceeded baseline +512 MiB (`1968187088`) by `102760448` bytes;
- final settled heap, Hikari and live-thread recovery were within their final bounds.

## Commands and results

Baseline Git inspection (explicitly required and permitted):

- `git rev-parse --show-toplevel` — root confirmed;
- `git status --short --branch` — branch/upstream and user-owned untracked packages confirmed;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse HEAD` — exact required parent PASS;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`.

Development and execution:

1. Focused `compile-tests` after initial helper — PASS, 2208 production +112 test sources; only two existing `System.runFinalization()` JDK removal warnings.
2. First CP3 attempt — suite `2/3`; pre/post PASS, main stopped after `1525436861900 ns` on premature raw-heap sample assertion; cleanup exact zero. This diagnosed measurement-before-assertion ordering and is not the final CP3 evidence.
3. Focused `compile-tests` after measurement-order correction — PASS.
4. Corrected full CP3 target with three admin env names and Ant in the same PowerShell process — full exact window completed; suite `2/3`; pre/post PASS; main FAIL after measurements on heap ratchet. Target total `31 minutes 10 seconds`.
5. Final focused `compile-tests` after generalized SELECT classifier and prescribed blocker order — PASS, 2208 +112, same two warnings.
6. CP2 `4/4` — `NOT RUN`: CP3 hard predecessor failed.
7. CP1 `6/6` — `NOT RUN`: CP3 hard predecessor failed.
8. Exactly one final `jar` — `NOT RUN`: CP3 hard predecessor failed; jar invocation count `0`.

Full `verify`, Goal016/Goal028 aggregates, production DB, real geodata and Goal030 were not run.

Two edit-command parser/anchor mismatches failed before file writes and were safely corrected. One polling-wrapper parser error did not affect the live Ant/JVM process. No partial target file remained.

## Static, encoding and scope

- `apply_patch` invocation count: `0`.
- All target edits used exact anchors, UTF-8 without BOM, same-directory temp file and atomic `Move-Item`.
- Temporary `*.goal029c.tmp` files must be absent at final scope check.
- Production Java/config/schema diff must be zero at final scope check.
- User task packages remain read-only and unstaged.
- Mojibake markers and escaped Cyrillic are checked separately before commit.
- `git diff --check`, exact diff/scope and staged allowlist are run before commit.

## DB, migrations and configs

- No schema/migration changes.
- No production or test DB config changes.
- No `DatabaseFactory`/Hikari change.
- Working production database was not used.
- General-log changes were transient test-only server settings and were restored exactly.

## Deviations, limitations and risks

- SUCCESS statuses requested for roadmap are intentionally not applied because CP3 did not pass.
- Final source classification is compiled but not followed by another 30-minute run; captured exact rows already prove the prescribed unclassified-driver blocker.
- Connector/J connection replacement issues non-SELECT session setup statements. Allowing them would require a new explicit contract; Goal029C forbids silently broadening maintenance from SELECT-only.
- Independent review must decide whether a follow-up task may classify bounded Connector/J `SET` session setup as maintenance and separately address/accept the reproduced heap growth gates. Production pool semantics remain untouched.

## Git and delivery

TASK/AGENTS.md authorize required Git inspection, exact diff/scope verification, ordinary commit and push. No amend/rebase/reset/squash/merge/force push is used.

Preferred commit subject: `test(phantoms): attribute endurance database maintenance`.

Commit SHA and push result are reported in the final message because the report-bearing commit cannot contain its own SHA.

## Next step

Independent review of `BLOCKED_029C_UNCLASSIFIED_DRIVER_STATEMENT`. A separately authorized corrective task must decide whether the two exact bounded Connector/J `SET` shapes can be admitted as driver maintenance while preserving fail-closed application/table/DML classification, and must address the independently reproduced CP3 heap epoch/raw-peak failures before CP3→CP2→CP1→jar may be rerun. Goal029 remains not accepted.