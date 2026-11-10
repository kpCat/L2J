# Передача контекста для PHANTOM-LEARNING-002

LEARNING-001 имеет статус **PARTIAL**. Базовый SHA `c5b81f89ce02030eff8441395b0411137a110b2f`, реализация `518318c89c6937c80818b4035df6b0fc9523f9ce`. Ветка [wip/phantom-learning](https://github.com/kpCat/L2J/tree/wip/phantom-learning). Локальный clone `D:/Codex/worktrees/phantom-learning-001-source`. Путь пользователя `D:/Codex/repos/L2J-phantom-world` оставался read-only.

## Что проверить перед новым этапом

1. Открыть [отчёт](../../reports/PHANTOM-LEARNING-001.md), [архитектуру](ARCHITECTURE.md), [ограничения](SAFETY.md) и [дорожную карту](ROADMAP.md). Сверить HEAD ветки и обе защищённые ветки. Обновление TASK130 переносить отдельно после ревью.
2. Для минимальной native вертикали подготовить отдельную схему с полным штатным installer и двумя learning таблицами. Имеющийся SQL-only datadir под `.phantom-local/learning001/sql-first-party` содержит лишь доказательство сохранения DRAFT и годится для разбора readback. Его PID 47728 и порт 33306 остановлены. Стандартный headless guard использует 3308 и фиксированную схему, прежние lane привязаны к своим manifest. Для новой вертикали нужен отдельный attested fixture без изменения этих guards и окружений.
3. На собственном runtime с geodata получить реальные исходы хотя бы для двух разрешённых навыков у нескольких профилей в Solo и автономной Party. Сначала SHADOW с `LearningEvidenceEnabled=True`, затем отдельная offline команда. При пустом NATIVE проверить число готовых кандидатов, фактический skill start в dispatch, наличие ровно одной цели, совпадение spawn и epoch, сторонние HP writes и receipt cancellation.
4. После достаточного сбора выполнить offline audit по незнакомым контекстам с отдельными актёрами и сравнить отдачу выбора против штатной. Принять точную версию только по результату проверки, включить ON на безопасной границе и подтвердить тот же выбор на новом JVM после MariaDB readback. Отдельно подтвердить F7/F8, SQL conservation, Geo203, protected207 и полную остановку.

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

Приёмка LEARNING-001 израсходовала 1/2 clean JAR и 0/2 GameServer boot. Набор train/tuning/audit, SHA модели и schema fingerprint зафиксированы в [отчёте](../../reports/PHANTOM-LEARNING-001.md). Синтетический checkpoint 2 закономерно отклоняется при promote. Редкие участки без точной квитанции пропускаются. Тест выбора использует настоящий CombatService с тестовым backend.

## Git и границы

Проверка исходника и публикации использует `git ls-remote https://github.com/kpCat/L2J.git refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning`. Для локального исходника выполнены `git rev-parse --show-toplevel`, `git rev-parse --verify` для исходного ref и commit, `git worktree list`, `git remote -v` и `git for-each-ref`. В независимом clone использованы `git clone --no-checkout --single-branch --depth 1 --branch wip/coordination-130-preserved -c core.longpaths=true`, `git switch --no-track --create wip/phantom-learning c5b81f89ce02030eff8441395b0411137a110b2f`, `git symbolic-ref --short HEAD`, `git rev-parse --verify HEAD`, `git status --short --branch`, `git for-each-ref`, `git ls-tree`, `git ls-files --eol` и `git diff` для проверки scope и CRLF.

Staging сделан через `git add -- <точный allowlist>`, проверен `git diff --cached --name-only`, `git diff --cached --stat` и `git -c core.whitespace=cr-at-eol diff --cached --check`. `git commit` и `git push origin HEAD:refs/heads/wip/phantom-learning` разрешены владельцем для этой ветки. Артефакты сборки, приватная SQL-папка, модель и логи остались локальными. Следующий task начинается отдельно после проверки указанных открытых gates.
