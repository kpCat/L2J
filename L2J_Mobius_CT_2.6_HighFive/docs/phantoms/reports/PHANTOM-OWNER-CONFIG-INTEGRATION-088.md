# PHANTOM-OWNER-CONFIG-INTEGRATION-088

25.10.2026. **TOTAL088=BLOCKED — APPROVED_BOOLEAN_WHITESPACE.** Штатный OwnerConfig Save на отдельной физической копии отказал до записи: `file=Player.ini key=FakeDeathUntarget`. В OWNER_APPROVED сохранено `True ` с завершающим U+0020; это значение не принимается boolean-контрактом OwnerConfig. Конфиги и три Owner metadata в основной ветке не изменены. Apply, перенос настроек и пользовательская сборка не выполнялись. Публикуется только этот отчёт по blocker fallback из DELIVERY.md и workflow.

| Gate | Результат | Доказательство / граница |
|---|---|---|
| P0_INPUTS | PASS с явным исключением владельца | SHA256: RAW19/19, APPROVED19/19, package48/48; ровно две согласованные правки; три ошибочных baseline blob сверены по фактическому baseline с разрешения владельца |
| P1_OWNER_SAVE | BLOCKED | Save exit1, conflicts1, changed0; новые JSON/TSV/properties не получены |
| P1_OWNER_APPLY | NOT_RUN | До успешного Save этот этап запрещён; независимая исходная копия подготовлена, Apply не вызывался |
| P1_SOURCE_PERSIST | BLOCKED / NOT_APPLIED | Source87/87 public hashes совпадают с preflight; все19 исходных конфигов и metadata сохранены в прежнем состоянии |
| P2_SEMANTIC | PARTIAL / SOURCE_VERIFIED | Две рекомендации и остальные входные значения проверены как вход; ClassMaster XSD PASS; effective APPROVED runtime не объявлен |
| P2_PERSONAL_SECURITY | AUDIT_COMPLETE / OPEN_DEFECT | Character QoL требует allowlist; premium level-gap/shop не owner-only, конкретная граница описана ниже |
| P3_NAV_087 | PASS для документирования | NAVIGATION_FIXED=NOT_ATTEMPTED_088; PARTIAL087 остаётся открытым |
| P4_DELIVERY | REPORT_ONLY | Разрешённый fallback: один новый отчёт; интеграция и постоянный конфигурационный контракт не выпущены |

## Authority, read-first и сохранность

Рабочий Git root определён через `git rev-parse --show-toplevel`: `D:/Codex/repos/L2J-phantom-world`. Модуль `M=L2J_Mobius_CT_2.6_HighFive`, ветка `feature/phantom-world`, remote `origin` — GitHub `kpCat/L2J`. До записей local HEAD, upstream tracking ref и live branch совпали с authored baseline `c5d5c525f53eaea6ea6b774583040bb3f2903821`. Tracked dirt отсутствовал. Первый sandbox `git ls-remote` не имел сети; повтор с разрешённым host network access вернул exit0 и тот же SHA.

Прочитаны root и module AGENTS.md, README.md, CODEX_WORKFLOW_CONTRACT.md, PHANTOM_CURRENT_STATUS.md, OWNER_FINAL_RELEASE_TARGETS.md, все документы и manifests пакета088, предыдущий отчёт087, OwnerConfig.ps1, OwnerConfigChecks.ps1 и SaveOwnerConfig.bat. Входные38 файлов прочитаны строгим UTF-8 decoder; все36 INI разобраны штатным Read-OwnerIni, без duplicate keys. Дополнительных AGENTS выше Git root и в docs/phantoms/tasks/reports ancestors нет; актуальный code-map/pattern-файл для этого scope не найден. SOURCE_MAP088 использован как карта.

Процессное отклонение: начальный обычный `rg --files -g AGENTS.md` пропустил существующий module AGENTS из-за ignore-правил; прямой Test-Path обнаружил его уже после scratch Save и первого черновика отчёта. До staging он прочитан полностью, дополнительно прочитаны TASK_PACKAGE_STANDARD, релевантные navigation/config/Git/review разделы PHANTOM_DEVELOPMENT_MASTER_PLAN и текущие границы RELEASE_CONTRACT. Production/config source writes к этому моменту не было. Module правило о blocker commit/push согласуется с текущим fallback; исторические абсолютные пути и обычное требование release не переопределяют фактический root и прямой запрет сборки из088. Первичное утверждение об отсутствии module AGENTS исправлено.

Локальные аналоги: штатный OwnerConfig transactional Save/Apply/Verify, evolution/rollback/privacy сценарии OwnerConfigChecks и Java OwnerProfileVerifier с38 правилами/quest digest/XML. Проверены связанные PersonalCharacterQoLConfig/Service, PersonalEffectDurationPolicy, PersonalPremiumQoLConfig/Service, PersonalProgressionQoLConfig/Service, AutoPlayTaskManager, ConfigReader/PlayerConfig, native ClassMaster, MultisellData/MultiSellChoose и ограниченные navigation owners087. Java25/Ant и существующая архитектура сохранены; новых API, writer, тестового проекта или слоя не добавлено.

Физическая scratch-копия создана в отдельном task-owned TEMP `phantom088-8269902029594f878276b1650562fc13`, вне checkout; это не worktree. Внутри `save/game`, `apply/game`, `backup/game` скопированы82 публичных config-файла и5 необходимых enchant/XSD-файлов, каждый с SHA256 equality. Source Database.ini, Network.ini, hexid.txt, исключённые private INI и приватные аккаунты не читались, не копировались и даже не включались в hash manifest. Не выполнялось массовое копирование всего game или config в source.

Перед подготовкой сохранён внешний inventory292174 видимых untracked-путей. Git сообщил прежние AccessDenied на защищённых untracked-каталогах: их содержимое UNVERIFIED; ACL не обходились. Защищённые поставки, существующие private/historical .phantom-local, старые БД, MAIN/PLAY, клиент, процессы и сервис MariaDB не использовались. Source Verify штатно создаёт, использует и удаляет только свой временный module `.phantom-local/OwnerConfig-<guid>` для Java/XSD verifier; это не доступ к прежним private каталогам. Source family проверяется по публичным hashes и tracked scope, без чтения Database.ini.

## P0: входной контракт и подтверждённое исключение baseline

`INPUT_MANIFEST.json` SHA256: `efa9dc709767d2fb877569e97bbc0ed090a2907a84a744523f2058335dba3694`.

`MANIFEST_SHA256.json` SHA256: `2b2ce8abf7bde377fa9cdd2c5563e4693d52161005891ee096f5ea5dfc87f9a8`.

Все48 entries package manifest совпали по bytes и SHA256. Набор каждой входной папки — ровно19 уникальных относительных путей, traversal/reparse и запрещённые имена не обнаружены. RAW и APPROVED совпадают в17 файлах. Полное сравнение после ровно двух штатных Set-OwnerIniValue подтвердило отсутствие иных текстовых отличий:

| Файл / ключ | RAW | APPROVED |
|---|---|---|
| General.ini / RiftMinPartySize | 1 | 2 |
| Custom/PersonalProgressionQoL.ini / EnableServerWideAutoNoblesse | True | False |

Первичная проверка baseline STOP произошла до создания scratch: три значения `source_git_blob_sha1` в manifest неверны. Git checkout при этом чистый, и local/upstream/live равны заданному baseline. Владелец в этом диалоге явно разрешил использовать следующие фактические blob и записать исключение в отчёт. Входной пакет и его manifest не редактировались.

| Файл | Blob из INPUT_MANIFEST | Фактический blob baseline c5d5c525 |
|---|---|---|
| GraciaSeeds.ini | `370d1f5b76ee35838404cd3b34a43612` | `370d1f5b76ee35838404cd3b7d380a3b34a43612` |
| Olympiad.ini | `2051c6268831f60e26a952e9b3d2063a8acbe26` | `2051c6268831f60e26a952e9b3d2063a8acbe26a` |
| Siege.ini | `4b0f21139a34eac1bc8e28bd3decb4e07556bfbb` | `4b0f21139a34eac7fdb82f5763afdac1bf8decb4` |

Остальные16 baseline blob совпали без исключений; после разрешения владельца проверены19/19. Это разрешение касается только baseline identity, не дополнительных изменений настроек или OwnerConfig-кода.

Все пути ниже относительны к `inputs/OWNER_APPROVED/config/`; при будущем разрешённом применении им соответствует только точный `M/dist/game/config/<path>`. SHA256 RAW равен APPROVED, кроме двух явно указанных после таблицы.

| Путь | APPROVED SHA256 |
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

RAW General.ini SHA256: `e92ade600462856f0af14ea83399c8daeedd883b1c4bef39e9030f84bace3fd9`.

RAW Custom/PersonalProgressionQoL.ini SHA256: `2053004df57f23717aba0afe1d1447f3ce9decbe93a2c8365719e041699b50d2`.

Overlay в save/game выполнен по точным19 путям, затем19/19 APPROVED SHA совпали. Относительно исходной публичной копии получено18 byte deltas:17 INI + ClassMaster.xml. Siege.ini уже byte-equal APPROVED, дополнительная дельта не создавалась. Изменённый в памяти один RAW byte не совпадает с expected SHA; traversal отклоняется штатным Assert-OwnerRelative; неожиданный путь отсутствует в manifest allowlist. Отрицательные проверки не записывали испорченные inputs на диск.

## P1: точный blocker и отказ от обхода

Запущено из Git root, с настроенным JDK25 `25.0.4.101`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File M/tools/OwnerConfig.ps1 -Action Save -GameRoot S/save/game -ProfilePath S/save/game/config/OwnerConfig.json -JavaHome $env:JAVA_HOME
```

Здесь `M` и `S` — обозначения абсолютных module/scratch путей, не буквальные аргументы. Результат exit1, около5.14s:

```text
OwnerConfig CONFLICT conflicts=1 changed=0;
Тип или допустимый диапазон значения не соответствует управляемому ключу.
file=Player.ini key=FakeDeathUntarget
```

Причина подтверждена чтением исходников и bytes. В source baseline строка имеет значение `False `, в RAW и APPROVED — `True `, UTF-8 bytes `54-72-75-65-20`. `Get-OwnerNativeKinds` находит boolean через PlayerConfig.getBoolean. `ConvertTo-OwnerValue` принимает true без пробелов, а trailing whitespace допускает лишь для false; для `True ` он бросает boolean type conflict. Проверка всех распознанных native primitive значений APPROVED обнаружила ровно один такой отказ.

Это не процедурная ошибка выбора GameRoot/ProfilePath и не hard-limit слотов250. Отказ происходит при создании overrides до Test-OwnerStage и Write-OwnerTransaction: новые38 projection и quest digest не записаны. После отказа три metadata в save/game byte-equal backup; исходные87 публичных файлов и независимые apply/game/backup/game87/87 совпадают с исходными hashes. Source restore не понадобился, собственных source config writes нет.

Отдельный важный semantic gap: native `ConfigReader` загружает Java Properties и `getBoolean` вызывает `Boolean.parseBoolean(value)` без trim; PlayerConfig читает FakeDeathUntarget именно через него. Поэтому `True ` по этому коду даёт false. Это SOURCE_VERIFIED вывод по parser, без запуска GameServer. Простое копирование APPROVED в source не доказывало бы включение выбранной опции. Молчаливое удаление пробела, подмена значения в JSON или патч OwnerConfig нарушили бы exact-input/no-bypass требования088; они не выполнялись.

Не запускались зависимые Save-stage Verify, Apply→Verify, repeat Apply/idempotence, source Verify с новым профилем, Ant compile-tests и native runtime. OwnerConfigChecks.ps1 прочитан, но не запускался: у него фиксированный вывод в историческую `.phantom-local/task075`, без параметра task-owned scratch; переносить/переписывать runner в088 не потребовалось. Нового test harness нет.

Для проверки сохранности существующего состояния выполнен только штатный **source Verify без Save**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File M/tools/OwnerConfig.ps1 -Action Verify -JavaHome $env:JAVA_HOME
```

Exit0, около2.39s: `filesChecked=68 overridesStored=14 conflicts=0 changed=0`. Встроенный Java/XSD/quest verifier38 прошёл для **старого source-профиля**, не для новых настроек владельца. Старые metadata не выдаются за сгенерированные для088.

Ранний shell hash-check имел ошибку скобок в Join-Path/Get-FileHash; результат metadata не принят до исправленного read-only повтора. Другая диагностическая проверка ошибочно ожидала19 byte deltas; фактически18, поскольку Siege.ini уже совпадает. Ни одна из этих диагностик не меняла входные файлы или source. Единственный продуктовый запуск Save завершился описанным отказом, повторов ради GREEN не было.

## P2: сохранённые намерения и read-only границы

Это свойства **входного APPROVED**, не применённая конфигурация сервера:

- Rates: XP3/SP5, party XP/SP2, quest XP25/SP35/Adena25/items50, обычный drop amount10/chance8, spoil20/10, raid10/10; item-specific57,1 и event chance0 сохранены. UseQuestRewardMultipliers=False, остальные quest rates1 не нейтрализованы вручную.
- Champions: RED5%, BLUE10%, HP settings50/10 и награды/instances как во входе. ChampionMonstersConfig описывает HP как stock damage-divisor contract; это не утверждение о native combat measurement088.
- CommunityBoard: CustomCommunityBoard=True, currency57, teleport100000, buff5000, heal50000, delevel1000, premium1000000/day; legacy General EnableCommunityBoard — отдельный ключ. HTML/handler gameplay NOT_RUN.
- PersonalCharacterQoL: switches включены, durations5/15/15, positive augmentation5, solo Rift=True, Cubic10min, empty duration overrides и пустые AllowedAccounts/AllowedCharacterIds. Пустая overrides map законна: PersonalEffectDurationPolicy использует category multiplier как default. isPersonalUser требует enabled и точный account/objectId и отсекает headless; adventure policy дополнительно исключает GM и PHANTOM/BACKGROUND ownership. При пустых allowlists персональная активация не доказана: **PERSONAL_USER_ACTIVATION=FINAL_PRIVATE_INPUT_PENDING**. Никаких выдуманных ID/wildcards/GM не добавлено.
- General RiftMinPartySize=2; EnableServerWideAutoNoblesse=False; NoblessMaster Enabled=true, LevelRequirement80, ItemId57, ItemCount500000000. Это входные решения, без выдачи профессий/дворянства или runtime-проверки оплаты.
- Player: slots NoDwarf250/Quest250, остальные выбранные inventory/craft/carry/enchant параметры и UnstuckInterval30 сохранены как вход. Stock RandomRespawnInTownEnabled=True/MaxOffsetOnTeleport50 не изменялись для обхода travel. Servitor×2, enchant protection и ordinary scroll cap25 в существующем source-профиле проходят Verify38; новый профиль088 не получен.
- ClassMaster.xml прошёл .NET XmlReader validation против существующего data/xsd/classMaster.xsd. В approved XML enabled/spawn=true, popup=false; FIRST_CLASS_GROUP free и SECOND_CLASS_GROUP с оплатой57×1000000, пример THIRD_CLASS_GROUP закомментирован. Native ClassMaster читает эти options/conditions. Approved XML SHA сохранён, source не заменён; native class-change действие NOT_RUN.
- AutoPlay=False и AutoPotions=False не менялись. В AutoPlayTaskManager глобальный false блокирует только при phantomPolicy==null; managed PhantomPolicy имеет отдельный путь. Полная native potion/owned-supply цепочка088 NOT_RUN/NOT_VERIFIED; общий false не повышен до доказательства её работы. GeoEngine/Threads/Network, Grid1/90, PhantomPlayers/PhantomMerchants и public allowlists не изменены.

**Конкретный security backlog PREMIUM_OWNER_ADMISSION_OPEN:** PersonalPremiumQoLService.snapshot/maximumGap проверяет enabled, наличие Player и отсутствие headless session, затем tier item в inventory; PersonalCharacterQoLService.isPersonalUser не вызывается. У shop isShopEnabled также нет Player/allowlist. MultisellData запрещает generic доступ к91001, но отдельный separateAndSendPersonalPremiumQoL передаёт enabled list без owner admission; MultiSellChoose проверяет list marker и feature enabled, без owner allowlist. Цена/item ownership и generic-list guard не превращают это в owner-only. Нельзя объявлять SECURITY_OWNER_ONLY=PASS. Нужна отдельная scope-authorized causal проверка/исправление допуска normal non-owner, owner, GM и headless actor на storefront/purchase/reward path;088 не выключает выбранную функцию и не расширяет права. Source premium switches также не переносились из APPROVED.

## P3: неизлеченная архитектурная проблема087

Отчёт PHANTOM-UNIVERSAL-REGION-TRAVEL-087.md остаётся **PARTIAL**. Human/Elf/Orc first-mile к stock GK имеют дистанции18705/10298/11508 units; три qualified native Player получили SEARCH_LIMIT. Наблюдавшиеся bounds128nodes/512edges/32queries исчерпаны, waypoints0, MOVE_TO0. Paid GK0, arrivals0, farm/rewards0, production fixes0/2. SEARCH_LIMIT означает finite budget exhausted, а не географически UNREACHABLE. Новый JVM восстановил fixture, но interrupted paid-leg/goal recovery не доказан.

Read-only source audit подтвердил shared finite caps PhantomWalkCorridorPlanner и текущие owners NormalGatekeeperTravel/VisibleFarmTravel/NavigationService/NativeTravelActions. Последний отвергает deterministic returnLanding при stock RandomRespawnInTownEnabled=True или MaxOffsetOnTeleport!=0. Настройки владельца ради fallback не меняются.

Для отдельной будущей089 требуется source-proven hierarchical region route с finite geo-attested waypoint/corridor legs от фактической позиции к native GK, native TeleporterData/GK execution и фактическим debit receipt; durable checkpoint/recovery сохраняет исходные deadline/resources/owner tokens и не превращается в бесконечный retry. Переиспользовать canonical world-data provenance, topology/query и existing navigation owners; никаких вручную придуманных XML edges, новых worker/движка, фиктивного teleport или bump budgets.

Будущие причинные RED/GREEN должны охватывать несколько рас/level bands, разные направления и высоты/NSWE, walls/doors/water, реальные gear/skills/supply/wallet, quote vs debit, insufficient money/missing GK/siege/stale state, смерть/party/store/visibility и повторный sameDB restart без duplicate debit или потери earned inventory. Нужны actual arrival и минимум один mob/progress episode. В088 это только backlog; **NAV_087_OPEN, NAVIGATION_FIXED=NOT_ATTEMPTED_088**.089/R2 не запускались.

## Exact scope и публикация

Единственный добавляемый tracked artifact:

`L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-OWNER-CONFIG-INTEGRATION-088.md`.

Пакет088 остаётся untracked и неизменным.19 source configs и3 metadata не stage-ятся. `docs/phantoms/OWNER_GAME_CONFIG_CONTRACT_2026-10-25.md` **не создан**: DELIVERY при blocker разрешает commit только отчёта; документ о действующем сохранённом профиле был бы ложным до успешных Save→Verify и Apply→Verify. Manifest и выбранные значения выше сохранены как evidence, но это не заменяет ещё не выполненный durable configuration contract. Общие финальные targets5000 durable/cap128+/минимум200 stores и будущая единственная финальная сборка не менялись.

Разрешённые текущим task/user Git-команды: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse '@{u}'`, `git status --porcelain=v1 --untracked-files=no`, `git status --porcelain=v1 --untracked-files=all`, `git ls-remote origin refs/heads/feature/phantom-world`, `git ls-tree HEAD -- <19 exact config paths>` и `git diff -- <exact config paths>`. Штатный Save дополнительно вызывает `git -C M rev-parse --show-toplevel`, `git -C R rev-parse --verify HEAD^{commit}` и `git -C R show <resolved-HEAD>:<public-config-path>` для запрошенных managed baseline INI из public catalog68. Они явно предусмотрены P1 и tools/OwnerConfig.ps1, не являются публикацией private files.

Публикационный exact allowlist — только этот отчёт: `git diff --check -- <report>`, `git add -- <report>`, `git diff --cached --check`, `git diff --cached --name-only`, `git diff --cached -- <report>`, затем один normal `git commit -m "phantom(task-088): report approved config save blocker"` и `git push origin HEAD:refs/heads/feature/phantom-world`. До commit проверяются report review, strict UTF-8, обе encoding-проверки, отсутствие staged paths вне allowlist и прежний untracked inventory. После push повторяются local/upstream/live SHA equality, tracked clean и inventory equality; фактический commit SHA и push receipt сообщаются в финальном ответе и внешнем publication receipt, поскольку собственный SHA невозможно включить в создающий его commit.

Запрещённые git clean/reset/restore/stash/branch/worktree/rebase/amend/force/history/default/master операции не выполнялись. Ant/jar, DB, release и production navigation code не запускались/не изменялись. Scratch с проверенными публичными копиями оставлена для review, не переносилась в Git.

Pre-publication guards: source/apply/backup87/87 hashes unchanged, Save metadata3/3 unchanged. Видимые untracked292174 совпали с исходным inventory; SHA256 упорядоченного списка `3411bd6a38d396a43f1aefa9384f3f918523189d414da351ca24b45a07a9811f`. На этом этапе один новый собственный report исключён из сравнения старых untracked; после staging он перестаёт быть untracked. Tracked baseline clean, `git diff --check -- <report>` exit0. Первая encoding shell-проверка отказала на PowerShell parser до выполнения; here-string повтор прошёл обе независимые проверки.

- mojibake-маркеры в изменённых файлах проверены: весь заданный набор,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: все шесть Unicode/XML patterns,0 совпадений.

Strict UTF-8 report PASS. Финальные cached scope/diff и post-push guards выполняются после последней правки отчёта; их SHA/receipt сообщаются отдельно. **Интеграция088 остановлена на BLOCKED.** Дальнейшее применение требует отдельного решения владельца о `FakeDeathUntarget` и согласованном bytes/parser contract; полномочие исправить три baseline blob этого не разрешает. Автоматического продолжения089/R2 нет.
