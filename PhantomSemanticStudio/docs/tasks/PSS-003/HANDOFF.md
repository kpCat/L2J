# Финальный handoff для PSS-003

Codex сообщает компактно на русском:

1. `GREEN` / `BLOCKED` / `FAILED` для реализации; отдельно `STAGED_UNVALIDATED`/`PASS_JAVA_STAGED`/`BLOCKED_JAVA`/`FAILED_JAVA`.
2. Точный source SHA Studio initial/feature/final, ветка, origin URL, статус remote equality. Required base `60d48348687467c3724ae2bb010ef62987c58d4e`.
3. Изменённые файлы и реальное функциональное поведение. Ссылка на `reports/PSS-003-final.md`, `reports/PSS-003-owned-files.txt` в **опубликованной** `main`.
4. Фокусные/final C# тесты, компилятор warnings/errors, static Designer, actual Java oracle status и команда, UI/DPI и live LM (если не запускались — NOT_TESTED/BLOCKED из реальных данных).
5. SHA before/after всех исходных humanized-файлов, отсутствие L2J writes/DB/Ant/JAR в source. Отдельный test-only stage folder — локальный игнорируемый workspace, **не прикладывать в Git или ZIP**.
6. Наличие ограничений: gender != ANY, любой placeholder, adult/profanity ambiguity, unsupported/gameplay branches запрещены к XML staging; semantic duplicate не доказан; Java validation не доказывает runtime/prod safety.
7. Обычный `git push origin HEAD:refs/heads/main`, проверенный удалённый commit SHA и чистота рабочей копии (за исключением известных user dirt).

**Категорически не создавать review ZIP, source review archives, `New-ReviewBundle` outputs или архивы с пользовательскими/сгенерированными XML.** Ассистент сам проверяет diff/коммит/тестовые журналы по GitHub.

После завершения STOP; не начинать PSS-004.
