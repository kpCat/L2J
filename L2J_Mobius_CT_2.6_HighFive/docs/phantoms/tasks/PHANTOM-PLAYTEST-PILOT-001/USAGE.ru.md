# LocalPlay pilot: запуск и отзыв доступа

Пилот работает только в приватном LocalPlay с `EnableLocalPlayPilot=True`. Исходная поставка сервера имеет `False`. Управлять можно одним отдельно выбранным персонажем с настоящим подключённым клиентом; для этого запуска выбран точный ник `TestAdmin`. Основного персонажа, GM-права, PersonalCharacterQoL allowlist и пароль оператору давать не нужно.

1. Оператор после проверки owned GameServer выполняет `Prepare-LocalPlayPilot.ps1 -ExpectedName TestAdmin`. Скрипт создаёт private mailbox с ограниченным Windows ACL и выдаёт одноразовую строку `.playtest arm <8 символов>` сроком не более 10 минут. Длинный внутренний nonce в игровом клиенте не вводится; код не переносится в git/отчёты. Короткий код подтверждён на работающем сервере после clean cutover.
2. Войдите отдельным `TestAdmin` уровня 1, введите полученную строку в игровом чате и оставьте клиент открытым. Оператор проверяет `Get-LocalPlayPilot.ps1` и запускает `Run-LocalPlayPilotScenario.ps1` один раз; сценарий сам выполняет оба сегмента и пишет private `pilot-*-REPORT.md` и `pilot-*-RESULTS.tsv`.
3. В клиенте `.playtest status` показывает срок и состояние. `.playtest stop` прекращает текущий run, но оставляет согласие до срока. `.playtest off` полностью отзывает согласие. Локальный `Stop-LocalPlayPilot.ps1` останавливает run при потере runner. При disconnect/restart/истечении срока привязка становится недействительной.

`STATUS`, snapshot, move, sit/stand, teleport, chat, party и разрешённый target используют native серверные пути. `ACCEPTED` означает вызов native path; runner отдельно проверяет наблюдаемую позу/позицию и отдельно записывает ответ бота. Если клиент недоступен, новый вход и новый arm нужны явно.
