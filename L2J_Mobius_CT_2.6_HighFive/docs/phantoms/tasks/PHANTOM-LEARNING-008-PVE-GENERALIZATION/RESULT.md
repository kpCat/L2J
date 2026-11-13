# Результат PHANTOM-LEARNING-008

`/goal PHANTOM-LEARNING-008-PVE-GENERALIZATION`

**PARTIAL / NO_TUNING_CHECKPOINT_IMPROVEMENT. Игровой verdict: INCONCLUSIVE. Финальный manual gate: REQUIRED.**

Одна V3 исполнила законные отличия от stock на Archmage94, новом тренировочном MysticMuse103 и unseen StormScreamer110; Party HEAL105 прошёл ограниченную регрессию. Продолжение Adam и сохранение нового DRAFT доказаны. Однако TUNING выбрал **epoch0**: selected DRAFT12 побайтово равен исходным terminal weights Adam11. Новые gradient steps008 не улучшили выбранную политику. Поэтому перенос в EVAL подтверждает работоспособность прежней terminal policy в новых canonical контекстах, но не эффект обучения008 или игровое превосходство.

## База, scope и архитектура

Ветка `wip/phantom-learning-008`, база принятой007 — `062862eca8e0bcedd4107d7b91720627307f736c`. Независимый TEST: `D:\Codex\repos\L2J-learning-006`. Основной dirty checkout оставлен без изменений. Исходный remote feature — `7e997636dbac9d5e1754e00c793cd6fac858b0c6`; с007 он diverged, общий предок `dfcba154fa1f459be6082a559744d2b4a5c9b47a`. Merge в feature/master не выполнялся.

Exact scope: 16 source/config/test paths, 10 delivery documents и один evidence ZIP; список — в PLAN. Переиспользованы существующие native owners, Player/SkillData/SkillTreeData, progression resolver, V3 state128/action64/topology и общая Store transaction. Новых executors, NN features, schema migrations, библиотек, runtime providers или UI нет. Старые007 codec и replay сохранены.

`Exploration008.ini` поставляется с opt-in OFF. Private TEST включался явно; exploration epsilon .25 только TRAIN, одна opportunity/episode. EVAL/controls: exploration OFF, trainingCredit false. HEAL emergency, margin .025, 5ms и cooldown2000ms сохранены. Новый objective008 разрешён только для DRAFT; promotion не выполнялся. MAIN/PLAY, protected207, Geo203, release057–061, чужие БД/процессы не использовались как TEST.

## Классы, сцены и данные

|Класс|Canonical learned DAMAGE skills|Назначение|
|---|---|---|
|94 Archmage|1230:28, 1339:1|TRAIN, TUNING, EVAL|
|103 MysticMuse|1235:28, 1340:1, 1342:1|TRAIN, TUNING, EVAL|
|110 StormScreamer|1239:28, 1341:1, 1343:1|AUDIT и EVAL, 0 gradients|
|105 EvaSaint|Прежний Party HEAL owner|Только regression EVAL, 0 gradients|

Проверены canonical XML и actual native SkillData/SkillTreeData/resolver; до CUT фиксировались learned ranks, текущий owner и минимум два ready законных кандидата. Дополнительный capability XML находится только в private TEST. Global skills/skill trees не менялись. Catalog SHA `3547b0ab195e9e9346247f1c43290d18d05167886c764f7375935d97cfb913fd`; capability SHA `c762f8d78142c4414193323c15403cb1e3004791b34bc444c16712ffc96a9fde`.

Каталог: 48 collection slots и 8 EVAL pair slots; TRAIN уровни77/78, A/S, enchant0/2, NPC21798/21799; EVAL — заранее отложенный enchant4. TRAIN имеет по две physical cells на94/103, сбалансированные AB/BA. Все actor/target incarnations свежие. Два source-keyed положения одного anchor разделяются классами: независимость по географии этим не доказана. RNG штатный; post-CUT HP/MP/damage не подменялись.

|Split/класс|Попытки|Completed|Другие исходы|Raw paid effects|
|---|---:|---:|---|---:|
|TRAIN94|16|15|1 pre-CUT failure|44|
|TRAIN103|16|11|1 pre-CUT failure, 4 incomplete|38|
|TUNING94|4|4|0|13|
|TUNING103|4|4|0|12|
|AUDIT110|8|8|0|44|

Итого 48 попыток, 42 completed. Все ошибки сохранены в denominator; повторов reserved slots нет. После deterministic admission/freeze: 13 TRAIN_A controls, 12 независимых matched TRAIN_B episodes, 24 paid action targets (14 класса94, 10 класса103). Orphan B без успешного A исключён из gradients. До двух первых chronological paid primary DAMAGE actions на B-episode; число action targets не выдаётся за число независимых эпизодов. Held-out quality использует одну initial decision на episode: TUNING8 и AUDIT8. Остальные held observations — вспомогательные, не увеличение n.

Utility008: `.70*terminalReward + .30*clip(2*actualHP/targetMaxHP - actualMP/500 - elapsedNanos/1e10, -1, 1)`. Residual — `clip(4*(U-baseline), -1, 1)`. Baseline использует только TRAIN_A: exact initial mean при минимум трёх других controls с исключением собственного block либо frozen class-balanced ridge forecast (lambda .01, LOBO для TRAIN). Это nuisance forecast, а не измеренный counterfactual stock outcome. Episode/class-balanced clipped IPS ограничен4; поздний stock action имеет conditional propensity1. Реальные probabilities и receipts сохранены.

## Модель и независимое качество

|Artifact|SHA-256 / факт|
|---|---|
|Selected DRAFT11|`7de6f5b6bd51f71a8bfce143422139e6b18da44cf81e6ea81f91728b0da206d0`|
|Parent Adam11|`f13d2f73ed7d0f3ffd38534b8ad413966b64cf7ba7c980a6ade2318da019d74d`|
|Terminal parent weights = selected DRAFT12|`6f3dfa43ef8a8335c9ebee16498d7a6406de629c6a85bdd66755675fb55b9546`|
|Adam12|`85cea56889abeef7b82807ddeba950f588d9bc4e84340c6d454a84bafe72670e`|
|Terminal207 weights в Adam12|`7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37`|
|Frozen008 targets|`02ff9c654a1e5c7881494937304ee798be6a2623e6db770e16495ccdeca6c08c`|

Adam восстановлен и продолжен: updates855→887, completed epochs191→207. Persisted run `1bd4a0d92a804b54b49373bc0d37e5e1`, model12 DRAFT, objective `FACTUAL_RESIDUAL_UTILITY_008`. TUNING initial MSE и selected MSE одинаковы: .0029208414; selected epoch0. Terminal207 не подставлялся вместо selected checkpoint после просмотра AUDIT/EVAL.

42 atomic application markers: 12 GRADIENT episodes, 13 BASELINE controls, 16 HELD_OUT, 1 EXCLUDED orphan. Actual cold SQL показал duplicateCreditAvailable=false, старые007 58 markers/19 gradients и Adam11 blob855/191 неизменны. AcceptedV1/2 сохранены. Отдельный cold-export после EVAL/controls подтвердил byte equality selected weights, optimizer, targets, applications, labels и working metrics; manifest отличается export metadata.

Все сравнения ниже используют **одинаковые008 labels**; напрямую сравнивать MSE с007 нельзя.

|Независимый split|n|DRAFT12 MSE|DRAFT11 MSE|TRAIN constant MSE|Raw U mean / variance|
|---|---:|---:|---:|---:|---|
|TUNING94|4|.000322550|.024150353|.001080637|.449695617 / .0000221714|
|TUNING103|4|.005519133|.040848524|.008442734|.452177025 / .000325127|
|AUDIT110|8|.068154119|.079478801|.069515362|.464715868 / .005287852|

На frozen initial observations top-rank DRAFT12 отличается от **stock**: TRAIN94 4/15, TRAIN103 6/11, TUNING0/4 и0/4, AUDIT110 2/8. DRAFT11 имеет те же counts; между DRAFT11 и12 **0/42 различий top skill** на этих observations. Это predictions, не исполненные interventions. TRAIN-only forecast воспроизводится точно; normalized squared OOD distance mean/max: TUNING94 .000940943/.000950445; TUNING103 .001058237/.001113934; AUDIT110 .001177571/.001584620. Порог OOD не подбирался. n8 и зависимость residual labels от nuisance forecast не дают надёжного вывода об обобщении или причинном преимуществе.

## Настоящая EVAL и отрицательные контроли

Для каждого класса две matched completed пары, AB и BA; exact physical facts равны, native randomness независима. Все16 arms завершились canonical native kill. Divergence считается только по исполненному эффекту; попытки reorder могут не дать paid effect.

|Класс|Matched pairs|Paid divergences B|Median B−A elapsed, ms|Описательный bootstrap interval, ms|Verdict|
|---|---:|---:|---:|---|---|
|94|2|5|+522.85|[-6000.76; +7046.45]|INCONCLUSIVE|
|103|2|2|−7379.66|[-12562.10; −2197.23]|INCONCLUSIVE|
|110 unseen|2|6|+773.64|[−247.40; +1794.67]|INCONCLUSIVE|
|105 HEAL|2|1|−2126.69|[−2254.58; −1998.79]|INCONCLUSIVE|

При n2 интервалы не доказывают значимость. У94/110 B был как быстрее, так и медленнее A. У103 оба B быстрее в этих двух сценах; причинность gradient steps008 не установлена. HEAL имеет один paid target divergence, native heal/MP receipts; в этих двух парах регрессия не наблюдалась, полнота Party комбинаций не проверена. По одной stock/stock паре94 и105: 0 policy divergences, elapsed differences −3299.54 и−678.36ms при n1. Нулевая divergence не означает одинаковое время боя.

Arm/pair ledgers сохраняют HP/MP, exact selected damage/heal, canonical kill/EXP/SP и rates. Полные deaths, loot, consumables, net adena, all-actions MP и whole-party damage остаются UNKNOWN, а не0. Observed deaths lower bound0 не доказывает отсутствие всех смертей. Полные значения и per-arm uncertainty находятся в archive `evaluate-v1-arms.json`, `*-pairs.json`, `report008-facts.json` и native ledgers.

## Неуспехи и bounded исправления

1. PREPARE26: stopped guard после собственного SQL pool; перенесён до pool. GS не запускался.
2. GS1 slot2: новый TEST offset240 пересёк blocked Geo segment. GeoProbe подтвердил180/160 valid; выбран160. Failed slot не повторён.
3. GS2 slot4: min-world-object выбирал плохую точку source. Закреплены source-keyed immutable positions; все56 slots проверены headless до GS3.
4. GS3: class103 cold TEST helper по-прежнему искал1230/1339, давал range0; три NO_SAFE_SOLO_OPENING и один REQUESTED_DRAIN incomplete сохранены. Минимально исправлен class-aware ActorFixture; RED→GREEN; из резерва израсходован один explicit recovery start, без сброса quotas/budget.
5. FIT36: COMMIT состоялся, но generic cold reader сравнил008 residual loss со старыми local-action units. Stale evaluation.json сначала ошибочно прочитан как отсутствие checkpoint; PREPARE37 выявил причину. Versioned authenticated residual reader исправлен, RED→GREEN; никаких SQL repairs/refit/изменений labels. FIT restore38 поднял тот же DRAFT12 без повторного credit.

Original protocol/JAR сохранены. Rebuild receipts проверили byte equality production payload и manifest, кроме Build-Date; после cold-reader fix исключение ровно одно: `TacticalStoreV3.class`, SHA `fadac44dad780a9fe7493e56ca06fcbeefb29cd67e3f3be1e208157aa0b32d14`. Другие core bytes неизменны. EVAL/CONTROL JAR SHA `852e223d8a797562faa48f5a1234f32ff077d10a056d939213a750a0c1d856a0`.

Read-first process deviation: case-variant серверный `Agents.md` был пропущен исходным case-sensitive inventory; найден review и прочитан перед поставкой вместе с master plan и package standard. PLAN исправлен с сохранением этого факта. Старые абсолютные пути/БД в документе не применялись: текущая задача явно определяет TEST006 и отдельную ветку008.

## Проверки и стоимость

Final `ant ... phantom-learning008-test phantom-learning007-test jar`: exit0, 2m03s, JDK25; пять существующих warnings. Десять существующих targeted routes004–006: exit0; schema prefix, Adam deterministic resume, 28 ledger assertions, outcome/clock/heal/authority/campaign/boundaries/routing. PowerShell parser:0errors. Actual cold SQL/STOP/RESUME подтверждены. Новая fault injection не запускалась: общий transaction/COMMIT contract не менялся; принятый007 rollback не выдаётся за новый008 run. `ant verify` и чужие DB gates не запускались.

Шесть GS starts из8, collection4 (один recovery); cumulative native time2551516ms =42.5253min из90, collection1730399ms =28.8400min из60; pendingfalse. Budget включает ошибки, bootstrap и teardown. Сумма sampled GS CPU1000.265625s, peak working set4747083776bytes ≈4.42GiB. Это process metadata, не CPU только inference. Ant/headless/SQL времени в native minutes нет.

Bounded policy ring windows: worst per-arm p50 / p95=p99=max для EVAL, ms:94 2.2215/5.6153 (n18);103 1.7335/2.6260 (n19);110 1.4825/2.0227 (n29);105 .0292/2.5424 (n12). Это maxima отдельных arm quantiles, не объединённые глобальные percentiles. CPU timer гранулярный; нулевые samples не означают нулевую стоимость. Allocation/encode/score samples сохранены отдельно. Collection BUDGET_5MS4, EVAL1, CONTROL1; защита реально отказала при превышении. EVAL fallback counters: MARGIN2/2/2 для94/103/110; cooldown/cap6 у103,1 у110,379 у105; HEAL_OFFER_COUNT545. HEAL diagnostic counters включают background offers, не число paid effects.

TEST STOPPED, порты33357/18001/19301 свободны; dataset, optimizer, checkpoints и historical ledger сохранены. Итоговые gates — в ACCEPTANCE, команды и exit codes — в COMMANDS. Git использован по прямому разрешению задачи для scope, ветки и non-force008 publication. Exact argv publication и independent readback добавляются в receipt; final manual gate остаётся REQUIRED.

Final scope guard:27/27exactpaths, `git diff --check` exit0. Только21добавленная Ant строка нормализованаCRLF→LF после этого whitespace check; baseline mixed endings сохранены. Source review и final scientific delivery review не оставили подтверждённых важных замечаний.

- Mojibake-маркеры в изменённых файлах проверены:26textpaths, отдельный rg, совпадений нет.
- Escaped Cyrillic/XML escaped Cyrillic в изменённых файлах проверены:26textpaths, отдельный rg, совпадений нет.

## Поставка и ограничение следующего шага

Evidence archive: `artifacts/learning008-evidence.zip`, с корнем `/goal` и SHA manifest; без Geo, SQL dump, credentials и больших private logs. Source backups и private history сохранены. Счётчик:008 задача№2 из13 после006R1; осталось11. PARTIAL расходует задачу. Следующий Goal/Slice не начат.

Source/evidence commit **`063ea173f69d53d6174569cc1f2d5118a9b640f7`** опубликован обычным non-force push только в `wip/phantom-learning-008`, exit0. Независимый GitHub connector подтвердил HEAD, единственного parent062862ec…, exact27-path diff и все16source blobs (с учётом штатной EOL normalization), EVIDENCE blob `152bf507ccbabd98e1784a269697b4f8ecdeab3d` и ZIP blob `8800790ac6004b7affa6e4af1e4305a4863f6605`, size20027039bytes. Archive SHA256 `7179df6f3df4453c4e7b47734ebc4ec62503dee4b6253545799d4c334308f429`; независимая local CRC/manifest проверка1135entries PASS. Feature и007 remote refs остались точными исходными SHA.

Этот readback записывается отдельным documentation-only receipt commit после source/evidence commit: иначе нельзя честно записать собственный ещё не существующий SHA и результат push. Его точный HEAD указывается в финальном ответе. Product source, model и архив в receipt не меняются. EVIDENCE.publication содержит развёрнутые Git argv/exit codes и независимый readback. G8 source publication VERIFIED; финальный manual gate по008 остаётся REQUIRED, PARTIAL не принят автоматически.

Удалены только два собственных ненужных scratch files: duplicate EXPERIMENT.final.md после byte equality и неиспользованный DiagnoseFit008.java. Проверены absolute bounds и local+remote archive identity; recursive delete не применялся. Остальные evidence, исходные SHA backups и private TEST dataset/optimizer/checkpoints/ledger сохранены.
