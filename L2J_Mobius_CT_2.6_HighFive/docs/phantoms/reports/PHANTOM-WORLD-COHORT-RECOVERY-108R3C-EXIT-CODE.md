# TASK108R3C — узкое исправление ExitCode

Статус: **PROCESS_EXIT_FIX=PASS**, native acceptance не изменён. Старый A108R3C ExitCode=UNKNOWN, clean/B отсутствуют. GameServer/новые A/B/SQL/force/production/config/economy changes0.

- Read-first: текущий wrapper (helpers, launch/wait, identity/deadline/Accept gates), ближайший локальный паттерн New-Text/retained receipts и [PowerShell issue5421](https://github.com/PowerShell/PowerShell/issues/5421). Повторного World/market/OwnerConfig аудита нет.
- Baseline local/live feature/phantom-world: `e039f1c28d0a16c899cf7a56a1b59e72bb574987`; tracked dirt0 перед изменениями.
- Windows PowerShell **5.1.19041.6456**. RED: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/phantom-runtime/Test-Task108R3Process.ps1 -Mode Legacy`: exit1. Настоящие stub0/stub7 с PassThru/WindowStyle Hidden/обоими redirects/WaitForExit(1000) дали null/null. stdout/stderr проверены.
- GREEN: тот же runner `-Mode Fixed`, exit0. После раннего Handle наблюдены ровно0/7; только0 принимается как нулевой exit. Null/string0/DBNull/ошибка Handle у unattached Process/null и zero Handle отвергаются. Undefined status не приводится к int0. Final JSON completed=true/guardNegatives=PASS/cleanAbsent=true; stdout/stderr stubs exact.
- Причина воспроизведена: Process, возвращённый Start-Process с redirects, не обеспечивал доступный ExitCode без раннего кеширования Handle. Раннее чтение Handle сохраняет его в том же управляемом Process до завершения ожидания; объект остаётся жив до чтения ExitCode. Для старой завершившейся JVM это не восстанавливает утраченный код.
- Изменены ровно3 файла: `tools/phantom-runtime/Task108R3Run.ps1`, новый `tools/phantom-runtime/Test-Task108R3Process.ps1`, этот новый report. Нет нового runtime слоя или зависимостей.
- Handle читается сразу после Start-Process, до CIM и ожидания. При его ошибке сохраняется identity-bound retained receipt и fail-closed без retry/force. После WaitForExit требуется HasExited и фактический Int32 ExitCode; unknown пишет отдельный unknown receipt,7 пишет observed7 и отклоняется прежним nonzero guard.
- PID/SID/CreationUTC/CommandSHA/JAR/stage/installation проверки,12min deadline/WaitForExit(1000), retained receipts, World/market/postflight/independent AcceptA/B gates сохранены. Exit0 сам по себе clean не создаёт; clean остаётся только после независимого полного postflight PASS.
- Focused evidence: `%TEMP%/Task108R3Exit-red-20261029`, `Task108R3Exit-green-20261029`, `Task108R3Exit-green-final-20261029`; последний GREEN повторён только для сохранения результатов negative checks в JSON, не stress test. Все6 stub-процессов завершились самостоятельно; native0/SQL0.
- Старый sealed manifest SHA256 `d0c20df68ef58785e16e2811b181bc0bbc829a8e155e7545edf7e8df07f9fb98`; старый stage wrapper `b6ce21126112bd33760d538240c39c40a360286d63f89b682422cb97dcbd2e8a`; старый exit6.observed `71bdff327c978757564ae1388b0744941963878bfee42cd6a6f5fdb20ba45a78`. Они прочитаны без изменений; старого exit6.clean нет. Исторические reports/receipts не переписывались.
- Independent review P1=0/P2=0 и final text/scope checks фиксируются перед публикацией. Полные focused stdout/stderr/JSON сохраняются вне checkout.
- Mojibake-маркеры в изменённых файлах проверены отдельно: полный набор27, совпадений0.
- Escaped Cyrillic в изменённых файлах проверены отдельно: все6 regex patterns, совпадений0.
- Git разрешён текущим запросом: bounded branch/head/status/live и exact-path diff/check; только exact3 add/cached-check/name-only/commit и обычный `git push origin HEAD:feature/phantom-world`. SHA/push публикуются в final, без самоссылки report.

## Отдельно предлагаемый план — НЕ запускался

1. После отдельного разрешения проверить свежую owned102 authority/SQL/JVM/TCP/foreign0 и фактический ledger (ожидаемый96 не считать immutable без проверки). Создать новую физическую shadow, private index и отдельный authority namespace; необходимые namespace правки — отдельный разрешённый scope. Source OwnerConfig/Geo/Semantic сохраняются; новый JAR/classpath/config pin и seal.
2. Одна A: World16/16 stable30s,250 OPEN63/162/25, genuine EARNED1/outstanding0, две lease/SQL cuts, canonical shutdown, ранний held Handle и observed exit0, JVM0/SQL0/TCP0, append-only ledger/no funding replay и exact postexit checkpoint.
3. Только независимый полный A PASS разрешает новый clean и одну B на той же БД, JAR/stage/classpath; B повторяет natural STOP и фактический exit0. Любой World/nonzero/unknown/postflight failure — STOP, без B/retry/force. Старый A не повышается.
4. Независимый финальный review, отчёт, exact-file normal commit/push и STOP. TASK109 не запускать.
