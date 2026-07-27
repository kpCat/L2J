# Независимое review Goal 010A

## Verdict

```text
Goal 010A generation/signal ordering: ACCEPT
Goal 010 overall: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010B: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

## Проверенный baseline

```text
Commit: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Parent: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Branch: feature/phantom-world
Push/remote: exact
Core: 38/38 ×3
Perception: 28/28 ×3
Generation: 17/17 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Navigation: 50/50 ×3
Shutdown: 7/7 ×3
Verifier: 82/82 ×2, byte-identical
Verifier SHA-256:
5751E0AEED65FB392D36CC66716DC985CE747F4801FDB2A99AA085CA5B72A802
```

Exact generation ownership profile update/event delivery, reload
re-resolution, invalidation-before-swap и unregister/event ordering приняты.
Topology XML, factual corpus, loaders, navigation, scheduler и decision
semantics сохраняются. Revert не требуется.

## Обязательные findings Goal 010B

1. Historical profile/source sequences хранились до final stop и росли по
   lifetime distinct profile IDs.
2. Failed cleanup tombstones находились в отдельном uncapped set и обходили
   registry capacity.
3. Inactive targetability для never-owned ID создавала sequence state и
   вызывала scheduler port.
4. Scheduler `STALE` не доказывает cleanup possibly-active source; он безопасен
   только при локальном `INACTIVE_CONFIRMED`.
5. Impossible submit `STALE`, `REJECTED` и `NOT_RUNNING` должны возвращать
   explicit signal ownership failure.

## Требуемое закрытие

Goal 010B ограничена одним capped per-profile ledger с тремя fixed sources,
общей capacity для active/retained/failed identities, truthful cleanup/release
и focused churn regressions. Она не меняет generation ordering, topology XML,
loaders, scheduler/navigation/decision semantics, config/schema и не начинает
Goal 011/012.

Goal 010B не может принять себя самостоятельно. До независимого verdict Goal
011 остаётся `BLOCKED`, Goal 012 — `NOT_STARTED`.
