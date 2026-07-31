# Goal 020 — Checkpoint 1: conversation observation, activation safety and response planning

## 1. Git and status

```text
branch: feature/phantom-world
required parent: 384b521f2cd29f4162c9aca9116eb0ff40cbd681
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic seed: 20002001
commit subject: feat(phantoms): add conversation observation and planning
success token: GOAL_020_CHECKPOINT_1_CONVERSATION_OBSERVATION_PLANNING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

This is a deliberately planned checkpoint of Goal 020, not a corrective suffix
and not Goal 020A/020B.

Create exactly one ordinary child and push it to `origin/feature/phantom-world`. Do not amend,
rebase, squash, merge or force push. Commit and push an honest SUCCESS, PARTIAL
or BLOCKED result.

Record:

```text
Goal 018: ACCEPT_WITH_ACTIVATION_GATE
Goal 019: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS
Goal 020 Checkpoint 1: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 020 action/outbound checkpoint: NOT_STARTED
Goal 021/025: NOT_STARTED
```

Create:

```text
docs/phantoms/reviews/019-russian-semantic-understanding-review.md
```

The review must pin `384b521f2cd29f4162c9aca9116eb0ff40cbd681` and the verdict above.

## 2. Checkpoint boundary

Implement one coherent observer-only capability:

```text
accepted Goal 018/019 activation hardening
+ actual delivered-chat observation
+ bounded per-Phantom dialogue state
+ semantic understanding with clarification continuation
+ social/personality-informed deterministic phrasing
+ structured action/query proposals
+ observer-only response-plan sink
```

Explicitly forbidden in Checkpoint 1:

```text
sending CreatureSay or any outbound chat packet
executing a semantic/action proposal
creating or changing gameplay goals
party invite/accept/refuse/leave execution
movement, combat, support, trade or inventory mutation
remote/runtime LLM
new worker/thread/executor/Future/scheduled task
```

An `ACCEPTED` understanding means only that a structured interpretation and
proposal may be planned. It is never authorization to act.

Goal 020 Checkpoint 2 will own canonical action authorization, durable outbound
delivery, flood control and generated-message loop suppression.

## 3. Execution-efficiency contract

Do not reread old Goal packages, all reports, the entire roadmap, `Player.java`,
`Party.java`, all chat handlers or unrelated subsystems.

Initial READ_SET:

1. this package;
2. Goal 018 social model/service/codec/store and accepted report;
3. Goal 019 semantic model/pack/service/normalizer/grounding and accepted report;
4. `PhantomProfileRepository` component transaction internals;
5. `Say2`, `CreatureSay`, `ChatType`, `IChatHandler`, `ChatHandler`;
6. `PhantomIdentityLeaseRegistry`;
7. `PhantomMaterializationService` exact active snapshot/action-lease methods;
8. `L2jPhantomPartyBackend` exact managed/party snapshot methods;
9. topology query exact point/node methods;
10. `PhantomSystem` construction, scheduler composition, snapshot and shutdown;
11. existing profile/social/semantic/party/headless-player test fixtures;
12. verifier 018/019 patterns.

Up to ten additional exact files or symbols are allowed, each explained in one
sentence in the report. Do not perform broad repository searches after this
audit.

Hard scope limits:

```text
new production/data files <= 16
changed production/data/config files <= 30
changed total files <= 54
no schema migration
no Player.java or Party.java change
no existing chat-handler implementation change
no combat/navigation/background/population/commerce/progression semantic change
no other chronicle/geodata change
report <= 220 lines
soft Goal usage target <= 900,000 tokens
maximum full ant verify invocations: 2
```

The user-supplied task-package files count toward total-file scope but not toward
production/data scope.

If a safe solution needs action execution, outbound chat, a schema migration or
scope above these limits, stop and publish a bounded BLOCKED result. Do not
invent another suffix Goal.

## 4. Historical verifier preflight

Before production edits:

### Verifier 019

Make `verify-task-019.ps1` historical/descendant-compatible:

- pin accepted Goal 019 commit `384b521f2cd29f4162c9aca9116eb0ff40cbd681`;
- verify its exact parent and subject
  `feat(phantoms): add russian semantic understanding`;
- require `384b521f2cd29f4162c9aca9116eb0ff40cbd681` to be an ancestor of current HEAD;
- inspect accepted Goal 019 blobs and scope at the pinned commit;
- never include Goal 020 paths in Goal 019 scope;
- support future descendants without requiring HEAD to be Goal 019's direct
  child.

Run verifier 019 once before continuing.

### Goal 018 and Goal 019 review records

Create final review records without another Goal 018/019 suffix. Goal 018 remains
`ACCEPT_WITH_ACTIVATION_GATE` until the activation tests in this checkpoint pass.
Goal 019 becomes `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`.

## 5. Goal 018 activation gate — durable causality

The current important-memory list is not an idempotency ledger. Memory expiry or
eviction must never allow the same canonical social event to apply relationship
or reputation deltas twice.

### 5.1 Atomic two-component write

Keep:

```text
social.state
```

Add:

```text
social.receipts
schemaVersion: 1
payload <= 4096 bytes
```

Add one narrow generic repository operation for atomically inserting/updating a
bounded sorted set of profile components in one DB transaction.

Required repository contract:

- one positive profile ID;
- 1..3 component mutations;
- component types strictly sorted and unique;
- each mutation is INSERT with expected version `-1`, or UPDATE with an exact
  nonnegative row version;
- exact schema version and payload validation;
- verify all optimistic winners;
- commit all or roll back all;
- return exact durable component rows after the transaction;
- no public arbitrary SQL callback;
- no profile-row or unrelated-component mutation;
- deterministic lock/order by component type.

Social service is the sole writer of both social components and updates
`social.state` plus `social.receipts` atomically. A crash cannot produce:

```text
relationship delta without receipt
or
receipt without the matching state decision
```

### 5.2 Receipt ledger

A receipt contains:

```text
full uppercase SHA-256 event ID
catalog event code
happened minute
event expiry minute
terminal receipt status: APPLIED or STALE
```

Bounds:

```text
maximum 96 receipts
payload <=4096
strict sort by event ID
unknown version/trailing bytes/duplicate/invalid time fail closed
```

Before every record mutation:

1. project effective monotonic minute;
2. remove only expired receipts;
3. if exact receipt exists, return `IDEMPOTENT`;
4. if receipt capacity remains full, return `CAPACITY_REACHED` without applying
   the event;
5. determine temporal policy;
6. atomically persist receipt and social state.

Receipt expiry must be at least the event's catalog TTL boundary. After receipt
expiry, replay is still safe because the event itself is already stale and
cannot reapply a delta.

### 5.3 Out-of-order and expired events

Use exact integer policy:

```text
eventExpiry = happenedMinute + catalog.ttlMinutes
effectiveNow = max(requested/ingress minute, stored monotonic minute)
```

If:

```text
eventExpiry <= effectiveNow
```

record an exact `STALE` receipt atomically, but do not modify relationships,
reputation, agreements, debt or important memories.

For a late but not expired event:

- decay each relationship/reputation delta from `happenedMinute` to
  `effectiveNow` using the target dimension's catalog decay;
- decay memory salience over the same interval;
- apply agreement/debt counters once;
- never give the event a fresh full emotional delta;
- never move monotonic time backwards.

Add `STALE` to the structured social result status. Party success remains
independent of social recording.

### 5.4 First canonical join emission

`party.member.joined` may be emitted only when an exact JOIN operation first
transitions:

```text
CANONICAL_OBSERVED → COMMITTED
```

Role re-evaluation, manifest refresh, route update, restart reconciliation or a
stable committed party pulse must not emit the join event again.

Use exact previous operation phase/identity evidence; do not rely solely on the
receipt ledger to hide producer duplication.

## 6. Goal 019 activation gate — action-safe interpretation

### 6.1 Exact context identity

Strengthen immutable semantic values:

```text
profile key          = positive decimal long
character.object key = positive decimal int
```

For `SlotValue.domain`, validate namespace by slot type:

```text
TARGET_PLAYER        → profile | character.object
PARTY_ROLE           → party.role
CAPABILITY           → capability
ITEM                 → item
NPC                  → npc
CONTENT              → content
TOPOLOGY_NODE        → topology.node
LOCATION             → topology.node | location
```

Reject duplicate slot types in one candidate/result/context previous-slot list.
Do not retain the old UUID-shaped profile test fixture.

### 6.2 Pattern-shape and candidate completeness

At strict pack load, reject:

- a pattern whose first part is a slot;
- adjacent variable-length slots;
- the same slot type appearing twice in one pattern;
- a pattern with more than four slots;
- a pattern with no literal separator between slots.

`CandidateBudget` must expose whether any candidate/match branch was skipped due
to exhaustion. If completeness was not proved, return:

```text
CLARIFICATION_REQUIRED
reason: clarify.complexity
```

Never select a winner from a partially explored candidate space.

Add `clarify.complexity` to the semantic pack and corpus.

### 6.3 Fragment resolution for clarification continuation

Add a bounded observer-only API:

```text
resolveFragment(
    String input,
    InputContext context,
    Set<SlotType> expectedSlots)
```

It:

- uses the same normalization, authority generation and candidate bounds;
- resolves only the supplied 1..4 slot types;
- returns exact typed slots, ambiguity reason and evidence;
- does not infer an intent;
- does not mutate state or execute anything;
- returns clarification on ties/missing slots/budget exhaustion.

This supports:

```text
turn 1: "пригласи"
→ clarify.target_player

turn 2: "Ивана"
→ exact TARGET_PLAYER fragment
→ complete the pending interpretation
```

### 6.4 Start-claim drain

`finishStop()` must wait boundedly for both:

```text
_startClaimed == false
_operationClaims == 0
```

A blocked loader racing with `beginStop()` may not publish a generation after
STOPPED or outlive service shutdown.

### 6.5 Real production-authority proof

Add one integration mode that builds/loads current:

```text
topology
Game Knowledge
party-role catalog
production Russian Semantic Pack
```

and resolves at least one alias for each current category:

```text
ITEM
NPC
CONTENT
TOPOLOGY_NODE
LOCATION
CAPABILITY
PARTY_ROLE
```

Use current immutable authorities, not a fixed map. It must fail closed on a
deliberately missing alias target and prove pinned hashes.

## 7. Generic actual-delivery chat observation

Do not duplicate channel recipient rules and do not scan `World` to guess who
heard a message.

Create one generic non-Phantom service, for example:

```text
java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java
```

The file may contain nested immutable records/interfaces to avoid unnecessary
types.

Core dependency direction:

```text
Say2 / CreatureSay
→ generic ChatObservationService
→ optional installed delivery
→ Phantom conversation ingress
```

The generic service must not import `gameserver.phantoms`.

### 7.1 Dispatch scope

After every existing `OnPlayerChat` filter and the general say filter, and
immediately around the current `IChatHandler.onChat(...)` call, `Say2` opens an
AutoCloseable client-chat dispatch scope containing only immutable primitives:

```text
dispatch ID
origin = CLIENT_CHAT
speaker object ID and exact display name
ChatType
whisper target name if present
final filtered text
server epoch millis
```

Requirements:

- final filtered text, not raw pre-filter text;
- nested/mismatched scopes fail closed;
- scope closes in `finally`;
- no behavior change when no delivery is installed;
- chat rejection/filter/handler behavior stays equivalent.

### 7.2 CreatureSay capture and actual delivery

A `CreatureSay` built inside that dispatch scope captures the immutable
descriptor. All packet instances created by one Say2 dispatch use the same
dispatch ID.

In `CreatureSay.runImpl(Player recipient)`, after ordinary snoop behavior, publish
one delivered observation only when:

```text
descriptor exists
sender is the exact dispatch speaker
text and ChatType match the dispatch
recipient is non-null
origin == CLIENT_CHAT
```

The observation includes actual recipient object ID/name. NPC/system/generated
server messages without a client dispatch are not observed.

Supported current channels:

```text
WHISPER / private
GENERAL / local
PARTY
TRADE
```

Resolve exact current enum names from `ChatType`; do not broaden to SHOUT,
CLAN, ALLIANCE, HERO, PETITION or GM channels in this checkpoint.

### 7.3 Registration and failure isolation

The generic service supports one explicit registration with a closeable handle.

- install/detach is atomic;
- publish never holds the service monitor across delivery;
- delivery is required to be nonblocking;
- delivery exception/backpressure never changes chat delivery;
- registration close prevents later callbacks;
- fixed aggregate metrics only;
- no Player reference is retained.

Reserve an explicit future origin:

```text
PHANTOM_GENERATED
```

but do not send or observe generated Phantom chat in this checkpoint.

## 8. Conversation data pack

Create:

```text
dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml
dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv
```

The XML is strict, XXE-safe and content-addressed. It declares:

- hard limits;
- supported channel policies;
- observation aggregation window in shared-scheduler pulses;
- session/queue/cache/operation bounds;
- cooldowns by channel and response-act category;
- style bands using social modifier keys;
- response acts;
- deterministic Russian templates;
- intent-to-proposal mappings;
- clarification-to-template mappings;
- proposal TTLs;
- observer election policy keys.

Required hard limits:

```text
ingress queue <=1024
open message batches <=256
managed observers per message <=32
operations per pulse <=32
sessions per profile <=8
recent observation hashes <=8
pending slots <=4
templates per response act <=8
rendered text <=100 code points and <=400 UTF-8 bytes
plan evidence <=16
action proposal slots <=8
conversation.state payload <=4096
```

Required style keys:

```text
neutral
warm
cold
cautious
terse
```

Required response acts include:

```text
clarify.intent
clarify.target_player
clarify.entity
clarify.party_role
clarify.location
clarify.quantity
clarify.complexity
ack.action_proposed
ack.query_proposed
ack.accepted
ack.refused
no_response.cooldown
no_response.not_addressed
no_response.unsupported
```

Templates are deterministic data, not claims about retail behavior. No Java
switch over Russian phrases or individual template text.

The TSV contains at least 128 cases across:

```text
private direct messages
party responder election
local/trade exact-name address
clarification continuation
social style bands
cooldown/no-response
semantic rejection/ambiguity
action/query proposal mapping
duplicate observation
restart context
```

## 9. Conversation model and persistence

Create:

```text
java/org/l2jmobius/gameserver/phantoms/conversation/**
```

### 9.1 Immutable values

Define bounded values:

```text
DeliveredObservation
ObservationBatch
ConversationSubject
ConversationSession
PendingClarification
ConversationState
ConversationActionProposal
ConversationResponsePlan
ConversationEvidence
```

No value owns `Player`, `Party`, packet, mutable world object or DB connection.

`ConversationActionProposal` contains:

```text
proposal key
actor profile ref
optional target ref
bounded typed domain/numeric slots
source semantic-result hash
source observation hash
confidence
created/expiry minute
authorization = CHECKPOINT_2_REQUIRED
```

It cannot execute itself and is not a `PhantomGoal`.

### 9.2 conversation.state

Use the existing profile component table:

```text
componentType: conversation.state
schemaVersion: 1
payload <=4096
```

Persist:

- conversation catalog hash;
- semantic pack/corpus/knowledge/topology/role hashes;
- social catalog hash;
- monotonic logical minute;
- up to eight sessions;
- up to eight recent observation hashes;
- no raw chat text and no generated response text.

A session is keyed by:

```text
channel + subjective counterpart ref
```

and stores:

- last observed minute;
- cooldown boundary;
- previous accepted intent code and up to four slots;
- pending clarification intent, known slots, missing-slot mask and expiry;
- last response-act/style/proposal hashes;
- no names.

Codec requirements:

- compact binary;
- strict version/order/duplicates/ranges;
- unknown/trailing/truncated fail closed;
- declared worst case <=4096.

### 9.3 Store/service ownership

Use existing profile components, no schema change.

- one conversation service is the sole writer;
- fixed striped locks;
- optimistic reload/retry maximum three;
- bounded access-order cache from catalog limits;
- no startup profile scan;
- no periodic writes;
- no lock held across semantic/social/party/context/plan callbacks;
- conversation mutation is durable before a successful plan is published;
- plan-sink failure is typed/metric-visible and never mutates gameplay.

## 10. Conversation ingress, batching and responder election

The installed Phantom delivery performs only:

1. reject any non-client origin;
2. consult `PhantomIdentityLeaseRegistry` for the recipient object ID;
3. if recipient owner is not `PHANTOM`, return ignored without DB;
4. copy immutable observation into the bounded queue;
5. return immediately.

No parser, DB query or social query runs on the chat delivery thread.

On the existing shared scheduler pulse:

1. drain bounded ingress operations;
2. map the actual observer object ID to one exact profile;
3. aggregate managed recipients by dispatch ID;
4. defer a new batch until at least the next shared pulse so all synchronous
   CreatureSay deliveries can join it;
5. enforce max 32 managed observers; overflow yields no response;
6. elect at most one responder.

Election:

```text
WHISPER:
    exact delivered managed recipient

PARTY:
    managed canonical Party leader if among actual observers;
    otherwise smallest positive profile ID among actual observers

GENERAL / TRADE:
    only a uniquely exact-name-addressed managed observer;
    zero or duplicate exact names → no response
```

For GENERAL/TRADE, support a bounded leading/trailing vocative form using exact
observer display-name tokens and punctuation. Strip only the exact elected
observer name before semantic parsing. No fuzzy player-address election.

Self-observation and a message from the same managed profile are ignored in
Checkpoint 1 to prevent bot-to-bot response loops.

Every pulse must count all drained observations, batch transitions, context
queries, state mutations and plan publications, and never exceed the catalog
operation budget. No full profile/session scan.

## 11. Context construction

Create a narrow production context port.

For one elected observer, build `InputContext` from immutable snapshots only:

- observer profile identity;
- actual speaker identity:
  - `profile` only if current profile mapping proves it;
  - otherwise `character.object`;
- channel;
- canonical Party leader/members if currently observed;
- actual speaker as the only nearby/recent candidate supplied by chat;
- current topology node from observer's current materialized position if exact;
- previous accepted intent/slots from `conversation.state`;
- no guessed selected target.

The conversation core does not read `World`, Player or Party directly. One L2J
adapter may use existing materialization/party/topology seams and must release
all action/read leases before returning the immutable snapshot.

If the observer is no longer a current materialized Phantom when the batch is
processed, discard the observation without writing state.

## 12. Understanding and clarification continuation

For a normal turn:

```text
semanticService.understand(text, context)
```

For a live pending clarification:

- first allow a complete new intent to replace the pending flow;
- otherwise call `resolveFragment` for the exact missing slot set;
- merge only exact compatible slots;
- preserve original semantic authority hashes;
- if complete, create one completed interpretation/proposal;
- if ambiguous/missing, emit another clarification plan;
- expire pending clarification deterministically by catalog TTL or turn bound.

A stale previous slot/intent cannot authorize a new proposal after expiry or
authority drift.

`REJECTED` understanding creates no action proposal. The catalog decides whether
to create no response or a bounded clarification plan.

## 13. Social/personality-informed phrasing

Read only these social modifiers:

```text
conversation.warmth
conflict.escalation
party.invite.preference
```

Social modifiers may select style/template and whether an observer-only
acknowledgement is suppressed. They must not:

- authorize an action;
- change a grounded target;
- turn a rejected/ambiguous understanding into accepted;
- bypass channel/address/cooldown policy.

Style selection is deterministic from:

```text
owner profile ID
observation hash
response-act key
social modifier bands
conversation catalog hash
```

No global random state and no wall-clock-only choice.

Unknown/neutral social state uses `neutral`. Social failure yields neutral style
and a fixed failure metric; it does not block chat or create an action.

Rendered text:

- comes only from strict templates;
- substitutes bounded validated display values;
- strips/rejects controls, item-link byte 8 and unsupported markup;
- remains within declared code-point/UTF-8 bounds;
- is never sent in Checkpoint 1.

## 14. Proposal and plan sink

Create:

```text
PhantomConversationPlanSink
```

Production installs an observer-only sink that performs no packet send and no
gameplay mutation. It may update fixed aggregate diagnostics only.

Tests install a capture sink.

A response plan contains:

```text
owner profile
dispatch/observation hash
channel and counterpart
semantic result hash
response act and style
rendered text
optional action/query proposal
cooldown boundary
bounded evidence
```

Exactly one plan at most may be published for one dispatch ID. Duplicate
delivered observations are idempotent.

No plan is persisted as a durable executable outbox in Checkpoint 1.

## 15. Lifecycle and PhantomSystem

Production startup:

```text
profile repository
→ social service with activation-safe receipts
→ topology / Game Knowledge
→ semantic understanding service
→ existing gameplay services and Party
→ conversation catalog/service
→ install generic chat observation delivery
→ install conversation shared-scheduler control
→ scheduler start
```

Conversation must start after every read authority it uses.

Normal shutdown:

```text
conversation beginStop:
    detach chat registration
    reject new ingress
    clear or terminalize unprocessed observer-only queues
→ conversation finishStop:
    zero operations/persistence/plan/context claims
→ Party stop
→ social and semantic stop
→ their authorities stop
```

A callback racing detach either entered under an operation claim or is rejected
before queue mutation.

Disabled Phantom World:

- does not load social/semantic/conversation files;
- does not install chat observation delivery;
- performs no conversation DB access;
- generic chat service remains no-op.

Expose bounded conversation and generic observation snapshots in
`PhantomSystem.Snapshot` and configured shutdown evidence.

## 16. Exact scope

Allowed existing production files:

```text
java/org/l2jmobius/gameserver/network/clientpackets/Say2.java
java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java
java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialModel.java
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStateCodec.java
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStore.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticModel.java
java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java
java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Allowed new production/data:

```text
java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialReceipt*.java
java/org/l2jmobius/gameserver/phantoms/conversation/**
dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml
dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv
```

A narrow read-only accessor may be added to materialization or party backend only
if the initial audit proves the current public seam cannot return the required
immutable profile/object mapping. It counts toward production scope.

Allowed semantic data adaptation:

```text
dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml
dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv
```

only for `clarify.complexity` and activation cases.

Allowed tests/build/tools/docs:

```text
build.xml
PhantomTestLauncher.java
PhantomSocialActivation*.java
PhantomSemanticActivation*.java
PhantomConversation*.java
targeted adaptations to existing social/semantic/party/profile/system/chat tests
tools/phantoms/verify-task-019.ps1
tools/phantoms/verify-task-020c1.ps1
master plan/roadmap status only
Goal 019 review
Goal 020 Checkpoint 1 architecture/report/task docs
```

Forbidden:

- `Player.java`, `Party.java`;
- existing chat-handler implementation files;
- action/step/candidate registration;
- `CreatureSay` creation for Phantom responses;
- inventory, party, movement, combat, trade or goal mutation;
- schema/migrations;
- other chronicles/geodata;
- Goal 020 Checkpoint 2, Goal 021/025.

## 17. Mandatory focused modes

```text
social-activation
semantic-activation
chat-observation
conversation-catalog-codec
conversation-understanding
conversation-social-style
conversation-chat-integration
conversation-lifecycle-performance
```

## 18. Verification discipline

Development order:

1. compile exact affected production/tests;
2. social activation mode;
3. semantic activation mode including real production authority;
4. generic chat observation mode;
5. conversation catalog/codec;
6. conversation understanding and clarification;
7. conversation social style;
8. actual delivered-chat integration;
9. lifecycle/performance;
10. exact affected profile/social/semantic/party/system/chat regressions;
11. verifier 019 and working verifier 020c1;
12. one final `phantom-conversation-checkpoint1-test` aggregate.

Do not run broad legacy affected aggregates.

After focused/static gates are green, freeze production/data/test/build/verifier
files:

```text
one final full ant verify
one standalone ant jar
update report terminal-results section only
ordinary commit/push
two post-commit byte-identical verifier 020c1 runs
```

A second full verify is allowed only after a real relevant production/test/build/
verifier fix. An unrelated preflight-green flake gets one exact targeted retry
without a broad rerun. Third full verify is forbidden.

Verifier 020c1 must:

- pin accepted Goal 019 ancestry;
- verify exact graph/subject/scope;
- enforce no Player/Party/chat-handler/action/schema changes;
- inspect generic chat dependency direction;
- prove no outbound send/action execution;
- verify atomic social components and receipt bounds;
- verify semantic identity/slot/pattern/budget/start-drain contracts;
- verify conversation payload/queue/pulse/template bounds;
- verify disabled mode, lifecycle, UTF-8 and JAR contents;
- be descendant-compatible after checkpoint acceptance.

Create:

```text
docs/phantoms/architecture/CONVERSATION_OBSERVATION_PLANNING_CONTRACT.md
docs/phantoms/reports/020-checkpoint-1-conversation-observation-planning.md
```

Print `GOAL_020_CHECKPOINT_1_CONVERSATION_OBSERVATION_PLANNING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. Otherwise publish an honest
bounded result without claiming Checkpoint 1 complete.
