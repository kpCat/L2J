# Результат PHANTOM-LEARNING-017

**TECHNICAL_VERDICT: PARTIAL017_PARTY_REWARD_UNPROVEN_CYCLE_CLOSED.**

Холодное завершение NO_FIT исправлено и выполнено на сохранённых данных016. Пригодный Party reward не восстановлен: в архиве отсутствуют факты, необходимые для доказательства причины первого отказа и законного terminal receipt. Новые игровые бои, FIT, EVAL и promotion не выполнялись. `TRAINED_MODEL=NO`, `GAME_ADVANTAGE=NOT_EVALUATED`, `PRODUCTION=OFF`. `OWNER_MANUAL_GATE=REQUIRED`.

Это задача **№11/13**; после выдачи остаются **2** задачи до обязательного пересмотра бюджета. Следующий Goal не запускался. Принятый владельцем016 остаётся `PARTIAL016_NATIVE_COLLECTED_DIRECTOR_CLOSE_FAILED`; его история не переписывалась.

## Приёмочные границы

|Gate|Вердикт|Доказательство и ограничение|
|---|---|---|
|G0|PASS|Свежий baseline eed183b7 после согласованного ff-only; SQL183 и frozen lineage; 1822 immutable originals и1823 backup copies|
|G1|PARTIAL|Для71 Party действий доказан неизбежный UNKNOWN earnings check; первый exception и13 Solo причин не восстановимы из сохранённых полей|
|G2|PARTIAL|RED→GREEN потери диагностики и DONE016; исправление пригодного Party reward UNPROVEN, V1 проверки не ослаблены|
|G3|PASS|Канонический census7/6/4, upper bound TRAIN≤13/AUDIT≤7; thresholds16/8/8 сохранены|
|G4|PASS|Настоящий external COMPLETED_NO_FIT, повтор с теми же SHA, optimizerDelta0; исторический phase FIT оставлен|
|G5|PARTIAL / NOT_RUN_NATIVE|0 новых и0 восстановленных native Party receipts. Offline fixtures явно NOT_NATIVE|
|G6|PASS|GS0/native0мин/FIT0/EVAL0/promotion0; повторов кампании и боёв нет|
|G7|PASS|Targeted Java/PS, jar/content, SQL held exits0, TEST OFF/ports free; CPU/RAM peak UNKNOWN|
|G8|PASS_DELIVERY|Обычный push0d7216ba, свежий GitHub parent/exact25/full25 SHA/blob и unchanged13 MANIFEST совпали; финальный receipt-only HEAD проверяется отдельно, manual gate REQUIRED|

FULL017 не заявляется, поскольку G1/G2/G5 не доказывают пригодный native Party результат.

## Причины потери Party и13 Solo исключений

Аудит начался с побайтовой внешней копии настоящих данных016. Исследованы51 settled/CUT эпизод,399 уникальных оплаченных native effects,29 distinct kills,84 исключения и27 настоящих Solo terminal receipts. Дополнительно прочитаны две реальные native attempts и старые G0 receipts. SQL before_json и application rows проверены отдельными read-only SELECT; отсутствующие тайминги оттуда не появились.

Все17 Party эпизодов завершились `NATIVE_KILL`. В каждом5 actors; ровно у одного, class94, EXP/SP null, у остальных4 значения известны. Штатный `TerminalRewardReceiptV1.java:142` требует известный canonical credited earnings каждого actor. Поэтому все71 Party action **неизбежно нарушают этот predicate**. Это доказательство непригодности имеющегося boundary, но не доказательство первого брошенного exception: writeStarted/writeFinished/nativePublished и terminalNanos для этих actions не сохранены. Причина UNKNOWN у class94 тоже не сохранена. Гипотезы PHASE_DEADLINE, owner loss или early freeze не выдаются за факт.

|Slot (с0)|Split|Режим|Paid = excluded|Actors с UNKNOWN earnings|Terminal|
|---|---|---|---:|---:|---:|
|17|TRAIN|STOCK|4|1/5|0|
|18|TRAIN|EXPLORE|4|1/5|0|
|19|TRAIN|EXPLORE|4|1/5|0|
|20|TRAIN|STOCK|4|1/5|0|
|21|TRAIN|STOCK|5|1/5|0|
|22|TRAIN|EXPLORE|4|1/5|0|
|23|TRAIN|EXPLORE|2|1/5|0|
|24|TRAIN|STOCK|3|1/5|0|
|25|TRAIN|STOCK|5|1/5|0|
|26|TRAIN|EXPLORE|5|1/5|0|
|27|TRAIN|EXPLORE|3|1/5|0|
|28|TUNING|STOCK|5|1/5|0|
|29|TUNING|STOCK|5|1/5|0|
|30|TUNING|STOCK|4|1/5|0|
|31|AUDIT|STOCK|5|1/5|0|
|32|AUDIT|STOCK|5|1/5|0|
|33|AUDIT|STOCK|4|1/5|0|

Итого Party: TRAIN43 (STOCK21 + EXPLORE22), TUNING14, AUDIT14. Для71 действий подтверждены доступные actor/profile/epoch, target/epoch и event membership; все проверки skill, event, instance, timing, lethal/native effects остаются действующими. Полного исходного Row и timing нет, поэтому подтверждать отсутствующие поля нельзя.

Оставшиеся13 исключений — Solo slot42, TRAIN/STOCK, `NATIVE_KILL`. Доступные actor/target/event/earnings не объясняют отказ; точная причина `UNPROVEN_MISSING_SOURCE`. В EVIDENCE.json перечислены все84 действия индивидуально (slot/episode/eventKey, доказанные checks, неизвестные timing и firstThrownCheck), а также51 эпизод. Исходные «IllegalArgumentException» остаются историческими байтами; подробный анализ записан отдельно017.

Из399 effects247 входят в сохранённые terminal actions,84 дошли до validation и были исключены,57 относятся к5 NO_LEGAL_TARGET:NO_SAFE_SOLO_OPENING,11 — к1 REQUESTED_DRAIN. Последние68 не означают13 дополнительных validation failures. Для331 дошедшего до validation действия247+84 сходятся. Прямые missingTiming/emptyEligible counters в старых51 отсутствуют и в новом census обозначены UNOBSERVED, а не0. Точные первые ошибки84 из этих данных восстановить нельзя.

## Пригодность TRAIN / TUNING / AUDIT

Все51 slots имеют настоящую selection, все51 settled/CUT;27 terminal дают27 точных selection/terminal пар, без fallback или чужого exact-cell match. Paid effect сам по себе не становится gradient example. Столбец «Цель V2» — число индивидуально допустимых целей; допуск всего FIT остаётся false.

|Scope / split / режим|Slots = settled = CUT = selection|Paid|Terminal|Exact pairs|Цель V2|Excluded actions|
|---|---:|---:|---:|---:|---:|---:|
|PARTY/AUDIT/STOCK|3|14|0|0|0|14|
|PARTY/TRAIN/EXPLORE|6|22|0|0|0|22|
|PARTY/TRAIN/STOCK|5|21|0|0|0|21|
|PARTY/TUNING/STOCK|3|14|0|0|0|14|
|SOLO/AUDIT/STOCK|6|59|4|4|4|0|
|SOLO/TRAIN/EXPLORE|12|124|9|9|7|0|
|SOLO/TRAIN/STOCK|10|98|8|8|0|13|
|SOLO/TUNING/STOCK|6|47|6|6|6|0|

TRAIN controls: class94 —5, class110 —3; exploratory pairs5+4, но у class110 две пары имеют лишь2 независимых controls после leave-own-block-out (minimum3), поэтому gradient5+2=7. TUNING3+3=6, AUDIT3+1=4. Три из7 TRAIN targets выбрали действие, отличное от stock; это не доказательство улучшения модели. Имеются2 класса и только SOLO scope.

Фактический недостаток до порогов16/8/8: **9 TRAIN,2 TUNING,4 AUDIT** плюс отсутствие пригодного PARTY scope. Dataset SHA `bcdf86051d090a9ac46f533e287c89b87a71dace173e9cc3c074878c005402b6`. SQL подтверждает51 episode,399 event,27 terminal и247 typed action hashes; ранее применённых совпадающих credit0. Исторических application rows148 advantage/13 terminal; они не изменялись.

Даже идеальное восстановление всех Party receipts даёт TRAIN≤7+6=13 и AUDIT≤4+3=7. Это более строгая граница, чем предварительное≤15: новые Party controls не исправляют две Solo exact-cell пары с недостатком независимых controls. Поэтому FIT невозможен и при полном Party rescue; attainable Party yield пока неизвестен. Thresholds, loss objective, codecs, SQL schema, exact-cell/leave-block-out правила не менялись.

Проект **одного будущего** набора, не запуск017: заранее законно отобрать3 exact cells с≥2 классами/2 scope. На cell6 независимых TRAIN stock +8 TRAIN exploratory +5 TUNING +5 AUDIT, всего72 slots; теоретически24/15/15 целей до attrition. Это предложение не гарантирует yield: в016 exploratory SOLO7/12, PARTY0/6. Нельзя закладывать Party yield=100%; сначала отдельное разрешение и измерение пригодности. При среднем времени016 это примерно100,46 native минут, далеко за пределами017. Не снижать thresholds и не добирать данные повтором завершённых боёв.

## Исправление и реальное холодное закрытие

Изменены только5 согласованных исходников; reused существующие collector/runtime, V1 constructor/codecs, `ActionAdvantageV2`, shared owner и runners. Production Combat, trainer weights и schema не менялись.

- Collector теперь сохраняет для будущего незавершённого episode канонический Row, исходный native timing либо явное отсутствие, boundary и bounded snapshot diagnostics. Точная причина — allowlisted validator category; неизвестный exception не раскрывает произвольный JDBC/secret payload. Итоговый summary различает missingTiming, validationFailures, eligible/emptyEligible; census также показывает no matching selection и отсутствие исторических counters. Запись в settled016 запрещена.
- Runtime observer использует уже полученные previous/current snapshots без дополнительного snapshot call. По умолчанию observer=null, после исходных HP/MP чтений. Реальные latency/CPU overhead не измерялись: GS не запускался.
- `Save-TrainCycleState013` допускает только version-bound `DONE016` с подтверждённым frozen NO_FIT graph; произвольные DONE017/SOME999 и013/014/015 не допускаются. Обычный повторный FIT с existing checkpoint отказывает `TRAIN017_EXISTING_CHECKPOINT_USE_REPAIR` до dispatch/config/Java/SQL.
- Явный `RepairClosure017` обходит startup/failure-journal путь, требует физический owned TEST/OFF/absent processes/free ports, оригинальные source/control/JAR/owner/config pins, точный frozen g2 graph и бюджет, fresh SQL183 и stoppedSQL file hashes. Канонические inventory sets исключают alias/omission. Read-only exclusive lease existing director.lock исключает конкурирующего владельца.
- External receipt публикуется под named mutex, durable create-only moves, marker last. Повтор только читает exact bytes; непонятный pending, drift и trainingCredit=true отвергаются. Interrupted валидный receipt может закончить отсутствующий marker, не переписать receipt.

Реальные вызовы первого repair и повтора: exit0, `COMPLETED_NO_FIT JAVA=0 SQL=0 GS=0 FIT=0`. Closure SHA `4FD8F058C25E5A6290BC133ABF93C3B395BC6E694C5D469D72204E2FE71934A2`; marker SHA `D24E426C10C932315F017E3EA6E6306A256620BA416092203546F57D49A94D8F`. Повторные SHA совпали, optimizerDelta0, trainingCredit=false. Исходные director phase FIT/fits0/completedCycles0, ошибка016, reservation/result/checkpoint и бюджеты неизменны. Отдельный repair подтверждает один завершённый NO_FIT цикл; он не переписывает историю в DONE016.

## Проверки, затраты и сохранность

Focused RED→GREEN выполнены для exact refusal loss, version-bound DONE016, cold dispatch до внешней работы; review выявил3 Important и2 Minor, оба Minor повышены до Important как обязательные017 contracts. Все5 исправлены за один pass: process helper write/root, SQL alias sets, concurrent receipt publication, direct counters, negative credit marker. Дополнительно проверен занятый existing TEST lock. Один независимый review, второго не было, deferred items нет. Synthetic fixtures помечены NOT_NATIVE. Реальный composed ONE_FIT не запускался, поскольку запрещён.

PASS: Ant `phantom-learning016-test`, `phantom-autonomous-training-test`, `phantom-learning008-test`, `phantom-learning007-test`, `jar`, `phantom-humanized-v3-content-validate`; PS `Closure017`, `Test-Cycle013`, `Readiness016`, `Headless013`, `Test-Config`, `Test-Atomic`; final standalone Party contract и настоящий census. Последняя сборка learning016+jar42сек после последней правки Runtime. Пять прежних compiler warnings не устранялись. Не запускались broad verify, production smoke, FIT/EVAL.

GS0170/native0мин; две короткие owned SQL-сессии только SELECT, held exit0/0; repair сам SQL не запускает. Исторические0162GS/4269588мс=71,1598мин не изменены и не обнулены. Java CLI Xmx256MiB; peak CPU/RAM UNKNOWN_NOT_MEASURED. Wall seconds targeted gates и размеры логов в EVIDENCE.json. Резервные копии1823 файлов,29127467байт; частный evidence не коммитился.

Финальная сверка:1822 immutable originals и1823 backup copies без drift. Единственное изменение operational root — worker-process.json после owned SQL Export; старые байты сохранены. Все historical016 campaign/journal/checkpoint/receipts и оба native budgets неизменны. Исторический SQL183 SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D` совпал before/after. DRAFT14, terminal Adam903/223, acceptedTEST2, pointer12, Geo203/protected207 проверены physical/hash guards; конкретные pins в EVIDENCE.json. TEST source/JAR не заменялись. MAIN/PLAY, общая MariaDB и чужая Studio работа не изменялись. Own TEST финально OFF,0owned processes,33357/18001/19301 свободны.

Ошибки оркестрации не засчитывались как RED: sandbox ACL/dubious ownership, неверный API URL encoded slash, два первых пути внешнего audit reader, PS5 manifest wrapper/File.Replace NullString, неверный parse-helper при компиляции, fixture equality неполного UNKNOWN map, temp physical path ACL, inline Cyrillic shell encoding, финальная hash-проба без elevation, первая передача cohort вместо installation в CLI. После корректировок подтверждены exit0; старые данные не менялись. Неуспешная ACL-проба только вывела ошибки чтения и не является evidence drift. Новая неприятная находка: даже SQL не хранит missing native write timing; будущая диагностика не исправляет это задним числом.

Принятые процессные решения: external exact5 edit copies/ledger вместо дополнительных repo artifacts; независимое C до B; явный Repair вместо повторного FIT; hard pins accepted g2 вместо самосогласованного нового graph; оба review Minor повышены и исправлены; существующий read-only TEST lock плюс отдельная external publication mutex. Они не расширили25-path allowlist. Без новых branches/worktrees, cleanup/refactor и повторных кампаний.

## Поставка и передача

Согласование владельца получено до исходников: «Утверждаю план и указанный scope». Исходный approved IMPLEMENTATION_PLAN017.md сохранён без переписывания его proposal gate. Структура сокращена согласно HANDOFF.md: diagnosis/census/director/acceptance/handoff объединены здесь, индивидуальные84/51 записи и SHA — в EVIDENCE.json. COMMANDS.md содержит воспроизводимые команды; READBACK.json — отдельный независимый publication receipt. Public файлы не содержат raw SQL, owner nonce, model weights или личный external путь.

Git использовался по явному заданию017 и approved plan; полный перечень команд и причины в COMMANDS.md. Только feature/phantom-world,25exact paths, обычный non-force push. Code CRLF working copy соответствует прежнему LF Git blob contract; unchanged13 task inputs публикуются побайтово с MANIFEST, без EOL преобразования. Full-body readback проверяет Git blob bytes, а executor receipt отдельно фиксирует physical CRLF hashes.

Финальные проверки кодировки после assembly отражаются в EVIDENCE/READBACK: mojibake-маркеры в изменённых файлах проверены; escaped Cyrillic в изменённых файлах проверены. Старые совпадения вне изменённых строк не исправляются.

После передачи нужен ответ владельца по manual gate. Не запускаются следующий Goal, будущий72-slot dataset, FIT, EVAL или production promotion.

Независимая публикационная проверка: delivery commit `0d7216ba43d1bdf26c5557375caddff367bed6f6`, parent `eed183b70dd8eb6e1a6db98b3d5fb825281b5a2f`; GitHub connector и отдельный HTTPS readback подтвердили25exact changes/25full bodies, Git blob SHA1 и SHA256,13 byte-exact inputs/MANIFEST. Источником проверки был GitHub, не local origin cache. Публичный READBACK фиксирует этот delivery commit. Следующий commit меняет только RESULT/EVIDENCE/READBACK; его fresh HEAD/parent/exact3 и все25 тел проверяются после push во внешнем receipt, окончательный SHA сообщается владельцу. Это исключает бесконечное переписывание self-hash. Дополнительная точная git-команда: `git ls-tree HEAD -- <all25 exact paths from READBACK.json>` с тем же одноразовым SOURCE safe.directory prefix, чтобы сопоставить ожидаемые blobs. Source hashes physical CRLF в настоящем cold receipt не изменились.
