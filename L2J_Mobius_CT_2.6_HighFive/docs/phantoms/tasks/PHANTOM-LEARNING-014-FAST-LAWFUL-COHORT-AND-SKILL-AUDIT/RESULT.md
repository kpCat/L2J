# PARTIAL014_HEADLESS_TIMEOUT_NO_FULL_TRAIN_ADMISSION

**FULL014 не достигнут.** Измерена и минимально исправлена одна причина дорогостоящего отказа. Полный законный TRAIN admission не получен за два разрешённых readiness запуска. GameServer0/3, native combat0/60min, CUT0, FIT0, optimizer delta0, new model0, new EVAL0. Игровое превосходство не оценивалось и не объявляется. Manual gate014 **REQUIRED**; следующая задача не начата. №8/13, после выдачи осталось5 задач до пересмотра бюджета.

Владелец принял предыдущий Goal013 как PARTIAL в текущем диалоге. Его результаты, issued deadlines, журналы, модели и история не переписывались. Утверждён IMPLEMENTATION_PLAN014/exact6 source. Использован один direct causal fix из двух допустимых; production Combat/Party/SkillData/buffs/config/reward/SQL/navigation не менялись.

## Причина и фактическая экономия

Baseline014:272357ms, TIMEOUT. Natural recovery2=199668.479ms, в том числе134458.889ms полного ожидания перед отказом HEAL. Roster reproduction926.879ms, materialization419.698ms, anchor travel2.662ms и formation413.031ms значительно дешевле. Recovery потребил73.31% всей readiness wall. Это measured causal path, а не гипотеза о медленном SQL/создании Player.

`prepareAcquired` требует полного естественного HP/MP, после чего passive opening не содержит раненого friendly получателя. Канонический `PhantomPartyTactics` предлагает HEAL только при недостатке HP. `SavedActor.requireReproducible` уже отвергает saved active effects/reuse/summons; no-native-action pre-CUT guard сохраняется. Ожидание134s не может удовлетворить этот HEAL-контракт.

Теперь такой TRAIN HEAL proposal отклоняется до roster reproduction/materialization/recovery с причиной `FULL_RECOVERY_HAS_NO_WOUNDED_HEAL_RECIPIENT`. Visitation/cursor обновляются до отказа; denominator сохранён. В post-fix native receipt этот отказ занял **0.1742ms**, без выдачи HP/MP, изменения характеристик, learned skills, seed, порядка fairness или production лечения. Само законное лечение раненых союзников не отключено: focused test использует тот же PartyTactics при100% и50% HP.

Это улучшение стоимости конкретной неподходящей заявки. Полное формирование cohort не ускорено до требуемого результата. Нельзя сравнивать272357 и515549ms как равные workloads: лимиты, число попыток и достигнутые стадии различаются. [Полный профиль](PROFILING_RESULT.md), [baseline](PREPARATION014-S1.json), [post-fix](PREPARATION014-S2.json).

## Полный native admission остаётся недоказан

Post-fix515549ms, TIMEOUT,3 admission-only arm proofs. Solo class94 имеет обе свежие A/B identities и одинаковый physical SHA; время до первой полностью paired cell **74153.531ms**. Native entities4/unique4. Реальные DAMAGE alternatives1230:28 и1275:14. Party class110 получил только STOCK arm с четырьмя реальными alternatives1148:13/1234:28/1239:28/1267:14; LEARNED arm прерван deadline. Второй и третий полный paired cell не достигнуты. `cells.tsv` и `training-admission013.json` не sealed.

Два иных Party attempts class103 потратили134460.161ms и134543.559ms recovery, затем получили `WAIT_GROUP_MP`: требуется3060MP при фактическом полном2806MP; штатная80% граница2244.8MP. Эти отказы не ослаблены. Их дешёвое immutable отрицание не было доказано через доступный canonical API в разрешённом scope: projection находится в private production `L2jPhantomPartyBackend.groupViable` и зависит от выбранного loadout, native stats, условий и target. Дублировать этот policy или считать текущую нехватку MP неизменяемой невозможностью нельзя.

Третьей readiness, изменения issued deadline или автоматического запроса нового бюджета нет. Суммарная readiness wall **787906ms (13:07.906)**, включая SQL startup/worker/cleanup; immutable caps480+720s, reserve210s внутри каждой. DB-free builds выполнялись отдельно до выдачи deadline и не входят в эти интервалы; они честно отражены вCOMMANDS. Незанятый reserve и остаток первой сессии не перераспределялись задним числом.

## Skills и buffs

[Skill audit](SKILL_AUDIT_RESULT.md): фактические learned inventories, Hydro Blast1235:28 у class103/classIndex0, exact enchant/subclass contracts, unsupported families, shortlist rotation и native target/MP/reuse/Geo/owner gates проверены на соответствующем уровне evidence. Фактическое исполнение DAMAGE/HEAL и paid effect014 не наблюдались, поэтому G4 остаётся PARTIAL, без false all-classes claim.

[Buff audit](BUFF_AUDIT_RESULT.md) выполнен read-only: existing suite9/9 PASS, code/contract checks missing/expiry/healthy, MP, scope, songs/dances, heal priority. Mage/fighter/hybrid marginal benefit и реальные duration/stacking014 — UNKNOWN. Значимая функциональная ошибка existing баффинга не подтверждена; production оставлен без изменений.

## Safety и проверка

- Два headless worker exit2 и own SQL exit подтверждены held receipts; native starts0, никакого forced kill. Финально automatic OFF/phase OFF/outcome false, own processes0,33357/18001/19301free. Director сохраняет точный TIMEOUT/RESOURCE_LIMIT, а не NO_DATA.
- Final canonical cold Export и5SELECT exit0:183rows byte-identical исходным/F3, SHA2569A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D. TEST2/DRAFT14/Adam903/223/apps48/gradients16/pointer12 сохранены. Selected/Adam/pointer exact hashes дополнительно проверены.
- Final canonical Preflight exit0:203Geo/207protected. MAIN7e997636/feature, tracked0/untracked112; MAIN/PLAY/foreign SQL не изменялись. TEST содержит только exact6 copied source changes относительно42d6b675 и прежнюю private `.phantom-local`; автоматического checkout/restore не делали.
- Targeted Ant014/013/012/011/009/008/007/JAR exit0,6m13s. PS5.1 budget/config и старый headless lifecycle/3OS child exits PASS. Tests/fixtures не являются игровыми эпизодами. Exact frozen config012 не найден в двух прежних evidence directories и canonical campaign; новый actual replay012 не выполнялся. Историческая EVAL012 не использовалась как TRAIN.
- Mojibake-маркеры в изменённых файлах проверены отдельным полным scan: NO_MATCH.
- Escaped Cyrillic в изменённых файлах проверены отдельным scan, включая XML: NO_MATCH.
- После Ant исправлены только CR в десяти добавленных строках build.xml; XML parse и exact diff-check PASS. Java/JAR не менялись. TEST XML приведён к тем же bytes после выхода; прежний compiled input fingerprint и byte backup сохранены, повторный headless не заявляется.

Публикация — scoped commit и обычный non-force push только feature/phantom-world, затем независимый full-body GitHub readback исходников и evidence. Фактические SHA и scope — FINAL_SCOPE/READBACK; команды — COMMANDS. Этот PARTIAL не является PASS manual gate и не разрешает следующую задачу.
