# Что вернуть пользователю

Обновлённый source/review ZIP, созданный `scripts/New-ReviewBundle.ps1`. Не ограничиваться текстовым обещанием «готово». Проверить содержимое ZIP и отсутствие workspace/secrets/binaries.

Отчёт: `reports/PSS-001-final.md`:
- Implementation GREEN/BLOCKED/FAILED; independent review PENDING.
- Initial Git root/HEAD/branch или NO_GIT_BASELINE; baseline manifest verification и сохранённый user dirt.
- Список изменённых файлов и конкретных исправлений.
- Точные команды, exit codes, фактическое число C# PASS/FAIL, Designer/static/runtime/LM статусы по отдельности.
- Actual source counts/fingerprint и equality source-before/source-after. Не заявлять runtime Java parity.
- Примеры нескольких новых кандидатов без персональных данных; решение по дублям/сохранённым замечаниям/экспорту.
- Проверка unsafe paths/изменённого approval/source drift/unknown JSON и ошибочного XML-export.
- Commit SHA, parent, branch; обычный push result только для собственного origin приложения. Нет origin — PUSH_NOT_CONFIGURED.
- Абсолютный путь к review ZIP; состав и SHA-256 ZIP; остающиеся ограничения.
- Рекомендуемый следующий самостоятельный PSS-002: Java oracle/validator в отдельной копии + точный controlled export; без автоматической записи в рабочий сервер.

Git: обычный commit с subject `feat(pss): verify and harden read-only semantic studio` после проверок. Не amend/rebase/force/reset/clean/stash. Stage только точные task-owned paths, не git add . . При BLOCKED сохранить безопасную реализацию/тесты/отчёт, не коммитить сломанный код как GREEN.
