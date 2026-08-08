# Rift factual/readiness contract

## Factual tiers

The current High Five Rift source has six type IDs. Their rooms/spawns are
parsed from the canonical source; names are display metadata only.

## Entry facts

Runtime current config supplies minimum party size, jump/timing settings and
per-tier entry cost. The current entry implementation supplies the exact entry
resource/item identity and any additional eligibility rule.

No remembered L2J constant is accepted without current-source evidence.

## Readiness

```text
tier factual
roster exact
minimum size
mandatory roles
member readiness
entry resources
travel readiness
```

No entry resource is consumed by Goal 023.

## Unknowns

Unsupported runtime capacity or eligibility facts are explicit UNKNOWN /
UNSUPPORTED and cannot be converted into READY by assumption.
