# CONTEXT — Goal 008

```text
Baseline: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Stage I: COMPLETE
Goal 007/007A: ACCEPT
Goal 008: ALLOWED
Goal 009: NOT_STARTED
```

Goal 008 fills the accepted scheduler work sink with a domain-neutral decision
engine. The Goal 005 component envelope is the only persistence surface:
`goal.runtime`, version 1. Production remains inert with zero attached profiles,
zero scheduler registrations and empty sealed candidate/handler registries.

The only scheduler follow-up is to reconcile successful cleanup retry from the
current requested state rather than a stale target captured before the external
call.
