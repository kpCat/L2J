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

На обязательном tracked baseline `c7219c2db451eaa6a36be1e8b1c9bcf7077aac25` принятый `WORLD_COVERAGE.tsv` сохранён как immutable input. Все 2 652 `ORDINARY_WORLD / READY_STATIC` groups имеют по одной source-derived topology candidate accounting row: 20 exact existing core, 2 632 generated; 2 551 новых групп требуют source-backed anchor/geodata. Machine-scanned mapregion/zone/teleporter evidence и 23 core farming nodes сопоставлены без изменения core XML. Candidate graph содержит 83 существующих edge, 1 474 factual directed teleport destinations и 4 776 новых bounded walking/region edges исключительно `NEEDS_GEODATA`, без runtime activation/backgroundEligible. Fixture suite, production validator и два побайтно одинаковых generation run GREEN; `ant verify`=0, `ant jar`=0, runtime/DB=0. Ruins of Despair оставлен factual teleporter landmark с candidate-set evidence без утверждения geodata membership. Подробности: `docs/phantoms/reports/LIVE-002-B.md`.

LIVE-002 целиком остаётся открытым. **NEXT_ACTION: LIVE-002-C — GeoEngine/path validation and publication of validated generated topology.** Автоматически не начинать.
