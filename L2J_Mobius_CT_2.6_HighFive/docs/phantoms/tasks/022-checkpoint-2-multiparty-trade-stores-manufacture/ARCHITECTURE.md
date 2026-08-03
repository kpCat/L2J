# Goal 022 Checkpoint 2 — multi-party economy architecture

## Dependency direction

```text
strict Goal / accepted standing listing
→ immutable offer snapshot and consent
→ C1 operation/reservation ledger
→ packet-independent canonical action service
→ exact two-owner observation
→ audit and Goal/store reconciliation
```

Packets and UI adapters do not own reusable mutation.

## Participant kinds

```text
PHANTOM:
    positive profile ID
    exact linked character
    materialization lifecycle protected

EXTERNAL:
    no Phantom profile
    exact canonical character object ID
    consent/listing evidence required
```

At least one PHANTOM initiates each operation.

## Lock order

```text
1. Phantom profiles ascending
2. economy offer
3. economy operation
4. reservations canonical
5. active Player/TradeList owners by object ID
6. canonical character rows by object ID
7. items by owner/object ID
8. components/Goals where required by the accepted DB transaction
```

No path may acquire a Phantom profile lock after a TradeList, item or character
lock.

## Social execution

Direct trade, private stores and manufacture are visible active gameplay.

```text
BACKGROUND → ACTIVE_REQUIRED
```

There is no invisible background trade or fake offline player.

## Mutation rule

Before first canonical resource mutation:

```text
operation = OBSERVING
```

After OBSERVING, no retry may invoke the canonical action again.

## Partial-effect rule

A partial canonical action is not automatically compensated.

```text
exact complete after → COMMITTED
exact before / no action → ABORTED
partial or ambiguous → INCONSISTENT
```

Global conservation must still hold and the operation cannot redispatch.

## Consent

- direct trade requires explicit counterparty acceptance;
- private-store/manufacture listing is standing canonical consent for its exact
  list hash and price;
- changed listing invalidates the offer;
- a Phantom never forges an ordinary player's response.
