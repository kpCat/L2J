# Historical/current documentation ownership contract

## Principle

A historical acceptance test must freeze the contract that its Goal owned.

It must NOT freeze mutable facts owned by future Goals merely because those facts
happened to be current when the historical test was last edited.

## Goal032 durable documentation contract

Use an explicit constant set:

`GOAL032_CONFIG_KEYS`

with exactly the 13 keys listed in CONTEXT.md.

For both shipped and local-play preset:

- require all 13 Goal032 keys;
- reject duplicate occurrences of Goal032 keys;
- do NOT require the whole config key set to equal 13;
- do NOT require shipped and preset whole key sets to be identical.

For each of the 13:
- production parser must reference it;
- Goal032 operator tuning guide must document it.

Preserve Goal032-value checks:
- shipped system OFF;
- shipped population 0;
- shipped active 0;
- local-play core population/cap values where relevant.

Do not make Goal032 own Goal033 ecology or Goal038 conversation values.

## Reset safety/UX

Goal032 documentation test must continue proving:

- `//phantom reset preview`
- `//phantom reset confirm <TOKEN>`
- `//phantom reset confirm <TOKEN> reseed`
- `//phantom reset cancel`
- read-only preview / one-time confirmation semantics;
- shared ownership blocks unsafe deletion;
- reset is not a time machine;
- production DB boundary is explicit;
- AdminPhantom exact reset routes still exist;
- GameServer does not automatically call reset.

Prefer anchoring detailed historical statements in:

`docs/phantoms/reports/032-phantom-reset-operator-control.md`

and current command documentation in QuickStart/operator tuning.

## Historical report

Require the accepted Goal032 report to preserve:
- `SUCCESS`;
- Phantom-only ownership;
- reset/reseed command semantics;
- transactional/rollback safety;
- no startup reset;
- the historical statement that Goal032 then documented 13 config keys.

It is fine for that historical report to retain Roadmap v3 and "next Goal033";
that is historical evidence, not current navigation.

Do not rewrite the historical report to current Goal039 wording.

## Mutable forward state forbidden in Goal032 test

Remove Goal032 assertions that require current mutable docs to say:

- roadmap version 3;
- next Goal034;
- current handoff Goal034;
- future Goal032..Goal037 ordering as a present navigation contract.

Goal032 may smoke-check that current docs still mention Goal032 SUCCESS if useful,
but Goal039 is the authority for current roadmap/status/handoff/freeze.

## Current Goal039 config inventory

In Goal039 structure/static suite, add or retain a clearly owner-partitioned current
config check.

Exact declared-scope owner sets:

- Goal032: 13
- Goal033: 4
- Goal038: 6

Current shipped union:
23 unique keys, no unknown/duplicate key.

Current local-play preset:
17 unique explicit keys = Goal032 + Goal033.

The six Goal038 keys may be absent from preset only if parsing the preset yields
exact defaults:
- humanized = true
- customPack = true
- register = CASUAL
- profanity = CONTEXTUAL
- variation = HIGH
- mature = false

Do NOT copy the six keys into the preset merely to make key sets equal unless an
independent functional requirement proves the preset must become explicit.

## Future-proofing

A future explicit new scope may add keys. At that time:
- historical Goal032 stays green because its 13-key contract is unchanged;
- Goal039/future release contract must be intentionally updated for the new scope.

This is the desired ownership model.
