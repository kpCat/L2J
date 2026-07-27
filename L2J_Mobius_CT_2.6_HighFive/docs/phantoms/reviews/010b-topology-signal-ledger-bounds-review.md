# Независимое review Goal 010B

## Verdict

```text
Goal 010B bounded architecture: ACCEPT
Goal 010 overall: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010C: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

## Проверенный baseline

```text
Commit: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Parent: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Branch: feature/phantom-world
Push/remote: exact
Signal ledger: 20/20 ×3
Generation: 17/17 ×3
Perception: 28/28 ×3
Core: 38/38 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Navigation and shutdown regressions: PASS
Final verifier: 85/85 ×2, byte-identical
External verifier SHA: abbreviated handoff only
ADA98158...25CCA
```

Один bounded fixed-source ledger на profile, общая capacity для active,
retained и cleanup identities, monotonic sequence ownership, never-owned
inactive zero allocation и all-three scheduler `NOT_REGISTERED` release
приняты. Topology XML, generation ordering, loaders и scheduler semantics
сохраняются. Revert не требуется.

## Обязательный finding Goal 010C

Реальный `PhantomScheduler.withdrawSignal` возвращает `STALE` как для старой
sequence, так и для отсутствующего source. Свежий topology ledger начинает
source в `NEVER_SUBMITTED`, но реализация Goal 010B принимала `STALE` только
после `INACTIVE_CONFIRMED`. Поэтому обычный unregister или reload до первого
perception event навсегда переходил в cleanup-pending.

`NEVER_SUBMITTED` является локальным доказательством inactivity только для трёх
эксклюзивно принадлежащих `PhantomPerceptionProvider` source keys. Goal 010C
должна принимать `STALE` из `NEVER_SUBMITTED` и `INACTIVE_CONFIRMED`, переводить
source в `INACTIVE_CONFIRMED`, но не считать scheduler отсутствующим и не
освобождать ledger. `POSSIBLY_ACTIVE` и `OWNERSHIP_UNCERTAIN` остаются
fail-closed.

## Требуемое закрытие

Goal 010C ограничена одним reconciliation condition и real scheduler adapter
tests. Scheduler implementation, topology generation/ledger structure,
XML/loaders, navigation, decision, config/schema и Goal 011/012 не меняются.

Goal 010C не может принять себя самостоятельно. До независимого verdict Goal
011 остаётся `BLOCKED`, Goal 012 — `NOT_STARTED`.
