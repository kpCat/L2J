# B05 finite native combat settlement

FINAL_SCENE_A33_A captured 380.83 seconds and every enrolled natural actor (6).
CONTINUITY_V2 was 4/6: profiles1059 and1272 published sticky PHASE_DEADLINE.
Their actual native combat phase continued across useful kills beyond the existing
120-second native phase horizon. The original evidence is retained; it is not reset.
Legacy result remains NOT_APPLICABLE_COUNT6. No full-scene PASS is inferred.

Read-first: PhantomVisibleAutoPlay, native evidence/work, CreatureAI/PlayerAI,
Creature and the existing native handoff/visible-intent test fixtures. Native core
and its 120-second limit remain read-only. Existing AutoPlay policy is reused.

Own controlled native RED: after actual native damage opened COMBAT, advancing
only the policy clock to phase+90 seconds still admitted another root (ACQUIRED).
The first root-only pause did not settle the phase: existing PlayerAI continued
combat. That intermediate GREEN attempt failed and its log remains evidence.

The bounded fix pauses new AutoPlay/AutoUse roots at the existing 90-second bound
and asks the stock AI for IDLE, unless a native cast is still executing. Already
captured earned callbacks keep their existing tickets. No abortCast, ownership
reset, evidence reset, new controller, scheduler or reward implementation exists.
When the original phase completes, the same session admits the next root.

COMBAT_EARNED_GREEN: 2/2 PASS. One case verifies actual passive completion and
same-owner resumption; the other captures a real RESERVED attack-hit ticket and
verifies its final state is COMPLETED, not cancelled. Native phase and damage are
actual callbacks; the controlled policy clock is TEST-only. Soft boundary tests
COMBAT_SOFT_BOUNDARY_GREEN: 3/3 PASS. Full-server final-candidate proof is pending.
