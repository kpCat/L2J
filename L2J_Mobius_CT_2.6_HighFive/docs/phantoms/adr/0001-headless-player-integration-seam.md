# ADR 0001 — Headless Player integration seam

## Status

`Proposed`

## Context

Phantom World must use canonical `Player` mechanics without a real TCP client,
a large `Player` fork, NPC substitution, or per-phantom threads. Existing
offline play/trade restores and operates a canonical player with a null or
detached client. However, `Player.sendPacket` drops packets when client is
null, while `GameClient.sendPacket` both writes the packet and invokes
`ServerPacket.runImpl(Player)`. Some of those implementations mutate
server-side validation/observer state.

`GameClient` is not a safe headless abstraction: its constructor requires a
real `Connection`, and its lifecycle owns LoginServer, flood, session, HWID and
network close behavior.

## Decision

Keep `Player` as the canonical world actor and introduce a small
outbound/session seam.

- Real sessions adapt the existing `GameClient` transport.
- Headless sessions never serialize/write bytes.
- Both session kinds apply required server-side packet effects exactly once.
- Headless diagnostics are optional, bounded and disabled by default.
- A separate lifecycle service owns identity claim, load, explicit
  materialization, spawn, dematerialization and idempotent cleanup.
- Phantom actions go through a narrow server-side facade; request packets are
  not an internal API.
- The implementation remains disabled by default.

Exact class names and production changes are deferred to the Task 004 spike.

## Invariants

- unique character/object ownership;
- no concurrent real-client/phantom identity;
- no headless network I/O;
- null packet fails;
- packet effects execute once;
- real-observer broadcasts remain;
- canonical domain validation and item/adena/karma/reuse rules remain;
- cleanup is repeatable and leaves no tasks/world/online/trade residue;
- shared bounded scheduler only;
- no high-frequency INFO/WARNING logging.

## Alternatives

- Fake `GameClient`: rejected because network/session lifecycle cannot be
  safely faked with null connection.
- Nullable client everywhere: rejected due broad NPE/effect-loss surface.
- `PhantomPlayer extends Player`: rejected because constructors are private and
  behavioral override coverage would be fragile.
- Fork/copy `Player`: rejected due permanent divergence.
- NPC-based core: rejected because it lacks canonical player systems.

## Consequences

Positive:

- minimal production blast radius;
- preserves canonical gameplay objects/rules;
- independently testable transport/effect semantics;
- supports deterministic headless integration.

Negative:

- requires explicit classification/testing of packet effects;
- requires extracting narrow facades for handler-bound actions over time;
- requires a lifecycle identity lease and failure-injection matrix;
- `isOnline`/`isOnlineInt` semantics need a deliberate Task 004 decision.

## Risks

- hidden direct `getClient` dereferences in a future action;
- newly added packet effects not covered by tests;
- incomplete cancellation of constructor/short-lived player futures;
- race between real login and phantom dematerialization;
- partial multi-table store or item/mail/trade persistence.

## Validation plan

Task 004 runs create/load, headless attach, world spawn, inventory/skills,
one safe canonical action, packet-effect tests, observer broadcast,
dematerialize, repeated cleanup, restart restore, collision and failure
injection in `l2jmobiush5_phantom_test` with seed `20260725001`.

## Rollback

Keep the real-client adapter as the default/only active implementation, remove
the headless binding/lifecycle spike, discard the isolated test fixtures and
leave existing connected/offline behavior unchanged. No production schema
migration is part of the spike.

## Supersession condition

Accept/supersede this ADR only after Task 004 passes. If Task 004 demonstrates
that the seam requires a large `Player` fork, broad handler rewrites, a fake
network stack, per-phantom threads or production DB access, this ADR is rejected
and the master plan must be formally revised.
