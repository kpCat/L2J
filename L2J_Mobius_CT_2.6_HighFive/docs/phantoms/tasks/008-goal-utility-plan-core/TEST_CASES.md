# TEST CASES — Goal 008

Core: model/registry bounds, capabilities, integer score, exception isolation,
deterministic tie, threshold, top-eight explanation, plan validation, one
step/work, retries/timeouts/replan, terminal states, stale result cancellation,
detach and stop quiescence.

Persistence: deterministic codec, invalid payloads, optimistic operations,
attach-load-once, zero tick reads, restart replan, no plan persistence, terminal
state persistence and zero owned residue.

Integration: scheduler drives one decision slice/work item; blocked handler plus
goal replacement discards stale result; cleanup retry recomputes current
requested state.

Performance: 1000 runtimes, 64 candidates, 8 considerations, deterministic
summary ×2, no per-profile threads/futures and no DB reads after attach.
