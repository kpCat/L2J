# Goal 025 context

## Roadmap scope

Goal 025 is **PvP/PK, threat and escalation**.

Roadmap-required result:

- strength/risk assessment;
- current party/friend ally awareness;
- retreat;
- warning;
- help calls;
- revenge memory;
- canonical zone rules;
- karma/PK/drop consequences;
- bounded escalation;
- current/max CP awareness;
- canonical CP -> HP PvP damage order;
- natural CP regeneration;
- stock/reuse-aware CP potion use;
- economic consumption;
- Olympiad restrictions.

Formal alliances and clan wars are explicitly deferred to Goal 027.

## Accepted source facts entering the task

### Existing combat owner

Goal012/012A already owns combat execution. `PhantomCombatService` owns combat
sessions, action leases, the bounded shared pulse worker, cleanup and terminal
truth. `L2jCombatBackend` delegates normal-monster attack/cast to canonical
Player AI and canonical known Skill execution. Existing monster paths must stay
semantically unchanged.

The current combat API intentionally rejects Player targets in `attack` and
`cast`. Goal025 must therefore add an explicit PvP target/admission path inside
the same combat owner. It must **not** relax `TargetSnapshot.normalMonster` or
make the existing monster API polymorphically attack arbitrary Playables.

### Canonical High Five PvP execution

The ordinary physical forced-attack server path resolves the target and invokes
`target.onForcedAttack(player)`. `Creature.onForcedAttack` owns canonical
peace-zone/Olympiad/etc checks and finally issues the ATTACK AI intention when
allowed. Phantom code must not instantiate or replay `AttackRequest` packets;
it may call the canonical server object seam from the L2j backend.

For skills, the ordinary request path ends in `Player.useMagic(skill,
forceUse, dontMove)`. `Player.useMagic` owns force-attack and target legality
rules. Goal025 must use this internal canonical server API rather than copying
packet logic or bypassing it with a weaker PvP-specific cast path.

### Canonical CP truth

`PlayerStatus.reduceHp` applies PvP damage to CP before HP unless the canonical
call explicitly ignores CP. `PlayerStatus` owns natural CP regeneration.
Production Phantom code must never directly decrement/increment HP/CP to model
PvP.

High Five item data defines:

```text
5591 CP Potion         -> skill 2166/1, restores 50 CP, reuse 500 ms,
                          ItemSkills handler, Olympiad restricted
5592 Greater CP Potion -> skill 2166/2, restores 200 CP, reuse 500 ms,
                          ItemSkills handler, Olympiad restricted
```

`ItemSkills` / `ItemSkillsTemplate` own Olympiad rejection, item/skill reuse,
skill conditions, canonical cast and inventory consumption. Goal025 may select
from real owned stock, but must execute through the registered authoritative
handler and observe inventory/reuse after use. No free CP and no direct
`setCurrentCp` in production.

### Canonical PvP/PK consequence truth

`Player` owns PvP flag, PvP/PK kill accounting and karma. `PvpConfig` and
`RatesConfig` own configured timing/drop-risk inputs. Player death/drop logic
owns the actual probabilistic item loss. Goal025 may read a bounded immutable
risk snapshot for decision making; it must never call Phantom-side karma/PK
mutation or directly drop inventory.

### Party help seam

Goal017 `PhantomPartyTactics` already produces `PROTECT_MEMBER` when a party
member has an observed attacker and routes mutation through Goal012 combat
ownership. Existing observations are monster-oriented. Goal025 may extend this
with an explicit PvP attacker/directive path, but must preserve the existing
monster protection behavior and 9-member party bound.

### Goal020 outbound language owner

`L2jPhantomConversationExecutionPort.dispatch` already owns generated outbound
chat through `ChatHandler` plus `ChatObservationService`. Goal025 warning/help
text must enter a narrow typed Goal020-owned outbound seam. PvP code must not
send packets or call `ChatHandler` directly.

## Safety interpretation of “PvP/PK”

Goal025 enables real canonical PvP/PK consequences, but does not authorize cold
random hunting. A Player is never an aggression candidate merely because they
are visible, PvP-flagged, low HP, valuable or inconvenient.

An encounter must originate from a bounded causal source:

```text
ACTUAL_ATTACK
FARMING_ESCALATION
PARTY_DEFENSE
REVENGE
```

`REVENGE` means exact Goal018 history for a known counterpart plus a current
bounded way to resolve/perceive that same counterpart. Social memory cannot be
used to scan the World for victims.

A proactive forced PK against a currently non-auto-attackable target requires
an exact stronger authority (`FARMING_ESCALATION` or evidence-backed `REVENGE`),
a persisted warning stage and all current canonical legality/risk gates.
Reactive defense and party defense do not wait for a warning.

No global `World.getPlayers()` / profile scans. Local WorldRegion visibility may
be used only for bounded risk/support context and never as a cold aggression
candidate generator.
