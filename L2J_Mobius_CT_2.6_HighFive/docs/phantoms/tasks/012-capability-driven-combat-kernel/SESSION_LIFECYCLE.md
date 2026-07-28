# SESSION LIFECYCLE — Goal 012

```text
RESERVED
→ ENGAGING
→ FIGHTING
→ LOOTING
→ terminal
```

Terminal outcomes include victory variants, player death, low HP, cancellation,
timeout, target loss and backend failure.

The actor ActionLease is held for the complete active session and released only
after owned AI attack/cast cleanup. Terminal data retains no Player or target
object.

The first shared pulse is scheduled only after a session exists. When no active
sessions remain, worker ownership returns to zero.
