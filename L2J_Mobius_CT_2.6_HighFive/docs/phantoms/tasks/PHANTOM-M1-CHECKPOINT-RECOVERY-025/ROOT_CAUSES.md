# Проверка coordinator: факты отдельно от гипотез

Проверен remote HEAD07df36d…, отчёт/COHORT/HANDOFF/FIX_LEDGER и нижеперечисленный source.

## RC1 — доказанный cold recovery routing gap
`HistoricalBackgroundService.ensureBaseline` обрабатывает VERIFY_PENDING только вместе
с retained materialization и закрытым action admission. После process crash такого
in-memory owner нет. Ветка MATERIALIZED вызывает recoverAbandonedMaterialization,
но та сама принимает только MATERIALIZED. Дальше VERIFY_PENDING попадает в
`catchup.baseline.conflict`, и полезное восстановление становится REPLAN failure.

При этом `PhantomBackgroundTransaction.reconcileVerifyPending` уже начинает с проверки
существования `background.owned-store`, вызывает `resolveOwnedStore` под locks и commit.
Проблема доказанного452 — достижимость существующего resolver, не отсутствие нового журнала.
После AFTER_NATIVE его canonical bytes точны, но pending не завершён. Receipt сохранён.

## RC2 — доказанный неполный исход live checkpoint; producer конкретного эпизода UNKNOWN
FinalSceneA/profile110: ACTIVE, owner=SEALED, actionAdmissionOpen=true,
pendingOwnedStore=false, failure/incident пустые, nativeOutstanding=0,
travel.arrival_capture_pending, last reason step.retry_exhausted. REGEN истёк позже.

`PhantomNativeWorkScope.checkpoint`: OPEN восстанавливается лишь при successful=true;
при exception до PREPARE pending может отсутствовать, failure тоже, checkpointThread
сбрасывается. Существующий resume требует pending receipt — обычный ActionLease закрыт.
`PhantomBackgroundService.captureVisibleArrival` скрывает RuntimeException как false.
`PhantomVisibleFarmTravel.arrive` превращает false обратно в arrival_capture_pending.
Такой код допускает вечное зависание без владельца продолжения.

НЕ утверждать, что уже известен first producer. Зафиксировать его в новом коротком
probe, сопоставить с timeline; только затем исправлять его конкретные условия.

## RC3 — повторная синхронная зависимость, причина timeout не установлена
В TASK024 exact RESERVED ON_ATTACKABLE_KILL для752 превысил drain deadline; последующая
штатная попытка завершилась. Не доказано, было ли это future due time, блокирование
монитора, очередь с занятыми workers, instrumentation, либо неправильная accounting.

Проверить reserve/publish/due/start/finish/ticket parent, очередь и locks. Не отменять
earned task, не повышать timeout, не использовать прямой run() ради GREEN. Таймер,
который законно ещё не должен выполниться, не может считаться потерянным callback.
Контрольная операция не должна занимать все workers/мониторы, нужные этому callback.

## RC4 — диагностический debt, а не разрешение переписать evidence
Scope.nativeObservationHealthy() не проверяет sensor overflow, а только failure/pending.
Поэтому нельзя утверждать, что именно overflow остановил110. `PHASE_DEADLINE` может
быть последствием уже SEALED owner. Старый overflow остаётся в отчёте, не сбрасывается.

## RC5 — optional producer, проверять после RC2
Authority.requireSupportedPlayer(nativePersistence=true) всё ещё отвергает isInCombat
и другие simulation-limits. Это возможность read-only отказа до capture; наличие
исторического combat flag не тождественно outstanding native work. Если доказан
этот producer, либо безопасно отложить ДО закрытия admission, либо capture под exact
quiescent owner с отдельным native-only eligibility. Не применять blanket return(true).

## Не переоткрывать автоматически
TASK024 fresh original action admission, off-anchor XYZ policy, topology stop order,
local polygon/route exclusion. Их регрессии сохраняются. Ни один профиль/координата
не hardcoded в продукт. Наличие ошибочного sample само по себе не доказывает rollback.
