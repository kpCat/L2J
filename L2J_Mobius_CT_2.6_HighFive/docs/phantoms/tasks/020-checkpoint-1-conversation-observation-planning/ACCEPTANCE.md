# Acceptance — Goal 020 Checkpoint 1

## Git/scope

- [ ] exact parent `384b521f2cd29f4162c9aca9116eb0ff40cbd681`, one ordinary child, exact subject, remote exact;
- [ ] Goal 019 accepted review and descendant-compatible verifier;
- [ ] no Goal 020A/020B, no Checkpoint 2/Goal 021/025 work;
- [ ] <=16 new production/data, <=30 production/data/config, <=54 total;
- [ ] no Player.java, Party.java, existing chat handlers or schema change;
- [ ] no worker/thread/executor/Future/task.

## Activation gates

- [ ] social receipts are separate, bounded and atomically written with state;
- [ ] memory eviction/expiry cannot break idempotency;
- [ ] stale/out-of-order event causality is exact;
- [ ] member.joined emits only on first canonical JOIN commit;
- [ ] semantic identities/namespaces/duplicate slots fail closed;
- [ ] unsafe pattern shapes fail load;
- [ ] candidate exhaustion clarifies;
- [ ] fragment resolver is bounded and observer-only;
- [ ] start claim drains;
- [ ] real production authority integration passes.

## Chat observation

- [ ] Say2 scope begins after all filters;
- [ ] CreatureSay confirms actual delivery;
- [ ] core chat service has no Phantom dependency;
- [ ] no recipient-rule duplication or World scan;
- [ ] only supported channels and client origin;
- [ ] callback is nonblocking and chat-failure isolated;
- [ ] close detaches exactly.

## Conversation

- [ ] bounded strict conversation catalog/corpus;
- [ ] conversation.state <=4096 and restart-safe;
- [ ] at most one elected responder/plan per dispatch;
- [ ] local/trade require exact unique address;
- [ ] clarification continuation works;
- [ ] social only affects style/suppression;
- [ ] proposals require Checkpoint 2 authorization;
- [ ] production sink sends nothing and executes nothing;
- [ ] operation budget covers all pulse work;
- [ ] disabled mode is inert.

## Evidence/release

- [ ] eight focused modes green;
- [ ] exact affected regressions green;
- [ ] final checkpoint aggregate green;
- [ ] verifier 019 and 020c1 green;
- [ ] one final green ant verify on frozen tree;
- [ ] standalone ant jar;
- [ ] two post-commit byte-identical verifier 020c1 runs;
- [ ] report <=220 lines;
- [ ] token `GOAL_020_CHECKPOINT_1_CONVERSATION_OBSERVATION_PLANNING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
