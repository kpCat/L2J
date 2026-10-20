# PSS-009 — обязательная матрица интерфейса

## Главная MainForm: ВСЕ 6 вкладок, даже если первая кажется исправленной

| Вкладка | Ключевые контролы для фактических Bounds, first/second switch, resize |
|---|---|
| `tabGenerate` Конструктор | `grpRequest`, `cmbAct`, `cmbTopic`, `cmbBand`, `cmbGender`, `cmbRegister`, `cmbMode`, `txtInstruction`, `txtWords`, `grpGeneratorInfo`, `nudCount`, `btnGenerate`, `btnToCandidates` |
| `tabChat` Диалог и обучение | `txtConversation`, `txtChatInput`, `btnPreview`, `txtCorrection`, `btnTeach`, `btnClearChat`, `btnDialogueLab`, `grpLessons`, `cmbLessons`, `btnApplyLesson` |
| `tabLibrary` Библиотека | `txtSearch`, `btnSearch`, `lblLibraryStats`, **`btnPackQuality`**, `gridLibrary` (все четыре колонки/горизонтальный доступ), `txtLibraryDetails` |
| `tabCandidates` Кандидаты | `gridCandidates`, `txtCandidateText`, `txtReviewNote`, `txtValidation`, `btnSaveCandidate`, `btnValidate`, `btnApprove`, `btnReject`, `btnFindSimilar`, `btnSemanticReview` |
| `tabExport` Экспорт для ревью | `txtExportInfo`, `btnCheckSource`, `btnExport`, `btnStageXml`, **`btnV3Proposal`**, `btnCopyWorkspace`, `txtExportLog` |
| `tabSettings` Настройки | `txtSourcePath`, `txtEndpoint`, `txtModel`, `txtApiKey`, `nudTemperature`, `nudTokens`, `nudTimeout`, `txtWorkspace`, `btnSaveSettings`, `btnCheckLm`, `btnImport`, `btnChatCorpus`, `txtSettingsResult` |

Для каждой страницы проверить parent (TabPage), её реальный `ClientSize`, фактические `Bounds` и `Visible`, clipped coordinates; не считать тестом только переход к вкладке или наличие контрола в дереве.

## Дополнительные Designer-формы

- `StageSelectionForm` — modal 1–20, full preview, default No, long notes, min size и отказ от обрезания текста.
- `DialogueLabForm` — tabsLab (трёхстраничный), `txtTranscript/Trace/Input`, mentor/corpus permissions, кнопки, TabPage sizes, собственные Anchor и DPI.
- `ChatCorpusForm` — browse/import/progress/cancel/search/filters/page/listRows/selected/details и PII warning.
- `PackQualityForm` — Analyze/Cancel/filters/grid/details, минимум 980×760 outer против ClientSize980×730, Scroll.
- `V3ProposalForm` — выбор pair, 2 consent по умолчанию No, stage/proof/release disabled до разрешения, full preview/proof/hashes, Scroll.

Не ломать сохранённые exact approvals, безопасность, имена/event handlers. UI-выполнение допускает только синтетические fixture данные и собственный workspace; диалоги не должны запускать настоящие model API / Ant / сервер.
