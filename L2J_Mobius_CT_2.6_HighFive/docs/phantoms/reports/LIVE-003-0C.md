# LIVE-003-0C — recoverable historical catch-up

Дата: 23.09.2026. Ветка: `feature/phantom-world`. Обязательный исходный tracked HEAD: `dea0242d71f8d4e664c2a18e6a1fd8f7c91221db`. Финальный implementation commit: `0ae81885f29eca8396ba0dbcfc5897148e3212d0`. Этот отчёт и checkpoint фиксируются отдельным exact-path documentation commit; его SHA приведён в итоговой передаче.

**CODE_STATUS=GREEN; RUNTIME_STATUS=GREEN для recoverable catch-up.** Нативный визуальный `.phantomstatus` через клиент в этом коротком окне не получен, поэтому ACTIVE/world materialization остаётся отдельным PENDING gate. LIVE-002 и остальные LIVE этапы не запускались. `planner.target_or_route.absent` остаётся явным topology block.

## Read-only evidence текущей игровой БД

Все прямые SQL-запросы к `l2jmobiush5_localplay3` были только `SELECT`. До работы сделан фактический resnapshot, а непосредственно перед и после runtime smoke — повторные SELECT-снимки binary components. Raw TSV и локальный parser: `.phantom-local/logs/LIVE-003-0C/play-*-{before,pre-smoke,smoke-1,post-smoke}.tsv` и `summarize_snapshot.py`. Начальные before/after снимки после RED теста были побайтно равны. Временный `smoke-1` снимок показывает, что изменения начались именно после доставки JAR.

| Состояние / причина | До smoke | Во время smoke | После STOP |
|---|---:|---:|---:|
| Catch-up COMPLETE | 546 | 863 | 780 |
| Catch-up RUNNING | 0 | 171 | 300 |
| Catch-up FAILED_REPLAN_REQUIRED | 734 | 246 | 200 |
| `catchup.authority_hash_or_generation_stale` | 301 | 37 | 7 |
| старый `model.object_cap` | 258 | 25 | 3 |
| явный `model.object_cap_indivisible` | 0 | 2 | 4 |
| `transaction.item_conflict` | 5 | 0 | 0 |
| `planner.target_or_route.absent` | 170 | 182 | 186 |
| Ecology initial complete / pending | 425 / 855 | 425 / 855 | 426 / 854 |
| Ecology caught up to now / behind | 0 / 1280 | 5 / 1275 | 6 / 1274 |
| Phantom profiles / linked characters | 1280 / 1280 | 1280 / 1280 | 1280 / 1280 |
| Characters / distinct character accounts | 1281 / 1281 | 1281 / 1281 | 1281 / 1281 |

COMPLETE и RUNNING являются моментными статусами: профиль может завершить один запрос и войти в следующий. Поэтому промежуточные 863 COMPLETE и итоговые 780 COMPLETE не означают потерю профилей. В начале все 1280 phantom characters были level 1; итоговая read-only гистограмма: level 1=1, 2=344, 3=847, 4=85, 5=3. Это естественное продвижение того же набора 1280 привязанных персонажей. Число `failures=166209` из прежнего `EVIDENCE.md` — накопительный счётчик, не число FAILED profiles. Новый native `.phantomstatus` во время автоматического smoke не вызывался; отсутствие telemetry spin доказано focused ecology test, а не выведено из этого старого счётчика.

## Семантика и scope

- `PhantomHistoricalBackgroundService` возобновляет stale RUNNING/FAILED из сохранённого cursor/seed/request lineage. Если hashes canonical background state устарели, существующий historical materialization/store recapture обновляет их через штатный admission; затем существующий planner строит текущий план, а `CatchupStore.replacePlan` атомарно публикует goal и catch-up. При отсутствии достоверного state/goal переход закрывается bounded reason. Уже закоммиченный интервал не переигрывается.
- `PhantomBackgroundCatchupState` хранит новые authority hashes при renewal. Legacy `transaction.item_conflict` может повторить тот же интервал с прежним cursor; старый `model.object_cap` получает явное `model.object_cap_indivisible` только если нет ни одной безопасной мутации. Частичный bounded prefix сохраняется штатной транзакцией и продвигает cursor без повторного reward. Safety constants 16/8 не менялись.
- `PhantomBackgroundTransaction` различает временный failed reservation claim (`ITEM_BUSY`), устаревший expected seed count (`ITEM_EXPECTED_COUNT_STALE`) и настоящий canonical `ITEM_CONFLICT`. Первые два идут через RETRY, настоящий mismatch остаётся INCONSISTENT; atomic predicates и locks сохранены.
- `PhantomPopulationEcologyService` повторно проверяет FAILED через общий pulse с интервалом 256, а RETRY ограничивает экспоненциальным backoff до 256 pulses. Одинаковый failure reason учитывается один раз до изменения причины; завершение идёт через прежний `completeRequest()` и permission edge. Нет per-profile worker/thread/timer и нового unbounded scan.
- `planner.target_or_route.absent` не получает fake route, anchor или teleport. Без фактического topology cursor/reward стоят; после обновления generation тот же durable профиль может войти в штатный planner. До LIVE-002 topology-dependent profiles остаются BLOCKED.

Изменены шесть существующих production-файлов `PhantomBackgroundCatchupState.java`, `PhantomBackgroundModel.java`, `PhantomBackgroundService.java`, `PhantomBackgroundTransaction.java`, `PhantomHistoricalBackgroundService.java`, `PhantomPopulationEcologyService.java`; расширены две существующие suites `PhantomHistoricalBackgroundGoal033ASuite.java` и `PhantomPopulationEcologyGoal033Suite.java`. Ещё изменены только этот отчёт и LIVE-003-0C section в `STATE.md`. Три предсуществующих dirty source/test файла, прочие user dirty/untracked файлы не staged. JAR собран в общей рабочей копии и включает предсуществующее содержимое dirty `PhantomMaterializationService.java`; exact commit касается только перечисленных восьми code/test файлов.

## RED → GREEN и guard

RED-фикстуры отдельно показали старый fence для stale recovery, неизменное topology telemetry, отсутствие object-cap/transaction classification, старый item conflict и retry spin. Логи `red-*.log` сохранены; первый `red-stale.log` остановился на sandbox dependency ACL, затем `red-stale-escalated.log` дал поведенческий RED. Две временные диагностические вставки в предсуществующий `PhantomMaterializationService.java` удалены; его пользовательский diff не staged. Между попытками case 02 иногда возвращал `MATERIALIZATION_FAILED_CLEAN`; заключительный Goal033A прошёл 10/10. Это intermittent pre-existing test risk, не объявленный исправленным данным task.

Финальный `ant phantom-historical-background-goal033a-test phantom-population-ecology-goal033-test phantom-background-model-test phantom-background-transaction-test phantom-background-materialization-abort-test phantom-background-quiescence-test` — GREEN: 10/10, 11/11, 7/7, 7/7, 3/3, 2/2. Отдельный `ant phantom-population-ecology-production-goal033-test` — GREEN 2/2. Заключительные 7 focused targets: 42 assertions, 0 failures. Логи: `final-focused.log`, `final-production-composed.log`. Development cadence: 9 RED logs, 9 промежуточных GREEN logs и 2 диагностических запуска; full `ant verify` runs=0, `ant jar` runs=1.

Mutating integration использовала только guard `.phantom-local/Database.test.ini`: `l2jmobiush5_phantom_test`, user `l2j_phantom_test`, max connections=4, `TestDatabaseConnections=false`, `BackupDatabase=false`; до тестов SELECT: 0 phantom profiles / 100 characters. Existing `PhantomTestDatabaseGuard` проверял launcher. Play DB не была target тестов и не получала прямых UPDATE/DELETE/DDL.

## JAR, runtime, сохранность

`ant jar` после GREEN собрал `C:\Users\ZBook\L2J_Mobius\build\dist\libs\GameServer.jar`; SHA-256 `0E3363F5302B556D056D27DD540238E7D079239E96D381A60F853D57217470BD`. Штатный `Check-LocalPlay.ps1` до delivery подтвердил STOPPED. Прежний только `GameServer.jar` сохранён в `.phantom-local/backups/LIVE-003-0C-20260923/GameServer.jar`, SHA-256 `93135D8AB1C77CE913477C5A9A5B1EADD208C28CFC07CA5BC066B63AE79C570E`. Доставленный `artifacts/local-play/runtime/libs/GameServer.jar` совпал со сборкой по SHA. Login JAR и runtime scripts/data/config не заменялись.

`Start-LocalPlay.ps1 -Background -LoginTimeoutSeconds 60 -GameTimeoutSeconds 600` поднял owned Login PID 31076 и Game PID 33476; CHECK показал их ownership портов 2106/9014/7777. После короткого bounded smoke `Stop-LocalPlay.ps1` остановил оба процесса. Финальный CHECK: LoginServer=STOPPED(pid=0), GameServer=STOPPED(pid=0), staleRecord=False, Port2106=False, Port9014=False, Port7777=False. Логи `runtime-*.log`. Reset/reseed, изменение rates/population/heap/schedules и глобальный taskkill не выполнялись.

## Read scope, searches и git

Начальный read set: `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md`, bounded `ROADMAP.md` и `STATE.md`, восемь указанных task production-файлов и две suites; также корневой README. Отдельные `AGENTS.md` и code-map не найдены. Пять дополнительных исходных файлов в лимите TASK: `PhantomBackgroundCatchupStateCodec.java` (binary snapshot), `PhantomPopulationEcologyStateCodec.java` и `PhantomPopulationEcologyState.java` (ecology cursor), `PhantomEconomyConflictPort.java` (reservation), `PhantomTestDatabaseGuard.java` (test DB).

По отдельным разрешениям пользователя точечно прочитаны `PhantomMaterializationService.java` (case 02), `PhantomBackgroundState.java` (hash invariant), `PhantomEconomyReservationService.java` и `PhantomEconomySuite.java` (reservation fixture), `PhantomBackgroundAuthority.java` и `L2jPhantomBackgroundAuthority.java` (real capture/matchesRuntime), три private scripts `Start-LocalPlay.ps1`, `Stop-LocalPlay.ps1`, `Check-LocalPlay.ps1` (deliver/smoke/STOP). Дополнительно bounded `build.xml` target и private `Database.ini` прочитаны для запуска/guard. Поиск `rg` ограничивался этими символами, test targets, SQL component таблицей и локальным runtime; whole-repo audit не выполнялся.

Git использовался по явному разрешению TASK/user: read-only `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git rev-parse --show-prefix`, exact-path `git diff`/`git diff --numstat`/`git diff --stat`/`git diff --check`, затем `git add -- <ровно 8 code/test paths>`, `git diff --cached --check`, `git diff --cached --name-only`, `git commit -m "Recover phantom historical catch-up after stale state and transient conflicts"`. Documentation commit и normal push завершают checkpoint; reset/clean/restore/force не применялись. Goal tracker при подготовке отчёта: около 642450 tokens used, около 3901 seconds, token budget не задан. Общая длительность до commit/push немного больше; точное usage зафиксировано в финальной передаче.

Mojibake-маркеры в изменённых файлах проверены отдельно: совпадений нет.

Escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.
