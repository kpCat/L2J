# Acceptance — Goal 020 Checkpoint 2

## Git/scope

- [ ] exact parent `21ba300fc612f9777891912f80efc633f5b6db18`, one ordinary child, exact subject, remote exact;
- [ ] Checkpoint 1 final review recorded;
- [ ] no third Goal 020 checkpoint and no Goal 021/025 work;
- [ ] <=18 new production/data, <=34 production/data/config, <=60 total;
- [ ] no Player.java, Party.java, existing handler or schema change;
- [ ] no dedicated worker/thread/executor/Future/task.

## Preflight

- [ ] only PHANTOM recipients enter ingress;
- [ ] real recipients do not consume observer/batch limits;
- [ ] delayed/overflow housekeeping is fully bounded and residue-free;
- [ ] verifier 020c1 is pinned/descendant-compatible.

## Durability

- [ ] conversation.state + execution handoff is atomic;
- [ ] execution schema <=4096 and transition-strict;
- [ ] restart recovers PREPARED without all-profile scan;
- [ ] DISPATCHING recovers UNCERTAIN and never resends;
- [ ] duplicate plan/action/send is impossible within accepted horizon.

## Actions/queries

- [ ] active unrelated goal is never overwritten;
- [ ] invite/leave/travel use Goal/Decision/Party paths;
- [ ] accept/refuse use exact canonical pending invitation;
- [ ] queries use current immutable authority and mutate nothing;
- [ ] support/assist/regroup are explicitly DEFERRED;
- [ ] no direct gameplay mutation from conversation code.

## Outbound

- [ ] current handlers own recipient/channel behavior;
- [ ] WHISPER/PARTY/GENERAL/TRADE integration passes;
- [ ] PHANTOM_GENERATED is explicit and cannot loop;
- [ ] one source dispatch sends at most one response;
- [ ] text/identity/materialization revalidated before send.

## Release

- [ ] eight focused modes and final aggregate pass;
- [ ] exact affected regressions pass;
- [ ] verifier 020c1 and 020c2 pass;
- [ ] one final green ant verify;
- [ ] standalone ant jar;
- [ ] two post-commit byte-identical verifier 020c2 runs;
- [ ] report <=240 lines;
- [ ] token `GOAL_020_CONVERSATION_OUTBOUND_ACTIONS_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
