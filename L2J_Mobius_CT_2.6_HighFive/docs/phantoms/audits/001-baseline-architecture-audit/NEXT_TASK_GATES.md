# NEXT TASK GATES — Task 001

## Task 002 — automated test infrastructure

Prerequisites:

- create `l2jmobiush5_phantom_test` only in Task 002;
- use dedicated test credentials/config, never production credentials;
- parse/normalize the JDBC database name before `DatabaseFactory`
  initialization;
- fail closed if the name is `l2jmobiush5`, empty, unknown, or not the exact
  allowlisted test database;
- define fixture/migration owner and deterministic cleanup/rollback;
- inject seed `20260725001`.

Acceptance gates:

- Ant targets `test`, `verify`, `phantom-scenario-test` and
  `phantom-performance-smoke`;
- one negative control proves production DB rejection before any connection;
- ordinary tests do not start servers or require the production config;
- fixture create/use/cleanup is repeatable;
- test failure preserves diagnostics without credentials;
- Windows `powershell`/Ant invocation is documented and deterministic.

## Task 003 — skeleton, config and metrics

Prerequisites:

- Task 002 test/DB guards pass;
- ADR 0001 remains `Proposed`;
- no materialized player or packet sink implementation yet.

Acceptance gates:

- canonical future file `dist/game/config/Custom/PhantomPlayers.ini`;
- `EnablePhantomSystem=false` default and fail-closed parse behavior;
- config load is added through `ConfigLoader` in local style;
- no-op lifecycle registration in `GameServer` with deterministic
  startup/shutdown ordering;
- shared scheduler/queue placeholders only, no per-phantom task;
- bounded counters/sampled trace disabled by default;
- disabled system changes no production behavior and creates no DB/network
  work;
- targeted tests prove disabled/no-op semantics.

## Task 004 — headless Player feasibility spike

Prerequisites:

- isolated DB guard from Task 002;
- disabled-by-default skeleton from Task 003;
- exact production touch allowlist approved after fresh drift audit;
- ADR 0001 and `HEADLESS_PLAYER_FEASIBILITY.md` treated as constraints.

Acceptance gates:

- canonical `Player`, no subclass/fork and no fake `GameClient`;
- materialize with no TCP/network bytes;
- output sink rejects null and preserves effect ordering/exactly-once behavior;
- recording is fixed-capacity with drop counter and disabled by default;
- explicit domain initialization, no direct `EnterWorld.runImpl`;
- one safe canonical server action through a narrow facade;
- inventory/skills/world/observer visibility verified;
- identity lease prevents duplicate object and concurrent real login;
- dematerialize/store, repeated cleanup and restart restore pass;
- failure injected after every lifecycle step leaves no world object, online
  flag, autosave/task, party/trade/request/instance residue;
- task/future counts and action latency are bounded;
- no per-phantom thread/executor/loop;
- ADR transitions only after evidence.

## Risks that cannot pass Task 004

- loss/double execution of `ServerPacket.runImpl`;
- inability to terminate `ItemList` chained effect headlessly;
- hidden essential `GameClient` requirement;
- `Player.load` task/autosave leaks on partial failure;
- real-login/phantom identity collision;
- non-idempotent `deleteMe`/cleanup;
- online DB flag inconsistent with world/session state;
- world duplicate handling used as normal ownership arbitration;
- need to modify broad client-handler families;
- need for production DB or a `Player` fork.

Any one of these unresolved after the bounded spike requires
`NOT_FEASIBLE_WITHOUT_PLAN_CHANGE`, a stopped gate and a separate master-plan
revision. Task 005 must not start before that review.

## Later performance gates

Task 004 smoke:

- one then ten fixtures;
- materialize/dematerialize latency;
- active scheduled-future/task-manager membership delta;
- packet sink count/drop count;
- no queue growth after cleanup;
- DB statement/query count where the isolated harness can measure it.

Task 030 scale:

- 10 → 25 → 50 → 100 → 250 → 500 profiles;
- controlled ACTIVE/WARM/BACKGROUND materialization;
- CPU, heap, queue, DB, world-visibility and pathfinding budgets;
- mass shutdown/restart and economy reconciliation;
- no O(N²) materialization burst without backpressure.

## Database migration and fixture contract

- Task-specific migrations are versioned and idempotent.
- Test migrations run only after the exact test-name guard.
- No credentials are committed.
- Each fixture owns character/account/item rows it creates.
- Cleanup is safe to retry and verifies zero owned residue.
- Failure injection covers partial character/item/offline/message rows.
- Phantom runtime never writes tables directly when a canonical domain API
  exists.
