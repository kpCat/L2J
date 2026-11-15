# Read-only аудит реалистичного поведения — Goal015

Статус на момент аудита: `PLAN_AMENDMENT_REQUIRED`. Впоследствии владелец отдельно утвердил PARTY_MP_POLICY_PLAN.md; общая MP policy исправлена, native3060/2806 проверен, итог PARTIAL015 в RESULT.md. Остальные findings/future требования аудита сохраняются. Ниже описан read-only проход до отдельного production approval.

Проверен SOURCE004: baseline `9a4495b9dbe24f53cfe7357d43be801fd2067512` и собственные незакоммиченные изменения015. Legacy арифметика groupViable и production result сохранены. Новый `previewRecoveryMp` пока имеет только объявление: вызовов из Java/test runtime нет. Работа не отменялась, бюджеты не сбрасывались.

## 1. Что означает текущий MP forecast

Факт подтверждён в `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java:650–652`:

- contribution участника — минимум damage-per-second среди selected навыков;
- interval — минимальный интервал среди них;
- стоимость — максимум MP на действие;
- затем общий DPS определяет длительность, `requiredActions` округляет число действий вверх и `actions * cost` сравнивается со строгим80% текущего MP (`:679–682`, `:744–748`).

Экстремумы могут относиться к разным навыкам. Это консервативный прогноз существующей policy, а не доказанная нижняя граница затрат реально выбираемой последовательности атак. Точное воспроизведение этого вычисления через общий canonical источник сохраняет authority, но не превращает оценку в физическое доказательство невозможности боя.

Дополнительно `L2jPhantomBackgroundAuthority.java:408` берёт maximum базовой и native-modified MP-стоимости. Если native эффект уменьшил цену ниже базовой, этот forecast сохраняет базовую цену. Масштаб завышения для конкретного roster без полного сравнимого native trace не измерен.

Исторические required3060/fullMp2806/allowance2244.8 доказывают конкретный `WAIT_GROUP_MP`014. Они не доказывают, что первый реальный cast незаконен или что бой нельзя начать и затем проиграть/прервать. Наличие общего private observer и зелёного арифметического теста015 этого вывода не меняет.

## 2. Что фактически учитывается

| Фактор | Native исполнение | Party MP forecast |
|---|---|---|
| Soulshots/Blessed Spiritshots | Реальные inventory items и native item handlers; фактический charge потребляется штатным combat. `Formulas.java:761–763` применяет Soulshot boost; `:961` применяет `bss ? 4 : sps ? 2 : 1` к MAtk перед native magic formula. Это условные native коэффициенты, не доказательство наличия shots у конкретного TRAIN actor. | `nativePlanningCombat` читает stats, но не shot charge. `estimateCastCycle` использует plain-hit calculation без shot multiplier. Поэтому уменьшение числа атак за счёт реально оплаченных shots здесь не отражено. |
| Текущие buffs | Native getters содержат уже применённые equipment/passive/buff modifiers (`L2jPhantomBackgroundAuthority.java:405–410`). | Эти текущие buffed stats входят в forecast. Не добавляется отдельный предполагаемый buff. Но expiry/возможный последующий rebuff не моделируются по времени боя. |
| MP regeneration | Реальный native regen существует; его наблюдаемая скорость попадает в CombatFacts (`:410`). | MP check groupViable не использует эту скорость и не вычитает восстановленный за бой MP. Учитываемый здесь regen — HP regeneration цели, который уменьшает effective DPS. |
| Recharge саппортом | `PhantomPartyTactics.java:110–112` предлагает RECHARGE при MP получателя≤35%; actual support проходит learned/target/Party/MP/reuse/conditions и `Player.useMagic` в `L2jCombatBackend.java:1779–1853`. Dispatch ещё не равен завершённому эффекту. | MP recharge не включён в MP budget. Возможность каста не считается полученной маной. Необходимо отдельно различать наличие актёра, готовый навык и фактический эффект. |
| Поддерживающий pet | Существует native BabyPet path через принадлежащий control item и `SummonItems` (`L2jCombatBackend.java:1923–1949`). Native feed/owner/MP/cooldown сохраняются. | groupViable работает с Players и не даёт pet recharge/DPS credit. Learned capability и наличие pet control item не доказывают активного питомца или выполненную поддержку. |

`PhantomBackgroundModel.java:476–491` также показывает принцип plain forecast: стоимость всех casts проверяется против начального MP до симуляционного цикла. Это не эквивалент native legality одного действия.

Будущие эффекты нельзя объявлять существующими. В частности, BabyPet recharge требует combat stance владельца, MP<60%, достаточных ресурсов/готовности питомца и случайного условия (`model/actor/instance/BabyPet.java:360–366`). Такую поддержку нельзя заранее гарантировать численным MP credit.

## 3. Обязательные shots20+ пока не обеспечены

В `PhantomNativeShotControl.java:66–87` grade соответствует weapon crystal type, а blessed предпочитается. Однако `charged` принимает также обычный SPIRITSHOTS (`:153–160`). `PhantomNativeWorkScope.java:205–226` при PvE допускает UNAVAILABLE: результат должен отличаться от FAILED; строгий ACTIVATED требуется для player combat. Отдельного обязательного условия20+ здесь нет.

`PhantomCombatPolicy.java:43–50` проверяет запасы shots только в специальной ветке рискованного Solo40+, с дополнительными условиями76+. Это не требование постоянного использования Soulshots/Blessed для всех фантомов20+ и Party.

Оплачиваемое снабжение существует: merchant offer, стоимость, Adena, вес, возврат и native BUY receipt (`commerce/L2jPhantomSupplyJourney.java:965`, `:1194–1211`). При нехватке денег есть income_farm. Но обычный Spiritshot stock может подавлять blessed-закупку (`:517–520`), а закупка допускает fallback blessed→обычные (`:774–775`). Требование владельца «магия только Blessed» этим не закрыто.

Нельзя приписать всему existing product flow отсутствие бесплатных предметов: Journey вызывает starter service (`:399`). `PhantomStarterShotService.java:48,84` запрещает новое entitlement20+, но допускает продолжение ранее начатого лимита; `:101` выдаёт REWARD/STARTER_SUBSIDY, только для подходящего NG оружия (`:125–131`). В015 новых выдач не было. Отключение/неиспользование этого пути в будущем конкретном запуске нужно подтвердить отдельно; историческую механику и старые данные здесь не меняли.

## 4. Законность действия и решение о риске различаются

Native `Creature.checkDoCastConditions` требует MP для текущего каста (`Creature.java:2236–2246`), а также HP/статус, оружие, предметы и остальные native условия. `L2jCombatBackend.java:1465–1504` перед настоящим cast повторяет target/learned/current native checks и передаёт действие native AI. Никто не должен получать фиктивный MP или обход этих условий.

groupViable применяет иной по смыслу фильтр: приблизительный MP budget целого боя и80% reserve. Его отказ — отказ текущего Phantom admission policy. Превращать его в новый безусловный запрет законно начать рискованный бой противоречит уточнению владельца.

Для Solo похожий policy слой находится в `PhantomEngagementPlanner.java:41–53`: вызывается forecast viability, включая весь cast budget. Поэтому проблема разделения legality/risk касается и Solo; она не решается переименованием только нового Party preview.

## 5. Правило трёх сопоставимых смертей не найдено

В проверенных phantom death/recovery/planning/decision routes нет подтверждённого контракта: три смерти в сопоставимом контексте → обязательная смена mob/Party/support/supply.

- `PhantomOrdinaryDeathRecovery.java:316,346`: observedDeaths — pending recovery map; завершённая запись удаляется. Это не накопительная история поражений.
- `PhantomBackgroundService.java:2171`: после town return возможен fresh admission прежней цели.
- `PhantomDecisionEngine.java:880,930–939`: общий retry/replan ограничивает отдельную работу, но очищает план и обнуляет attempts; сравнимый опасный контекст не исключается.

Это пробел гарантии, а не доказательство фактически бесконечного death-loop: новых боёв/смертей015 не было. Optimizer stagnation и timeout counters не заменяют индивидуальную историю поражений в игровых условиях.

## 6. Восстановление и реальная поддержка

Solo runtime использует настоящий deficit и regen при расчёте ограниченного ожидания, затем native sit/stand и повторные readiness checks (`PhantomVisibleAutoPlay.java:448–501`, `PhantomCombatPolicy.java:60–74`). Не найдена отдельная оценка полезности фарма с учётом длительного MP простоя и сравнения оставшегося buff lifetime с recovery+fight. `PhantomNativeCombatCapability.java:122–132` видит реальный buff с remaining>10сек либо постоянный, но не доказывает, что он переживёт весь recovery/бой.

В Party `supportPresent` означает живого участника, MP≥15% и learned supporting capability; readyNow не обязателен (`PhantomAutonomousFarmParty.java:1046–1048`). Это не обещание конкретного каста. Реальные heal, recharge и maintenance имеют собственные native проверки. `PhantomSupportEffectAuthority.java:83–99` проверяет настоящий BuffInfo/уровень/оставшееся время, а `PhantomPartyTactics` обрабатывает missing/near-expiry/healthy и MP expense policy. Этот рабочий maintenance не переписывался.

Текущий TRAIN source contract отвергает сохранённые pets/character_summons (`test/.../PhantomTrainingActors.java:209–210`). Поэтому нельзя объявлять full pet-supported TRAIN по одной capability или наличию control item.

## Конфликт с прежним планом015

Предыдущий план хотел назвать результат общего MP forecast доказанной невозможностью. Для существующего консервативного groupViable такой смысл слишком сильный: в нём нет полного учёта реально оплаченных shots и поступления MP, а minimum DPS/maximum cost не являются доказанным minimum расходом реального боя.

Новый прототип не подключён к планированию. Его `PROVEN_IMPOSSIBLE` нельзя закреплять как verdict игровой законности. Консервативный global effects guard также не решает задачу реализма: canonical DB-free catalog обнаружил10482 NPC templates,1209 BUFF facts и232 unassessed template/skill/target-type combinations. Это граница знания guard, не232 доказанно опасных для каждой сцены источника. Отбрасывать из-за неё всех кандидатов было бы неверным product outcome.

Минимальная поправка вынесена в `PLAN_AMENDMENT_REALISM.md`. Реализация mandatory20+ shots/Blessed, изменение subsidy и death-context strategy затрагивает production пути за пределами exact allowlist015 и не выполняется без отдельного согласования.

## Сохранность и уровень доказательств

Источник выводов — код и выполненные DB-free contracts/catalog, не новые native эпизоды. После уточнения владельца source changes не выполнялись. Readiness015=0/2, wall0/1200000ms; GS0/3, native0/3600000ms, CUT0, FIT0/1, EVAL0. Предыдущие бюджеты014/013 сохранены. Selected14/terminal Adam/pointer12 и183 SQL rows уже проверены G0; никаких новых learning rows, grants или promotion015 нет.

Git-команды в данном read-only аудите не выполнялись. Ранее в015 использовались разрешённые bounded inspection/fetch/status/exact diff-stat; commit/push пока отсутствуют. Полный журнал текущего процесса — PROGRESS.md; финальная публикация и G8 ещё не выполнены.
