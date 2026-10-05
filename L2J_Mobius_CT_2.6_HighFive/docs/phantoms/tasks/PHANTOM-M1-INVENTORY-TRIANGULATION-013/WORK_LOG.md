# Task013 execution ledger

M1=OPEN. Без субагентов.

## Read-first и base

Прочитаны Agents.md, README, релевантные master plan разделы, CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, task013 GOAL/DESIGN/PLAN/ACCEPTANCE/GIT/SOURCE_MAP/ADMIN/HANDOFF и task012R RESULT/HANDOFF_RESULT/COMMANDS.
AGENTS выше корня, отдельные code-map/pattern files и task013 TASK.md не найдены. Task013 source map и GOAL задают scope.

Remote проверен через git ls-remote: ae6f0236f7f16039eb4b1e03f4df009943c59b91, parent = 88b7dd76643cb80b78246668cd16052e740a55aa.
Diff этих commits содержит ровно шесть task012 docs/evidence файлов, production/test source неизменны.
Пользователь явно разрешил base ae6f0236 после проверки diff; это сохраняет возможность normal push без merge/rebase/force.

Создан managed isolated worktree C:/Users/ZBook/.codex/worktrees/m1-inventory-013/L2J_Mobius, первоначально exact 88b7dd. После разрешения пользователя clean detached worktree переключён на ae6f0236; HEAD проверен, startup status пустой.
Existing experiment branch занята FOREIGN checkout. Работа в detached task worktree; normal publication будет HEAD:experiment/m1-candidate007-observe008 без изменения FOREIGN checkout/его index.

FOREIGN evidence сохранены read-only, исходные SHA256:

- PhantomM1PopulationFixture.java: 061CD53A82D6A8D56AC1B611E00D4845821FDAFE9DEF9364EEB993FE5AACFDF1
- PhantomM1HistoricalNativeContextChecks.java: 66CAFE145AE64C44D4409B056AA9901A0A9F601252894A32EC8AE54039A5A1C1
- task012R RESULT.md: 94C637B8A65CC7BE9215E60769BCED76335F209DE373505367CDAD51E3025E58
- task012R HANDOFF_RESULT.md: 90E90CB7AEE8D6B0CF06F5747934380803BED34532D635B16726F79F9F325C24

## Исходные пять differences

| objectId | itemId | A background | task012R captured projection |
|---|---|---|---|
|268496061|1795|140 INVENTORY|absent|
|268567849|1868|50 INVENTORY|absent|
|268568616|1864|absent|10 INVENTORY|
|270235841|1833|50 INVENTORY|absent|
|270570952|1873|10 INVENTORY|absent|

Absent означает отсутствие в tracked projection, не доказанное удаление native item.

## Bounded scope

JDK25/Ant/existing DB guards, без dependencies или core changes. Max2 Phantom production files и только после доказанной классификации.
Test изменения: exact profile68 READY|DEAD selector и A/B/C/D probe в existing lifecycle; exact 012R festival startup restore predicate переиспользован для безопасного full fixture cleanup, без broadening GLOBAL_WRITERS.
Task package/evidence является явно ограниченным исключением к file-count limit; другие artifact families не меняются, отдельная TestAdmin операция прямо разрешена пользователем.

A/B снимаются после existing admission, перед load; C/D до production afterPlayerLoad delegate/cleanup/store. Сравниваются exact goal/claim, все canonical objects и C native locations, committed tracked subset, capture projection и autoGet. Evidence содержит только item факты и totals, без payload/паролей.

До native run production edits=0. Classification, regression, runtime пока REQUIRED.

## Bootstrap diagnostics

compile-tests с -Dbuild=.phantom-local/build013: exit0, 30s, два прежних System.runFinalization warnings.
Первый Java launch не достиг main из-за PowerShell parsing unquoted -D; исправлены только quotes.
Второй launch остановился до DB connection: schema manifest stale. Read-only comparison 121 SQL выявил только шесть EOL mismatches (CRLF нового worktree против LF existing fixture); normalized текст одинаков.
В isolated worktree восстановлены exact existing bytes этих шести файлов; git diff --exit-code по SQL/migrations = exit0. SQL semantics и DB metadata не менялись, guards не ослаблены.
После этого начат actual native run, before-image journal создан existing fixture path. До run существовали только два FOREIGN localplay Login/Game процесса; они не остановлены.

## Классификация ДО production edit

Exact profile68 object268485428 epoch87812159084400 достиг afterPlayerLoad. A/B/C/D за один run сохранены в INVENTORY_TRIANGULATION.tsv до delegate, cleanup или store.
Все пять object differences воспроизведены; B=D и полный C совпадает с native B locations по objectId/itemId/count/location; totals B=C=D.
A совпадает с B под committed mutableItemIds [14,57,1795,1833,1868,1873,2007].
Новый capture использует mutableItemIds [17,57,428,463,1100,1103,1864,1869,2136].
Следовательно, каждый из пяти diff = CAPTURE_PROJECTION_DRIFT. Четыре item не исчезли, item1864 count10 не создан; они лишь исключены/включены новым projection. Никакого item/count/location conflict или native normalization. AutoGet exact=true.
Production diff перед этой классификацией = exit0. Required RED = INVENTORY_PROJECTION_RED, до delegate.

Предстоящий минимальный fix: только Authority arrival native capture READY/DEAD использует committed tracking IDs и реальные Player item values. Обычный capture и materialized gameplay capture продолжают применять текущую goal projection. Full canonical inventory hash и owned-store guards сохраняются.

## Fix и проверка native lane

После записи классификации изменён ровно один production файл L2jPhantomBackgroundAuthority.java: arrival READY/DEAD capture читает реальные native objects под committed mutableItemIds. Core и canonical writers не менялись.
Повторный exact run: diffs=0, autoGetExact=true, B=D и C=B; in-memory count-hint+1 control не меняет Player/DB и не скрывает несовпадение count.

Полный native lane сначала остановился в тестовом preflight. Добавлен diagnostic каждого условия: status=SUCCESS, scalar COMPLETED/SUPPORTED, canonicalPoints=1, stateEquals=true, hashesEqual=true, loads=1; общий simulationEligible=false, поскольку exact profile68 DEAD.
Существующий historical producer PhantomHistoricalBackgroundService проверяет proof.context().simulationEligible(), а общий NativeContextResult.simulationEligible() дополнительно требует READY. В двух TEST assertions переиспользован producer contract с явным SUCCESS, без оживления profile или production eligibility изменений. Все прежние durable/claim/epoch/store guards сохранены. Final native run после этого REQUIRED до получения результата.

Оркестрационные ошибки: Ant compile был ошибочно запущен параллельно native JVM с тем же build013 и удалил классы до fixture apply; run завершился NoClassDefFoundError, journal отсутствовал. Последующие compile/native идут последовательно. Один Ant command указал несуществующий handoff target после двух успешных suites; handoff запущен отдельно через existing launcher route.
Подтверждены reports: native-context-contract 3/3, ecology 18/18, handoff-regression 6/6. Это не заменяет полный historical-context lane.
После всех завершённых native runs before/after TEST aggregate точно совпадает E5EE30F33A9EF870383E1AEB335CCD2CBA6C418020BEB33D28DF882CD4802C55, infrastructure/owners drained, restore journal удалён.

## Отдельная TestAdmin операция и old runtime

PLAY exact SELECT вернул одну offline строку charId268492939/name TestAdmin/account testadmin/accesslevel0. Exact CAS UPDATE accesslevel100 затронул ровно одну строку; after SELECT подтверждён. level12/exp138026/sp13880/x44131/y42673/z-3488 неизменны. ADMIN_EVIDENCE.tsv содержит before/after без секретов. kpCat и другие characters не входили в mutation predicate.

Old observe011 занимал порты, TestAdmin online1. После exact logout question пользователь ответил «вышел». DB подтвердила online0 и сохранённые те же level/exp/sp/x/y/z. Old server пока работает.
Existing Stop-LocalPlay имеет force fallback и не используется. В private ops013 подготовлен attach helper для вызова штатных публичных Shutdown.startShutdown(null,0,false)/LoginServer.shutdown(false); проверяет exact runtime/role и отказывает при online REAL Player. Это private operational tooling, не production source change и не новый серверный API. До shutdown повторно проверяются ownership/PID и DB offline/store.
Private Prepare-Runtime013 переиспользует existing observe011 clone script, меняет имя только fresh clone/runtime и добавляет четыре GM overrides false. Runtime ещё не создан и не запущен.

## Engineering GREEN

Final admitted-goal run exit0/1 of1. Producer stale-authority renewal goal14→15 происходит до native admission; exact admitted goal/plan и original population components сохранены при attestation. Проверяется только штатный +1 revision/+1 planOrdinal при original stale hashes; календарь, seed, all native scalars/items/skills/B4 защищены прежними exact comparisons. Новая production правка для renewal не делалась.
Inventory0diff/autoGet exact, attestedBeforeRunning=true, NORMAL fenced, unadvanced cursor/ordinal0. TEST before=after exact E5EE30F33A9EF870383E1AEB335CCD2CBA6C418020BEB33D28DF882CD4802C55; journal отсутствует. ant -q -Dbuild=.phantom-local/jar013 jar exit0/28s. GameServer.jar SHA256 9E0BCFF192DEDD7708A2D656E95DDEF8A72CBB6779BD5AFCD11FE27B7837A987; LoginServer.jar D82E588B1D047EA18B203885A53F6C5E31454B09558D37478EB3CF53A5CE12C7.

Old observe011 Game14472/Login17672 штатно остановлены после повторного DB online0/store и exact ownership. Первый общий agent был несовместим с Login-only classpath; server остался running без force, затем отдельный LoginShutdownAgent вызвал штатный shutdown(false). Порты освобождены. Foreign four SHA256 повторно совпали.

- mojibake-маркеры в изменённых файлах проверены: rg exit1, совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: отдельный rg exit1, совпадений нет.

Self-review exact diff: production1 file, actual counts/locations read from Player, no fallback copying state objects; current goal tracking still validated; core/canonical/store guards untouched. Test corrections correspond to existing historical producer code, retain exact admitted goal instead of discarding goal protection. Runtime gate remains REQUIRED.

## Runtime и STOP

Source/evidence commit c23915df102 normal-pushed на ту же experiment branch. Fresh clone013 унаследовал accesslevel100. Runtime сначала отказал raw-hash guards v1/v2 из-за Windows CRLF/LF. Обе private XML сравнили с working observe011 по полному normalized text, восстановили только exact existing bytes с hash23B12F…/D555AB…. Production guards и source XML не менялись. Partial startups drained через stock graceful после DB online0/store. Private Stop-LocalPlay early delegates to no-force helper, включая startup orchestrator fallback. Game26344/Login18056 finally ready/owned; code/jar/config evidence RUNTIME_READY.md.

Получено «в игре». ARMED_IDLE без arm до RPC, STATUS IN_GAME/REAL_LOGIN actor268492939 succeeded. Gate start21:08:19.9637223Z. First SELECT rejected SESSION_OR_DEADLINE (different runId), old run retry CANCELLED; fresh valid run дал NO_VISIBLE_MATERIALIZED_PHANTOM78.322s и SNAPSHOT_PHANTOMS NO_CANDIDATE79.128s. Snapshot envelope111.125s отказал ENVELOPE_NOT_PREPARED; preparation не вызывалась. Late final sample141.722s исключён; это нарушение window bound, не positive gate evidence. Sampling не непрерывный и locality-only, поэтому отсутствие global live Phantom не доказано. Exact live tuple/PASS не получен. Следующий gameplay blocker не исправляется, additional3min observation не проводится. TASK_RESULT=BLOCKED_OTHER, M1=OPEN.

После «вышел» exact observe013 DB online0 и сохранённые level12/exp138026/sp13880/x44131/y42673/z-3488 подтверждены; REAL online1 count0. Штатный graceful Game26344/Login18056 exit/ports verified, no force. Final report publication остаётся разрешённым шагом; runtime больше не запускать в task013.
