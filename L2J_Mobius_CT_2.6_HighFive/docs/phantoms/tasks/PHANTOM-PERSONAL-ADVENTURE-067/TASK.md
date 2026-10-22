# PHANTOM-PERSONAL-ADVENTURE-067 — личные инстансы и игровой комфорт

## Зачем это делаем

Одна **продуктовая кампания** вместо 3–5 отдельных мелких задач: дать владельцу **реально используемые** исключения для своих обычных персонажей (включая соло-вход в Камалоку/Лабиринты/Rift), и реализовать большую часть давно согласованных QoL. Не пересматривать M1–M7 и TASK066/066-R2. Они остаются проверенным baseline с зафиксированными PARTIAL. Не возвращаться к Гремлинам, гномам, соскам, старым рейдам и старым runtime-фикстурам без прямой регрессии именно этого задания.

**Git:** `D:\Codex\repos\L2J-phantom-world`, branch `feature/phantom-world`, GitHub `kpCat/L2J`. Последний подтверждённый при составлении пакета HEAD `8631faf3d4ce7f0f2256cb312af99659c008560d`. В начале проверить live remote SHA и актуальный working tree. C:-репозиторий `C:\Users\ZBook\L2J_Mobius` не рабочий; оба его stash сохранять нетронутыми.

**Владелец не участвует в ручном QA** до завершения всего Phantom World. У Codex заранее есть разрешения `AUTHORITY.md` на самостоятельное проектирование, модификации, собственные проверки, свою безопасную поставку, commit и non-force push. Максимум **12 часов**, после **10,5 часов** не начинать новый функционал, а заканчивать, фиксировать честную матрицу, STOP, commit/push. Не тратить оставшееся время лишь потому, что бюджет 12 часов.

## Продуктовые результаты в приоритете

### A. Личный соло-вход в настоящий instance (высший приоритет)

На основе **существующего** `PersonalCharacterQoLService.isPersonalUser()` и нового адресного switch `EnablePersonalSoloInstanceEntry=False` (default OFF) реализовать **при отсутствии Party вход одного allowlisted обычного персонажа** в поддержанный party-gated контент, не выдавая GM/accessLevel, не отключая глобально ограничения. Первым делом:

1. **Kamaloka и Labyrinth of the Abyss** — общий script `dist/game/data/scripts/instances/Kamaloka/Kamaloka.java` (индексы/template IDs и уровни штатные). При solo: `KamaWorld`, один allowed entrant, штатный instance, время, cooldown/reuse, boss/minions, выход и cleanup; без `party == null` NPE. При существующей Party: допускается соответствующий leader и все законно подходящие члены **в один и тот же instance**; не делить их по разным инстансам и не давать индивидуальное исключение неразрешённому инициатору.
2. **Dimensional Rift** — `DimensionalRiftManager.start()` и `DimensionalRift` зависят от настоящего Party. Сначала оценить party-centric owner и life-cycle. Разрешён **маленький внутренний/временный одноучастниковый native Party-контекст** только для allowlisted solo инициатора, если он корректно переживает rooms/fee/reuse/death/logout/exit/cleanup и не добавляет fake Player/GM. Если такой контекст опасен или нарушает native Party, не переписывать весь Rift: изолировать Rift как честный PARTIAL с точным root cause и сохранить полностью работающую Камалоку/Лабы. Не снижать `RiftMinPartySize` для всего сервера.
3. Сделать компактную **общую admission policy**, чтобы у других поддержанных native скриптов со временем не плодить проверки личного allowlist. Но **не делать sweep/переписывание всех instances** в рамках 067: ограниченный inventory entry points и фактическое подключение только вышеназванных. Обычные игроки и Phantom по-прежнему получают стандартные правила.

Не обходить native: уровень, quest progress, items/fee (Rift fragments id7079), reuse timers, max party, наличие/близость участников, instance identity, gate NPC, teleport restrictions, exit/cleanup. Существующая настоящая Party из 2+ игроков входит в один instance, а не только allowlisted лидер. Соло-персонаж не должен вынужденно состоять в пользовательской Party перед разговором с NPC.

### B. Wondrous Cubic — 60-минутный reuse для allowlisted

Реальная находка из source: item **10632**, XML `dist/game/data/stats/items/10600-10699.xml`, стандартный `reuse_delay=86400000`, skill2510, native item use в `network/clientpackets/UseItem.java` и handler `handlers/items/ItemSkills.java`.

Новый config `EnablePersonalWondrousCubicCooldown=False`, `PersonalWondrousCubicCooldownMinutes=60` (default). Вычислять effective reuse **только** в native item-use для разрешённого живого Player, не редактировать stock XML reuse глобально. Корректно обработать как новый use, так и уже существующие timestamps от предыдущего 24-часового использования и общую группу reuse/пакеты клиенту; не очищать чужие таймеры, не давать повторное использование до истечения 60 минут, включая relog/restart/subclass. Физический предмет/штатный skill и награда используются как прежде — не выдавать бесплатные cube fragments вручную. При disabled — обычные 24 часа без ложных сокращений.

### C. Рыбалка — автоматизация Reel/Pump для личного игрока

Игрок вручную начинает обычную рыбалку со **своей удочкой/приманкой/ресурсами**. При `EnablePersonalFishingAssist=False` (default) всё штатно. При True allowlisted Player получает серверную помощь в текущем fish combat: выбор правильного Reel/Pump по настоящим `_mode` и `_deceptiveMode` (с учётом специальных рыб), используя **нативные навыки/Skill effects** и их реальные требования, MP, reuse, FishingRod/FishShots, вероятность resist и настоящие item rewards/EXP. Допускается bounded automatic fishing action внутри уже существующего 1-second `Fishing` tick/owner, а не собственный глобальный поток на игрока и не прямое `changeHp` с выдуманным уроном.

Закончилась рыбалка, удочка снята, lure кончилась, игрок умер, вышел, сменил instance/трансформировался или выключено QoL — помощь прекращается, нет фонового цикла и лишних rewarded fish. Автоматизировать только Reel/Pump во время реального клюнувшего рыболовного боя; **не нужно автоматически забрасывать удочку бесконечно**. Автоматизация может быть очень сильной в рамках личного комфорта, но не уничтожает рыбу без расходов и не подменяет награду.

### D. Остальные личные QoL одним адресным проходом, в порядке времени/риска

**D1 — SA / Soul Crystal:** Добавить `EnablePersonalSoulCrystalOverlevel=True/False` (default False). Проверить реальный level-gap отказ в stock `LevelUpCrystalData` и обработчике kill/absorb; снимать **только ограничение чрезмерно высокого уровня игрока** для разрешённых обычных персонажей. Нельзя снять требования NPC/crystal stage, level cap, quest, absorb type, skill needed, native chance или случайность. Существующий `SoulCrystalChanceMultiplier` оставить независимым. Если выяснится, что server native вообще не имеет такого запрета, не добавлять бессмысленный обход: доказать отсутствие и записать `NOT_APPLICABLE`.

**D2 — Henna:** `PersonalHennaNegativeMode=OFF|IGNORE_NEGATIVE|FLIP_NEGATIVE` (default OFF) и switch/типизированная политика. Реальные модификаторы считать через `Player.recalcHennaStats()` и существующие stat getters; не менять XML самих красок и клиентскую общую модель. При IGNORE отрицательное становится0, FLIP отрицательное становится положительным **с применением stock итоговых потолков по каждой характеристике** (особенно +5). Не дать бесконтрольного роста при надевании/снятии/переключении класса/relog; не менять ordinary/Phantom, пакет `HennaInfo` допускает старую нативную подсказку клиента при честно новых серверных статах.

**D3 — Augmentation / Life Stones:** `EnablePersonalPositiveAugmentationMultiplier=False` и `PersonalPositiveAugmentationMultiplier=1.0` (настраиваемый конечный предел, например 1–5; default 1). В native `model/options/Augmentation.java` + `OptionData/Options` сначала определить реальные stat-func writers, отличить **положительные численные stat bonuses** от skills/proc/chance, отрицательных значений, skill levels и glow. У разрешённого персонажа умножается именно положительное stat value. Apply/remove обязаны быть симметричными, повторный login/equip/unequip не удваивает бонус; не менять глобальные option templates. Если нельзя гарантировать типизированное выделение положительных эффектов в этом бюджетном объёме — оставить D3 OFF и честный NOT_RUN, а не мутировать глобальную формулу.

**D4 — конфиги:** все новые настройки в существующем `PersonalCharacterQoL.ini` (или его логическом вспомогательном файле только если parser реально требует) с **краткими русскими комментариями**: действие, значения, зависимости, нужен ли restart. Убрать гигантские повторы CPU/RAM/network **только в затронутом PersonalCharacterQoL.ini**, без массовой чистки всех configs и unrelated diffs. Все новые policy children default OFF. Master `EnablePersonalCharacterQoL`, `AllowedAccounts` / `AllowedCharacterIds` обязательны, новые опции не повышают доступ.

Blue champions, все остальные cooldown предметов, массовая offline-экономика, рейдовая AI-кампания, cross-class skill refactor, Semantic Pack/Studio **вне** 067; перечислить как future roadmap, не имплементировать в довесок.

## Решение по объёму вместо охоты за галочками

A и B — обязателен конкретный полезный результат. C и D — максимально выполнить в рамках той же кампании, сохраняя безопасность. Если сложность Rift, рыболовного tick или augmentation превысила bounded investigation, **не блокировать** продвижение других независимых функций: зафиксировать точную архитектурную причину в одной странице и переключиться на следующий потребительский результат. Не запускать 067-R2 автоматически в конце.

Приёмка — `ACCEPTANCE.md`, время и операции — `AUTHORITY.md`, source map — `READ_SET.md`, сохранность — `RUNTIME_INPUTS.md`. Критерий — реальные, изолированные игровые действия и отсутствие выдачи исключения постороннему, а не число helper tests. Живая клиентская QA владельца отложена до завершения всего Phantom World.
