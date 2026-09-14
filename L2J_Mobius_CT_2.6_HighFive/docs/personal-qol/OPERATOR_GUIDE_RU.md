# Personal/Premium QoL — инструкция оператора

## Файлы и включение

- конфигурация: `dist/game/config/Custom/PersonalPremiumQoL.ini`;
- каталог tier: `dist/game/data/custom/personal-qol/level-gap-items.xml`;
- магазин: `dist/game/data/multisell/91001.xml`;
- curated progression-магазин: `dist/game/data/multisell/91002.xml`;
- конфигурация progression: `dist/game/config/Custom/PersonalProgressionQoL.ini`;
- страница: `dist/game/data/html/CommunityBoard/Custom/personal-qol/main.html`.

Поставка безопасная: оба ключа выключены.

```ini
EnablePersonalPremiumQoL=False
EnablePersonalPremiumShop=False
LevelGapItemsFile=data/custom/personal-qol/level-gap-items.xml
```

Для эффекта включите `EnablePersonalPremiumQoL=True` и перезапустите Game Server. Для магазина дополнительно нужны `EnablePersonalPremiumShop=True`, штатный `EnableCommunityBoard=True` и `EnableCustomCommunityBoard=True`. Модуль ничего из этого не включает молча. Изменения INI/XML требуют перезапуска.

## Предметы

| Item ID | Исходное имя H5 в инвентаре | Русская метка в Alt+B | N | Финальная цена |
|---:|---|---|---:|---:|
| 22290 | Recipe: Happy Cake - Event | Оберег разницы уровней: 5 | 5 | 100 000 Adena |
| 22296 | Cake Ingredient: Dark Chocolate - Event | Оберег разницы уровней: 10 | 10 | 500 000 Adena |
| 22297 | Cake Ingredient: White Chocolate - Event | Оберег разницы уровней: 20 | 20 | 2 000 000 Adena |
| 22298 | Cake Ingredient: Creme Fraiche - Event | Оберег разницы уровней: 40 | 40 | 8 000 000 Adena |

Это проверенные stackable inert H5 templates без handler/skill/action/timer/condition/reference-price поведения. Сами item templates не изменены, поэтому client patch не нужен и stock-клиент показывает исходные английские имена/иконки. Русские tier labels существуют только на серверной странице Alt+B.

## Семантика защиты

Сервер просматривает только четыре настроенных item ID в основном инвентаре reward actor. Если найдено несколько предметов или несколько tier, применяется один максимальный N. Предмет не расходуется. Склад не считается инвентарём: после перемещения со склада эффект появляется, после перемещения на склад исчезает; обычный relog сохраняет предмет и эффект.

Граница включительная и симметричная: при `abs(actual native level gap) <= N` только native DROP/SPOIL level-gap multiplier поднимается до нейтрального значения. Если native chance уже не ниже нейтральной, она не уменьшается. При `N+1` и дальше используется stock formula без поправки. Уже созданный drop/spoil не пересчитывается.

Reward actor остаётся штатным: для party/servitor действует тот Player, которого выбирает текущий native reward path. Предмет другого члена группы не создаёт ауру. Phantom/Fake Player reward actor всегда исключён.

Предмет не даёт Spoil skill, не меняет шанс успешного Spoil cast, не гарантирует выпадение или количество, не меняет RNG/check order, XP/SP, quest rewards/rates, manor и raid curse.

## Личный allowlist QOL-002

Конфигурация: `dist/game/config/Custom/PersonalCharacterQoL.ini`. Она независима от QOL-001 и поставляется полностью выключенной:

```ini
EnablePersonalCharacterQoL=False
EnablePersonalCrossClassSkills=False
EnablePersonalCrystallization=False
EnablePersonalSevenSignsAccess=False
EnablePersonalPartySupport=False
AllowedCharacterIds=
AllowedAccounts=
EnablePersonalEffectDurations=False
PersonalBuffDurationMultiplier=1.0
PersonalDanceDurationMultiplier=1.0
PersonalSongDurationMultiplier=1.0
PersonalEffectDurationOverrides=
```

Для одного или нескольких персонажей включите master switch и нужные subfeature, затем заполните хотя бы один allowlist. `AllowedCharacterIds` принимает положительные decimal object ID персонажей, `AllowedAccounts` — имена аккаунтов без пробелов; регистр аккаунта не учитывается. Разделители — запятая или точка с запятой, максимум 256 значений и 4096 символов на список. Пример с синтетическими значениями:

```ini
EnablePersonalCharacterQoL=True
EnablePersonalCrossClassSkills=True
EnablePersonalCrystallization=True
EnablePersonalSevenSignsAccess=True
EnablePersonalPartySupport=True
AllowedCharacterIds=100001;100002
AllowedAccounts=test_account
```

После изменения нужен перезапуск Game Server. Ошибка базовых ключей или allowlist отключает QOL-002; QOL-001 продолжает использовать свой отдельный конфиг. Ошибка только в duration-ключах изолированно отключает QOL-003. Пустые allowlist не разрешают доступ никому. Headless Phantom не допускается даже при совпадении ID/account.

## Верхнеуровневые квесты и auto-Noblesse

Обе новые возможности находятся в отдельном файле `dist/game/config/Custom/PersonalProgressionQoL.ini` и поставляются выключенными:

```ini
EnablePersonalQuestOverLevelRelief=False
EnableServerWideAutoNoblesse=False
```

После изменения перезапустите Game Server. Оба значения принимают только `True` или `False`; malformed-файл закрывает обе возможности fail-closed.

`EnablePersonalQuestOverLevelRelief=True` дополнительно требует действующие `EnablePersonalCharacterQoL=True` и allowlist из `PersonalCharacterQoL.ini`. Оно применяется только к real Player и только к явно проаудированным upper/too-high predicates. Minimum level, race/class, prerequisite quest, QuestState, item/kill/party/cooldown/repeatability и reward-rate правила не обходятся. Headless Phantom и ordinary Player всегда используют stock behavior. В частности, Q186 всё ещё требует level 41, завершённый Q184 и Loraine's Certificate; персональное relief только возвращает XP/SP ветку на уровне 47 и выше, а множители остаются из `Rates.ini`.

`EnableServerWideAutoNoblesse=True` — независимая server-wide настройка: Personal allowlist для неё не используется. При login, реальном level-up активного subclass и Phantom materialization сервер ищет любой сохранённый subclass level 75 или выше. Подходящий real или Phantom Player один раз проходит canonical `Player.setNoble(true)` и штатный store. Main class может быть активен. Функция не завершает Q247, не выдаёт tiara/quest rewards, не делает Hero и не меняет class/subclass identity. Повторная проверка уже Noble Player ничего не делает.

Ancient Adena не получила отдельного переключателя: штатные seal-stone drops, Seven Signs exchange `3/5/10`, Black Marketeer daily exchange и существующие quest paths уже дают canonical acquisition. QOL-008 не включает и не force-spawnит Mammon NPC.

## Личный Seven Signs access

`EnablePersonalSevenSignsAccess=True` действует только внутри `EnablePersonalCharacterQoL=True` и для тех же allowlist. На любой странице Dawn/Dusk Priest такому real Player добавляется отдельная ссылка в штатный `HuntingGroundsTeleport`; исходные действия страницы не скрываются. Дальше сервер разрешает только player-specific регистрацию/cabal/winner admission в Catacomb/Necropolis и не выполняет cabal-only ejection при смене периода или relog.

Эта функция не записывает синтетическую Seven Signs регистрацию, не выбирает seal и не меняет contributions, score, festival, winner или награды. В Seal Validation остаются обязательными реальный глобальный winner и соответствующий owner Avarice/Gnosis. Обычные игроки и headless Phantom получают точное stock-поведение.

Normal combat population Catacomb/Necropolis не переключается Seven Signs controller: 14 штатных spawn lists всегда загружаются общим `SpawnData` и используют свои обычные respawn delays. Mammon, preacher/orator, crests и другие special/event NPC остаются отдельным stock lifecycle. Функция не force-spawnит Merchant/Blacksmith of Mammon; если они штатно существуют, personal bypass касается только player cabal/winner interaction, а global period/seal/winner и экономика остаются обязательными.

Dimensional Rift уже не имеет cabal/period gate в waiting-room, start или combat-room population. Функция его не упрощает: по-прежнему нужны party leader, `RIFT_MIN_PARTY_SIZE`, свободная capacity, присутствие всех участников в waiting room и native Dimensional Fragment `7079` у каждого. Сохраняются штатное списание, первый combat room, spawn cadence/respawn, jump limit/timers, leader-only manual jump/exit, low-member return, quest/session cleanup и relog return в waiting room.

## Поддержка своей группы

`EnablePersonalPartySupport=True` действует только внутри `EnablePersonalCharacterQoL=True` и для тех же allowlist real Player. После изменения конфигурации перезапустите Game Server. В Alt+B откройте `Личная QoL-панель` → `Расходники` (`Расходники и утилиты`): сервер выводит текущего персонажа и текущих online Player-участников его группы с доступными кнопками `Восстановить`, `Воскресить` и `Снять карму`.

Каждое нажатие заново разрешает object ID через `World`, повторно проверяет actor eligibility и актуальную принадлежность цели той же `Party`. Устаревший, поддельный или malformed bypass, вышедший из группы участник и outsider не изменяются. Actor всегда только настроенный real personal Player; обычный Player и headless Phantom не получают привилегию. Целью может быть сам actor или текущий Player-участник его собственной группы.

`Восстановить` для живой цели вызывает штатный `Player.fullRestore()` и доводит текущие HP/MP/CP до текущих максимумов. `Воскресить` принимает только мёртвую цель без resurrection block и использует узкий `Player.doRevive()` без выдуманного возврата XP. `Снять карму` меняет только положительную H5 karma-пенальти на `0` через `Player.setKarma(0)`; PvP/PK counters, clan reputation, fame, recommendations, Seven Signs, inventory, skills и quests не затрагиваются. Все действия запрещены в combat/casting, duel, Olympiad, siege/PvP, event, store, teleport/observer/vehicle, jail, cursed weapon, nonzero instance и Dimensional Rift.

Remote private message уже полностью обслуживается штатным `Say2` → `ChatHandler` → whisper handler с фильтрами, block list, jail/chat-ban, event hooks и logging. Exact-name remote invite уже проходит через `RequestJoinParty` → `PartyInvitationService` и сохраняет pending invitation, accept/refuse, timeout и повторную проверку. Summon Friend skill `1403` уже имеет self-anchored `myPartyExceptMe` flow через `CallPc`/`SummonRequestHolder`, переносит только текущего party member к actor и сохраняет native zone/instance/Olympiad/event/jail/resource/Rift/Seven Signs gates. Поэтому QOL-006 не добавляет второй PM/invite transport, direct party insertion, auto-accept или teleport service.

## Humanized Semantic Pack v2

При штатном `EnablePhantomHumanizedConversation=True` сервер загружает совместимый humanized-v1 base, затем отдельные каталоги `data/phantoms/semantic/humanized/high-five-ru-humanized-semantic-v2.xml` и `data/phantoms/conversation/humanized/high-five-ru-humanized-conversation-v2.xml`, после чего — существующие operator custom overlays. Functional `high-five-ru-semantic-v1.xml` и `high-five-ru-corpus-v1.tsv` остаются отдельным неизменённым владельцем команд. Изменение любого production humanized-v2 или base/custom input меняет детерминированный content hash; malformed ID/version, UTF-8, XML, размер, duplicate alias/ID/response или неизвестный class ID закрывает загрузку fail-closed.

Ответы Phantom о собственном имени используют live visible name, о поле — canonical appearance sex, о профессии — текущий active `PlayerClass`. Snapshot читается под существующим action lease и не записывает профиль, персонажа или БД. Exact-class alias подтверждает только совпадение canonical class ID. Общие слова вроде `танк`, `хил`, `маг`, `суммонер` и `котовод` относятся к явно заданной role family; они никогда не превращаются в произвольно выбранный exact class. Линия `Warlock`/`Arcana Lord` задана как ограниченное множество class ID `14/96`.

Не редактируйте shipped v1/v2 файлы для локальных реплик. Добавления и intentional override stable ID размещайте в прежних `data/phantoms/semantic/custom/` и `data/phantoms/conversation/custom/`: эти файлы остаются последними по precedence и проходят те же bounds/security/response-uniqueness проверки. Изменения требуют перезапуска Game Server. Для полного отключения humanized planner установите `EnablePhantomHumanizedConversation=False`; функциональные команды продолжат использовать semantic v1.

## Личная длительность эффектов

`EnablePersonalEffectDurations=True` включает QOL-003 только внутри общего `EnablePersonalCharacterQoL=True` и только для тех же allowlist. Отдельные множители принимают конечное decimal-значение от `0.01` до `100.0`. Десятичный разделитель — точка. Override имеет формат `positiveSkillId,multiplier`, записи разделяются точкой с запятой; максимум 256 записей и 4096 символов. Повторяющиеся skill ID, неположительные ID, нечисловые и бесконечные значения отклоняют весь duration-блок.

Пример:

```ini
EnablePersonalEffectDurations=True
PersonalBuffDurationMultiplier=2.0
PersonalDanceDurationMultiplier=1.5
PersonalSongDurationMultiplier=1.25
PersonalEffectDurationOverrides=1068,3.0;269,2.0
```

Override заменяет, а не умножает category multiplier. Сначала сервер получает полный stock duration с учётом `SkillDurationList` и штатных правил, затем один раз применяет personal multiplier к конкретному получателю. Положительный explicit `abnormalTime` при наложении эффекта остаётся точным и повторно не умножается. Recast, relog/restore и refresh используют исходное сохранённое время без накопительного умножения.

Категория song/dance подтверждается реальным H5 class tree: song принадлежит `SWORDSINGER`/`SWORD_MUSE`, dance — `BLADEDANCER`/`SPECTRAL_DANCER`, и шаблон навыка должен иметь штатный dance-флаг. Неизвестное или конфликтное происхождение сохраняет stock duration. Passive, toggle, triggered, abnormal-instant, debuff/negative effects также исключены. Изменение QOL-003 действует только на real Player; active subclass допускается, а headless Phantom, summon/pet и NPC всегда остаются stock.

Все изменения этого блока требуют перезапуска Game Server. Если новые ключи отсутствуют, конфигурация считается legacy-valid и QOL-003 выключен. Ошибка только в новых ключах отключает duration feature, но не выключает валидные QOL-001/QOL-002 и не стирает базовые allowlist.

## Cross-class обучение

Глобальный `AltGameSkillLearn` оставляйте в прежнем состоянии; для personal path включать его не требуется. Allowlisted Player на основной профессии использует обычный `SkillList` у NPC-наставника. Выбранная профессия должна реально входить в production teach set этого NPC, а её hierarchy level не может быть выше активной профессии игрока.

Доступны только штатные `CLASS`-навыки, изучаемые у NPC: forgotten-scroll и auto-get не добавляются, GM/hero/clan/subclass/transform trees не расширяются. Сервер повторно проверяет trainer, выбранный class, level, previous level, prerequisites, required items и SP непосредственно перед mutation. Для чужой профессии действует native alternative цена: одинаковый fighter/mage тип — 2x, противоположный — 3x; свой class — 1x. Изученные навыки сохраняются обычным `character_skills` path и восстанавливаются после relog, пока персонаж остаётся allowlisted и функция включена.

## Кристаллизация

Race bypass отсутствует: персонаж любой расы обязан реально знать `CRYSTALLIZE` нужного уровня. Native client packet продолжает работать; если H5 client не показывает действие non-dwarf, доступен server-side fallback на личной странице Alt+B.

Alt+B выводит только ограниченный список подходящих предметов. После выбора показываются exact item/count/enchant и ожидаемый `Item.getCrystalCount()` result. Подтверждение необратимо, одноразово, действует не более 120 секунд и заменяется при подготовке другого предмета. При replay, смене владельца, count/enchant/item drift, отзыве allowlist или гонке с native packet операция закрывается без повторного credit.

Сохраняются native ограничения: достаточный уровень навыка для D/C/B/A/S grade, ownership/manipulation, store/in-flight state, hero/shadow/time-limited/augmentation и `isCrystallizable`. Экипированный предмет сначала снимается. Кристаллы начисляются только после точного destruction; `inCrystallize` очищается в `finally`.

## Магазин и цены

Alt+B вызывает отдельные guarded routes, которые подготавливают native multisell `91001` для level-gap pass и `91002` для curated clan progression. Generic multisell bypass для обоих list ID запрещён. При execute повторно проверяются dedicated provenance и live `EnablePersonalPremiumShop`; поэтому prepared до выключения список после disable отклоняется до списания.

Оплата, ownership, capacity, inventory slots/weight и flood protection остаются штатными в `MultiSellChoose`/multisell engine.

Финальные цены хранятся только в `91001.xml` и `91002.xml`; Alt+B получает их из валидированных runtime snapshots, а не из HTML или Java literals. Точный audit, anchors и таблицы владельцев данных находятся в `docs/personal-qol/SHOP_PRICING.md`. Для замены цены измените `count` у соответствующего `<ingredient id="57">`, выполните соответствующий focused test и перезапустите Game Server. Одного `//reload multisell` недостаточно: runtime metadata панели кэшируется при старте.

Guard `91001` принимает только Adena `57` и четыре ожидаемых tier-carrier. Guard `91002` принимает ровно пять canonical clan inventory prerequisites: Blood Mark `1419 x1`, Alliance Manifesto `3874 x1`, Seal of Aspiration `3870 x1`, Blood Oath `9910 x150`, Blood Alliance `9911 x5`. Предметы сами не повышают clan level: `Clan.levelUpClan` по-прежнему проверяет текущий level, SP/CRP/members/territory и сам потребляет нужное количество. Stateful quest tokens, Noblesse items, raid jewelry, equipment, enchant dump и arbitrary rare loot не продаются. Другая валюта или новый товар требуют отдельного economy audit, кодового изменения и теста; простой XML append fail-closed отклоняется.

## Личные настройки EXP и трав

Панель Alt+B доступна через категорию `Персонаж / EXP` и меняет тот же persisted `EXPOFF`, что команды `.expon` и `.expoff`. После relog состояние восстанавливается прежним login path. Отдельной модели состояния у панели нет.

Категория `Травы` хранит для текущего персонажа три независимых выключателя: восстановление HP/MP, боевые усиления и Vitality. По умолчанию все категории включены, то есть поведение полностью штатное. При выключении известная H5 herb всё равно подбирается и уничтожается native pickup flow, но effect handler/cast не вызывается; предмет в инвентаре не остаётся. Настройка одного персонажа не влияет на другого. Неизвестные immediate-effect items и headless Phantom всегда используют vanilla path.

Аудированный набор строится из H5 item/skill XML: recovery `8154/8155`, `8600–8605`, `8614`, `8952/8953`, `10432/10433`, `14777/14779`; Vitality `13028–13031`, `20273`, `20926`; остальные известные ex-immediate herbs относятся к combat. Pet flow не менялся.

Mana Potion `728`, Vitality items `20034/20391/20392` и XP rune `21084` технически присутствуют в H5 data, но безопасный Adena retail owner не подтверждён, а часть template premium/non-trade/non-sellable. Они намеренно не продаются в L2-QOL-004.

## Откат

Для QOL-001 установите оба switch в `PersonalPremiumQoL.ini` в `False`. Для QOL-002 установите `EnablePersonalCharacterQoL=False` или выключите отдельные subfeature в `PersonalCharacterQoL.ini`. Для отдельного отката QOL-003 установите `EnablePersonalEffectDurations=False`; для отдельного отката QOL-005 — `EnablePersonalSevenSignsAccess=False`; для отдельного отката QOL-006 — `EnablePersonalPartySupport=False`. Для operational rollback QOL-007 установите `EnablePhantomHumanizedConversation=False`. Для QOL-008 установите оба ключа `PersonalProgressionQoL.ini` в `False`; закрытие progression-витрины выполняется прежним `EnablePersonalPremiumShop=False`. Затем перезапустите Game Server. DB migration отсутствует. Купленные предметы останутся обычными stock items; уже полученный canonical Noble status не снимается. Stale shop/crystallization execution будет отклонён до debit/credit. Изученные foreign skills хранятся штатно; при выключенном admission штатный skill checker может удалить их как недопустимые при следующем restore. Уже наложенные эффекты сохраняют записанное остаточное время, новые эффекты после перезапуска используют stock duration. После отключения QOL-005 следующий admission/relog/period check снова применяет stock Seven Signs eligibility. После отключения QOL-006 новые support bypass отклоняются до mutation; уже выполненные heal/revive/karma cleanup не откатываются. QOL-007 не создаёт persistent state. Phantom schema не меняется.

## Проверка

Обязательная серверная composition:

```text
ant qol-level-gap-test
ant qol-shop-test
ant qol-personal-skills-test
ant qol-crystallization-test
ant qol-effect-duration-test
ant qol-004-storefront-controls-test
ant qol-004-affected-test
ant qol-004-verify
ant qol-seven-signs-access-test
ant qol-005-affected-test
ant qol-005-freeze-test
ant qol-005-verify
ant qol-party-support-test
ant qol-006-affected-test
ant qol-006-freeze-test
ant qol-006-verify
ant qol-semantic-v2-test
ant qol-007-affected-test
ant qol-007-freeze-test
ant qol-007-verify
ant qol-economy-progression-closure-test
ant qol-008-affected-test
ant qol-008-freeze-test
ant qol-008-verify
ant qol-002-affected-test
ant qol-002-verify
ant qol-003-affected-test
ant qol-003-verify
ant verify
ant -q jar
```

`qol-level-gap-test` покрывает grouped/ungrouped/spoil, boundaries, inventory/relog и bot exclusions. `qol-shop-test` использует реальные native debit/credit и отрицательные prepare/execute/flood/capacity controls; в L2-QOL-004 он также проверяет data-driven prices, Java-8 script routes, единый EXP owner и persisted/isolation herb state. Focused QOL-002 targets покрывают personal policy, настоящий `RequestAcquireSkill`, relog и required items, а также native/common/Alt+B crystallization, replay/stale/expiry/concurrency. `qol-effect-duration-test` покрывает strict config isolation/master OFF, pure policy/rounding, реальные H5 buff/song/dance/debuff, self/NPC/ordinary/headless/summon recipient semantics, global-then-personal ordering, Skill immutability, override, explicit/steal-copy time, recast и restore/relog. `qol-seven-signs-access-test` покрывает shipped OFF/real-only policy, все четыре Seven Signs периода, Catacomb/Necropolis route/admission/continued/relog wiring, полный normal combat spawn census и actual respawn, native Rift prerequisites/first/jump populations/cleanup и Mammon global boundary. `qol-party-support-test` покрывает native PM/invite/Summon Friend no-op census, shipped-OFF/real-only authority, strict board parser, self/own-party heal, stale/outsider denial, narrow revive без XP, karma-only cleanup и сохранение unrelated state. `qol-semantic-v2-test` покрывает versioned/bounded/content-addressed v2 load, malformed/XXE/UTF-8/size/collision negative controls, canonical name/gender/current-class identity, exact и ambiguous role aliases, social variation и сохранение v1/custom/safety gates. `qol-economy-progression-closure-test` покрывает native Ancient Adena/resource census, exactly-once rate authority, 26 upper gates, полный Q186 Leto path, strict shipped-OFF controls, XML-owned progression transactions и real/Phantom canonical auto-Noblesse persistence/idempotence. База должна быть заранее подготовленным allowlisted test schema по действующей Phantom test policy; `prepare-phantom-test-db` в этом workflow не запускается.

Клиентский визуальный статус релиза: **NOT_TESTED_CLIENT_UI**.

L2-QOL-001/002/003/004/005/006/007/008 завершены со статусом SUCCESS. QOL-009 Summoner/Servitor combat hardening остаётся незавершённым и автоматически не запускается.
