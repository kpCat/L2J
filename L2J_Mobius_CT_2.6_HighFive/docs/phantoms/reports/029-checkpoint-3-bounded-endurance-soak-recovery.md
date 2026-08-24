# Goal 029 Checkpoint 3 — bounded endurance soak and recovery

## Статус

- Delivery status: `BLOCKED`.
- Blocker: `BLOCKED_029CP3_RESOURCE_BOUNDARY_REDESIGN_REQUIRED`.
- Goal029 Checkpoint 1: `ACCEPT after Goal029A/Goal029B`.
- Goal029A: `ACCEPT after Goal029B`.
- Goal029B: `ACCEPT`.
- Goal029 Checkpoint 2: `ACCEPT`.
- Goal029 Checkpoint 3: `BLOCKED_029CP3_RESOURCE_BOUNDARY_REDESIGN_REQUIRED`.
- Goal029 overall: `IN_PROGRESS_BLOCKED_029CP3_RESOURCE_BOUNDARY_REDESIGN_REQUIRED`; Goal029 не принят самостоятельно.
- Required parent: exact `717932d7060b9c0ff17bb1e9f1dae31246740de6`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `yes`.
- Goal token usage at final report update: `371633`; elapsed goal time: `3733` seconds.
- `apply_patch` invocation count: `0`.

## Summary

Добавлен test-only CP3 suite с exact 10 000 durable SHELL profiles, real PopulationManager/Store/Repository, real PhantomScheduler production policy, real PhantomNavigationService production defaults, deterministic fake navigation backend/manual dispatcher, foreground `System.nanoTime` pulse loop и 25-секундными Hikari/JVM samples. Production Java не изменялся.

Полный 30-минутный target был выполнен. Pre/post population cases прошли. Main case завершил exact wall interval, шесть scheduler spikes и шесть navigation saturation/recovery cycles, но обязательный MariaDB gate получил `select=12,insert=0,update=0,delete=0` вместо exact zero. Gate не ослаблялся и maintenance traffic не вычитался.

Read-only owner audit подтвердил resource boundary: current `DatabaseFactory` фиксирует `minimumIdle=2`, `idleTimeout=300000` и `maxLifetime=600000`. При test pool maximum 4 minimum idle остаётся 2; за 30 минут Hikari заменяет idle connections на 10-минутной lifetime boundary. Совпадение +12 SELECT с этим lifecycle является обоснованной инфраструктурной причиной, а не Scheduler/Navigation DB path. TASK прямо запрещает менять `DatabaseFactory`/Hikari/config в CP3 и требует данный blocker token.

## Exact changed files

1. `build.xml` — fixed seed `29002903`, forked CP3 target из `dist/game`, `-Xmx4096m`, timeout `2700000`, no provisioning.
2. `docs/PHANTOM_BOTS_ROADMAP.md` — CP2 `ACCEPT`; CP3 и Goal029 отмечены prescribed resource-boundary blocker, Goal029 не `ACCEPT`.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomScaleEnduranceGoal029Checkpoint3Suite.java` — bounded CP3 environment/endurance suite.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — CP3 launcher mode.
5. `docs/phantoms/reports/029-checkpoint-3-bounded-endurance-soak-recovery.md` — этот отчёт.

Production Java, schema, migration, production/test DB config и другие хроники не изменялись. User-owned untracked task packages оставались read-only.

## Architecture and process decisions

- CP2 population fixture/probe pattern переиспользован без изменения CP2 semantics.
- CP1 manual Navigation dispatcher/fake backend pattern переиспользован; real GeoEngine для маршрутов не вызывался.
- Один общий `PhantomMetrics` обслуживал Scheduler и Navigation; второй production metrics/overload registry не создавался.
- Scheduler использовал production policy, scheduled driver disabled, foreground pulse 100 ms и real `pulse()`.
- Navigation сохраняла один service owner до cycle 6, затем clean stop с zero retained queue/workers/active/cache/completed/cooldown/progress assertions.
- Threads/Futures/Executors per profile отсутствуют; structural reflection checks сохранены.
- Production correction отсутствует. Scope не разрешает исправление обнаруженной `DatabaseFactory`/Hikari lifetime boundary.

## Guarded DB and fixture

- DB: exact `127.0.0.1:3308/l2jmobiush5_phantom_test`.
- Dedicated test user и current manifest guard: PASS.
- Schema aggregate SHA-256: `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.
- Production database `l2jmobiush5`: не использовалась; active production session guard не сработал.
- Admin credentials использовались только через environment одного PowerShell/Ant process, не печатались и не записывались.
- Seed: exact `10000` canonical `SHELL`; no `advanceCreation`, accounts, characters или Players.
- Seed diagnostic: `19106 ms`; DB delta вне measured windows `select=20000,insert=20000,update=0,delete=0`.
- Cleanup: exact owned `profiles=0,components=0,accounts=0,characters=0`.

## Pre/post 10k Population restart

Pre-soak:

- bootstrap DB delta: `select=40,insert=0,update=0,delete=0`;
- drain DB delta: `0/0/0/0`;
- ownership calls: exact `30000`;
- productive/total pulses: exact `469/469`;
- max operations per pulse: `64`;
- heap baseline/loaded/recovered: `1426440440 / 1440489336 / 1426494320` bytes;
- Hikari peak: `active=1,idle=2,total=2,awaiting=0`;
- after stop: `active=0,idle=2,total=2,awaiting=0`.

Post-soak:

- bootstrap DB delta: `select=40,insert=0,update=0,delete=0`;
- drain DB delta: `0/0/0/0`;
- ownership calls: exact `30000`;
- productive/total pulses: exact `469/469`;
- max operations per pulse: `64`;
- heap baseline/loaded/recovered: `1432595592 / 1446615352 / 1432598176` bytes;
- Hikari peak: `active=1,idle=2,total=2,awaiting=0`;
- after stop: `active=0,idle=2,total=2,awaiting=0`;
- recovered ratchet versus pre-soak: `6103856` bytes, ниже +64 MiB.
## 30-minute Scheduler/Navigation endurance

- Ant target total time: `30 minutes 54 seconds`.
- Main test-case elapsed: `1800460170500 ns` = `30 minutes 0.4601705 seconds`; test-case elapsed включает небольшой in-case setup вокруг exact measured interval.
- Кодовый wall gate `duration >= 30m && duration < 31m` был пройден до DB assertion.
- Exact internal `endurance.durationMillis` не был опубликован текущим run: DB assertion находился перед measurement-record block. После классификации blocker test-only suite переставлен так, чтобы будущий authorized rerun публиковал measurements до prescribed DB failure; повторный 30-минутный stress-run с уже доказанным неизбежным blocker не выполнялся.
- Шесть WARM spikes и шесть Navigation cycles были завершены: assertions `spikes.size()==6`, `navigationCycles.size()==6` и clean Navigation stop прошли до DB assertion.
- Каждый cycle в executed code требовал queue `256`, workers `2`, extra `QUEUE_BACKPRESSURE`, drain `0/0/0`, next admission `ACCEPTED`, cache `<=1024`, completed `<=10000`.
- Exact per-spike CRITICAL/recovery timings и per-cycle retained counts не опубликованы из-за того же раннего DB assertion; выдавать вымышленные числа запрещено.
- Scheduler/navigation MariaDB delta: exact observed `select=12,insert=0,update=0,delete=0`; required `0/0/0/0` — FAIL.
- Periodic 25-second Hikari/JVM samples выполнялись до конца wall loop. Каждый опубликованный-в-памяти sample прошёл inline gates Hikari `total<=4,active=0,awaiting=0`, heap `<=registered baseline+512 MiB`, live threads `<=baseline+4`.
- Aggregate sample count, six 5-minute minima, raw peak, pulse totals/overruns/max work, final settled heap/thread/GC находились после DB assertion и в текущем run не были вычислены/записаны. Поэтому эти gates честно считаются `NOT PROVEN`, а не PASS.
- `PhantomNavigationService` после cycle 6 прошёл zero retained structures до DB assertion.
- Scheduler cleanup и fixture cleanup были выполнены через `afterAll` после failure.

## Blocker classification

Current owner settings:

- `DatabaseFactory.initializePool`: `setMinimumIdle(determineMinimumIdle(4))` → exact `2`;
- `setIdleTimeout(300000)`;
- `setMaxLifetime(600000)`;
- pool MBean name: `L2JMobiusPool`;
- guarded test pool maximum: `4`.

На 30-минутном idle Scheduler/Navigation окне pool lifecycle пересекает 10-минутную connection lifetime boundary. Наблюдаемые `+12 SELECT` при zero DML и отсутствии DB calls в Scheduler/Navigation suite path согласуются с переоткрытием minimum-idle connections. Исправление потребовало бы менять `DatabaseFactory`/Hikari/config boundary или искусственно выключать pool, что TASK относит к отдельному redesign и запрещает в CP3.

Не применялись:

- вычитание maintenance counters;
- relaxed `<=12` gate;
- закрытие Hikari на время soak;
- изменение `.phantom-local/Database.test.ini`;
- изменение production `DatabaseFactory`;
- повторный 30-минутный run без возможности пройти exact-zero predecessor.

## Commands and results

Baseline Git inspection:

- `git rev-parse --show-toplevel` — Git root подтверждён;
- `git status --short --branch` — branch/upstream и user-owned untracked packages подтверждены;
- `git rev-parse HEAD` — exact required parent PASS;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`.

Development/execution:

1. `.phantom-local/apache-ant-1.10.17/bin/ant.bat compile-tests` — PASS, 2208 production и 112 test sources; только два JDK removal warnings для bounded `System.runFinalization` в CP2/CP3 heap settle.
2. Первая CP3 target invocation — FAIL до suite: test-only launcher mode не был записан первой exact-anchor попыткой; DB/bootstrap/fixture/soak не запускались.
3. После focused launcher wiring correction exact CP3 invocation с тремя `PHANTOM_DB_ADMIN_*` env values и target в одном PowerShell process — suite `2/3`: pre PASS, main FAIL exact DB `12/0/0/0`, post PASS; seed `29002903`; total `30 minutes 54 seconds`.
4. Final `.phantom-local/apache-ant-1.10.17/bin/ant.bat compile-tests` после blocker-reporting hardening — PASS, 2208 production и 112 test sources, total 17 s; те же два JDK removal warnings.
5. CP2 `4/4`: NOT RUN, потому что CP3 hard predecessor FAIL.
6. CP1 `6/6`: NOT RUN, потому что CP3 hard predecessor FAIL.
7. Exactly one final `jar`: NOT RUN, потому что CP3 hard predecessor FAIL.

Full verify, Goal016/028 aggregates, production DB, real geodata, Goal030 и повторный soak не запускались.

## Static, encoding and scope

- `apply_patch` invocation count: `0`.
- Изменения выполнялись exact-anchor UTF-8-no-BOM temp + atomic `Move-Item`.
- Temporary `*.goal029cp3.tmp` отсутствуют.
- UTF-8 BOM отсутствует; strict UTF-8 decode PASS.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- `git -c core.whitespace=cr-at-eol diff --check` — PASS; exact diff/scope/staging allowlist проверяются перед commit.
- Production Java/config/schema diff нулевой.
- User task packages не включаются в staging.

## Deviations, limitations and risks

- SUCCESS невозможен: exact-zero DB gate нарушен инфраструктурным pool lifecycle.
- Goal029 CP3 не получает `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal029 остаётся незавершённым.
- Текущий 30-минутный run не публикует detailed scheduler/JVM aggregates из-за раннего DB assertion; source исправлен только для future authorized evidence preservation, без relaxed gate.
- Отдельная задача redesign должна определить допустимый production-neutral способ сохранить Hikari наблюдаемым и активным при exact-zero MariaDB statement delta на 30-минутном idle окне.

## Git and delivery

TASK/Agents.md разрешают required baseline Git inspection, bounded exact diff/scope verification, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не используются.

Commit subject: `test(phantoms): expose endurance DB boundary`.

Commit SHA и push result указываются в финальном сообщении после ordinary commit/push; report-bearing commit не может самоссылочно содержать собственный SHA.

## Next step

Создать отдельную явно разрешённую corrective task для `DatabaseFactory`/Hikari 10-minute lifetime boundary и exact-zero endurance semantics. После independent acceptance corrective boundary повторить CP3 full 30-minute target, затем только при PASS выполнить CP2 `4/4`, CP1 `6/6` и exactly one final `jar`. Goal029 может получить `ACCEPT` только последующим independent review.