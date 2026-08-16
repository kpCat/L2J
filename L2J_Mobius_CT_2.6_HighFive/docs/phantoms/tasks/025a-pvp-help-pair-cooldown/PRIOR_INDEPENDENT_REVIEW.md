# Goal 025 independent review — Goal 025A handoff

Reviewed remote commit: `5656b9ce8c423f503d4a8b5d1046eb12929950d4`
Parent: `922f72c0d422904dcbdc6215a5cc1167a1bb84fb`
Branch: `feature/phantom-world`

Independent verdict: `Goal025 = CHANGES_REQUIRED`.

## R025A-01 — PARTY help uses the hostile counterpart

`PhantomPvpContext.Snapshot` carries both:
- `conversationCounterpart`: the hostile PvP counterpart;
- `helpCounterpart`: the exact attacked Party member supplied by Goal017 PARTY_DEFENSE evidence.

But `PhantomPvpService.help(...)` submits `MessageKind.HELP_REQUEST` using
`observed.conversationCounterpart()`.

`PhantomPvpConversationBridge` maps HELP_REQUEST to PARTY chat, while the
Goal020 production dispatch requires the expected counterpart to be in the
sender's current Party.

Therefore a real PARTY_DEFENSE help call is addressed to the hostile attacker
and production dispatch can become STALE/FAILED instead of delivering the Party
help call.

Required correction:
- HELP_REQUEST must use an exact current Party-member help counterpart from
  Goal017 evidence;
- hostile counterpart must never be substituted for PARTY help;
- stale/missing/non-party help evidence fails closed;
- Goal020 remains the chat owner; no direct ChatHandler/packet calls.

## R025A-02 — persisted pair cooldown is profile-global

`PhantomPvpService.process(...)` currently returns when the persisted encounter
has `cooldownUntilLogicalNanos() > now` before `_context.observe(profileId, now)`
runs.

Therefore cooldown for counterpart A suppresses a new exact causal counterpart
B, including a new ACTUAL_ATTACK or PARTY_DEFENSE. This violates pair-scoped
cooldown semantics and immediate reactive-defense doctrine.

Required correction:
- current causal source/counterpart must be observed before deciding whether the
  persisted cooldown applies;
- cooldown A must not block a different exact B;
- same-pair proactive A remains cooldown-gated;
- a fresh reactive ACTUAL_ATTACK/PARTY_DEFENSE remains immediately defensible;
- preserve one encounter/profile and bounded persistence; do not add an
  unbounded per-pair history/map.

## Already verified and not to redesign in 025A

- physical PvP uses canonical `target.onForcedAttack(actor)`;
- skill PvP uses canonical `Player.useMagic(skill, forceUse, false)`;
- legacy Monster target path remains separate;
- CP 5591/5592 executes through registered ItemSkills;
- Goal018 remains social/revenge owner;
- Goal024 remains farming escalation owner;
- retreat delegates to navigation + existing combat action ownership;
- no second combat engine is required.

## Process-only finding

The Goal025 plain `ant verify` reached historical `verify-task-014a.ps1`, which
still asserts `Goal 025: NOT_STARTED`. Do not repair that historical verifier in
025A and do not rerun plain `ant verify`.
