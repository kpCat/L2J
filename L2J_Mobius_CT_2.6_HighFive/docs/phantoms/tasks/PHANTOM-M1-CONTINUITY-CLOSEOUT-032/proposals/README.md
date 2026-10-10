# Что здесь является кодом

VisibleFarmReadiness.java-snippet — code-shaped вариант раскрытия фактического predicate.
Он не исправляет UNKNOWN-причину43 и сам не grant. Вставлять только после parity RED/GREEN
и проверки интерфейса, сохранять текущий источник exceptions и прежние вызовы.

CURRENT_FAILURE_BOUNDARIES.java-snippet — реальные source excerpts, не компилируемый класс.

Read-Task032Budget.ps1 — standalone helper для контроля времени задачи. Не обязательная
часть runtime и не повод строить новую систему orchestration. При существующем учёте
достаточно перенести те же START/FREEZE/END в WORK_LOG. Helper не тестировался на Windows
координатором: проверить pwsh AST/короткий запуск в own-path до использования.

Нет «нового интеллектуального движка» и искусственных hundreds of helper assertions.
Все Java root/gameplay tests обязан выполнить исполнитель на фактическом HighFive.
