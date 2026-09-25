# LIVE-003-FINAL-CODE-GATE — GREEN_PARTIAL

Дата: 25.09.2026. Ветка: `feature/phantom-world`. Baseline: `d90335c2d0e43a2673d7386497555719b75aad8a`. Итоговый SHA проверенного кода и canonical output: `582e2e245cf3a20695968110504f614411780818`.

Это последний code-side LIVE-003 Goal. `CODE_GREEN` не заявлен: factual route replay для 15 строк заблокирован `LIVE002_AUTHORITY_NOT_LOADABLE`, а первый local signal для READY-профиля без сохранённого BackgroundState заблокирован `MATERIALIZATION_LOCALITY_BLOCKED`. Точные статусы находятся в `docs/phantoms/live-world/LIVE003_CORE_LIFE_MATRIX.tsv`. `0D_RUNTIME=PENDING_RUNTIME`.

## Результат A–I

| Gate | Статус | Проверенный результат / ограничение |
|---|---|---|
| A external BUSY | GREEN | Существующий `PhantomPresenceRegistry` принимает ограниченный набор именованных exact sources: `PhantomPartyCoordinator.blocksBackground`, `PhantomStoreService.blocksDecision`, `PhantomMaterializationService` action ownership (`admittedActionCount > 0`). Schedule OFFLINE имеет приоритет; освобождение owner возвращает AVAILABLE при online. |
| B periodic owner | GREEN | Общий `PhantomScheduler` сохраняет due 5–15 минут; `PhantomPopulationEcologyService` — единственный production periodic background owner. Source/packaged default Ecology=true при top-level Phantom World disabled-by-default. Явное `EnablePhantomEcology=False` остаётся fail-closed: ноль periodic awards и typed `ecology.disabled`. Второго calendar и 60-секундного partial award нет. |
| C idempotence | GREEN для pure matrix | Дубликат 10-минутного due, crash до commit, commit до ack, operation/receipt replay, monotonic cursor, materialize/dematerialize boundary, stale-authority renewal после штатного backoff, topology absent/resume, OFFLINE/BUSY покрыты pure tests. MariaDB atomicity относится к ранее принятому 0C evidence; pure tests её не доказывают. Schema/DB не менялись. |
| D progression | BLOCKED: `LIVE002_AUTHORITY_NOT_LOADABLE` | Ровно 15 строк Human-like, Dwarf spoiler и alternate non-Dwarf на уровнях 1/20/40/76/85 остаются `NOT_PROVEN`. Production topology/knowledge/progression adapters требуют загруженных server data; текущий headless loader инициирует DatabaseFactory. DB-free factual route replay через production planner не выполнен. Ни class ID, ни NPC, ни route digest не выдуманы. |
| D ordinary spoil | GREEN | Ordinary farm читает только candidate skill IDs из progression `profession.spoil` и `profession.sweep` через существующий `readAcquisitionEligibility`. Native spoil facts добавляются лишь при обоих exact learned/evidence skill levels. Спойлер получает death drops + spoil; non-spoiler и класс без Sweep получают только death drops. Повторного death roll нет; RNG replay детерминирован. |
| E topology index | GREEN | READY membership и verified committed background/lifecycle position публикуются в существующий `PhantomTopologyProfileRegistry`; retirement удаляет запись. Transient navigation не публикуется. Human point query использует существующие node/perception buckets, cap и deterministic result; человека в registry не добавляет. Pure 10k bound и 100 lifecycle cycles проверены. |
| E materialization | GREEN_PARTIAL: `MATERIALIZATION_LOCALITY_BLOCKED` | Reconcile-first сохраняется, к materialization допускаются только уже локально сигнализированные профили; remote исключены. Action BUSY и hysteresis остаются у существующих владельцев. READY-профиль без committed BackgroundState не имеет доказанной текущей позиции для самого первого local signal; conservative gate оставляет его вне выбора. |
| F weekly schedule | GREEN | v1 SHA-256 `23B12FC523DE83D1BDEA54A75677823B2E8129978792A9C9FEC684ACCFAA4748` не изменился; v2 SHA-256 `D555AB8DDA0783D0736D3AADB0BD3D1B76481C73F0A34656CDF5C70FB88FBBC6`. 168/168 часов ACTIVE ≥64, min255/max884. |
| G nickname | GREEN | Детерминированный 10k dry-run: 2336 reserved, дубликатов и исчерпаний 0. Live DB acceptance остаётся в 0D. |
| H observability | GREEN | Существующие `operatorStatus`/`.phantomstatus` показывают registry registered/resolved/buckets, last/max local candidates, periodic owner `ECOLOGY`/`DISABLED_BY_CONFIG`, external BUSY count и reason для запрошенного профиля. Base response ≤4 sysmessage lines; diagnostics=false проверен. |
| I integrated pure 10k | GREEN | Production scheduler policy + presence + topology registry/local query, 10000 профилей за 24 часа. Один общий scheduler driver; 0 Player, 0 per-profile futures. Это pure contract, не замер live CPU/SQL. |

## Владельцы состояния и локальный паттерн

- `PhantomPopulationManager` публикует READY/retired membership; `PhantomBackgroundService` публикует только проверенное committed положение через маленький `PhantomTopologyPositionPublisher` port. `PhantomTopologyService` и прежний registry остаются единственным пространственным индексом.
- `PhantomPopulationEcologyService` использует существующие 0C cursor/operation/receipt, а общий Scheduler назначает 5–15-минутные due. Новая схема или отдельный календарь не вводились.
- `PhantomPresenceRegistry` агрегирует точные party/store/action owners; lifecycle/action lease остаётся у `PhantomMaterializationService`.
- `PhantomHumanLocalityControl` выполняет bounded local query для реальных online humans и передаёт краткоживущий сигнал в существующий reconcile-first materialization path.
- Ordinary spoil использует текущие progression capability rules и точное чтение skill evidence; acquisition goal, class hardcode и выдача skills не добавлены.

## Factual route blocker

`LIVE003_PROGRESSION_ROUTE_PROOF.tsv` содержит требуемые 11 колонок и ровно 15 строк: пять уровней для каждого из трёх архетипов. Все 15 — `NOT_PROVEN` с `LIVE002_AUTHORITY_NOT_LOADABLE`. Текущие native `L2jTopologyValidationBackend`, `L2jGameKnowledgeBackend` и `L2jProgressionBackend` используют уже загруженные `MapRegionData`/`NpcData`/`SpawnData`/`ItemData`/`SkillTreeData`; существующий `PhantomHeadlessPlayerTestEnvironment.initialize` вызывает DB bootstrap. В пределах запрета runtime Hikari/DB нельзя доказать production GK/planner route и suitability. Исторический LIVE-002 DATA_COMPLETE не заменяет этот новый replay.

## Pure tests и output

Единый safe aggregate завершился exit 0 / `BUILD SUCCESSFUL`:

```text
ant compile-tests test-live003-admission phantom-activity-scheduler-test phantom-population-ecology-goal033-test phantom-population-schedule-test phantom-population-catalog-test phantom-population-humanization-goal030a-test phantom-background-model-test phantom-progression-catalog-test phantom-progression-operations-test phantom-live003-core-life-1280-test phantom-operator-observability-goal028cp1-test phantom-topology-perception-test
```

Focused результаты: admission 11/11, activity scheduler 23/23, ecology 15/15, background 8/8, core-life 9/9, topology perception 30/30, progression operations 36/36, operator 6/6. Operator target был предварительно проверен на отсутствие `phantom.test.config`. Второй свежий прогон `ant phantom-live003-core-life-1280-test phantom-activity-scheduler-test` завершился exit 0. Пять canonical TSV остались побайтово одинаковыми между прогонами:

| TSV | SHA-256 |
|---|---|
| `LIVE003_CORE_LIFE_MATRIX.tsv` | `0543A30A8933A82A91980A5800D48F2D3E0E333D56AB7D70BEA44A9D13FDE188` |
| `LIVE003_PROGRESSION_ROUTE_PROOF.tsv` | `9C2ED619D2ED06B257E5AA92E64FDB9015316A7A7DCE5D4A92F28AD2398F1A02` |
| `LIVE003_CORE_SCALE_10000.tsv` | `A203A0186E1B5E9457E4A3D39A026189337771CB6DDF08E00401C4BF54F5CDCA` |
| `LIVE003_WEEKLY_SCHEDULE_1280.tsv` | `E3DE6279FF67485A59339FB5E3221ABCA9639F224D341DEB06F398963F9ED1C1` |
| `LIVE003_NICKNAME_10000_DRYRUN.tsv` | `CE7128A2ABF25E8D3AE910A39E66E2A34F05DE44E8F0055DFB361594946C4C1E` |

Integrated pure scale: 1 230 094 dispatches; cadence min/mean/max 300049/599767/899925 мс; max due queue 9000; presence AVAILABLE/external BUSY/OFFLINE 8900/100/1000; registry registered/resolved/buckets 10000/10000/2; максимум кандидатов local query 100; shared drivers 1; Player 0; per-profile futures 0.

Проверки изменённых файлов выполнялись отдельно: mojibake-маркеры — совпадений нет; escaped Cyrillic (`\\u04xx`, `\\u05xx`, XML numeric) — совпадений нет.

GameServer/LoginServer/client, runtime Hikari initialization, DB connect/mutation, live 1280/5000/10000 scale, `ant jar`, full `ant verify` и guarded DB targets: **0 запусков**. Hikari JAR использовался только в compile-time classpath. `0D_RUNTIME=PENDING_RUNTIME`; LIVE-004/005 не начинались.

## Git и scope

Изменены только 24 code/test/output файла в code commit и этот отчёт с последним разделом `docs/phantoms/live-world/STATE.md` в documentation commit. Чужие dirty/untracked файлы, включая `PhantomMaterializationService.java` и два тестовых файла, не включены. Больше 10 файлов потребовалось из-за связанных production owners, focused tests и обязательных canonical outputs; отдельные artifact families не переделывались.

Git использовался по прямому разрешению TASK для baseline/branch/remote, exact scope guard, exact staging, двух commits и normal push. Команды read-only: `git status --short --branch`, `git rev-parse HEAD`, `git remote get-url origin`, `git status --short --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive`, `git diff --cached --name-only`, `git diff --cached --check`, `git diff --cached --stat`, `git diff --cached -- <PhantomSystem.java> <PhantomStatus.java> <PhantomTopologyService.java>`. Mutating commands: `git add -- <24 exact code/test/output paths>`; `git commit -m "Finish LIVE-003 code gates with explicit route and locality blockers"`; final documentation `git add -- <STATE.md> <LIVE-003-FINAL-CODE-GATE.md>` and documentation commit; normal exact-branch push. No broad add/restore/reset/merge/rebase or force push.
