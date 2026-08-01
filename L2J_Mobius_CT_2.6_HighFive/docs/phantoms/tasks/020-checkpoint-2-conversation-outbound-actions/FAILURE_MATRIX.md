# Failure matrix — Goal 020 Checkpoint 2

| Boundary | Failure | Required result |
|---|---|---|
| managed fast path | real recipient | ignored before queue/context |
| ingress | dual DELIVERED/CLOSED queue full | bounded terminal overflow, no residue |
| atomic handoff | one component conflict | rollback/reload, no half-plan |
| execution capacity | four nonterminal entries | CAPACITY_REACHED, no eviction |
| authority | hash drift | no action/send/write except typed terminal |
| goal arbitration | unrelated ACTIVE goal | goal.busy, no overwrite |
| goal submission | optimistic conflict | reload/retry <=3 |
| party response | no/stale/mismatched invitation | rejected, no response mutation |
| query | unknown/ambiguous fact | bounded not_found/ambiguous result |
| sender | dematerialized or identity mismatch | outbound FAILED before DISPATCHING |
| counterpart | offline/stale | typed failure, no guessed target |
| handler | missing/throws before return | DISPATCHING becomes UNCERTAIN or FAILED by exact boundary |
| restart | DISPATCHING | UNCERTAIN, never resend |
| generated callback | PHANTOM_GENERATED | audit only, conversation ingress 0 |
| shutdown | in-flight boundary | claim drains or durable UNCERTAIN |
