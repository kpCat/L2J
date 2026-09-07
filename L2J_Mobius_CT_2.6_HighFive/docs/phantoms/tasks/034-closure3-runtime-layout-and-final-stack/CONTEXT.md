# Goal034 closure 3 context

Remote required parent: `82f6c562bc14bd18eeffead03172d71124fbedee`.

Последний real-stack доказал: LoginServer READY; GameServer JVM реально стартует; DB/ports/process cleanup безопасен; verify/jar green.

GameServer упал, потому что sandbox `game/` содержал config/root cfg, но не `data/`.
Это ломает existing runtime contracts:
1. `IXmlReader` читает cwd-relative `./data/...`;
2. `ScriptExecutor` компилирует scripts с hardcoded `-sourcepath data/scripts`.

Правильный sandbox должен зеркалировать обычный `dist/game`: полный `data` snapshot + sandbox config; `DatapackRoot=.` и `ScriptRoot=./data/scripts`.
Не лечить missing files по одному.
