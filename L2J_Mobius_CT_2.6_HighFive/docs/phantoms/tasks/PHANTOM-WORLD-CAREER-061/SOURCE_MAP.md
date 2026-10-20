# SOURCE_MAP061 — проверенные источники и ограниченный read-first

Ordinary whole24/E1069090:6 сам оплатил Power Strike3:1 у native trainer, SP92→42; skill3:1 найден в native БД. После возврата два native kills/rewards дали EXP2896→3314/SP42→52, SS11→4. STOP сохранил этот результат и самостоятельно выбранный3:2. Для21 отдельно доказан expired supply ACTIVE вне поля. Причинный Supply RED2 воспроизвёл недоступность terminal через Decision; первый RED имел ошибочный after-start TEST binding и сохранён отдельно. Исправление только в прежнем proposal: exact bound/matching ACTIVE child предлагается существующему executor для FAILED/cleanup, даже если истёк. Требуется уже существующий action lease/allowed; ownsAutomaticIntent по-прежнему не принимает expired, cold admission/pin не добавляются, deadline/стоимость/receipt guards прежние.

Финальный read-first20.10: ordinary23 доказал покупку11SS за88 Adena и возврат6, затем learning3:1 повторялся rev1/5/13 без SP debit. Прочитаны `PhantomNativeTravelActions.localWalkDestinations`, `PhantomNavigationService` (policy maximumLocalStraightDistance), `L2jPhantomLearningJourney` (native trainers/approaches/finite route), `PhantomAutonomousLearning.proposal/advance`, `L2jPhantomSupplyJourney` и текущие JourneyLifecycle native guards. Общий bounded slice: Learning/Supply сначала передают полную цель native PathFinding в действующем policy limit, затем прежние максимум17 checkpoints; время рассчитывается по actual MoveSpeed. Learning сохраняет route.context в прежнем intent и после компенсационного возврата не повторяет окончательно проваленную дорогу при том же native route context. Root revision/SP earnings/restart сами не снимают запрет; knowledge/policy/anchor/class/speed могут изменить маршрут. Legacy goals без нового поля совместимы. Это не новый AI, storage или timer. Expired supply finalization21 остаётся отдельным непроверенным ограничением, не замаскировано этим изменением.

Уточнение shots20октября: прочитаны `Creature.doAttack/beginCast`, `PlayerNativeWork.Owner`, `PhantomNativeWorkScope`, `PhantomNativeShotControl`, `L2jCombatBackend.cast`, `PhantomVisibleAutoPlay`, `L2jPhantomSupplyJourney`, `Summon.rechargeShots`, native `handlers/items/BeastSoulShot.java` и `BeastSpiritShot.java`. Beast handlers требуют Player и расходуют его inventory для pet/servitor; отдельная сумка servitor не authoritative. `Servitor.onSpawn/run` показал, что default lifetime0 TEST-слуги немедленно завершает его жизнь; это исправление входа, не игрового правила. Native combat stance после summon атак учитывается до следующего независимого входа. Общие дефекты: class-based supply stock, отключение противоположного auto-shot и reserve500 при кошельке215. Решение переиспользует native handlers/owner/Commerce, точный грейд и существующие finite budgets; новый AI/store/timer отсутствует. Перечень79 изменённых functional файлов от BASE до codeCommitda5efb8 — `CHANGED_CODE_FILES061.txt`.

Поправка20.10.2026 для общей автоматической карьеры: прочитаны native `model/actor/enums/player/PlayerClass.java` (parent/root/nextClasses и Kamael Inspector), `data/categoryData.xml`/CategoryData (роли), `Player.setPlayerClass/setNoble/rewardSkills/giveAvailableAutoGetSkills/storeMe`, `VillageMaster` (Inspector — особый subclass), `PhantomPopulationStore.verifyAndLink`, `PhantomAutonomousCareer`, `PhantomProgressionService`/`L2jProgressionBackend`, `PhantomOwnedStoreIntent`/`PhantomBackgroundTransaction.prepareOwnedStore`. Общий дополнительный barrier найден в receipt: разрешал только0→1/Q401; теперь general native edge подтверждается прежним owned-store и сохранённым `career.choice`/root/child. Обычный class handler и global AutoNoblesse не меняются.

Новый read-first вывод: класс выбирается из authoritative дерева и native категорий, не из пяти quest whitelist entries. `career.choice` — данные существующего ProfileRepository, `career.intent` — прежний дочерний owner, Progression — прежняя сериализация операций; отдельного AI/таймера/хранилища нет. Native AutoGet при большом подготовленном level gap может требовать более одного штатного прохода; ограниченная синхронизация достигает native floors без paid skill/SP grants. Историческая Q401 ветка и её доказательства остаются доступными, но production producer использует отдельную Phantom-only профессию.

Проверено при подготовке18.10.2026: remote branch `experiment/m1-candidate007-observe008` указывает на `18a5c70839a7b1a4f7ca2c57c717820b9c6c26db`. Локальное состояние Windows/БД здесь не измерялось. Пути ниже относительно High Five; диапазоны относятся к BASE, после изменений искать symbols.

## Что прочитано, что из этого следует
| Источник BASE | Проверенное содержание, не гипотеза |
|---|---|
| `docs/phantoms/reports/PHANTOM-LIVING-PROGRESSION-060.md` | Native life/supply/pet/area успехи, selected111; обе ordinary samples0; профессии/feed/disposal не завершены; сборка060STOPPED |
| `localplay/LocalPlayPilotService.java` (под java/org/l2jmobius/gameserver), `loadObservationOrigin`, `refreshObserverFocus`, строки660–765 | Пассивный origin из configured ordinary character; REAL отдельно; sampled World predicate1024→filter→16, population status30s. Причины zero из этого кода не следует |
| `phantoms/questinstance/PhantomQuestInstanceCatalog.java`, строки45–108 | Exact whitelist из5content включая `class.warrior-q401`, version1/source hashes. Добавление любой профессии толькоXML без обновления explicit validation не является готовым решением |
| `phantoms/questinstance/PhantomQuestInstanceService.java`, строки40–218,247–283 | CLASS_GOAL_TYPE class.transfer, namespace supported.content; validGoal и live ACTIVE; CLASS_PATH направлен в warrior(); квестовые стадии и0→1 уже обрабатываются |
| `phantoms/questinstance/L2jPhantomQuestInstanceBackend.java`, строки43–260 | locate по actual NPC/authoritative spawn facts; invoke exact script.onTalk/onEvent при fingerprint и canInteract; state наблюдается, не выдаётся adapter напрямую |
| Mounted `PHANTOM-LIVING-PROGRESSION-060.zip`, GOAL/AUTHORITY | Формат архива и шесть документов; наследуемая058t/058a authority, защита старых сред, accepted supply dependencies |

Основные связанные источники уже известны из060/предыдущего анализа, но не объявляются полностью отревьюенными здесь: `PhantomAutonomousLearning`, `L2jPhantomLearningJourney`, `PhantomHistoricalBackgroundService`, `PhantomVisibleAutoPlay`, `PhantomPopulationEcologyService`, `PhantomPopulationManager`, `PhantomSystem`, `PhantomMaterializationService`; прочитать actual нужные участки до изменения. Конкретная причина нулевой выборки и полный маршрут Q401 должны быть установлены исполнителем на собственной среде.

## Начальный рабочий read-set
1. RESULT060 + точные строки ACCEPTANCE060 по ordinary/sample, learned/supply и текущему FINAL111.
2. LocalPlayPilotService refreshObserverFocus/loadObservationOrigin/realClient.
3. PhantomSystem startup wiring/operatorStatus/onlineHumanPoints и вызовы ecology/materialization.
4. PhantomPopulationManager admission и PhantomPopulationEcologyService readiness/current-needed/nextDue — только ветви фактического отказа.
5. PhantomAutonomousLearning producer/proposal/bound/advance и current History consumer — reuse pattern для career, не cloning.
6. PhantomQuestInstanceService advance/warrior/validGoal/cleanup; PhantomQuestInstanceDecision.
7. PhantomQuestInstanceCatalog и exact XML, указанный в его загрузке из PhantomSystem.
8. L2jPhantomQuestInstanceBackend invoke/locate/observe и exact owners из catalog (Q401 и native village-master).
9. Ближайшие существующие native tests из Launcher по questinstance/class transfer и living060; mode/seed найти адресно по symbol, не угадывать название класса.
10. Build-Package/Runtime/текущий exporter, build.xml targets; соответствующие explicit058/060 ownership mappings.

Этот set не запрет открыть нужный caller. Расширять небольшими причинно связанными группами; не гонять поиск по десяткам старых задач. Два неудачных поиска имени — перейти к imports/реальному каталогу, не гадать имена файлов.

## Конкретные уже известные технические ловушки
- Существующий `PhantomQuestInstanceService` поддерживает конкретный Warrior flow, не все `CLASS_PATH`. Сначала этот реальный путь, дальше явное расширение без universal bypass.
- Native interaction не требует попасть в центр NPC. Использовать canInteract и legal approach с корректнымZ/Geo, shared navigation и его завершение. NO_PATH обратного пути не объявлять дефектом подхода к тренеру без чтения phase.
- Прежние fixtures ошибались классом лекаря, запасом зарядов/весом, WATER и stale test class после failed compile. Преусловия061 обязаны это исключить до длинного запуска.
- Каталоги/сохранённая память содержат hash/version/числовые коды: новый career content не должен отключать старый М3. Versioned migration известных inputs, не новое authority значение поверх неизвестного payload.
- Контекст TASK060 описывает ordinary n=0 без причинного доказательства. «Счётчик неправильный», «все спят», «cap мал» и «нужно ещё12часов warm-up» являются гипотезами до фактического среза.
- Тестовый sample current actors и client-known list — разные понятия. Наличие в World технически доказуемо без клиентского видео; от этого настоящая сеть/UI автоматически PASS не становится.

## Канонические ссылки для локального follow-up
- https://github.com/kpCat/L2J/tree/18a5c70839a7b1a4f7ca2c57c717820b9c6c26db/L2J_Mobius_CT_2.6_HighFive
- https://github.com/kpCat/L2J/blob/18a5c70839a7b1a4f7ca2c57c717820b9c6c26db/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LIVING-PROGRESSION-060.md

Публичные форумы не заменяют native quest/script truth этого репозитория. Внешний research не является обязательной подготовкой; новую зависимость/сервис не вводить. Если проектные инструкции отсутствуют локально, зафиксировать факт один раз, не фабриковать AGENTS.
