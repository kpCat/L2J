# Goal 025A focused test matrix

Seed: `25002511`.

## A — PARTY help

A1. Helper H, Party member M, hostile E, Goal017 evidence says E attacks M:
- combat target stays E;
- help counterpart is M;
- HELP_REQUEST uses PARTY;
- Goal020 dispatch sees M as expected counterpart and current Party membership;
- E is never used as PARTY expected counterpart.

A2. Party generation/member becomes stale:
- help delivery fails closed;
- no hostile-target substitution.

## B — pair cooldown

B1. Persist cooldown for A, expose exact ACTUAL_ATTACK B:
- B is observed/admitted; A cooldown does not block the profile.

B2. Persist cooldown for A, expose exact PARTY_DEFENSE B:
- B remains immediately defensible.

B3. Persist cooldown for A, expose proactive FARMING_ESCALATION or REVENGE B:
- B establishes a fresh encounter and reaches normal OBSERVE/WARN path.

B4. Persist cooldown for A, expose proactive A:
- A stays cooldown-gated until expiry.

B5. Persist cooldown for A, then receive a fresh exact ACTUAL_ATTACK A:
- current reactive defense remains allowed;
- it does not become proactive revenge/corpse camping.

## C — negative controls

- WARNING/DISENGAGE hostile counterpart routing unchanged.
- Legacy Monster attack/cast unchanged.
- No new worker/timer/Future/thread.
- No direct ChatHandler use from pvp package.
