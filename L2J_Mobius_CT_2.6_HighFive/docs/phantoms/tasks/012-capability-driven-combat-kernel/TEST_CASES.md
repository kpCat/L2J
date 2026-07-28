# TEST CASES — Goal 012

Fake/core:

- session, lease, worker, queue and threat bounds;
- normal-monster validation and PvP/raid rejection;
- capability/loadout and skill selection;
- shots, HP/MP, target/player death, loot and cancellation;
- dispatch/stop races.

Decision ownership:

- same-plan token survives step advancement;
- every plan terminal boundary cancels;
- stale work cannot cancel a newer plan.

Real server integration:

- canonical physical kill;
- canonical selected skill cast;
- canonical shot consumption;
- canonical ground-item pickup;
- player death and normal-town respawn;
- combat cancellation and materialization drain.

Performance:

- 10,000 sessions;
- 100,000 pulses;
- 100,000 threat updates;
- no retained leases/workers/terminal slots.
