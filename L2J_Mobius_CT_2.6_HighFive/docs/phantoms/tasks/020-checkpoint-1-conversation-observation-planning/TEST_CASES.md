# Test matrix — Goal 020 Checkpoint 1

## A. Social activation

1. Memory eviction then exact event replay remains IDEMPOTENT.
2. Memory TTL expiry then replay is STALE/IDEMPOTENT and no delta changes.
3. Receipt expiry after event expiry cannot reapply a delta.
4. Late live event receives exactly aged relationship/reputation/salience.
5. Expired event writes STALE receipt only.
6. 96 receipts fit <=4096; 97th live receipt returns CAPACITY_REACHED.
7. Atomic failure after either component mutation rolls back both.
8. Concurrent same event produces one APPLIED receipt/delta.
9. Component conflict reloads; fourth conflict fails typed.
10. Corrupt/unknown/trailing receipt payload fails closed.
11. Role/manifest/restart reconciliation does not repeat member.joined.
12. First CANONICAL_OBSERVED→COMMITTED JOIN emits exactly once.

Use the real test DB for the two-component transaction and restart tests.

## B. Semantic activation

1. Reject profile UUID, zero, negative and overflow keys.
2. Reject invalid namespace for every slot type.
3. Reject duplicate slot types in result and previous context.
4. Reject slot-first, adjacent-slot and repeated-slot patterns.
5. Candidate exhaustion returns clarify.complexity.
6. No partially explored winner is accepted.
7. Fragment resolution completes target/item/role/location/quantity.
8. Fragment ambiguity remains clarification.
9. Full new accepted intent replaces pending clarification.
10. Loader blocked during beginStop cannot publish after STOPPED.
11. Real production topology/knowledge/role authority loads every current alias
    category and pins exact hashes.
12. Missing production alias target fails publication.

## C. Generic chat observation

1. No registration: ordinary Say2/CreatureSay behavior unchanged.
2. Final filtered text, not raw input, reaches observation.
3. One dispatch creates the same dispatch ID for multiple CreatureSay packets.
4. Actual recipient object/name are exact.
5. NPC/system CreatureSay has no observation.
6. Unsupported channel has no Phantom plan.
7. Delivery exception/backpressure does not change packet/chat behavior.
8. Registration close blocks later callbacks.
9. No Player reference remains in queued observation.
10. Generated origin is ignored.

Where practical, use current chat handlers and headless players. Static source
assertions alone do not count as delivery evidence.

## D. Conversation catalog/codec

1. Strict XML/TSV hashes and all required keys.
2. XXE/unknown/duplicate/invalid bounds fail closed.
3. Corpus has >=128 cases and declared category coverage.
4. Worst-case conversation.state <=4096 and roundtrips.
5. Corrupt/truncated/trailing/duplicate session/slot/hash fails closed.
6. Authority drift prevents state reuse.
7. Deterministic session eviction and recent-observation bounds.

## E. Observer election

1. WHISPER elects exact managed recipient.
2. PARTY elects managed canonical leader among actual recipients.
3. PARTY without managed leader elects smallest profile ID.
4. GENERAL exact unique leading/trailing name elects that observer.
5. TRADE exact unique name elects that observer.
6. No name, fuzzy name or duplicate same display name yields no response.
7. Self/managed-speaker observation yields no response.
8. More than 32 managed observers yields no response.
9. One dispatch publishes at most one plan.

## F. Understanding and clarification

1. Direct accepted intent produces one proposal and acknowledgement plan.
2. Rejected input produces no proposal.
3. Ambiguous intent produces clarification plan.
4. "пригласи" then "Ивана" completes exact target.
5. Unrelated new intent replaces pending clarification.
6. Pending clarification expires by minute/turn bound.
7. Authority drift clears/rejects stale pending flow.
8. Duplicate dispatch is idempotent.
9. Restart reloads previous accepted intent and pending clarification exactly.

## G. Social style and rendering

1. Neutral state selects neutral template deterministically.
2. Warmth positive selects allowed warm variant.
3. Anger/conflict selects allowed cold/terse variant.
4. Social modifier cannot turn REJECTED into ACCEPTED.
5. Social modifier cannot change target/proposal slots.
6. Same inputs across restart render byte-identical text.
7. Control characters, byte 8 and unsupported markup cannot enter rendered text.
8. Rendered output stays <=100 code points and <=400 UTF-8 bytes.
9. No template text is hardcoded in Java.

## H. Real integration

Use test DB and materialized Players:

1. create/materialize at least two managed profiles and one real test client;
2. install the generic delivery and conversation service;
3. deliver filtered WHISPER, PARTY, GENERAL and TRADE messages through current
   Say2/CreatureSay path or the nearest legitimate packet/handler fixture;
4. prove only actual managed recipients enter batches;
5. prove one elected plan per dispatch;
6. persist/reload conversation.state;
7. record social style evidence;
8. capture proposals but execute none;
9. prove no outbound CreatureSay/server packet was created by conversation code;
10. stop and prove zero observation/conversation/social/semantic claims and no
    registration residue.

## I. Performance

1. 100,000 generic non-managed deliveries: no DB and bounded callback cost.
2. 100,000 mixed conversation observations through the actual shared-pulse
   service.
3. Every pulse examined operations <= catalog budget.
4. Queue/batch/cache/session bounds never exceed policy.
5. No full profile/session scan.
6. No new thread/executor/Future/scheduled task.
7. Query-only social/semantic work preserves their write contracts.
