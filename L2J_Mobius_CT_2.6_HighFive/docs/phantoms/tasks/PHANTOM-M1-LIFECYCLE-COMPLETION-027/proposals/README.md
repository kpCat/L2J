# Вспомогательный код
DrainDecision027 — executable reference decision table, не реализация Mobius shutdown.
Она фиксирует, что pending accepted work нельзя считать complete и что retry не меняет deadline.
Тесты этой таблицы не считаются native RED/GREEN. Интеграционный S01/S02 обязателен.
inspect_lifecycle027.py — read-only extraction of raw cohort and shutdown logs;
не заменяет evaluator026 и не повышает результат строки до PASS.

Локально:
`javac -d <private-temp-dir> DrainDecision027.java DrainDecision027Test.java`
`java -cp <private-temp-dir> DrainDecision027Test`
`python -m unittest discover -s proposals -p "test_*.py" -v`
Не коммитить generated .class/__pycache__.
