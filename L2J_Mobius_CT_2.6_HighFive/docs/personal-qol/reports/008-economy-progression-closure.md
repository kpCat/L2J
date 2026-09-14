# L2-QOL-008 evidence report

Дата census: 2026-09-14
Required parent: `3cf8f5d35997ff65e5e484c1c3d6224d7f759859`
Required branch: `feature/phantom-world`
Required subject: `qol(008): close economy and progression gaps`

## Обязательный read-first census

Этот раздел записан до первой production-правки. Прочитаны полностью task package L2-QOL-008 (`TASK.md`, `ACCEPTANCE.md`, `CONTEXT.md`, `FEATURE_CONTRACT.md`, `SOURCE_AUDIT.md`, `TEST_PLAN.md`, `PACKAGE_MANIFEST.md`, `CODEX_LAUNCHER.md`), module `Agents.md`, корневой `README.md`, module `readme.txt`, master plan/workflow/task-package docs, Personal QoL `CURRENT_STATUS.md`, `ROADMAP.md`, `OPERATOR_GUIDE_RU.md`, `SHOP_PRICING.md`, отчёты QOL-001, QOL-002, QOL-004, QOL-005, QOL-006, QOL-007, Goal037/Goal039 evidence и релевантные Ant/test owners.

### Currency, Ancient Adena и Phantom economy

- Canonical item owners: Adena `57`, Ancient Adena `5575`, Blue/Green/Red Seal Stones `6360/6361/6362`.
- `SevenSigns` владеет значениями seal stones `3/5/10` Ancient Adena и расчётом `calcAncientAdenaReward`; native catacomb drop data содержит seal stones, а принятый QOL-005 постоянно загружает все 14 normal-combat Seven Signs populations без переписывания Mammon lifecycle.
- Black Marketeer of Mammon `31092` имеет native daily exchange `2 000 000 Adena -> 500 000 Ancient Adena`, то есть подтверждённое соотношение `4:1`; `Q00198_SevenSignsEmbryo` и support item `14850` также дают Ancient Adena штатными путями. Native Mammon multisells реально потребляют Ancient Adena.
- Phantom background authority читает authoritative NPC drops и `RatesConfig`, применяет canonical drop chance/amount/level-gap rules и сохраняет добычу через существующие acquisition/inventory owners. Commerce использует catalogued supply и budget/reservation owners; бесплатного resource grant нет.
- Классификация: Ancient Adena/resource closure — `ALREADY_NATIVE`; новый exchange/shop не нужен. Mammon force-spawn, Seven Signs lifecycle rewrite и free Phantom grant — `OUT_OF_SCOPE`.

### SP и quest-rate authority

- `Rates.ini` shipped defaults: `RateSp=1`, `RatePartySp=1`, `QuestItemDropAmountMultiplier=1`, `RateQuestRewardXP=1`, `RateQuestRewardSP=1`, `RateQuestRewardAdena=1`, `RateQuestReward=1`, typed potion/scroll/recipe/material multipliers `=1`.
- Combat SP: `Npc.getSpReward(...)` выбирает dynamic SP rate или `RateSp` ровно один раз; `Attackable.calculateExpAndSp(...)` использует результат, а `Party.getSpBonus(...)` отдельно и ровно один раз применяет `RatePartySp`. Player stat/premium multipliers остаются своими canonical слоями.
- Quest rewards: `Quest.addExpAndSp(...)`, `rewardItems(...)`, `giveItems(...)`, `giveQuestItemsUpTo(...)` и `giveItemRandomly(...)` владеют XP/SP/Adena/generic/typed item multipliers; amount масштабируется один раз, chance и caps не подменяются.
- Goal037 AST inventory фиксирует 543 quest sources / 7066 rate sites; при изменении quest source оба manifest должны быть детерминированно перегенерированы и проверены.
- QOL-002 cross-class SP/item costs принадлежат canonical skill-tree data и не зависят от Personal QoL rate feature.
- Классификация: canonical SP/quest rates — `ALREADY_PRESENT`; дефект не доказан, поэтому shipped global rates не меняются.

### Quest upper-level gates

- Полный search census отделил minimum gates от upper/too-high gates. Явные upper gates найдены в Pailaka `Q00128`, `Q00129`, `Q00144`; start eligibility `Q00179`, `Q00182`, `Q00694`, `Q00695`, `Q00698`; reward caps `Q00134`, `Q00135`, `Q00139`-`Q00143`, `Q00183`-`Q00191`, `Q00335`; low-level reward branch `Q00178`.
- Реальный Leto Lizardmen SP quest — `Q00186_ContractExecution`: minimum level `41`, prerequisite `Q00184` completed, Loraine certificate and normal quest state/kill/item progress remain mandatory; base reward is `105083 Adena`, while `285935 XP / 18711 SP` is stock-capped below level `47`.
- `Q00300_HuntingLetoLizardman` имеет minimum `34`, но не имеет upper gate и уже работает для over-level Player — `ALREADY_NATIVE`.
- Tutorial/newbie checks below level 25 and level-banded scenario/reward-selection logic are not otherwise-valid quest denial gates and are not rewritten. Minimum-level, class/race, prerequisite, quest-state, item, kill, party, cooldown, repeatability and reward-rate checks remain authoritative.
- Классификация: allowlisted-real-Player-only upper-level relief — `CHANGE_REQUIRED`; ordinary Player, feature OFF и Phantom behavior остаются stock.

### Noblesse

- Canonical terminal owner `Q00247_PossessorOfAPreciousSoul4` proves subclass level threshold `75`, invokes `Player.setNoble(true)` and separately grants quest rewards/tiara.
- `Player.setNoble(true)` owns Noble skill-tree add/remove semantics; `characters.nobless` is restored by `Player.load(...)` and persisted by canonical Player store. Stored subclass levels belong to `character_subclasses` / `SubClassHolder`, so eligibility can be proven even while main class is active.
- Safe non-polling seams: real Player login, subclass level change and Phantom materialization after canonical `Player.load(...)`.
- Классификация: separate server-wide shipped-OFF idempotent auto-Noblesse — `CHANGE_REQUIRED`. Synthetic quest completion, Hero, class mutation and duplicate tiara/reward — `OUT_OF_SCOPE`.

### Clan progression и curated inventory prerequisites

`Clan.levelUpClan(Player)` — canonical mutation owner. Он непосредственно проверяет и уничтожает:

| Переход | Inventory prerequisite | Прочие сохранённые gates |
|---|---:|---|
| 2 -> 3 | Blood Mark `1419 x1` | `350 000 SP` |
| 3 -> 4 | Alliance Manifesto `3874 x1` | `1 000 000 SP` |
| 4 -> 5 | Seal of Aspiration `3870 x1` | `2 500 000 SP` |
| 5 -> 6 | нет | `5 000 CRP`, 30 members |
| 6 -> 7 | нет | `10 000 CRP`, 50 members |
| 7 -> 8 | нет | `20 000 CRP`, 80 members |
| 8 -> 9 | Blood Oath `9910 x150` | `40 000 CRP`, 120 members |
| 9 -> 10 | Blood Alliance `9911 x5` | `40 000 CRP`, 140 members |
| 10 -> 11 | нет | territory, `75 000 CRP`, 170 members |

Possession alone не повышает clan level: canonical owner всё равно проверяет текущий level, SP/CRP/member/territory gates и сам потребляет точное количество. Эти пять inventory prerequisites классифицированы `CHANGE_REQUIRED` для curated shop. Intermediate quest-state tokens, Noblesse quest items, raid jewelry, equipment, enchant dumps и arbitrary rare loot — `OMIT_UNSAFE_STATEFUL_ITEM` или `OUT_OF_SCOPE`.

### Alt+B/store authority и цены

- Existing server-rendered Community Board owner: `PersonalPremiumQoLBoard`; dedicated protected multisell list `91001`; `PersonalPremiumQoLService` читает offer/price из XML, а `MultisellData` + `MultiSellChoose` обеспечивают dedicated provenance и native transaction.
- Collision census: `91002` не занят. Выбран отдельный protected list `91002`, а не расширение generic shop.
- Planned data-owned prices: `1419 x1 = 5 000 000 Adena`, `3874 x1 = 15 000 000`, `3870 x1 = 30 000 000`, `9910 x150 = 75 000 000`, `9911 x5 = 100 000 000`. Лестница следует возрастающей acquisition/late-clan сложности и остаётся выше convenience-pass anchors.
- QOL-004 prices `100 000 / 500 000 / 2 000 000 / 8 000 000 Adena` и list `91001` остаются byte-for-byte неизменными.
- Классификация: curated progression section with single data authority — `CHANGE_REQUIRED`; generic GM/item/quest shop — `OUT_OF_SCOPE`.

### Freeze owners и bounded exception

- QOL-001..007 closure принадлежит их focused suites/Ant targets; QOL-007 verify transitively freezes earlier Personal QoL contracts. Goal037 owns quest-rate manifests; Goal039 static/documentation suites own full-vision freeze.
- Task package прямо объявляет L2-QOL-008 «one intentionally broad bounded closure task». Из-за явного census и точечных quest gates потребуется больше 8-10 файлов; bounded exception ограничен новым config/service/store/test/doc owners, canonical login/materialization/level seams и только перечисленными quest scripts/manifests. Никакого соседнего cleanup/refactor нет.
- Не найдены дополнительные project-local `AGENTS.md`, code-map или pattern-файлы сверх module `Agents.md` и прочитанных master/workflow/task docs.

### Переиспользуемые локальные паттерны, ограничения и непроверенное

- Config: strict fail-closed parsing и test override по образцу Personal QoL services.
- Eligibility: существующий `PersonalCharacterQoLService` для allowlisted real Player; headless/Phantom явно исключаются только из quest relief.
- Noblesse: canonical `SubClassHolder` + `Player.setNoble(true)` + Player persistence, без дублирования skill/DB logic.
- Store: dedicated list provenance, XML-owned offers/prices и preview из runtime catalog по образцу QOL-004.
- Tests: существующий seeded suite launcher, guarded local test DB и Ant targets; production DB и `prepare-phantom-test-db` запрещены.
- На момент census ещё не проверены compile/runtime tests, deterministic manifest regeneration, focused/affected/freeze suites, единственный final full `ant verify`, standalone jar, encoding guards, exact staged scope, commit/push/remote SHA. Эти результаты будут записаны ниже после выполнения.

## Реализация и проверки

Итог qualification: `PASS`. Git-only closure (создание единственного commit, обычный non-force push и сравнение remote SHA с local HEAD) выполняется после включения этого отчёта в commit; итоговый SHA приводится в delivery evidence, поскольку SHA commit не может самоссылочно храниться в собственном содержимом.

### Реализованный bounded scope

- Добавлен строгий fail-closed config `config/Custom/PersonalProgressionQoL.ini` с двумя независимыми shipped-OFF ключами: `EnablePersonalQuestOverLevelRelief=False` и `EnableServerWideAutoNoblesse=False`.
- Добавлен единый `PersonalProgressionQoLService`: quest relief действует только для allowlisted real Personal Player и только в 26 перечисленных upper-level predicates; minimum level и все остальные canonical prerequisites не изменены. Auto-Noblesse проверяет реальный stored subclass уровня `75+`, вызывает canonical `Player.setNoble(true)` и `Player.store()`, не выдаёт Hero, tiara или quest completion и идемпотентен.
- Auto-Noblesse подключён в canonical real-Player login, успешное повышение subclass level и Phantom materialization. Для materialized Phantom сохранён canonical cleanup lifecycle; после воспроизведённой teardown-гонки task cleanup закреплён финальным `stopAllTasks()` после `deleteMe()`.
- Добавлен отдельный protected progression multisell `91002`, dedicated Alt+B route и server-rendered preview из того же XML/runtime catalog, который используется native transaction. Единственный найденный `91002.xml` — новый canonical owner; forged generic route отвергается.
- В `91002.xml` присутствуют только подтверждённые inventory prerequisites: Blood Mark `1419 x1 / 5 000 000 Adena`, Alliance Manifesto `3874 x1 / 15 000 000`, Seal of Aspiration `3870 x1 / 30 000 000`, Blood Oath `9910 x150 / 75 000 000`, Blood Alliance `9911 x5 / 100 000 000`. Phantoms не получают бесплатных предметов. Quest-only/stateful tokens, Noblesse items и generic inventory не добавлены.
- Ancient Adena/resource path не добавлялся: census доказал `ALREADY_NATIVE`. Shipped global rates и QOL-004 list `91001`/цены не изменены.
- После изменения allowlisted quest source Goal037 manifests детерминированно перегенерированы; Q00128 canonical source pin в Goal036 supported-content manifest обновлён на `6be37daff54269321273677ea71072141da42fd983b74bbeaf695675e9585b49`.
- Обновлены Personal QoL status/roadmap/operator/pricing docs. QOL-008 закрыт; QOL-009 Summoner/Servitor остаётся отдельной следующей задачей и не начинался.

### Focused evidence

`ant -q qol-economy-progression-closure-test` — `PASS`, 7/7 deterministic scenarios:

1. Native Ancient Adena/rates/upper-gate census, 26 quest seams и единственный multisell `91002`.
2. Strict shipped-OFF config, malformed-config fail-closed behavior и real-Player-only quest relief.
3. Combat SP и quest XP/SP/Adena/item baseline x1, test-only modified multipliers exactly once, Personal QoL OFF без влияния на rates.
4. Реальный Leto Lizardmen quest `Q00186_ContractExecution`: normal-level stock start, minimum `41`, prerequisite `Q00184`, certificate/item/progress gates, over-level Personal relief только для XP/SP cap, base Adena и rate ownership сохранены; ordinary Player, Phantom и feature OFF остаются stock.
5. Curated XML-owned progression store: exact item/count/price allowlist, preview/transaction parity, insufficient Adena, safe repeat, protected route и forged generic request denial.
6. Real Player auto-Noblesse: OFF/no-subclass/below-threshold denial; stored subclass `75+` при active main class; canonical skills/persistence; repeat idempotence; без Hero/quest completion/tiara.
7. Phantom materialization auto-Noblesse: canonical stored subclass eligibility, idempotence и отсутствие free progression grants.

Test fixture использовал только allowlisted local schema `l2jmobiush5_phantom_test`.

### Affected, freeze и build evidence

- Goal037 AST regeneration/audit — `PASS`: `543` quest sources, `7066` rate sites, `0` failures, `0` corrections.
- `ant -q qol-008-verify` — final `PASS`: focused QOL-008, affected QOL-001..007/QOL-007 transitive freeze, Goal037 и Goal039 static/documentation freeze.
- Во время qualification были исправлены три детерминированно локализованные причины: stale Q00128 source pin; mixed-EOL raw hash для Goal039 release matrix без content diff; воспроизводимая Phantom `_skillListTask` teardown race. После исправлений final aggregate прошёл полностью.
- Ровно один fresh full `ant verify` — `PASS`, `BUILD SUCCESSFUL`, `32 minutes 52 seconds`. Повторный full verify не запускался.
- Standalone `ant -q jar` — `PASS`, `16 seconds`.
- `LoginServer.jar` успешно перечислен через `jar tf`; размер `313194` bytes; SHA-256 `6EEDC4FF9E2FB414A439B27617BBEED3035B2EB59B528C83D8D4F0810532249D`.
- `GameServer.jar` успешно перечислен через `jar tf`; размер `9075334` bytes; SHA-256 `997B3972A43A76259DF383D8CD4FA75D37F19E9D2AD185D55AF06AA9A9A44A0F`.
- QOL-004 `dist/game/data/multisell/91001.xml` exact diff check — unchanged.
- `git diff --check` — clean перед full verification; staged exact-scope check выполняется перед commit.

### Safety, encoding и untested scope

- Production DB: `NOT_USED`.
- `prepare-phantom-test-db`: `NOT_RUN`.
- Client patch: `NOT_USED`.
- Runtime LLM: `NOT_USED`.
- Mammon force-spawn / Seven Signs lifecycle rewrite: `NOT_USED`.
- Summoner/Servitor work: `NOT_USED`.
- `NOT_TESTED_CLIENT_UI`: Alt+B contract проверен на server-rendered board route, preview и native transaction; визуальная проверка реальным High Five клиентом не выполнялась.
- Mojibake-маркеры в изменённых файлах проверены отдельно: `0` совпадений.
- Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены отдельно: `0` совпадений.
- Strict UTF-8 scan изменённых текстовых файлов: `0` invalid files.
- Control-character scan изменённых файлов: `0` совпадений.
- Три существовавших до задачи unrelated user modifications и unrelated untracked task/history artifacts сохранены и исключаются из exact staged scope.

### Git closure contract

- Parent до commit должен оставаться exact `3cf8f5d35997ff65e5e484c1c3d6224d7f759859` на `feature/phantom-world`.
- Разрешён ровно один commit с subject `qol(008): close economy and progression gaps`.
- Разрешён только normal non-force push `feature/phantom-world`; после push remote branch SHA должен равняться local `HEAD`.
- Commit SHA, remote SHA и фактический результат этого post-report шага фиксируются в итоговом delivery evidence.
