# Независимое review Goal 007A — scheduler transition ownership hardening

## Verdict

```text
Reviewed commit: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Parent: 9958edd9e133557f4966eed0a4124e68326401b3
Goal 007: ACCEPT after Goal 007A
Goal 007A: ACCEPT
Revert: NOT_REQUIRED
Bounded Goal 008 follow-up: cleanup retry uses current requested state
Goal 008: ALLOWED
Goal 009: NOT_STARTED
```

## Проверенные доказательства

Независимо приняты scheduler ownership, retained cleanup truth и stop
quiescence. Commit является одним ordinary child требуемого parent и совпадает
с `origin/feature/phantom-world`.

```text
Scheduler: 17/17 ×3
Scale: 2/2 ×2
Scale SHA-256: 67B7FC26B98141661890DFAAE5F307B86BB5C768EA82A2DF6A8D1F1556F7EE30
Production: 20/20 ×3
Shutdown: 5/5 ×3
Verifier: 63/63 ×2
Verifier SHA-256: D0F1BBD00C96AE180BA7D96A9B808F20C18467A2F996183CBBD9E559702C78A1
```

Review подтвердило:

- physical slot removal не происходит при `processing`/`boundaryInFlight`;
- retained ownership не теряется из-за equality, withdrawal или TTL;
- cleanup success не публикует materialized state без fresh materialize;
- adapter использует фактическое lifecycle ownership;
- `finishStop` сохраняет in-flight state для следующего explicit shutdown.

## Bounded follow-up

Единственное не блокирующее замечание относится к Goal 008: если signals
меняются во время внешнего `retryCleanup`, scheduler после возврата обязан под
monitor заново вычислить current requested state вместо применения stale
target. Это не требует Goal 007B и не разрешает redesign scheduler.

## Gate

Goal 007 и Goal 007A закрыты. Goal 008 разрешена в domain-neutral scope.
Goal 009 не начата.
