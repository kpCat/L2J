# Goal016 — PARTIAL016_UNSEALED

**G3 и настоящий TRAIN не достигнуты.** Реализован накопительный016 journal/seal/director path в exact7, но первая живая headless-подготовка завершилась fail-closed. FULL016 не заявляется. Final manual gate **REQUIRED**.

Первая сессия создала две native pre-CUT Solo руки с совпадающим physical SHA и разными свежими объектами. Новая проверка ошибочно сравнила NPC spawnGeneration как глобальный epoch: у двух разных новых NPC значение1. Отказ `TRAIN016_PRECUT_MATCH_OR_FRESHNESS` оставил0подтверждённых пар,2pending candidates, sequence1 и5atomic transitions. Две руки не являются действующим разрешением на бой, не являются TRAIN и не получают credit.

Причина исправлена в опубликованном исходнике по штатной семантике `TacticalSnapshotV3`; точный отказ воспроизведён отдельно для A/B helper и journal, затем GREEN. Повторный objectId и actor owner epoch по-прежнему отвергаются. Исправленный вариант проверен **offline**, в frozen TEST не установлен: замена build/source pins потребовала бы отдельно согласованного нового идентификатора подготовки. Существующие59файлов не перепечатаны, внешняя приватная копия сверена по SHA. Сессии2/3 не выдавались.

|Gate|Фактический результат|
|---|---|
|G0|PASS: Source актуализирован, TEST overlay защищён byte backups; cold пять SELECT183строки; TEST2/DRAFT14/Adam903/223/apps48/grad16/pointer12 сохранены|
|G1|PASS offline и config:51; три distinct cells/≥2classes/Solo+Party завершают выбор; legacy режимы сохранены|
|G2|PASS focused contracts: atomic pair+selection, cold replay, interrupted scan, partial seal, tamper/reparse, reservation/conditional FIT; реальные completed pairs0|
|G3|FAIL:0из3подтверждённых пар, seal отсутствует|
|G4|NOT_RUN: GS запрещён безG3; no-EVAL/director/budget recovery проверены DB-free|
|G5|NOT_RUN/FAIL:0настоящих TRAIN CUT, paid effects и терминалов|
|G6|PRESERVED / NO_FIT_NO_LEGAL_DATA: FIT0, новые gradient/terminal credits0; холодная lineage неизменна|
|G7|PASS с ограничением измерений: headless1/3,72,430s issued /172,590s весь Prepare; GS0/3,0native-min; targeted checks PASS, CPU/RAM только последний status sample, не peak|
|G8|Публикация и независимый GitHub readback отражены в READBACK.json; manual REQUIRED|

Source прошёл два проверенных fast-forward до `591c5261ed5`; параллельные Studio commits сохранены. MAIN не использовался для реализации; его чужой dirt не менялся. Exact7 code allowlist, отдельные task outputs/report/новый roadmap section. Git-команды использовались с прямого разрешения владельца; точные формы и цели — [COMMANDS.md](COMMANDS.md).

Изменения: явный opt-in016 и общие Java/PowerShell config checks51; immutable hash-chain journal с pair commit до возврата следующему кандидату; восстановление последней подтверждённой selection boundary; marker-last seal с closure validation; свежий native recheck и exactly-once reservation; отдельные cumulative budgets и один conditional FIT, завершение без EVAL/promotion. Незавершённый read-only scan повторяется от последней committed boundary; его временные счётчики не выдаются за committed history, cost telemetry остаётся сохранённой. Это ограничение транзакционной границы, а не доказательство полного live STOP/RESUME016.

Независимый review:0Critical/3Important/1Minor; все четыре исправлены с RED/GREEN, foreign-root finding повышен доImportant по task contract. Второй review не проводился. Поздний реальный epoch-дефект выявил пробел synthetic fixtures; он исправлен focused regression, но **не получил живого подтверждения**. Нет утверждения, что новая ветка прошла весь live recovery маршрут.

**TRAINED_MODEL=NO · GAME_ADVANTAGE=NOT_EVALUATED · PRODUCTION=OFF.** Новая EVAL/probe, promotion и следующий Goal не выполнялись. Goal015 остаётся принятым владельцем PARTIAL; его история не изменена. Shots20+/Blessed/покупки, память трёх смертей/адаптация, роли/баффы, PvP/raid/team и server bootstrap V3 остаются отдельными незакрытыми требованиями. Задача№10/13, осталось3до обязательного пересмотра бюджета; PARTIAL не сбрасывает счётчик.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Факты и SHA — [EVIDENCE.json](EVIDENCE.json), проверки — [NEGATIVE_TESTS.md](NEGATIVE_TESTS.md), стоимость — [PROFILING.md](PROFILING.md). Продолжение требует отдельного согласования frozen generation/перехода, описанного в [IMPLEMENTATION_PLAN016.md](IMPLEMENTATION_PLAN016.md); repin/reset автоматически не выполняется.
