# PSS-004 — что вернуть оператору/ассистенту

1. Одной строкой: `GREEN / BLOCKED / FAILED` для scope PSS-004; отдельно `Java`, `LM`, `Designer`, `UI/DPI` (фактические статусы, не наследовать PASS из PSS-003).
2. `reports/PSS-004-final.md` — real test counts/commands/exits, diff review, boundary/snapshot outcome, stage subset proof, списки изменённых файлов и ограничений.
3. `reports/PSS-004-ui.md` — модальное окно, поиск/selection across filters, выбранные полные IDs/text, Save/Discard/Cancel, Designer round-trip, 100%/150%; если не удалось — `NOT_TESTED`.
4. `reports/PSS-004-owned-files.txt` — точный inventory, а не приблизительное количество. `reports/PSS-004-plan.md` и тестовые логи.
5. **Только** обычный commit + non-force push `HEAD:refs/heads/main` собственного `kpCat/PhantomSemanticStudio`, exact parent/HEAD/local SHA/remote SHA, URL commit и clean status. При remote conflict — честный BLOCKED, не rebase/force.
6. Никаких review/source ZIP и никаких просьб прислать архив. Ссылку на commit и report достаточно — ассистент сам сверит GitHub.
7. **STOP после PSS-004**. Не начинать PSS-005, не расширять Java/schema, не модифицировать High Five. Semantic duplicates/гендерные runtime проблемы остаются известными задачами следующих этапов.
