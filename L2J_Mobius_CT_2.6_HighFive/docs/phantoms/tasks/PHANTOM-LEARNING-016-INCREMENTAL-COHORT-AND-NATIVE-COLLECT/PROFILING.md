# Стоимость016

|Измерение|Факт|
|---|---|
|Readiness sessions|1из3; result ERROR, без повторного расхода после детерминированного отказа|
|Issued→SQL/worker/drain complete|72 430ms, лимит600 000ms; SQL startup и cleanup включены|
|Весь Prepare со штатной TEST сборкой|172 590ms по creation/last-write stdout; консервативная wall оценка, не точный process stopwatch|
|Headless Java held exit|49 700ms, exit2|
|TEST compile-tests / jar|22:31:09→22:31:40 /22:31:40→22:32:31UTC; build stamp22:32:36, readiness issued22:32:44UTC|
|GameServer / native minutes|0/3;0/75|
|FIT / EVAL / promotion|0/1;0;0|
|Последний director status CPU/RAM|26,781CPU-s /138 964 992bytes; это last-reported sample, не peak и не отдельная атрибуция Java|
|Сохранённый cohort|59файлов,553 954bytes; внешняя private byte-copy/SHA проверена|

Наблюдаемые phase sums: SOURCE_DISCOVERY1,0862876s; ROSTER_REPRODUCTION0,3040467s/2records; MATERIALIZATION0,0992948s/2; NATIVE_ADMISSION0,0170314s/2; DRAIN0,0105613s/2. Они не покрывают весь startup и не заменяют wall/held-exit. Максимумы CPU/RAM не были захвачены; не утверждается наличие полного peak profiling. SQL Export до/после — отдельные cold preservation операции, не readiness и не GS; их логи/SHA включены в evidence.

Source Ant targets выполнялись последовательно. До live использованы семь targeted targets016/015/autonomous/008/007/jar/content; после выявления epoch-дефекта повторены016, legacy autonomous и jar. DB-free PS checks шли частично параллельно с source Ant, что раскрыто как отклонение от полной последовательности инструментов; Ant между собой не параллелился. Было пять существующих javac warnings, warning-free не заявляется.

Остаток бюджета автоматически не расширялся и новые окна не выдавались. Для возможного продолжения следует сохранить как issued72 430ms, так и консервативный общий Prepare172 590ms; даже с двумя будущими600s окнами исходные30мин не превышаются. Такой расчёт не является разрешением запускать новую generation.
