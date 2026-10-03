# Исполняемые модели — ограничение доказательности

M1BoundaryReproducer.java — самостоятельная модель control-flow/interleaving по
проанализированным фрагментам. Она НЕ импортирует L2J, не запускает server/DB и не доказывает
точный historical crash110/142/175. Не подменять actual TEST этими результатами.

Сохранён один код с двумя режимами: legacy (рассмотренный pattern) и --fixed (proposal).
Сначала написаны/выполнены negative cases для legacy; затем добавлены proposal branches.

```text
javac -Xlint:all M1BoundaryReproducer.java
java M1BoundaryReproducer            # EXPECT exit1, passed3/failed8
java M1BoundaryReproducer --fixed    # EXPECT exit0, passed11/failed0
```

Контроли: ordinary actor с NOOP допустим; обе native registrations healthy;
real attributed progress positive. NativeLife/Cohort/Terminal примеры нарочно упрощены:
весь production grading и mapping задаёт основной DESIGN, а не эти boolean модели.

Из этой среды фактически выполнено только это: javac и оба model runs.
JDK версия приложена. Windows PowerShell/JDK25/Ant/MariaDB/native server не исполнялись.
Class files не включены; модель можно скомпилировать в отдельном временном каталоге.
