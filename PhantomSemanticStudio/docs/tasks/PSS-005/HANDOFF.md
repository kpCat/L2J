# PSS-005 — что вернуть после работы

По завершении один отчёт `reports/PSS-005-final.md` с: expected/actual base, scope файлы, specific APIs, fake/real test counts, Release build exit/0 warnings, offline scout statistics и честная граница coverage, sample synthetic model evidence с IDs без raw personal text, HTTP errors, сохранение legacy approvals, session/source integrity, L2J read-only, Designer UI/DPI (каждый статус отдельно), live Gemma status, Java NOT_RUN.

`reports/PSS-005-ui.md`: фактически открытый Designer, UI routes, 100/150% DPI (если реально), screenshots только если безопасно; если недоступны — NOT_TESTED, без фиктивной приёмки.

`reports/PSS-005-owned-files.txt`: все task-owned изменённые/созданные tracked файлы, без пользовательских изменений. Narrow exact verification logs без credentials/raw prompts. `git diff --check`, staged allowlist, exact parent/branch, нормальный commit, `git push origin HEAD:refs/heads/main`, `git ls-remote` proof. При blocked сохранять безопасный код/тесты/report и пушить нормальным способом без force, если remote base всё ещё совпадает.

**Никаких review ZIP/source ZIP/архивов кода**. Ассистент проверяет публикацию только по GitHub. Никаких staged XML в Git и никакого переноса на L2J. `STOP` после PSS-005, никакого PSS-006.
