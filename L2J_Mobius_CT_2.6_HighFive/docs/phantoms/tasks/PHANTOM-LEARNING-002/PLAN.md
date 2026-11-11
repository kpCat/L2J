# Training Director — план реализации

> Для исполнителей: выполнять проверяемыми задачами, использовать существующие
> тестовые маршруты и test-driven-development; перед завершением — независимое review.

**Goal:** автономный TEST-конвейер настоящих Solo/Party PvE эпизодов и обучения.
**Architecture:** отдельный Windows supervisor; штатный GameServer и bounded native
runner; SQL episode/dataset/checkpoint ledger; существующая residual MLP.
**Tech Stack:** Windows PowerShell 5.1, Java25, Ant, существующая MariaDB11.4/JDBC.
**Spec:** TASK.md, ARCHITECTURE.md, ACCEPTANCE.md, SAFETY.md, DESIGN_DECISIONS.md.

## Общие ограничения

- Только независимый clone от b85dd2a3, branch wip/phantom-learning-002.
- Пользовательский checkout, исторические runtime и защищённые ветки read-only.
- Реальные Player/CombatService/Party/Geo; штатные safety, EXP/SP/loot и leases.
- Runtime root `<clone>/.phantom-local/learning002/train`, schema
  `phantom_learning001_train002`, account learning001; defaults ports33327/17971/19271.
- Полный stock SQL installer; physical Geo203/protected207; G0 PID/FILETIME/SID/hash/socket.
- Никаких новых библиотек, SQL/обучения в Combat tick или потоков на актёра.
- Неподдерживаемые режимы — NOT_IMPLEMENTED. Кандидат может честно остаться DRAFT.
- Исходные файлы пакета неизменны. Exact final allowlist перед scoped commit/push.

## Review focus

1. START одновременно из двух консолей: единственный owner, durable lock.
2. PID reuse/чужой порт/перезапуск Windows: fail-closed и никакого чужого STOP.
3. Crash между EFFECT/SQL/COMMIT: INCOMPLETE и отсутствие повторного credit.
4. Движущийся dataset/повторный AUDIT: frozen membership и запрет подбора по аудиту.
5. Одна плохая сцена/нет модели: bounded SKIP/COLLECTING, честные exit codes и отчёт.

## Task 1 — contracts и наблюдения

Files: TrainingConfig.java, TrainingCollector.java; точечные LearningPolicy.java,
LearningCastEvidence.java, PhantomCombatService.java; PhantomTrainingContracts.java.

Interfaces:
- Config — UTF-8 Properties с version1, seed, classes/levels/grades/NPC,
  budgets/batch/threshold/retention/endpoints. Строгая проверка диапазонов и paths.
- Collector — `implements LearningPolicy.Observer`; `begin(String episodeId)`,
  `List<Observation> end()`, `Observation(String eventKey,String json)`.
- Policy observer — только opt-in in-memory callback: `issued(Attempt,Player,Monster)`
  и `recorded(Attempt,LearningStore.Evidence)`; Attempt дополнен bounded offered list,
  старый конструктор сохранён. Наблюдение не меняет native dispatch/HP/credit.
- Timing — bounded histogram полного decision path, p50/p95/p99 и max.

- [x] Focused RED: invalid config/modes/bounds; UNKNOWN masks; exact-key dedup/stale episode.
- [x] Реализовать contracts и bounded observer; exceptions observer не влияют на бой.
- [x] GREEN contracts и существующий learning001 regression.

## Task 2 — SQL episodes, frozen evaluation, export

Files: TrainingStore.java, TrainingEvaluator.java,
dist/db_installer/sql/game/phantom_training.sql, PhantomTrainingStoreChecks.java;
LearningStore.java только при необходимости package-level save metadata.

Interfaces native→store:
- `new TrainingStore(Path installation)` — validates owner marker `LEARNING002:<uuid>`,
  physical root, game/config/Database.ini, exact own schema/loopback/account.
- `initialize()`, `recover(String runId,String epoch)`, `long nextOrdinal()`.
- `String begin(String runId,String epoch,long ordinal,String scenario,long seed,
  String scope,String split,String beforeJson)` — consumes ordinal durably.
- `finish(String episodeId,String afterJson,List<TrainingCollector.Observation> actions,
  String outcome,long elapsedMillis)` — COMMITTED only with SQL-verified exact native keys.
- `abort(String episodeId,String reason)` — INCOMPLETE, never learning credit.
- CLI `TrainingEvaluator <train|status|report|export|probe> <installation>`;
  structured `<installation>/evaluation.json`, `status.json`, exports and report.md.
- `train` executes only with no live GameServer connections; automatic threshold,
  snapshots committed event membership, train/tune/audit actor split with context exclusion,
  candidate DRAFT history, per-scope constant/prior comparison, optional CAS acceptance.
- Expected model version/SHA for next cold batch in evaluation.json. Never MAIN promotion.

- [x] Cross-schema/duplicate/incomplete events, rejected candidate и checkpoint guards.
- [x] Migration, exact ledger и independent dataset history; bounded retention.
- [x] GREEN DB-free rules и own SQL integration under attested supervisor.

## Task 3 — реальные сценарии и эпизоды

Files: PhantomTrainingNative.java, PhantomTrainingActors.java, PhantomTrainingScenarios.java;
точечные reuse seams в PhantomLearning001ActorFixture/ShadowNative.

Interfaces:
- CLI `PhantomTrainingActors <installation> <configPath>` prepares fresh catalog/actors
  before CUT and reuses immutable existing manifests on restart. No synthetic event insert.
- CLI `PhantomTrainingNative <installation> <attemptId> <jarSha> <configPath>`.
- Per-attempt files: game.ready(pid), socket.ok(pid), stop.request, pause.request,
  batch.complete, native.out/native.err; existing root owner marker and DB config.
- One GameServer/Runtime/source authority per batch; shared owners keep monotonic generations.
- Seeded queue from actual catalog and config/coverage; at least three legal combinations,
  two templates if feasible. Rejected legal preflight logged and next selected automatically.
- New episode-local observer keys; no use of cumulative001 minimum as episode evidence.
- Canonical terminal consume, Party drain, STORE and no effects/claims before next episode.
- Rich observed facts with UNKNOWN; native reward/loot only from authoritative evidence.

- [x] Deterministic generation/coverage, unsupported class, all-infeasible bounded exit.
- [x] Generalized pre-CUT native setup via local helper reuse, no rule changes.
- [x] Multiple episode batch и durable begin/finish/abort through Task2.
- [x] GREEN focused tests; native Solo/Party/diversity proven in Task5.

## Task 4 — detached supervisor и семь команд

Files: tools/phantom-runtime/training/Training.ps1, TrainingOwner.ps1, TRAIN_CONFIG.ini,
TRAIN_START/STATUS/PAUSE/RESUME/STOP/REPORT/EXPORT.cmd, Test-Training.ps1.

Interfaces:
- Training.ps1 -Action Start|Run|Status|Pause|Resume|Stop|Report|Export|Preflight.
- Root based on actual script location, no user/drive hardcoding. Reuse G0SqlIdentity.ps1
  and Win32SqlIdentity.cs, existing installer manifest/StrictSqlScriptRunner Java route.
- START own exclusive lock + detached hidden PowerShell + redirected streams; START returns.
- Run builds own source if needed, stages physical owned runtime, provisions own SQL,
  calls actor preflight, native batch, stopped boundary evaluator, then next batch.
- Own identity includes PID/FILETIME/SID/image/config hashes/nonce; attest before signals.
- SQL kept until JVM stopped; no unknown PID termination. Durable requests safe at boundary.
- Crash resumes own SQL/JVM only on exact attestation, otherwise fails closed; incomplete
  epochs excluded from training. Previous attempts/logs/weights preserved.

- [x] RED→GREEN: invalid config/foreign identity; exact argv shadowing regression;
  повторная атомарная запись с кириллицей, исправлен PowerShell null-string binder.
- [x] Source: owner и CLI на G0/Win32, seven wrappers, full SQL installer,
  initialization mutex + lifetime file lock, receipts/requests/recovery.
- [x] Родительский процесс подтвердил parse/config/argv/atomic и read-only Preflight.
- [x] Detached parent-exit и реальные pause/resume/STOP проверены в Task5;
  presence/heartbeat smoke не подменяют native acceptance. Дополнительно закрыты
  Windows Job lifetime и startup ACK/pending race, см. PROGRESS.md.

## Task 5 — native acceptance и поставка

- [x] Expensive runs: hypothesis, changed inputs, proof и timeout записаны в ledger.
- [x] Compile-tests и jar sequentially; own full-schema SQL и Geo203/protected207.
- [x] Native Solo/Party, combinations/two templates, два новых COMMITTED после parent exit.
- [x] Native threshold автоматически запустил candidate+audit; accepted model1 cold read.
- [x] Pause/resume, controlled own crash/resume, idempotent commands, STOP/ports0.
- [x] Targeted Learning/Combat/Party/coordination/PvP/Support/commerce/F7/F8 routes.
- [x] Actual full-decision quantiles, startup/throughput/CPU/RAM/DB timings.
- [x] Independent review; выявленные дефекты закрыты focused regressions.
- [x] Финальный post-ACK запуск/остановка generation8 и cold readback: 117 unique events/model1.
- [x] Russian RESULT/report/runbook/handoff; финальные encoding/scope проверки перед staging.
- [ ] Exact final artifact scope guard, scoped commit, non-force push only new branch,
  independent GitHub HEAD readback and unchanged protected refs.

Единственная финальная пользовательская приёмка остаётся required; технические
доказательства перечисляются отдельно и не заменяют её утверждением агента.
