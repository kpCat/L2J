# Personal/Premium QoL roadmap

Этот roadmap начинается после принятого Phantom World Goal039 и использует независимую нумерацию `L2-QOL`. Старый Phantom freeze не открывается заново.

## L2-QOL-001 — защита от level-gap и магазин

Статус: **SUCCESS**.

Добавлены четыре нерасходуемых inventory-tier 5/10/20/40, отдельные INI/XML, русская страница Alt+B и штатный multisell с демонстрационными ценами. Максимальный найденный tier снимает только DROP/SPOIL level-gap penalty в пределах включительной границы `abs(actual native level gap) <= N`. XP/SP, quest rates, Spoil cast success, reward actor, amount/chance/RNG и Phantom/Fake Players не изменены. Shipped feature и shop выключены.

## L2-QOL-002 — личные cross-class skills и crystallization

Статус: **SUCCESS**.

Добавлены отдельный shipped-OFF/empty account+character allowlist, personal-only доступ real main-class Player к обычным foreign `CLASS`-деревьям реального trainer owner и native alternative SP 1x/2x/3x без включения глобального `AltGameSkillLearn`. Уровни, предыдущие навыки, required items, persistence и stock skill data остаются штатными. Non-dwarf должен реально изучить `CRYSTALLIZE`; native packet и Alt+B используют один mutation owner с точным `Item.getCrystalCount()`, preview, одноразовым token до 120 секунд и at-most-once защитой. GM/hero/clan/subclass/transform/FS/auto trees не расширены.

## L2-QOL-003 — длительность buff/dance/song

Статус: **SUCCESS**.

Добавлены отдельные shipped-OFF множители положительных buff/dance/song и bounded override по skill ID. Stock duration сначала полностью вычисляется в `Formulas.calcEffectAbnormalTime`, после чего personal policy применяется один раз к конкретному allowlisted real Player в `BuffInfo`; общие skill templates не меняются. Passive/toggle/triggered/abnormal-instant/debuff/negative, explicit abnormal time, неизвестное или конфликтное music ownership, summon/pet/NPC и headless Phantom остаются stock. Restore/relog/recast не создают повторного умножения.

## L2-QOL-003-HF1 — гонка reload music classifier

Статус: **SUCCESS**.

Refresh, invalidate и публикация immutable music snapshot сериализованы одним монитором, поэтому завершившаяся invalidation побеждает ранее начатую сборку. `classify()` и `conflictCount()` используют по одному локальному snapshot, а прогретый путь остаётся без повторного обхода skill trees. Исправление защищено controlled concurrency regressions с конечными deadlines и не меняет множители, exclusions, allowlist, `Formulas`, `Skill` или `BuffInfo`.

## L2-QOL-004 — Personal Board storefront и личные controls

Статус: **SUCCESS**.

Alt+B объединён в категории персонаж/EXP, дроп и спойл, травы, кристаллизация, расходники и статус. Provisional цены level-gap pass заменены audited data-owned лестницей `100 000 / 500 000 / 2 000 000 / 8 000 000 Adena`; `91001.xml` остался списком только четырёх pass. Chat и board используют один persisted EXP owner. Recovery/combat/Vitality herb preferences сохраняются per-character, vanilla default включён, pickup остаётся clean consume/no-effect. Кристаллизация остаётся inventory-derived и canonical. Utility store entries без надёжного Adena retail owner отложены.

## L2-QOL-005 — личный Seven Signs access и playability

Статус: **SUCCESS**.

Добавлен отдельный shipped-OFF personal switch. Allowlisted real Player получает штатный priest/hunting route, bypass только player registration/cabal/winner admission и защиту от cabal-only period/relog ejection; глобальные winner/seal owners и special NPC lifecycle сохранены. Все 14 Catacomb/Necropolis normal combat lists уже оказались period-independent и имеют native respawn, поэтому глобальная spawn-relaxation не нужна. Rift combat rooms также изначально не зависят от Seven Signs phase: production Rift не изменён, а native party/leader/min-size/capacity/fragments/jumps/timers/population/cleanup доказаны focused regression. Mammon не force-spawned; personal interaction возможен только при существующем stock global spawn.

## L2-QOL-006 — bounded party mobility/support

Статус: **SUCCESS**.

Remote PM, exact-name invite и self-only Summon Friend подтверждены как уже достаточные native flows; новые transports, direct party insertion, auto-accept и teleport service не добавлялись. В существующем Alt+B utilities-разделе появился отдельный shipped-OFF блок heal/res/karma cleanup только для allowlisted real personal actor и self/current own-party Player targets. `World` identity и party membership повторно проверяются перед mutation; competitive/special states закрыты. Heal использует `fullRestore`, resurrection — `doRevive()` без XP, cleanup — только `setKarma(0)` без изменения PvP/PK/clan/fame/recommendation state. Phantom AI/lifecycle и client не менялись.

## L2-QOL-007 — Semantic Pack v2

Статус: **SUCCESS**.

Functional command semantic v1 и corpus сохранены байт-в-байт. Отдельная humanized-v2 family добавляет strict/bounded/content-addressed identity, gender, exact-class и explicit role-family aliases, а также существенно больше русских social patterns/templates. Runtime передаёт только immutable read-only snapshot canonical `Player`/profile/appearance/active `PlayerClass`; persistent identity и action authority не дублируются. Неоднозначные роли сопоставляются с явным множеством canonical classes без произвольного выбора exact class. Existing relationship/profanity/mature/command/recent-response gates, deterministic selection и custom-overlay precedence сохранены.

## L2-QOL-008 — economy/progression closure

Статус: **SUCCESS**.

Ancient Adena/resources и SP/quest rates закрыты доказательством существующих canonical owners без глобальной инфляции или free grants. Allowlisted real Personal Player получает shipped-OFF relief только для 26 переписанных upper-level predicates при сохранении minimum/prerequisite/state/rate правил. Отдельный server-wide shipped-OFF auto-Noblesse идемпотентно применяет `Player.setNoble(true)` к real и Phantom Player при stored subclass level 75, не создавая Hero, quest state или награды. Alt+B progression list `91002` содержит только пять реально потребляемых clan prerequisites по XML-owned ценам; QOL-004 цены не изменены.

## L2-QOL-009 — Summoner/Servitor combat hardening

Статус реализации и delivery gate: **SUCCESS** — после устранения bounded test-only рисков четвёртый, отдельно разрешённый fresh full `ant verify` завершился `BUILD SUCCESSFUL` (39 min 13 s); focused/affected/QOL-001…008/Goal039 freeze также PASS. Единственный commit и normal non-force push с remote-SHA guard выполняются при передаче.

Phantom summoner использует только live true Servitor своего Player: синхронизирует PvE/raid/PvP target, возвращает Servitor в native follow при cleanup, законно перепризывает через известный progression skill и ограниченно пробует active skill из live NPC parameters. Все resource/reuse/condition/zone/instance/geodata проверки остаются у штатных `Player`/`Summon` APIs; Pet/BabyPet исключены. В normal PvP hostile Servitor может стать transient linked tactical subtarget при угрозе, high-impact easy-removal или временной недоступности owner, но owner Player остаётся canonical PvP/consequence context.

## Следующий этап

L2-QOL-001…009 и hotfix L2-QOL-003-HF1 имеют статус SUCCESS по реализации и полному verify; точный commit/push SHA фиксируется в итоговой передаче. QOL-010 не планируется. Общий проект не объявлен окончательно закрытым без отдельного final acceptance/freeze. Client names/icons остаются deferred из-за запрета client patch.
