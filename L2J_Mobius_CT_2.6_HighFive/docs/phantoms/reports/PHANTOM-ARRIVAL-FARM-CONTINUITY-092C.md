# TASK092C — BLOCKED на A3, native damage без earned farm

Дата: 26.10.2026. Итог: **BLOCKED / `092C_NO_EARNED_FARM`**, `FULL_092C_GREEN=false`.
Ветка `feature/phantom-world`; исходные local/upstream/live GitHub HEAD: `caa1b137fdc5ac3ca0248153dda185aacd839bdf`.
Commit/push не выполнялись: TASK092C разрешает их только при полном GREEN.

## Первая причина и остановка

Тот же Player штатно материализован на committed arrival, обычные owners выбрали stock Buffalo Slave21299.
Зафиксирован 51 положительный native damage event, фактическое уменьшение HP и только melee (`skillId=0`).
Native cast1245 не наблюдался. Убийства, EXP/SP gain и pickup отсутствуют.
Фантом погиб; EXP3075966164→3063772306 (−12193858), level80→79, SP0→0.
`expBeforeDeath=3075966164`. Финальная причина visible recovery: `LOCAL_FARM_UNAVAILABLE`.
Формальный первый failed acceptance: A3, assertion `092C_NO_EARNED_FARM`, JVM exit1.
После него выполнены только shutdown, read-only postflight, восстановление временного shadow и сохранение evidence.
Resurrection, ручное возвращение EXP/уровня/XYZ, reseed и второй native START не выполнялись.

Наблюдаемый продуктовый блокер — проигранный одиночный native бой без заработка.
Почему обычный AutoUse не выполнил1245, причинно не доказано; production root cause не выдумывается.
DB-free MAGIC1245 contract PASS не доказывает исполнение этого каста в данной сцене.
`SAFE_NONVIABLE_SOLO` для всех соседних целей не установлен: после смерти продолжение остановлено.
Ослабления viability, изменения HP/защиты NPC, бесплатных shots/бафов или тестового исполнения атаки нет.

## Матрица A0–A6

| Gate | Факт | Статус |
|---|---|---|
| A0 INPUT | Пакет8/8 SHA/size; OWN UUID, полный DB dump, arrival62, class/JAR/input/Geo seals | PASS |
| A1 PLAYER / HANDOFF_NATIVE | profile1/object268480990, исходные XYZ/Z/instance0, World indexes, action admission и identity lease | PASS |
| A2 ACTION / DAMAGE_NATIVE | Обычный decision→AutoPlay, stock target268450995/NPC21299, 51 native damage event и HP delta | PASS для melee |
| A3 EARNED_FARM | kill0/reward0/loot0, EXP loss после смерти | BLOCKED |
| A4 STORE | Native dematerialize SUCCESS, сохранены реальные последствия смерти, World/retained entries0 | SAFE STORE PASS; earned continuity не доказана |
| A5 RESTART | Условие заработка первой кампании не выполнено | NOT_RUN |
| A6 REGRESSION | Fresh compile/JAR, preflight focused7 routes, final OwnerConfig Verify | Частично PASS; final regression/review после BLOCKED NOT_RUN |

Client/PLAY/scale5000/cap128/shops200/Party readiness/визуальная QA — OUT_OF_SCOPE / NOT_RUN.
Успешная historical092B arrival/restart остаётся исторически успешной; её отчёт/arrival/receipts не переписаны.

## Read-first и reuse

Прочитаны root/module AGENTS, CODEX_WORKFLOW_CONTRACT, начало PHANTOM_CURRENT_STATUS,
092B report строго строки1–63, весь пакет092C и его MANIFEST_SHA256.json.
Чтение больших materialization/background/history/AutoPlay/092B/022 файлов ограничено нужными символами и диапазонами.
Локальные аналоги:092B owner/arrival bootstrap;022 composed decision/travel/AutoPlay и native sensor;068 пассивные callbacks.
Для безопасной композиции дополнительно прочитаны Task091Lane, HeadlessPlayerTestEnvironment,
OrdinaryLife068Native.observe/imports, VisibleFarmTravel и MaterializationLifecycleBridge.
Для A0 отдельно прочитаны существующие092B operational launch/snapshot helpers и attested manifests;
stock XML инспектировались как входные данные: NPC templates и spawn territories, без изменения.
До первой repository правки выполнено6 repository text searches. Повторных археологических чтений хроник нет.
README.md модуля и AGENTS выше Git root/в docs ancestry отсутствуют; SOURCE_MAP пакета найден.
Существующие master plan/package standard найдены; их широкое чтение не выполнялось по более узкому READ_SET092C.
Новый engine/catalog/bootstrap, schema, актор, БД, конфиг, dependency и production fix не добавлялись.
JDK25/Ant и существующие production owners сохранены. Бюджет causal production fixes использован0/2.

## A0, backup и bindings

Единственная OWN DB: `l2jmobiush5_phantom_test_092b`,127.0.0.1:3308.
UUID `cf38a85a-6522-4b07-bfcd-2477d876f0c3`, profile1/object268480990, online0/foreignConnections0.
Полный before dump побайтово равен final dump092B;9679 INSERT rows. Admission перед START снова byte-exact.
Исходный state rowVersion62, SHA `ecc3eed956413882ec5901025fa39e55f36e8ca89ff4534afcb5b44a3a900c1b`.
Исходные position180486/−52817/−3264, goal3065608245847424986/revision2.
Проверены28814 исходных input entries,203 Geo,5924 прежних class entries,2778 source seals и7 protected config/JAR seals.
Известные committed092B source/shadow расхождения аттестованы прежним final seal; неизвестного drift не было.
Один внешний backup bundle содержит38 исходных файлов; добавлены exact preimages только заменённых shadow paths.
Private cleanup overlay остался только `DatabaseCleanUp=False`; source Database.ini/IdManager/OwnerConfig не менялись.
Во время launch manifest включал28816 entries: текущие источники и новую suite; затем original manifests восстановлены.
Fresh JAR SHA `f0beaa9bef81a5c54c806381a75ad410a2bd9f72df34c53607ac26abc9b9a030`;
4590 production class entries byte-exact сверены с JAR. Native CodeSource указывает на данный proof build/bin.

## Реальная сцена и DB deltas

Stock WallOfArgos territory у anchor содержит21299/21302/21312; в пределах5км есть другие штатные усиленные templates.
Buffalo21299: stock Lv72/HP×3; фактический spawned target268450995 имел12040HP, distance262.19, native viability=true.
Наблюдалось уменьшение HP12040→как минимум8382.5 в ограниченном callback log; sensor сообщил51 damage events.
Материализация: HP3998/4348, MP3785/3985, пять прежних equipped object IDs268480991–268480995.
В конце MP3985; damage callbacks1245=0. Попытки каста/interrupt отдельно не измерялись.
Decision сохранял `background.visible.autoplay_running`, затем LOCAL_FARM_UNAVAILABLE.
Prepared goal templates:21299/21302; фактический damage target один, NPC21299. Лимит3 templates/6 objects не исчерпан.
Окно ограничено180s; завершилось при смерти, а не по таймауту. Test не создавал/перемещал NPC или Player.
Native START PID16152:03:31:51.624761Z→03:37:09.567681Z, exit1; native START1/1, conditional restart0/1.
Ранний resource sample: WorkingSet190775296bytes, CPU94.625s; это не peak/scale measurement.
Store: `DematerializeResult SUCCESS`, `ShutdownResult STOPPED, failedProfileIds=[]`, topology stop=true.
После store XYZ180432/−52594/−3299; canonical progress сохраняет смерть и EXP loss.
Inventory/Adena побайтово по row facts неизменны, Adena200000; новых earned items нет.
State rowVersion62→67; финальный payload SHA `d4c06777bafab34960c3520624611903889b8bce8a3902ccc4c1801634fdba97`.
Component rowVersions: catchup31→32, native-context89→94, goal.runtime2→3.
Полные dumps9679→9680 rows: заменены characters1 и canonical components4; добавлена character_variables1:
`(268480990,'hunting_points','2000')` — native variable, не earned farm reward.
Other rows не добавлены/удалены; полный exact row delta сохранён приватно. SQL DDL/DML от теста отсутствует.
Postflight: JVM0/connections0/online0, timestamp tables0; source protected seals сохранены.
Original private metadata, manifests, shadow preimages и arrival восстановлены/проверены byte-exact;
1522 historical private files/receipts092B проверены на неизменность. БД не восстанавливалась из backup.

## Команды и проверки

Приватный adapter `task092c.py`: preflight0, stock0, build0, gates0, prepare0, native1, postflight0, restore-private0.
Адресный javac: private compile receipts1/2/4 фиксируют sandbox/import/syntax diagnostics;3/5 exit0.
Это ошибки подготовки наблюдателя, не заявленные production RED→GREEN fixes.
Единственный full Ant/JAR: reused `gate092b.build`, `ant -f <module>/build.xml -Dbuild=<proof>/build -Dlibs=<proof>/runtime-libs jar`, exit0 (21s).
Compile всех текущих test sources и HpDrain: exit0; binaries/source dist/libs не заменены.
DB-free:092B combat groups3, offscreen groups3, background-model10/10, progression-catalog60/60,
combat-viability068 2/2, combat-core55/55, observer092A — все exit0, SQL0/native NOT_RUN.
Final `powershell -NoProfile -ExecutionPolicy Bypass -File <module>/tools/OwnerConfig.ps1 -Action Verify`:
exit0, files68/overrides287/conflicts0/changed0. General ant verify/чужие DB routes не запускались.
- mojibake-маркеры в изменённых файлах проверены: полный заданный набор, совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: все6 regex, совпадений нет.
Exact diff/whitespace/scope verification и final WIP SHA inventory сохранены в private final-verification.json.

## Git и доставка

Git использовался только read-only, разрешение — TASK092C A0/Git и root AGENTS bounded inspection.
Точные начальные команды: `git rev-parse --show-toplevel`; `git symbolic-ref --short HEAD`;
`git rev-parse HEAD`; `git rev-parse @{upstream}`; `git status --short --branch`; `git worktree list --porcelain`;
`git for-each-ref --format="%(refname:short) %(objectname) %(upstream:short)" refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`.
Финальные команды: `git diff -- <exact Offscreen092B path>`; `git diff --check -- <exact three changed paths>`;
`git status --short --branch`; `git rev-parse HEAD`; `git rev-parse @{upstream}`. Полные argv — private receipt.
Live ref проверен GitHub compare API: baseline↔feature/phantom-world identical, ahead0/behind0.
Stage/commit/push/branch/reset/restore/clean/stash/history rewrite не выполнялись. Чужой untracked dirt сохранён.
Изменённые repository files — ровно3:
1. `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreen092BChecks.java` — узкий guarded entrypoint092C и cleanup.
2. `test/java/org/l2jmobius/tests/phantoms/PhantomArrivalFarmContinuity092CChecks.java` — composed native observer/conditional restart, без тестовых атак/награды.
3. Данный отчёт. PHANTOM_CURRENT_STATUS не менялся.

Proof/backup/WIP: `C:/Users/ZBook/.codex/visualizations/2026/10/26/01a1762e-8fd8-7522-bd57-f9baa88879c3/proof092c/`.
Raw stdout/stderr, SQL dumps, manifests, PID/argv/exits, row deltas и original preimages остаются только там.
Текущее состояние OWN БД — реальный post-death result, не исходный snapshot62. Повторной baseline-подгонки нет.
Продолжение, новый native budget, recovery baseline или расширение диагностики требуют отдельной owner задачи.
**STOP по TASK092C и последнему owner указанию; следующие задачи не начаты.**
