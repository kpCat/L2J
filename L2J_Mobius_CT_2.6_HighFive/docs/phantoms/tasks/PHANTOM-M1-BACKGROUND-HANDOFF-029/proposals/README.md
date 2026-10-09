# Вспомогательный код029, не готовая серверная интеграция

`RewardKernel029.java` — исполняемая иллюстрация порядка vitality bonus→award→consume
по прочитанным native методам. Нет World/DB/Player. Input addExp/addSp уже прошёл base
native penalties/EXPSP-rate и SP-truncation. Коэффициент Nevit здесь предполагается1;
pet/party/premium/time-dependent policy не реализованы. Наличие arithmetic-флага
adventBlessing не является разрешением симулировать временное благословение.
Параметры тестов — контролируемые TEST rates, НЕ текущие настройки вашего сервера.
Считать input level после addExp для targetDelta, как в Attackable. Не копировать
kernel в native PlayerStat. Обязательный native oracle в SCENARIOS остаётся впереди.

`receipt_coverage029.py` — read-only проверка НОРМАЛИЗОВАННОГО потока native evidence.
Она не проверяет байты SQL или достоверность input сама. `SQL_VERIFIED` создаётся только
после существующего независимого strict validator, поле proof ссылается на его result.
Библиотека не записывает БД и не восстанавливает пропущенные receipts.

Пример запусков из proposals (compiled outputs за пределами публикуемого пакета):
```
javac -d <temporary-output> RewardKernel029.java RewardKernel029Test.java
java -cp <temporary-output> RewardKernel029Test
python -m unittest discover -s . -p test_receipt_coverage029.py -v
python receipt_coverage029.py <normalized-native-events.jsonl> <new-coverage-report.json>
```

Root/source tests для Mobius из этого не следуют. Codex должен интегрировать actual
production paths, codecs, atomic transactions и выполнить full-server proof.
