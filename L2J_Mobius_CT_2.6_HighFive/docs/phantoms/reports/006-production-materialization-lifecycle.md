# Goal 006 — production materialization lifecycle

## Статус, baseline и gate

Статус Goal 006: `FIX_REQUIRED`.

```text
Baseline: 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
Parent baseline: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Branch: feature/phantom-world
Goal 006 architecture direction: ACCEPT
Goal 006 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 006A: REQUIRED
Goal 007: NOT_STARTED / BLOCKED
```

Goal 005 закрыта отдельным independent review как `ACCEPT`; commit, parent,
exact remote, три headless run, profile suite и два финальных verifier run
перенесены в обновлённый отчёт и review Goal 005. Принятые SHA-256:

- provisioning aggregate:
  `20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E`;
- verifier Goal 005, два byte-identical run `69/69`, восстановленный из
  локального сохранённого final handoff:
  `483B6CAD90CEAE55E282E492639DA6253F754424FDD7EB8DB57A41B23B966E97`;
- verifier Task 004B, два byte-identical run `66/66`:
  `39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9`.

Независимое ревью приняло архитектурное направление Goal 006, но обнаружило
четыре обязательных finding: неполный World/autosave identity preflight,
гонку action admission с `STOPPING`, отсутствие wall-clock bound для caller
`shutdown` и ошибочную provenance Goal 005. Revert не требуется; закрытие
выполняется только Goal 006A. Goal 007 не начата и остаётся заблокированной.

## Config и disabled behavior

Добавлен единственный operator limit:

```text
MaxMaterializedPhantoms = 32
```

Default config остаётся `False / False / 32`. Effective disabled settings имеют
cap `0` и не открывают repository/service. Enabled cap принимается только как
unsigned base-10 `1..10000`; missing, blank, signed, zero, malformed или
out-of-range значение отключает всю subsystem fail-closed. Diagnostics
эффективны только при enabled system.

Configured production startup внутри `PhantomSystem`:

```text
scheduler start
→ PhantomProfileRepository.open() и schema validation
→ PhantomMaterializationService.start()
→ RUNNING
```

Configured disabled path возвращается до production instance construction:
profile/materialization DB queries равны нулю. Shutdown сначала drain-ит service
и только после terminal service `STOPPED` останавливает scheduler. Failed drain
сохраняет configured instance для второго explicit shutdown.

## Единый lifecycle и совместимость spike

Production core `PhantomMaterializedPlayer` является единственной реализацией
per-actor state machine:

```text
STORED / CLAIMED / LOADING / MATERIALIZING / ACTIVE / DEMATERIALIZING / FAILED
```

Core владеет PHANTOM identity lease, canonical `Player`, headless outbound
attachment, tokenized action admission, retryable cleanup и immutable snapshot.
Он не знает repository, global maps, cap, gameplay policy или fixture item.

`PhantomPlayerMaterializationSpike` сокращён до compatibility wrapper над core.
В wrapper остались только Task 004 failure-point mapping, fixture baseline и
reversible fixture action. `Player.load`, online/spawn/store/delete и admission
больше не продублированы. Старый headless suite после extraction прошёл `18/18`.

## Production service, ownership и concurrency

`PhantomMaterializationService` не singleton и принадлежит configured
`PhantomSystem`. Service реализует `NEW / RUNNING / STOPPING / STOPPED / FAILED`
и явные start/materialize/dematerialize/retry/find/list/shutdown/snapshot/
recovery operations.

Materialization делает:

1. repository lookup;
2. проверку linked positive character ID;
3. conditional reservation profile и captured character;
4. fair bounded permit;
5. не более одной on-demand retained recovery;
6. PHANTOM claim;
7. `Player.load` с exact object-ID validation;
8. output/domain initialization/online/spawn;
9. открытие admission и публикацию `ACTIVE`.

Две `ConcurrentHashMap` и exact `putIfAbsent/remove(key, entry)` дают одного
owner на profile и character. Concurrent same-profile test получил ровно одного
`SUCCESS` и одного `ALREADY_ACTIVE`. Link change во время ACTIVE не retarget-ит
actor. Fair `Semaphore` удерживается до terminal `STORED`; cap-one test доказал
reject, release и readmission. Общий state monitor не удерживается на
`Player.load`, World, store/delete или DB operation; slow lifecycle
сериализуется только на конкретном entry. Per-phantom executor/future/thread нет.

Missing и unlinked profile возвращают отдельные результаты. Service start имеет
active zero и ничего не materialize-ит автоматически. Snapshots immutable и
отсортированы по profile ID.

## Action admission, cleanup и retry

`ActionLease` выдаётся только в `ACTIVE`, закрывается ровно один раз и безопасен
при double/stale close. Cleanup сначала закрывает admission, затем ждёт уже
выданные tokens в bounded timeout; новый token в это время отклоняется.

Cleanup сохраняет порядок stop tasks → store → delete → object-ID postconditions
→ detach output → release identity → exact map removal → permit release →
`STORED`. Timeout и injected store failure оставили Player, PHANTOM lease, maps и
permit; explicit `retryCleanup` после снятия fault достиг `STORED`. Production
service/core не ссылаются на `PhantomActionFacade.FIXTURE_ITEM_ID`.

## Shutdown и safe restart

Shutdown запрещает новые materialization/action admissions, проходит entries в
стабильном profile-ID order, выполняет не более двух passes и использует общий
budget не более 10 секунд.

Проверены:

- drain двух actors;
- one-time failure, закрытая одним immediate retry;
- persistent failure с exact failed profile ID и service `FAILED`;
- сохранение identity/maps/permit после persistent failure;
- второй explicit shutdown после снятия fault, достигший `STOPPED`.

Runtime ACTIVE state не записывается. Новый service/repository после stop имеет
active zero, видит сохранённый profile, не находит lifecycle component и
materialize-ит его только по явному запросу.

## RETAINED REAL_LOGIN recovery

Identity entries получили состояния `RESERVED` и `RETAINED`. `Disconnection`
переводит только matching REAL_LOGIN lease в `RETAINED`, если cleanup бросил
ошибку или postconditions неполны.

Recovery доступна только explicit или как одна on-demand попытка
materialization того же character. Перед conditional removal того же token
обязательны:

```text
owner REAL_LOGIN
state RETAINED
World player отсутствует
World object отсутствует
autosave object ID отсутствует
prepared SELECT online FROM characters WHERE charId=? возвращает одну строку
online == 0
```

Проверены clean recovery, PHANTOM block, live RESERVED rejection, World
player/object residue, autosave residue, DB online `1` и `2`, missing character
row и stale-token replacement. RESERVED owner не освобождается. Periodic,
age-based, startup release-all или background retry отсутствуют.

## Metrics и diagnostics

`PhantomMetrics` расширен фиксированными `AtomicLong` counters:
requested/succeeded/rejected materialization, retained materialization/cleanup
failures, successful dematerialization, retained recovery success/reject,
shutdown failures, active current/peak. Per-profile metric map нет.

Существующий bounded sampled trace записывает только короткие internal event
names с profile/character ID. Test с capacity 2 подтвердил overwrite accounting.
INFO/WARNING per action не добавлены.

## Follow-up Goal 005

`PhantomProfileComponent.equals/hashCode` теперь сравнивает payload через
`Arrays.equals/hashCode`. Tests проверили separately loaded equality, equal hash,
different payload и defensive copies.

`PhantomProfilePersistenceSuite` больше не использует unqualified delete. Она
отслеживает exact owned profile IDs, удаляет только их prepared statement,
проверяет foreign sentinel после каждого cleanup и удаляет sentinel отдельно в
final teardown. Suite сохранила `18/18`.

## Тесты и performance

Seed всех routes: `20260725001`. Test DB:
`l2jmobiush5_phantom_test`.

Три независимых production-materialization run (`three consecutive runs`):

| Run | Result | Cases | Ant time |
|---|---:|---:|---:|
| 1 | PASS | 16/16 | 25 s |
| 2 | PASS | 16/16 | 33 s |
| 3 | PASS | 16/16 | 27 s |

Performance smoke выполнил один и десять последовательных production lifecycle
cycles: `2/2`, без World/autosave/lease/permit/thread residue.

Уже выполненные targeted regressions:

| Command | Result |
|---|---:|
| `ant compile-tests` | PASS; 1913 production + 29 test sources |
| `ant phantom-profile-persistence-test` | PASS; 18/18 |
| `ant phantom-headless-player-test` | PASS; 18/18 |
| `ant phantom-skeleton-test` | PASS; 12/12 |
| `ant phantom-production-materialization-test` ×3 | PASS; 16/16 each |
| `ant phantom-production-materialization-performance-smoke` | PASS; 2/2 |
| `ant phantom-db-test` | PASS; 9/9 |
| `ant test` | PASS; harness 66/66, intentional controls expected |
| pre-commit `ant verify` | PASS; all cumulative routes, 70 s |
| pre-commit `ant jar` | PASS; 1913 production sources, 14 s |
| pre-commit `verify-task-006.ps1` | PASS; 72/72 |
| production JAR inspection | PASS; test entries 0, materialization entries 20 |

Отдельные обязательные проверки изменённых файлов:

- mojibake-маркеры в изменённых файлах проверены: 32 files, 0 matches;
- escaped Cyrillic в изменённых файлах проверены: 32 files, 0 matches.

Post-commit verify, jar, два byte-identical verifier outputs, commit SHA, push и
remote ref являются external final-handoff evidence и выполняются после commit
этого отчёта.

## DB, schema и production safety

Schema/migrations не менялись. Runtime lifecycle ничего не записывает в profile
components. Recovery выполняет только prepared read `characters.online`.

```text
Production DB: no access
l2jmobiush5: no read, no write, no mutation
Test DB only: l2jmobiush5_phantom_test
```

Production `GameServer.jar` содержит `0` entries из
`org/l2jmobius/tests/`; production materialization core/service представлены 20
class entries.

## Scope, deviations и ограничения

Изменения ограничены High Five и одной lifecycle artifact family:
config/System/metrics, identity/recovery/core/service, минимальная real-login
retention integration, profile equality follow-up, tests/build/verifier и
обязательные closure/contract/report документы. Другие хроники, dependencies,
schema, `Player`, `GameServer`, `Shutdown`, `PlayerAutoSaveTaskManager`, protocol
и старые verifiers не менялись.

Task package задаёт bounded scope больше обычных 8–10 файлов; это ожидаемое
исключение для Goal 006. Дополнительно изменён один не перечисленный fixture:
`test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java`. Причина —
два Task 003 enabled-config cases не содержали новый обязательный cap и
противоречили одновременно требуемым strict missing-cap rejection и сохранению
всех regressions. В них только добавлено
`MaxMaterializedPhantoms = 32`; production contract не ослаблен.

Публичный constructor `PhantomSystem(Settings)` сохранён как совместимый inert
Task 003 test path. Единственный фактический GameServer entrypoint
`startConfigured()` использует production-owned repository/service path.

Auto materialization, scheduler activity states, population, AI, navigation,
economy и Goal 007 не реализовывались.

## Команды Git

Git использовался, поскольку Task 006 прямо требует baseline audit, ordinary
commit и push. До production edits выполнены read-only команды:

```text
git status --short --branch
git branch --show-current
git rev-parse --abbrev-ref --symbolic-full-name @{upstream}
git rev-parse --show-toplevel
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
git diff --name-status f5b66c4edf1ddf18e044ef8c692d70ecea616485..9d0465eb62f9913644fab9f1d60feb2f4fd9a674
```

Final scope/diff inspection, commit и push перечисляются в external final
handoff после успешного выполнения.

## Финальный gate

```text
Goal 006 architecture direction: ACCEPT
Goal 006 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 006A: REQUIRED
Goal 007: NOT_STARTED / BLOCKED
Result: FIX_REQUIRED
```

Полный verdict и findings зафиксированы в
`docs/phantoms/reviews/006-production-materialization-lifecycle-review.md`.
