# Фактический lifecycle и оставшиеся blocker

Все runtime episode используют полный GameServer, stock scheduler,42407 active NPC spawns,
existing LocalPlaySyntheticHumanSession/ActorMode=Synthetic, ordinary copy TestAdmin в clone.
GameClient отсутствует; fake REAL_LOGIN и Phantom EXP/items grants не использованы.
Runtime episodes6/8; semantic rounds4/4. Source frozen1b71464a9e09a80dad890e2bfd39df0f3b5d3b96.

| Проверка | Наблюдение | Итог |
|---|---|---|
| Unchanged-base a | Реальные DEAD/restore ошибки, retained4, raw receipts сохранены | RED для R2; не final PASS |
| Candidate b probe | Полная8 cohort, primary27220 cycles, затем REGEN deadline; stock stop retained8 | RED для R3/R4 |
| Frozen b final A |386.96s,5 cohort; actual EXP/SP, но все tail120 rewards0 | FAIL continuous |
| Frozen b final B | Observer один раз перенесён в factual oren02_2119_07s,42400/41700; native teleport accepted, snapshot read-only | ENVIRONMENT_GAP: только1 eligible после60s; полноценного второго scene нет |
| b native walk | Реальное MOVE_SELF до38131/42673/-3616; полный уход/возврат не завершён | PARTIAL; не SOFT_RETURN PASS |
| b frozen stock stop | Processes22928/16244 завершились штатно; Phantom retained5 | FAIL earned drain |
| b same-DB restart | Проверенный Start-Reviewed023 запустил10460/3836; никакого reset | FAIL точного progress; затем empty drain completed60ms |
| c planned crash |27584/ticks639269314875887753, exact runtime/JAR, dumps; synthetic STOP и REAL count0 | Выполнен ровно один crash; не общий CRASH PASS |
| c same-DB restart |19460/2196, native stock reconciliation,8 canonical identities не дублированы | Full actor continuation не доказана; nearby census0 после30s |
| Final c stop | Phantom stopped=true49ms, оба PID завершились штатно | Последняя empty drain PASS; прежний retained5 FAIL остаётся |

Actual cooperative residual:411/663 fail closed при NPC retaliation внутри другого
earned HitTask. Полный stack находится в FINAL_SCENE_A/cohort-result.json и raw samples;
NATIVE_EARNED_RECIPIENT_NOT_CAPTURED сохранён, не отнесён к unrelated debt.
Остальные281/459/1176 остановились с LOCAL_FARM_UNAVAILABLE/travel.journey_deadline.

| b profile | EXP live в SceneA | EXP после stop | SP live | SP после stop |
|---|---:|---:|---:|---:|
|281|26919|25937|1827|1720|
|411|19695|19570|1569|1555|
|459|21518|20885|1418|1348|
|663|25029|24461|2062|2000|
|1176|11142|11142|570|570|

281/459: NATIVE_STORE rejects canonical position, exactAnchor требует одну законную
anchor identity.1176: native values сохранены, POST_STORE rejects topology NOT_RUNNING.
У411/663 native incident ранее; mutable progress не применялся вручную.
Read-only comparison: B_SAVED_PROGRESS_COMPARISON.tsv. Полный failed b DB snapshot
сохранён privately в night023b/after-failed-stop-R4.sql; SHA в B_FAILED_CLONE_SNAPSHOT.txt.

Guarded TEST process-crash PREPARE/NATIVE/FINALIZE не запустились: exact schema metadata
394F... не соответствует repository/local2CD5... . Read-only121 SQL comparison показал
0 различий после CRLF normalization и6 byte differences. Это объясняет guard barrier,
но не разрешает менять guard, schema metadata или SQL outside scope. Всё сохранено.

Stock loot: candidate и exact unchanged-base8/9, один identical excluded-source gap.
Protection/capacity/concurrent/watchdog и actual pickup controls PASS; party11/11 PASS.
Natural autoloot observed у281/459 с raw inventory delta. Общий LOOT gate UNKNOWN,
excluded-ground case не подменён искусственным drop. Controlled H15 death preflight PASS;
full-server same-actor native death→recovery→farm не доказан: DEATH gate UNKNOWN.

Task-local invalid attempts сохранены: Min overload zero-step, conservative water height
margin, wrong ConfigLoader import исправлен по существующему headless environment,
single-page census assumption исправлена на existing bounded pagination.
Actual stock dry route afterward PASS63 points/447 water bounds; не доказательство remat.
Synthetic five-run/JVM cap сохранён; replay/run-cap timeout не обходился.

Два последних empty drains не исправляют lost earned progress. Никакой uncertain receipt
не удалён, Phantom epoch/EXP/items не сброшены ради PASS. Default STOPPED, clones сохранены.
