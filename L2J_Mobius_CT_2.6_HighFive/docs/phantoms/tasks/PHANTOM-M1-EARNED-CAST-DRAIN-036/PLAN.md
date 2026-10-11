# План реализации TASK036

> Для Codex: executing-plans, systematic-debugging, test-driven-development,
> verification-before-completion. Один исполнитель, без subagent-driven execution.

**Goal:** устранить доказанное нарушение завершения native earned cast и подтвердить live drain.
**Architecture:** штатные MagicUseTask/PlayerNativeWork/PhantomNativeWorkScope и existing lifecycle control.
**Tech stack:** High Five, JDK25, Ant, MariaDB127.0.0.1:3308, Windows, existing LocalPlay Synthetic.
**Spec:** DESIGN.md. Точные полномочия — SOURCE_MAP.tsv, GIT.md, RUNBOOK.md.

## Review focus
Старый cached sample; inline completion; два получателя и частичный start; новый root из finalizer;
aggregate failed stop и потеря sibling callback. Эти входы покрывают E01,E07,E11,E06,E09/E10.

### 1. Закрепить старт и текущее состояние (0–30мин)
- [ ] MANIFEST/base/remote/reusable worktree проверить, перенести только packet.
- [ ] Прочитать exact03427 и accepted035; раскрыть UNKNOWN, не объявлять current leak.
- [ ] Записать START_UTC и monotonic elapsed budget; выбрать исходную readonly DB и защищённые hashes.
- [ ] Reuse035 runner/observer, один собственный036t и первый036a clone. Не копировать весь старый архив.

### 2. Закрыть диагностический пробел и воспроизвести (до60–100мин)
- [ ] E01 RED на base: настоящий cast не содержит dispatch trace, EVENT содержит.
- [ ] Минимальный diagnostic patch по PATCH_GUIDE, focused GREEN/disabled diagnostics/zero-delay.
- [ ] Первый full GameServer, естественная materialization. Попытка проследить27 только если current
  active incarnation существует; иначе взять actual eligible caster той же цепочки и указать отличие.
- [ ] Краткий probe, current ticket timeline/пулы/3 thread dumps только при задержке; не повторять
  unchanged scene. До semantic patch записать classification A–H и причинный RED.

### 3. Реализовать связанный fix либо доказать текущий путь (до210мин)
- [ ] E02–E11 на реальной native fixture, фокус сначала конкретному отказу, не полная матрица.
- [ ] При source defect — минимальная production-правка только scoped methods, causal GREEN и
  соответствующие negatives. Допускается несколько связанных исправлений одной цепочки.
- [ ] При PASS на неизменённых semantics — не выдумывать RED; записать semanticFix=NONE,
  диагностический fix отдельно; пройти требуемые race/native tests и actual runtime.
- [ ] Проверить релевантность Q15; не игнорировать возможный earned duplicate/late writer.
- [ ] После двух одинаковых outcomes без новой причины — сменить probe; после трёх неудачных
  semantic решений одной границы — BLOCKED_ARCHITECTURE с описанием, не продолжать перепись.

### 4. Один frozen current candidate (до270мин)
- [ ] Diff/review/encoding; commit exact source+tests+task, обычный push; clean build от этого SHA.
- [ ] Провести E13 native gameplay→drain. Expected5 native cycles у заранее выбранного primary
  внутри фактического epoch; cooperative/EXP-only cases честно по штатной модели.
- [ ] Capture FILE export отдельно, PREPARED/FINALIZED native и остановленный SQL сопоставить;
  реальные items/EXP/SP, общая карта owners, world/autoSave/lease и все tickets после drain.
- [ ] При связанном новом cast blocker до freeze — исправление/targeted проверки разрешены.
  Нельзя объявить новый кандидат frozen без повторной затронутой проверки.

### 5. Принять и завершить (последние90мин)
- [ ] Required93/93 и lifecycle11/11 на итоговом Jar; inventory035 focused6 + релевантные SQL/codec
  guards, новые E-тесты. Не подменить11-case acceptance общим24-case native suite.
- [ ] Разделить historicalCause, currentCast, earnedConservation, globalDrain, gameplay/cohort,
  relatedQ15, productionFixNeeded. Никаких переносов PASS между Jar.
- [ ] Остановить own processes; архив с hashes; один RESULT/HANDOFF/MORNING-файл и индексы.
- [ ] Normal push результата, read-back SHA. TASK036 закончен; следующие задачи не запускать.

Первый текущий короткий runtime важнее долгого просмотра исторических файлов.
Не гонять93+11 после каждой строки. Если всё проходит за90мин — закончить, не добирать время.
