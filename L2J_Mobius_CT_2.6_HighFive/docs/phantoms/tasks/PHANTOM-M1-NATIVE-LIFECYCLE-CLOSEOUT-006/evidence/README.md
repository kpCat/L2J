# Evidence и границы

TXT/XML — копии реальных guarded native TEST reports; *-execution.txt — последние строки соответствующего Ant log. Все runs выполнены в отдельном worktree против `l2jmobiush5_phantom_test`, seed 15001501. XML содержит actual assertions; PRIVATE config/DB manifest сюда не включены.

Название файла GREEN не означает, что весь run прошёл. green-incident-mage = 17/18, green-hit-cast-owned = 2/3. Отдельный green-stock-mage = 1/1 после исправления TEST bootstrap. Нельзя суммировать разные runs в новый финальный suite verdict.

red-ownership-lf: P04/P05 обоих managers — валидные RED. Исходная P01 AutoPlay fixture не имела spawn observer, её failure не засчитывается. Исправленная fixture имеет GREEN, но её baseline RED не повторён. Старый RED-ownership.txt — ранняя копия этого же run, с тем же ограничением.

red-errors-and-context: E03 SQL/finalizer failures — RED; native context fixture failures из этого run не заменяют quiescence RED. red-terminal-travel-assets: geodata inventory исправлен до запуска, Native FarmTravel и mirrored callback проверены; raw assertion wording о production composition завышено. Production PhantomSystem composition ещё REQUIRED.

red-delayed-native-quest — критический native producer RED на последнем candidate. Fixture использует настоящий stock kill/event + штатный Quest.giveItems; полный quest-state replay не выполнялся. CANDIDATE.patch соответствует сохранённому uncommitted candidate; для deploy не принят.

Patch экспортирован с `--unified=0`: полные изменённые hunks и новый DTO сохранены,
context строк нет. При approved восстановлении такого patch требуется поддержка
zero-context hunks. Здесь apply не выполнялся. Execution tails очищены только от
trailing whitespace; actual TXT/XML assertions не редактировались. Publication text
сохранён как UTF-8/LF для совпадения manifest с Git blob; только line endings
нормализованы. INPUT_PACKAGE_MANIFEST содержит historical original package hashes.
Первоначальный
staged diff-check поймал whitespace у patch context и одного execution tail, exit2.
Экспорт U0 убрал context; остались четыре удалённые baseline строки с tab-only
whitespace. Они сохранены буквально для точного diff. Bounded exception только для
этого audit artifact: остальные docs и текущие candidate additions проверяются
обычным diff-check; patch проверяется отдельно с исключением blank-at-eol.

proofs/ — унаследованный input package control-flow model и его старые outputs. В этой задаче proofs не исполнялись; ни один native gate ими не подтверждён.
