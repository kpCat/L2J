# Goal 026D — raid attempt invariants

## Status

`SUCCESS — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal 026 Checkpoint 5 остаётся `CHANGES_REQUIRED` до независимого review этого corrective Goal. Goal 026 overall — `IN_PROGRESS`; Goal 027 — `NOT_STARTED`.

`occurred_context_compaction: no`

## Summary

Исправлены только findings R026D-01/02/03:

- каждый вызов `PhantomPartyTactics.plan` из raid Attempt runtime получает exact Party-local snapshot map, а неполное, дублированное или пересекающееся Party evidence завершается fail-closed;
- attempt-time viability проверяет все required capabilities по живым `PHANTOM` и `REAL` через exact key/rank/intrinsic/learned без зависимости от `readyNow`;
- actionable PHANTOM support резервируется отдельным стабильным subset и исключается из offense, а `REAL` не получает Phantom-команд;
- active и terminal Attempt используют сохранённые lifecycle evidence и не зависят от удержания CP4 `ReadyReceipt`;
- `raid.prepare` сначала вызывает Attempt и допускает Assembly только при typed `WAITING_FOR_READY`; после READY Attempt повторно вызывается в том же bounded step;
- replacement goal revision отменяет старое runtime ownership до чтения readiness новой Assembly.

Accepted ENTRY_GATED, Queen Ant, Zaken 83, script adapter, raid Combat, canonical death/native loot и CP1–CP4 semantics не менялись.

## Mandatory reading and local reuse

Прочитаны `Agents.md`, master plan, workflow/task standards, весь package Goal 026D, предыдущий CP5 report, релевантные Goal 026/027 секции roadmap, целевые runtime/service/decision файлы, неизменённый `PhantomPartyTactics`, Party/Member contracts, focused CP5 Attempt/Decision suites, CP5 dynamic Combat fixture и Ant/launcher seams.

Переиспользованы локальные паттерны: caller-driven bounded state, `AssemblyIdentity`/`AttemptIdentity`, stored `ReadyReceipt`, `RuntimeStatus.PROVIDER_UNAVAILABLE`, `MemberRef.stableKey()`, существующие PartyTactics/Combat external-action paths и memory fake ports.

## Changed files

- `java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAttemptRuntime.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAttemptService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidDecision.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidAttemptRuntimeGoal026DSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidAttemptGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidDecisionGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/reports/026d-raid-attempt-invariants.md`

User-owned untracked task package files, включая `docs/phantoms/tasks/026d-raid-attempt-invariants/`, не изменялись и не включаются в commit.

## Architecture decisions

### R026D-01

Runtime строит `Map<MemberRef, MemberSnapshot>` отдельно для каждой `PartySnapshot`. Проверяются limit `<=9`, отсутствие duplicate roster entries, наличие exact snapshot каждого участника, отсутствие повторного назначения участника другой Party и полное покрытие current force. `PhantomPartyTactics` и Goal017 guard не менялись.

### R026D-02

Hard viability отделена от command reservation. Для каждого required requirement считаются все живые `PHANTOM` и `REAL` с exact capability key, достаточным rank и `intrinsic && learned`; `readyNow` и наличие action skill не влияют на viability. Затем только PHANTOM с usable action skill стабильно сортируются и резервируются для heal/resurrection/recharge до required minimum subset. Резерв исключается из offense.

### R026D-03

`PhantomRaidAttemptService.advance` выполняет: goal validation/identity → cancellation replaced live Attempt → exact terminal replay → exact active advance → чтение `ReadyReceipt` только для нового Attempt. Typed `WAITING_FOR_READY` является единственным разрешением для Decision вызвать Assembly. Stored `_ready` остаётся источником retreat evidence live Attempt.

## DB, migrations and configs

DB, migrations, data files и runtime config keys не менялись.

## Commands and test results

- `ant phantom-raid-attempt-runtime-goal026d-test` — не запускался: `ant` отсутствовал в PATH, exit 1 до build/test.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-raid-attempt-runtime-goal026d-test` — PASS 4/4, seed `26002653`.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-raid-attempt-goal026cp5-test` — PASS 9/9, seed `26002653`; после усиления explicit no-ReadyReceipt-read assertion повторно PASS 9/9.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-raid-decision-goal026cp5-test` — PASS 2/2, seed `26002653`.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat jar` — единственный запуск, `BUILD SUCCESSFUL`; LoginServer/GameServer/DatabaseInstaller JAR собраны, server JAR скопированы в `dist/libs`.

Aggregate не запускался. CP1, CP3, CP4, raid Combat, GameKnowledge, Zaken compile, broad Goal017, broad Combat, plain `ant verify`, Goal025, all-Phantom и stress loops не запускались.

## Required evidence

- R026D-01: actual `L2jPhantomRaidAttemptRuntime` выполнен с двумя Party по 6 участников, total 12; PASS без PartyTactics bound exception, recorder подтвердил exact Party-local target sets без cross-Party members.
- R026D-02: смерть последнего tank при 8 живых из minimum 8 дала `PROVIDER_UNAVAILABLE`; живой tank с `readyNow=false` продолжил mechanic; exact REAL tank сохранил viability и отсутствовал среди controlled actors; стабильный первый PHANTOM healer получил support, второй не был сверхрезервирован, первый не вошёл в raid offense, второй вошёл.
- R026D-03: после удаления fake CP4 receipt live Attempt сохранил identity/authority и не читал AssemblyPort; terminal replay вернул тот же receipt без runtime replay и без AssemblyPort read; active/terminal Decision не вызывал Assembly; waiting/READY path вызвал Attempt → Assembly → Attempt; revision replacement зафиксировал `runtime.cancel` перед новым Assembly readiness read.

## Performance, concurrency and lifecycle

Новые worker/thread/Future отсутствуют. Existing caps `64` live / `256` terminal сохранены. Viability и Party-local validation ограничены текущим bounded force; глобальных scans, DB I/O и новых hot-path logs нет. Replacement cancellation выполняется синхронно до нового Assembly work.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.

## Deviations and diagnostics

Built-in `apply_patch` не смог читать существующий workspace file из-за Windows `apply deny-read ACLs`. Existing files изменялись bounded exact line/text replacements с count validation и temporary sibling file; новый test/report созданы через `apply_patch`.

## Limitations and risks

- Verdict не является self-accept: требуется независимый review Goal 026D.
- Универсальный epic solver, новые encounter semantics, CP1 readiness и REAL control остаются вне scope.
- Отсутствующая geodata остаётся ранее зафиксированным общим ограничением и этой задачей не затрагивалась.

## Git and delivery

- branch: `feature/phantom-world`
- required parent: `8a4a0a6972d4e7387f564b45358c77b76b695c43`
- exact commit subject: `fix(phantoms): harden raid attempt invariants`
- commit SHA: определяется создаваемым commit; фактический SHA приводится в final handoff
- push target: `origin feature/phantom-world`
- remote HEAD after push: проверяется после push и приводится в final handoff
- amend/rebase/squash/reset/force-push не выполнялись

Git-команды использовались только по прямому разрешению task/project workflow для parent/scope/diff/delivery verification; точные команды перечисляются в final handoff.

## Next step

Независимый review Goal 026D по R026D-01/02/03. До verdict Goal 026 остаётся `IN_PROGRESS`, Goal 027 — `NOT_STARTED`.
