# Goal 018 — Personality, bounded memory, subjective reputation and relationships

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: 6cf261370e3cb98158805828e995cfe6e8b14651
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic seed: 18001801
commit subject: feat(phantoms): add social memory and relationships
success token: GOAL_018_SOCIAL_MEMORY_RELATIONSHIPS_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Create exactly one ordinary child and push to `origin/feature/phantom-world`. No amend,
rebase, squash, merge or force push. Commit/push an honest SUCCESS, PARTIAL or
BLOCKED result.

Record the independent verdict:

```text
Goal 016: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS
Goal 017: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS
Goal 018: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 019/020/021/025: NOT_STARTED
```

Create `docs/phantoms/reviews/017-party-coordination-final-review.md`. No new
Goal 017 suffix is required.

## 2. Execution-efficiency contract

Do not reread old task packages, all reports, the whole master plan, the whole
roadmap, `Player.java`, `Party.java` or unrelated subsystems.

Initial READ_SET:

1. this package;
2. Goal 018 sections of roadmap/master plan;
3. `PhantomProfileRepository` component methods and `PhantomProfileComponent`;
4. `PhantomGoal`, `PhantomPlanningContext`, consideration/candidate contracts;
5. `PhantomPartyCoordinator` terminal/membership/social injection ranges;
6. `PhantomPartyDecision`;
7. `PhantomSystem` production construction/snapshot/shutdown ranges;
8. `verify-task-017.ps1`;
9. existing profile/party in-memory and real-DB test fixtures.

At most six additional exact files/symbols, each listed in the report with one
sentence. No broad repository search after this audit.

Hard limits:

```text
new production files <= 10
changed production files <= 15
changed total files <= 32
no schema migration
no Player/Party/packet/combat/navigation/background/population change
no worker/thread/executor/Future/scheduled task
report <= 190 lines
soft Goal usage target <= 550,000 tokens
```

If a necessary design exceeds these limits, stop unsafe work and report the
exact blocker instead of inventing 018A/018B.

## 3. Product result

Implement one coherent capability:

```text
deterministic personality
+ bounded important-event memory
+ asymmetric relationships
+ subjective reputation
+ agreement/debt history
+ deterministic lazy decay/expiry
+ explainable bounded action modifiers
+ canonical party-event ingestion
+ restart-safe component persistence
```

This Goal does not parse or generate text and does not execute social actions.
It supplies structured facts and modifiers to later Goals 020/024/025/027.

## 4. Preflight closure without Goal 017 suffix

Before social production work:

### Historical verifier 017

Make `verify-task-017.ps1` descendant-compatible:

- pin accepted final Goal 017 commit `6cf261370e3cb98158805828e995cfe6e8b14651`;
- verify that commit's exact parent and subject
  `fix(phantoms): finalize party terminal verification`;
- require `6cf261370e3cb98158805828e995cfe6e8b14651` to be an ancestor of current HEAD;
- inspect accepted Goal 017 blobs/scope at the pinned commit;
- do not require future HEAD to be its direct child;
- do not include Goal 018 paths in historical Goal 017 scope.

Run it once before continuing.

### ACTIVE party consent

A Phantom may consent only through an exact ACTIVE `party.join` goal.

Add `goal.status() == ACTIVE` to:

- real→Phantom preparation;
- managed invitation processing;
- member/leader goal transition where the current goal authorizes the transition.

FAILED, COMPLETED or ABANDONED join/form goals cannot prepare, accept or be
revived as active membership. Add dynamic negative tests. This is a Goal 018
preflight contract, not another Goal 017 suffix.

## 5. Strict social catalog

Create:

```text
dist/game/data/phantoms/social/high-five-social-v1.xml
```

Implement a strict XXE-safe, content-addressed loader.

The XML declares:

- stable numeric codes and keys for personality traits;
- required relationship dimensions:
  `trust`, `respect`, `fear`, `anger`, `friendship`, `rivalry`, `debt`;
- required subjective reputation dimensions:
  `reliability`, `helpfulness`, `competence`, `hostility`;
- linear decay rates toward zero in units/day per dimension;
- event definitions: code, key, TTL, salience, relationship/reputation deltas
  and agreement-counter deltas;
- modifier definitions and weights;
- hard state limits: relationships <=24, memories <=24;
- memory salience threshold and deterministic eviction policy parameters.

Required event keys include at least:

```text
party.invite.accepted.outbound
party.invite.accepted.inbound
party.invite.refused.outbound
party.invite.refused.inbound
party.invite.expired.outbound
party.member.joined
party.member.left
party.member.expelled
party.leader.transferred
party.support.received
agreement.fulfilled
agreement.broken
debt.incurred
debt.repaid
```

Required modifier keys:

```text
goal.persistence
risk.tolerance
party.invite.preference
party.support.priority
conversation.warmth
conflict.escalation
```

Validate version, byte/count bounds, duplicate keys/codes, complete required
dimensions, deltas, TTL, decay, modifier sources/weights/clamps and deterministic
SHA-256. Catalog values are tuning policy, not claimed retail facts.

## 6. Persistent social model

Create `phantoms/social/**`.

Component:

```text
componentType: social.state
schemaVersion: 1
payload: <=4096 bytes
```

### Subject identity

Use a typed immutable subject reference:

```text
PHANTOM_PROFILE + positive profileId
CHARACTER_OBJECT + positive canonical character objectId
```

Do not persist Player references, names, packet/session facts or mutable Party
objects.

### State

Persist:

- catalog/authority hash and deterministic personality seed;
- sorted trait code/value pairs in `[-10000,10000]`;
- at most 24 relationship records;
- at most 24 important memory records;
- monotonic logical epoch-minute boundary;
- no redundant profile gameplay state.

A relationship record contains:

- subject reference;
- seven signed relationship dimensions;
- four signed subjective reputation dimensions;
- bounded agreement counters: offered, accepted, fulfilled, broken, refused;
- last decay/interaction minute.

`debt` is signed from the owner's perspective:

```text
positive: subject owes owner
negative: owner owes subject
```

A memory record contains:

- full uppercase SHA-256 event identity;
- catalog event code;
- subject reference;
- happened minute and expiry minute;
- bounded salience and magnitude;
- source/evidence hash if needed for exact provenance.

Use compact binary encoding. Unknown version, trailing bytes, invalid ordering,
duplicates or out-of-range values fail closed. A worst-case state at declared
limits must remain <=4096 bytes.

Relationships are asymmetric: A→B and B→A are separate subjective truth.

## 7. Deterministic personality

On first social access for an existing profile:

```text
SHA-256(catalog hash | seed | profileId | trait code)
→ deterministic bounded trait value
```

No random global state and no wall-clock input.

The same profile/catalog/seed is byte-identical after restart. Different profiles
must show deterministic diversity without class/race/name stereotypes.

Catalog hash drift must return typed `AUTHORITY_STALE` before mutation. Do not
reinterpret or auto-migrate an existing personality under changed policy.

## 8. Decay, expiry and eviction

Use integer/long arithmetic only.

For every decaying value:

```text
effectiveNow = max(requestedNow, storedMonotonicMinute)
decayedMagnitude =
    max(0, abs(value) - floor(elapsedMinutes * unitsPerDay / 1440))
restore original sign
```

Requirements:

- query frequency does not change the result;
- clock rollback cannot resurrect old emotion/memory;
- query-only decay is a read-only projection;
- mutation materializes decay exactly once before applying the event;
- values clamp to `[-10000,10000]`;
- TTL expiry is exact at the boundary;
- debt may have catalog decay 0;
- no per-tick/per-minute DB writes.

Memory eviction order:

1. expired;
2. lowest currently decayed salience;
3. oldest happened minute;
4. lexical event hash.

Relationship eviction is allowed only when all dimensions/reputation/debt are
neutral, no unresolved agreement remains and no live memory references it.
Otherwise return typed `CAPACITY_REACHED`; never silently discard an important
relationship.

## 9. Store and service ownership

Implement a store adapter over existing profile components; no schema changes.

Service lifecycle:

```text
NEW → RUNNING → STOPPING → STOPPED
                     ↘ FAILED
```

Public operations:

```text
ensurePersonality(profileId)
record(SocialEvent)
snapshot(ownerProfileId, subject, memoryLimit, now)
modifier(ownerProfileId, subject, modifierKey, now)
beginStop()
finishStop()
snapshot()
```

`SocialEvent` requires:

- owner profile;
- exact uppercase SHA-256 event ID;
- catalog event key;
- subject;
- happened epoch minute;
- bounded magnitude;
- evidence/provenance hash.

Ownership:

- use a fixed bounded array of striped locks, not a per-profile lock map;
- no lock held across party callbacks;
- optimistic conflict reload/retry maximum three attempts;
- event ID makes retry idempotent;
- one service is the only `social.state` writer;
- first-access insert collision reloads the winner;
- catalog stale/corrupt/unknown state fails closed;
- no startup scan of all profiles.

A bounded access-order cache may contain at most
`PhantomSocialCacheProfiles` profiles. Add this single config key:

```text
default 1024
range 16..10000
```

Cache is an optimization only. Eviction cannot lose a durable mutation. Reads
after optimistic conflict reload exact DB truth.

No DB write for a query after initialization. Record metrics only as fixed
aggregates; no profile/subject IDs in labels or high-frequency logs.

## 10. Structured social event sink

Create a generic no-op-capable `PhantomSocialEventSink`. The core
`PartyInvitationService` must not import social/Phantom classes.

Inject the sink into `PhantomPartyCoordinator` with backward-compatible
constructors/test seams.

Emit idempotent events only after canonical/durable party facts are known:

- accepted/refused/expired invitation for every managed perspective;
- member joined;
- member left;
- member expelled;
- leader transferred.

Rules:

- event ID derives from canonical invitation/party operation identity,
  owner profile, perspective and counterpart;
- real counterpart uses `CHARACTER_OBJECT`;
- managed counterpart uses `PHANTOM_PROFILE`;
- stale callback/retry emits no duplicate;
- rejected preparation and noncanonical mutation emit nothing;
- social persistence failure never rolls back canonical Party;
- a bounded three-attempt store retry occurs inside the social service;
- final failure is typed and counted, never hidden.

Do not emit support/death events until canonical completion evidence exists.
The catalog/API may support them for later producers.

## 11. Modifiers

Return an immutable explainable modifier snapshot:

```text
modifier key
delta basis points in [-3000,3000]
trait contributions
relationship/reputation contributions
agreement contributions
up to 8 canonical evidence keys
catalog hash
```

Modifiers are pure queries. They do not mutate goals, Party, combat or text.

Demonstrate:

- personality changes risk/persistence;
- trust/friendship/reliability raise party preference;
- fear/anger/rivalry/hostility lower or redirect social preference;
- fulfilled/broken agreements affect reliability/trust;
- debt affects cooperation in the configured direction;
- neutral/unknown subject returns deterministic neutral output.

No hardcoded Java switch over individual event or modifier keys; evaluate
catalog definitions generically.

## 12. PhantomSystem lifecycle

In production mode:

```text
profile repository
→ social catalog/service start
→ party coordinator receives social sink
→ decision/scheduler start
```

Party remains the canonical fact owner. Social memory is an observer.

Shutdown ordering:

```text
party closes admission and drains terminal callbacks
→ social beginStop
→ social finishStop with zero operations/writes
→ remaining existing shutdown
```

Startup failure follows reverse ownership. Disabled Phantom World does not load
the social XML, create the service or access social DB.

Expose bounded social snapshot in `PhantomSystem.Snapshot` and configured
shutdown evidence.

## 13. Exact scope

Allowed production/data/config:

```text
java/org/l2jmobius/gameserver/phantoms/social/**
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
dist/game/config/Custom/PhantomPlayers.ini
dist/game/data/phantoms/social/high-five-social-v1.xml
```

`PhantomPartyDecision.java` is allowed only for ACTIVE consent or optional
read-only modifier evidence; explicit goals remain authoritative.

Allowed tests/build/tools/docs:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomSocial*.java
targeted adaptations to Party/System/Skeleton suites
PhantomTestLauncher.java
tools/phantoms/verify-task-017.ps1
tools/phantoms/verify-task-018.ps1
roadmap/master-plan status only
Goal 017 final review
Goal 018 architecture/report/review/task docs
```

Forbidden:

- schema/migrations and repository SQL changes;
- Player.java, Party.java, packet handlers or PartyInvitationService;
- combat, navigation, background, population, commerce, progression semantics;
- text, Semantic Pack, chat, LLM, personality phrasing;
- PvP/PK, clans, economy, matchmaking;
- other chronicles/geodata;
- Goal 019/020/021/025.

## 14. Mandatory tests

Focused modes:

```text
social-catalog
social-codec
social-personality
social-decay
social-events
social-modifiers
social-party-integration
social-lifecycle-performance
```

Evidence:

- XML/hash determinism and invalid controls;
- trait determinism/diversity/bounds;
- worst-case codec <=4096, roundtrip, corruption/trailing/unknown-version;
- exact decay at 0, 1 minute, day, expiry, large elapsed and clock rollback;
- query-frequency independence and query-only DB writes 0;
- idempotent duplicate/out-of-order/concurrent events;
- optimistic conflict reload and insert race;
- relationship/memory limits and deterministic eviction;
- important relationship capacity fail-closed;
- asymmetric A→B/B→A;
- agreement/debt/repayment and fulfilled/broken history;
- catalog drift fail-closed;
- all modifier keys, clamp and evidence;
- inactive FAILED/COMPLETED/ABANDONED party.join cannot prepare/accept/transition;
- accepted/refused/expired Party events write exact managed perspectives once;
- real counterpart is persisted by character object ID;
- canonical leave/expel/transfer event ingestion;
- restart reload byte-identical and no duplicate on terminal retry;
- social failure does not roll back canonical Party;
- disabled mode inert;
- shutdown with blocked social write/party callback;
- 100,000 pure decay/modifier evaluations with DB writes 0;
- 10,000 synthetic states and cache bound;
- zero new worker/thread/Future/task.

Use real test DB for component and party integration. Fakes are allowed only for
clock, conflict, failure and pure-model tests.

## 15. Verification discipline

Development:

1. compile affected/tests;
2. run eight Goal 018 modes;
3. run exact affected Party consent/lifecycle, profile component, system/config;
4. verifier 017 and working verifier 018;
5. one final `phantom-social-test` aggregate.

Do not run broad historical affected aggregates.

After all focused/static gates are green, freeze production/data/test/build/
verifier files:

```text
one final full ant verify
one standalone ant jar
report terminal results only
ordinary commit/push
two post-commit byte-identical verifier 018 runs
```

A second full verify is allowed only after a real relevant code/test/build/
verifier fix. An unrelated preflight-green flake receives one exact targeted
retry and no broad rerun. Third full verify is forbidden.

Verifier 018 checks graph/scope, accepted 017 ancestry, config/data hashes,
component bounds, no per-profile workers, no query writes, ACTIVE consent,
party-event sink direction, lifecycle, tests, UTF-8 and JAR contents. It must be
descendant-compatible after acceptance.

Create:

```text
docs/phantoms/architecture/SOCIAL_MEMORY_RELATIONSHIP_CONTRACT.md
docs/phantoms/reports/018-social-memory-relationships.md
```

Print `GOAL_018_SOCIAL_MEMORY_RELATIONSHIPS_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. On blocker, remove unsafe
production changes, preserve safe audit/tests/docs, ordinary commit/push and
return an honest status.
