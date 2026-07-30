# Tests — Goal 017

Deterministic seed:

```text
17001701
```

Focused modes:

```text
party-canonical-invitation
party-state-recovery
party-role-vacancy
party-semantic-acts
party-route
party-tactics
party-lifecycle
party-server-integration
party-performance
```

## Canonical parity

- ordinary `RequestJoinParty` and `RequestAnswerJoinParty` delegate to the shared
  service and preserve every existing validation outcome;
- client→client packets/request fields/Party result remain equivalent;
- real→Phantom, Phantom→real and Phantom→Phantom use the same validation;
- managed target needs no fake client answer packet;
- refuse, disabled, timeout and cancellation clear exact request ownership;
- party full, nonleader, other party, pending invite, block, event, jail,
  Olympiad, cursed weapon, Rift and visibility races fail correctly;
- concurrent invites produce at most one pending request.

## Durable saga

- crash/reconstruct at every phase in `FAILURE_MATRIX.md`;
- Phantom-only group rebuild after all members rematerialize;
- real member is not auto-rejoined after restart;
- deterministic leader election and generation change;
- same-key idempotency and stale generation/revision conflict;
- maximum roster nine and component payload <=4096;
- no unrelated goal overwrite.

## Roles

- one actor fills several capabilities without losing secondary facts;
- two same coarse archetype actors remain distinct;
- main/subclass/main isolation;
- context changes GENERAL_PVE/AREA_PVE/RECOVERY/TRAVEL assignment;
- missing/optional/unsupported vacancy output and provenance;
- no class-ID switch or fixed single role;
- real-player facts come from exact current Player;
- deterministic current-catalog fixtures for heal, recharge, resurrection,
  song/dance, tank/control, damage and summon where present.

## Semantic acts

- canonical stable encoding/hash;
- all party act keys and bounded slots;
- text/LLM absent;
- act alone performs no mutation;
- stale group generation cannot dispatch.

## Route

- shared leader route and follower waypoints;
- regroup/separation, cancellation, stuck, timeout and hash drift;
- partial movement and restart;
- real-leader observation-only;
- no snap/cross-instance/background travel;
- navigation backpressure and shutdown release.

## Tactics

- exact assist of leader normal-monster target;
- protect member from observed normal-monster attacker;
- heal/recharge/resurrection real current test fixtures;
- explicit one support/song/dance directive, never use-all;
- readiness/resource/reuse/range/party membership revalidation;
- no PvP target;
- external action excludes combat/respawn and releases on every failure.

## Real integration

Use test DB and real current loaders:

1. create three managed level-1 profiles through accepted population saga;
2. materialize them and form one canonical Party;
3. refuse then accept invitations;
4. verify exact Party leader/roster/distribution and durable claims;
5. execute role assignment and shared route/regroup;
6. run assist and one supported real party-support capability using a
   deterministic current-catalog test Player fixture;
7. dematerialize one member and recover;
8. transfer/lose leader and recover;
9. reconstruct party coordinator and rebuild Phantom-only Party;
10. invite a real test client and require canonical acceptance;
11. restart coordinator with real member and prove no automatic real consent;
12. leave/disband and prove every component/goal/request/action is terminal.

## Lifecycle/performance

- disabled/no-state/no-goal path inert;
- scheduler composite has one scheduled task;
- shutdown with blocked DB, invite, route and support claims;
- 100,000 steady party control pulses with no DB writes;
- 10,000 profiles / at least 1,000 synthetic groups;
- no pulse exceeds configured party action budget;
- matching remains bounded to nine members;
- no Phantom party Thread/Executor/Future/ScheduledFuture;
- no dynamic high-cardinality metric labels;
- all historical 014A/015/016, combat, navigation, progression,
  materialization, decision and scheduler regressions green.

Verification order:

```text
all focused and affected suites
verifier 014A, 015, 016 and working 017
one final party aggregate
one full ant verify
one standalone ant jar
commit/push
two byte-identical post-commit verifier 017 runs
```

A second full verify is allowed only after a real source/test/build/verifier fix.
An unrelated flake receives one exact targeted retry and no second full verify
without a relevant code change. Third full verify is forbidden.
