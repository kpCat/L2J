# Исполняемая таблица, не готовая серверная интеграция

`CheckpointPolicy025` выбирает следующий тип действия из проверенных facts. Он не
открывает Player и не записывает БД. Интеграция обязана получать факты из настоящего
checkpoint/transaction boundary, а не из диагностических counters.

Проверка (в отдельный output, не в production):
```
javac -encoding UTF-8 -d out CheckpointPolicy025.java CheckpointPolicy025Test.java
java -cp out CheckpointPolicy025Test
```
Java21+; target server JDK25. При conflict данных conservative action не скрывает ошибку.
`recovery-flow.java-snippet` — последовательность existing API; она намеренно не является
компилируемым целым методом, потому что должна сохранять текущие ownership wrappers.

Пакетные self-checks не считаются R01–R28 и не могут дать ENGINEERING/SERVER PASS.
