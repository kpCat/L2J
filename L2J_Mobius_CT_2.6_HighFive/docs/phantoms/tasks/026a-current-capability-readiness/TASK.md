# Goal 026A — current capability readiness
Branch: `feature/phantom-world`
Required parent: `1f056dfd97969b463a9f7140a08f160e8fc16a74`
Required subject: `fix(phantoms): require current raid capability readiness`
Seed: `26002611`

Read only this package, `PhantomRaidReadinessService.java`, `PhantomPartyModel.MemberCapability/MemberSnapshot`, and the relevant existing readiness tests. Do not reopen CP1 architecture or historical corpus.

Fix exactly R026A-01: required raid/epic capability must not count merely because it is intrinsic and learned. Require current Goal017 `readyNow` as well, while preserving capability key, minimumRank, minimumCount and optional-capability semantics. A dead/currently unavailable healer/resurrector must not produce `GROUP_READY`. Do not modify Goal017 production code unless a blocker proves this impossible.

Focused tests only:
1. required healer with intrinsic+learned but readyNow=false => GROUP_INCAPABLE;
2. required resurrection same => GROUP_INCAPABLE;
3. readyNow=true can satisfy normally;
4. rank/count still gate;
5. unavailable optional capability alone is non-fatal.

Verification: one focused readiness test target, one rerun only if the edit causes failure, one `ant jar`, diff/scope/encoding checks. No plain `ant verify`, no Goal025 aggregate, no Goal011/017 regressions, no broad tests, no new gap search. First context compaction = STOP and delivery.

After delivery: CP1 remains CHANGES_REQUIRED pending 026A review; CP2+ NOT_STARTED. Ordinary commit exact subject `fix(phantoms): require current raid capability readiness`, ordinary push. Final report includes branch/parent/commit/remote HEAD/subject/verdict/R026A-01/tests/occurred_context_compaction.

Success token: `GOAL_026A_CURRENT_CAPABILITY_READINESS_FIXED_PENDING_INDEPENDENT_REVIEW`
