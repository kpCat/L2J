# Goal 020 — Checkpoint 2: durable outbound conversation and canonical action dispatch

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: 21ba300fc612f9777891912f80efc633f5b6db18
Checkpoint 1 implementation: e7ba469e63caa6dee113278087258fab005a435a
Checkpoint 1 safety completion: 21ba300fc612f9777891912f80efc633f5b6db18
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic seed: 20002002
commit subject: feat(phantoms): activate conversation responses and actions
success token: GOAL_020_CONVERSATION_OUTBOUND_ACTIONS_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

This is the second and final checkpoint planned before Goal 020 implementation
started. It is not Goal 020A/020B and not a corrective suffix.

Create exactly one ordinary child and push to `origin/feature/phantom-world`. No amend,
rebase, squash, merge, force push or force-with-lease. Publish an honest SUCCESS,
PARTIAL or BLOCKED result.

Record:

```text
Goal 018: ACCEPT after activation gates closed in Goal 020 Checkpoint 1
Goal 019: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS
Goal 020 Checkpoint 1: ACCEPT_WITH_ACTIVATION_GATE
Goal 020 Checkpoint 2: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 021/025: NOT_STARTED
```

Create the actual independent review record:

```text
docs/phantoms/reviews/020-checkpoint-1-final-review.md
```

Pin `21ba300fc612f9777891912f80efc633f5b6db18` and verdict `ACCEPT_WITH_ACTIVATION_GATE`. The only remaining
Checkpoint 1 activation requirements are the managed-recipient ingress and
bounded housekeeping corrections in section 4 below. No new Checkpoint 1 suffix
commit is required.

## 2. Product result

Implement one coherent final Goal 020 capability:

```text
actual client chat observation
→ deterministic conversation plan
→ atomic durable execution envelope
→ allowlisted authorization
→ canonical read query or gameplay-goal/party action
→ canonical result observation
→ at-most-once generated outbound chat
→ durable terminal receipt
```

Goal 020 is complete only when:

- actual recipients remain the observer authority;
- non-Phantom recipients never enter Phantom ingress;
- generated text is actually delivered through current channel handlers;
- generated delivery cannot loop back into conversation understanding;
- read-only query answers are grounded in current immutable authorities;
- gameplay actions use existing Goal/Decision/Party services;
- no conversation code mutates Player/Party/inventory/movement/combat directly;
- crash/restart cannot duplicate a goal, party response or outbound message;
- uncertain outbound state is never blindly retried.

## 3. Execution-efficiency contract

Do not reread old Goal packages, all reports, the whole roadmap, `Player.java`,
`Party.java`, all chat handlers or unrelated subsystems.

Initial READ_SET:

1. this package;
2. accepted Goal 020 Checkpoint 1 final tree and verifier;
3. `ChatObservationService`, `Say2`, `CreatureSay`;
4. conversation catalog/model/codec/store/service/context/plan sink;
5. `PhantomGoal`, `PhantomGoalStateStore`, Decision engine goal lifecycle;
6. `PhantomPartyDecision` and only exact coordinator methods for
   invite/join/refuse/leave/travel/current generation/pending invitation;
7. `PhantomGameKnowledgeQuery` exact item/NPC/content/source/location queries;
8. topology exact node/anchor/point lookup;
9. materialization action-lease and object/profile lookup methods;
10. `ChatHandler`, `IChatHandler` and exact GENERAL/WHISPER/PARTY/TRADE handlers;
11. `PhantomSystem` composition/snapshot/shutdown;
12. existing Checkpoint 1 integration fixtures and relevant goal/party tests.

At most twelve additional exact files/symbols are allowed, each listed in the
report with one sentence. No broad search after the audit.

Hard limits:

```text
new production/data files <= 18
changed production/data/config files <= 34
changed total files <= 60
no schema migration
no Player.java or Party.java change
no existing chat-handler implementation change
no combat/navigation/background/population/commerce/progression semantic rewrite
no dedicated worker/thread/executor/Future/scheduled task
report <= 240 lines
soft Goal usage target <= 1,100,000 tokens
maximum full ant verify invocations: 2
```

If safe implementation requires bypassing canonical services, changing schema,
or exceeding the limits, publish a bounded BLOCKED result. Do not invent a
third Goal 020 checkpoint.

## 4. Mandatory Checkpoint 1 activation preflight

### 4.1 PHANTOM-only delivery ingress

Checkpoint 1 currently accepts every actual recipient of a supported channel
and resolves managed identity later. That is unacceptable for GENERAL/TRADE:
many real recipients can fill the queue or trigger observer overflow before one
addressed Phantom is elected.

Restore the original process-local fast path:

```text
ChatObservation delivery
→ PhantomIdentityLeaseRegistry.getOwnerKind(recipientObjectId)
→ owner != PHANTOM: ignored immediately, no queue/batch/context/DB
→ owner == PHANTOM: enqueue immutable observation
```

Requirements:

- store and use the injected identity registry;
- only managed recipients count toward `observersPerMessage`;
- 100,000 real-recipient callbacks perform zero ingress offers and zero context
  lookups;
- GENERAL/TRADE with hundreds of real recipients plus one addressed Phantom
  retains exactly one managed observer and can respond;
- identity can still be revalidated by ContextPort at processing time;
- a lease that changed from PHANTOM before processing causes discard.

### 4.2 Fully bounded housekeeping

All work that can scale with open batches must be bounded and represented in
metrics.

- promote delayed entries incrementally, never all due entries in one pulse
  outside the operation budget;
- count each delayed→due transition as one operation;
- `_forcedOverflow` cannot retain a dispatch forever if both DELIVERED and CLOSED
  ingress offers fail;
- a failed CLOSED offer terminalizes or schedules exact bounded overflow
  cleanup;
- forced-overflow entries have an exact bounded lifecycle and zero shutdown
  residue;
- no full open-batch, due, delayed or tombstone scan on pulse.

Add dynamic saturation tests.

### 4.3 Historical verifier

Make verifier 020c1 descendant-compatible after acceptance:

- pin `21ba300fc612f9777891912f80efc633f5b6db18` as the final accepted Checkpoint 1 tree;
- verify implementation and completion graph/subjects;
- inspect Checkpoint 1 blobs at `21ba300fc612f9777891912f80efc633f5b6db18`;
- require `21ba300fc612f9777891912f80efc633f5b6db18` to be an ancestor of current HEAD;
- do not include Checkpoint 2 files in Checkpoint 1 scope.

Run verifier 020c1 before main Checkpoint 2 work.

## 5. Durable conversation execution component

Use the existing profile component table; no schema changes.

Create:

```text
componentType: conversation.execution
schemaVersion: 1
payload <=4096 bytes
```

It stores at most four execution entries plus at most sixteen terminal plan
receipts.

### 5.1 Execution entry

An immutable entry contains:

```text
full uppercase SHA-256 plan ID
observation hash
owner profile ID implicit in component row
channel
counterpart stable reference
response act/style
rendered UTF-8 text <=400 bytes
optional action/query proposal
created minute
expiry minute
outbound state
action state
goal ID/revision if submitted
exact result/reason key
attempt counters
```

Outbound states:

```text
NONE
PREPARED
DISPATCHING
SENT
FAILED
UNCERTAIN
```

Action states:

```text
NONE
PREPARED
SUBMITTED
COMPLETED
REJECTED
DEFERRED
EXPIRED
UNCERTAIN
```

Terminal receipts contain plan ID, observation hash, final action/outbound
states, terminal minute and reason key.

Rules:

- entries and receipts are strictly ordered and unique;
- unknown version/state, invalid transition, duplicate plan, trailing bytes or
  range violation fails closed;
- terminal/expired entries may be compacted deterministically;
- nonterminal entry is never silently evicted;
- capacity full returns typed `CAPACITY_REACHED`;
- declared worst-case encoding must remain <=4096.

## 6. Atomic plan handoff

Checkpoint 1 currently saves `conversation.state` and then calls a volatile
observer-only sink. That is not a durable handoff for real outbound/action work.

Refactor the conversation store/service so that the PERSISTING phase atomically
mutates:

```text
conversation.state
conversation.execution
```

using the accepted sorted multi-component repository transaction.

For one dispatch:

```text
conversation state mutation
+ PREPARED execution entry
```

must both commit or both roll back.

Rules:

- suppressed/no-response plans may persist state without execution entry only
  when policy explicitly requires silence;
- duplicate observation/plan returns DUPLICATE and creates neither a second
  execution entry nor a second plan;
- plan sink becomes a bounded wake/signal only; durable execution state is truth;
- crash after atomic save but before signal is recovered from the component;
- no startup scan of all profiles: page only profiles containing
  `conversation.execution`, maximum 256 rows/page;
- restart rebuilds a bounded due index;
- one execution service is sole writer of execution entries.

## 7. Execution service and lifecycle

Create a shared-scheduler control port under `phantoms/conversation/execution`
or the existing conversation package.

Lifecycle:

```text
NEW → RUNNING → STOPPING → STOPPED
                     ↘ FAILED
```

No new worker.

Every shared pulse performs at most the configured operation budget:

```text
load/recover one entry
authorize
prepare canonical mutation
submit/observe action
prepare outbound
dispatch outbound
publish terminal state
```

No monitor is held across Goal/Party/Knowledge/topology/materialization/chat
boundaries.

Shutdown:

```text
conversation observation admission closes
→ planner drains atomic handoffs
→ execution admission closes
→ in-flight canonical/outbound claims drain
→ uncertain transitions persisted
→ execution stops
→ remaining Goal 020 dependencies stop
```

Expose bounded snapshots and fixed metrics. No profile IDs or plan IDs in metric
labels or high-frequency logs.

## 8. Authorization policy

Create a strict, XXE-safe, content-addressed data file:

```text
dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml
```

It declares:

- executable proposal keys;
- kind: `QUERY`, `GOAL`, `PARTY_RESPONSE`, `DEFERRED`;
- required channel/slot/counterpart constraints;
- goal type and constraint mapping where applicable;
- result reason keys;
- outbound result templates by action/query state and style;
- execution/outbound TTL and retry limits;
- hard queue/cache/pulse/component bounds.

No Java switch over Russian text or template strings. Java may switch over
typed execution kinds and canonical proposal keys only through validated catalog
entries.

Required policy:

```text
QUERY:
  party.role.query
  entity.locate
  item.acquire
  item.source
  content.requirements

GOAL:
  party.invite
  party.leave
  party.travel

PARTY_RESPONSE:
  party.accept
  party.refuse

DEFERRED:
  party.support
  party.assist
  party.regroup
```

Deferred actions are intentional boundaries for Goal 024 tactical policy. They
produce no gameplay mutation and an explicit bounded response.

## 9. Goal authorization and arbitration

Conversation never calls gameplay mutations directly when an accepted
Goal/Decision path exists.

Use `PhantomGoalStateStore` and the existing Decision engine.

### 9.1 Stable identity

Derive deterministic positive goal ID from:

```text
plan ID + owner profile ID + proposal key
```

The execution entry stores exact goal ID and revision.

### 9.2 Existing goal arbitration

```text
no goal.runtime:
    atomically INSERT conversation-owned ACTIVE goal + update execution SUBMITTED

terminal goal.runtime:
    atomically REPLACE with new conversation-owned ACTIVE goal + update execution

same exact conversation goal/plan:
    IDEMPOTENT

different ACTIVE goal:
    REJECTED goal.busy; never overwrite or cancel it
```

A conversation-owned goal is identified by exact purpose/reason/plan evidence,
not merely by goal type.

On expiry, cancel only the exact conversation-owned goal ID/revision if still
ACTIVE. Never cancel unrelated work.

### 9.3 Mappings

#### `party.invite`

Create an explicit `party.form` goal:

- target = exact grounded TARGET_PLAYER;
- subject = current general party objective;
- one target only;
- no global matchmaking;
- Decision/Party coordinator owns form/invite execution.

#### `party.leave`

Require a current committed party claim. Store exact current party generation in
goal constraints and create `party.leave`.

#### `party.travel`

Require exact grounded topology/location slot. Resolve one current authoritative
destination point before goal creation and store exact x/y/z/instance and party
generation constraints. Create `party.travel`.

Do not add new party movement semantics.

## 10. Exact party accept/refuse

A conversation reply such as `согласен` or `отказываюсь` must operate only on an
exact current pending managed invitation from the message counterpart.

Add a narrow read/action port to `PhantomPartyCoordinator` if required:

```text
pendingInvitation(profileId)
respondToPending(profileId, exactInvitationIdentity, ACCEPT|REFUSE, planId)
```

Contracts:

- one exact pending identity, requester and invitee;
- counterpart must match the conversation speaker;
- ACCEPT requires an exact ACTIVE `party.join` goal created atomically with the
  execution SUBMITTED state;
- REFUSE uses the canonical invitation response path and does not fabricate a
  join goal;
- stale/no/multiple invitation fails closed;
- retry with same plan ID is idempotent;
- a different plan cannot respond twice;
- terminal callback/canonical Party observation determines COMPLETED/REJECTED;
- real-player consent is never inferred.

No packet handler invocation and no fake GameClient.

## 11. Read-only query execution

Use current immutable Game Knowledge/topology/party role facts.

Queries:

### `party.role.query`

Return current canonical role/vacancy evidence for the owner's Party. No
matchmaking or role mutation.

### `entity.locate`

For exact NPC/content/topology refs, return a bounded authoritative topology node
or exact `not_found/ambiguous` result.

### `item.acquire` / `item.source`

Return bounded current source categories such as drop, spoil, manor, recipe or
known shop facts only when present in accepted Game Knowledge. Do not promise a
purchase or craft.

### `content.requirements`

Return bounded current recommended requirements from accepted content facts.

Query results contain structured facts first. Russian output is rendered from
strict templates and bounded validated values. No guessed names, routes, prices
or missing facts.

Queries never create goals or mutate gameplay.

## 12. Generated outbound delivery

Create a narrow outbound port backed by current materialization and chat
handlers.

### 12.1 Sender ownership

Before send:

- owner profile is still current;
- acquire one materialization action lease;
- exact Player object/profile identity matches;
- channel and counterpart are revalidated;
- generated text passes strict code-point/UTF-8/control/item-link limits.

### 12.2 Channel delivery

Use the currently registered `IChatHandler` for:

```text
WHISPER
PARTY
GENERAL
TRADE
```

Do not duplicate recipient/range/party rules and do not modify handler
implementations.

- WHISPER resolves the current exact counterpart name from stable identity;
- PARTY requires current Party membership;
- GENERAL/TRADE require the original exact-address conversation and current
  materialization;
- missing/offline counterpart or invalid current state fails typed.

### 12.3 Generated origin and loop prevention

Extend the generic dispatch seam with explicit:

```text
Origin.PHANTOM_GENERATED
```

The outbound service opens a generated dispatch scope around the existing chat
handler.

`CreatureSay` recipient callbacks may be audited as generated delivery, but:

- conversation ingress accepts only `CLIENT_CHAT`;
- generated dispatch can never create a new conversation batch;
- no heuristic text comparison is used for loop prevention;
- client and generated metrics remain separate.

### 12.4 At-most-once crash policy

Before calling a chat handler, durably transition:

```text
PREPARED → DISPATCHING
```

After normal return:

```text
DISPATCHING → SENT
```

If restart sees `DISPATCHING`, mark `UNCERTAIN` and never resend automatically.
High Five has no client acknowledgement that can prove delivery. Prefer a
possible lost response over a duplicate message.

Failures before DISPATCHING may be retried within catalog bounds. Failures after
DISPATCHING are terminal/uncertain.

## 13. Result ordering

For one plan:

### Query

```text
authorize
→ execute read-only query
→ build result text
→ outbound DISPATCHING/SENT
→ terminal receipt
```

Send one result message, not an acknowledgement plus a result.

### Goal action

```text
authorize
→ atomically submit exact goal
→ send one submitted/rejected/deferred response
→ continue observing exact goal in background
→ durable terminal action state
```

Do not send a second completion message in Goal 020 unless the policy explicitly
declares it. Default is one response per source dispatch.

### Party response

```text
authorize exact invitation
→ canonical accept/refuse
→ observe terminal callback/Party fact
→ send one exact result response
→ terminal receipt
```

## 14. PhantomSystem composition

Production ordering:

```text
accepted social/semantic/party/knowledge authorities
→ conversation planner
→ conversation execution store/service
→ planner installs durable handoff sink
→ generic observation installed
→ shared scheduler starts
```

Execution service shares the existing composite scheduler control port.

Shutdown order follows section 7.

Disabled Phantom World:

- loads no conversation execution XML;
- installs no observer/generated dispatch;
- performs no conversation execution DB access.

Update master plan/roadmap status only after all gates.

## 15. Exact scope

Allowed existing production:

```text
java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java
java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java
java/org/l2jmobius/gameserver/phantoms/conversation/**
java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java
java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Repository/goal-store changes are allowed only for atomic sorted component/goal
mutations required by this task. No arbitrary SQL callback.

Allowed new production/data:

```text
java/org/l2jmobius/gameserver/phantoms/conversation/execution/**
or equivalent bounded files under phantoms/conversation/**
dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml
```

One narrow read-only/dispatch adapter may be added for Game Knowledge/topology/
chat/party if necessary.

Allowed tests/build/tools/docs:

```text
build.xml
PhantomConversationExecution*.java
targeted adaptations to conversation/chat/party/decision/knowledge/system tests
PhantomTestLauncher.java
tools/phantoms/verify-task-020c1.ps1
tools/phantoms/verify-task-020c2.ps1
master plan/roadmap status only
Goal 020 Checkpoint 1 final review
Goal 020 final architecture/report/review/task docs
```

Forbidden:

- `Player.java`, `Party.java`;
- existing chat handler implementations;
- packet-handler invocation or fake Phantom client;
- direct inventory/combat/navigation/commerce mutation;
- schema/migrations;
- runtime LLM or remote service;
- clans/PvP/Rift/party farming tactical policy;
- other chronicles/geodata;
- Goal 021/025.

## 16. Mandatory focused modes

```text
conversation-managed-ingress
conversation-execution-catalog-codec
conversation-handoff-durability
conversation-query-execution
conversation-party-actions
conversation-outbound-chat
conversation-restart-idempotency
conversation-execution-lifecycle-performance
```

## 17. Mandatory evidence

### Managed ingress

- 100,000 supported-channel real recipients: ingress offers 0, context lookups 0;
- 100 real recipients + one addressed Phantom: exactly one managed observer;
- lease changes before processing: discard;
- forced overflow dual-drop leaves zero residue;
- delayed promotion work remains within pulse budget.

### Durable handoff

Inject failure:

```text
before atomic state/execution save
between component statements
after commit before signal
during restart page rebuild
before/after every execution state transition
```

Prove one plan entry or none, never state without execution for a response plan.

### Queries

Real current authority tests for every query type, exact not-found/ambiguous
controls, bounded factual output and zero gameplay writes.

### Actions

- busy unrelated active goal never overwritten;
- same plan goal insertion idempotent;
- party invite reaches current Decision/Party path;
- accept/refuse uses one exact pending invitation;
- leave/travel carry exact generation/current destination;
- stale goal/invitation/counterpart fails closed;
- deferred support/assist/regroup mutates nothing.

### Outbound

Using real headless/materialized Phantom and current handlers:

- WHISPER, PARTY, GENERAL and TRADE generated delivery;
- current recipient/range/party rules remain handler-owned;
- generated callbacks carry `PHANTOM_GENERATED`;
- generated callbacks create zero conversation ingress/batches;
- invalid/offline state sends nothing;
- crash at DISPATCHING restarts as UNCERTAIN and does not resend;
- duplicate signal/plan produces one send maximum.

### Performance/lifecycle

- 100,000 real-recipient callbacks no queue/context/DB;
- 10,000 durable execution records through bounded scheduler;
- every pulse within configured operation budget;
- startup paging <=256;
- no all-profile scan;
- no new worker/thread/executor/Future/task;
- shutdown at each action/query/outbound boundary drains or persists UNCERTAIN;
- final residue zero.

## 18. Verification discipline

Development:

1. compile exact affected production/tests;
2. run managed-ingress preflight;
3. run execution catalog/codec;
4. durable handoff;
5. query execution;
6. party actions;
7. outbound chat;
8. restart/idempotency;
9. lifecycle/performance;
10. exact affected Checkpoint 1, Goal/Decision, Party, Knowledge, materialization
    and shutdown regressions;
11. verifier 020c1 and working verifier 020c2;
12. one final `phantom-conversation-checkpoint2-test` aggregate.

Do not run broad historical affected aggregates during development.

After focused/static gates are green, freeze production/data/test/build/verifier:

```text
one final full ant verify
one standalone ant jar
update report terminal-results section only
ordinary commit/push
two post-commit byte-identical verifier 020c2 runs
```

A second full verify is allowed only after a real relevant production/test/build/
verifier fix. An unrelated preflight-green flake receives one exact targeted
retry without broad rerun. Third full verify is forbidden.

Verifier 020c2 must:

- pin accepted Checkpoint 1 `21ba300fc612f9777891912f80efc633f5b6db18`;
- verify exact graph/subject/scope;
- enforce no Player/Party/handler/schema/direct gameplay changes;
- verify PHANTOM-only ingress;
- verify atomic state/execution handoff;
- verify execution codec bounds/transitions;
- verify canonical Goal/Party/Knowledge dependencies;
- prove generated origin and no loop;
- prove no direct action/outbound bypass;
- verify tests, lifecycle, UTF-8, datapack files and JAR classes;
- be descendant-compatible after acceptance.

Create:

```text
docs/phantoms/architecture/CONVERSATION_OUTBOUND_ACTION_CONTRACT.md
docs/phantoms/reports/020-conversation-outbound-actions.md
docs/phantoms/reviews/020-checkpoint-2-independent-review.md
```

Print `GOAL_020_CONVERSATION_OUTBOUND_ACTIONS_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. Otherwise commit/push a bounded
honest result without starting Goal 021.
