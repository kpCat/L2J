# TASK092C — финальное закрытие WIP и read-only postmortem

26.10.2026. **TASK092C=PARTIAL; COMBAT_CORRECTIONS=DBFREE_VERIFIED.**
**NATIVE_CAST/SAFETY=NOT_RUN; EARNED_FARM=NOT_PROVEN.** Native acceptance остановилась на `092C_NO_NATIVE_DAMAGE`.
FULL_092C_GREEN=false. Состоявшийся результат — штатное восстановление после прежней смерти и store.
MAGIC execution и safety/retreat в бою не подтверждены. Дополнительной кампании ради kill нет.
Последнее разрешение владельца в чате допускает публикацию безопасного DB-free WIP при Critical0/Important0
и exact receipts. В этом закрытии JVM/native/DB/SQL/builds/новые измерения/production-правки запрещены и не выполнялись.

## Приёмка

| Проверка | Результат | Доказательство / предел |
|---|---|---|
| A0 WIP/source/build/owner/БД | PASS | Exact post-death rows, backups, private seals, JVM0/connections0 до START |
| A1 настоящий Player | PASS | profile1/object268480990, исходные post-death XYZ, World/lease, свежий CodeSource |
| Death/respawn/recovery | PASS | Начальный HP0; существующий decision → requestRecovery → native town lifecycle → новый epoch |
| Live MAGIC readiness | PARTIAL | preparationSkill=1245/MAGIC и AutoAction2=false у живого Player; AutoPlay/AutoUse session не создана |
| Фактический cast1245/damage/MP debit/HP drain | NOT_RUN | Исполнение не достигнуто; cast issued0, outgoing/incoming damage0 |
| Native cast refusal | NOT_REACHED | Отказа навыка нет: вызова cast не было; причина до AutoUse — LOCAL_FARM_UNAVAILABLE |
| Safety verdict / блокировка новых roots / settlement | NOT_RUN | Threat=null, attack=false, cast=false; опасного боевого эпизода не было |
| PvE retreat/movement | NOT_RUN | После respawn actor стоит в городе; IDLE не принят как отступление |
| A3 earned farm | NOT_RUN / BLOCKED | kill0, EXP/SP gain0, pickup0 |
| A4 store / STOP | PASS | Dematerialize SUCCESS, сохранены реальные native последствия recovery, World/leases освобождены |
| A5 sameDB restart/idempotence | NOT_RUN | Native combat не подтверждён; earned-state/goal artifacts не созданы |
| A6 DB-free regression / config | PASS | Семь affected routes exit0; combat-core59/59; OwnerConfig Verify68/287/0/0 |

## Read-first, reuse и scope

Прочитаны root AGENTS/README, workflow/current status, пакет092C и оба предыдущих отчёта092C,
exact WIP diff, целевая suite/entrypoint и private launch/snapshot helpers. Дополнительных AGENTS и README модуля нет.
Локальные аналоги: alignment AutoUse; resourcePause/retreatFrom; background decision/requestRecovery;
существующая suite092C и guarded092B launcher. Нового bootstrap, planner, движка или test runner нет.
Java25/Ant, зависимости, class-independent MAGIC/physical policy, OwnerConfig287 и ordinary players сохранены.
География/каталог заново не исследовались. Прочитан только причинный путь смерти/восстановления.
Независимый read-only review до START: Critical0/Important0; live recovery/бой/store оставались непроверенными.

В предыдущем native-продолжении изменялись только два test-файла и этот отчёт:
- `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreen092BChecks.java`: guarded accept092c/accept-restart092c и ThreadPool cleanup.
- `test/java/org/l2jmobius/tests/phantoms/PhantomArrivalFarmContinuity092CChecks.java`: exact отдельный post-death baseline,
  passive cast/incoming/outgoing listeners, наблюдение rematerialized Player и STOP при новой смерти.
- Данный отчёт. После native исправлена одна строка summary: HP/MP/dead читаются из текущего Player после rematerialization.

Production fixes после native0/2. Четыре production WIP-файла и combat-core tests прежнего DB-free этапа не менялись.
Исходный отчёт092C, DB-free report, task package и исторические092B evidence сохранены.
Никаких class115/NPC21299/level79 условий в production не добавлено; physical fallback проверен existing59/59.
Protected078, MAIN/PLAY, pet-policy, source configs, клиент и чужой dirt не изменялись.

## Точная native сцена и сохранённый результат

OWN `l2jmobiush5_phantom_test_092b`,127.0.0.1:3308, UUID `cf38a85a-6522-4b07-bfcd-2477d876f0c3`.
Входящий dump побайтово равен `proof092c/after.all-rows.sql` прежнего этапа:9680 rows.
Начало: level79, EXP3063772306, SP0, expBeforeDeath3075966164, HP0, XYZ180432/−52594/−3299,
background.state67/SHA `d4c06777bafab34960c3520624611903889b8bce8a3902ccc4c1801634fdba97`, goal revision3.
Отдельные baseline-state.bin/baseline-goal.bin сняты read-only; accepted arrival62 не подменялся.

Native PID49108: 07:09:24.781560Z → 07:16:01.478943Z, exit1; native START1, conditional restart0.
После обычного corpse window native town recovery дал XYZ146144/−57900/−2976 и новый epoch.
Это разрешённый существующий death lifecycle; тест не вызывал doRevive/teleport/attack/cast/damage/reward.
В окне180s prepared template21302, actual target objects0; новых атак/кастов/убийств не было.
Наблюдались primary1245/MAGIC, action2=false, buffs0, shots0; inventory содержит только прежнюю экипировку и Adena200000.
После recovery: LOCAL_FARM_UNAVAILABLE, runtime WAITING_RETRY/background.retry, continuationSession=false,
AutoPlay/AutoUseRegistered=false; корневая причина недостижения боевой сессии глубже этого не установлена.
В summary старого native stdout MP3735 взят из исходного retired Player; это дефект наблюдателя,
а не MP debit. Live snapshots показывают штатную регенерацию до MP3935; исходный stdout не переписывался.

Финальный store: level79/EXP3063772306/SP0/expBeforeDeath3075966164; inventory hash
`bb83d1f3bfe30887603062c4b81c9bc18d1f9f9f8ceb1b8a57706ba537d59545` неизменён.
Background.state67→77, SHA `f6a74cf2c4e371bb985bf92ca6f4f9d48a1c05f5a5b4af0ab0b69b016611d597`;
native-context94→104; catchup32 и goal.runtime3 неизменны. Сохранён town anchor `m1.route.5c99a34ceb2c44d44593ee83.anchor`.
Полный row delta:9680→9680, заменены characters1 и phantom_profile_components2; других изменений строк нет.
HP в characters0→3739, persisted MP3735; это native recovery/store, не тестовая выдача ресурсов.
Shutdown STOPPED/failedProfileIds=[], topology stop=true; postflight JVM0/connections0/online0/timestamp tables0.

## Проверки, сохранность и Git

Все нижеследующие build/DB-free/native/config результаты получены ранее; в финальном закрытии повторов нет.
Private helper `native092c.py`: read-only preflight0, prepare0, native1, postflight0, restore0.
Один full `ant -f <module>/build.xml -Dbuild=<proof>/build -Dlibs=<proof>/runtime-libs jar`: exit0,20s;
4591 production class побайтово совпадают с JAR; CodeSource live указывает на этот build/bin.
Первый javac — sandbox ACL failure на скопированном HikariCP.jar; повтор с разрешённым доступом exit0.
Финальный javac после summary correction exit0, прежние5 warnings; Ant повторно не запускался.
Pre-native combat-core59/59. Финальные092B combat/offscreen, background-model10/10, progression-catalog60/60,
combat-viability0682/2, combat-core59/59, observer092A: все exit0, DB0/native NOT_RUN.
Финальный `powershell -NoProfile -ExecutionPolicy Bypass -File <module>/tools/OwnerConfig.ps1 -Action Verify`:
exit0, filesChecked68/overridesStored287/conflicts0/changed0. Общий verify и дополнительные DB routes не запускались.

Private metadata/shadow восстановлены byte-exact; проверены1522 historical private files092B и исходный arrival.bin.
Реальная БД после recovery не откатывалась. WIP, SQL dumps, backup/preimages, native stdout/stderr,
PID/argv/exits, compact trace и final scope/encoding verification сохранены вне checkout:
`C:/Users/ZBook/.codex/visualizations/2026/10/26/01a176fe-677b-7422-85de-98943fd79264/native092c/`.
- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0.
- escaped Cyrillic в изменённых файлах проверены: отдельные6 regex, совпадений0.

Git при native-приёмке использовался read-only по root AGENTS/TASK092C bounded scope guard:
`git symbolic-ref --short HEAD`; `git rev-parse HEAD`; `git rev-parse @{upstream}`; `git status --short`;
`git status --short --branch`; `git worktree list --porcelain`;
`git for-each-ref --format=%(refname:short) %(objectname) %(upstream:short) refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`;
`git diff -- <exact task paths>`; `git diff --numstat -- <exact two paths>`;
`git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <exact task paths>`.
Точные прежние allowlist/argv/exits — `native092c/final-verification.json`; baseline `caa1b137fdc5ac3ca0248153dda185aacd839bdf`.

## Origin LOCAL_FARM_UNAVAILABLE — финальный postmortem

В native stdout `reason=` вызван через `visibleRecoveryReason()` (HistoricalBackgroundService:1406–1409).
Он берёт `VisibleEpisode.reason`; `VisibleEpisode.unavailable()` (1657) устанавливает LOCAL_FARM_UNAVAILABLE
и cooldown `max(now+10s, episode.started+60s)`. Это агрегат episode, не прямой `replacement.reasonKey` planner.
Установить его могут бюджет targets+routes>=3 (1321) или `replanVisibleLocal(...).ready()==false` (1327).
Planner сначала ищет factual local farm, затем topology fallback; внешний overload допускает до3 itinerary
через foreground admission. Отказы могут означать combat/current exclusion, отсутствие допустимого пути
либо `planner.visible_route.unavailable`; одинаковый episode reason не различает эти причины.
Возвращённый Result/candidateRejections и фактические exclusions последним observer не записаны в stdout/receipts.
По коду обычная ветка исключает прежний `21302@generated.farm.8bcb77c9ec91bdb3ecdf78d6.anchor`;
точные runtime sets и per-NPC результаты viableTarget после respawn — NOT_CAPTURED, не выдумываются.
Имеющиеся stock viable=false сняты при HP0 ДО respawn; их нельзя переносить на восстановленного Player.

После respawn XYZ146144/−57900/−2976, instance0, town anchor `m1.route.5c99a34ceb2c44d44593ee83.anchor`.
Level79, EXP3063772306; goal3065608245847424986/revision3 по-прежнему указывает на прежний farm/21302.
Live preparationSkill=1245/MAGIC, MP3748.65→3935/3935; buffs0/shots0. Store loadout сохраняет1245/14.
Отказа самого cast нет: AutoUse session не создана. В конце REGEN получил PHASE_DEADLINE, не useful farm.
Штатный town return и неполная Combat Readiness согласуются с наблюдением; достаточность их как единственной
причины НЕ ДОКАЗАНА. Отсутствие безопасного соло-кандидата или дефект planner этим эпизодом не установлены.

## Финальный production review и публикация безопасного scope

Независимый bounded review всего production WIP: Critical0/Important0. Проверены MAGIC→Action2 OFF,
physical/no-skill→Action2 ON, сохранение AutoUse cursor при неизменном списке и primary при reuse.
Busy-safe verdict закрывает новые roots; accepted native work не прерывается; earned loot/Sweep и two-cast MP reserve сохранены.
Retreat использует existing backend/Geo/action leases, session/epoch/destination checks и bounded recovery time/hops.
Shared AutoPlayTaskManager/AutoUseTaskManager и ordinary Player/Creature не менялись; live combat остаётся NOT_RUN.
Read-only SHA сверка:2368 production sources,5927 class files,4591 JAR-bound classes; семь Java-файлов092C
совпали с сохранёнными успешными Ant/compile/DB-free receipts. В этом закрытии менялся только данный отчёт.

Exact allowlist10: четыре production файла VisibleAutoPlay/NativeCombatCapability/CombatPolicy/EngagementPlanner,
три test-файла CombatCoreSuite/Offscreen092BChecks/ArrivalFarmContinuity092CChecks и три отчёта092C.
Разрешение текущего владельца: exact `git add -- <allowlist>`, staged review, `git commit -F <message-file>`,
`git push origin HEAD:refs/heads/feature/phantom-world`; normal/non-force. Task packages и чужие untracked не включаются.
Перед публикацией GitHub/local/upstream=caa1b137…, индекс пуст; нестандартных hooks/core.hooksPath нет.
SHA, staged allowlist, точные Git argv/exits и ответ push фиксируются во внешнем `closure092c/delivery.json` и финальном ответе.
Private metadata, historical092B evidence и исходные receipts не менялись. После публикации — STOP без092D/093 и FULL GREEN.
