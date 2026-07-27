# SIGNAL LEDGER — Goal 010B

One bounded profile ledger owns exactly three fixed sources.

```text
current registrations
+ retained sequence tombstones
+ pending cleanup tombstones
<= policy.maximumRegisteredProfiles
```

Never-owned inactive targetability creates no ledger.

Release requires all-three scheduler NOT_REGISTERED evidence or final service
stop. Accepted withdrawals retain the ledger for monotonic re-registration.
