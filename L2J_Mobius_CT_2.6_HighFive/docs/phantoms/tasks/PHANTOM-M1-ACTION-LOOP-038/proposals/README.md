# Назначение code proposal

`ActionContinuation038.java` — минимальный чистый образец выбора продолжения из typed
native facts. Здесь нет обращения к игре, pathfinder, scheduler, HP/MP, persistence или
side effects. Ключ включает epoch, goal revision и generation цели; ответы не grant права.
`ActionContinuation038Checks.java` проверяет только этот контракт, не исходный дефект037.

Запуск из каталога proposals в любой временный output (не bin сервера):
```
javac -d <temp-dir> ActionContinuation038.java ActionContinuation038Checks.java
java -cp <temp-dir> ActionContinuation038Checks
```
В production использовать existing types, а не две параллельные модели identity.
Допускается встроить switch как небольшой private policy в Controller; выделять ради него
отдельную глобальную систему не нужно. Caller повторно проверяет native lease/currentness.
Actual cause RED и GameServer proof требуют SCENARIOS.md независимо от этих unit checks.
