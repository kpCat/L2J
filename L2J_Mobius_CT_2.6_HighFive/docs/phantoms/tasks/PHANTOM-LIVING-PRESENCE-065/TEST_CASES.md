# TEST_CASES065 — компактный, но причинный набор

До тестирования явно записать OWN, все test player identities, исходную GeoData/Calendar, capacity, time horizon, целевые WorldRegion и ожидаемые события. Не использовать GM-клиент или искусственные сетевые подключения ради PASS.

**T1: cap-full positive.** Малый TEST cap (например3–4) и несколько уже materialized actors. Human-local demand/Phantom demand соединены через production ports. Старый действительно невидимый, idle, owner-safe actor вне наблюдаемой области освобождает permit; новый visible-local actor получает native Player/World membership в ограниченном числе pulses. Записать before/after root, profileId, epoch, reason/permit/World, DB receipts. Провести RED на исходной проверяемой поломке (когда фактическая причина установлена).

**T2: hard-held negative.** При заполненном cap все существующие materialized относятся к real Party, current human-visible, combat/cast/move, accepted child, commerce или persistence pending. Никаких eviction, duplicate Player, lost item/goal. Новый спрос остаётся честным pending с причиной. По завершении настоящего действия допустима новая попытка, но без отмены earned work.

**T3: lifetime races & fairness.** Пара human demand одновременно, два кандидата с разным возрастом последнего спроса, stale generation/wrong instance и резкое перемещение человека. Atomic revalidation перед cleanup; finite wait/hysteresis, no ping-pong, no starvation, один release permit не расходуется дважды.

**T4: native town envelope.** Выбрать Giran+другой stock town на фактических GeoData/anchors. Измерить 3D/native surrounding vs prewarm; проверить край town/world region, подземную Z плоскость и другой instance. Не делай нового глобального географического каталога только под один город.

**T5: 24h schedules.** Не ждать сутками: 7×1440 synthetic CLOCK evaluations двух-трёх existing schedule cohorts плюс persisted snapshots/accepted native journeys. Проверить как минимум одну активную когорту для каждого часового окна при достаточном population; типовые смены 12–16ч в сутки; воскресенье/полночь/возможный DST тест отдельно. Проверить native state boundaries на нескольких точках, не менять профили во время основной сцены.

**T6: ordinary current JAR.** Новая собственная TEST/065 release DB; обычный Main START/STATUS с synthetic OFF, наблюдать native presence и earned action при законном расписании/доступном спросе. SAVE/STOP/sameDB START, новые полезные факты только если реально наблюдались. Время теста bounded; отказ из-за SLEEPING честно классифицировать вместо вмешательства в календарь.

**T7: affected regression.** Selection/scheduler/materialization/Geo, M2 real Party/Store hold и baseline current M1 life. Не запускать непричастные рейд/Rift/semantic/native soak. Если изменён shared scheduler, выбрать актуальные affected suites. Одна финальная source build + соответствующие affected gates, без повторов без причины.

Сценарии T1–T5 выполняются на isolated native fixtures с подготовленными до measurement input. T6 — ordinary без управляющего тестового цикла. Test fixture не доказывает actual network client QA. Обязательно отрицательные случаи, чистый lifecycle/stop и сохранение старых source evidence.
