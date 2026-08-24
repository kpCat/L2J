# Goal 029D — driver-session attribution + retained-memory corrective

## Статус

- Delivery status: `SUCCESS`.
- Goal 029D: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 029C: `BLOCKER_CLOSED_PENDING_029D_INDEPENDENT_REVIEW`.
- Goal 029 Checkpoint 3: `IMPLEMENTED_PENDING_029D_INDEPENDENT_REVIEW`.
- Goal 029 Checkpoint 2: `ACCEPT`.
- Goal 029 overall: `IN_PROGRESS_PENDING_029D_INDEPENDENT_REVIEW`; Goal 029 не `ACCEPT`.
- Required parent: exact `b4b5837223ea2ed7d17e75d8de1b6d0cad0d0000`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- `occurred_context_compaction`: `no`.
- Goal token usage at pre-report snapshot: `330891`; elapsed goal time: `4942` seconds.
- `apply_patch` invocation count: `0`.

## Summary

Исправлены только test semantics CP3. General-log attribution теперь захватывает dedicated-user `Connect` и `Query` rows с `event_time`, `thread_id`, `command_type`, `user_host`, `argument`, связывает driver-shaped Query только с Connect того же `thread_id` внутри exact soak window и требует строго положительный delay не более 5 секунд. Допущены только существующие строгие no-table Connector/J `@@session`/system-variable SELECT shapes и два exact SET: `SET autocommit=1`, `SET character_set_results = NULL`. Generic SET allowlist отсутствует; каждый exact SET допускается не более одного раза на connection.

Raw heap samples с интервалом не более 30 секунд, 5-minute epoch minima и raw peak сохранены как наблюдения. Hard gates используют maximum committed heap, minimum headroom, live threads и bounded final retained recovery; periodic `System.gc()` во время soak не добавлялся. `-Xmx4096m`, workload и production owners не менялись.

Production Java/config/schema, `DatabaseFactory`, Hikari, Scheduler, Navigation и Population owners не изменены. Production DB не использовалась.

## Exact changed files

1. `test/java/org/l2jmobius/tests/phantoms/PhantomScaleEnduranceGoal029Checkpoint3Suite.java` — connection-scoped driver init attribution и corrected retained-memory gates.
2. `docs/PHANTOM_BOTS_ROADMAP.md` — предписанные статусы Goal029D/029C/CP3/CP2/Goal029.
3. `docs/phantoms/reports/029d-driver-session-attribution-retained-memory-corrective.md` — этот отчёт.

`build.xml`, launcher, production Java/config/schema и user-owned untracked task packages не изменялись.

## General-log lifecycle and SQL attribution

Финальный CP3 PASS:

- saved: `general_log=OFF`, `log_output=FILE`;
- enabled: `general_log=ON`, `log_output=TABLE`;
- exact server-time window: `2026-08-24 21:33:48.325875` — `2026-08-24 22:03:48.378353`;
- restored in `finally`: `general_log=OFF`, `log_output=FILE`;
- отдельная post-run read-only проверка: `general_log=OFF`, `log_output=FILE`;
- `mysql.general_log` не очищался: не выполнялись `TRUNCATE`/`DELETE`;
- credentials не печатались и не записывались в tracked artifacts; admin env удалялся в `finally` того же PowerShell-процесса.

Dedicated replacement Connect count: exact `6`, bound `<=6`.

Thread/time attribution:

- thread `136`, Connect `2026-08-24T18:41:49.537581Z`, four allowed statements after `0/1/1/2 ms`;
- thread `137`, Connect `2026-08-24T18:42:40.997536Z`, delays `0/0/0/0 ms`;
- thread `138`, Connect `2026-08-24T18:49:39.681743Z`, delays `0/0/0/0 ms`;
- thread `139`, Connect `2026-08-24T18:51:49.975930Z`, delays `0/0/0/0 ms`;
- thread `140`, Connect `2026-08-24T18:57:32.822921Z`, delays `0/0/0/0 ms`;
- thread `141`, Connect `2026-08-24T18:59:22.662575Z`, delays `0/0/0/0 ms`.

Все 24 statements имеют тот же `thread_id`, что и соответствующий Connect, находятся строго после Connect и значительно ниже лимита 5 секунд. Нулевые миллисекунды в отчёте являются округлением положительного microsecond delay.

Exact maintenance SQL/counts:

1. `SELECT @@session.transaction_read_only` — `6`.
2. `/* mysql-connector-j-9.5.0 (Revision: a7b3c94f50efbddb9f0dd69b3e0d1aaa25305cd6) */SELECT @@session.auto_increment_increment AS auto_increment_increment, @@character_set_client AS character_set_client, @@character_set_connection AS character_set_connection, @@character_set_results AS character_set_results, @@character_set_server AS character_set_server, @@collation_server AS collation_server, @@collation_connection AS collation_connection, @@init_connect AS init_connect, @@interactive_timeout AS interactive_timeout, @@license AS license, @@lower_case_table_names AS lower_case_table_names, @@max_allowed_packet AS max_allowed_packet, @@net_write_timeout AS net_write_timeout, @@performance_schema AS performance_schema, @@sql_mode AS sql_mode, @@system_time_zone AS system_time_zone, @@time_zone AS time_zone, @@transaction_isolation AS transaction_isolation, @@wait_timeout AS wait_timeout` — `6`.
3. `SET autocommit=1` — `6`.
4. `SET character_set_results = NULL` — `6`.

Maintenance SELECT: `12 <= 2*6`; maintenance SET: `12 <= 2*6`; каждый exact SET: `6 <= Connects`, по одному на connection. Application/table SQL: `0`; unattributed SQL: `0`; unclassified SQL: `0`.

Global scheduler/navigation counters: `Com_select +12` (наблюдение, ничего не вычиталось), `Com_insert +0`, `Com_update +0`, `Com_delete +0`.


## Full CP3 measurements

Target result: `3/3 PASS`; Ant total `31 minutes 1 second`. Main case elapsed `1800946653400 ns`. Exact measured scheduler/navigation duration `1800050 ms`: `>=30m` и `<31m`. Samples: `86`; maximum sample gap `25001 ms`.

Memory/JVM:

- registered baseline: used `1430650600`, committed `2489319424`, max `4294967296` bytes; live threads `9`;
- maximum committed: `2489319424` bytes, delta from registered baseline committed `0`, within `+256 MiB`;
- minimum heap headroom: `2226782488` bytes, above `512 MiB`;
- raw 5-minute epoch minima observations: `[1430650600, 1537605352, 1644560104, 1749417704, 1856372456, 1963327208]` bytes;
- raw heap peak observation: `2068184808` bytes;
- live thread peak: `11`, baseline `9`, delta `+2`, within `+4`;
- final settled after Scheduler/Navigation stop: used `1425670920`, committed `2489319424`, max `4294967296`, live threads `9`; used delta versus registered baseline `-4979680`, threads delta `0`;
- GC delta across endurance: count `+2`, time `+321 ms`;
- no periodic `System.gc()` during soak; existing bounded settle remained before/after owner lifecycles.

Hikari:

- soak peak: `active=0,idle=2,total=2,awaiting=0`;
- final: `active=0,idle=2,total=2,awaiting=0`;
- population peaks: `active=1,idle=2,total=2,awaiting=0`;
- after each population stop: `active=0,idle=2,total=2,awaiting=0`.

Pre-soak population restart:

- bootstrap DB: exact `select=40,insert=0,update=0,delete=0`;
- drain DB: exact `0/0/0/0`;
- ownership calls: `30000`;
- productive/total pulses: `469/469`;
- max operations per pulse: `64`;
- baseline/loaded/recovered heap: `1425306888 / 1439362752 / 1425358216` bytes.

Post-soak population restart:

- bootstrap DB: exact `select=40,insert=0,update=0,delete=0`;
- drain DB: exact `0/0/0/0`;
- ownership calls: `30000`;
- productive/total pulses: `469/469`;
- max operations per pulse: `64`;
- baseline/loaded/recovered heap: `1425716680 / 1439744896 / 1425720096` bytes;
- recovered ratchet versus pre: `361880` bytes, within `+64 MiB`.

Scheduler:

- registered base: `10000`, persistent state `BACKGROUND`;
- six WARM overload phases достигли `CRITICAL` и восстановились до `NORMAL`;
- recovery times: `11843, 11845, 11846, 11845, 11845, 11844 ms`, все `<=60s`;
- pulses started/completed: `18081/18081`;
- overruns/work failures/ready backpressure: `0/0/0`;
- work delivered: `1793728`;
- max work per pulse: `128`;
- structural maxima: `registered=10000,ready=9872,due=10000`;
- max pulse execution: `3893000 ns`;
- max scheduling lateness: `4805200 ns`.

Navigation, exact six saturation/recovery cycles:

1. queue/worker peak `256/2`, extra `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted, cache/completed `257/257`.
2. `256/2`, `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted, `514/514`.
3. `256/2`, `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted, `771/771`.
4. `256/2`, `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted, `1024/1028`.
5. `256/2`, `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted, `1024/1285`.
6. `256/2`, `QUEUE_BACKPRESSURE`, drained `0/0/0`, recovery accepted, `1024/1542`.

Final cleanup: `profiles=0,components=0,accounts=0,characters=0`; Scheduler, Navigation, Population and Hikari lifecycle assertions passed.


## Final regressions and build

1. CP3 final full target: `3/3 PASS`, exact duration gate passed.
2. CP2: `4/4 PASS`, total `56 seconds`.
3. CP1: `6/6 PASS`, total `19 seconds`.
4. Final `jar`: `BUILD SUCCESSFUL`, total `17 seconds`.
5. `jar` invocation count after CP3 PASS: exact `1`.

Exactly one final `jar` target built and copied `GameServer.jar` and `LoginServer.jar` into the working `dist/libs`. Full `verify`, Goal016/Goal028 aggregates, production DB, real geodata and Goal030 were not run.

## Diagnostics and deviations

- Первый full 30-minute 029D run завершил exact interval, restored general log и exact cleanup, но fail-closed получил `BLOCKED_029D_UNATTRIBUTED_DRIVER_STATEMENT`: initial predicate matched Query `user_host=user[user] @ ...`, но не MariaDB Connect `user_host=[user] @ ...`.
- Bounded read-only query по captured thread IDs `125–130` подтвердил ровно шесть Connect rows внутри первого soak window и их exact MariaDB row shape. Predicate исправлен test-only извлечением dedicated username из brackets для обоих command types; второй полный 30-minute run прошёл.
- Первый CP2 orchestration attempt не получил admin env и остановился в `before-all` с `BLOCKED_029CP2_ADMIN_STATUS_ENV_REQUIRED`, без выполнения cases; Hikari shutdown прошёл. Повтор в одном процессе с local admin env дал `4/4 PASS`.
- Первый вызов `ant compile-tests` не стартовал, поскольку `ant` отсутствовал в `PATH`. Использован существующий local Apache Ant `1.10.17` и JDK `25.0.4`; зависимости не менялись.
- Несколько edit-command anchor/parser mismatches завершились до target write; один output-wrapper error произошёл после успешной roadmap записи. Частично записанных target files не осталось.
- Production change не потребовался; `BLOCKED_029D_PRODUCTION_CHANGE_REQUIRED` не возник.

## Process, scope and encoding

- `apply_patch` invocation count: exact `0`; `BLOCKED_029D_FORBIDDEN_APPLY_PATCH_ATTEMPT` не возник.
- Все target edits выполнялись exact-anchor операциями в памяти, записью UTF-8 без BOM в same-directory temporary file и atomic `Move-Item`. Новый report собирался bounded chunks во временном UTF-8-no-BOM файле и atomically promoted.
- Temporary `*.goal029d.tmp` files отсутствуют после promotion.
- User-owned untracked task packages оставались read-only и не staging.
- Production Java/config/schema diff: exact zero, подтверждено final scope diff.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- `git diff --check`, полный diff и exact scope прошли; staged allowlist содержит exact три файла.
- Context compaction: `no`.
- Pre-report token snapshot: `330891`; финальное token usage сообщается пользователю после завершения goal.

## DB, migrations and configs

- Schema/migration changes: none.
- Production/test DB config changes: none.
- `DatabaseFactory`/Hikari changes: none.
- Working production database: not used.
- General-log server settings были transient test-only и восстановлены exact.
- SQL cleanup general-log table не выполнялся.

## Git and delivery

TASK/Agents.md разрешают обязательный bounded Git inspection, exact diff/scope verification, ordinary commit и push. История не переписывается; amend/rebase/reset/merge/force push не используются.

Preferred commit subject: `test(phantoms): correct endurance resource attribution`.

Commit SHA и push result указываются в финальном сообщении, поскольку report-bearing commit не может содержать собственный SHA.

## Risks and next step

Goal029D исправляет только test semantics и не доказывает production DB bug или retained-memory leak. Raw unswept growth остаётся observation; final settled memory, committed/headroom, thread, Hikari и post-restart gates прошли.

Следующий шаг — независимый review Goal029D. Goal029 остаётся `IN_PROGRESS_PENDING_029D_INDEPENDENT_REVIEW` и не `ACCEPT`.