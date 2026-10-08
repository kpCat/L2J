# Код пакета, не патч сервера

`LocalExecutionDecision026` — чистая таблица приоритетов решений из DESIGN F1/F2.
Факты обязан получать production adapter из актуального native состояния. Этот класс
не проверяет GeoEngine, не начисляет наград, не вызывает AutoPlay и не доказывает M1.
Его можно встроить в существующий класс вместо создания ещё одного слоя.

`classify_cohort026.py` читает опубликованные cohort-result.json и строит краткую
матрицу наблюдаемых состояний. Это не evaluator: reported_pass не пересчитывается,
состояния не объявляются root cause. Выход создаётся только новым файлом.

Проверка reference:
```
javac -encoding UTF-8 -d <temporary-output> LocalExecutionDecision026.java LocalExecutionDecision026Test.java
java -cp <temporary-output> LocalExecutionDecision026Test
python -m unittest discover -s proposals -p 'test_*.py' -v
```
Нельзя включать эту проверку в FARM_PASS. Native suites и full server обязательны.

HOLD/WAIT в reference означает отсутствие НОВОЙ команды, не отмену EARNED callback.
REQUEST_BOUNDED_RETURN — запрос существующему retention/lifecycle, не разрешение
исчезнуть на глазах REAL. Перед native действием adapter повторно проверяет exact
identity/epoch/goal и фактическую геометрию; scalar snapshot сам по себе не lease.
