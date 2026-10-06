# GOAL

Required base:
`0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7`

Branch:
`experiment/m1-candidate007-observe008`

Model: GPT-6.1 Sol
Reasoning: High
Subagents: forbidden.

Use a NEW isolated worktree if current checkout is wrong/dirty/detached:
`C:\Users\ZBook\.codex\worktrees\m1-online-trace-017\L2J_Mobius`

Do not modify/switch/reset/stash/clean the main checkout.

## Exact known bug in diagnostics

In current `PhantomHumanLocalityControl.onPulse()`, recorder watch admission occurs
before `_online.test(profileId)` has filtered the topology probe.

This saturated TASK016 watched profiles with OFFLINE ids, while the same refresh summary
proved 9 final accepted local candidates.

## Task

1. Change recorder cohort selection so OFFLINE topology probes cannot consume watch slots.
2. Prove diagnostics-only semantics: topology eligibility/candidate set/signal behavior unchanged.
3. Re-run one real TestAdmin trace and follow actual ONLINE human-local candidates.
4. Name the real first lost edge for an accepted human-local profile.
5. Do NOT repair that edge in TASK017.

Preferred outcome is naturally reaching MATERIALIZE_CALL or MAT_WORLD_SPAWN, but this
task is diagnostic only.

M1 remains OPEN.
