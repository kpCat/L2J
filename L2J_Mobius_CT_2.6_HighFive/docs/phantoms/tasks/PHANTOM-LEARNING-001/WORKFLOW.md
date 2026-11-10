# Codex workflow for LEARN-001

1. New Codex dialogue, reasoning Very High. Attach this ZIP and send separate GOAL.txt verbatim or user-provided shorter `/goal`; no reuse of TASK130 Codex thread.
2. Inspect `git ls-remote` for exact WIP SHA, create isolated clone and new branch `wip/phantom-learning` at `c5b81f89ce02030eff8441395b0411137a110b2f`. No user checkout writes. If remote WIP advanced, pin exact SHA for reproducibility and report newer SHA; no merging without review.
3. Read Sources and `Agents.md`/master/Workflow; propose any pivotal architecture conflict only before expensive native. Technical details can be resolved independently in scope.
4. Before code: document 5-10 key risks, select minimal CombatService seam, stable feature and evidence API, DB table layout. Avoid architecture debate loops; once plan is concrete execute.
5. One implementation pass: TDD small model + trainer + DB + real injection. One bounded repair pass. Stop if 3rd unrelated blocker; output usable milestone and HANDOFF, no 15h smoke.
6. Tests in order: DB-free model -> integration -> isolated SQL -> build parity -> performance -> single cheap native only if needed. Don't block completed learning milestone on re-proving prior TASK130 3-GK group kill.
7. Full test/build wall-clock gates: focused tests target < 3min each, JAR one or two, GameServer max two. No repeated unchanged scenario. If runtime fixture is fragile use proper headless and report limits honestly.
8. Write `docs/phantoms/reports/PHANTOM-LEARNING-001.md`, new `RESULT.md` in this task package directory, a compact `HANDOFF.md` for 002: branch, SHA, architecture, recorded features/model schema, exact commands and known gaps, budget.
9. git diff --check, standard encoding scans; scoped commits; ordinary push only `wip/phantom-learning`. Report actual remote HEAD verified; mention clean or dirty tree explicitly.
10. New task LEARN-002 = new Codex chat. Human-readable RU summary, no undocumented long-running process started by Codex.

### Important repository document conflict
`Agents.md`, `CODEX_WORKFLOW_CONTRACT.md`, `TASK_PACKAGE_STANDARD.md` were authored before current clone layout; some mention `C:\Users\ZBook\L2J_Mobius` and force new work onto `feature/phantom-world`. The owner has now explicitly directed LEARN-001 to derive from TASK130 WIP into a separate experimental branch, leaving live/user checkout untouched. Record this scoped exception in results; never silently write an old path or merge into feature.
