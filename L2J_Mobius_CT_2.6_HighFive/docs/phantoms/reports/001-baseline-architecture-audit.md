# Codex report — 001-baseline-architecture-audit

## Status

`SUCCESS`

## Summary

Task 001 completed as a documentation/static-verifier audit with no production
behavior changes. The current High Five code supports the gate verdict
`FEASIBLE_WITH_SEAM`: canonical `Player` can be restored/spawned without an
active socket, but a small outbound/session seam is required to preserve
`ServerPacket.runImpl` effects and avoid faking `GameClient`.

## Baseline

- Git root: `C:/Users/endim/L2J_Mobius`.
- Module: `L2J_Mobius_CT_2.6_HighFive`.
- Review snapshot, actual `origin/master`, and start `HEAD`:
  `16d61833b3983a3976583d0e4813e0de9457a52f`.
- Drift: none.
- Java: 25.0.4 LTS.
- Ant: 1.10.15 temporary binary outside the repository (`ant` absent from
  `PATH`).
- `ant jar`: exit `0`, 1,895 sources compiled, both server JARs copied.
- Geodata: absent (directory has only `Readme.txt`); pathfinding config is `2`.

## Gate verdict

`FEASIBLE_WITH_SEAM`

Offline play/trade proves canonical null/detached-client materialization and
`Disconnection.of(Player)` cleanup. The seam is mandatory because:

- `GameClient` requires a real network `Connection`;
- `Player.sendPacket` suppresses packet server effects when client is null;
- `EnterWorld` mixes session/HWID/LoginServer and domain initialization;
- selected actions/handlers directly require client/flood/session state.

ADR 0001 remains `Proposed` until Task 004 passes.

## Git provenance

- Original Codex Task 001 commit:
  `e7dcf575dd45a94c83560fd140144635bbf96e37`.
- Parent:
  `16d61833b3983a3976583d0e4813e0de9457a52f`.
- After the Codex handoff, the user independently added `Agents.md` through
  amend; the resulting branch commit is
  `cdca7a3d96554285eb8c992fa14f65b27f7f36ae`.
- The amend was a user action, not a Codex action. Both commits have the same
  parent; the original Codex commit is audit evidence rather than an ancestor
  of the amended commit.

## Independent review

- Original Task 001 content: `ACCEPT`.
- Amended branch state: `ACCEPT WITH FOLLOW-UP`.
- No P0/P1 architectural findings were identified.
- `FEASIBLE_WITH_SEAM` remains the accepted architecture gate verdict.
- ADR 0001 remains `Proposed` until Task 004.
- The documentation follow-ups are implemented by Task 001A and require an
  independent review before Task 002 may begin.

## Changed files

- `docs/phantoms/tasks/001-baseline-architecture-audit/*` — supplied task
  package, preserved and included by explicit scope.
- `docs/phantoms/audits/001-baseline-architecture-audit/BASELINE_MANIFEST.json`
- `docs/phantoms/audits/001-baseline-architecture-audit/BASELINE.md`
- `docs/phantoms/audits/001-baseline-architecture-audit/CURRENT_SYSTEM_AUDIT.md`
- `docs/phantoms/audits/001-baseline-architecture-audit/DEPENDENCY_MAP.md`
- `docs/phantoms/audits/001-baseline-architecture-audit/HEADLESS_PLAYER_FEASIBILITY.md`
- `docs/phantoms/audits/001-baseline-architecture-audit/NEXT_TASK_GATES.md`
- `docs/phantoms/adr/0001-headless-player-integration-seam.md`
- `docs/phantoms/reports/001-baseline-architecture-audit.md`
- `tools/phantoms/verify-task-001.ps1`

This is the bounded exception to the usual 8–10-file preference: all files are
independent required Task 001 artifacts or the supplied package, within one
documentation/tooling subsystem and the exact task allowlist.

## Architecture decisions

- Keep canonical `Player`; reject NPC core, fake `GameClient`, global nullable
  client edits, subclassing and forking.
- Introduce only a small output/session seam in Task 004.
- Preserve `ServerPacket.runImpl(Player)` exactly once while suppressing
  headless network bytes.
- Use explicit lifecycle/identity ownership and idempotent cleanup.
- Use `PhantomActionFacade`; never use client packets as Phantom internal API.
- Reuse shared pooled scheduling; no per-phantom thread/executor/task loop.

## Evidence index

- Git/environment/build/hashes: `BASELINE.md`.
- Fake Players, Player/GameClient, packet effects, lifecycle, offline systems,
  build/DB/performance audit: `CURRENT_SYSTEM_AUDIT.md`.
- All required gameplay subsystems, persistence/tasks/failures:
  `DEPENDENCY_MAP.md`.
- Verdict, alternatives A–F, seam and Task 004 tests:
  `HEADLESS_PLAYER_FEASIBILITY.md`.
- Decision record: ADR 0001.
- Task 002/003/004 prerequisites: `NEXT_TASK_GATES.md`.

Every source conclusion is tied to path/symbol and snapshot SHA; key files also
have SHA-256 hashes.

## Database changes

None. No DB connection was opened and no DB mutation was performed.
`l2jmobiush5` was neither read nor used by tests. The reserved
`l2jmobiush5_phantom_test` contract and Task 002 fail-fast guard are documented
only.

## Database safety

Manifest flags:

- `databaseConnectionPerformed=false`;
- `databaseMutationPerformed=false`;
- production DB `l2jmobiush5`;
- test DB `l2jmobiush5_phantom_test`.

The verifier performs only local file and Git inspection; it has no DB/network
code.

## Configuration changes

None. The future canonical path
`dist/game/config/Custom/PhantomPlayers.ini` and
`EnablePhantomSystem=false` fail-closed contract are documented for Task 003;
the file was not created.

## Commands executed

```text
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/master
git branch --all --list "*feature/phantom-world*"
git log -1 --format=fuller origin/master
git switch -c feature/phantom-world origin/master
java -version
ant -version                                      # failed: ant absent from PATH
ant -p                                            # failed: ant absent from PATH
<temp>\apache-ant-1.10.15\bin\ant.bat -version   # exit 0
<temp>\apache-ant-1.10.15\bin\ant.bat -p         # exit 0
<temp>\apache-ant-1.10.15\bin\ant.bat jar        # exit 0
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-001.ps1
git diff --check
git status --short --branch
git diff --name-status origin/master...HEAD
```

Static audit used targeted `rg`, `Get-Content` and `Get-FileHash` commands.
No MariaDB client/JDBC/server command was run.

## Test results

- Java version: PASS.
- Ant target inventory via temporary Ant: PASS.
- `ant jar`: PASS, exit `0`.
- Pre-commit Task 001 verifier: PASS, 43/43 checks, exit `0`; this belongs to
  the Task 001 execution that produced original Codex commit `e7dcf575...`.
- Final-commit verifier run 1 on `e7dcf575...`: PASS, 43/43 checks, exit `0`.
- Final-commit verifier run 2 on `e7dcf575...`: PASS, 43/43 checks, exit `0`.
- The two final verifier outputs were identical.
- JSON parse/schema checks: covered by verifier.
- Scope/production/other-chronicle/DB/binary guards: PASS.
- JSON and DB safety checks: PASS.
- `git diff --check`: PASS.

## Scope verification

The verifier compares committed, tracked working and untracked paths with
`origin/master` and an exact allowlist. It rejects production Java,
`build.xml`, runtime config/data, SQL, other chronicles and binary/build/log
artifacts. Generated JARs/build output are ignored and not staged.

## Determinism

- Seed: `20260725001`.
- Manifest field order and target list are stable.
- Verifier result names and changed paths use ordinal stable sorting.
- Verifier uses no randomness, timestamp, DB or network.
- Repeated final-commit output must match byte-for-byte.

## Verifier runs

- Pre-commit for the original Codex Task 001 result: PASS, deterministic
  stable-sorted output, 43/43 checks, exit `0`.
- Final commit `e7dcf575...` run 1: PASS, 43/43 checks, exit `0`.
- Final commit `e7dcf575...` run 2: PASS, 43/43 checks, exit `0`.
- Final run outputs: identical.

These historical results apply to original Codex commit `e7dcf575...`; they
were not rerun on or attributed to user-amended commit `cdca7a3d...`.

## Performance measurements

No production benchmark was run, as required. Build duration was 13 seconds.
Static risks and Task 004/030 smoke metrics cover constructor tasks, autosave,
broadcast fan-out, pooled schedulers, DB store bursts and bounded packet
recording.

## Review snapshot drift

None: review snapshot and fetched `origin/master` were both `16d61833…`.

## Pre-existing working tree changes

Only the untracked Task 001 package existed. No other user work was found,
modified, removed, stashed or staged.

## Deviations from TASK.md

- `ant` was not available in `PATH`. Apache Ant 1.10.15 was downloaded to a
  temporary OS directory and invoked by absolute path. The repository and
  dependency set were not changed.
- Final commit SHA cannot be embedded literally in the same commit without
  creating a self-referential changing SHA. The report records branch/parent
  and identifies the audit commit as `HEAD`; the exact immutable SHA and remote
  ref are reported in the final task handoff.

## Known limitations

- Static/compile audit only; no server, client, DB or runtime scenario.
- ADR remains `Proposed`.
- Task 004 must resolve exact enter steps, session-kind online semantics,
  effect dispatch access and short-future cleanup.

## Risks

- hidden client dereference in a future action;
- packet effect loss/double-run;
- identity collision with real login;
- partial multi-table/item/mail/trade persistence;
- constructor/autosave/future leak on partial materialization;
- dense world visibility/broadcast amplification.

## Commit parent

`16d61833b3983a3976583d0e4813e0de9457a52f`

## Remote branch verification

Performed after commit/push with:

```text
git push -u origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
```

The exact remote SHA is reported in the final handoff.

## Git

- Branch: `feature/phantom-world`
- Original Codex commit SHA:
  `e7dcf575dd45a94c83560fd140144635bbf96e37`
- User-amended branch commit SHA:
  `cdca7a3d96554285eb8c992fa14f65b27f7f36ae`
- Parent SHA: `16d61833b3983a3976583d0e4813e0de9457a52f`
- Original Codex push result: successful according to the Task 001 handoff.

## Recommended next step

Complete and independently review Task 001A. Only after the Task 001A commit
receives independent `ACCEPT` may Task 002 be prepared; Task 002 remains
`NOT_STARTED`.
