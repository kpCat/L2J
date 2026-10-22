# READ_SET067 — проверенная карта настоящего репозитория

**Снимок при составлении:** GitHub `kpCat/L2J`, `feature/phantom-world`, HEAD `8631faf3d4ce7f0f2256cb312af99659c008560d`. Изучить живое состояние прежде mutation. Все пути от `L2J_Mobius_CT_2.6_HighFive/`, если не указано иное. Часть source owners приведена **по точной текущей файловой структуре**. При изменении HEAD искать точные symbols, не считать старые номера строк истиной.

## Инструкции и текущий baseline

Root `/AGENTS.md`; module `Agents.md`; `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `RELEASE_CONTRACT.md`, `PHANTOM_CURRENT_STATUS.md`, `PHANTOM_WORLD_ROADMAP_REVISED_2026-10-21.md`, `docs/PHANTOM_BOTS_ROADMAP.md` — читать связанные разделы. Последние отчёты `docs/phantoms/reports/PHANTOM-FARM-ECONOMY-066.md` и `...066-R2.md` только status/protected resource ownership, не возобновлять их кампании.

## Общий личный допуск

- `java/org/l2jmobius/gameserver/qol/PersonalCharacterQoLService.java` — `isPersonalUser(Player)` проверяет master + allowlist и отклоняет headless outbound; нужно отдельно доказать **невозможность Phantom** с личной привилегией и не испортить human.
- `java/org/l2jmobius/gameserver/config/custom/PersonalCharacterQoLConfig.java` — настройки/strict flags/парсинг; не делать второй allowlist.
- `dist/game/config/Custom/PersonalCharacterQoL.ini` — действующий файл, сейчас громоздкие CPU/RAM-комментарии, default false.
- Действующие QoL `qol/*` и соответствующие tests: сверить сохранённые cross-class, Seven Signs, durations, crystallization; не ломать их ради нового переключателя.

## A. Instancing (конкретные stock owners)

- `dist/game/data/scripts/instances/Kamaloka/Kamaloka.java`: `checkPartyConditions` около 445 — immediate null Party rejection; `enterKamalokaInstance` около 559–640 после создания `KamaWorld` и `InstanceManager.createDynamicInstance()` **безусловно проходит `party.getMembers()`**. Разделить допустимых участников с одной shared проверкой, сохраняя штатные level, reentry, range, template и существующий `finishInstance`.
- `dist/game/data/instances/Kamaloka/` — `KamalokaXX.xml`: обычная Камалока и Лабы обслуживаются общим кодом; не писать один patch на каждый layout.
- `java/org/l2jmobius/gameserver/managers/DimensionalRiftManager.java`, `start(Player,byte,Npc)` около260; проверяет `party.getLeaderObjectId()`, `party.getMemberCount()<GeneralConfig.RIFT_MIN_PARTY_SIZE`, per-member fragments, room occupancy; `model/sevensigns/DimensionalRift.java` **имеет поле `Party _party` и использует `_party.getMembers()` всю жизнь**.
- `dist/game/data/scripts/handlers/bypass/npc/Rift.java` — `enterrift`, `changeriftroom`, `exitrift` и `inRift=player.isInParty() && party.isInDimensionalRift()`; при solo без Party эти 3 входа требуют честной адаптации либо безопасного native singleton Party.
- `dist/game/config/General.ini` — `RiftMinPartySize=2`, не менять globally; `model/groups/Party.java` и `instancezone/` читать адресно после одного audit.

## B. Wondrous Cubic — факт из XML и server reuse

- `dist/game/data/stats/items/10600-10699.xml`, Wondrous Cubic **id10632**, skill **2510**, `reuse_delay=86400000` ms, `handler=ItemSkills` (примерно строки461–475).
- `java/org/l2jmobius/gameserver/network/clientpackets/UseItem.java` — `item.getReuseDelay()`, `getItemRemainingReuseTime`, `getReuseDelayOnGroup` около 167–185; после настоящего `handler.onItemUse` делает `player.addTimeStampItem(item, reuseDelay)` и `sendSharedGroupUpdate` около384–387.
- `dist/game/data/scripts/handlers/items/ItemSkills.java` и base `ItemSkillsTemplate.java` (точный каталог/наследника найти по import). Нельзя менять `reuse_delay` в XML всем игрокам.
- `model/actor/Player.java`: existing reuse item timestamps persistence и re-login; если old24h timestamp сохранён, выяснить семантику времени последнего использования, а не автоматически сбросить без анализа.

## C. Рыбалка

- `java/org/l2jmobius/gameserver/model/fishing/Fishing.java`: state `_mode`, `_deceptiveMode`, `_fishCurHp`, `useReeling()`/`usePumping()`, существующий `ThreadPool.scheduleAtFixedRate(this, 1000, 1000)`; это owner рыболовного боя. Не добавлять новый глобальный polling scheduler.
- `dist/game/data/scripts/handlers/skill/effects/Reeling.java`, `Pumping.java`: native skill effect, FishShots, rod, levels/penalties/HP; также `.../skill/effects/Fishing.java` для начала ловли.
- `java/org/l2jmobius/gameserver/data/xml/FishData.java`, `FishingRodsData.java`, `FishingMonstersData.java`; `model/fishing/Fish.java`; `model/actor/Player.java` methods fishing flag/combat.

## D. Отдельные QoL

- `java/org/l2jmobius/gameserver/data/xml/LevelUpCrystalData.java` — soul crystal data, `PlayerConfig.SOUL_CRYSTAL_CHANCE_MULTIPLIER` около221 (только chance); нужен фактический event/kill admission проверяющий player level (найти направленным code search по `getNpcSoulInfo` / SoulCrystal objects), не принимать догадку за дефект.
- `java/org/l2jmobius/gameserver/model/actor/Player.java` `recalcHennaStats()` около9002, `_hennaSTR`, `_hennaINT` и т. п. getter-ы; `model/item/Henna.java` методы `getStatSTR/CON/DEX/INT/MEN/WIT`; `data/xml/HennaData.java`; `network/clientpackets/RequestHennaEquip.java/RequestHennaRemove.java`, `network/serverpackets/HennaInfo.java`.
- `java/org/l2jmobius/gameserver/model/options/Augmentation.java`, nested `AugmentationStatBonus`, native `applyBonus/removeBonus`; `model/options/Options.java` и `data/xml/OptionData.java` или фактически найденный parent `OptionData` (сверить package). Не умножать шанс получения опции вместо её значения.
- Необязательные future: синие чемпионы в `dist/game/config/Custom/ChampionMonsters.ini`, не в scope067.

## Tests / build / snapshots

`test/java/org/l2jmobius/tests/...`: найти **существующие** минимальные QoL suites/Launcher modes через конкретные import/entrypoint, не угадывать имена. `tools/phantom-runtime/Build-Task066R2.ps1`, `Assemble066R2.ps1`, `Runtime066R2.ps1` — **read-only reference**, не использовать для R2 writes. `build.xml` JDK25 Ant. Native approval проверять в коротком suite на собственных test fixtures; при отсутствии настоящего сетевого клиента `CLIENT_QA=DEFERRED_BY_OWNER` и не считать fake GameClient доказательством живой сессии.
