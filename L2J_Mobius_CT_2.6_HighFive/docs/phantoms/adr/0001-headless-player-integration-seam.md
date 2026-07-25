# ADR 0001 — Headless Player integration seam

## Status

`Accepted`

## Independent acceptance

ADR принят после независимой проверки Task 004B.

```text
Accepted commit: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Parent: d36e10e24787edce3fe4f4d933fca4d0ac884d50
Task 004 technical feasibility: ACCEPT
Task 004A: ACCEPT after Task 004B
Task 004B: ACCEPT
```

Retained-identity correction является обязательной частью принятого seam:
любой существующий `REAL_LOGIN` или `PHANTOM` owner защищается независимо от
feature flag, lease release требует exact object ID и полных object-ID cleanup
postconditions.

Оставшееся явное ограничение: recovery orchestration для удержанного
`REAL_LOGIN` lease принадлежит Goal 006. Goal 005 не вводит автоматический
retry loop.

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

Task 004 implements the proposal as `PlayerOutboundSession`,
`HeadlessPlayerOutboundSession`, `PhantomIdentityLeaseRegistry`,
`PhantomActionFacade` and `PhantomPlayerMaterializationSpike`, with bounded
real-login release hooks in `GameClient`, `CharacterSelect` and
`Disconnection`.

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

## Task 004 implementation verdict

The bounded implementation evidence supports:

```text
FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW
```

The real path continues to delegate through `GameClient.sendPacket`. The
headless path performs zero game-network writes and invokes each
`ServerPacket.runImpl(Player)` exactly once, including bounded recursive
effects. Tokenized identity ownership is claimed before load and released last.
Canonical create/load/materialize/action/dematerialize/reload, both collision
directions, observer visibility, all eleven failure points, repeated cleanup
and one/ten sequential measurements pass against the dedicated test DB.

This is a recommendation to accept the seam, not an ADR status transition.
The ADR remains `Proposed` until independent review. The spike is not wired
into production startup and does not authorize Task 005.

## Task 004A hardening recommendation

Independent review accepted the technical feasibility of the seam and required
four bounded ownership/lifecycle corrections. Task 004A implements disabled-mode
legacy login compatibility, shared CharacterSelect/disconnection locking,
fail-closed REAL_LOGIN release postconditions and retryable Phantom cleanup.

The resulting technical recommendation is:

```text
FEASIBLE_WITH_SEAM_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Identity ownership is now released only after the exact `Player` is offline,
absent from both World registries and autosave, and detached from its client.
Operation failures retain the Player, outbound attachment and PHANTOM lease;
retry reaches terminal `STORED` only after all postconditions hold.

This remains implementation evidence rather than self-acceptance. ADR status
stays `Proposed`, Task 004A awaits independent review, and Task 005 remains
`NOT_STARTED`.

## Task 004B retained-identity hardening recommendation

Independent review of Task 004A found that a retained `REAL_LOGIN` owner could
be bypassed while Phantom was disabled, a cleanup Player could release a lease
for another object ID, and cleanup postconditions accepted another World or
autosave object with the same object ID.

Task 004B hardens the existing seam without changing `Player`, packet effects,
configuration or persistence:

- exact legacy login remains only for disabled mode with no current owner;
- every existing `REAL_LOGIN` or `PHANTOM` owner is arbitrated;
- client lease release validates the cleanup Player object ID internally;
- World player/object maps and autosave must be empty by object ID;
- Task 004A retryable cleanup and terminal `STORED` remain unchanged.

The resulting technical recommendation is:

```text
FEASIBLE_WITH_SEAM_HARDENED_PENDING_INDEPENDENT_REVIEW
```

This is still implementation evidence rather than self-acceptance. ADR status
remains `Proposed`, the manual gate remains pending independent review, and Goal
005 remains `NOT_STARTED`.

## Rollback

Keep the real-client adapter as the default/only active implementation, remove
the headless binding/lifecycle spike, discard the isolated test fixtures and
leave existing connected/offline behavior unchanged. No production schema
migration is part of the spike.

## Supersession condition

Task 004/004A/004B удовлетворили acceptance condition без большого `Player`
fork, broad handler rewrites, fake network stack, per-phantom threads или
production DB access. Будущий evidence, нарушающий эти инварианты, требует
отдельного superseding ADR и формального пересмотра master plan.
