# Goal 038 — Humanized Russian semantic pack

Status: SUCCESS

## Source-fact map (before semantic implementation)

| Required fact | Exact source | Proven local behavior | Goal038 boundary |
|---|---|---|---|
| Functional-understanding priority | `PhantomConversationService.processBatch` / `plan`; `PhantomSemanticUnderstandingService.understand` | Existing Goal018 semantic returns `ACCEPTED`, `CLARIFICATION_REQUIRED`, or `REJECTED`; accepted/clarification paths are resolved before the current unsupported response is planned. | Humanized understanding may run only after an existing `REJECTED`. It cannot replace accepted intent, clarification, proposal, authorization, execution, or query behavior. |
| Human/client origin | `ChatObservationService.Origin.CLIENT_CHAT`; `openClientDispatch`; `PhantomConversationService.onDelivered` | Current managed conversation ingress accepts only `CLIENT_CHAT`, supported channels, and a Phantom recipient. | Client behavior remains the functional-first entry. |
| Phantom-generated origin | `ChatObservationService.Origin.PHANTOM_GENERATED`; `openGeneratedDispatch`; `L2jPhantomConversationExecutionPort.dispatch` | Outbound text is dispatched through the current chat handler inside a generated dispatch scope; current conversation ingress intentionally rejects all generated deliveries. | Goal038 must introduce a distinct social-generated provenance before admitting any Phantom message. Generic/action/system-generated text remains ineligible. |
| Final response emission | `PhantomConversationExecutionService` → `L2jPhantomConversationExecutionPort.dispatch` → registered `IChatHandler.onChat` | A persisted `ConversationResponsePlan` becomes an execution entry, is leased to the exact materialized owner/counterpart, and is emitted by the existing server chat path. | Humanized replies continue through this durable handoff; no direct chat-send side path is added. |
| Social relationship/memory authority | `PhantomSocialService`, `PhantomSocialStore`, `PhantomSocialModel.SocialSnapshot`, component `social.state` | Social owns personality traits, relationships, memories, decay, typed events, bounded cache, and optimistic persistence. | Relationship bands and persona tone are projections of `PhantomSocialService`; Goal038 adds no parallel relationship ledger or duplicate social schema. |
| Generic component sufficiency | `PhantomProfileComponent`; `PhantomProfileRepository.findComponent/insertComponent/updateComponent`; existing `conversation.state` and `social.state` stores | A typed component has schema version, opaque payload ≤4096 bytes, row version, insert/find/CAS update, and atomic multi-component support. | One bounded `conversation.personal` schema v1 is sufficient for allowlisted personal facts and anti-repeat state; no SQL migration and no raw chat log. |
| Optimistic row-version behavior | `PhantomProfileRepository.updateComponent` and `requireOptimisticWinner`; existing store retry patterns | Update succeeds only for the expected row version; zero winners are a concurrent modification; local services retry a bounded maximum of three times. | Personal-memory writes use the same insert-or-CAS contract, at most three attempts, deterministic merge/eviction, and observation-hash idempotency. |
| Startup ordering | `PhantomSystem.start` | Social starts before Semantic; Semantic starts before Conversation composition; Conversation Execution starts before Conversation ingress. | Humanized catalog/personal service are composed after Social+Semantic authorities and before Conversation starts. |
| Shutdown ordering | `PhantomSystem.shutdown` and startup-failure cleanup | Conversation ingress stops and drains before Conversation Execution; Social is stopped only after conversation/execution and Party dependents. | Humanized service owns no worker and is closed with Conversation, before Social/profile repository teardown. |
| Existing cache/pulse owner | `PhantomConversationService.onPulse/runPulse`; `PhantomConversationExecutionService.onPulse`; current pulse-source registration | Conversation already owns bounded ingress, batch state, per-pulse operations, persistence, and execution signalling; Execution owns outbound recovery/dispatch on the shared pulse source. | Humanized per-message work is caller-driven within the existing Conversation batch/pulse. No executor, timer, future, sleep loop, or unbounded queue/map is introduced. |

## Scope guard

- Baseline: `b7420deb245c87de4ba71631782caea0c0d5e997` on `feature/phantom-world`.
- Goal037 is the accepted predecessor. Goal039 is not started.
- Production database `l2jmobiush5` and `prepare-phantom-test-db` are forbidden.
- User-owned tracked modifications and untracked task packages are out of scope and remain untouched.
- Intended artifact family: humanized conversation runtime/config/data, exact Conversation/Social/Profile integration seams, focused tests/build targets, and required Phantom documentation.

## Evidence ledger

### Versioned core/custom catalogs

Core files:

- `dist/game/data/phantoms/semantic/humanized/high-five-ru-humanized-semantic-v1.xml`;
- `dist/game/data/phantoms/semantic/humanized/high-five-ru-humanized-corpus-v1.tsv`;
- `dist/game/data/phantoms/conversation/humanized/high-five-ru-humanized-conversation-v1.xml`;
- `dist/game/data/phantoms/conversation/humanized/high-five-ru-persona-v1.xml`.

Directly editable custom files, loaded in this exact order:

- `dist/game/data/phantoms/semantic/custom/my-ru-aliases.xml`;
- `dist/game/data/phantoms/semantic/custom/my-slang.xml`;
- `dist/game/data/phantoms/semantic/custom/my-social-topics.xml`;
- `dist/game/data/phantoms/conversation/custom/my-phrases.xml`;
- `dist/game/data/phantoms/conversation/custom/my-profanity.xml`;
- `dist/game/data/phantoms/conversation/custom/my-mature-dialogue.xml`.

Fresh catalog evidence:

- core SHA-256: `e5a6b54bcdcd8aa47de8faa486b2f3975817d072f7128d31f4a3cb13fd2c46c2`;
- custom SHA-256: `46b7392651de1c75dd81cfe18f3070b1a7d0435ed39fd311ea8d825d9edcf785`;
- combined SHA-256: `e6ab3604f1de22cb4f1fb112c82a0a61261af836ab14e78bfd864e5befab5ef5`;
- topics `25`, acts `22`, patterns `60`, templates `67`, aliases `8`, profanity entries `4`, mature templates `1`;
- corpus `109/109`; focused catalog suite `5/5`.

Custom edits require no Java recompilation. A temporary direct slang alias and an explicit phrase override changed only custom/combined hashes and affected understanding/rendering. Four strict negative controls passed: collision without `override=true`, XXE/DOCTYPE, unknown attribute, and mixed social/game-action input. An invalid configuration enum also disabled the system fail closed. Custom data cannot define or dispatch gameplay actions.

### Runtime authority and bounded state

`PhantomConversationService` preserves Goal018 semantic priority: existing `ACCEPTED` and `CLARIFICATION_REQUIRED` paths finish before Humanized planning. Humanized replies use durable `social.reply` response plans and never carry a proposal. `PhantomSocialService` remains the personality, relationship, memory-event and decay authority; Goal038 emits typed supportive/conflict/apology/reconciled events through it and adds no relationship ledger.

Personal facts use existing generic profile-component persistence:

- component `conversation.personal`, schema `1`, payload at most `4096` bytes;
- at most `16` subjects, `48` facts globally, `12` facts per subject and `8` recent response hashes per subject;
- fact values at most `64` code points and `192` UTF-8 bytes;
- allowlisted structured kinds only, no raw message history or credentials;
- deterministic salience/age eviction, exact TTL, insert-or-CAS update and at most three optimistic attempts.

The worst focused structured state serialized to `3515` bytes. After `4096` synthetic social messages it retained `16` subjects and serialized to `1359` bytes. The Humanized runtime owns no thread, executor, timer, future or unbounded queue.

Relationship bands are derived only from the existing Social relationship map: all-zero is `UNKNOWN`; negative total at least `3500` is `HOSTILE`; rivalry at least `1200` and greater than anger is `RIVAL`; remaining negative total at least `1200` is `TENSE`; positive total at least `4500` is `TRUSTED`; positive total at least `1200` is `FAMILIAR`; otherwise `NEUTRAL`. Tests proved `TRUSTED` teasing, `RIVAL` sarcasm, rejection of hostile teasing and unknown sarcasm, restart-stable persona `calm.travel`, and at least three distinct humor variants.

### Conversation, profanity and mature gates

The 109-case corpus and focused behavior suite cover greetings, acquaintance, mood, small talk, rest, food, music, films, games, hobbies, plans, likes/dislikes, achievements/failures, humor, teasing, sarcasm, surprise, disagreement, irritation, apology, reconciliation, relationship/light flirt and personal/game pivots. Personal → functional `item.acquire.query` → personal recall retained `старый рок`, proving functional priority and continuity without fabricated open-domain facts.

`NONE` emitted no profanity. `MILD` stayed at mild intensity. `CONTEXTUAL` activated only for eligible positive/negative/anger/amused/surprise acts with relationship/persona/intensity gates, stayed silent for neutral small talk and selected a different output when the prior response hash was present. Mature content remained unreachable for all `512` selectors while disabled, became reachable only after explicit opt-in in a trusted private/WHISPER context, and shipped `EnablePhantomMatureConversation=False`.

Shipped configuration:

- `EnablePhantomSystem=False`, population/ACTIVE `0/0` remain unchanged;
- `EnablePhantomHumanizedConversation=True`;
- `EnablePhantomCustomConversationPack=True`;
- `PhantomConversationRegister=CASUAL`;
- `PhantomConversationProfanity=CONTEXTUAL`;
- `PhantomConversationVariation=HIGH`;
- `EnablePhantomMatureConversation=False`.

### Phantom-to-Phantom loop proof

Generated social egress has the distinct `PHANTOM_SOCIAL` origin and an exact expected-counterpart delivery proof. Generic `PHANTOM_GENERATED` output remains rejected, and self-response is suppressed. In the deterministic A → B → A chain, exactly two generated replies were eligible; the third was suppressed by a per-side generated-turn budget of `1`, a `5` minute cooldown and response anti-repeat state. The existing chat election/recipient authority and durable execution handoff remain unchanged, so the client ingress was not broadly opened and no ping-pong loop exists.

### Guarded validation and cleanup

- focused Goal038: `12/12` (`5/5` catalog, `6/6` behavior, `1/1` persistence);
- directly affected Goal018–020/Profile/ChatObservation/lifecycle regressions: `133/133` across the exact affected aggregate;
- guarded production composition/restart: `1/1`, three durable response plans, catalog hash stable across restart, personal memory recalled after the functional game turn;
- persistence and composition fixtures both reported `cleanupRows=0`;
- guarded database: `l2jmobiush5_phantom_test`, schema aggregate SHA-256 `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`;
- production `l2jmobiush5` was not used and `prepare-phantom-test-db` was not executed;
- one fresh `ant verify`: PASS in `32 minutes 4 seconds`; report aggregate `1353` successful checks plus the two expected negative-control failures, final exit `0`;
- one final standalone `ant -q jar`: PASS in `22 seconds`.

Text/data gates: strict UTF-8, XML/XXE fail-closed behavior, stable ordering and control-character checks passed. Mojibake markers in changed files were checked separately and absent. Escaped Cyrillic/XML escaped Cyrillic in changed files was checked separately and absent. `git diff --check` and exact staged-scope verification passed before commit.

Goal039 is the next planned final gate and remains `NOT_STARTED`.
