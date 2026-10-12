# Вспомогательные файлы039

`tools/commit_chain039.py` — reference read-only проверка структуры ОДНОГО SQL-компонента
между двумя версиями. К БД не подключается. JSON содержит origin, latest, commits.
Identity: database/profileId/objectId/component. У snapshot есть version/digest;
у commit — beforeVersion/beforeDigest/afterVersion/afterDigest, status=COMMITTED,
operationId (уникальная реальная write-фаза), producer и evidenceId.

```powershell
python "$task/tools/test_commit_chain039.py"
python "$task/tools/commit_chain039.py" $normalizedInterval --output $newOutput
```

В команде должны быть определены реальные проверенные пути, не пустые placeholders.
Нормализация raw и проверка SHA выполняются отдельно. Поле fullSaveProven остаётся false
даже при LINKED: пример не проверяет actual native fields, payload contents или истинность
текстовых producer labels. Один exit0 этого инструмента не закрывает G3.
Использовать маленькую функцию как образец отрицательной проверки, не строить новую инфраструктуру.

В контейнере координатора прошли23 unit checks примера. GameServer/JDK25/Windows/MariaDB
здесь не выполнялись. Первоначальный импорт до создания модуля дал ModuleNotFoundError;
это не причинный RED продукта. Настоящий native RED требуется в работе Codex.

`tools/verify_packet039.py` проверяет MANIFEST и имена файлов, ничего не правит.
До импорта и source write сверить полный пакет. Новые runtime evidence должны лежать
в private directory, не попадать в MANIFEST исходного неизменяемого пакета.
