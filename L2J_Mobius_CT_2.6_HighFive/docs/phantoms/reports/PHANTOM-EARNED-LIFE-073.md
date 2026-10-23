# PHANTOM-EARNED-LIFE-073

**CORE073=PARTIAL.** Committed-source delivery и сохранность реальных заработков проверены; связанный оплаченный1463 цикл не завершён. Начало:2026-10-23 19:48 UTC; campaign закрыта примерно за1час с сохранённым актором, без R2/reseed. Все действия ограничены OWN073; ранее опубликованные071/072 PARTIAL не пересматриваются. [Матрица приёмки](PHANTOM-EARNED-LIFE-073-data/ACCEPTANCE073.tsv).

## История денег и предметов

Ошибка репрезентативности072: сразу создан Warrior20, выданы129/352/2378, обнулён кошелёк и отсутствовала история1–19. Его1066A после12kill не характеризуют естественную карьеру. В073 этот защищённый актор не используется. Новый TEST073 имеет отдельные schema/account/root, даже если fresh IdFactory выдаёт совпадающий integer object ID.

| Этап | Реальный контракт | Граница доказательства073 |
|---|---|---|
| Рождение | PlayerCreationInitializer, PopulationInitializationContract; stock StartingLevel1/StartingAdena0 и штатные initial items | Нет гарантированной суммы к20; стартовый NG entitlement не является покупкой D |
| ACTIVE farm | Native combat/reward/drop/pickup добавляет физические items, включая57; PlayerInventory.restore восстанавливает указатель на Adena57 | Нужны фактические earned deltas, а не только ростEXP |
| Уровень/профессия/skills | Progression меняет level/class/SP/skills; реальные оплаченные costs остаются расходами | SOURCE_AUDITED; полная естественная1→20/все профессии NOT_OBSERVED |
| BACKGROUND | Model.inventoryDelta → Service → BackgroundTransaction: существующие counts плюс дельта; SQL guarded/CAS inventory mutation | SOURCE_AUDITED; отдельный native BG/class-transfer эпизод не заявляется |
| Dematerialization/newJVM | PhantomMaterializedPlayer вызывает обычные Player.load/storeMe, physical inventory rows сохраняются | Genuine early profile и earned20+ state подтверждены; paid chain отсутствует |

**HISTORY_EARNED_PRESERVED=PASS:** `task073early/Earned073Early`, actor268480996/profile2, stock level1. Два native Gremlin18342 kills: EXP0→29→58, SP0→2→4, Adena0→9→20. Второй JVM на том же C59D JAR сохранил20A в том же item object268481004 и все8 исходных positive item objects/gear/counts. Это настоящий ранний заработок и durable inventory, без заявления1→20. Первая попытка early helper была REJECTED_TARGET из-за null knowledge supplier; исправлен TEST caller по существующему native backend contract, использован тот же актор, без повторного bootstrap/выдачи. Полный [HISTORY073.tsv](PHANTOM-EARNED-LIFE-073-data/HISTORY073.tsv) сохраняет этот FAIL и последующие PASS.

К20 остаётся фактический накопленный баланс: старт + полученные Adena/loot/продажи − реальные покупки/fees/расходы. Он зависит от числа успешных убийств, x1 drops, потерь/смертей, купленного gear, расходников и пути; конкретная сумма не выдумывается. Grade debt остаётся самостоятельной экономической целью.

CASE_B073: Warrior20 с129 D sword/352/2378 и100A — объявленный **NATIVE_PRIOR_HISTORY_FIXTURE**, без заявленной карьеры1–19. Деньги после начала measurement не выдаются. Для BUY требуется физический расход больше100A, поэтому он обязан включать **NATIVE_EARNED**. Все gear/cash fixture задаются до measurement; rates/prices/taxes/reserves остаются native.

## Сцена до рождения актора

Независимый read-only planner не создаёт Player/account. Первоначальный shortlist:3 source groups, затем повторная проверка выбранной сцены в committed native JVM до birth. [SCENE_ECONOMICS073.tsv](PHANTOM-EARNED-LIFE-073-data/SCENE_ECONOMICS073.tsv) содержит реальные quote/tax, native per-charge, speed, actual route length/ETA, budget/reserve/fee, threshold и projected kills. Ожидаемый gross x1 income не считается гарантированной добычей или обещанием бессрочной прибыли.

Дешёвый ближайший30063/21801:14base+20%→16A native truncation,1375direct, но bounded native route дал SEARCH_LIMIT. Это не доказательство непроходимости. Tyra30420:14base+50%→21A,3shots/charge=63A. Около4km и нулевая walk fee; нижняя native template speed115 делает предварительный порог консервативным. Actual live running speed и меняющиеся wallet/distance/usefulness перепроверяются при заработке. Выбрана сцена20038lvl18; альтернативная20418lvl18 тоже имела native both-way corridor. Бюджет earning15min и journey8min; cap12 отсутствует.

## Native цепь и restart

**SAME-ACTOR PARTIAL:** TEST073 `task073supply/Supply073`, actor268480990/profile1, root72007303. Integer ID совпадает с другим актором в защищённой TEST072; schemas/accounts/roots разные. Measurement:level20, EXP835862, SP0,100A, HP516, D1463=0. После35 genuine rewarded kills: EXP859164, SP1023,1747A. Native income **1647A**, fixture100A отдельно. На17kills терминальный результат VICTORY_LOOT_BLOCKED; число issued pickups не считается числом успешных acquisitions. Реальный доставленный cash≈47A/kill ниже catalog-only projection; targets в v1 включали также lvl17 после перемещения, поэтому сцена не объявлена полностью проверенной по динамическому риску/доходу.

При100A native shortage сохранил ACTIVE farm root и child income.farm. После1747A actual distance3113, running159.5, unit21A, available budget1311, count62 и useful=true. Existing SupplyJourney создал commerce.buy intent62×1463, expense budget1311, revision2; реальная цена62×21=1302A. **COMMITTED receipt отсутствует, дебет0, D1463=0.** Затем native route дал local checkpoint retries. После остановки отдельного combat owner тестом актор остался в поле, погиб; saved HP0, EXP844835 после штатной death penalty, SP1023 и1747A/items сохранились. Native death и отсутствие активного defense/ordinary corpse recovery в отдельно собранном harness объясняют дальнейший stall; это не доказательство сломанной native transaction или обнуления экономики.

Один continuation на том же акторе выполнил **stock CombatService.respawnTown**, без прямого HP/XYZ setter: HP0→335.4, native town(10713,16362,-4563),1747A и все исходные earned object IDs сохранены. После настоящей смерти проверены только2 deterministic recovery-кандидата у Lara30063: price16A с native20%tax/truncation, budget1311,81shots; первый both-way proof SEARCH_LIMIT/FOUND, второй отклонён QUOTE threshold. SEARCH_LIMIT не объявлен непроходимостью. Lane остановлена без расширения PathFinding, нового seed/актора, resource gift, продажи protected recipe/material или повторного поиска мира.

Финальный read-only SQL: actor268480990 жив, HP335, EXP844835, SP1023, Adena item268480994:57:1747; loot2140×2/1870/1867/1868 и1831 сохранён. Original farm root revision1 и unpaid child revision2 остаются, child deadline истёк; receipt отсутствует. Это сохранённый pending state для отдельного последующего решения, а не обещание завершённой закупки. [LIFE_CHAIN073.tsv](PHANTOM-EARNED-LIFE-073-data/LIFE_CHAIN073.tsv) —55строк compact extract с source SHA; [CORE_ATTEMPT073.tsv](PHANTOM-EARNED-LIFE-073-data/CORE_ATTEMPT073.tsv) сохраняет все35 kills и исходный FAIL.

Невыполненный минимум: фактический paid1463 COMMITTED → native return→два rewarded kills с debit купленного стека→новый полезный акт того же paid profile после sameDB/sameJAR restart. Независимый Main restart и earned-state restart не подменяют этот HARD gate.

P1_PVP: DEFERRED_WITH_ROOT_CAUSE. В072 легальный safe corridor не доказан; в073 lane не запускалась, поскольку P0 не завершён и bounded recovery scenes исчерпаны. Новых PvP/Seven Signs/AA-CP действий нет. OWNER_VISUAL_PENDING; клиентская приёмка не имитируется.

## Source, проверки и delivery

Game source commit `97cefafc451a97e80c37254889b387c20ecf4764`. Game JAR SHA256 `C59D4E9CFB579EBFF80C0AC1F5C4412D673B271C2E2AD59D5223DD8D8248B695`; Login `5BFFB23A6C47AB10CCE14EA4F820840C3AD61A8EA5D939AB529C00839F5D03B8`. Одна команда committed archive Ant `jar compile-tests`, exit0/37s; унаследованный Ant dependency graph внутри этой команды вызвал production compile дважды;5 existing test compiler warnings. Другого whole-JAR build не было. [SOURCE_BUILD_INFO073.json](PHANTOM-EARNED-LIFE-073-data/SOURCE_BUILD_INFO073.json). Позднейшие bounded TEST helpers отдельно собраны из committed f16cd7830b2/bea30e9927e/fbf1fd54223 и привязаны к тому же whole Game JAR; [HELPERS073.tsv](PHANTOM-EARNED-LIFE-073-data/HELPERS073.tsv). Изменение отчёта не требует повторной JAR сборки.

Focused: combat-core55/55, supply0715/5; actual Supply072Suite не существует, не заявляется запущенным. Runtime073 guard и cleanup assertion regression PASS; оба meaningful RED→GREEN. Свежий read-only review выявил cleanup/sameJAR proof gaps; устранены до measured run. EOL normalization в существующем headless adapter создаёт большой текстовый diff без дополнительного изменения поведения; это отмеченное minor ограничение review.

Собственные схемы `l2jmobiush5_phantom_test_073` и `l2jmobiush5_phantom_release_073`, каждая из123stock SQL scripts/128tables, unchanged economy071 migrationSHA с authority.task_id73 и собственным installationUUID. Не импортировалась предыдущая DB. Physical release:`D:\PhantomWorld\Earned-Life-073-candidate`; native TEST:`.phantom-local/task073/native-test`, operator TEST:`.phantom-local/task073/runtime-test`. Все три roots содержат203physical Geo и hash bindings. START/STATUS/STOP.cmd находятся в candidate; [START_HERE_073_RU.md](D:/PhantomWorld/Earned-Life-073-candidate/START_HERE_073_RU.md).

Обычный RELEASE Main:ready20:15:28UTC, stock STOP20:20:44; sameDB/sameJAR restart:ready20:22:42, STOP20:24:50.16identities/profiles SHA неизменны, item rows117→121, Vortexbra actor268484865/profile2 level2→3, EXP87→367, SP6→14. На втором snapshot4нативных dead profiles; HP не исправлялся. [RUNTIME073.md](PHANTOM-EARNED-LIFE-073-data/RUNTIME073.md). Это bounded useful activity/restart, не FULL M1 или доказательство paid20+ chain.

Оба runtime STATUS:GameServer/LoginServer STOPPED, own DB connections0. Отдельно CIM OWN073 control/native JVM0 и listeners2106/7777/9153/2253/7953/9253=0. Все native materialization drains STOPPED; глобальная MariaDB и чужие процессы не останавливались. Protected SHA guard сверяет19untracked package files,4prior072 files including original measured checkpoint,4selected baseline inputs и203source Geo. Старые DB не подключались инструментами073; полного побайтового inventory чужих DB/клиента не заявляется. [SAFETY073.tsv](PHANTOM-EARNED-LIFE-073-data/SAFETY073.tsv).

Проверки исходников и отчёта:

- mojibake-маркеры в изменённых файлах проверены — PASS;
- escaped Cyrillic в изменённых файлах проверены — PASS.

Использованы полный набор маркеров и отдельные regex. Raw runtime-generated PS5 stderr с локализованным месяцем имеет encoding artifacts; эти логи не исправлялись и не выдаются за source-text PASS.

Пути source/tools/tests ограничены [SOURCE_PATHS073.txt](PHANTOM-EARNED-LIFE-073-data/SOURCE_PATHS073.txt), финальный exact artifact allowlist — [ARTIFACT_PATHS073.txt](PHANTOM-EARNED-LIFE-073-data/ARTIFACT_PATHS073.txt); план, решения и review fixes — [IMPLEMENTATION073.md](PHANTOM-EARNED-LIFE-073-data/IMPLEMENTATION073.md). Git использован по прямой authority073: bounded branch/status/remote/exact diff, exact staging/commits/archive и normal non-force feature push. [Точные команды и publication contract](PHANTOM-EARNED-LIFE-073-data/GIT073.md). Фактический final remote HEAD после push фиксируется в локальном publication receipt и финальном сообщении, без самореферентного SHA внутри своего commit. Ни master, ни чужой dirt не изменяются.
