# Acceptance — Goal 017 lifecycle safety completion

## Git/scope

- [ ] exact parent `d731bf91b5f75cf733175bf57faf19c0354085c0`, one ordinary child, exact subject, remote exact;
- [ ] no suffix Goal and no Goal 018/019/020/023/025 work;
- [ ] production files <=18, new production files <=3, total files <=30;
- [ ] no Player.java/Party.java/schema/other chronicle/geodata change;
- [ ] no worker/thread/executor/Future/task.

## Invitation and saga

- [ ] requester and invitee timeout cleanup both work;
- [ ] reservation cannot be observed before durable preparation/publication;
- [ ] managed requester and managed invitee identities are retained;
- [ ] every terminal outcome is delivered once for either managed side;
- [ ] registration close drains outbound and inbound invitations;
- [ ] exact sequence is persisted on leader and Phantom member claims;
- [ ] refusal/timeout/cancel leaves no orphan claim;
- [ ] form/invite retry is exact-key idempotent;
- [ ] operation deadline terminalizes canonical and durable state;
- [ ] shutdown waits for invitation and operation claims.

## Membership/background

- [ ] leave/expel/transfer/travel are reachable through decision handlers;
- [ ] canonical Party postcondition drives durable claims;
- [ ] departed member is not re-invited;
- [ ] disband terminalizes every managed claim;
- [ ] background rejects live party intent before mutation and on final recheck.

## Roles/tactics/route/performance

- [ ] maximum deterministic matching, not greedy;
- [ ] real role facts are current and target-null truth is honest;
- [ ] Phantom support is evaluated against exact target;
- [ ] target scope and exact capability/variant/skill are revalidated;
- [ ] absent/cross-instance member cannot advance route;
- [ ] topology drift/deadline/cancel release route actions;
- [ ] no full group/claim/tactical scan on pulse;
- [ ] actual coordinator operation count never exceeds budget.

## Tests/release

- [ ] dynamic ordinary and managed invitation parity;
- [ ] fault/restart matrix and repeated handler retry;
- [ ] real three-Phantom plus real-client integration;
- [ ] actual coordinator 10k/1k and 100k-pulse evidence;
- [ ] targeted affected suites and verifiers 016/017 green;
- [ ] production/test/build/verifier freeze before final full verify;
- [ ] one final green ant verify on final tree;
- [ ] standalone ant jar;
- [ ] verifier 017 2× byte-identical after commit;
- [ ] report <=190 lines;
- [ ] token `GOAL_017_PARTY_LIFECYCLE_SAFETY_COMPLETED_PENDING_INDEPENDENT_REVIEW` only after all gates.
