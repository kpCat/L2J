# Direct-trade contract

## Offer

Each side has up to sixteen exact lines:

```text
owner character
item object ID
item ID
count
enchant/location evidence
optional Adena
```

The offer content hash includes both sides and partner identity.

## Request/accept

The existing request timeout, distance, instance, refusal, block list, karma,
jail, store and Olympiad rules remain canonical.

For a real player partner, acceptance and confirmation must come from their
ordinary server state.

## Confirm

Any changed line invalidates both confirmations.

Trade-list monitor order is ascending owner object ID. Economy profile locks are
acquired before TradeList monitors.

## Exchange

The C1 operation crosses OBSERVING before the first transfer.

The observer reports every line transfer and both terminal callbacks. Exact
global item/Adena sums are checked before COMMITTED.

Partial exchange is INCONSISTENT and is never repeated.
