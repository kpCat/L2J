# Goal 025 test cases

Seed: `25002501`.

All core behavior must be dynamic. Source-marker/static checks are supplementary.

## A. Prior baseline gate

- branch parent is exact `922f72c0d422904dcbdc6215a5cc1167a1bb84fb` unless a pre-existing user commit
  is explicitly reviewed and proven compatible before work;
- docs record Goal024A ACCEPT, R024A-01/02/03 CLOSED, Goal024 overall ACCEPT;
- Goal026+ remain NOT_STARTED.

## B. Causal admission

1. no causal source -> no PvP candidate;
2. visible neutral Player alone -> no candidate;
3. PvP-flagged/karma Player alone -> no candidate;
4. exact current Player attacker -> ACTUAL_ATTACK;
5. exact attacker of same current Party member -> PARTY_DEFENSE;
6. exact live Goal024 bilateral ESCALATED receipt -> FARMING_ESCALATION;
7. SHARE/WAIT/MOVE/REFUSE/expired/stale farming agreement -> no farming PvP authority;
8. Goal018 revenge evidence without currently resolvable exact counterpart -> no candidate;
9. exact current revenge counterpart + evidence -> bounded REVENGE candidate;
10. same Party/self/friendly forbidden cases reject;
11. no global Player/profile scan.

## C. Monster combat isolation

Pin existing Goal012/012A behavior:

- normal Monster `attack/cast` remains accepted exactly as before;
- Player passed to legacy monster API remains rejected;
- acquisition/spoil/manor/quest combat cannot route to Player;
- party monster `PROTECT_MEMBER` remains monster-only;
- PvP-only Skill is not enabled by weakening monster `PhantomCombatSkillSafety`.

## D. Canonical physical PvP integration

Using isolated headless/test DB fixtures with two canonical Players:

- exact Player target route reaches canonical `onForcedAttack` and resulting
  PlayerAI ATTACK ownership;
- peace zone rejects;
- wrong instance/dead/stale identity rejects;
- same Party rejects before issue;
- canonical rejection is not converted to ISSUED;
- cancel cleans only the Goal025-owned action and preserves foreign action truth.

No ClientPacket is constructed.

## E. Canonical skill PvP integration

- exact known one-target hostile skill route uses `Player.useMagic`;
- forceUse=false cannot attack a non-auto-attackable target through policy;
- forceUse=true is available only with exact proactive authority;
- canonical checkDoCast/Skill condition/reuse rejection propagates;
- PvP-specific safety is separate from monster safety;
- unsafe AoE/suicide/GM/etc reject;
- no `RequestMagicSkillUse` construction.

## F. CP truth

1. PlayerStatus-backed PvP damage demonstrates CP is consumed before HP;
2. Goal025 never directly mutates CP;
3. natural CP regeneration remains canonical; no Phantom timer;
4. exact owned item 5591 use runs registered ItemSkills and consumes one;
5. exact owned item 5592 use runs registered ItemSkills and consumes one;
6. 500 ms item/skill reuse is respected by canonical handler;
7. Olympiad item use is rejected by canonical handler;
8. zero stock -> no synthetic potion/use;
9. after use, record success only from observed inventory/reuse/CP evidence.

Fixture-only `setCurrentCp` is allowed to construct deterministic initial state;
production Goal025 code may not call it.

## G. PvP/PK/karma consequence truth

- retaliatory/auto-attackable PvP route observes canonical PvP flag/kill outcome;
- a controlled isolated proactive neutral-target fixture, where server rules
  classify the kill as PK, observes canonical PK/karma result without direct
  Phantom mutation;
- risk snapshot matches current PvpConfig/RatesConfig values;
- no direct karma/PK/PvP/drop call from Phantom production code;
- probabilistic death drop is not asserted as guaranteed unless deterministic
  test config/evidence safely proves the exact branch; risk can be tested
  without forcing random inventory loss.

Every destructive fixture restores test-owned Player state/inventory in finally.

## H. Escalation/warning

- proactive forced conflict cannot jump OBSERVE directly to ENGAGE;
- WARN stage persists before proactive attack;
- Goal020-owned warning dispatch returns a receipt/result;
- warning/source authority stale -> no proactive attack;
- reactive ACTUAL_ATTACK bypasses warning delay;
- warning delay uses shared logical pulse time; no timer/Future;
- maximum proactive per-pair budget enforced;
- cooldown survives restart;
- no corpse camp / immediate post-respawn same-pair loop.

## I. Party help

- same Party member attacked by Player creates distinct PvP protection directive;
- bounded helpers only;
- non-party Player does not create help directive;
- existing monster protection regression remains PASS;
- helper executes through Goal012 combat ownership;
- stale party generation cancels/rejects help.

## J. Retreat

- low HP/effective CP+HP or materially inferior bounded risk selects RETREAT;
- retreat cancels only owned PvP action;
- route/movement goes through existing navigation/action owner;
- route failure degrades to DISENGAGE/blocked, never teleport/direct XYZ;
- recovery above policy threshold can end retreat/cooldown but cannot exceed
  same-pair engagement budget.

## K. Restart and persistence

- warning/cooldown/idempotency survive exact profile component reload;
- stale authority fails closed;
- persisted Phantom counterpart is re-resolved by exact profile ID only;
- persisted human object-only encounter does not blindly resume after restart;
- social deliveries are idempotent/retryable through Goal018;
- no duplicate warning/help/kill/death social event after replay.

## L. Perceptual fairness

- target hidden inventory/equipment/skill list is not exposed to decision policy;
- target strength uses coarse permitted observations;
- local support/risk scan is bounded and cannot generate a victim candidate;
- result independent of unrelated Players outside bounded local context.

## M. Performance/lifecycle

At least one deterministic performance test with a large number of pure policy
or encounter evaluations. Prove:

- fixed/capped attacker and local risk lists;
- <= one encounter per profile;
- no new worker/timer/Future/thread;
- no unbounded per-pair history;
- shutdown drains/clears Goal025 runtime state;
- disabled mode creates no Goal025 work.

## N. Required regressions

Run only current exact affected targets after implementation:

- Goal012/012A combat core/action ownership/server integration;
- Goal017 party state/tactics paths touched by PvP help;
- Goal018 social paths touched by new PvP event evidence;
- Goal020 conversation/outbound paths touched by warning/help;
- Goal024A farming lifecycle/escalation source;
- navigation regression only if retreat integration changes its behavior;
- disabled Phantom regression.

Historical unrelated timing flakes: one targeted confirmation only. If it PASSes
and no direct Goal025 relation exists -> `KNOWN_UNRELATED_FLAKE`. No stress loop.

After production/test freeze: one final Goal025 aggregate. Because Goal025 is a
VERY_HIGH-risk new cross-system feature, one final plain `ant verify` is allowed
and expected after the Goal025 aggregate; do not repeat it ceremonially after
process/docs-only changes. One `ant jar` after freeze.
