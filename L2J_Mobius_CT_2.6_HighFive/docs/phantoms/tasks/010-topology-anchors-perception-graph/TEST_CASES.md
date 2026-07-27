# TEST CASES — Goal 010

Core:

- schema/ID/count/geometry/hierarchy validation;
- canonical hash independent of XML order;
- duplicates/dangling/cycles;
- factual map-region/NPC/spawn/door checks;
- atomic invalid reload;
- deterministic spatial/nearest/edge queries;
- live door state.

Perception:

- profile registration and stale update;
- same-node/neighbor local chat;
- closed-door channel blocking;
- combat ACTIVE/NEARBY;
- targetability ACTIVE/withdraw;
- recipient/backpressure isolation;
- stop quiescence;
- perceptible neighbor minimum gate.

Corpus:

- every production entity validates against current High Five loaders.

Performance:

- 10000 nodes, 20000 edges, 50000 anchors, 10000 profiles;
- 1000 chat + 1000 combat events;
- two identical canonical summaries;
- no per-profile task/Future/thread and no DB.
