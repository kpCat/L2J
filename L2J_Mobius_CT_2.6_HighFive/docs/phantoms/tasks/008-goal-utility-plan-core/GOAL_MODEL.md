# GOAL MODEL — Goal 008

- Generic immutable `DomainRef(namespace,key)`; no lookup.
- Generic capability requirements/ranks; no concrete production keys.
- One immutable schema-version-1 goal/profile.
- Bounded sources, constraints, budgets, priority, deadline and monotonic
  revision.
- Component `goal.runtime`, version 1, deterministic binary payload <=4096.
- Persist goal only; never persist plan, handler, token or explanation history.
