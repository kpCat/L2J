# Контракты состояний

## Native действие
NEW_NATIVE_ACTION → exact all-owner admission → native prechecks/MP/shots → EARNED publish
→ exact delayed continuation → native writer/finalizer → tickets complete.
Отказ до admission/publication ничего не зарабатывает. Ошибка после writer не ретраится
как новое начисление. Новая retaliation вложена в старый callback логически, но не обязана
принадлежать его target set. Delayed continuation никогда не приобретает чужой new epoch.

## Durable native / background projection
MATERIALIZED + OPEN → close admission → earned drain → SEALED
→ PREPARE(exact actual snapshot + eligibility)
→ native canonical store → FINALIZE
→ READY/DEAD + (SUPPORTED | VITALITY_REQUIRES_NATIVE | POSITION_REQUIRES_NATIVE)
→ release live owner.

POSITION_REQUIRES_NATIVE не INCONSISTENT и не permission к virtual farming. При REAL/synthetic
local demand rematerialize exact savedXYZ → bounded local native work → valid arrival capture
→ eligibility re-evaluated. Старая неподходящая позиция не «исправляется» database teleport.

## Shutdown
QUIESCE_PRODUCERS → DRAIN_EARNED → STORE_AND_POST_STORE → RELEASE_LIFETIMES
→ STOP_DEPENDENCIES → STOP_POOLS.
Повторный вызов продолжает ту же фазу. Если STORE_AND_POST_STORE не завершилась, topology
не объявляется выключенной заранее; если DB исход неизвестен, owner не подменяется.

## Farm
FARM_AREA → SEEK → NATIVE_APPROACH/ATTACK → REWARD/LOOT → SEEK.
PATH_FAILURE → bounded route replan (не вечный запрет FARM_AREA).
AREA_UNSUITABLE → atomic local area replan.
NO_FEASIBLE_AREA → bounded rest/return с concrete reason; не бесконечный start_retry.
