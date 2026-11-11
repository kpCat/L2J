# GameServer receipts, safe learning and missing facts

## V3 DAMAGE new seam
Read-first: `L2jCombatBackend.learningCandidates` and `castLearning`, `PhantomCombatService.issueAction`, `LearningPolicy`, `LearningCastEvidence` (existing true native exact HP), `TacticalPolicyV3`, `LegalActionV3`, `TacticalObservationV3`, `TacticalStoreV3`, `TacticalTrainerV3`, `TrainingCollector`. Prefer forwarding existing verified event identity into new V3 envelope with exact source mark, not a second self-made damage calculus. Actual skill/target/HP change, mana debit and temporal values from native effects; pre-CUT ability/HP setup never a reward. Include baseline skill, offered candidates, chosen action, policy version, owner/epoch, target spawn identity, duration and result, observational UNKNOWN. No invented cast completion from command ISSUE.

## V3 HEAL carry forward
Preserve `HealEvidenceV3`, PartyTactics safety: urgent heal stock for HP <=25% target / <=30% caster / under pressure; no neural decision overriding emergency. Ensure moving/restored heal training examples do not regress after adding DAMAGE. Existing 003 Cardinal97 audit worse than constant: must retain per-class metrics and fail closed on unsupported improvement. Two changed HEAL targets in 003 do not imply better outcome.

## PvE episode summary
At physical START and SETTLE record immutable encounter ID, class/level, actual role, health resources, active targets/NPC spawn/instance, group scope, owner nonce/epoch, timed actions, effect receipts. At SETTLE record actual NPC death and attributable kill if verifiable, durations, participant alive/dead and deaths, exact paid MP, exact native damage/heal, overhead, drop/EXP/SP/adenа *only from canonical credited receipts*. Unknown MUST stay UNKNOWN. Do not use `hit >0` as proof of mob kill, or absolute NPC HP difference as whole-party DPS without provenance. Partial/timeout episodes retain observed facts but NO invented outcome credit.

## Learning signal and safety
Separate instant verified action reward and delayed episode result. First training in 004 may use action reward (with explicit model/truth limitations); episode-level victory/survival/efficiency must be recorded and assessed, not falsely asserted as optimized policy if no credit attribution path. If episode terminal attribution is implemented, specify credit assignment and double counting rules. Store raw observations for later counterfactual/sequence learning; choice outcomes are observed only for selected action.

## Future hooks, not claims in 004
No native PvP win, retreat/Return, raid, AoE pike train, wipe, summon or Olympiad credit without a corresponding verifiable receipt in their future tasks. Semantic/dialogues and pathfinding learning explicitly after combat work. No illegal enemy hidden stats/gear in PvP observations. No teleport, invuln, free heal/reset of in-flight cooldown DURING combat. Pre-CUT test setup may be controlled, isolated and reported, not turned into magical world powers.
