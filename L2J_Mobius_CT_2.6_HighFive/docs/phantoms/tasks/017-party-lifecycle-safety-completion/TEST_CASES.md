# Tests — Goal 017 lifecycle safety completion

## 1. Canonical invitation dynamic tests

Use real current Player/Party/request fields, not source-string inspection:

- ordinary client→client accept preserves Party/distribution/request cleanup;
- ordinary refusal and disabled response;
- timeout then same requester can invite again;
- timeout then same invitee can receive again;
- requester and invitee indexes both clear;
- simultaneous accept versus cancel has one terminal result;
- simultaneous timeout versus response has one terminal result;
- delivery preparation failure publishes no prompt/request residue;
- registration close drains inbound and outbound managed invitations;
- Phantom→real refusal/timeout reaches managed requester terminal callback;
- managed invitee backpressure clears exact request ownership;
- stale identity cannot terminate a newer invitation.

Static delegation tests may remain but do not count as parity evidence.

## 2. Durable saga tests

Inject failure at:

```text
leader PREPARED
member PREPARED
exact identity leader update
exact identity member update
after core publication
canonical accept
each member claim commit
each goal transition
terminal callback
shutdown callback race
```

For each boundary reconstruct the coordinator and prove one canonical membership
or none, no orphan INVITED claim and no repeated explicit refusal.

Retry the same `party.form_invite` handler at least ten times while one invite is
pending; assert one core invitation sequence and unchanged goal identity.

## 3. Membership commands

Real canonical Party tests for:

- Phantom leave;
- leader expel;
- leader transfer;
- automatic leader change;
- disband;
- stale action rejection;
- departed claim does not get re-invited;
- travel goal dispatches one route request.

## 4. Role and capability truth

- global matching counterexample fills HEAL and RECHARGE;
- deterministic tie-break;
- same coarse archetype remains distinct;
- main/subclass isolation retained;
- real current Player observation changes when equipment/resource/reuse changes;
- target-null observation does not claim target-specific readiness;
- exact target evaluation makes supported heal/recharge/resurrection ready;
- SELF skill cannot target another member;
- unrelated positive skill cannot be relabelled as heal/buff;
- range/party/instance/reuse changes between planning and dispatch reject safely.

## 5. Route and pulse

- absent member prevents waypoint advance and ARRIVED;
- cross-instance member prevents advance;
- topology hash drift fails/cancels;
- deadline and cancellation stop movement;
- combat/dead/casting member is not moved;
- group-scoped cancellation retains other routes;
- 10,000 claims / 1,000 groups through real coordinator;
- 100,000 steady pulses, DB writes zero;
- every pulse examined-operation count <= configured budget;
- instrumentation proves no full group/claim/tactical scan.

## 6. Background gate

For a profile in every live blocking party status:

- directive returns `party.materialized_only`;
- farm and travel reject before model/transaction;
- race changing SOLO→MEMBER immediately before commit is caught by final recheck;
- SOLO/RETIRED/INCONSISTENT do not block ordinary accepted Goal 015 behavior.

## 7. Real integration

Use test DB and current loaders:

1. create three managed level-1 profiles through PopulationStore;
2. materialize all three;
3. issue one Phantom→Phantom refusal, verify exact terminal cleanup;
4. issue/accept invitations and observe one real Party;
5. verify exact leader, roster, distribution, claims and goals;
6. execute maximum role assignment;
7. execute a shared route/regroup without snap;
8. execute one exact target-specific support action available in current catalog;
9. leave and rejoin;
10. transfer leader;
11. dematerialize/re-materialize one member and recover;
12. reconstruct coordinator and recover Phantom-only group;
13. Phantom invites a real test client; real refusal and acceptance are observed;
14. restart with real member strips consent and does not rejoin it;
15. disband and prove all claims/goals/invites/routes/tactical/external leases terminal.

No fake GameClient is attached to a Phantom. A real-client test fixture may use
the existing legitimate test client seam.

## 8. Final evidence

The final report records:

- exact dynamic invitation counts;
- exact core terminal outcomes;
- real Party object IDs/roster;
- operation-claim peaks and zero shutdown residue;
- matching counterexample result;
- target-specific support skill/variant/scope;
- pulse maximum examined operations;
- background gate results;
- all exact test invocations and full-verify count;
- usage and changed-file counts.
