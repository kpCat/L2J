# PSS-005 — подтверждённые входные контракты (GitHub PSS-004)

Репозиторий: https://github.com/kpCat/PhantomSemanticStudio, `main` базовый SHA `5d1aedaed38d048e3313c0d8be7d5f81f4db2df2`.

| Owner | Реальное поведение до PSS-005 | Обязательное решение |
|---|---|---|
| `src/PhantomSemanticStudio.Core/CandidateValidator.cs` | `EXACT_DUPLICATE` ошибка, `NEAR_DUPLICATE` lexical по триграммам (порог 0.72, top 3), `SEMANTIC_NOT_CHECKED` warning | Не выдавать lexical за semantic; существующие error/warning сохранять |
| `src/PhantomSemanticStudio.Core/TextRules.cs` | NFKC, русская нижняя строка, ё→е, буквенно-цифровая нормализация; трёхграммный similarity | Переиспользовать без глобального изменения нормализатора |
| `src/PhantomSemanticStudio.Core/PackReader.cs` | Source fingerprint, `PackEntry` id/text/kind/act/topic, SourceFile/SourceLine, импортированный humanized пакет | Сохранять полный provenance, v1/v2/v3/custom и source drift |
| `src/PhantomSemanticStudio.Core/Models.cs` | Candidate — mutable class; `SessionState.Version=1`; optional JSON fields допустимы | Поле advisory evidence необязательное, совместимое с сохранёнными файлами |
| `src/PhantomSemanticStudio.Core/WorkspaceStore.cs` | Atomically saves session.json, bounds, source disjoint, workspace lock | Валидировать evidence, не менять source/approval |
| `src/PhantomSemanticStudio.Core/LmStudioClient.cs` | localhost /v1, one POST, strict `response_format.json_schema`, no tools, no redirects/proxy, sanitized errors | Новый semantic endpoint через те же guards, не дублировать небезопасную сеть |
| `src/PhantomSemanticStudio.WinForms/MainForm.cs` | Выбор Candidate с сохранением редактора; `RunAsync` + cancel; `txtValidation` и кнопка обычной проверки | Добавить два явных UI действия, не строить controls в runtime |
| `src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs` | 6 tabs, кандидатский editor/validation text и кнопки Save/Validate/Approve/Reject | Две static Button, совместимый Visual Studio Designer layout |
| `src/PhantomSemanticStudio.Core/IsolatedPackStager.cs` | Только exact APPROVED с ручными подтверждениями, отдельный XML в workspace, всегда NOT_RUN до Java | Не менять в PSS-005 |
| `src/PhantomSemanticStudio.Core/StageBatchSelection.cs` + `StageSelectionForm` | PSS-004 exact subset 1–20 и два согласия | Не менять в PSS-005 |

Прямые ссылки: https://github.com/kpCat/PhantomSemanticStudio/blob/5d1aedaed38d048e3313c0d8be7d5f81f4db2df2/src/PhantomSemanticStudio.Core/CandidateValidator.cs ; https://github.com/kpCat/PhantomSemanticStudio/blob/5d1aedaed38d048e3313c0d8be7d5f81f4db2df2/src/PhantomSemanticStudio.Core/LmStudioClient.cs ; https://github.com/kpCat/PhantomSemanticStudio/blob/5d1aedaed38d048e3313c0d8be7d5f81f4db2df2/src/PhantomSemanticStudio.WinForms/MainForm.cs.

Проверка публикации GitHub на подготовке пакета: `main` = `5d1aedaed38d048e3313c0d8be7d5f81f4db2df2`, parent `edc41128270c14578b9edf748a3c8bc0c6196623`; опубликованный отчёт PSS-004 содержит 77/0 ordinary и 4/0 WinForms-control тестов, UI/DPI/Designer round-trip отдельно NOT_TESTED. Повторной локальной C# сборки при подготовке задачи нет. Java PSS-003 PASS относится к прошлой staged-копии, **не** к PSS-005.
