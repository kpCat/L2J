# Fixtures: границы достоверности

observed-approach.tsv — 23 исходные D6 строки из приложенного capture, без исправления исторических значений.
native-envelope-expectations.tsv — расчёт требований нового envelope на тех же координатах (все точки instance 0); это НЕ output новой Java-реализации.
capture-summary.json — арифметика и подсчёт фактических thread states. Дампы сняты позднее RED; global worker state ими не восстановлен.

Эти файлы не содержат pending component payload/inner historical cursor. Их нельзя выдумать из outer cursor. В PLAN предусмотрено одно ограниченное read-only чтение текущих компонентов и guarded TEST replay без нового участия владельца.
