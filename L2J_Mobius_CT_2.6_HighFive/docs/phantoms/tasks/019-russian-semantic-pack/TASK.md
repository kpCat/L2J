# Goal 019 — Russian Semantic Pack, deterministic understanding and entity grounding

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: d30b657a9351d8cb099548e959854bf826b7d1d1
test DB: not required for parser; any DB test uses only l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 19001901
subject: feat(phantoms): add russian semantic understanding
success token: GOAL_019_RUSSIAN_SEMANTIC_UNDERSTANDING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Create one ordinary child and push to `origin/feature/phantom-world`. No amend/rebase/squash/
merge/force push. Record:

```text
Goal 017: ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS
Goal 018: ACCEPT_WITH_ACTIVATION_GATE
Goal 019: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 020/021/025: NOT_STARTED
```

Create `docs/phantoms/reviews/018-social-memory-review.md`.

Goal 018 activation gate is documented, not implemented here:

- durable idempotency is currently bounded by retained important memories;
- out-of-order/expired event causality requires hardening;
- `party.member.joined` emission must be restricted to first canonical membership
  transition before Goal 020+ consumes social modifiers.

Goal 019 must not consume social modifiers or mutate social state.

## 2. Efficiency contract

Do not reread old task packages, all reports, `Player.java`, `Party.java`, packet
handlers, social implementation or unrelated subsystems.

Initial READ_SET:

1. this package;
2. Goal 019 sections of roadmap/master plan;
3. `PhantomDomainRef`, `PhantomDecisionKey`, `PhantomSemanticAct`;
4. `PhantomGameKnowledgeService`, query, immutable facts and source hashes;
5. topology identifiers/query only;
6. one strict current Phantom XML loader pattern;
7. `PhantomSystem` knowledge construction/snapshot/shutdown ranges;
8. launcher/build/verifier 018 patterns.

At most six additional exact files/symbols, each recorded with one sentence. No
broad repository search after the audit.

```text
new production files <=10
changed production/data/config files <=13
changed total files <=28
no schema migration
no Player/Party/packet/social/combat/navigation/background/population mutation
no worker/thread/executor/Future/scheduled task
report <=190 lines
soft usage target <=500000 tokens
```

If safe implementation exceeds the limits, publish a bounded honest blocker; do
not create 019A/019B.

## 3. Product result

```text
Russian/Latin input
→ strict normalization/tokenization
→ intent candidates
→ typed slots
→ context-safe entity candidates
→ exact authoritative grounding
→ confidence/ambiguity decision
→ ACCEPTED | CLARIFICATION_REQUIRED | REJECTED
```

No action, goal, Party, social, inventory, Player or world mutation. Goal 020 owns
dialogue policy and action dispatch.

## 4. Strict data package

Create:

```text
dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml
dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv
```

The XML declares locale/version, normalization policy, bounded filler words,
transliteration mappings, typo policy, intent definitions, typed slots, phrase
patterns, entity aliases, role/capability aliases, structured clarification reason
keys and all hard count/byte/token/candidate limits.

Required intents:

```text
party.invite
party.accept
party.refuse
party.leave
party.role.query
party.travel
party.support.request
party.assist.request
party.regroup.request
entity.locate
item.acquire.query
item.source.query
content.requirements.query
unknown
```

Required slots:

```text
TARGET_PLAYER PARTY_ROLE CAPABILITY ITEM NPC CONTENT TOPOLOGY_NODE LOCATION
QUANTITY RESPONSE
```

Entity aliases may target item/NPC/content/topology/class/capability/party-role
identities only after current authority validation. Do not copy server entity
corpora. Russian, common Latin transliteration and Lineage 2 abbreviations/slang
are explicit data; Java contains no word/intent/entity phrase switch.

## 5. Normalization

1. reject null, malformed surrogate, unsupported control and oversized input;
2. Unicode NFKC;
3. lowercase `Locale.ROOT`;
4. `ё → е`;
5. normalize dashes/quotes and collapse whitespace;
6. tokenize letters/digits plus bounded punctuation;
7. preserve original code-point spans;
8. reject unsupported mixed-script confusables;
9. apply only explicit data-driven abbreviation/transliteration aliases;
10. no locale-default behavior or unsafe stemming.

Limits: input <=512 code points and <=2048 UTF-8 bytes; tokens <=64.
Normalization is idempotent and linear.

## 6. Typed immutable model

Create immutable:

```text
NormalizedText Token InputContext EntityCandidate SlotValue IntentCandidate
UnderstandingResult UnderstandingEvidence
```

Status:

```text
ACCEPTED CLARIFICATION_REQUIRED REJECTED
```

Result contains normalized hash, pack/knowledge/topology hashes, selected intent,
confidence 0..10000, bounded typed slots, up to four alternatives, structured
reason, up to 16 evidence entries and no generated sentence/mutable server object.
`PhantomDomainRef` is produced only after authoritative validation.

## 7. Context boundary

`InputContext` is an immutable supplied snapshot with optional speaker identity,
channel, canonical party leader/members, nearby/recent player refs, selected
target, current location/topology ref and previous accepted intent/slots.

```text
party members <=9
nearby/recent refs <=32
previous slots <=16
```

The parser never reads `World`, Player, Party, packets or DB. Player names and
pronouns resolve only from exact context. Ambiguous names/roles/pronouns require
clarification. Real refs remain `character.object`; managed refs may use `profile`.

## 8. Matching and scoring

Priority:

```text
exact normalized phrase/pattern
→ exact alias/token sequence
→ explicit transliteration alias
→ bounded fuzzy token candidate
```

Fuzzy rules:

- deterministic Damerau-Levenshtein or equivalent;
- disabled below four code points;
- distance 1 for length 4..7, distance 2 for length >=8;
- pre-indexed same category only;
- hard candidate bounds, total <=128;
- exact match cannot be overridden by fuzzy;
- near-ties inside ambiguity margin clarify.

Integer-only generic scoring covers pattern evidence, slot completeness,
exact/translit/fuzzy quality, context and ambiguity penalties. No intent-specific
Java behavior beyond generic required-slot validation.

## 9. Grounding

Implement a narrow read-only grounding port pinned to current immutable source
hashes:

- item via `findItem`;
- NPC via `findNpc`;
- content via `content`;
- topology via exact snapshot node IDs;
- capability via authoritative capability keys;
- party role via accepted role catalog;
- class only if an exact authoritative class-ID set is available without scope
  expansion; otherwise omit class aliases.

Unknown/stale authority makes service construction fail closed. Language aliases
are not world truth and cannot invent IDs.

## 10. Ambiguity safety

Return `CLARIFICATION_REQUIRED` for missing required slot, multiple contextual
players, tied entities/intents, fuzzy tie, unresolved pronoun or alias whose target
fails authority validation. Structured reasons include:

```text
clarify.intent clarify.target_player clarify.entity clarify.party_role
clarify.location clarify.quantity reject.unsupported reject.too_long
reject.mixed_script
```

Never guess an actionable target and do not generate final prose.

## 11. Corpus

Strict UTF-8 TSV columns:

```text
case_id input context_fixture expected_status expected_intent expected_slots
minimum_confidence reason_key
```

Minimum:

```text
>=240 total
>=120 Cyrillic
>=40 transliteration
>=30 abbreviations/slang
>=30 typo/fuzzy
>=40 ambiguity/clarification
>=40 negative/unsupported
```

Cases may overlap. Cover party lifecycle, roles, support/assist/regroup,
location/entity/source questions, quantities, pronouns/duplicate names,
mixed-script/oversized controls and curated current entities. No fabricated IDs.

## 12. Service/lifecycle/performance

Implement `PhantomSemanticUnderstandingService`:

```text
NEW → RUNNING → STOPPING → STOPPED
                     ↘ FAILED
```

API:

```text
understand(String input, InputContext context)
snapshot()
beginStop()
finishStop()
```

All data/indexes load before publication. Parsing has no DB writes or mutable
runtime cache. Operation claims protect shutdown. Disabled Phantom World reads no
semantic files. Metrics are fixed aggregates with no per-parse logs/IDs.

Performance: 100000 mixed corpus parses; bounded allocations/tokens/patterns/
candidates; no workers/tasks/Futures.

## 13. PhantomSystem integration

Ordering:

```text
Game Knowledge + topology + capability/party-role authority
→ semantic service start
→ no chat/action hookup
```

Shutdown semantic before knowledge/topology destruction. Expose pack/corpus and
source hashes plus bounded metrics in `PhantomSystem.Snapshot`. Register no
candidate or step handler.

## 14. Scope

Allowed production/data:

```text
java/org/l2jmobius/gameserver/phantoms/semantic/understanding/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml
dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv
```

One targeted read-only accessor in knowledge/topology/party-role authority is
allowed only to enumerate already-authoritative IDs/keys.

Allowed test/build/tools/docs:

```text
build.xml
PhantomSemantic*.java tests
exact System/disabled adaptations
PhantomTestLauncher.java
verify-task-018.ps1
verify-task-019.ps1
status-only roadmap/master-plan
Goal 018 review and Goal 019 task/architecture/report docs
```

Forbidden: social production changes/consumption, Player/Party/packets/chat,
schema/DB writes, decisions/actions, gameplay subsystem mutation, generated text,
remote/runtime LLM, other chronicles/geodata, Goal 020/021/025.

## 15. Tests

Focused modes:

```text
semantic-pack
semantic-normalization
semantic-intents
semantic-grounding
semantic-context
semantic-corpus
semantic-lifecycle-performance
```

Mandatory evidence: strict hash/bounds/invalid controls; authority validation;
normalization idempotency; precedence/edit boundaries; required slots/ambiguity;
identity preservation; no mutable subsystem access; corpus deterministic in
forward/reverse/shuffled order; positive intent accuracy >=96%; slot precision
>=99%; clarification/negative safety 100%; source drift fail-closed; disabled and
shutdown claims; 100000 parses; zero DB writes/workers.

## 16. Verification

1. compile affected/tests;
2. seven focused modes;
3. exact Game Knowledge/topology/Party-role/System disabled regressions;
4. verifier 018 and working verifier 019;
5. final `phantom-semantic-test` aggregate.

Then freeze production/data/test/build/verifier files and run one final full
`ant verify`, one standalone `ant jar`, ordinary commit/push and two byte-identical
post-commit verifier 019 runs. Second full only after an actual relevant fix;
unrelated preflight-green flake gets one exact retry; no third full.

Create:

```text
docs/phantoms/architecture/RUSSIAN_SEMANTIC_UNDERSTANDING_CONTRACT.md
docs/phantoms/reports/019-russian-semantic-understanding.md
```

Print `GOAL_019_RUSSIAN_SEMANTIC_UNDERSTANDING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
