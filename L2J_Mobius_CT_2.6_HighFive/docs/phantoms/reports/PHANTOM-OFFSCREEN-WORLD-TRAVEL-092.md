# PHANTOM-OFFSCREEN-WORLD-TRAVEL-092

25.10.2026. **TOTAL092=PARTIAL; IMPLEMENTED=TEST_ONLY; production changes=0.**

**SOURCE_VERIFIED=PASS (ограниченный trace); FOCUSED_VERIFIED=PASS (boundary contracts); PRODUCT_GATE=RED; NATIVE_VERIFIED=NOT_RUN; OWNER_QA_PENDING=REQUIRED.**

Новая offscreen-ветка не реализована. Цепочка STORED → canonical arrival → materialization → native WALK → earned EXP/SP не выполнена. Применена разрешённая TASK092 §8 / SAFETY stop policy: оставить trace, focused proof и отчёт, не подменяя отсутствие доказанного безопасного допуска обходом canonical owners. Это не утверждение, что расширение без новой схемы невозможно. В этом запуске безопасная минимальная интеграция identity и наблюдения не была реализована и доказана.

## Authority и read-first

Git root определён фактически: `D:\Codex\repos\L2J-phantom-world`; ветка `feature/phantom-world`. До правок local HEAD, upstream и live GitHub branch совпали с baseline пакета: `8f5fb546d544ec0691324e23531cfdefca0b90f1`. Начальный tracked dirt отсутствовал. Существовавшие `.phantom-local/` и untracked task packages сохранены; пакет092 не редактировался и не добавляется в commit.

Все восемь payload entries MANIFEST_SHA256.json совпали по размеру и SHA-256; сам manifest исключён его контрактом. Отдельного исходного ZIP в переданном пакете нет: сверены распакованные байты. Финальная повторная проверка подтвердила8/8 hashes/sizes и strict UTF-8/no BOM всех девяти файлов пакета.

Прочитаны root/module AGENTS, root README, релевантные разделы master plan/current status/release contract, CODEX_WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD, OWNER_GAME_CONFIG_CONTRACT_2026-10-25 с дополнениями090/091; весь пакет092, отчёты087/089/091 и owner-config разделы отчёта090. Дополнительные AGENTS в целевых подкаталогах/предках и module README.md не найдены. Карта scope — SOURCE_MAP092; отдельный актуальный travel code-map/pattern не найден.

Прочитаны `PhantomHistoricalBackgroundPlanner`, `PhantomHistoricalBackgroundService`, `PhantomBackgroundAuthority`, `L2jPhantomBackgroundAuthority`, travel/commit участки `PhantomBackgroundService` и `PhantomBackgroundTransaction`, state/codec/goal/catchup/operation key, production retention/locality wiring в `PhantomSystem`, `PhantomMaterializationRetentionPolicy`, `PhantomHumanLocalityControl`, topology model/validator/loader, штатный OwnerConfig/OwnerProfileVerifier и Ant targets. Ближайшие аналоги — immutable planner fixture074, pure travel091, historical033A, canonical historical CAS и own-shadow tooling087/089/091.

Сохранены Java25/Ant, canonical native owners, binary format2/compact3, architecture/scope и запрет runtime-записи в source. Новых workers, navigation budgets, topology/catalog edges, конфигов, схем БД и production API нет.

## Причинный trace и граница остановки

| Участок | Подтверждённый факт / ограничение |
|---|---|
| Planner graph0 | `candidate()` требует `travel.route(currentAnchorId, anchorId)`; при отсутствии маршрута возвращает null. `planOrIdle()` превращает отсутствие цели/маршрута в historical idle. Обычный допуск unreachable farm не расширен. |
| Authority | `advanceTravel` получает `PhantomBackgroundState`, `PhantomBackgroundGoalSpec`, budget/logical time/policy. Farm-spec содержит NPC/anchor/resources и не сохраняет goalId/revision. Существующая ветка при route absent возвращает NO_ROUTE. |
| Canonical transaction | `advanceHistorical()` строит HISTORICAL_TRAVEL key и Command; `requireSimulationContext()` под блокировкой повторно вызывает authority и сравнивает position/clock/policy. Goal/revision, CAS и receipt здесь уже сохраняются. Транзакция существует и не заменялась. Готового typed offscreen proof с origin/destination/goal/generations/observer admission в текущем travel API нет. |
| Остаток времени | `Clock` содержит residualTravelMillis без identity перехода. Сам scalar и farm-spec не доказывают принадлежность остатка новой цели. Новый durable binding или его проверенное получение из canonical goal/catchup/receipt не реализованы. Остаток не переиспользован для offscreen. |
| Retention | `observe()` при `materialized=false` удаляет lastHuman. Тест подтверждает потерю отметки уже через1ms после наблюдения, до истечения60s grace. Этот live-retention snapshot нельзя использовать как доказательство отсутствия недавнего человека у STORED actor. Политика не менялась. |
| Human census | `PhantomSystem.onlineHumanPoints()` и locality refresh ограничивают список256. `PhysicalDemand.overflow` существует, но агрегирует overflow запроса profiles, а не полноту human census. Проверенного source+destination/recent/overflow-aware допуска для этой ветки не добавлено. |
| Вывод | Простое разрешение candidate или новый статус ARRIVED не закрывают identity и observation. Безопасная минимальная extension и её commit-time recheck не доказаны; production оставлен fail closed. |

Потенциальная дальнейшая работа внутри той же задачи потребовала бы явного typed admission и стабильного canonical binding через planner/service/authority/transaction и observation wiring, focused stale/restart/fault checks, затем настоящего guarded native proof. Этот перечень описывает открытый gate; реализация или следующая задача автоматически не начинаются. Optional extension goal/catchup не объявлена невозможной или проверенной. Paid-GK INTENT/ARRIVED TASK091 остаётся отдельным OPEN.

## Focused proof: RED и отдельные GREEN boundaries

Новый runner читает фактический `high-five-core.xml`, native NPC XML и exact spawn XML. Выбранные входы:

- origin: `elven.farming.oren04-2019-02s`, `(24954,50864,-3400)`, FARMING, outdoor polygon;
- destination: `giran.farming.22859`, `(87439,121072,-3061)`, FARMING, outdoor point-radius;
- stock Monster22859 Howl, level81, base EXP27409/SP3036; exact spawn присутствует в `data/spawns/Others/22_21.xml`.

Это **XML-backed fixture**, не production-loaded native knowledge/Geo/viability proof. В fixture один виртуальный READY state, profile92/object920092, level80/class1; эти IDs не создавались в БД. Graph0 и положительное certified edge заданы fixture. Native map-region/spawn validation и combat viability не проверялись; нет утверждения, что данный actor законно выбран реальным AI.

`required-product` действительно завершился exit1 на `planner.target_or_route.absent` с `REQUIRED092_RED`. Этот режим оставлен opt-in diagnostic gate; он не включён в обычные suites и не является рецептом сделать обычный `candidate()` допускающим любые unreachable цели. **Product RED→GREEN не достигнут.**

`contracts` завершился exit0, три группы:

1. Обычный planner отказывает на graph0; те же stock-backed факты выбираются при добавленном certified fixture edge. Действующий маршрут сохраняется.
2. Разные goalId/revision дают одинаковый farm-spec, но разные canonical operation digests. GK travelLegId отвергает подмену `OFFSCREEN_VIRTUAL_TRANSIT`. Реальные encode/decode/encode проверяют format2 и compact3 (0/161 inventory objects) byte-exact для неизменённых синтетических snapshots.
3. Live recent-human удержание и очистка отметки при STORED воспроизводят точную границу retention. Это не authorization перехода.

`DatabaseFactory.isInitialized=false` проверено. `NATIVE_PLAYERS=0`, SQL=0. State/clock/inventory не применялись к native character; actual before/after profile, gear, wallet, goal и earned receipt отсутствуют. Ни fee, ни награды не выдавались.

## Own DB-free shadow, команды и неудачные попытки

Обозначения: `M=<repo>/L2J_Mobius_CT_2.6_HighFive`, `S=<own TEMP>/phantom092-<UUID>`, `C=S/source`, `W=M/test/offscreen_world_travel092.py`.

Один новый physical shadow, без Git worktree. Exact inventory2872 inputs: Java/test sources/resources, vendor jars, штатный build.xml, публичные68 INI/metadata и нужные public XML. Private Database/Network/Server config, SQL bootstrap и Geo203 не копировались. Это **DB-free build lane**, не допущенная native TEST092. Source/copy SHA сверяются, зависимости запуска берутся только из manifest, actual classes/resources/JAR outputs имеют отдельный SHA seal. Неизвестные Java/resources/vendor JAR additions в shadow отвергаются.

| Команда / шаг | Exit / результат |
|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -File M/tools/OwnerConfig.ps1 -Action Verify` |0;68 files/287 overrides/conflicts0/changed0 |
| `python -B W prepare M` |0;2872 exact source/copy inputs; source server JAR hashes сохранены |
| `ant -f C/build.xml -Dbuild=S/build compile-tests jar`, sandbox attempt |1 до javac,3s; OwnerProfileVerifier AccessDeniedException. Host read-only Java verifier того же shadow прошёл38 правил. ACL не ослаблялась. |
| Тот же Ant command, host attempt |1,27s; одна моя test compile error: sourceRefs вместо List<SpawnFact>. Исправлен новый fixture по фактической сигнатуре; failed log сохранён. Это не behavioral RED. |
| `W refresh-tests M S`, correction Ant command |0/0; final Ant50s,2365 production/407 test sources,5 существующих warnings, JAR создан. Ant при двух явно запрошенных targets повторно исполнил compile: в этой успешной invocation две production-компиляции. Вместе с failed compile attempt всего3 production javac passes; budget превышен по этому счётчику, не скрыт как «одна компиляция». |
| Первый `W required-product M S` |1 до нужной проверки: shared farming anchor требует polygon evidence. Исправлен только test fixture — исходный polygon/radius прочитан из stock XML. Ошибка не считалась behavioral RED. |
| `javac -encoding UTF-8 -source 25 -target 25 -implicit:none -sourcepath <empty> -cp <fresh classes/vendor> -d S/build/phantom-test/bin C/test/java/.../PhantomOffscreen092Checks.java` |0; один targeted javac только собственного теста, production/JAR без повторного Ant. Source/copy pins и outputs resealed. |
| Исправленный `W required-product M S` |1; настоящий graph0 admission RED, приведён выше |
| `W contracts M S`; `W regression M S` |0/0;3 boundary groups и3 существующих Farm074 fallback/fence/admission checks |
| Review negative: дополнительная byte-copy собственного vendor JAR в own shadow |Старый `verify` ошибочно exit0, Java не запускалась. После exact copied-inventory/manifest-classpath fix — `TASK092_INPUT_INVENTORY_DRIFT`, Python exit1, negative orchestration exit0. Единственный own probe удалён exact path. Это guard RED→GREEN, не product GREEN. |
| Финальные `seal-build`, `contracts`, `regression`, `required-product` |0/0/0/1; fresh source/copy/output attestation, boundary PASS и product RED сохранены |

Каждый Python вызов выше: `python -B W <mode> M S`; prepare создаёт свежий S без третьего аргумента. `refresh-tests` разрешает менять ровно два собственных test inputs, проверяет остальные hashes и pre-correction outputs, сохраняет old bytes/manifests/seal в S и инвалидирует seal до нового подтверждения. Повторного полного staging нет. Все отказы различены по причине; source/runtime output с нечитаемой кириллицей Windows shell не копировался в исходники.

Final GameServer.jar SHA-256: `3341808117622aa6df30ed23940844731f53b6f5a989c44504072a4a16b904f8`.
LoginServer.jar: `7bd87abc91ce689fdf408f7c84ff1755d4e8832adc57d045538a7802cb54a238`.
JAR содержит неизменённый production baseline. Он не поставляется как реализованный092 и не коммитится. Последующая коррекция затрагивала только test class и launcher. Exact output seal сохранён в S.

## Сохранность, native gates и эффективность

Source OwnerConfig/Rates/Premium/PhantomPlayers/PhantomMerchants не изменялись. Штатный Apply выполнялся только build dependency внутри собственного shadow и каждый раз дал changed0. Source Save/Apply не вызывались. Source/copy pins включают68 public INI и все3 metadata; исходные GameServer/LoginServer JAR отдельно проверяются на неизменность.

| Frozen source | SHA-256 source bytes |
|---|---|
| config/OwnerConfig.json |5776e83810689da1acf94d0a3d8ee54d204e87ca0bf7488e685034b57c61f4e8 |
| config/Rates.ini |ba9c6fcbd364e09588f50a4d507d06fc9f21f0bfeaaba229977b44124db819f2 |
| config/Custom/PremiumSystem.ini |6772ddc9a713152e338dbf90ef83c5f07b37e41d0cd77aefd9beaeda41c897a4 |
| config/Custom/PhantomPlayers.ini |55043b107c918d5232b838cb56583ab3dfda2b4530b2c5321fddf35c660180b2 |
| config/Custom/PhantomMerchants.ini |11684ea937af679931abc253e01d7825de5fe9d1f3acac5437be90091f8981b3 |

Native campaigns **0/2**. TEST092 schema absence/CREATE grants/foreign connections/UUID admission/bootstrap — **NOT_RUN**. Схема не создавалась, не подключалась, не удалялась. DB0 не выдаётся за проверенный connection census. Никаких GameServer/LoginServer/Main/client запусков, материализованных Player или fixture awards нет. Выполнялись только compiler/verifier/DB-free test JVM, завершившиеся по exit codes.

Protected078 и другие releases/DB/JVM/worktrees/MAIN/PLAY не открывались для runtime и не менялись. Protected078 hashes **UNVERIFIED**, проверки ACL не обходились. Studio, чужие task packages и отчёты087/089/091 сохранены. ant verify, native farm, sameDB STOP→START, crash/fault/rollback, stale/party/combat/store/PvP/unknown-human/Geo-Z/closed-content native negatives — NOT_RUN. Полная старая snapshot-corpus совместимость не заявлена: проверены только два конкретных canonical fixture round-trips, код codec не менялся.

Work count: один shadow, один stock-backed target, три focused contract groups, ближайший074 runner и один launcher guard negative. Heap cap DB-free JVM256MiB; фактические CPU/peak heap/GC/per-profile latency **NOT_MEASURED**. Native time/distance/damage/EXP/SP/Adena/drop/restart receipts отсутствуют.5000 durable profiles/128 ACTIVE/200 stores и performance/scaling — NOT_RUN. Production workers/scans/budgets не менялись.

| Gate | Статус |
|---|---|
| A0 |Source authority/config preservation PASS; native environment admission NOT_RUN |
| A1 |PARTIAL: stock-backed fixture RED воспроизведён, typed STORED offscreen candidate не реализован |
| A2 |OPEN: bounded offscreen time/identity/transactional pending/arrival не реализованы |
| A3 |NOT_RUN: реальный profile/Player/AI goal/materialization отсутствуют |
| A4 |NOT_RUN: native WALK/damage/kill/earned EXP/SP отсутствуют |
| A5 |NOT_RUN: actual sameDB restart, stale/fault/native safety negatives |
| A6 |PARTIAL: только bounded DB-free proof; product cost/scale не измерены |
| A7 |Build/boundary tests PASS; product RED; exact publication receipt — в итоговом сообщении |

## Exact scope, review и публикация

Allowlist A — ровно3 новых файла относительно модуля:

1. `test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomOffscreen092Checks.java` — DB-free witness и3 boundary groups.
2. `test/offscreen_world_travel092.py` — own physical shadow, SHA pins/seal, scoped test correction и DB-free execution.
3. `docs/phantoms/reports/PHANTOM-OFFSCREEN-WORLD-TRAVEL-092.md` — этот отчёт.

Отдельный read-only reviewer по [requesting-code-review](C:/Users/ZBook/.codex/plugins/cache/openai-curated-remote/superpowers/7.0.1/skills/requesting-code-review/SKILL.md) обнаружил unlisted-JAR/classpath gap; он исправлен с guard RED→GREEN. Иных важных замечаний к двум test files не осталось. Отдельное чтение этого отчёта не выявило важных замечаний. Reviewer не запускал git/JVM/SQL и не аттестовал private runtime evidence.

Git разрешён прямой командой /goal, TASK092/DELIVERY и root AGENTS. Выполненные inspection команды: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse '@{upstream}'`, `git status --short`, `git status --short --untracked-files=normal`, `git worktree list`, `git config --get remote.origin.url`, `git ls-remote origin refs/heads/feature/phantom-world`, `git diff --check -- A`. Первый ls-remote отказал по sandbox network; host read-only повтор подтвердил live baseline.

Финальная доставка использует только `git add -- A`, `git diff --cached --name-only`, `git diff --cached --stat -- A`, `git diff --cached --check -- A`, полный `git diff --cached -- A`, затем commit `phantom(task-092): record partial offscreen transit safety proof` и обычный `git push origin HEAD:refs/heads/feature/phantom-world`. После push — `git rev-parse HEAD`, `git rev-parse '@{upstream}'`, live ls-remote и `git status --porcelain=v1 --untracked-files=no`. Фактический создающий SHA, push и equality receipt сообщаются после исполнения в итоговом ответе; собственный SHA не может содержаться в создающем его commit. Чужие untracked не stage/clean/move. Force/reset/restore/stash/branch/worktree/history mutations отсутствуют.

Первый inline PowerShell text-scan завершился ParserError до проверок из-за передачи literal marker characters через shell. Повтор с техническими code-point definitions выполнил обе отдельные проверки; кириллица исходников/отчёта сохранена читаемым UTF-8.

- mojibake-маркеры в изменённых файлах проверены:3 файла, полный заданный набор27,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены:3 файла, все6 требуемых regex,0 совпадений.

**STOP на PARTIAL092. Реализация092, native gates и OWNER_QA остаются открытыми. TASK093 или иная следующая задача не начинается.**
