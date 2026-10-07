# Код в пакете
BoundaryPolicy024.java + Test — автономная исполняемая таблица разграничения операций,
сохранения factual XYZ с отдельной eligibility, и зависимости stop.
Это REFERENCE, а не реализация locks/tickets/native writes. Не копировать без интеграционных
RED. CONTINUE_EXACT_EARNED оставляет работу действующим reserve/start/current guards,
не даёт права reopen. geometryValid вычисляется сервером по topology/actual points.

Запуск (JDK21+; проект всё равно JDK25):
```text
javac -encoding UTF-8 -d <private-out> proposals/BoundaryPolicy024.java proposals/BoundaryPolicy024Test.java
java -cp <private-out> BoundaryPolicy024Test
python -m unittest discover -s tools -p test_verify_persistence.py
```
verify_persistence.py не подключается к БД, проверяет собранный JSON. Он не аутентифицирует
сборщик; rawsnapshot/rawSQLhash и nativebarrier обязательны. Пример структуры входа
в tools/test_verify_persistence.py (valid()), это тестовые числа/формат, не live evidence.
Время/EXP/item данные не синтезируются из counters. В репозиторий не включать class/pycache.
