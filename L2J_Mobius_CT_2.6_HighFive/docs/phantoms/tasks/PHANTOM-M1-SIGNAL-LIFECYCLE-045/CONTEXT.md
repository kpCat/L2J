# CONTEXT045 — проверенные факты, не предположения

## Публикация

- Repo `https://github.com/kpCat/L2J`, ветка `experiment/m1-candidate007-observe008`, HEAD=`a6fde1df1b1785b93e2248a20786c61878e05428` при составлении пакета.
- TASK042 G2 A5 8/8 и C24 8/8 — реальный historical PASS на GameServer.jar `5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c`; при смене semantic JAR не переносить автоматически.
- TASK043 B2: 367 удерживался настоящими входящими native hits; законный hard hold. C1: 776 законно сохранился и вышел, но при return не доказаны background reward/useful native work. D1: 1189 после settled-away физически спокоен, retentionPins пустые, однако scheduler ACTIVE/activeSources1; sourceKey в старом raw нет.
- TASK044: C1 в 77 frames имел `SLEEPING`, `activeSources0`, `nextDue0`, без ordinary work due. Для **стандартного scheduler background work** первое due — через 300000–900000 мс, но отдельная ecology/catch-up очередь не обязана ждать именно этого cadence; определить точный producer, а не подменять их.
- TASK044 добавил read-only `PhantomScheduler.signalSources(profileId)` и регрессии. На дополнительном настоящем trace профиль73 показал только `human.local`, `NEARBY_PERCEPTIBLE`, TTL 10000, seq135–186 при оставшемся рядом observer. Away не состоялся. Исторический owner1189 остаётся UNKNOWN; source-only run не воспроизводил его ситуацию.
- TASK044 additional strict FULL_SAVE/GLOBAL_DRAIN PASS, original044 strict FAIL из-за сохранённого `STOP_MONITOR_TIMEOUT`; не переименовывать старый FAIL. 93/93, lifecycle11/11, soft-return3/3, scheduler28/28 были PASS, но новых полных G4 и current-G2 на E502 JAR не было.
- M1=OPEN; READY_FOR_M2=false; старый Zariche инцидент отложен пользователем.

## Новая гипотеза — не считать доказанным фактом

На текущем коде `PhantomPopulationManager.processReadySchedule` публикует `SCHEDULE_SIGNAL_SOURCE="population.schedule"` с требованием `entry._effectiveState`; при допущенном расписании оно может быть `ACTIVE`, TTL ограничен границей/часом. Отдельно `PhantomHumanLocalityControl` публикует `human.local` с `NEARBY_PERCEPTIBLE`, TTL10000 мс. `PhantomScheduler.requestedStateLocked` берёт самое высокодетальное требование среди источников; `ACTIVE` сильнее `NEARBY_PERCEPTIBLE/BACKGROUND`.

Следствие как **проверяемая гипотеза**: при отсутствии наблюдателя `human.local` истечёт, но независимый `population.schedule=ACTIVE` продолжит требовать физическую материализацию, хотя персонаж логически работает по расписанию и все native hard holds сняты. Это может объяснить D1, но **не доказывает**, что у D1 был именно этот ключ, и не объясняет C1 SLEEPING (там нет готового BG due).

Обязательные контрольные точки: `PhantomPopulationManager.processReadySchedule/effectiveStateLocked`, `PhantomPresenceRegistry.schedule/permitsOrdinaryFarm`, `PhantomScheduler.submitSignal/expireSignalsLocked/requestedStateLocked/transitionPlanLocked`, `PhantomHumanLocalityControl.onPulse`, `PhantomMaterializationRetentionPolicy`, `PhantomReconcileFirstActivityPort.dematerialize/materialize`, `PhantomPopulationEcologyService.updateMaterializationDemand/requestBackgroundDue`, `PhantomSystem` wiring. Проверить другие физические причины: topology.combat, topology.targetability, REAL_PARTY, active earned, native visibility, current physical demand.
