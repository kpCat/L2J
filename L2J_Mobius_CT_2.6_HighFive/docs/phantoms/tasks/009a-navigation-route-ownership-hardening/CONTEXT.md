# CONTEXT — Goal 009A

```text
Goal 008/008A: ACCEPT
Goal 009 commit: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Goal 009 independent verdict: FIX_REQUIRED
Goal 009A: REQUIRED
Goal 010: BLOCKED
```

Keep the inert bounded service, factual capability, direct/no-geodata semantics,
two shared workers, cancellation/deadline discard, LRU cache, cooldown and pure
progress tracker.

Remaining findings:

- computed A* segments are not validated before first publication/cache;
- exact destination is appended without validating the final segment;
- expired/impossible requests call backend before preflight;
- worker dispatch may be overtaken by STOPPING;
- real shutdown diagnostics expose only materialization state.
