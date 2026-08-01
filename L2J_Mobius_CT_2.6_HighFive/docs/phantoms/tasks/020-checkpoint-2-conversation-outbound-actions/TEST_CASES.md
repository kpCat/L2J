# Test matrix — Goal 020 Checkpoint 2

## Managed ingress and housekeeping

- 100k supported real recipients: queue 0, batches 0, context lookups 0;
- 100 real + one managed exact address: one managed observer and one plan;
- PHANTOM lease removed before processing: discard;
- 32 managed recipients is accepted; 33rd managed recipient overflows;
- both delivered and closed offers fail: no forced-overflow residue;
- 256 delayed batches promote within operation budget;
- no full queue/map scan instrumentation.

## Execution codec/store

- worst-case four entries + sixteen receipts <=4096;
- all state transition positives and invalid negatives;
- strict order, duplicate, truncation, trailing, unknown version/state;
- atomic conversation.state/execution insert and update;
- insert collision and three optimistic retries;
- crash/failure after each SQL mutation rolls back;
- restart page load <=256 and no all-profile scan.

## Handoff

- persisted state always has matching execution for a non-silent plan;
- signal lost after commit is recovered;
- duplicate source dispatch creates one entry;
- capacity full fails without overwriting state or observation receipt;
- suppressed response creates no outbound entry.

## Goal actions

- party.invite creates exact conversation-owned `party.form`;
- Decision engine/Party path observes it; conversation never invokes invite mutation;
- active unrelated goal yields goal.busy;
- retry same plan is idempotent;
- terminal old goal may be replaced;
- party.leave carries exact generation;
- party.travel carries exact authoritative destination and generation;
- expiration cancels only exact owned goal.

## Party accept/refuse

- exact pending real→Phantom and Phantom→Phantom invitations;
- accept creates exact ACTIVE join goal and canonical membership;
- refuse uses canonical response and creates no join goal;
- wrong speaker, stale identity, no pending or already terminal fail closed;
- duplicate plan cannot respond twice;
- restart reconciles terminal invitation/Party state.

## Queries

Use real Game Knowledge/topology/role data:

- role vacancy;
- NPC/entity location;
- item drop/spoil/manor/recipe/shop sources where available;
- content requirements;
- exact unknown and ambiguity controls;
- output facts and evidence bounded;
- DB/gameplay writes zero.

## Outbound

Use real materialized Phantom and current handlers:

- WHISPER reaches exact counterpart;
- PARTY uses current Party broadcast;
- GENERAL and TRADE use current handlers/range rules;
- no direct recipient list in outbound adapter;
- generated origin visible in audit;
- generated callbacks produce zero conversation ingress;
- invalid text, missing handler, offline target and dematerialization fail safely;
- DISPATCHING crash → UNCERTAIN and no resend;
- duplicate signal/entry sends once;
- one source dispatch sends at most one message.

## Deferred actions

`party.support`, `party.assist`, `party.regroup`:

- action state DEFERRED;
- no goal, combat, movement or Party mutation;
- bounded explicit response;
- stable terminal receipt.

## Lifecycle/performance

- 10k execution entries through shared scheduler;
- operation budget never exceeded;
- no new worker/thread/executor/Future/task;
- shutdown during query/goal/party/outbound/store boundaries;
- all claims/queues/due indexes zero after stop;
- disabled mode loads no execution file and registers nothing.
