# Goal 026 CP1 independent review
Reviewed `1f056dfd97969b463a9f7140a08f160e8fc16a74`. Verdict: `CHANGES_REQUIRED`.

R026A-01: `PhantomPartyModel.MemberCapability` has `intrinsic`, `learned`, `readyNow`, `readinessReason`, but `PhantomRaidReadinessService.satisfies(...)` currently counts only `intrinsic && learned`. Therefore a dead/currently unavailable healer or resurrector can satisfy a hard requirement and permit `GROUP_READY`.

Required correction: hard capability contribution must require current Goal017 readiness evidence; at minimum `intrinsic && learned && readyNow`, preserving key/rank/count semantics and optional capability behavior. No Goal017 redesign.

All other inspected CP1 boundaries are accepted: read-only boss authority, conservative EPIC UNKNOWN handling, Goal011 reuse, bounded Party/CommandChannel snapshot, and no orchestration/navigation/combat/persistence/worker/victory simulation.
