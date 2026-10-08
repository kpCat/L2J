# Проверка самого пакета

Проверены только task package и его offline helper-код. Сервер Mobius здесь не запускался,
реальная MariaDB/Windows runtime не использовалась. Не выдавать это за product GREEN.

- Helper unittest:25 tests PASS.
- mailbox helper не dispatch/replay и не изменяет входные bytes (проверено test).
- Аргументы/UUID, result identity, namespace/DTD/size, claimed/no-result отрицательные случаи.
- Budget учитывает elapsed со START, absolute525s, cleanup reserve и command cap.
- Все task-файлы strict UTF-8, zip paths только внутри папки TASK028.
- В zip нет JAR/DB/геодаты/ключей/production replacements и __pycache__.

Команда: `python -m unittest discover -s proposals -p test_tools028.py -v`.
