# Private-store contract

## Store offer

A durable Phantom store offer includes:

```text
SELL | PACKAGE_SELL | BUY | MANUFACTURE
bounded title
exact item/recipe lines
counts/prices
content hash
expiry
```

Canonical Player store state is installed only after materialization.

## Buy from sell store

Buyer reserves Adena and capacity. Seller exact stock is revalidated. Package
semantics cannot be weakened.

## Sell into buy store

Seller reserves exact objects/counts. Store owner Adena and capacity are
revalidated.

## Transaction

Packets delegate to one packet-independent service. The operation is OBSERVING
before Adena or item mutation.

List hash, count and price are immutable for the operation. No hidden price
clamp is accepted as the quoted result.

## Lifecycle

Store owner remains ACTIVE/NEARBY_PERCEPTIBLE. Dematerialization is blocked.
Restart requires materialization and revalidation before reopening. Offline
trade persistence is not redesigned.
