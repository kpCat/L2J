# Контракт сохранения игровых настроек владельца — 25.10.2026

Authority: PHANTOM-OWNER-CONFIG-INTEGRATION-088 и явные решения владельца в диалоге. Этот документ фиксирует публичную конфигурацию source и её перенос в будущую финальную поставку. Пользовательская сборка в088 не создаётся. Фактические проверки и исключения: [отчёт088](reports/PHANTOM-OWNER-CONFIG-INTEGRATION-088.md).

## Канонический набор и исключения

Источник — неизменённый пакет `docs/phantoms/tasks/PHANTOM-OWNER-CONFIG-INTEGRATION-088/`: RAW19 и APPROVED19. SHA256 INPUT_MANIFEST.json — `efa9dc709767d2fb877569e97bbc0ed090a2907a84a744523f2058335dba3694`; MANIFEST_SHA256.json — `2b2ce8abf7bde377fa9cdd2c5563e4693d52161005891ee096f5ea5dfc87f9a8`. Пакет не является game config и не заменяет весь каталог конфигурации.

Согласованные изменения RAW→APPROVED: только `General.ini / RiftMinPartySize: 1→2` и `Custom/PersonalProgressionQoL.ini / EnableServerWideAutoNoblesse: True→False`. Остальные решения владельца сохраняются.

Дополнительное явное решение после первоначального BLOCKED: удалить ровно завершающий U+0020 в `Player.ini / FakeDeathUntarget = True ` в stage/source. Результат — `FakeDeathUntarget = True`, принимаемый штатным OwnerConfig и native ConfigReader. RAW/APPROVED и manifests не меняются. Нормализованный Player.ini имеет SHA256 `b3b4ffdde9bcbec1b5844b93f675cd6d0ba29e3759867b09f80a2f2cc2b3c8c7`; это единственное отличие итогового public source от APPROVED bytes. Изменять другие пробелы/значения под видом нормализации нельзя.

Три ошибочных baseline blob в INPUT_MANIFEST были отдельно разрешены владельцем к сверке по фактическому `c5d5c525f53eaea6ea6b774583040bb3f2903821`; точные старые/правильные SHA1 приведены в отчёте. Это исключение identity, не разрешение менять дополнительные настройки.

Все пути таблицы относительны к `dist/game/config/`. RAW SHA256 равен APPROVED, кроме двух policy-файлов, указанных после таблицы. Final source SHA256 равен APPROVED, кроме нормализованного Player.ini выше. Различия CRLF в Git checkout не считаются изменением настройки; сравнение релизного файла выполняется по каноническим public bytes и parsed values с явным учётом line endings.

| Файл | APPROVED SHA256 |
|---|---|
| ClassMaster.xml | `c1165828e7e41741852bebaa2536203374be06d93db300305a9b2d2143576e5d` |
| Custom/ChampionMonsters.ini | `5ee572c2d3ec9027a1800cdaa508df4a98f10cb7207256386c99444f6553d0e5` |
| Custom/CommunityBoard.ini | `5b1832d9ea3cb255e5184f0d3419262a16093a942897621f8eae7aa89fc6dfd4` |
| Custom/NoblessMaster.ini | `847e10f13ea5f314f7ab3060b794c18a4d7c318b74985cb6ad54a2064d6b6d06` |
| Custom/PersonalCharacterQoL.ini | `36756116575b9eb17b4e0a34a85c169f2830b650b04c48638ef8f6e608c36932` |
| Custom/PersonalPremiumQoL.ini | `1424654b82542b038560f4e6d2caab9ff328834bbdaf05f71cbd2e90d9afc1a1` |
| Custom/PersonalProgressionQoL.ini | `77aedacbfdb79080d14f0326f0c77f417a70a6782397d8f6554dd24aa674ee8f` |
| Feature.ini | `814546a1465cd3bb26214483c89c5333da1fbb864cf8d226646acc5f97b64e2f` |
| FortSiege.ini | `ccf251db5fff8586a53dc434ad3d948005d93500d0faa2259a9500b6be574552` |
| General.ini | `51494431204935eba443aee66cc454ddb420b8eef759a9bea965b684eae2a242` |
| GraciaSeeds.ini | `58e13ceceee8e8fee57b310280a418f9e508e9dd1f42861557c447796b4b12f2` |
| GrandBoss.ini | `bc7b1a2bb2013a22ff8bdecfaf3c1f025f5663fa6ed64f4ef34cc5b1adf2026c` |
| NPC.ini | `d73bb9fc68c7ecdd3408804c1a0e9e9d11993f31aeee71a5366ff177f2aae639` |
| Olympiad.ini | `d88f88f84760b8671f67af86e3da5d851f8101bc4eda6886804503bc7c396574` |
| PVP.ini | `7118863b9cc75277f646b64ccaac30eecb18fd487b4e284ff37a2ec51f762d2f` |
| Player.ini | `359bf3081efaff051c5c226737ffb680ee77c335a63e667470a0c34819b993b4` |
| Rates.ini | `ac5a73283fc2f2f582dcbab38ac9128d7553b475dcf74700229ce13a90a67244` |
| Siege.ini | `4352914bc4cefc2fa866063597fedf2b55d67f70145e641806fd5fa29aa67a9d` |
| TerritoryWar.ini | `c587b5f9430cb01d76cda1396bb28a0d0c54ac78fe231e8401d72606b1e3156c` |

RAW General.ini: `e92ade600462856f0af14ea83399c8daeedd883b1c4bef39e9030f84bace3fd9`. RAW PersonalProgressionQoL.ini: `2053004df57f23717aba0afe1d1447f3ce9decbe93a2c8365719e041699b50d2`.

## Значения, которые нельзя сбрасывать в будущей поставке

| Семейство | Выбор владельца |
|---|---|
| Rates | XP3, SP5, party XP/SP2; quest XP25/SP35/Adena25/items50, остальные quest keys как в полном файле; UseQuestRewardMultipliers=False |
| Drop/spoil/raid | Amount/chance: ordinary10/8, spoil20/10, raid10/10; item-specific57,1 и event chance0 не нейтрализовать |
| Champions | RED enabled5%, HP divisor50; BLUE enabled10%, HP divisor10; полные rewards/instances без изменений |
| Community Board | Custom=True, currency57; teleport100000, buff5000, heal50000, delevel1000; premium1000000 Adena/day; legacy General switch отдельно |
| Character QoL | Включённые персональные flags, durations5/15/15, empty duration overrides, positive augmentation5, solo instance/Rift, Cubic10min, fishing/Henna как в файле |
| Admission | AllowedAccounts и AllowedCharacterIds пусты в public source; без wildcard/GM/выдуманного login |
| Progression/NoblessMaster | Server-wide auto Noblesse=False; paid master enabled, level80, Adena57×500000000; General RiftMinPartySize2 |
| Player | NoDwarf/Quest inventory250; выбранные остальные slots/craft/carry/skills/enchant параметры сохраняются полностью; Unstuck30, stock random town respawn=True/teleport offset50 |
| Enchant/servitor | Существующие38 правил: ordinary cap25, protection включена, servitor11 multipliers×2; специальные scroll caps сохраняются |
| ClassMaster | Byte-exact approved XML: native options/conditions, FIRST_CLASS_GROUP free, SECOND_CLASS_GROUP57×1000000; закомментированный third-class пример не активировать |
| Остальные полные файлы | Feature/FortSiege/GraciaSeeds/GrandBoss/NPC/Olympiad/PVP/Siege/TerritoryWar — весь approved набор, а не только краткая таблица |

## Штатное сохранение и применение

Все три файла metadata получены штатным `tools/OwnerConfig.ps1`, без ручного JSON/TSV writer. На момент088:68 public INI,271 overrides и38 правил Java verifier. Byte SHA256:

| Metadata | SHA256 |
|---|---|
| OwnerConfig.json | `9cd3cd0cc68f7eab363aaf444e9f291a1f5af701f174b6fc493d4f4df7c47df0` |
| OwnerProfile38.tsv | `5670fa6bc0bd6457cfac849bd2c45e24dbd317d86d501015b3ab4624555312db` |
| OwnerProfilePreserved.properties | `fed46ac78630cef11b617a8caf4e624aed88ac19bcff29adbc69de9ad1710529` |

Quest logical digest — `e6ba4e5a07c537000fc8b5680e1276cd3f82112f6e19a1c69186c608e46b3a67`. Verifier preservedSha256 является нормализованным digest properties, не byte hash файла.

Для будущей финальной сборки, в отдельно разрешённой среде с JDK25:

1. Проверить точную ветку/source, user dirt, public hashes и этот контракт. При конфликте — STOP/three-way analysis, без reset/restore/clean и без молчаливой замены owner values.
2. После новых осознанных правок владельца использовать штатный `SaveOwnerConfig.bat` либо `OwnerConfig.ps1 -Action Save`, затем `-Action Verify` на source. JSON/TSV/properties сохраняются вместе. Существующие overrides хранят baseValue: последующий Save после commit не должен превращать выбор владельца в потерянный default.
3. Создать отдельную физическую staged copy, затем штатный `OwnerConfig.ps1 -Action Apply -GameRoot <absolute-game> -ProfilePath <absolute-source-OwnerConfig.json>` и `Verify` с теми же аргументами. Проверить all-key equality всех18 INI, unrelated defaults и repeat Apply idempotence;38 правил не заменяют эту широкую сверку.
4. ClassMaster.xml не управляется INI pipeline: отдельно перенести точный согласованный XML и проверить SHA/XSD/native options. Не делать массовый overlay из task inputs поверх game и не удалять другие конфиги.
5. Только после GREEN выполнять отдельно разрешённую финальную сборку/поставку с public settings и private activation path. Проверить effective runtime/HTML handlers/клиент в рамках её собственных gates.088 этого не доказывает.

Source Verify088 выполнен без source Save после точного переноса22 targets; Apply на независимой копии совпал по1038 INI keys, ClassMaster byte-exact, repeat Apply changed0. Siege.ini уже соответствует approved и не требует искусственной дельты.

## Приватность и открытые продуктовые границы

**PERSONAL_USER_ACTIVATION=FINAL_PRIVATE_INPUT_PENDING.** Реальный account/objectId вводится владельцем только в private финальном развёртывании, не в Git/ZIP. Пустая allowlist не является ошибкой сохранения и не даёт права выдать персональные функции всем. Private activation должна иметь отдельный apply/validation path, сохраняя сравнимость public keys.

**PREMIUM_OWNER_ADMISSION_OPEN.** Текущий premium level-gap service требует item и nonheadless Player, но не owner allowlist; специальные storefront/purchase guards также не обеспечивают owner-only. Enabled-флаги сохранены по решению владельца. Эксклюзивность premium не объявлена; перед обещанием персональной доступности нужен отдельный causal admission fix/audit, описанный в отчёте088.

AutoPlay=False/AutoPotions=False и существующие GeoEngine/Threads/Network/Grid1/90/PhantomPlayers/PhantomMerchants не изменяются этой задачей. PhantomPolicy и owned native potion используют существующие отдельные пути; нагрузка5000/cap128+/минимум200 native stores требует будущих performance доказательств. Не активировать demo/массовые profiles/merchant заодно с переносом конфига.

**NAV_087_OPEN; NAVIGATION_FIXED=NOT_ATTEMPTED_088.** SEARCH_LIMIT087 — исчерпание finite budget на длинных starter→GK legs, не доказанная непроходимость. Будущая089 требует hierarchical region route, finite geo-attested corridor/waypoint legs, native GK debit и durable checkpoint/recovery с causal RED/GREEN. Не менять владельцу RandomRespawn/MaxOffset/Unstuck ради обхода; не увеличивать budgets и не добавлять фиктивные teleports/edges в088.089/R2 автоматически не начинать.

Database.ini, Network.ini, hexid.txt, реальные IDs/аккаунты и защищённые поставки остаются вне публичного набора и вне разрешения этой задачи. Финальный release остаётся отдельной будущей задачей; этот контракт не является разрешением запустить его сейчас.

## Дополнение TASK090 — 25.10.2026

Новое явное решение владельца: текущий `Rates.ini / RateQuestReward = 50` вместо исторического `1`. RAW/APPROVED088, их таблица SHA256 выше и manifests088 неизменны. Это единственное новое отклонение effective public config: сравнение всех68 public INI/1521 ключа дало ровно `RateQuestReward: 1→50`; остальные67 INI и `ClassMaster.xml` byte-identical исходному088/089 baseline. Все271 прежних overrides сохранены с теми же file/key/kind/baseValue/value; штатный Save добавил272-ю запись `Rates.ini / RateQuestReward`, kind=number, baseValue=1, value=50.

`UseQuestRewardMultipliers=False`, `RateQuestRewardAdena=25`, XP25/SP35, QuestItemDropAmountMultiplier50 и category multipliers1 сохранены. Конечные предметные призы Q663/Q384/Q386 используют native `Quest.rewardItems`; Q662 уже использовал этот helper и не изменён. Денежные призы Q663 остаются на native Adena×25; стоимость участия и quest-objective drops не переведены на reward rate.

Фактические SHA256 текущих source bytes090:

| Файл | SHA256 |
|---|---|
| Rates.ini | `ba9c6fcbd364e09588f50a4d507d06fc9f21f0bfeaaba229977b44124db819f2` |
| OwnerConfig.json | `cbb60daacc466518459faf536f4d088ad85ec160568d57f54f248de74fd704ac` |
| OwnerProfile38.tsv | `4f25d9419b1a01aff2792a29f09a6b12f6fc464314ace73419ea4590a6ad3244` |
| OwnerProfilePreserved.properties | `c49b6a7e3aa04f55c287dd28212ef9935b478aa78e31d74c2e6caa2d8712e9fc` |

Штатный Save→Verify прошёл на source; повторный Save changed0 и все3 metadata hashes неизменны. Отдельный физический TASK090 TEMP stage восстановил все272 base values перед Apply→Verify; после применения все68 public INI byte-identical текущему source. Повторные Apply→Verify: changed0,77/77 staged files byte-identical. Native Java verifier подтвердил `OWNER_PROFILE_PASS rules=38`, XML_XSD=PASS и новый quest digest `5f62ca25a25eb94773e58c2e43689fc8ed125d2923552e38ea085f639b2ae256`; остальные preserved properties сохранены.

Таблица090 выше фиксирует фактические source bytes штатного Save. При staging Git нормализовал только CRLF→LF в OwnerConfig.json: SHA256 Git blob `62fb313d5e59f5b703efc8af914806efa76ff9f8bc5f85b127eaef5fa121ff4e`. Полное содержимое после этой единственной EOL-нормализации совпало; overrides и другие metadata не изменены.

Полная матрица и границы проверок — [отчёт090](reports/PHANTOM-QUEST-REWARD-RATES-090.md). Native Player выигрыши/ставки/replay090 не запускались: FULL090 остаётся PARTIAL/NATIVE_NOT_RUN. Публичная конфигурация готова к будущему штатному Apply; финальная поставка, private activation, прежние открытые QoL/navigation gates этим дополнением не принимаются.

## Дополнение владельца TASK091 — 25.10.2026

Явное дополнительное решение владельца из приложенного текста применено **после** завершения native тестовой фазы и отдельной публикации навигации `179cfc6f3a6365950488170e729bbff9f6083b20`. FULL091 остаётся **PARTIAL**, подробности — [отчёт091](reports/PHANTOM-INTERREGIONAL-TRAVEL-CLOSURE-091.md). Новые INI не применялись к frozen native TEST091, не меняют её доказательства и не принимают открытые graph0/Elf/foreground gameplay/farm/paid resume gates. Аварийный fallback не реализован; точка безопасной интеграции указана в отчёте.

Изменены ровно15 ключей в трёх публичных INI; остальные байты всех68 public INI сохранены, включая комментарии и обе per-ID premium строки:

| Файл | Новые утверждённые значения |
|---|---|
| Custom/PhantomPlayers.ini | EnablePhantomSystem=True; PhantomPopulationTarget=5000; MaxMaterializedPhantoms=128; PhantomPopulationActiveTarget=128 |
| Custom/PhantomMerchants.ini | EnableOfflinePhantomMerchants=True |
| Custom/PremiumSystem.ini | EnablePremiumSystem=True; PremiumRateXp=4; PremiumRateSp=4; PremiumRateDropChance=5; PremiumRateDropAmount=5; PremiumRateSpoilChance=5; PremiumRateSpoilAmount=5; PremiumRateQuestXp=5; PremiumRateQuestSp=5; PremiumMaximumCombinedChanceBonus=5 |

Сохранены PhantomMaterializationBurstSlots=0, MaxScheduledPhantomProfiles=10000, PhantomPopulationCreationInFlight=2, scheduler/queue/job budgets, Ecology и календари. Native `PhantomPlayersConfig.read` принял target5000/active128/cap128/burst0 без fail-closed. Это значения будущей поставки:5000 durable profiles не означают5000 одновременно материализованных Player. Массовое создание профилей/128 Player и CPU/heap/GC/latency нагрузка **NOT_RUN/NOT_MEASURED**, производительность этим решением не доказана.

MerchantTargetStores=200, MerchantHardCap=250, MerchantLifetimeIdentityLimit=300 и MerchantLinesPerStore=20 сохранены. Native `PhantomMerchantPolicy.load` принял эти bounds. OfflineTradeEnable=True и OfflineCraftEnable=True уже включены; OfflineTrade.ini не менялся. Независимый `PhantomMarketMaximumOpenStores` и его отдельная подсистема не изменялись.200 лавок не создавались, runtime admission/rotation/торговля **NOT_RUN**.

Штатный Save сохранил все272 прежних overrides с идентичными file/key/kind/baseValue/value, включая RateQuestReward=50; добавил15 новых, итого287. Старые overrides не пересчитывались/не терялись. Rates.ini, ClassMaster.xml, OwnerProfile38.tsv и OwnerProfilePreserved.properties побайтово совпадают с source090. Save изменил только OwnerConfig.json; два разрешённых projection metadata не требуют фиктивного изменения.

Штатная сериализация Save также изменила порядок записей overrides в JSON; поэтому текстовый diff metadata шире15 добавлений. Полное сравнение по уникальному file/key подтвердило идентичность всех272 прежних tuples и неизменность format/files. Ручного пересортирования или изменения tool ради этого diff нет. Обычный `diff --check` отметил CR в10 изменённых строках исходного CRLF PremiumSystem.ini; CRLF-aware `git -c core.whitespace=cr-at-eol diff --check -- <exact paths>` прошёл. Исходный EOL INI сохранён, реальные trailing spaces не добавлены.

### Premium: смысл новых значений и исключения

Источник — существующие `Attackable`/`Party`, `Quest.addExpAndSp`, grouped/ungrouped `NpcTemplate`, `PremiumLootPolicy` и `PremiumManager`; Java не меняется. XP/SP множители premium применяются поверх native server rates; при текущих RateXp3/RateSp5 произведения4×3=12 и4×5=20. Quest premium5 применяется перед Rates questXP25/SP35, произведения125/175. Это описание порядка native кода, **не измеренные выигрыши Player**; party distribution, level/other native modifiers сохраняются. Drop/spoil используют premium chance/amount, собственные native/server/champion modifiers и roll rules; cap действует на combined chance bonus до независимых server/champion rates, не на amount.

Общий cap3→5 затрагивает также обычного nonpremium игрока с native item bonus>3: `min(cap,1×itemBonus)`. При itemBonus4 теперь4 вместо3, при itemBonus1 по-прежнему1. Premium ordinary chance5×native bonus2 ограничится5, не10. Та же общая chance политика используется Spoil; managed/headless исключение premium не отменяет применение cap к собственному native item bonus. Включение системы не создаёт premium подписки: сохраняются expiration/admission в PremiumManager и исключения managed/headless в PremiumLootPolicy. Автоматических выдач premium аккаунтам/Phantom нет.

Обе неизменённые per-ID строки имеют одинаковые11 исключений, которые приоритетнее ordinary premium5:

| ID и native название | Premium chance / amount |
|---|---|
| 57 Adena |2 / 2|
| 6656 Earring of Antharas |1 / 1|
| 6657 Necklace of Valakas |1 / 1|
| 6658 Ring of Baium |1 / 1|
| 6659 Earring of Zaken |1 / 1|
| 6660 Ring of Queen Ant |1 / 1|
| 6661 Earring of Orfen |1 / 1|
| 6662 Ring of Core |1 / 1|
| 8191 Necklace of Frintezza |1 / 1|
| 10170 Baylor's Earring |1 / 1|
| 10314 Ring of Beleth |1 / 1|

Это premium bonus, не обещание финального количества/шанса; независимые native/server/champion множители остаются. Herbs/immediate без explicit override получают1; explicit0 остаётся0. DB-free smoke через реальные публичные API загрузил новые staged INI и подтвердил policy bounds, rates, все11 исключений обеих карт, ordinary5/herb1/explicit0, nonpremium cap4=4 и premium5×2=5. `DatabaseFactory.isInitialized=false`; materialization, реальные grouped/ungrouped drops, Spoil, Quest/kill XP/SP и payout с новыми значениями **NOT_RUN**. Historical070 native tests/его конфиги не изменялись и не переиспользовались как ложное доказательство091.

### Сохранение и независимый Apply

До правок SHA-checked byte backups76 файлов и прежнего текста контракта сохранены вне checkout. Отдельная private ACL stage: `%TEMP%/phantom091-config-db2d5314c8af4e56a6ff6f0fe2b02421/game`; public68 INI +3 metadata +ClassMaster.xml +4 enchant XML/XSD. Исходный stage уже содержал прежние272 применённых overrides; они не откатывались к base. Первоначальная копия остановилась на неверном optional `data/ClassMaster.xml`; путь проверен как `config/ClassMaster.xml`, копирование завершено до изменений source. Никакой private Database/Network/Server конфигурации, DB/JVM или защищённой поставки в stage нет.

Вместо BAT с pause выполнен разрешённый штатный `powershell -NoProfile -ExecutionPolicy Bypass -File tools/OwnerConfig.ps1 -Action Save`, затем `-Action Verify`: оба exit0, filesChecked68/overridesStored287/conflicts0; Save changed1 (OwnerConfig.json), Verify changed0. На отдельной stage выполнены `-Action Apply -GameRoot <stage/game> -ProfilePath <source/config/OwnerConfig.json>`, затем Verify: exit0, Apply changed4 (три INI и OwnerConfig.json), Verify changed0. Все68 INI и все76 staged файлов byte-identical source. Повторные Apply→Verify: exit0, changed0/0, все76 hashes неизменны. Прямой `java java/org/l2jmobius/gameserver/config/OwnerProfileVerifier.java <stage/game>`: exit0, `OWNER_PROFILE_PASS rules=38`, XML_XSD=PASS, quest digest `5f62ca25a25eb94773e58c2e43689fc8ed125d2923552e38ea085f639b2ae256`. Доказательства и snapshots сохранены в своей private stage, secret values не публикуются.

Фактические SHA256 source bytes после Save091 (Git может нормализовать CRLF→LF при staging):

| Файл | SHA256 |
|---|---|
| Custom/PhantomPlayers.ini | `55043b107c918d5232b838cb56583ab3dfda2b4530b2c5321fddf35c660180b2` |
| Custom/PhantomMerchants.ini | `11684ea937af679931abc253e01d7825de5fe9d1f3acac5437be90091f8981b3` |
| Custom/PremiumSystem.ini | `6772ddc9a713152e338dbf90ef83c5f07b37e41d0cd77aefd9beaeda41c897a4` |
| OwnerConfig.json | `5776e83810689da1acf94d0a3d8ee54d204e87ca0bf7488e685034b57c61f4e8` |
| OwnerProfile38.tsv (unchanged) | `4f25d9419b1a01aff2792a29f09a6b12f6fc464314ace73419ea4590a6ad3244` |
| OwnerProfilePreserved.properties (unchanged) | `c49b6a7e3aa04f55c287dd28212ef9935b478aa78e31d74c2e6caa2d8712e9fc` |
| Rates.ini (unchanged) | `ba9c6fcbd364e09588f50a4d507d06fc9f21f0bfeaaba229977b44124db819f2` |

Config commit ограничен3 INI +OwnerConfig.json +этим append-only дополнением; отдельный ordinary non-force push в feature/phantom-world. Git-команды разрешены дополнением владельца: exact `git diff --/--cached --check/--name-only/--raw --no-abbrev/--stat`, `git add -- <five paths>`, `git commit --only -m <subject> -- <five paths>`, `git push origin HEAD:refs/heads/feature/phantom-world`, `git rev-parse HEAD/'@{upstream}'`, `git ls-remote origin refs/heads/feature/phantom-world`, `git status --porcelain=v1 --untracked-files=no`. Штатный Save дополнительно читает `git rev-parse --show-toplevel`, `git rev-parse --verify HEAD^{commit}`, `git show <revision>:<exact INI path>` для managed base. История/чужой dirt/worktrees не меняются; финальный второй SHA/parity сообщается отдельно.

- mojibake-маркеры в изменённых файлах проверены: полный набор27 markers,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: все6 regex patterns,0 совпадений.

Конфигурация сохранена для будущей поставки, сервер не запущен. Protected078 и старые runtime/DB/JVM/worktrees не затронуты. **STOP: следующая задача не начинается.**
