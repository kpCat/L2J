# Независимое ревью для Codex после реализации

- Reviewer read-only читает exact diff относительно required base, не доверяет отчёту.
- Проверяет сохранение всех original roles, locale/style/aliases, отсутствие фиктивных identity/memory/interest/value, truthfulness status.
- Отдельно проверяет реальную причину ложного отказа: первый templated `greet.01`/`mood.share.01` больше не заслоняет safe `greet.02`/`mood.share.02`.
- Убедиться, что `NO_PACK_MATCH`/`TEMPLATE_CONTEXT_UNAVAILABLE`/`FUNCTIONAL_OR_MEMORY_UNSUPPORTED` не смешаны и mismatch raw/rendered XML не вернулся.
- Проверяет negative mutation и поддержание транзакционного recent queue при cancel/stale.
- Сверяет owned-files, source/user `.sln` preservation, git scope, отсутствие private/raw content.
- Отдельно Declined-to-judge: настоящий Java runtime parity, live LM, physical UI/VS Designer. Не присваивать им PASS из Core.
