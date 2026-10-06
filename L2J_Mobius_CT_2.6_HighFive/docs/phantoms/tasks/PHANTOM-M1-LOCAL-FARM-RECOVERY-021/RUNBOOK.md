# Единственный реальный прогон observe021

## Подготовка

Fresh DB `l2jmobiush5_localplay_observe021` из PLAY штатным SELECT/export clone pattern.
PLAY `l2jmobiush5_localplay3` не менять. Guarded TEST не использовать для connected run.
Runtime из clean committed SHA; hashes JAR проверить до запуска.
Effective population target1280 / active8 / materializedCap8 / scheduled10000.
Diagnostics=True, Pilot AutoAttach TestAdmin, Synthetic=False.
TestAdmin accesslevel100 только в private clone по уже согласованной процедуре;
GM hide/invisible/invulnerable/silence=False. kpCat не менять.
Не менять rates/geodata/spawn/NPC/skills/дроп, не подбирать специально самый лёгкий профиль.

Перед login: system ENABLED, корректные DB URLs, owned PIDs/start-times/runtimeId,
2106/9014/7777 принадлежат только observe021. Active native lifecycle и known errors
в prelogin логе проверить до просьбы пользователю.

Написать обычным сообщением и ждать без таймаута 10 секунд:
«Сервер готов. Войди вручную TestAdmin и напиши \"в игре\".»
Ожидание — WAITING_USER, не технический BLOCKED. Не закрывать запрос и не делать
три одинаковых status-turn. Автокод .playtest arm не нужен.

## Съём

После «в игре»: IN_GAME + REAL_LOGIN + ARMED_IDLE server-side.
Никакого PREPARE_M1_ENVELOPE, movement/teleport TestAdmin, target/attack/cast Phantom.
Использовать существующий SNAPSHOT_PHANTOMS includeCensus=true с pagination и
SELECT_VISIBLE_PHANTOM_TRACE/SNAPSHOT_SELECTED_PHANTOM_TRACE. Они не создают маршрут.

Выбрать nearest natural visible eligible Player; сохранить небольшой cohort всех видимых
(до4), а не только успешный один. Первичный selection должен давать profile/object/epoch.
Если никто не visible — один bounded startup window до60s, затем точный STOP, без repeater.

Полный baseline перед observation: exact goal/runtime/travel revision; currentActionGuard;
travel failure identity+reason+segment witness; intention/target/AutoPlay; npcId/anchor;
native evidence object/epoch, damage/kill/reward/farmCycle/target/EXP/SP/loot/overflow.
TestAdmin XYZ/target/moving записать до/после diagnostics.

Active observation до300s с первого успешного baseline (не с запуска PowerShell!).
Samples на t0, +2,+5,+15 секунд, далее каждые10s. Полные JSON писать на диск, в чат
только компактный summary. Дедлайн сверять с server timestamps; не выдавать 1.8s за120s.
Один runId/последовательность запросов, нельзя переиспользовать stopped/cancelled run.
Существующая session/run duration должна вмещать300s; проверить её ДО login. Если меньше,
заранее задать документированный допустимый bound, не менять safety semantics.

Успех: same profile/object/epoch, nativeFarmCycleSequence delta>=5, nativeKillSequence>=5,
positive EXP/SP и подтверждённые next-target transitions. Счётчик farmCycle растёт после
выбора следующей цели, поэтому пяти отдельных убийств самих по себе недостаточно.
Damage/kill/reward должны иметь native attribution; self-heal не farm progress.
Loot delta=0 отмечать как NO_LOOT_OBSERVED и не объявлять loot работающим. Проверять
pickup/autoloot только при фактическом applicable drop, ничего не создавать искусственно.

Не останавливать успешный первый удар как GREEN. При повторяющемся outside-scope defect
либо истечении300s — exact failure и STOP. Covered revision/route-recovery должны быть
проверены ещё до этого прогона; живой run не место для новых fixes.

## Shutdown и сохранение

Попросить выйти до character select; дождаться «вышел». Проверить online0 и сохранённые
level/exp/sp/x/y/z у TestAdmin, total human sessions и отсутствие активного Pilot run.
Stock graceful Game/Login shutdown; проверить PIDs и порты. Force только после отдельного
разрешения на конкретные текущие PID. Старое разрешение force из018/019 не переносится.
Сравнить сохранённые поля до/после. Сохранить clone и recovery evidence; не выдавать
сохранение в clone за перенос прогресса в оригинальный PLAY.
