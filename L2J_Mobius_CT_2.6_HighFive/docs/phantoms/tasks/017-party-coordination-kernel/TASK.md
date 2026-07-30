# Goal 017 — Party coordination kernel, semantic acts and shared routes

## Contract

```text
branch: feature/phantom-world
required parent: 57caea2e5b5597c9a06b87cb8e868f227c4aa88e
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 17001701
subject: feat(phantoms): add party coordination kernel
success token: GOAL_017_PARTY_COORDINATION_KERNEL_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Create one ordinary child and push to `origin/feature/phantom-world`. No amend/rebase/squash/
merge/force push. Commit an honest SUCCESS/BLOCKED/FAILED result.

This is one coherent capability. Do not pre-split it into 017A/017B.

## Efficiency-standard exception

This high-risk canonical multi-actor lifecycle package intentionally exceeds the
ordinary package line guideline. The extra text is a closed architecture,
current-code map and failure matrix supplied to reduce Codex discovery,
repository rereads and suffix risk. It does not authorize a broader READ_SET,
extra full verifies or scope expansion.

Normative files:

```text
016_REVIEW_HANDOFF.md
CURRENT_CODE_MAP.md
PARTY_ARCHITECTURE.md
FAILURE_MATRIX.md
TEST_CASES.md
ACCEPTANCE.md
```

## User-visible result

A materialized Phantom with an explicit party goal can:

- invite/refuse/accept another Phantom or real player through canonical Party
  rules;
- form, join, leave, expel and recover a real server `Party`;
- persist only coordination intent and rebuild Phantom-only groups after restart;
- evaluate several capabilities per member and report actual vacancies;
- assign leader/member responsibilities without fixed class roles;
- communicate decisions as typed semantic acts;
- follow one shared leader route with regrouping;
- assist, protect, heal, recharge, resurrect or issue one explicit party support
  action through exact capability/action ownership;
- degrade safely when members disappear, decline, disconnect, dematerialize,
  become stale or cause backpressure.

No natural-language generation is required.

## Progress and documentation

Create:

```text
docs/phantoms/reviews/016-population-manager-safety-review.md
```

Record Goal 016 as `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS` using the handoff.

Align only master-plan sections 017, 021, 022 and 023 with the accepted roadmap:

```text
017 party coordination
021 acquisition chains
022 economy transaction kernel
023 Rift/advanced recruitment
```

Do not redesign or renumber later Goals.

Before any Goal 017 code, run historical verifier 016 on the descendant working
tree and fix only descendant-compatibility if actually required.

## Exact read-first audit

Initial bounded symbol/range reads:

1. this package and efficiency standard;
2. roadmap Goal 017/023 and master-plan 017/021–023 only;
3. `RequestJoinParty`, `RequestAnswerJoinParty`;
4. `Party` invite/add/remove/disband/leader methods;
5. `Player` request ownership and join/leave methods;
6. materialization ActionLease and identity lifecycle;
7. progression capability evaluation/model/catalog query;
8. combat service/backend/actor lease ownership;
9. navigation service/route/progress tracker;
10. topology query anchor/hash APIs;
11. goal store, decision engine and step context;
12. scheduler control port/composition and PhantomSystem wiring;
13. profile component repository;
14. Goal 016 population-created Player integration fixture;
15. DR-04 normalized party capability matrix.

Maximum ten additional exact symbol reads, each reported with reason. No whole
Player/Party file reread, no old task packages and no other chronicle.

Before implementation write an internal all-writer/owner table for:

```text
Player request fields and Party.pendingInvitation
Party membership/leader/disband
party.state leader/member components
current persisted goals
materialization and real login
combat/external action ownership
navigation route/progress ownership
scheduler signals/control pulse
```

## Production requirements

Implement `PARTY_ARCHITECTURE.md` exactly.

Expected responsibility-equivalent packages/types:

```text
model/groups/PartyInvitationService and generic managed-delivery port
phantoms/semantic/PhantomSemanticAct
phantoms/party/model, store, backend, service/coordinator
role/vacancy matcher
route coordinator
decision candidates/handlers
metrics/snapshot
```

Names may differ locally; responsibilities may not be collapsed into one
monolithic class.

Production defaults:

```text
PhantomPartyOperationsPerPulse = 64
```

Missing legacy config defaults to 64. Disabled Phantom World creates no party
service/catalog/component read/control work.

## Allowed scope

Production:

```text
java/org/l2jmobius/gameserver/phantoms/party/**
java/org/l2jmobius/gameserver/phantoms/semantic/**
java/org/l2jmobius/gameserver/model/groups/PartyInvitation*.java
java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java
java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerControlPort.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java
java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatMetrics.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatStepHandlers.java
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
dist/game/config/Custom/PhantomPlayers.ini
dist/game/data/phantoms/party/high-five-party-roles-v1.xml
```

Minimal `Party.java` change is allowed only for a generic observer/service seam
that cannot be achieved through existing public methods. `Player.java` is
forbidden.

Read-only dependencies, no production semantic changes:

```text
progression/**
navigation/**
topology/**
population/**
background/**
commerce/**
Game Knowledge
```

Tests/build/tools/docs:

```text
build.xml
PhantomParty*.java tests
targeted compile adaptations to existing Party/combat/system tests
PhantomTestLauncher.java
tools/phantoms/verify-task-017.ps1
historical verifier 016 only if descendant compatibility is necessary
Goal 016 review, Goal 017 contract/report/task docs
roadmap/master-plan alignment described above
```

Hard forbidden:

- schema migrations;
- fake `GameClient` or packet-handler invocation;
- direct Party member-list mutation;
- global party matchmaking/World scan;
- Party Match Room, Command Channel, Rift implementation;
- background party rewards/travel/farming;
- text generation, Semantic Pack parsing or runtime LLM;
- personality/reputation/clans;
- PvP/PK;
- direct trade/economy;
- per-party/profile thread, executor, timer or Future;
- other chronicles/geodata;
- Goal 018/019/020/023/025 implementation.

## Lifecycle and performance

Party service starts after its dependencies and before scheduler start. Compose
population and party control into one installed scheduler control chain.

`beginStop` closes admission. `finishStop` is false while any group operation,
persistence claim, actor ActionLease, invite request, navigation attempt or
external combat action remains.

All ordinary queries use indexes; no XML/DB/World scan in a pulse or decision
step. Component paging is <=256. Group/roster algorithms are <=9.

Fixed aggregate metrics only; profile/group/member IDs are not metric labels.

## Verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-017.ps1
```

It must verify:

- exact graph/branch/subject/scope and High Five only;
- Goal 016 accepted review and descendant-compatible verifier;
- canonical packet handlers delegate to one shared invitation service;
- core service has no Phantom implementation dependency;
- no packet-handler invocation/fake client/direct member-list mutation;
- state/operation phases, roster <=9 and payload <=4096;
- goal consent and no unrelated-goal overwrite;
- no class-specific branching/fixed role;
- strict party role XML and hashes;
- semantic acts are string-keyed and text-free;
- one shared route, no snap/background party travel;
- support action uses combat external ownership;
- one scheduler task/control chain and bounded pulse;
- lifecycle counters, tests, UTF-8 and JAR contents;
- verifier is descendant-compatible for future Goals.

## Completion

Create:

```text
docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md
docs/phantoms/reports/017-party-coordination-kernel.md
```

Report <=240 lines, including:

```text
exact current-code audit
invitation and persistence state machines
role/vacancy evidence
semantic acts
route/action ownership
real Phantom/real-player integration
restart/failure matrices
all test/full-verify runs
READ_SET expansion and usage
limitations deferred to 018/019/020/023/025
```

Do not self-accept.

If shared canonical invitation extraction or external action ownership cannot be
implemented without `Player.java`, a schema migration or unsafe Party rewrite,
remove unsafe production code, preserve audit/tests/docs and return honest
BLOCKED.

Print `GOAL_017_PARTY_COORDINATION_KERNEL_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
