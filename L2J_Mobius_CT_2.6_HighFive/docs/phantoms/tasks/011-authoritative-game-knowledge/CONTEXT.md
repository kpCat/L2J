# CONTEXT — Goal 011

```text
Accepted baseline: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Goal 010/010A/010B/010C: ACCEPT
Goal 011: ALLOWED
Goal 012/013: NOT_STARTED
```

Goal 011 builds one immutable language-neutral knowledge snapshot from existing
High Five source facts. It performs no gameplay action and stores no per-profile
state.

Key boundaries:

- drops/spoil are raw loaded definitions, not exact runtime kill probabilities;
- manor knowledge is parsed from static `data/Seeds.xml`, never through
  `CastleManorManager` or its DB/tasks;
- Rift/raid/epic party requirements are versioned `CURATED_RECOMMENDATION`, not
  server admission rules;
- public query paths use immutable indexes only and never scan loaders, files or
  DB.
