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
- Pre-commit Task 001 verifier: PASS, 43/43 checks, exit `0`.
- Final-commit verifier run 1: required after commit.
- Final-commit verifier run 2: required after commit.
- JSON parse/schema checks: covered by verifier.
- Scope/production/other-chronicle/DB/binary guards: covered by verifier.
- `git diff --check`: required before/after commit.

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

- Pre-commit: PASS, deterministic stable-sorted output, 43/43 checks,
  exit `0`.
- Final commit run 1: to be recorded after commit.
- Final commit run 2: to be recorded after commit.

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
- Commit SHA: Task 001 audit commit (`HEAD` after commit; exact SHA in final handoff)
- Parent SHA: `16d61833b3983a3976583d0e4813e0de9457a52f`
- Push result: recorded after push in final handoff

## Recommended next step

Independent GitHub review of Task 001. Do not begin Task 002 or Task 004 until
the manual gate returns `ACCEPT` or an explicit follow-up decision.
