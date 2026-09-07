# Goal034 closure 5 — restart ecology-fence resume

## Статус

`BLOCKED` — restart-only ecology scheduling-fence gap воспроизведён и исправлен bounded permission-edge propagation, missing gen2 profile evidence сохраняется до cleanup, а последующие реальные поколения достигали schedule/admission parity. Однако post-fix Phase B исчерпан двумя независимыми predecessor-сбоями полного `ant verify`. По TASK final `ant jar`, новый real-stack acceptance, SUCCESS docs и Goal035 запрещены.

Branch: `feature/phantom-world`.
Required parent/HEAD/origin до изменений: `79184f2ed8ed4d3d787ac70e9e306b45c6893e7e`.

## Read-first, scope и переиспользование

- Прочитаны closure5 TASK/ACCEPTANCE/CONTEXT/package manifest, closure4 report, root README, module `Agents.md`, current roadmap/status/handoff и релевантные production/test owners.
- Отдельные module README, parent AGENTS, code-map и pattern-файлы в bounded read-set не найдены и повторно не искались.
- Локальные аналоги: existing ecology `_due` pulse processing и `ecologyFenceChanged(profileId)`, Goal033 bounded fixtures, Goal034 guarded manifest/cleanup, background transaction/recovery tests.
- Переиспользован existing ecology pulse/event path; admission, schedules, schema и population cap не менялись. Новый thread/executor/timer/full scan не добавлялся.
- Bounded exception больше 10 файлов относится к одной closure-family: ecology fix/evidence, две доказанные Phase-C drain corrections, focused tests, этот report и переданный task package.
- Unrelated user files и historical reports не изменялись.

## Focused reproduction и ecology fix

- На parent добавлен restart fixture с 10 existing READY durable rows и already-complete initial catch-up.
- Inventory сначала не готов; real bounded `onPopulationPulse()` постепенно загружает его. До production fix новый test дал `8/9`: eligible READY rows не получали refresh после missed false→true transition.
- `PhantomPopulationEcologyService` теперь хранит per-entry last-published scheduling permission и публикует только false↔true edge через existing `ecologyFenceChanged(profileId)`.
- Edge вычисляется при bounded обработке самого entry, callback выполняется вне lock; no-op pulses не спамят, O(N) burst при inventory-ready отсутствует.
- После fix Goal033 ecology: `9/9 PASS`.
- Goal034 contract: `18/18 PASS`; failure manifest записывает acceptance instant, desired/actual/missing/unexpected IDs, character object id и schedule/ecology fields каждого missing profile до cleanup.
- DB negative guard: `1/1 PASS`, exit `2`, driver loads `0`, connections `0`.

## Сохранённое missing-profile evidence

Run `20260907-200115-e0db69fd` сохранил честный pre-cleanup gen2 snapshot:

- gen1: managed `10`, desired/expected/online `5/5/5`, IDs `4611,4615,4616,4619,4620`, parity=true;
- native gen1 restart: expected/observed `2026-09-07T18:05:00Z`, exit `2`, Phantom drain=true;
- gen2: managed `10`, desired/expected/online `5/5/1`, actual ID `4611`, subset=true, parity=false;
- missing desired profile `4615`, character `268485537`, `evening/-6`, home `914`, ACTIVE, online=false;
- missing desired profile `4616`, character `268485538`, `evening/0`, home `910`, ACTIVE, online=false;
- missing desired profile `4619`, character `268485569`, `evening/-16`, home `910`, ACTIVE, online=false;
- missing desired profile `4620`, character `268485570`, `evening/-12`, home `910`, ACTIVE, online=false;
- unexpected online IDs: none.

Artifact: `.phantom-local/blackbox/goal034/20260907-200115-e0db69fd/artifacts/manifest.properties`; он пережил cleanup и не содержит secrets.

## Реальные follow-up runs и bounded Phase C fixes

- Runs `20260907-184611-72c5134b` и `20260907-193600-68b1fb3c` подтвердили одинаковые desired/actual gen1 и gen2 IDs `5/5`, но gen2 drain оставлял один retained entry.
- Diagnostic runs `20260907-195617-52514c5f` и `20260907-200115-e0db69fd` локализовали первую retained-cleanup family: retry уже claimed DEMATERIALIZING transition и non-canonical recovery anchor.
- Bounded fix сохранил existing transition, выбрал canonical same-region recovery position через production topology и оставил vanilla MapRegion TOWN teleport/drain path.
- Release run `20260907-205859-7616131b`: gen1 и gen2 managed/desired/expected/online `10/5/5/5`, одинаковые IDs, missing/unexpected none, identity/ecology continuity=true; gen2 drain evidence отсутствовал.
- Diagnostic run `20260907-211935-16a30c25` сохранил ту же parity и доказал второй Phase-C blocker: lifecycle capture отвергал exact terminal `farm.background` goal после recovery, потому что execution parser требовал ACTIVE.
- Bounded fix разделил ACTIVE execution validation и exact terminal lifecycle capture; active execution contract не ослаблен.
- Background recovery focused test после fix: `3/3 PASS`, включая terminal lifecycle dematerialization; materialization-abort test: `3/3 PASS`.

## Phase B stop condition

- После terminal lifecycle fix первый fresh `ant verify` упал в predecessor suite `combat-server-integration.02-canonical-player-ai-attack-and-death`: victory cleanup сохранил exact dead target.
- Немедленный focused `ant phantom-combat-server-integration-test` прошёл `20/20 PASS` без изменений; failure классифицирован как первый transient predecessor blocker.
- Единственный разрешённый полный repeat дошёл дальше и упал в другом predecessor suite: `acquisition-manor-active.after-all`, live Player futures=`[Player._skillListTask]`; `BUILD FAILED`, `22:47`.
- Это второй независимый predecessor blocker. TASK Phase B требует `BLOCKED`; новый repeat, fix, final jar и black-box запрещены.
- Последний полный green verify до terminal lifecycle fix: `BUILD SUCCESSFUL`, `22:43`. Ранее в closure также были green verify `33:46` и `21:53`.
- Standalone jar последнего предшествующего green production cycle: `BUILD SUCCESSFUL`, `0:28`; post-terminal-fix final jar намеренно не запускался.
- Всего closure5 real/diagnostic black-box runs: `6`; новый post-terminal-fix acceptance не запускался из-за Phase B gate.

## PIDs, ports, cleanup и safety

- Последний diagnostic run `20260907-211935-16a30c25`: LS/gen1/gen2/cleanup PIDs `29388/13252/41372/1100`; ports `57793/57794/57795`.
- LS READY, обе GS READY и registered; latest gen1/gen2 parity `5/5/5`, same IDs, missing/unexpected none.
- Cleanup: population `10`, registration=true, forced=false; orphans.none=true; working.integrity=true.
- Canonical/sandbox data fingerprint одинаков: `f793178f4ab857bcf7f260a7d8db21d086980e9e6c1dc5f61f9caf12eb28395d`.
- Использовалась только `127.0.0.1:3308/l2jmobiush5_phantom_test`; `database.production.used=false` во всех сохранённых manifests.
- `prepare-phantom-test-db` не выполнялся; production `l2jmobiush5` не probe/read/cleanup; wildcard/global Java kill не использовался.

## Изменённые task files

- Production: ecology permission edge; background canonical recovery, transition retry и terminal lifecycle capture.
- Tests/harness: Goal033 restart regression, Goal034 missing-profile evidence contract, focused background regressions.
- Docs: этот новый BLOCKED resume report и closure5 task package (`TASK/ACCEPTANCE/CONTEXT/PACKAGE_MANIFEST/CODEX_LAUNCHER`).
- Current roadmap/status/handoff не менялись: Goal034 остаётся `BLOCKED`, Goal035 — `NOT_STARTED`.
- User-owned `PhantomClanDirectiveIntegrationGoal030C2ASuite.java`, `PhantomMultipartyEconomySuite.java` и прочие unrelated untracked artifacts остаются unstaged.

## Resume

- Нужна новая explicit Goal034 resume task: отдельно устранить/стабилизировать predecessor future leak `acquisition-manor-active.after-all`, затем начать с fresh full verify.
- После green verify: standalone final jar, один real gen1 → native restart/drain → gen2 parity/drain → exact cleanup; только затем SUCCESS docs.
- Missing-ID artifact выше является обязательным preserved evidence и не должен удаляться до resume review.
- Usage snapshot перед отчётом: `1,592,158` tokens, `16,086` seconds.
- BLOCKED commit subject: `phantom(goal-034): record restart ecology fence blocker`; SHA и non-force push result сообщаются в final handoff, amend не выполняется.
- Goal035 не начинать.
