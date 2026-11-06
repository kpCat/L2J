# Phantom World / TASK129 — независимый статический аудит патча

Дата ревью: 2026-11-06. Основа: `TASK129-review.patch` (UTF-16LE, локальный diff). Сопоставление с опубликованной веткой `kpCat/L2J feature/phantom-world`: remote HEAD `faec72161f9e4f6231764e17d6f2637a4db080af`. Локальный `5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe` ещё не опубликован. Это **статический** анализ: локальные JAR/TEST129/SQL/GameServer не выполнялись; нативные факты известны лишь из отчёта Codex.

## Масштаб

28 файлов: 20 production (+1501/−66 строк), 7 tests (+1300/−7), 1 INI (+29). Всего +2830/−73. Есть реальная реализация настроек, durable offer/consent/rendezvous CAS, изменения в Background/Presence, human follow, целевые проверки. Это не «ноль кода», но полноценный игровой цикл не доказан.

## Основные находки

### F1 — P1, фундаментальное несоответствие: требуется native Player до переговоров

`PhantomAutonomousFarmParty`: `rendezvousPulse` получает `page` из `materializedFarmMembers()`, а `_discovered` заполняется только такими `MemberSnapshot`. `L2jPhantomPartyBackend.materializedFarmMembers` лишь пытается materialize максимум один из scheduled profiles и возвращает активных. `PhantomPartyRendezvousState.Participant` требует `characterObjectId>0` и `epoch>0`; `invalidOffer` и `currentOffer` требуют неизменного materialization epoch. `PhantomHistoricalBackgroundService.previewVisibleRendezvous` требует `MATERIALIZED`, `worldPresent`, active native lease. Итого durable оффер — это **соглашение физически материализованных**, а не логическая партия 5000 профилей. При дематериализации одного актёра оффер теряет действительность. Эта архитектура не удовлетворяет целевой модели пользователя «логическая группа, затем materialize при необходимости».

**Решение:** persistent logical GroupIntent/roster/roles/goal independent of objectId/native epoch; short-lived PhysicalGroupSession binding with current objectIds, native Party membership only during physical phase. При переключении World↔Background сохранять логическую группу, native Party законно пересобирать; никаких ghost members в штатной Party.

### F2 — P1, ложные отрицания географической достижимости

`PhantomAutonomousFarmParty.SourcePreview.sources()` обрезает `candidates().distinct().limit(4)` **до** `reachable`/проверок полного состава. `PhantomHistoricalBackgroundPlanner.rendezvousSources()` сам генерирует максимум четыре варианта. `rendezvousRouteAvailable` требует готового пути из `committedAnchor` в `farmAnchor` в графе; `rendezvousRouteAbsent` трактует отсутствие такого пути как структурный отказ. При этом существующий `PhantomVisibleFarmTravel` умеет `idleIngress` — WALK к доступному входу графа. Из логов Codex уже известен конкретный вариант с WALK ~5.2k единиц. Возможный реальный маршрут отвергается **до** native-планировщика. `terminalSourceLoss` может отменить оффер на таком ложном отказе.

**Решение:** один canonical travel preview с результатом `REACHABLE | REACHABLE_VIA_INGRESS | TEMPORARILY_BLOCKED | DEFINITIVELY_UNREACHABLE | UNKNOWN`; полный composite route (включая ingress) должен участвовать в ранжировании. Нельзя делать false `routeAbsent` лишь по graph-only.

### F3 — P1, кандидатов фильтруют ПОСЛЕ ограничений выборки

В `rendezvousPulse`: будущие участники сортируются по support/profileId и `.limit(MAXIMUM_CANDIDATES - 1)`, затем `removeIf(... !sourceAdmitted ...)`. Если первые 15 не имеют общего маршрута, а 16-й кандидат имеет — он вообще не проверяется. Независимый cap `leader.limit(4)` и top-4 sources ещё ухудшают покрытие. Особенно опасно при 5000 durable identities и 128 scheduled.

**Решение:** fair paged bounded поиска по eligibility+reachability без раннего усечения по произвольному ID; cache/indices и детерминированный тест «первые 15 недоступны, 16-й подходит» и «первые 4 anchors недоступны, 5-й возможен».

### F4 — P2, согласие группы частично транзакционное, recovery всё ещё привязан к материальности

Оффер сохраняется отдельным `saveRendezvous` CAS для каждого участника, нет единой атомарной операции на группу; в случае прерывания используется best-effort cancellation/TTL. `_offers` — in-memory map лидеров; восстановление офферов вызывается при переборе **materialized** page, а не общим durable index. Нет подтверждённого recovery логической группы с нулём materialized. `currentOffer` требует текущих native actors/epoch. При падении/долгой дематериализации возможны зависшие до TTL частичные резервы или отмены, а не «фоновая Party».

**Решение:** aggregate groupId+generation/leader+roster with idempotent coordinator saga; durable bounded recovery by group intent; explicit compensate/replan и смена physical epoch без потери logical identity.

### F5 — P2, молчаливое подавление ошибки в ключевом scheduler

`PhantomAutonomousFarmParty.rendezvousPulse`: `catch (RuntimeException exception) { return false; }`. Нет error code, trace, счётчика и state-specific rejection. Неправильный SQL/CAS/Null/state exception становится тихим «нет партии», что воспроизводит описанную многодневную диагностику.

**Решение:** bounded structured diagnostic with error-class, profile/group/phase (без секретов), counters, suppression rate; неизменный fail-closed admission.

### F6 — P2, поддержка проверяется с правилами одиночного боевого фарма

`PhantomHistoricalBackgroundPlanner.replanVisibleRendezvous(... supportTravelOnly)` умеет bypass solo combat feasibility для support travel после принятия оффера, но `rendezvousSources(...)` для предварительного отбора всегда использует `PlanningCombat.from(state)`. То есть healer, которому нужен лишь безопасный путь для поддержки, может быть отсечён ещё до выбора общего источника. Нужна role-aware preview, но без выдачи ему права fake damage/EXP.

### F7 — P2 (требует подтверждения), риск ослабления receipt/hash guard

`PhantomBackgroundTransaction.offscreenReceiptHashCurrent`: возвращает `true` для **всех untagged receipt**, даже если там непустой `expectedAfterHash`, не совпадающий с актуальным. Прежняя проверка отвергала такой конфликт. Не утверждается реальный exploit: нужно доказать, что до этого метода с `OffscreenProof` невозможно дойти при untagged state, либо вернуть строгий fence. Это изменение связано с сохранностью gameplay и не должно публиковаться без отдельной проверки.

### F8 — P2, наблюдатель Heal затрагивает общий игровой эффект и не доказывает MP debit

`handlers/skill/effects/Heal.java` теперь условно изменяет выполнение общего HP-writer, добавляя synchronized status/observer; влияние на обычных игроков должно быть отдельно проверено. `PlayerNativeEvidence.nativeHealWritten` фиксирует HP before/after и **одно конечное значение MP**, а не MP до/после; значит он сам по себе не доказывает расход MP. Тест 022 вызывает метод `healed` напрямую, а не делает полноценный native Heal cast. Рекомендуется минимально инвазивный observer у существующего native event/write, точное before/after MP из корректного источника и native E2E.

### F9 — P2, продуктовые Party 2–9 пока не поддерживаются автономным поиском

`PhantomPartyRendezvousState` допускает 2–9 участников, но `PhantomAutonomousFarmParty.selectRendezvousTeam` фактически требует минимум 3 (DD+support+DD), добавляет максимум ещё одного guardian. `invalidOffer` отвергает `<3`. Кодек `plannedProfiles` прежней Party-цели допускает лишь 2–5. Значит заявленная общая модель Party из 2–9 сегодня не сквозная. Для TASK129 обязательным было именно Party3, но для будущих кланов/гангов такая структура приведёт к новой переработке. Отдельный выбор `NPC@anchor` для всех участников также смешивает общий спот с индивидуальной боевой пригодностью healer.

## Что сохранить

1. `PartySeekingSettings`: пороги 0/20/35/50/80, стабильный hash profileId, корректная семантика поиска (не per-tick RNG).
2. Идею долговечного CAS-согласия, TTL, epoch checks и штатного `formForGoal`/invite/accept; нужно отвязать **логическую** фазу от physical lifetime.
3. Безопасную интеграцию typed native-context demand, не используя чужие journey-permissions; сохранить после независимых регрессий.
4. `navigationAnchor`: follow single REAL без передачи официального Party/Clan authority; реальная mixed-human сцена всё ещё не доказана.
5. Тесты как основу, но не путать Mock backend with actual GameServer, Geo, rewards and restart.

## Следующие действия — без новой многодневной кампании

**Не публиковать автоматически весь локальный HEAD до аудита F1–F9.** Заморозить `5cb04fe...`, сохранить 12 коммитов и отдельные черновики. Выполнить дешёвый deterministic vertical prototype без JAR: (a) 3 ненативных durable профиля создают persistent logical Party; (b) survive restart без единого World Player; (c) only materialization request привязывает current physical epoch; (d) при уходе человека Party возвращается в background без потери логического состава; (e) double-join, stale epoch, capacity, STOP/STORE. Затем union-route tests и отдельный native pilot. Не добавлять новую FULL-матрицу PvP/Clan/Olympiad до доказательства этой вертикали. Контроль бюджета — полезный progress milestone, а не ограничение ради ограничения.

**Ограничения оценки:** этот вывод по patch и старому GitHub baseline, не по живому локальному JAR/TEST129; чистота/компиляция runtime известны из отчёта Codex, но не воспроизводились в этом ревью.
