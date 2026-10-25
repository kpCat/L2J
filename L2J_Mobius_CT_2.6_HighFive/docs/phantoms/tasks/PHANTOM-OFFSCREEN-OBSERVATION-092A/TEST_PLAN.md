# Test plan — no new TEST DB/runtime, no reused RED exit masquerading as GREEN

1. Read existing `PhantomOffscreen092Checks.retentionBoundary()` and production observer sources. Add focused RED for original early `_lastHuman.remove` and `refresh`, independent RED for 257th human missing with naïve `.limit(256)`. Record both RED conditions once, no repetitive attempts.
2. Implement retention fix and typed observer admission in production, without touching background transaction/codec/graph. Add injected monotonic clock and `Supplier` for deterministic tests. Actual production façade must use live World and existing `humanObserver` classification.
3. Focused GREEN: different observer counts 0/1/256/257; origin/dest both guarded, fresh movement changes decision; human=STORED grace 59.999s vs >60s and warmup; null/overflow/unknown fail-closed; 2D region and native Z/instance; real player vs offline headless; no retention action escape.
4. Run nearest source tests (`PhantomObserverHandoff040Suite`, `PhantomHumanLocalityTraceSuite`) only where startup-free. If dependent on GameServer/services, do not launch long native scenarios; state SKIP with reason; targeted `java` after single bounded Ant compile/JAR or javac isolated tests.
5. Verify no changes to `PhantomBackgroundStateCodec` format2/compact3 (not modified), no source OwnerConfig edits, no changes to `onlineHumanPoints` old API semantics.
6. Report `SUCCESS_092A_OBSERVER_GUARD`, product `OFFSCREEN_TRANSIT_OPEN`; output exact commit SHA after Git push, clean tracked state, no unexpected untracked deletion.
