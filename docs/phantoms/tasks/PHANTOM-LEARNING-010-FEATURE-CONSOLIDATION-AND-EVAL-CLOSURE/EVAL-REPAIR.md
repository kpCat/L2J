# EVAL-REPAIR010 — admission и окончание собственного JVM

Исправления опубликованы после VERIFIED A, source commit `6c97ee43d25acc37b42599b4e5ef138ea0980f4a`. Product scope — восемь существующих файлов, перечисленных в EVIDENCE. Combat/Party/Player, optimizer, reward, splits, schema, MAIN/PLAY и игровые ограничения не менялись.

## Почему EVAL009 не нашла сцену

Immutable campaign F: TRAIN3cells/19refusals, независимая EVAL0cells/222refusals. В EVAL202 отказа TARGET_BOUND_ALTERNATIVES (186eligible=0,16eligible=1) и20 TARGET_BOUND_HELPER_MAGIC_ENVELOPE. В TRAIN18+1 соответственно. Обе стороны начинали с тех же10 source snapshots.

Каталог ограничивал первые12 геометрически подходящих кандидатов до target-bound проверки actual acquired навыков. В этой странице оказывались NPC уровней76/82, не проходившие canonical magicLevel/две доступные альтернативы; законные более поздние кандидаты не рассматривались. TRAIN имела3 допущенные сцены NPC21318lvl74. Старые records не содержат детальной причины каждой eligibility failure: нельзя приписать все202 только magicLevel.

Теперь общий TRAIN/EVAL `admitScene` проверяет настоящую цель, actual learned rank/enchant, ready eligibility/cast cycle, две альтернативы и прежний helper envelope до bounded lawful page. Target-bound proposal кешируется. Геометрия, Geo/LOS, faction/raid, actor capacity, native safety и полный ordinary Party admission перед CUT сохраняются. Ни class IDs, ни skill IDs не вписаны в алгоритм.

Источник независимой EVAL задаётся immutable reference campaign/dataset F. Из pre-CUT roster исключены6 TRAIN snapshots и physical duplicates; остаются4 неизученных варианта. TRAIN outcomes/rewards не используются. Свежие actor/target incarnations создаются для каждой стороны. Известный historical ingress без source-index не выдаётся за новый provenance.

`admit-eval` использует настоящий Player/Skill/NPC/Geo и тот же runtime admission, но возвращается до CUT, действий и GameServer listeners. Receipt требует две сцены Solo DAMAGE + Party HEAL либо DAMAGE, пустые native actions и равное training state. Успех headless не засчитывается как завершённый бой.

## Честная парность

Один pair = два settled arms STOCK/LEARNED с равными pre-CUT физическими фактами, свежими incarnation и независимыми paid event keys, завершёнными terminal outcomes. Raw effects отдельно сохранены; в numerator допускаются только canonical NativeTiming внутри CUT→terminal либо прежнее linked lethal exception. Незавершённый arm или reused incarnation не считается парой.

Native risk зависит от profile ID. Первый arm сохраняет thresholds до CUT; второй выбирает следующий unused monotonic profile ID с тем же canonical risk в bounded512 поиске через существующий TEST fixture AUTO_INCREMENT pattern. Actual ID и uncovered/covered thresholds перепроверяются. Нет discarded actors, повторных эпизодов, нового risk policy или ослабления caps.

Immutable `evaluation-arm.tsv` позволяет resume прочитать settled arm без нового выполнения. Reserved без settled ledger не повторяется. Partial/cancelled schedule не получает aggregate/evaluation.complete. Owner отдельно требует ≥2completed pairs, Solo+Party и AB/BA balance; одна `.complete` этого не доказывает.

## STOP009 и исправление времени

Старый JVM25868 создан13.11.2026T23:53:00.7888121Z, ready23:53:24Z. Старый resources finished23:54:50.3778671Z имел exitObserved=false. Stop timeouts23:55:21.911/23:55:53.436, batch.complete23:55:56.344, shutdown в логе23:56:01.496. Planner не проверял stop во внутренних обходах; Process/handle освобождался до полноценного drain proof. Точный kernel exit009 неизвестен, его нельзя восстановить из log timestamp. Charge0095803240мс и10starts остаются неизменными.

Cancellation теперь проходит через discovery/NPC/anchor/target loops; partial cells не запечатываются. Owner удерживает исходный Process/handle до Stop, получает OS ExitTime, фиксирует terminatedUtc/nativeElapsedMillis отдельно от observationUtc, затем Dispose. Receipt идемпотентен. Некорректный тип/дата/несогласованный interval не является exact time; unknown не даёт новый EVAL budget.

Deadline ограничен UTC и monotonic Stopwatch с резервом shutdownTimeout+30с. Перед каждым GS создаётся immutable reservation; после exit — charge. Накопительный010 journal отдельно от009, максимум3/45. EvaluationOnly запрещает FIT/CHECKPOINT, TRAIN exploration и новый training credit. Java/PS config contract одинаков; legacy009 bounds не менялись.

## Проверки до native

Runtime RED→GREEN: lawful-before-cap; config3/45; native risk equality. Compile RED для отсутствующего arm codec отдельно помечен, не выдан за runtime test. Existing source-contract проверяет cancellation, facts/freshness/pair denominator, settled reload, bounded risk/cap и timing. Config runner проверяет cadence1 и запрет обучения. PS Test-Config/Test-CimExitRace имеют наблюдённые RED/GREEN, включая malformed exact-time tuple.

Канонические JDK25 Ant autonomous009/008/007/jar exit0 (145с). Existing inventory/shortlist/eligibility/curriculum/objective/config/catalog routes exit0. Статическая компиляция не заменяет native EVAL и STOP; фактические исходы и ограничения находятся в RESULT/EVIDENCE.

Первый native010: 15с окно, четыре cells,8arms; Solo pair completed, Party3pairs incomplete. Paid HP/MP и HEAL эффект реальны, но owner отказал EVAL_COMPLETE. Эта кампания сохранена. Второй прогон объявлен до исходов: существующий family filter DAMAGE, окно180с после TIMEOUT, frozen14 и прежние guards; новый campaign переносит prior1/265981мс без сброса бюджета. Отрицательные исходы первого прогона остаются в denominator отчёта.

Второй native010 завершился до CUT:0cells/84target-bound refusals,0arms. Геометрический поиск live world не нашёл изолированную точку (1584NO_ISOLATED_TARGET_POINT); headless перед этим имел4lawful cells. Это runtime-зависимость NPC census, а не доказательство пригодности второго headless receipt для живого мира. Точное native время221044мс, cumulative487025мс/2starts, graceful exit подтверждён. Окно180с само по себе не участвует в target-point генерации; нельзя объявлять его причиной этого отказа.

Дополнительный узкий fix расширяет только геометрическое покрытие прежнего acquiredTargetPoints: вместо24точек добавляется bounded200-шаговый круг внутри существующих MAXIMUM_TARGET_DISTANCE=2000. Старые точки сохранены, кандидаты по-прежнему упорядочены по seed/identity; исходы и model scores не используются. Все area/Geo/LOS/path/faction/aggro проверки и новый pre-CUT native check остаются прежними. Focused runtime RED→GREEN фиксирует неполноту старой страницы и запрет выхода за canonical distance; bounded каталог <400points. Это не гарантирует lawful scene при любом живом мире. Последний разрешённый GS возможен только после повторного admission и успешной сборки, с перенесённым cumulative487025мс/2starts.

Третий GS после этого fix реально нашёл4lawfulcells. Однако первый Solo arm после CUT упал на `PhantomCombatRequest`: адаптер передаёт оставшееся окно целиком, а выбранные оператором180с превышают неизменный `PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS=120000`. Это ошибка выбора параметра010 и неполный preflight contract; успешный headless не покрывал post-CUT request constructor. Native113990мс, cumulative601015мс/3starts, settled arm0, repeat запрещён. Ни эпизод, ни aggregate не объявлены завершёнными.

После исчерпания starts добавлен только fail-closed config guard EvaluationOnly: Java использует canonical MAXIMUM_TIMEOUT_MILLIS, PS зеркалирует120с. Existing Java/PS config focused tests сначала приняли180с и упали RED, после guard GREEN; Ant autonomous/JAR PASS. Это source fix `add152c6a114166a98bf41416ed6b09ff8b6e2b9`, native подтверждение после него **NOT_RUN**. Combat request/policy/owner не менялись. Финальный private config120с/OFF, budget3/601015мс; четвёртый GS не запускался. G4 остаётся FAIL, FULL010 не достигнут.
