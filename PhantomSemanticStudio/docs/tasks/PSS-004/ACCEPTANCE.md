# PSS-004 — критерии приёмки

## Must pass / must remain true

- Точный локальный/удалённый base `edc41128270c14578b9edf748a3c8bc0c6196623` проверен до кода. Отличие HEAD — остановиться, не перетирать изменения/историю.
- Actual Windows Release build: exit 0, compiler errors 0, warnings 0; ordinary C# tests > прежних 69, без регрессий; отдельный `--pss-004` GREEN и initial RED evidence.
- Ручной выбор subset: 25 APPROVED, выбираем 2, получаем ровно 2 в XML/receipt; 21, empty, stale, duplicate, rejected/unknown полностью блокируются. Модель выбора без файловой записи и без автоматического select-all.
- Шесть старых вкладок и новая модальная форма показаны стандартным WinForms Designer layout; старый и новый конструкторы `InitializeComponent` only. Static verifier PASS, отдельный VS Designer round-trip — только по реальному наблюдению.
- Подтверждение selected IDs/text и отдельная editorial safety attestation; обе default No. При отмене ноль finished proposals, approvals/session/source неизменны.
- `IsolatedPackStager` сохраняет PSS-003 fail-closed, Java не запускается из GUI. Результат `STAGED_UNVALIDATED / Java NOT_RUN` до отдельной operator проверки. Старый Java evidence PSS-003 не превращается в PSS-004 PASS.
- Source L2J: **строго READ_ONLY**; если actual import/copy smoke выполнялся, сверить SHA/bytes до/после; не заявлять source=65/65 PASS без реального read-run.
- Нет новых автоматических исправлений и семантической уверенности: `SEMANTIC_NOT_CHECKED`, `GENDER_RUNTIME`, мат/adult и gameplay/факты проверяются отдельно; не ослаблять `CandidateValidator`/approval.
- Недоступные live LM, UI/DPI/Java — отдельные честные статусы, а не fake GREEN или молчаливое пропускание.
- Код/отчёты в публичном `kpCat/PhantomSemanticStudio`, `origin/main`, с доказанным обычным non-force push и проверкой удалённого SHA. Репозиторий L2J не получает никаких коммитов.

## Required evidence

`reports/PSS-004-plan.md`, `reports/PSS-004-final.md`, `reports/PSS-004-ui.md`, `reports/PSS-004-owned-files.txt`, scoped RED/GREEN и final Build-Verify log (без raw model prompts, токенов, приватных кандидатов). Команды/exit codes, число тестов, Designer static/round-trip, UI routes/100%-150% DPI, LM/Java, source write counters, Git SHA+parent+branch+push.

Никаких source/review ZIP, `New-ReviewBundle.ps1`, огромных binary artifacts в Git. Task package ZIP от ассистента — только инструкции. Исторические отчёты не переписывать.
