# SKILL AND RESPAWN SAFETY — Goal 012A

## Skills

A selected actual skill must be a hostile/offensive one-target action:

- exact known ID/level;
- active, non-passive, non-toggle;
- `TargetType.ONE`;
- negative/hostile effect;
- correct physical/magic mode;
- not PvP-only, suicide, hero/GM/SevenSigns or special-only.

Pass the exact session mode into cast revalidation. Do not derive a trivially
matching mode from the skill itself.

## Respawn

Respawn receives the exact plan token and reserves an operation generation.

Before and after actor acquisition require:

- service RUNNING;
- token current;
- no active combat session;
- no pending action cleanup;
- operation still current.

New respawns are rejected after STOPPING. A previously claimed operation is
visible to the stop barrier.
