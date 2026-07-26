# TEST CASES — Goal 006A

- existing World Player rejected and untouched;
- non-Player WorldObject same ID rejected;
- autosave same ID rejected;
- object injected pre-spawn rejected without split maps;
- no lease/map/permit residue after clean rejection;
- after STOPPING all new action attempts reject;
- blocked BEFORE_STORE causes bounded shutdown return;
- timeout retains entry, permit and PHANTOM identity;
- second shutdown reuses same in-flight drain;
- after latch release, late completion/explicit retry reaches STOPPED;
- no new executor/thread/per-profile future;
- production suite x3, headless/profile/recovery/performance and cumulative gates.
