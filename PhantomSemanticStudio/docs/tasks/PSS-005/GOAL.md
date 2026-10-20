# PSS-005 — смысловая антидубликатная экспертиза кандидатов (advisory, без автоматического допуска)

## Основание и граница задачи

Приложение: `C:\Users\ZBook\PhantomSemanticStudio\`, публичный репозиторий `https://github.com/kpCat/PhantomSemanticStudio.git`, `origin/main`.
**Required base / expected remote main:** `5d1aedaed38d048e3313c0d8be7d5f81f4db2df2` (PSS-004). Перед изменениями проверить **фактический** локальный HEAD/remote, git root, статус и user dirt. Не перебазировать и не стирать. Несовпадение удалённого SHA: `BLOCKED_BASE`, без force/merge/rebase.

Модель Codex: основная coding-модель, reasoning **HIGH**. Новый диалог, одна завершённая задача, без вопросов о повторном согласовании. Следующее задание не начинать.

PSS-004: опубликованы точный отбор 1–20 APPROVED для staging, modal WinForms Designer и проверки; согласно опубликованным логам 77 ordinary PASS/0 FAIL, 4 control PASS/0 FAIL, Build 0 warning/error. Interactive UI, physical DPI и новый VS Designer round-trip **NOT_TESTED**. PSS-003 Java PASS относится только к прошлой физической копии, а не к PSS-005.

**Задача:** помочь пользователю находить не только точные/лексические, но и *потенциальные смысловые* повторы фраз в исходном humanized v1/v2/v3/custom и в своих активных кандидатах. Проверка строго двухступенчатая:

1. **Локальная, полностью офлайн:** детерминированный поиск ограниченного списка похожих записей (`PATTERN` сравнивать с `PATTERN`, `TEMPLATE` с `TEMPLATE`), ID и provenance, показ ограничений покрытия. Никакого ложного «смысловой дубль доказан» на основании триграмм.
2. **Опциональная по явной кнопке:** один bounded JSON Schema POST в *локальный* LM Studio к выбранной Gemma; сравнить кандидата со сформированным shortlist и получить решения `SAME_MEANING / RELATED / DIFFERENT / UNSURE` с краткими причинами. Это **совет модели**, не утверждение о полном охвате и не разрешение на staging.

Сохранить результат модели **в собственном workspace** как ограниченное типизированное evidence, привязанное к точной редакции кандидата, source fingerprint, peers fingerprint, model ID и shortlist fingerprint. После редактирования кандидата/смены источника или peers — evidence показывает `STALE`, а не «чисто». Старые session.json продолжают загружаться. Результат не меняет одобрение, не выбирает/исправляет/отклоняет автоматически и не переключает тип генерации.

## Абсолютная безопасность

- Весь `C:\Users\ZBook\L2J_Mobius\` **READ_ONLY**, включая High Five. Никаких записей, запусков Ant/JAR/Java/server/client/DB, git-команд в L2J и копирования новых XML поверх него. Вызовы `PackReader` для чтения разрешены.
- Никаких файлов из `workspace`, model prompts/responses/credentials или stage XML в Git. Нет review/source ZIP от Codex. Пользовательская существующая кнопка REVIEW_ONLY JSON ZIP не меняется.
- **НЕ МЕНЯТЬ** `IsolatedPackStager`, `ReviewExporter`, `StageBatchSelection`, `StageSelectionForm`, Java operator, schema/XML, v1/v2/v3/custom/manifest или лимиты Java. После PSS-005 реальный XML всё ещё только `STAGED_UNVALIDATED` до отдельной ручной Java-проверки.
- Не добавлять auto-approve/auto-reject, автоисправление слов, самостоятельную загрузку модели, фоновые задачи, бесконтрольную генерацию, внешние API/embedding-model downloads, model tools/function calls, shell/Process.Start, хранение токена или retries.
- `CandidateValidator` не должен удалять предупреждение `SEMANTIC_NOT_CHECKED` на основании ответа Gemma: отсутствие найденного совпадения **не доказывает** отсутствие смысловых повторов во всех 21k+ ответах.

## Read first

`AGENTS.md`, `README_RU.md`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`, `reports/PSS-003-final.md`, `reports/PSS-004-final.md`, `reports/PSS-004-ui.md`; весь пакет PSS-005, `src/PhantomSemanticStudio.Core/{Models,TextRules,CandidateValidator,PackReader,WorkspaceStore,LmStudioClient}.cs`, `src/PhantomSemanticStudio.WinForms/{MainForm,MainForm.Designer}.cs`, `tests/PhantomSemanticStudio.Tests/{Program,Pss004}.cs`, скрипты Build-Verify/Verify-Designer и .gitignore. Не начинать массовый аудит L2J.

## Deliverable

- Два понятных, статически созданных в MainForm.Designer.cs действия на вкладке «Кандидаты»: **«Найти похожие»** (без сети) и **«Оценить смысл (Gemma)»** (по явному нажатию). Существующий `txtValidation` допускается переиспользовать для полного списка ID/текстов/решений/границ охвата; не создавать 7-ю вкладку. Сначала сохранить/разрешить несохранённые правки. Без фактического source snapshot не выполнять запрос.
- Детерминированный offline shortlist, максимальное число сравнений с Gemma **12**. Не скрывать, сколько исходных элементов было просмотрено и сколько НЕ передано модели. `PATTERN` в пределах темы/act имеет больший приоритет, `TEMPLATE` — совпадающий act; exact matches в других act также нельзя молча исключить. Без лексически близких примеров нельзя объявлять нулевую вероятность дубля; сохранять явное `COVERAGE_LIMITED`.
- Строгое typed evidence: идентификаторы оригиналов, их хэши/положение, verdict + причина, статус `MODEL_ADVISORY_NOT_VERIFIED`, дата, model ID и freshness SHA; никаких больших сырых ответов в workspace/Git. Не изменять `CandidateReview.Fingerprint` и старые approval.
- JSON Schema + собственный строгий парсер, точный набор ID без пропусков/лишних/дублей, one POST, no retries, reject tools/refusal/finish_reason length/unknown labels/malformed JSON; cancellation, timeout, 4xx/5xx = zero new evidence. Ошибки локализованы, без echo token/response/prompt.
- Независимые RED/GREEN проверки; итоговый Build-Verify (обычные 77 плюс новые), static Designer и доступные реальные UI smoke. Live Gemma тестировать **только если локальный сервер уже запущен**: максимум один явный POST для 1 кандидата/shortlist; если connection refused — `BLOCKED_LM`, не повторять и не выдавать fake HTTP за live.
- Финальные `reports/PSS-005-final.md`, `reports/PSS-005-ui.md`, `reports/PSS-005-owned-files.txt`, logs (без raw user/model text), commit/push только в собственный origin/main и проверка remote SHA. **Никаких review ZIP.** STOP.

Структура интерфейсов и шаги реализации — `ARCHITECTURE.md` и `IMPLEMENTATION.md`, проверки — `TEST_CASES.md` / `ACCEPTANCE.md`, Git — `SAFETY_AND_GIT.md`.
