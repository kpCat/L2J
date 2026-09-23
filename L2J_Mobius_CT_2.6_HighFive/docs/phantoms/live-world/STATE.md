# Состояние LIVE WORLD

Baseline: `6c229190d5920e6159c317e78433972457d01912`.
Дата фиксации: 17.09.2026.

| Этап | Статус | Следующая контрольная точка |
|---|---|---|
| LIVE-001 | NOT_STARTED | 001-A: получить свежие логи и отличить завершение Java от дисконнекта |
| LIVE-002 | NOT_STARTED | машинный реестр всех spawn-групп и карта покрытия |
| LIVE-003 | NOT_STARTED | трассировка существующего фонового цикла и разделение доступности/детализации |
| LIVE-004 | NOT_STARTED | единое владение торговцем и отдельный бюджет сидящих Player |
| LIVE-005 | NOT_STARTED | реестр инстансов, затем сквозная приёмка после предыдущих этапов |

## Не потерять

- Рейты пользователя сознательные: XP3/SP5, партия2/2. Не «исправлять».
- Сбой 17.09: маршрут Talking Island; точная привязка к границе города не доказана.
- Последний показанный лог: 20:00:15, 34 516 байт; нет его содержимого.
- Июльский Access denied — не доказательство причины сентябрьского вылета.
- Cross-class отказ у Nerga/Kincaid; до исправления проверить действующий runtime, реальный логин и путь до showNoTeachHtml.
- 10 000 постоянных личностей — цель; отдельные видимые торговцы не расходуют боевой лимит, но расходуют память и входят в общий body cap.
- PM/приглашение далёкому Player не равно PM/приглашению профилю без Player.
- Региональный крик не становится глобальным только потому, что адресат нематериализован.
- Фоновая модель должна обновляться редко; обычный spoil только при доступных Spoil/Sweep.
- Полное географическое покрытие и доступ человека в катакомбы — разные вещи.
- Существующий QOL freeze сохраняется; нет автоматического QOL-010 и бесконечных «финальных» задач.

PREVIOUS_NEXT_ACTION (checkpoint 17.09): собрать TXT по CRASH_CHECK.md без Codex, изменений сервера и БД.

## LIVE-003-0B — 22.09.2026

Исходный HEAD `8676bbf8dcf78b73686b0601d101740b704a43fa`, ветка `feature/phantom-world`. Исправлен ecology-aware ACTIVE admission до региональных квот и последний native materialization failure; focused 10 Ant targets GREEN (79/79), guarded Player-in-World integration GREEN, `ant jar` GREEN. Отчёт: `docs/phantoms/reports/LIVE-003-0B.md`.

GameServer JAR SHA-256 `3B7D421924A5702CB6D8183422494FE94A77430DE03251F2E3E212334D452F15` доставлен в существующий private runtime вместе с точечным admin handler. Пользовательские игровые конфиги, данные, heap и collector сохранены; manifest metadata синхронизированы с действующим конфигом. CHECK/START/STOP в private runtime теперь сопоставляют owned PID по DateTime; rollback-копии лежат в `.phantom-local/backups/LIVE-003-0B-20260922-2255/` и `.phantom-local/backups/LIVE-003-0B-review/`.

Runtime CHECK/START подтверждают два owned процесса и открытые ими порты. Native eligibility/World snapshot в живой JVM ещё не получен через авторизованную GM-команду; DB `online` не заменяет эту проверку. Поэтому **LIVE-003-0B runtime gate=PENDING; общий результат=PARTIAL**. LIVE-003 целиком, recovery/content/crash, LIVE-001/002/004/005 не закрыты. Новые этапы не запускались.

### LIVE-003-0B-C1 — 23.09.2026

На tracked baseline `314a570327cb7d5c60ae102a3fa809d53cb598bd` source tooling стал каноническим: PID record v2 с UTC ticks и JVM runtime/role marker, exact port ownership, видимый manual START, явный background mode, no-op duplicate START и STOP с проверкой exit/портов. Private runtime получил эти скрипты, новый GameServer JAR `93135D8AB1C77CE913477C5A9A5B1EADD208C28CFC07CA5BC066B63AE79C570E` и `.phantomstatus [profileId]` voiced handler для personal allowlisted real player. A09/A10 теперь имеют отдельные deterministic assertions; финальные 6 focused Ant targets, PowerShell 5.1/pwsh 7 ownership regression 8/8 и один `ant jar` GREEN. Manual visible, duplicate, STOP, background и missing-record recovery smoke подтверждены exact PID/портами. Отчёт: `docs/phantoms/reports/LIVE-003-0B-C1.md`.

После smoke private background pair Login PID 32684 / Game PID 21708 держит 2106/9014 и 7777; игровые конфиги, rates, heap/collector и Login JAR совпадают по SHA до/после. Клиентский `.phantomstatus` в этой JVM ещё не получен, поэтому native live status gate **BLOCKED на клиентском снимке**, не подтверждён отсутствием ACTIVE кандидатов. Recovery/content/crash и другие LIVE goal здесь не закрыты.

NEXT_ACTION: войти обычным personal allowlisted персонажем в работающий private runtime и выполнить `.phantomstatus`; при наличии admitted ID выполнить `.phantomstatus <profileId>`, сохранить native eligible/admitted/worldPresent или точную причину blocker и принять только gate LIVE-003-0B-C1.

### LIVE-003-0C — GREEN recovery checkpoint 23.09.2026

На обязательном baseline `dea0242d71f8d4e664c2a18e6a1fd8f7c91221db` реализован recoverable historical catch-up; implementation commit `0ae81885f29eca8396ba0dbcfc5897148e3212d0`. Canonical stale authority/generation возобновляется из сохранённого cursor/seed через штатный materialization/planner/store; object cap сохраняет bounded partial progress и отдельно блокирует только indivisible случай; transient item contention retry не переводит Background в fail-stop; отсутствие factual topology остаётся quiet/recoverable до LIVE-002 без fake route/teleport.

После RED финальные 7 focused Ant targets GREEN (42/42), guarded production-composed Goal033 GREEN 2/2 включён в этот итог; full `ant verify`=0, `ant jar`=1. GameServer JAR SHA-256 `0E3363F5302B556D056D27DD540238E7D079239E96D381A60F853D57217470BD` доставлен с backup прежнего JAR. Bounded background smoke и read-only SELECT `l2jmobiush5_localplay3`: COMPLETE 546→780, FAILED 734→200, RUNNING 0→300; stale 301→7, старый object cap 258→3, item conflict 5→0. Topology absent 170→186 остаётся честным block; 1280 профилей и связей с персонажами, 1281 characters/accounts сохранены, ecology initial complete 425→426. Естественная level-гистограмма после smoke: 1/344/847/85/3 для уровней 1–5. Reset/reseed и прямых SQL mutations play DB не было.

После smoke штатный STOP остановил owned Login/Game JVM; финальный CHECK: оба STOPPED, 2106/9014/7777 закрыты. `CODE_STATUS=GREEN`, `RUNTIME_STATUS=GREEN` для recoverable catch-up; native клиентский `.phantomstatus`/ACTIVE World gate ещё PENDING. Подробный отчёт: `docs/phantoms/reports/LIVE-003-0C.md`. LIVE-003 целиком, LIVE-002 и следующие этапы не закрыты.

## LIVE-002-A — GREEN source coverage checkpoint 23.09.2026

На обязательном tracked baseline `c325baa8aac21410e7153bfc1dd43604dbc29816` implementation commit `94a1461539a86d8fcc52ef36d2f168aa6fc9a851` добавил deterministic generator и полный `WORLD_COVERAGE.tsv`/`WORLD_DATA_MANIFEST.json`. Из 740 source XML native spawn groups 3 752/3 752 представлены ровно по одному разу; parse failures=0, duplicate keys=0, missing NPC IDs=0. Class counts: ordinary 2 652, conditional 19, instance 260, raid 2, event/scripted 0, non-farming 819, unresolved 0. PowerShell 5.1 и pwsh 7 дали byte-identical TSV/manifest; fixture и production validator GREEN. Full `ant verify`=0, `ant jar`=0, runtime/DB не запускались. Подробности и hashes: `docs/phantoms/reports/LIVE-002-A.md`.

`READY_STATIC` означает только source/static validation; LIVE-002-B/C, geodata/pathing и полное LIVE-002 остаются открытыми. `Ruins of Despair` имеет factual teleporter label, но точная spawn group связь в этом checkpoint не утверждается. NEXT_ACTION: отдельный LIVE-002-B task для source-derived spatial association и topology generation после принятия этого checkpoint.

## LIVE-002-B — GREEN candidate graph checkpoint 23.09.2026

На обязательном tracked baseline `c7219c2db451eaa6a36be1e8b1c9bcf7077aac25` принятый `WORLD_COVERAGE.tsv` сохранён как immutable input. Все 2 652 `ORDINARY_WORLD / READY_STATIC` groups имеют по одной source-derived topology candidate accounting row: 20 exact existing core, 2 632 generated; 2 551 новых групп требуют source-backed anchor/geodata. Machine-scanned mapregion/zone/teleporter evidence и 23 core farming nodes сопоставлены без изменения core XML. Candidate graph содержит 83 существующих edge, 1 474 factual directed teleport destinations и 3 550 новых bounded walking/region edges исключительно `NEEDS_GEODATA`, без runtime activation/backgroundEligible. После focused RED→GREEN regression 676 ordinary groups из закрытых source families исключены из новых walking edges до отдельного door/room evidence; их topology rows сохранены. Fixture suite, production validator и два побайтно одинаковых generation run GREEN; `ant verify`=0, `ant jar`=0, runtime/DB=0. Ruins of Despair оставлен factual teleporter landmark с candidate-set evidence без утверждения geodata membership. Подробности: `docs/phantoms/reports/LIVE-002-B.md`.

LIVE-002 целиком остаётся открытым. **NEXT_ACTION: LIVE-002-C — GeoEngine/path validation and publication of validated generated topology.** Автоматически не начинать.

## LIVE-002-C — GeoEngine/topology checkpoint 23.09.2026

На обязательном tracked baseline `dac4df2e31ec2eda1dde14f0d62fd4b520de0586` шесть accepted A/B artifact hashes совпали. Реальный GeoEngine загрузил 203 geodata regions. B candidates машинно проверены bounded Java validator: 2632/2632 generated anchors имеют proof rows, из них 919 VALID; 7100/7100 walking directions имеют отдельные результаты, из них 1424 доказаны. 1474 teleports и 83 core rows остались evidence-only. Все 676 closed/multi-floor groups заблокированы без factual entrance/door path. Ruins candidate set 10/10 anchor-blocked, reachable groups=0.

В активную topology опубликован один generated shard 1 313 494 bytes: 919 farming nodes/anchors и 1424 направленных BACKGROUND edges с exact endpoint anchors; core/siege entities сохранены, общий datasetVersion=4. Два fresh GeoEngine validation/publication run побайтно совпали; production corpus 9/9, focused generated historical planner 1/1, Goal033A1 ingress 4/4, Goal033A 10/10 на контрольном повторе. Directed graph от существующих ingress имеет reachable validated groups только в band 1–5 (8/38); остальные восемь bands имеют 0. Недостающие маршруты не имитировались. `ant jar` выполнен один раз, full `ant verify`=0.

Private runtime получил backup и доставленный JAR/XML. Bounded background smoke с read-only play-DB SELECT сохранил profiles/links/characters/accounts 1280/1280/1281/1281; COMPLETE 780→942, но `planner.target_or_route.absent` остался 186→186. Штатный STOP завершил Login/Game JVM, 2106/9014/7777 закрыты. **CODE_STATUS=GREEN; RUNTIME_STATUS=PARTIAL; LIVE-002 в целом не GREEN** из-за нулевой достижимости восьми bands и отсутствия доказанного runtime unblock. Подробности и SHA: `docs/phantoms/reports/LIVE-002-C.md`. Следующий LIVE этап автоматически не запускать.

## LIVE-002-D1 — factual world travel backbone checkpoint 23.09.2026

На обязательном tracked baseline `0b7732eaeaa7368776e56b42113491e3f1e1c5ed` все принятые A/B/C hashes совпали. Машинно учтены 1 756 native NPC/list/destination relations, 1 813 rows с factual spawn multiplicity, 256 NORMAL, 237 NOBLESSE и 924 special/unmodeled transitions; 396 rows `BLOCKED_SOURCE`. Для 1 602 directional local candidates настоящий GeoEngine/pathfinding доказал 173 connectors; остальные имеют явные причины блокировки. Два fresh run дали byte-identical canonical TSV/JSON, focused fixtures и production validator GREEN. Из 38 roots семи ingress families `WALK_ONLY` достигает только band 1–5 (13 из 43 опубликованных groups); factual NORMAL GK структурно добавляет Dwarf 11–19 (6 из 57) и 20–39 (4 из 192), но не закрывает прочие bands. Ruins of Despair остаётся structural unreachable: нет ingress→Bella GK access и нет GeoEngine-proven destination connector, reachable coverage keys=0. Factual GK transition не активирован в runtime; planner/economy/authority/topology XML/DB не менялись. Full `ant verify`=0, `ant jar`=0, server=0, runtime delivery=0, DB connections=0. Подробности, witnesses, fee/condition facts и hashes: `docs/phantoms/reports/LIVE-002-D1.md`, `docs/phantoms/live-world/TRAVEL_BACKBONE_MANIFEST.json`. **CODE_STATUS=GREEN для D1 structural evidence; LIVE-002 остаётся открытым.**

NEXT_ACTION: **LIVE-002-D2 — native background gatekeeper travel semantics**. Автоматически не начинать.

## LIVE-002-D2 — NORMAL Gatekeeper runtime checkpoint 23.09.2026

На обязательном tracked baseline `6d2d356ad10ee3f6b336ce16cd23807e4e10938c` принятые D1/A/B/C hashes совпали. Строгий runtime catalog содержит 5 FACTUAL_NORMAL compound legs с GeoEngine-proven connectors: четыре e4b (970 Adena) и один e904 (12000 Adena). Planner и authority теперь используют один bounded typed router; Dwarf level 12 и 24 выбирают обязательные D1 witnesses. Native free-level, exact paid Adena и Mon/Tue discount используют explicit logical epoch minute. Existing transaction атомарно фиксирует optional fee, destination anchor/position, receipt и historical cursor; guarded `l2jmobiush5_phantom_test` подтвердил free move, paid 12000→0, pre-commit rollback, ambiguous commit и idempotent replay. Финальный focused aggregate GREEN 72/72; full `ant verify`=0, `ant jar`=1.

Private runtime получил backup и JAR/XML, bounded `-Background` smoke использовал play DB только через SELECT. Profiles/links/characters/accounts сохранились `1280/1280/1281/1281`; `planner.target_or_route.absent` остался 186. Все managed профили находились на уровнях 1–5, поэтому natural Dwarf 11–39 GK transition в этом окне не наблюдался. Штатный STOP завершил оба owned JVM, ports 2106/9014/7777 закрыты. **CODE_STATUS=GREEN; RUNTIME_STATUS=PARTIAL; LIVE-002 в целом остаётся открытым.** Подробности: `docs/phantoms/reports/LIVE-002-D2.md`.

NEXT_ACTION: отдельный factual unreachable-world/closed-area topology checkpoint. Автоматически не начинать.
