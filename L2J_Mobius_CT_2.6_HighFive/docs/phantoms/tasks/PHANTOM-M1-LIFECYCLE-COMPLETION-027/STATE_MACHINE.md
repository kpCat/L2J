# States / outcomes

## Stop attempt (in-memory, один на configured instance)
```
NONE -> CLOSE_PRODUCERS -> WAIT_ACCEPTED_CONTROL -> DRAIN_PLAYERS
     -> FINISH_DEPENDENCIES -> COMPLETE
                    \-> FAILED(reason, first blocking phase, exact counters)
```
PENDING не terminal failure и не COMPLETE. Контрольный deadline установлен один раз.
Повторная заявка тому же instance получает тот же attempt, не второй процесс сохранения.
Новый configured instance после restart не наследует old in-memory attempt.

## Native callback (существующий ownership остаётся)
```
RESERVED -> SUBMITTING -> SUBMITTED -> EXECUTOR_ENTERED
         -> TICKET_RUNNING -> BODY_EXIT -> COMPLETED
```
EXECUTOR_ENTERED — diagnostic событие до acquisition; оно не разрешение выполнять body.
Rejected-before-publication и body-threw разные причины. RESERVED/EARNED не отменяется
из-за истечения deadline stop attempt. No replay. Старый epoch -> no write to replacement.

## Death / return
```
DEATH_OBSERVED (до dead flag) -> DEAD_CONFIRMED -> CORPSE_WAIT
 -> NATIVE_RETURN -> STORE_PENDING -> READY
 -> LOCAL_DEMAND -> MATERIALIZED(new exact epoch) -> FARM_RESUMED
```
Cold DEAD: admission через existing durable/native recovery, без подделки события.
Shutdown в середине: finish accepted earned persistence, не новый ordinary materialization.
Мёртвый/отсутствующий actor не отбрасывается из исходной группы.

## Invariants
1. Shared pools/DB/index не закрыты, пока нужен принятый writer и stop budget не истёк.
2. Timeout — diagnosed failure, а не разрешение присвоить COMPLETE.
3. Native store начинается только после exact quiescence; finalized snapshot сравним с SQL.
4. DRAINING/SEALED не открываются просто потому, что work count стал0.
5. Sensor counters/overflow не управляются verifier-ом.
6. Восстановление не удваивает ни rewards, ни penalties, ни stored plan revision.
