# Failure matrix — Goal 020 Checkpoint 1

| Boundary | Failure | Required result |
|---|---|---|
| Say2 scope | nested/mismatch | close safely; ordinary chat remains authoritative |
| generic delivery | no registration | no-op |
| generic delivery | callback throws | chat unchanged; fixed failure metric |
| Phantom ingress | queue full | drop observer-only event; chat unchanged |
| identity | observer no longer PHANTOM | discard without state write |
| batch | >32 managed observers | no response; overflow metric |
| election | no/duplicate local address | no response |
| semantic | rejected | no proposal; catalog no-response/clarification |
| semantic | candidate exhaustion | clarify.complexity |
| semantic | authority drift | no state mutation/plan |
| social | unavailable/failure | neutral style; fixed metric |
| social receipt | capacity full | no social mutation; Party remains successful |
| conversation component | corrupt/stale authority | fail closed |
| conversation write | optimistic conflict | reload/retry <=3 |
| plan sink | failure | gameplay/chat unchanged; visible failure |
| shutdown | delivery race | callback claimed or rejected before queue mutation |
| restart | pending clarification | reload if authority current; otherwise discard/fail closed |
| duplicate dispatch | exact same observation | at most one state mutation and one plan |
