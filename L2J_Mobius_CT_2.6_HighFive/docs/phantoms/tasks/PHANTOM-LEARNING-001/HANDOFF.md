# Передача контекста для завершения PHANTOM-LEARNING-001

LEARNING-001 имеет статус **PARTIAL**. Базовый SHA `c5b81f89ce02030eff8441395b0411137a110b2f`, реализация `518318c89c6937c80818b4035df6b0fc9523f9ce`. Ветка [wip/phantom-learning](https://github.com/kpCat/L2J/tree/wip/phantom-learning). Локальный clone `D:/Codex/worktrees/phantom-learning-001-source`. Путь пользователя `D:/Codex/repos/L2J-phantom-world` оставался read-only.

## Состояние native-вертикали

1. Открыть [отчёт](../../reports/PHANTOM-LEARNING-001.md), [архитектуру](ARCHITECTURE.md) и [ограничения](SAFETY.md). Сверить HEAD learning-ветки и обе защищённые ветки.
2. Готовый контур находится в `D:/Codex/worktrees/phantom-learning-001-source/.phantom-local/learning001/native`. Там есть Geo203, собственная остановленная MariaDB на 33317 со штатной схемой, шесть актёров, `rosters.tsv`, `capacity.tsv` и расходующий первый bootstrap `cut.properties`. В родительской папке находятся `native-shadow.log` и `native-shadow-readback.log`. Приватный каталог содержит credentials и хранится локально.
3. Штатный `GameServer.java:502` игнорирует `GameserverHostname` при открытии сокета. Подтверждены IPv4 и IPv6 wildcard у собственного PID. Launcher завершает `shadow` до запуска SQL или JVM. Для дальнейшей работы сначала нужна подтверждённая сетевая изоляция самого процесса или отдельно согласованный bind fix. Проверять реальный endpoint в сетевом контуре до сбора.
4. Подтверждённый Solo-сеанс `profile=1, generation=1, target=268452014, spawnGeneration=1` оставил 3 строки по обоим навыкам, один actor group и 0 моделей. Runner пропускал `consumeTerminal`, поэтому штатное удержание завершённой сессии блокировало следующее поколение. Исправленный runner использует `consumeTerminal` и проверяет `hasClaim`. Лог `LEARNING001_SOLO_TERMINAL` покажет точный результат при следующем разрешённом запуске. Повторное открытие проверено в DB-free `combat-core` 72/72. Native Solo вызывался через CombatService из runner. Штатный видимый AutoPlay идёт своим маршрутом.
5. После решения обоих вопросов нужны полные train/tuning/audit и проверка боевого выбора после чистого STOP и холодной загрузки ACCEPTED версии. С текущими тремя строками для такого цикла нужны ещё два GameServer запуска, а в текущем бюджете остался один. Отдельно открыты native F7/F8, обычный Heal, PvP/PK, полная стоимость решения OFF/ON и SQL conservation по боевым итогам. PHANTOM-LEARNING-002 ждёт закрытия 001.

## Команды

Из корня модуля выполнить дешёвый gate перед JAR.

```powershell
ant -f build.xml phantom-learning001-test
ant -f build.xml jar
```

Для штатных suite, требующих игровые XML, рабочий каталог `dist/game`. Имена и seeds указаны в [отчёте](../../reports/PHANTOM-LEARNING-001.md). Пример offline CLI из модуля после provision и G0. Секреты передаются отдельно через переменные процесса `PHANTOM_LEARNING_JDBC` и `PHANTOM_LEARNING_PASSWORD`. Допускается только loopback, учётная запись `learning001` и выделенная схема с префиксом `phantom_learning001_`. Версия выбирается оператором.

```powershell
java '-Dphantom.learning.train=true' -cp 'dist/libs/*' org.l2jmobius.gameserver.phantoms.learning.LearningStore train dist/game 4096 183
java '-Dphantom.learning.train=true' -cp 'dist/libs/*' org.l2jmobius.gameserver.phantoms.learning.LearningStore promote dist/game $version
java '-Dphantom.learning.train=true' -cp 'dist/libs/*' org.l2jmobius.gameserver.phantoms.learning.LearningStore read dist/game $version
```

Приёмка LEARNING-001 израсходовала 1/2 clean JAR и 1/2 GameServer boot. Для проверки сохранённых native строк использовать `powershell -NoProfile -File tools/phantom-runtime/Invoke-Learning001Fixture.ps1 -Action read-native` из модуля. Команда открывает и штатно останавливает только собственную MariaDB. Синтетический checkpoint 2 из прежней SQL-only схемы отклоняется при promote.

## Git и границы

Проверка исходника и публикации использует `git ls-remote https://github.com/kpCat/L2J.git refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning`. Для локального исходника выполнены `git rev-parse --show-toplevel`, `git rev-parse --verify` для исходного ref и commit, `git worktree list`, `git remote -v` и `git for-each-ref`. В независимом clone использованы `git clone --no-checkout --single-branch --depth 1 --branch wip/coordination-130-preserved -c core.longpaths=true`, `git switch --no-track --create wip/phantom-learning c5b81f89ce02030eff8441395b0411137a110b2f`, `git symbolic-ref --short HEAD`, `git rev-parse --verify HEAD`, `git status --short --branch`, `git for-each-ref`, `git ls-tree`, `git ls-files --eol` и `git diff` для проверки scope и CRLF.

Staging сделан через `git add -- <точный allowlist>`, проверен `git diff --cached --name-only`, `git diff --cached --stat` и `git -c core.whitespace=cr-at-eol diff --cached --check`. `git commit` и `git push origin HEAD:refs/heads/wip/phantom-learning` разрешены владельцем для этой ветки. Артефакты сборки, приватная SQL-папка, модель и логи остались локальными.
