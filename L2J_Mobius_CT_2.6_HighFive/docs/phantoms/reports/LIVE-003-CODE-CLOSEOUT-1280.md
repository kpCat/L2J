# LIVE-003-CODE-CLOSEOUT-1280 — GREEN_PARTIAL

Дата: 25.09.2026. Ветка: `feature/phantom-world`. Исходный SHA: `1f15f53b2a9c09603980121e69492f7ada228eef`. SHA проверенного кода: `2ffa764e80a9652b44b21272946ca4672b60e281`.

Это один code closeout A–F/H/I. `0D_RUNTIME=PENDING_RUNTIME`. Никакие GameServer/LoginServer, DB, Hikari runtime, live scale, `ant jar` и full `ant verify` не запускались. Hikari JAR читался только компилятором разрешённых Ant targets.

## Итог по областям

| Область | Результат | Проверенное и оставшееся |
|---|---|---|
| A | GREEN_PARTIAL: `PRESENCE_INTEGRATION_BLOCKED` | `PhantomPresenceRegistry` хранит AVAILABLE/BUSY/OFFLINE отдельно от activity и Player; PopulationManager публикует расписание, BackgroundService не допускает обычный farm при OFFLINE/BUSY. Внешние exclusive party/trade/raid owners ещё не публикуют BUSY claims. |
| B | GREEN_PARTIAL: `BACKGROUND_CADENCE_INTEGRATION_BLOCKED` | Production `PhantomScheduler` назначает детерминированный due 300000–900000 мс, первый award ждёт due. При включённой ecology один due использует durable calendar/0C cursor для всего прошедшего интервала. Базовый `EnablePhantomEcology=False` не имеет periodic calendar owner; ordinary farm там fail closed, а не урезает 10 минут до 60 секунд. Runtime gate не выполнялся. |
| C | GREEN_PARTIAL: `PERIODIC_IDEMPOTENCE_BLOCKED` | Pure port: duplicate due не начисляет повторно; сбой до и после durable save восстанавливается с одним request ID и 10/10 минутами. 0C HistoricalBackgroundService сохраняет существующие operation key/receipt/cursor contracts. Полная real receipt/ack, stale-authority и boundary матрица не доказана без запрещённых runtime targets. |
| D | BLOCKED: `PROGRESSION_ROUTE_BLOCKED`, `SPOIL_EVIDENCE_BLOCKED` | Existing progression catalog/operations tests GREEN. Новый factual LIVE-002 replay для Human/Dwarf/alternate на 1/20/40/76/85 не выполнен; `LIVE003_PROGRESSION_ROUTE_PROOF.tsv` честно помечает все строки `NOT_PROVEN`. Ordinary farm остаётся death-drop only; acquisition capability truth `profession.spoil`/`profession.sweep` найден, но перенос в ordinary path без доказательства skill evidence не сделан. |
| E | GREEN_PARTIAL: `LOCATION_INDEX_INTEGRATION_BLOCKED`, `MATERIALIZATION_POLICY_BLOCKED` | Scheduler materialization теперь сначала сверяет durable cursor; pure 100 циклов не оставляют lifecycle ownership. Уже существующий topology registry имеет node buckets, но production position wiring и 10k local-only query не добавлены. Scenic remote teleport не вводился. |
| F | GREEN | v1 байты не менялись; SHA-256 `23B12FC523DE83D1BDEA54A75677823B2E8129978792A9C9FEC684ACCFAA4748`. v2 SHA-256 `D555AB8DDA0783D0736D3AADB0BD3D1B76481C73F0A34656CDF5C70FB88FBBC6`. Новые профили выбирают v2, только exact v1 predecessor проходит validation, неизвестные hash/ID/phase отвергаются. Имена template и phase bounds прежние; 168/168 часов имеют ACTIVE ≥64, минимум 255, максимум 884. DB rewrite/reset/reseed нет. |
| G | GREEN | Предыдущий deterministic nickname gate: 10 000, reserved 2336, дубликаты/исчерпания 0. |
| H | GREEN_PARTIAL: `OBSERVABILITY_INTEGRATION_BLOCKED` | Existing `operatorStatus`/`.phantomstatus` показывают presence, activity, due/overdue/running/blocked, queue, cadence и world materialized; diagnostics=false operator suite GREEN. Index profiles/buckets ждут E. |
| I | GREEN_PARTIAL: `SCALE_CONTRACT_REGRESSION` | Pure production-policy 10 000 профилей за 24 ч: 1 366 738 dispatches, cadence min/mean/max 300049/599767/899925 мс, максимум общей due queue 10 000, Player=0, per-profile future=0. Это не измерение CPU/SQL и пока не включает index/query metrics. |

## Владение состоянием

- Расписание и presence: `PhantomPopulationManager` → `PhantomPresenceRegistry`.
- Общий due и ограниченная очередь: `PhantomScheduler` → `PhantomSchedulerPolicy`; отдельных profile timer/thread/future нет.
- Минутный durable reconciliation: `PhantomPopulationEcologyService` → существующий `PhantomHistoricalBackgroundService`/0C; pure crash/retry проверен.
- Операция, receipt и экономическая запись: существующие `PhantomBackgroundService`/`PhantomBackgroundTransaction`; schema не менялась.
- Player и identity lease: существующий `PhantomMaterializationService`; scheduler adapter сверяет cursor до входа.
- Счётчики оператора: существующие `PhantomSystem.operatorStatus` и `.phantomstatus`.

## Проверки

Разрешённый DB-free прогон завершился `BUILD SUCCESSFUL`: `ant compile-tests`, `test-live003-admission` 11/11, `phantom-activity-scheduler-test` 23/23, `phantom-population-ecology-goal033-test` 13/13, `phantom-population-schedule-test` 3/3, `phantom-population-catalog-test` 3/3, `phantom-population-humanization-goal030a-test` 5/5, `phantom-background-model-test` 7/7, `phantom-progression-catalog-test`, `phantom-progression-operations-test` 36/36, `phantom-live003-core-life-1280-test` 6/6, `phantom-operator-observability-goal028cp1-test` 6/6. Operator target был предварительно проверен на отсутствие `phantom.test.config`. После последней узкой правки `BackgroundService` повторный `ant compile-tests` — GREEN. Guarded DB/integration/production-materialization targets не запускались.

Два свежих output run дали побайтно одинаковые TSV:

- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

| Файл | SHA-256 |
|---|---|
| `LIVE003_WEEKLY_SCHEDULE_1280.tsv` | `E3DE6279FF67485A59339FB5E3221ABCA9639F224D341DEB06F398963F9ED1C1` |
| `LIVE003_NICKNAME_10000_DRYRUN.tsv` | `CE7128A2ABF25E8D3AE910A39E66E2A34F05DE44E8F0055DFB361594946C4C1E` |
| `LIVE003_CORE_SCALE_10000.tsv` | `013EDC045D07C982958843FED69613B7B8530A7A183DA09D9370553E4E2ED273` |

`LIVE003_CORE_LIFE_MATRIX.tsv` содержит точный статус каждой области. Чужие рабочие изменения, включая `PhantomMaterializationService.java` и два не относящихся к closeout test файла, не включены в code commit.

Git использовался только по явному разрешению задачи для baseline/ветки/remote, точного scope guard, exact staging, коммита и push; broad restore/reset/merge/rebase не выполнялись. После этого checkpoint работа останавливается: runtime 0D, LIVE-004 и LIVE-005 не начинались.
