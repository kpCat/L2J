# Personal/Premium QoL — current status

Дата: 2026-09-14

Ветка: `feature/phantom-world`

Required parent: `58672daf0bcd008c7c8b9c022c40671873ad89ce`

Текущая задача: `L2-QOL-005`

Статус: **SUCCESS**

## L2-QOL-005

- Добавлен отдельный shipped-OFF `EnablePersonalSevenSignsAccess`, использующий прежний allowlist real Player и исключающий headless Phantom.
- Personal Player получает personal-only ссылку из любого stock Dawn/Dusk Priest page в штатный `HuntingGroundsTeleport`, проходит только player-specific registration/cabal/winner checks Catacomb/Necropolis и не выбрасывается за отсутствие cabal при period transition или relog.
- Глобальные winner/seal owner, Seven Signs scores/contributions/festival, special NPC spawn lifecycle и Mammon economy не меняются. Mammon interaction bypass применяется только если штатно spawned Merchant/Blacksmith уже существует и его глобальные winner/seal условия выполнены.
- Все 14 Catacomb/Necropolis normal combat spawn lists (1 716 attackable declarations) загружаются общим `SpawnData` во всех четырёх периодах и используют штатный respawn; отдельная глобальная spawn-relaxation не понадобилась.
- Rift waiting/start/first room/timed/manual jump/spawn/respawn/cleanup census не обнаружил Seven Signs period/cabal dependency. Production Rift code не менялся; party, leader, min-size, capacity, fragments 7079, jump/timers и ejection сохранены.
- Focused suite проверяет четыре периода, actual normal combat respawn, native two-Player Rift start, обе населённые combat rooms и stock cleanup.

## L2-QOL-004

- Personal QoL Alt+B стал одной категоризированной панелью: персонаж/EXP, дроп и спойл, травы, кристаллизация, расходники и статус/справка. Внутренние item/list ID запоминать не нужно.
- Четыре provisional цены заменены финальной data-owned лестницей `100 000 / 500 000 / 2 000 000 / 8 000 000 Adena`; HTML не содержит копии цен. Политика и anchors записаны в `SHOP_PRICING.md`.
- `.expon`/`.expoff`, Alt+B и login restore используют один `PersonalPlayerControlService` и прежний persisted `EXPOFF`.
- Отдельно для персонажа сохраняется включение recovery/combat/Vitality трав. Vanilla default включён; при ignore штатный pickup уничтожает траву без эффекта и без inventory clutter. Другие игроки, неизвестные immediate-effect items и headless Phantom не меняются.
- Кристаллизация остаётся inventory-derived и делегирует canonical `CrystallizationService`; общий item catalog не создан.
- Mana/vitality/rate store entries отложены: templates существуют, но безопасный repository-local Adena retail owner отсутствует; новые товары, multisell ID и client patch не добавлялись.

## L2-QOL-003-HF1

- `PersonalEffectMusicClassifier` публикует и инвалидирует immutable snapshot под одним монитором: завершившийся `invalidate()` больше не может быть затёрт публикацией ранее начатого refresh.
- Каждый вызов `classify()` и `conflictCount()` работает с одним локально захваченным snapshot; прогретый путь не сканирует `SkillTreeData` повторно.
- Controlled concurrency regression использует production classifier, конечные latch/barrier deadlines, baseline negative control и обязательный cleanup. Сохранены все пять исходных QOL-003 cases, включая реальный reload H5 skill tree.
- Множители, exclusions, allowlist и владельцы `Formulas`/`Skill`/`BuffInfo` не менялись.

## L2-QOL-003

- В `PersonalCharacterQoL.ini` добавлены отдельный shipped-OFF switch, множители положительных buff/dance/song и bounded override по skill ID.
- Сначала полностью вычисляется штатный `Formulas.calcEffectAbnormalTime`, затем результат один раз корректируется в новом per-recipient seam `BuffInfo`. Общий `Skill._abnormalTime` не изменяется.
- Множитель действует только для allowlisted real Player из QOL-002, включая активный subclass. Headless Phantom, summon/pet и NPC используют stock duration.
- Passive, toggle, triggered, abnormal-instant, debuff и negative effects исключены. Положительный explicit `abnormalTime` остаётся авторитетным и не умножается повторно.
- Song/dance определяется по реальному H5 `SkillTreeData`: `SWORDSINGER`/`SWORD_MUSE` и `BLADEDANCER`/`SPECTRAL_DANCER`. Конфликтное или неизвестное происхождение fail-closed оставляет stock duration; snapshot обновляется после reload дерева.
- Ошибка новых ключей fail-closed отключает только duration feature. Валидные QOL-001, QOL-002 и базовые allowlist продолжают работать.

## Сохранённое поведение L2-QOL-002

- Новый `PersonalCharacterQoL.ini` поставляется с master/subfeature switch в `False` и пустыми allowlist character ID/account.
- Только allowlisted real main-class Player может открыть у реального владельца-тренера обычное `CLASS`-дерево другой профессии того же или более низкого hierarchy level. Headless Phantom и active subclass исключены.
- Глобальный `AltGameSkillLearn` не включён. Stock `SkillList` / `Folk` / `SkillTreeData` / `RequestAcquireSkill`, требования уровня/предыдущего навыка/предметов и сохранение в БД остаются владельцами процесса.
- Personal foreign skill сохраняет native alternative SP: own 1x, same fighter/mage type 2x, opposite type 3x; цена списка совпадает со списанием.
- Реально изученный `CRYSTALLIZE` разрешает non-dwarf штатную кристаллизацию без race bypass.
- Native packet и Alt+B используют один mutation service с исходными ограничениями и точным `Item.getCrystalCount()`.
- Alt+B показывает bounded inventory page и необратимый preview; подтверждение имеет непредсказуемый одноразовый token не дольше 120 секунд, один на Player, с повторной проверкой item/count/enchant/result и at-most-once защитой от native/board гонки.
- Ошибка нового конфига fail-closed отключает только QOL-002 и не повреждает валидное состояние QOL-001.

## Сохранённое поведение L2-QOL-001

- Четыре нерасходуемых level-gap tier 5/10/20/40 и guarded multisell `91001` работают без изменения semantics.
- XP/SP, quest rates, Spoil cast success, amount/chance/RNG, native reward actor, raid curse и Phantom/Fake Player exclusions не изменены.

## Артефакты

- INI: `dist/game/config/Custom/PersonalPremiumQoL.ini`
- tier XML: `dist/game/data/custom/personal-qol/level-gap-items.xml`
- native multisell: `dist/game/data/multisell/91001.xml`
- Alt+B HTML: `dist/game/data/html/CommunityBoard/Custom/personal-qol/main.html`
- personal access INI: `dist/game/config/Custom/PersonalCharacterQoL.ini`
- duration policy: `java/org/l2jmobius/gameserver/qol/PersonalEffectDurationPolicy.java`
- Alt+B crystallization HTML: `dist/game/data/html/CommunityBoard/Custom/personal-qol/crystallization*.html`
- storefront pricing policy: `docs/personal-qol/SHOP_PRICING.md`
- операторская инструкция: `docs/personal-qol/OPERATOR_GUIDE_RU.md`
- evidence reports: `docs/personal-qol/reports/001-level-gap-and-shop.md`, `docs/personal-qol/reports/002-cross-class-skills-crystallization.md`, `docs/personal-qol/reports/003-personal-effect-duration-rates.md`, `docs/personal-qol/reports/003-hf1-music-classifier-reload-race.md`, `docs/personal-qol/reports/004-personal-board-storefront-controls.md`, `docs/personal-qol/reports/005-seven-signs-personal-access.md`

Focused, affected, historical static, Goal039 structure/static/docs, финальный fresh `ant verify` и standalone jar зафиксированы в evidence report. Использовалась только allowlisted test DB; production DB не читалась и не проверялась.

Клиентский UI: **NOT_TESTED_CLIENT_UI**. Серверная страница и bypass проверены, но визуальная проверка в H5-клиенте не выдаётся за выполненную. Client patch не требуется: в инвентаре остаются исходные stock names/icons.

L2-QOL-001/002/003/004/005 и L2-QOL-003-HF1 имеют статус **SUCCESS**. Неаудированные utility-продажи отложены; дальнейшие bounded задачи перечислены в roadmap и автоматически не запускаются. Phantom freeze и алгоритмы Phantom не переписывались; следующий Goal не создавался.
