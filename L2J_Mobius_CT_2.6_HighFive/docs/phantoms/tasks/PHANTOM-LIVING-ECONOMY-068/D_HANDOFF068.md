# D — native equipment/craft, agent champions_f

## Exact scope и read-first

Четыре production-файла, два focused test-файла и этот handoff; пути относительно модуля High Five:

1. java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeEquipmentPolicy.java
2. java/org/l2jmobius/gameserver/model/skill/Skill.java
3. java/org/l2jmobius/gameserver/model/stats/functions/FuncTemplate.java
4. java/org/l2jmobius/gameserver/model/conditions/ConditionUsingItemType.java
5. test/java/org/l2jmobius/tests/phantoms/PhantomEquipment068PolicySuite.java
6. test/java/org/l2jmobius/tests/phantoms/PhantomEquipmentCraft068Native.java
7. docs/phantoms/tasks/PHANTOM-LIVING-ECONOMY-068/D_HANDOFF068.md

Shared fee и последнее unresolved NPC quote исправление относятся к E_HANDOFF068.md. Launcher equipment068-policy зарегистрировал родитель. Другие shared/merchant/Loot файлы этот агент не менял.

Read-first: инструкции root/module и task D/acceptance; NativeEquipmentPolicy, SupplyJourney/Loot/AutonomousCareer callers; progression backend/service/capability evaluator; native Skill/FuncTemplate/conditions, Item/Weapon/Armor, ArmorSetData/ArmorSet; RecipeData/RecipeList/RecipeManager/RecipeCraftObserver и handlers/items/Recipes; existing progression/native061/merchant068 test analogues. Все методы/enums сверены по исходнику. Новый production gear catalog не добавлен.

## Изменения

Policy читает native class/mage role, реальный expertise/checkCondition, изученные passive skills и их parsed equipment-conditioned stat functions. Типы weapon/armor берутся из native condition masks; таблицы item/class IDs для архетипов нет. Learned mastery разрешает смену прежнего weapon type, когда новая ветка поддержана фактически изученным skill. При отсутствии такого mastery прежний консервативный fallback сохранён.

Три узких read-only API: Skill.getStatFunctionTemplates(), FuncTemplate.getAttachCondition()/getApplyCondition(), ConditionUsingItemType.matchesItemType(). Native testImpl/calc/equip/combat stats не изменены. And/Or используют существующие parsed conditions; неподдерживаемые динамические условия не дают mastery credit. Unconditional generic stat не выдаётся за equipment mastery.

Preference использует native base power, bounded mastery weight и bonus за завершение реального ArmorSet с native set skills. Это авторский ranking, не точный combat simulator. Comparator локально кэширует utility. Full armor/chest+legs projection учитывает оба затронутых слота. Native grade и динамические условия проверяются до предпочтения. Бюджет, получение и применение остаются в существующих Commerce/Progression; бесплатных runtime equipment grants нет.

## Focused tests

Isolated javac четырёх production-файлов + suite/launcher: exit0. Существующий launcher `PhantomTestLauncher equipment068-policy 20260725001`: PASS2/2, exit0. Параметризованы BLUNT/BOW/DAGGER и LIGHT/HEAVY/MAGIC, conjunction mismatch, отсутствие ложного mastery у unconditional stat. Отчёт: .phantom-local/task068/reports/equipment068-policy/equipment068-policy.txt. DB не использовалась. Первый вызов без explicit report path завершился ошибкой записи default ../build; успешный run использовал own068 report path.

## Native actual PASS

Direct main PhantomEquipmentCraft068Native выполнен в последовательный слот родителя, только own068 TEST schema l2jmobiush5_phantom_test_068 на localhost3308. Working directory own068/native-test/game; infrastructure-only helper, без blanket initialize/cleanup. Команда:

```text
java -Dphantom.task068.native=true -Dphantom.test.config=<own068/game/config/Database.ini> -Dphantom.module.root=<module> -cp <equipment-bin;build-dev/phantom-test/bin;build-dev/dist/libs/GameServer.jar;module/dist/libs/*> org.l2jmobius.tests.phantoms.PhantomEquipmentCraft068Native
```

R3 exit0, примерно18 s, ThreadPool/Hikari закрыты. Собственный Ant/server launch не выполнялся. Родительский общий build до последних test/authority edits не заменяет следующий integrated compile.

Evidence:

- .phantom-local/task068/reports/equipment-craft068.tsv
- .phantom-local/task068/reports/equipment-craft068-native-r3.log
- .phantom-local/task068/reports/equipment-craft068-inputs.properties — finite input/paid receipts
- equipment-craft068-attempt-1.tsv и attempt-2.tsv — сохранённые прежние попытки

28 data rows: четыре архетипа × NONE/D/C/B/A/S/S80, weapon и armor в каждой строке. Классы выбраны по реальным native skill effects/tree, без fixed class IDs: DUELIST70 learned skills, MYSTIC_MUSE88, FORTUNE_SEEKER63, CARDINAL95. TSV — source of truth. Предметы выбираются из ItemData через production policy. Expertise и native conditions применяются; grade penalty не обходится.

Реальные paid private BUY и native useEquippableItem выполнены для B/S80 weapons и B armor разных архетипов, включая S80 weapon10223 за1280053332 Adena. Для fighter сохранена B-body/head/gloves/feet экипировка; setComplete=false честно отражает отсутствие всех source-priced set pieces. Buyer/seller Adena conserved, предмет получен через настоящий paid native transfer. Native useEquippableItem — тот же конечный API, который вызывает progression backend; сама сцена не создаёт полный materialization/decision journey.

Начальные levels85/native class skills, фонды10b и конечный seller stock — TEST premeasurement inputs, не earned live income. Native цены берутся из shared authority, при недостатке денег payer не пополняется. Все выдачи fixture обозначены отдельно от продуктовых receipts.

Четыре S80 chest получили SKIPPED_NOT_UPGRADE. Пример: Doom full-body2381 pDef270 нельзя заменить единственной Moirai chest15609 pDef238 без legs. TSV содержит candidate power, price, remaining funds и owned loadout. Это безопасный отказ single-item evaluator. Полный план перехода full-body→двухчастный high-grade set не реализован; полного S80 armor set для всех архетипов не заявлено. Реальная mid/high native замена подтверждена weapons и B armor.

Canonical self-craft: data-selected 100% dwarven recipe1, known craft skill172 level10,4 Stem+2 Iron Ore →500 Wooden Arrow, MP30. Forced success/rate overrides отсутствуют. Платный manufacture той же RecipeManager: клиент предоставляет4+2 ингредиента, переводит crafter100 Adena, MP30 расходуется у crafter, получает500 продукта. Observer фиксирует ACCEPTED/FEE_TRANSFERRED/INGREDIENTS_CONSUMED/SUCCESS_PRODUCT. Assertions проверяют exact ingredient delta один раз, product event, MP и fee conservation. Self-craft не взимает service fee. Начальное знание рецепта — объявленный TEST input; earned recipe learning run не заявлен.

## Ошибки fixture и повторяемость

R1 exit1: ordinary client-null vendor после первой пустой listing удалён штатным OFFLINE_DISCONNECT_FINISHED. Fixture использует явный OFFLINE_DISCONNECT_FINISHED=false только в этом JVM для нескольких последовательных ordinary listings; rates/chances не меняет. Это не C merchant lifecycle override.

R2 exit1 после реальных B-покупок и S80 weapon: одна S80 chest не улучшает full-body. Неверный assert «каждый tier купить» заменён bounded refusal, затем независимый craft выполнен. Обе ошибки fixture не представлены как product RED; реальные successful paid receipts сохранены.

Постоянные D068{role}, собственный disabled account task068craft; загрузка existing identity по exact account/name/class/access0. Повторной выдачи начальных денег нет. PAID gear checkpoints проверяются/переиспользуются, PENDING stock допускается только при наличии того же одного unsold native item. Uncertain craft PENDING не повторяет ingredient grant, DONE содержит native events. Старые TSV сохраняются как attempt-файлы. Новые names/reset schema/blanket inventory cleanup не использовались. Actors сохраняются и удаляются только из World; persisted данные остаются.

## Остаток

Следующий integrated Ant родителя включает финальные D tests и E authority split. Последние изменения isolated javac PASS; после R3 новых behavior-правок не делалось. Scene не является длительным автономным earned career run. Runtime SupplyJourney/Loot/Career уже вызывают эту policy. Full set transition planning, полный enchant/elemental utility и точное сравнение всех динамических skill conditions выходят за доказанные границы.

Git write/commit/push не выполнялись. Использованы только разрешённые bounded diff --check по exact tracked paths. Две отдельные проверки кодировки фиксируются после завершения handoff.

## Финальная интеграция root

Подготовительные ограничения выше сохраняют историю R3 build-dev; последующие committed builds выполнены. Shared paid native recipe1 ещё раз прошёл в C на committedv2; finalv9 code935b334fe78e4548295354189eafe5144023d38f сохраняет соответствующие C/D consumers относительноv2, по jar-class comparisons. Final candidate200 включает actual prepared MANUFACTURE268480990 с настоящим book/skill/fee100, историческая profession preparation не повторялась. Полного автономного earned career/S80 fullset это не доказывает; real R3 weapon/armor upgrades и metadata tiers остаются bounded evidence с указанной версией. Общий отчёт/матрица фиксируют native craft и отдельные ограничения.
