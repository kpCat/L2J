# Goal 019 tests

## Normalization
NFC/NFD/NFKC, case, ё/е, whitespace, punctuation/spans, Cyrillic/translit,
malformed/control/oversized/mixed-script and second-pass identity.

## Precedence
Exact Cyrillic > explicit transliteration > fuzzy; exact entity cannot be
replaced by fuzzy intent; short tokens never fuzzy; distance boundaries and
candidate counters are exact.

## Context
Selected target, one/two duplicate names, unique/ambiguous pronouns, leader/role,
managed profile vs real character-object identity, no invented party member.

## Authority
Valid current item/NPC/content/topology/role/capability aliases; stale/missing
identity prevents publication; every result carries pinned source hashes; parser
performs no mutable query.

## Corpus
Strict counts/unique IDs; run declaration/reverse/deterministic shuffle; intent
accuracy >=96%, slot precision >=99%, ambiguity/negative safety 100%, no accepted
case missing required slots, byte-identical result encoding.

## Lifecycle/performance
Disabled path reads no file; failed startup publishes nothing; shutdown waits for
blocked parse claim; 100000 mixed parses; bounded counters; zero DB writes and no
thread/executor/Future/task.
