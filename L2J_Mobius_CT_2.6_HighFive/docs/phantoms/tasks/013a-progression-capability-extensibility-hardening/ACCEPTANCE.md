# Acceptance — Goal 013A

## Git/scope

- [ ] HEAD and `origin/feature/phantom-world` started at `ca50ea28f233e41343035977c55c98129e5d113a`.
- [ ] Exactly one ordinary child commit.
- [ ] Exact subject `fix(phantoms): harden progression capability extensibility`.
- [ ] No amend/rebase/squash/merge/force push.
- [ ] Exact allowlist only.
- [ ] Root `.gitignore` unchanged.
- [ ] No `.l2j` files.
- [ ] No other chronicle.
- [ ] Goal 014/015/017 not started.
- [ ] Production DB not used.

## Capability/resource

- [ ] Stable variant identity exists.
- [ ] Multiple same-group variants per class are preserved.
- [ ] No arbitrary first-evidence collapse.
- [ ] Combat resolver does not stop at the first same-group/static-rank variant.
- [ ] Static rank is not final tactical suitability.
- [ ] Production Game Knowledge + progression composition is independently
      proven.
- [ ] Fixture and production hashes are distinguished.
- [ ] Skill item consumption participates in `READY_NOW`.
- [ ] Positive item references validate against `ItemData`.
- [ ] Dead Goal 013 required-item validation is removed.
- [ ] No tactical suitability in catalog.

## Summon/equipment

- [ ] Cubic has no body commands or fabricated body state.
- [ ] Distinct summon variants and own factual evidence are preserved.
- [ ] Runtime body snapshot is immutable and sufficient for future controller.
- [ ] Pet/servitor/BabyPet/cubic/siege/quest distinctions remain.
- [ ] All matching owned equipment remains reachable through bounded paging.
- [ ] No universal equipment preference score/top-N truncation.
- [ ] Exact canonical equip remains safe.

## Progression truth

- [ ] Main/subclass/main isolation proven on canonical test Player.
- [ ] Certification/persistent skills are distinguished.
- [ ] No production class/subclass/Noble/certification mutation.
- [ ] Skill-learning success is exactly conservative.
- [ ] Cancellation/failure causes no partial SP/item/skill mutation.
- [ ] Event occurs only after success.
- [ ] Idempotency preserved.

## Architecture/lifecycle

- [ ] Facts remain separate from policy/doctrine.
- [ ] No central class switch or one-script-per-class.
- [ ] Game Knowledge production code/data unchanged.
- [ ] Persistence/scheduler/materialization/identity/shutdown unchanged.
- [ ] No new executor/thread/task/Future.
- [ ] Disabled behavior inert.
- [ ] No hot-path loader/file/DB scan.
- [ ] Unsupported future doctrine/mode has safe fallback.
- [ ] Canonical Player current/max CP are separate immutable snapshot facts.
- [ ] Controlled actors do not receive fabricated Player CP.
- [ ] No CP persistence, potion policy, PvP policy or reconciliation is added.

## Evidence

- [ ] Focused suites cover unique semantic cases.
- [ ] Production composition suite does not use inert Game Knowledge.
- [ ] Performance bounds pass.
- [ ] `ant verify` PASS.
- [ ] `ant jar` PASS.
- [ ] Verifier run twice, byte-identical, same SHA-256.
- [ ] Report complete.
- [ ] Roadmap records 013A pending independent review.
- [ ] Ordinary commit pushed to `origin/feature/phantom-world`.
- [ ] Success token `GOAL_013A_PROGRESSION_CAPABILITY_EXTENSIBILITY_HARDENED` printed only after all gates pass.
