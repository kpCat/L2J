# Goal 013B — Durable CLASS skill learning transaction

## Status

`SUCCESS — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

- branch: `feature/phantom-world`;
- required parent:
  `06929a2973ca2450688d413b4d58de034194053f`;
- independently accepted pre-013 baseline:
  `8dba87e9c1d5828376b80c1ea16c4578726d4947`;
- subject: `fix(phantoms): make class skill learning durable`;
- commit: один ordinary child; exact SHA и push result указываются во внешнем
  final handoff, потому что этот отчёт входит в тот же commit;
- Goal 013: `FIX_REQUIRED after first review`;
- Goal 013A: `FIX_REQUIRED after durability review`;
- Goal 013B: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 014: `NOT_STARTED / BLOCKED`;
- Goal 015/017/025: `NOT_STARTED`.

До изменений local и remote branch указывали на required parent. Единственным
pre-existing изменением был предоставленный untracked task package Goal 013B;
он признан входным артефактом и включён в exact allowlist.

## Summary

Добавлена одна bounded production-граница
`PhantomClassSkillLearningTransaction`. Для exact CLASS learning она атомарно
изменяет optional exact item row, main/subclass SP row и exact class-indexed
`character_skills` row на одном MariaDB connection.

Runtime `Player` не меняется до commit. После commit выполняются exact inventory
reconciliation, runtime SP, `Player.addSkill(skill, false)`, shortcut update,
fresh runtime check и fresh-connection DB check. Только после них backend
возвращает `SUCCESS` и dispatch-ит `OnPlayerSkillLearn`.

Post-commit invariant failure возвращает
`DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED`, переводит progression service в
`FAILED` и не выполняет компенсацию. Durable DB остаётся authority для reload.

## Read-first source audit

Прочитаны:

- `Agents.md`, master plan, roadmap, workflow/task/report contracts;
- Goal 013, Goal 013A и Goal 013B task packages, reports и architecture
  contract;
- все production progression classes и связанные Goal 013/013A tests,
  launcher, build и verifier;
- canonical `RequestAcquireSkill`;
- `Player.addSkill`, `storeSkill`, `store`, main/subclass SP, class-index skill
  restore/store и `setSp`;
- `ItemContainer.destroyItem`, `PlayerInventory`, exact `Item` persistence;
- materialization `ActionLease`;
- actual `SHOW CREATE TABLE` для `characters`, `character_subclasses`,
  `character_skills`, `items` только в `l2jmobiush5_phantom_test`.

Локальные аналоги:

- transaction/rollback/affected-row pattern
  `PhantomProfileRepository.write`;
- DB rollback integration pattern
  `PhantomTestDatabaseIntegrationSuite`;
- canonical trainer/precondition semantics `RequestAcquireSkill`;
- exact runtime object mutation `Player.destroyItem(..., Item, ...)`.

## Architecture decisions

Lock order:

```text
profile operation slot
ActionLease
final plan/token check
synchronized exact Player
synchronized exact Item, when present
MariaDB transaction
commit
runtime reconciliation
fresh runtime + fresh DB postconditions
OnPlayerSkillLearn
```

Durable row-lock order:

```text
characters(charId)
  or character_subclasses(charId, class_index)
character_skills(charId, skill_id, class_index)
items(object_id), when present
```

Mutation order:

```text
exact item UPDATE/DELETE
exact main/subclass SP UPDATE
guarded skill INSERT/UPDATE
commit
```

Все writes требуют `affectedRows == 1`. Первый skill level использует
`INSERT`; upgrade — `UPDATE` с exact previous level. `REPLACE` не используется.
JDBC statements имеют bounded query timeout.

## Runtime/durable ownership

| State | Runtime owner | Durable owner |
|---|---|---|
| skill | exact active `Player` skill map | `character_skills(charId, skill_id, class_index)` |
| main SP | `PlayerStat` base SP | `characters(charId).sp` |
| subclass SP | active `SubClassHolder` SP | `character_subclasses(charId, class_index).sp` |
| required item | exact `Inventory` + exact `Item(objectId)` | `items(object_id, owner_id, item_id, loc, count)` |

## Success, rollback and conflicts

Focused durability suite доказала:

- real trainer main-class zero-item commit;
- exact SP и skill DB rows через fresh connection;
- dematerialize/materialize restart proof;
- repeated request `IDEMPOTENT` без второго cost/event;
- real subclass row update, unchanged base `characters.sp`, exact
  `character_skills.class_index=1`;
- main → subclass → main reload isolation;
- exact one-item object selection and one committed decrement/remove;
- same-profile race: один `SUCCESS`, один `OPERATION_IN_PROGRESS`, один cost;
- concurrent `Player.storeMe` не перезаписывает committed SP;
- typed SP/skill/item/class drift conflicts;
- operation slots и actor leases возвращаются к нулю.

Fault matrix:

| Fault point | Result | Runtime | Fresh DB |
|---|---|---|---|
| `BEFORE_ITEM_SQL` | rollback | baseline | baseline |
| `AFTER_ITEM_SQL` | rollback | baseline | baseline |
| `BEFORE_SP_SQL` | rollback | baseline | baseline |
| `AFTER_SP_SQL` | rollback | baseline | baseline |
| `BEFORE_SKILL_SQL` | rollback | baseline | baseline |
| `AFTER_SKILL_SQL` | rollback | baseline | baseline |
| `BEFORE_COMMIT` | rollback | baseline | baseline |
| `BEFORE_POSTCONDITION_READ` | fail-stop after commit | committed reconciliation | committed authority |

Postcondition injection также доказала rejection новых mutations,
drain-safe shutdown и reload committed state.

## Event evidence

Test-only listener на real trainer получил ровно три события для трёх успешных
backend transactions: main, concurrent-main winner и subclass. Idempotent
requests, competing same-profile request, pre-commit faults и post-commit
fail-stop не создали дополнительных событий. Callback видел exact runtime skill
и fresh DB skill row, то есть событие следовало после обеих postconditions.

## Database, migrations and configuration

- использовалась только `l2jmobiush5_phantom_test`;
- schema aggregate:
  `20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E`;
- production DB не использовалась;
- `characters`, `character_subclasses`, `character_skills`, `items` —
  existing InnoDB tables;
- migrations/schema/profile schema: без изменений;
- config: без изменений;
- deterministic seed: `13001302`.

## Tests and commands

Apache Ant отсутствует в PATH; использован bundled Ant из локального NVIDIA
toolkit:

```text
"C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v12.8\libnvvp\plugins\org.apache.ant_1.9.2.v201404171502\bin\ant.bat" ...
```

Focused results текущей final implementation:

- durability suite: `15/15`, обязательная матрица `3/3 PASS` на seed
  `13001302`; дополнительный fixed-target контроль также `15/15`;
- progression server integration: `28/28`, обязательная матрица `2/2 PASS`
  на seed `13001302`;
- progression operations: `36/36`;
- Goal 013/013A focused regressions и all historical Phantom regressions:
  `PASS` в cumulative `ant verify`;
- combat/CP regressions: final cumulative `PASS`;
- `ant verify`: `PASS`, `BUILD SUCCESSFUL`, `6 minutes 26 seconds`;
- standalone `ant jar`: `PASS`, `BUILD SUCCESSFUL`, `17 seconds`;
- Goal 013B verifier: два прогона `58/58`, output `4006` bytes,
  byte-identical; SHA-256 обоих:
  `8364FEE35EF66D832865BDCC6661738FD9CE2FE4A8AF29B7C78A1ADFE42F4B63`.
- mojibake-маркеры в изменённых файлах проверены: `PASS`;
- escaped Cyrillic в изменённых файлах проверены: `PASS`.

Historical default seed `20260725001` сохранён для старого harness checksum.
Только новый target `phantom-progression-durability-test` жёстко связан с
`phantom.goal013b.seed=13001302`; два server integration повтора запускались с
явным `-Dphantom.test.seed=13001302`.

Промежуточные failures сохранены честно:

1. первая compile попытка обнаружила non-effectively-final lambda fixture;
   исправлена, повторная compile прошла;
2. первые durability fixture attempts не нашли подходящий subclass zero-item
   learn и first-level item learn; fixture приведён к real level-74 subclass и
   real upgrade `SkillLearn` с одним required item;
3. первый `ant verify` с временно изменённым global default seed обнаружил
   hard-coded historical harness checksum; default возвращён к baseline;
4. следующий cumulative run доказал все executable gates до durability, после
   чего fixed seed был привязан только к новому Goal 013B target;
5. следующий полный run прошёл все executable regressions и остановился на
   устаревшей seed-проверке нового verifier; verifier исправлен и прошёл 58/58;
6. accepted combat regression `canonical-player-ai-attack-and-death` дважды
   воспроизвёл timing race: test ждёт terminal result, но не
   `CleanupState.COMPLETE`. Combat/CP scope не менялся. Третий unchanged
   targeted run прошёл 20/20, после чего полный `ant verify` прошёл;
7. после этих corrections final focused и cumulative suites прошли полностью.

## Performance and lifecycle

Focused transaction/reload/fault/concurrency matrix:

```text
elapsedMillis=11772
boundMillis=120000
operationsAfter=0
actorLeasesAfter=0
productionWorkersAdded=0
productionTasksAdded=0
productionFuturesAdded=0
```

No per-phantom thread, executor, retry loop или hot-path logging не добавлены.
JDBC query timeout и existing service/action drain ограничивают ожидание.
Focused measured transaction matrix укладывается в required 120-second bound;
GameServer bootstrap в Ant elapsed этого bound не касается.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomClassSkillLearningTransaction.java`;
- `java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java`;
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java`;
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java`;
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionStepHandlers.java`.

Tests/build/tools:

- `build.xml`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomProgressionDurabilitySuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `tools/phantoms/verify-task-013b.ps1`.

Documentation:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md`;
- этот report;
- полный task package
  `docs/phantoms/tasks/013b-durable-class-skill-learning-transaction/`.

## Scope and deviations

- ordinary `Player`, `RequestAcquireSkill`, `Item`, `Inventory`: без изменений;
- accepted Game Knowledge: без изменений;
- combat/CP, materialization, scheduler, profile schema: без изменений;
- other chronicles, config, geodata, `.gitignore`: без изменений;
- Goal 014/015/017/025: `NOT_STARTED`;
- новых libraries/framework/build systems нет.

Deviation from TASK.md: нет. Расширение более чем на десять paths является
bounded exception, прямо заданным Goal 013B allowlist; production change
ограничен пятью existing progression files и одним dedicated facade.

## Known limitations and risks

- поддерживается только отсутствие required item либо один distinct item ID,
  полностью покрываемый одним exact inventory object;
- distributed item cost и несколько distinct required item IDs fail closed;
- outbox/journal/compensation/schema change намеренно не добавлялись;
- ambiguous/post-commit failure переводит service в fail-stop; автоматической
  компенсации нет, DB является authority для reload;
- Goal 013/013A/013B не self-accepted и требуют independent review exact child.

## Git

- branch: `feature/phantom-world`;
- parent:
  `06929a2973ca2450688d413b4d58de034194053f`;
- commit: один ordinary child с exact subject; SHA — во внешнем final;
- push: `origin/feature/phantom-world`, result — во внешнем final;
- amend/rebase/squash/merge/force push: не использовались.

Git-команды использовались, потому что task и project workflow явно требуют
branch/parent/scope/diff/commit/push verification. До commit выполнялись только
read-only `status`, `rev-parse`, `show`, `diff`, `ls-files`; mutation будет
ограничена одним `git add`, одним ordinary `git commit` и обычным `git push`.

## Recommended next step

Independent review exact Goal 013B child commit. Goal 014 не начинать до
принятия этого gate.
