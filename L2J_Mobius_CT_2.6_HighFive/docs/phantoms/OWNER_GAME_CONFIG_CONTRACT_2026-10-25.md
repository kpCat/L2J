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
