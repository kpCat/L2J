# Acceptance — Goal 016
## Git and scope
- [ ] exact parent `a546dae868d93d54ec4bc6e1836080b90f810167`, branch/subject, one ordinary child, remote exact;
- [ ] final Goal 015 review recorded ACCEPT;
- [ ] verifier 015 is pinned/ancestor-compatible before Goal 016 implementation;
- [ ] no schema, Player.java, protocol, other-chronicle, geodata or future-Goal change;
- [ ] no per-profile task/thread/Future or high-frequency logging.
## Creation and persistence
- [ ] target zero/default/disabled paths create nothing;
- [ ] shell profile + population component is atomic;
- [ ] bounded managed-profile paging, no startup unbounded list;
- [ ] shared initializer is used by CharacterCreate and population;
- [ ] population never invokes packets/GameClient/client OnPlayerCreate;
- [ ] exact level 1, canonical location/vitals/Adena/items/skills/shortcuts;
- [ ] reserved inaccessible account ownership is durable;
- [ ] every saga stage restarts idempotently;
- [ ] no duplicate profile/account/character/link/item/skill/shortcut;
- [ ] mismatches fail-stop INCONSISTENT without guessed cleanup.
## Schedule and load shaping
- [ ] strict deterministic population XML and full start-class coverage;
- [ ] one shared scheduler pulse control outside scheduler monitor;
- [ ] no DB/XML scan or character creation on pulse;
- [ ] midnight/DST/clock-jump/restart semantics exact;
- [ ] ACTIVE target/materialization cap and proportional region quotas;
- [ ] deterministic stable rotation and bounded signals/TTL;
- [ ] target `0→3→1→3` retires/returns same profiles before creating new ones;
- [ ] scheduler/backpressure and creation-in-flight limits exact.
## Lifecycle and evidence
- [ ] real level-1 creation for at least two classes;
- [ ] real wake/materialize/sleep/dematerialize/restart;
- [ ] retirement/unregister restart recovery;
- [ ] all creation fault points and shutdown claims drain;
- [ ] class/level/region snapshots bounded;
- [ ] seven focused modes and affected regressions green;
- [ ] historical verifier 015 and verifier 016 green before cumulative;
- [ ] one final aggregate, green ant verify and standalone jar;
- [ ] post-commit verifier 016 2× byte-identical;
- [ ] report <=220 lines with full command/usage telemetry;
- [ ] token `GOAL_016_POPULATION_MANAGER_SCHEDULES_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate.
