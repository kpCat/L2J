# CONTEXT043 — точная отправная точка

## Принятые доказательства042 (не переписывать)

- Final delivery HEAD=`9a378d0e8984b3673b41538a9f5f9c9823507317`. G2=P2 PASS: A5 восемь из восьми за382.3743138s; C24 независимая группа восемь из восьми за381.6816272s. Игровой Java/config/data в TASK042 не менялся; тот же JAR/evaluator, historical C8 FAIL сохранён.
- Profile276 (object268487174, epoch590209020958800) после ухода из locality законно завершил native earned и ушёл в DETACHED; `LEGAL_SOFT_RELEASE`, а не потеря AI. Сохранять исходный C8 `CONTINUITY_V2 FAIL` — нельзя объявлять исправлением новой метки.
- B2 G4: заранее selected profile367/object268487935/epoch657223077372300, перед уходом реальный earned cycle/reward/damage, EXP+20/SP+2. Synthetic observer native MOVE20 шагов на6000 запад; after arrival physicalDemand/prewarm=false. `RECENT_HUMAN` истёк около32.79s, затем профиль оставался в `ACTIVE_ACTION` из-за настоящих **входящих атак**. HP:180→179.71→133.82→81.94→20.06 за122.63s. `SOFT_RETIRE_BOUND120` FAIL, до background/return сценарий не дошёл; смерть не зарегистрирована. Выдумывать «вечный stale pin» нельзя.
- `PhantomMaterializationRetentionPolicy.Facts.nativeFacts()` относит `isMoving/isAttackingNow/isCastingNow/isTeleporting/isInCombat` и admitted actions к `ACTIVE_ACTION`. `Hard()` истинно при ACTIVE_ACTION, REAL_PARTY, NATIVE_VISIBLE. `permitsNewActions()` после expiry без человека/party/visibility=false. В `PhantomVisibleAutoPlay.Policy.acquireTick()` при `!_permitsNewRoots` уже пытается останавливать новые native intentions (stock IDLE без отмены earned tickets). Это гипотезы границ — проверить на B2 actual timestamps и source прежде чем менять.
- `PhantomSystem.retentionFacts()`, `nativeContextDemand()` и `_reconcileMaterializationActivity` — соседний фактический owner: `PhantomSystem.java` около строк615–627,2908–2939 на snapshot042. Actor **нельзя** освобождать при live earned/active incoming combat по одному истёкшему таймеру.
- P4 conservation TASK042 не глобальный PASS: A1 collector incomplete после Windows FileShare; A2 FULL_SAVE true, но без typed global DONE. A3/A4/A5/B1/B2 strict full-save/global-drain PASS; B2:434 births/31009 commits. Старые gaps неизменяемы. Новая043 должна иметь **собственный complete normal stopped cut** для своей объявленной final positive lane, но не переписывать P4 historical.
- jar-first93/93, lifecycle11/11, soft-return3/3, debt2/2, action5/5 пройдены в042; при отсутствии product changes текущие проверки переиспользовать только с точным SHA/evaluator и повторять лишь затронутые tests.

## Неприкосновенное

LIVE040a: Game PID30312, Login18688 на последнем подтверждённом источнике042, проверить read-only startTime/PID incarnation; НЕ предполагать что PID достаточно. MAIN/PLAY и все прежние041/042 БД+raw read-only. Не импортировать из живого040a и не изменять его конфигурацию.

Старые041: native-goal after-commit fixes и самостоятельная поставка уже находятся в tracked Git; итоговые G3/G5/G6 всё ещё открыты. Zariche `DEFERRED_BY_USER` — не чинить. Нет оснований начинать М2.

## Исходная гипотеза, а не готовый диагноз

Активные атаки мобов могут законно удерживать фантома и после истечения soft locality. Следует разделить: (А) продолжают ошибочно запускаться **новые** native roots без спроса, (Б) выполняется earned work, (В) игрок является мишенью и получает новые incoming hits без новых outgoing roots, (Г) после окончания всех in-flight/hard reasons retention остаётся stuck, (Д) фон юридически недоступен по goal/catchup. Только A/Г с причинной демонстрацией могут требовать product-fix; B/В сами по себе не доказывают defect.
