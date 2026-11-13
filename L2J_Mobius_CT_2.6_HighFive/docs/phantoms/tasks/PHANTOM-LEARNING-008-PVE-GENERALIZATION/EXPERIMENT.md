# Эксперимент008

`/goal PHANTOM-LEARNING-008-PVE-GENERALIZATION`

Протокол: одна общая V3, TRAIN94/103, independent TUNING94/103, unseen AUDIT110 без gradients, HEAL105 только regression EVAL. NN topology, state128/action64 и public weight codecs сохранены. Выбор catalog — canonical admission/coverage/Geo, без отбора по neural score или outcome. До CUT закреплены quotas, order, identities, objective, Adam и build. PLAN содержит исходное согласование; PROGRESS — все последующие bounded исправления.

Начальные selected DRAFT11 weights7de6… отличаются от terminal Adam11 weights6f3…. Fit продолжает именно terminal Adam11,855updates/191epochs; teacher11 при collection остаётся frozen selected11. Baseline008 не переинтерпретирует исторический codec007.

## Labels, baseline и независимость

Objective `FACTUAL_RESIDUAL_UTILITY_008`: U=.70terminalReward+.30clip(2actualHP/targetMAXHP−actualMP/500−elapsedNanos/1e10,−1,1). Residual=clip(4(U−baseline),−1,1). Это фактическая utility выбранного действия относительно TRAIN-only nuisance forecast, а не causal stock advantage.

Baseline получает только TRAIN_A controls. Для initial TRAIN_B допускается exact class/level/legal shape/physical context с минимум тремя другими controls и исключением собственного block. Иначе используется class-balanced kernel ridge lambda .01 на существующих192features; TRAIN forecast всегда leave-own-block-out. Held-out forecast использует frozen TRAIN_A. Поздние TRAIN observations используют forecast, без ложного exact matching. Никаких AUDIT/TUNING gradients или подстановки baseline0.

На TRAIN_B не более двух первых chronological paid primary DAMAGE actions, на других arms градиентов нет. Одна exploration opportunity/episode: epsilon .25; первый выбор stock имеет p.75, каждая альтернатива .25/(K−1). Поздний фактический stock имеет conditional propensity1; uniform-action unbiased claim не делается. Clipped IPS≤4 нормализован по episode и классу, длинный episode не доминирует. Unmatched B исключён из gradients, но сохраняется в raw denominator.

Независимое quality n — одна initial paid decision/episode:8TUNING,8AUDIT. Дополнительные held observations не увеличивают n. Никаких fabricated outcomes для невыбранных действий. OOD расстояние — только описание frozen192features; threshold не подбирался по AUDIT.

## Coverage, отклонения и бюджет

План48collection attempts +16EVALarms +4stock/stockcontrolarms. Catalog56slots включает8pair slots для EVAL. TRAIN32:16A/Bpairs, по8на94/103, две physical cells/class, AB/BA. TUNING8, AUDIT1108. EVAL по2pairs/class94/103/110/105, обе очередности; controls по1pair94/105. DRAFT11 native C-arms не добавлялись.

Сцены используют уровни77/78, A/S, NPC21798/21799, разные заранее закреплённые enchant/HP/MP/distance; EVALenchant4 не входит в TRAIN0/2. Actors/targets fresh, RNG не синхронизирован. Source positions двух NPC разделяют один anchor между классами; нельзя называть это независимым географическим переносом.

Попытки2/4 отказали до CUT из-за новых TEST Geo positions; они не повторены. Три TRAIN103 episodes имели cold helper range0, ещё один остановлен drain. ActorFixture class-aware fix прошёл RED→GREEN; explicit recovery4 потратил заранее выделенный резерв. Все48attempts сохранены:42completed,4incomplete,2pre-CUTfailure. New class103 alternatives исполнены с p.125, class94 с p.25. Final targets24 из12matchedBepisodes,14class94/10class103; TRAIN_A13,110/105gradients0.

Ограничения8starts/90native min/60collection min соблюдены:6starts,42.5253min total,28.8400min collection,1recovery,pendingfalse. Startup, неуспехи и teardown включены; headless/Ant/SQL учитываются отдельно. Runtime deadline enforcement проверен кодом, фактическое достижение wall ceiling не наблюдалось.

## Fit, freeze и отрицательный результат

Fit один раз продолжил Adam855→887,191→207. COMMIT создал DRAFT12, но cold reader затем ошибочно сравнил residual loss с legacy local-action labels. Versioned authenticated reader исправлен RED→GREEN, без refit, SQL repair, смены labels или hyperparameters. Restore поднял тот же checkpoint; duplicateCreditAvailable=false.

TUNING выбрал **epoch0**, selected12 SHA6f3… = terminal parent11. Новые gradient steps не улучшили выбранную политику. Terminal207 SHA7d3… остаётся в optimizer и не подменяет выбранный checkpoint после AUDIT/EVAL. Итог **PARTIAL / NO_TUNING_CHECKPOINT_IMPROVEMENT** обязателен, несмотря на новую строку model12 и paid EVAL divergences.

Same008-label AUDIT110 n8 MSE: candidate .06815411865, DRAFT11 .07947880127, TRAINconstant .06951536195; rawUvariance .00528785249. Сравнивать это напрямую с007 другим objective нельзя. Малое n, forecast bias и отсутствие improvement от008 не позволяют causal/generalization superiority claim.

## EVAL и границы вывода

Stock против frozen DRAFT12:16arms,8matchedcompletedpairs, exact physical facts, independent native randomness, explorationOFF и trainingCreditfalse. Paid divergences94/103/110/105 =5/2/6/1. Это lawful canonical transfer predecessor terminal policy на новые contexts; эффект gradient steps008 не выделен. Все per-class game verdict INCONCLUSIVE при n2. Bootstrap interval описательный, не статистическое доказательство.

Stock/stock controls:4arms/2pairs,0policydivergences; elapsed deltas94−3299.54ms и105−678.36ms показывают вариативность даже без изменения политики. HEAL native полезный эффект и MP debit сохранены; два случая не доказывают отсутствие всех Party regressions. Полные deaths/loot/consumables/economy/wholePartyDamage остаются UNKNOWN.

После EVAL и controls exact cold-export подтвердил unchanged selected weights, optimizer, target bytes, applications, labels и metrics. Accepted1/2 и old007 history сохранены; no promotion. Compact archive содержит raw receipts, sealed protocol/rebuild receipts, failed slots, model/Adam, analyses, focused logs и SHA manifest. Final manual gate REQUIRED.
