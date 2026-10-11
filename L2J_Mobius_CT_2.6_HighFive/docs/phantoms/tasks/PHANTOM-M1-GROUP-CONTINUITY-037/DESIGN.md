# DESIGN037 — восстановление замкнутого контура игры

## Решение
Сохраняем существующий Java Phantom-слой и stock Mobius. Не меняем общую архитектуру.
Для всех пяти failed IDs строим одну причинную таблицу, группируем по реально общим
producer'ам, затем исправляем класс нарушения один раз. Управляющая цепочка:
`exact live goal → ready → travel/arrival → registered AutoPlay+AutoUse → native work
→ временная пауза/исчерпание локального варианта → законное перепланирование → resume`.
Вторая обязательная цепочка — сохранение и release исходного owner после earned drain.

## Разделять четыре вида событий
A. GAMEPLAY: нет целей/ресурсов/выхода из pause, недостижимая цель, навсегда stale goal.
B. LIFETIME: смерть, lawful календарный выход, реальная дематериализация, смена epoch.
C. EVIDENCE: sample взят от старого owner, неверная граница, пропущенное native событие.
D. PERSISTENCE: настоящий failure native store либо несогласованная запись.
Здоровый FULL_SAVE036 запрещает объявлять D лишь из слова lineage в evaluator.
Сам по себе classifier не отменяет ни одного исходного FAIL.

## Ветви после диагноза (не заранее выбранные fixes)
- Unavailable/stale target: исправить переход в existing local replan и его resume;
  проверять arrival и естественную доступность альтернатив. Не запрещать joint target.
- Native resource recovery: сохранить штатные MP/cooldown/charges/regen; разрешены
  ограниченные тактические решения внутри существующего Phantom adapter по доступным
  навыкам/целям. Не выдавать MP, не поднимать rates, не заставлять каждого мага бить кулаком.
- Temporary checkpoint / registration mismatch: ровно одна current session и её два
  stock registrations; distinguish PAUSED vs REVOKED; дочерние earned callbacks заканчиваются.
- Publication/replan: current claim+row versions+object+epoch; использовать replacePlan,
  finishVisiblePublication и decision.reload. CAS race не лечить unconditional overwrite.
- Wrong native attribution/phase completion: правка только установленного writer→sensor
  завершения с причинным отрицательным тестом. Не удалять sticky fault/увеличивать capacity.
- Legitimate death/offline: не «лечить» календарь/смерть; объяснить исторический FAIL,
  зафиксировать native recovery отдельно. Stable-continuity имеет естественные предпосылки
  в RUNBOOK; полного lifetime-away gate в этом задании нет.

## Согласованная граница вмешательства
Primary files: VisibleAutoPlay, BackgroundDecision, HistoricalBackgroundService,
HistoricalBackgroundPlanner. Conditional: VisibleFarmTravel handoff; PlayerNativeEvidence
observation lifecycle; PhantomSystem только существующее visible pre-work wiring.
Произвольный core rewrite не разрешён. SOURCE_MAP задаёт точные пути и доступ.
Несколько последовательных исправлений внутри этой цепи разрешены с RED→GREEN каждого,
без возврата за пользовательским разрешением. Исходный false сохраняется в evidence.

## Законы сохранения
Inventory035 и native cast/drain036 защищены. Нельзя сбрасывать debt/epoch ради новой сцены,
объявлять self-heal или autoplay=true useful progress, подменять reward receipt, заранее
отбрасывать слабого участника. Нельзя открывать roots при DRAINING либо очищать identity
до FINALIZED. Никаких SQL, I/O и ожиданий под native locks или в FINALIZED hook.

Если факты доказывают, что неизменённый evaluator требует невозможного/некорректного
для штатного допустимого поведения, сохранить исходный FAIL и точный counterexample,
не подгонять продукт под ошибочный тест. Статус BLOCKED_CONTRACT; это новая граница
координатора, а не разрешение редактировать evaluator. Завершить автономно без ожидания.
