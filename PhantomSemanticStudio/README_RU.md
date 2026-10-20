# Phantom Semantic Studio · 0.1

Отдельная C# WinForms-программа для расширения **разговорного** Semantic Pack High Five под контролем редактора. Текущий checkpoint PSS-010: [reports/PSS-010-final.md](reports/PSS-010-final.md), [UI evidence](reports/PSS-010-ui.md); Java content evidence прежнего PSS-003: [reports/PSS-003-final.md](reports/PSS-003-final.md).

В исходной поставке сборка C#, Visual Studio Designer, LM Studio и Java runtime не запускались. Этот исторический статус сохранён в BASELINE_VERIFICATION.md. PSS-001 выполняет реальные Windows-проверки; LM, Designer, runtime UI/DPI и Java имеют отдельные статусы в итоговом отчёте. Статические проверки структуры не заменяют компиляцию.

## Где распаковать
Создайте `C:\Users\ZBook\PhantomSemanticStudio\` и распакуйте содержимое ZIP туда. В этой папке сразу должны лежать `PhantomSemanticStudio.sln`, `src`, `tests`, `scripts`.
Не распаковывайте программу в `L2J_Mobius` и не заменяйте ею игровой пак.

## Открыть и собрать
Откройте `PhantomSemanticStudio.sln` в Visual Studio 2026 с .NET 10 / .NET desktop development. Startup project: `PhantomSemanticStudio.WinForms`.
Откройте `src\PhantomSemanticStudio.WinForms\MainForm.cs` → «Открыть конструктор».
Контролы находятся в `MainForm.Designer.cs`; конструктор формы не обращается к файлам/API. Все вкладки и controls объявлены статически, без циклов в InitializeComponent.
Модальная форма выбора партии: `StageSelectionForm.cs` → «Открыть конструктор»; `.Designer.cs` и `.resx` вложены в проекте. Её конструктор тоже содержит только `InitializeComponent()`.
Окно корпуса: `ChatCorpusForm.cs` → «Открыть конструктор», со статическими `.Designer.cs`/`.resx` и безопасным конструктором. Static verification не заменяет настоящий VS round-trip.
Лаборатория: `DialogueLabForm.cs` → «Открыть конструктор»; зависимости задаются после конструктора, layout/events в `.Designer.cs`, ресурсы в `.resx`.

Из PowerShell:
```
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Verify.ps1
```
После успешной сборки запускайте F5 или `Start.cmd`. Скрипты ничего не устанавливают в L2J.

## Первый сценарий
1. «Настройки»: уже вписан `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive` и `http://127.0.0.1:1234/v1`.
2. В LM Studio запустите локальный API server и загрузите свою модель. Identifier по умолчанию: `gemma-4-26b-a4b-it-ultra-uncensored-heretic`. При включённой авторизации введите token; он остаётся только в памяти процесса.
3. «Проверить LM Studio / модель» делает один GET /v1/models. CHECKED_MODEL_LIST означает только наличие точного ID: при JIT список может содержать скачанную, ещё не загруженную модель. Успешная генерация и качество проверяются отдельно. При SERVER_OFFLINE: LM Studio → Developer → API Server → Start, затем проверьте порт и точный API identifier. AUTH — проверьте token вручную; ENDPOINT — локальный путь /v1; MODEL_NOT_LISTED — исправьте ID вручную. TIMEOUT и CANCELLED различаются; BAD_RESPONSE отклоняет всю партию. Повторов и автоматической подмены модели нет.
4. «Импортировать пак»: читаются имеющиеся humanized v1/v2/v3/custom. Функциональный Semantic Pack не редактируется.
5. «Конструктор»: выберите существующий act/topic, отношения, пол и стиль; напишите пожелания. Тип по умолчанию «Ответ (TEMPLATE)»; «Входная фраза (PATTERN)» создаёт только входы игрока, «Смешанный (MIXED)» допускает оба вида. Выбранный тип ограничивает JSON Schema и повторно проверяется локально у каждого item; неверный kind отклоняет всю партию. Старые GenerationRequest/замечания читаются с MIXED в Core; применение замечания сохраняет явный выбор UI. Партия по умолчанию 6, максимум 20. Контекст включает ограниченную выборку релевантных записей, не весь репозиторий.
6. «Кандидаты»: проверьте, отредактируйте, сохраните и одобрите каждую нужную запись с отметкой ручного ревью. Входные фразы и ответы различаются. Ошибки не принимаются автоматически; неудачная генерация не запускает бесконечных повторов.
7. «Экспорт для ревью»: прежний отдельный ZIP с одобренным JSON, baseline и замечаниями, **REVIEW_ONLY_NOT_SERVER_VALIDATED**. Эта кнопка не создаёт XML. **Java-validator NOT_RUN; не распаковывать поверх сервера.**
8. «Создать изолированное XML-предложение»: в модальном окне вручную отметьте конкретные 1–20 APPROVED из любого числа одобрений. По умолчанию выбор пуст. Поиск по ID/тексту/виду/теме/act показывает первые 500 совпадений; скрытые отметки сохраняются, справа всегда доступен полный выбранный список с исходными текстами и отметками редактора. Старое одобрение или baseline и выбор >20 блокируют продолжение до ручного исправления выбора. Затем отдельно подтвердите точный прокручиваемый список (default «Нет») и редакционную аттестацию (default «Нет»). Отмена ничего не создаёт. Весь выбранный batch повторно проверяется и отклоняется при несовместимой записи; никто не пропускается молча. Только Gender ANY, существующая разговорная topic/act-связка, без placeholders/markup/control, 160 UTF16 после нормализации для PATTERN и 240 UTF-8 bytes для TEMPLATE. Пользователь отдельно проверяет мат/adult, гендерные обороты и ложные игровые утверждения: автоматическое доказательство смысла отсутствует.
9. В `workspace/proposals/<id>/module/dist/game/data/phantoms` создаётся побайтная копия ровно импортированных файлов. Append только в `semantic/custom/my-social-topics.xml` и `conversation/custom/my-phrases.xml`, deterministic IDs и `override="false"`; existing overrides/comments сохраняются. Остальные SHA/bytes неизменны. Source fingerprint проверяется до/после, правка approval блокирует stage. Atomic .partial→finished; receipt хранит ID/hashes без raw текста. Статус **STAGED_UNVALIDATED / Java NOT_RUN / НЕ ДЛЯ УСТАНОВКИ**.

Отдельная явная операторская проверка готового stage:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-PSS003-Java.ps1 -StageRoot <абсолютный путь готового workspace\proposals\id>
```
Скрипт требует JDK25/Ant и локальные libs; ничего не загружает. Физический scratch внутри stage получает только build.xml/java/test/java/test/resources и dependency JAR (без GameServer/LoginServer/sources JAR). Запускаются штатный `phantom-humanized-v3-content-validate` и узкий `loadV3(..., true)` bridge с baseline/stage counters/hash/ID assertions и отдельной corrupt-копией. Все build/temp/reports только внутри scratch. Копирование ограничено 4000 файлами/256 MiB, свободный scratch budget минимум 512 MiB. Доказательство сохраняется в `oracle-<id>/java-validation.json`, связано с exact staged hashes; исходный receipt остаётся STAGED_UNVALIDATED, GUI не присваивает Java PASS автоматически.

**PASS_JAVA_STAGED / JAVA_CONTENT_VALIDATED_NOT_SERVER_READY** означает только успешную загрузку именно этой физической копии. При недоступном окружении **BLOCKED_JAVA**, при отказе loader **FAILED_JAVA**. Изменившиеся source/stage требуют нового staging и проверки. Ни один статус не разрешает установку или публикацию на сервер.

## Диалоговое обучение

На вкладке «Кандидаты» доступны два отдельных действия. «Найти похожие (локально)» читает источник и сравнивает выбранную сохранённую запись со всем корпусом того же вида и активными кандидатами. Показывает до 12 references с полным текстом, ID, provenance и лексической оценкой, число просмотренных и не переданных модели записей. Exact-повторы из других act сохраняются. Поиск работает без LM Studio; `COVERAGE_LIMITED` всегда означает неполное смысловое покрытие, включая пустой результат и пропущенные синонимы.

«Оценить смысл (Gemma)» делает один явный JSON Schema POST выбранной локальной модели по этому ограниченному списку. Только `SAME_MEANING / RELATED / DIFFERENT / UNSURE` и короткие причины. `MODEL_ADVISORY_NOT_VERIFIED` — мнение модели, решение остаётся ручным. При ошибке/отмене новый отчёт не сохраняется, локальный список доступен; повторов и подмены модели нет. Несохранённую правку сначала нужно сохранить/отбросить/отменить штатным диалогом.

Evidence содержит ID/hashes/provenance/verdict/reason/model/UTC, максимум 12 решений, 240 символов причины и 32 KiB на объект. Оно сохраняется только в `session.json` собственного workspace после повторного чтения источника и проверки candidate/source/peers/shortlist SHA. Старые session Version=1 совместимы; advisory не входит в approval fingerprint. Правка сбрасывает evidence; изменившийся источник, scope, active peers или references дают `STALE`. Выбор сохранённого отчёта перечитывает источник; ошибка чтения также даёт STALE. Семантическое мнение не меняет approvals, generation mode, exact staging или Java-gates; `SEMANTIC_NOT_CHECKED` сохраняется. Live PSS-005: один POST завершился BAD_RESPONSE, без новой evidence; модель выгружена.
«Диалог и обучение» выбирает ответ из импортированного каталога приблизительным C#-инспектором, без вызова Gemma. Показаны pattern/template ID. Это не полная эмуляция сервера: functional-first, identity, социальные gates, gameplay, mood, постоянная память и точный Java selector здесь не воспроизведены.
Объяснение ошибки сохраняется с topic/act/полом/отношениями/стилем и source fingerprint. Во вкладке «Диалог и обучение» можно выбрать и просмотреть сохранённое замечание, затем явно применить только его в конструктор. При смене baseline нужно новое ручное ревью. Старые строковые замечания сохранены; для них scope и baseline неизвестны и требуют проверки. Генерация и каждое одобрение остаются ручными.

При смене кандидата, вкладки или закрытии несохранённый текст и отметка ревью защищены диалогом «Сохранить / Отбросить / Отмена». Правка отменяет approval; неудачное сохранение сохраняет прежнюю корректную state и текст в редакторе.

## Корпус публичных чатов

На вкладке «Настройки» откройте «Корпус чатов», выберите локальный `chat.zip` и нажмите «Импортировать». Это отдельное окно; импорт пака и загрузка модели для корпуса не нужны. ZIP только читается, без распаковки. Приватные TELL/FRIENDTELL и их варианты исключаются до записи; ники из заголовков не сохраняются. Публичные оригиналы остаются только в приватном `workspace/corpora`, отдельно от session.json. SQLite DB и receipt имеют неизменяемую версию/отпечатки; повреждённый индекс блокирует чтение. Cancel отменяет чтение и SQL, завершённый прежний корпус сохраняется.

Поддерживается плоский classic ZIP с .log и chat.log.date: до 500 entries, 64 MiB на entry, 256 MiB исходных и распакованных bytes, 3 млн строк, 16 KiB на строку, 4000 символов сообщения, сжатие до 100:1. Central directory до 512 KiB проверяется до создания entries. Шифрование, ZIP64, split archives, ссылки, вложенные пути и неправильный UTF-8 отклоняются. Большие логи нужно разделить заранее в отдельную копию; программа не меняет архив.

Поиск и фильтры по каналу, языковой оценке, дате, длине, дубликатам и шуму дают страницы по 100 записей. «LATIN_TRANSLIT_CANDIDATE» означает возможный транслит, «EN_OR_OTHER» — латиницу без доказательства английского. Перевода нет. Вручную отметьте от 1 до 20 публичных фрагментов; скрытые фильтром отметки видны справа. Другой корпус очищает выбор. Preview приблизительно скрывает email/URL/IP/телефоны; свободный текст может содержать личную информацию, совершенная анонимность не доказана. Статус всегда SOURCE_MATERIAL_ONLY. Обучение, LM-запросы, создание кандидатов, approvals и XML из этого окна не запускаются.

Одна direct NuGet dependency Microsoft.Data.Sqlite закреплена на 10.0.12, native SQLitePCLRaw — lock files. Core session по-прежнему JSON. DB cache 8 MiB, SQL temp в памяти, paging <=200; corpus.db ограничен 1 GiB. Не публикуйте workspace, ZIP и индексы. Synthetic smoke использует только вымышленные данные.

Сохранённый семантический совет теперь проверяется в фоне с отдельной отменой. Пока проверка не закончилась, показывается STALE; смена кандидата/правка/отмена не позволяют старой операции перерисовать новый выбор. BAD_RESPONSE показывает безопасный этап отказа (envelope/content/schema/finish/refusal/tools), без сырого ответа или повторного запроса.

## Лаборатория диалогов PSS-007
На вкладке «Диалог и обучение» после импорта пака откройте «Лабораторию диалогов». PACK выбирает пригодный TEMPLATE существующего каталога: буквальный текст или безопасную подстановку {value} из реально захваченного нормализованного input. Шаблоны с недоступными name/interest/memory и неизвестными подстановками пропускаются; вымышленных значений нет. Trace сохраняет PatternId, выбранный TemplateId, act/topic и fingerprint. PACK_CATALOG_APPROXIMATE / NOT_JAVA_RUNTIME_PARITY обязательны: это C# inspector. NO_PACK_MATCH означает отсутствие распознанного PATTERN, TEMPLATE_CONTEXT_UNAVAILABLE — недоступный контекст шаблонов, NO_ELIGIBLE_TEMPLATE — отсутствие ответа в clean/band/register gates, FUNCTIONAL_OR_MEMORY_UNSUPPORTED — настоящая functional/identity/Fact/Recall-ветка. После исчерпания безопасных неповторённых шаблонов допускается повтор с REPEAT_FALLBACK; только принятый ответ меняет очередь. AUTO/GAME/REAL/MIXED и распознавание пвп меняют только advisory гипотезу, не Java-поведение и не исходную реплику. До 200 реплик; input до 1024 символов, match до 256 без усечения.

PSS-010 focused smoke: `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release -- --pss-010`. Узкий read-only v1 probe: тот же runner с `--pss-010-source <HighFive module root>`; читает только два заданных v1 XML и проверяет физическую копию в ignored Studio artifacts, без изменения L2J. Полный импорт каталога и Java parity этим probe не доказываются. Negative mutation: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-PSS010.ps1 -NegativeMutation`; runtime RED, восстановление bytes в finally и final GREEN. Scope/encoding gate: тот же verifier с `-GitScope`.

Наставник по умолчанию выключен. Включите «Gemma помогает», подготовьте context preview, вручную замаскируйте его и подтвердите один локальный запрос, затем нажмите «Спросить Gemma». MENTOR — отдельная роль, не ответ фантома. Контекст ограничен последними 10 репликами / 8 KiB, вопрос один на turn, общий бюджет 20 запросов на лабораторию. Ответ на уточнение принимается только отдельным подтверждением world. Смена контекста/источника или Cancel блокирует устаревший результат; отзыв разрешения до POST отменяет отправку. Автоматических повторов/загрузки модели нет.

Предложение переносится в редактируемую заметку отдельной кнопкой. Несохранённая правка требует Save/Discard/Cancel. Сохранение scoped lesson требует существующих act/topic, явных Band/Register/Gender и проверки точного scope; изменение scope сбрасывает подтверждение. Candidates/approval/XML не меняются. История эфемерна; отдельное явное сохранение создаёт bounded private `workspace/labs` JSON без token, без автоматического восстановления.

В корпусе вручную отметьте 1–20 public строк, проверьте полный sanitized preview и подтвердите перенос в лабораторию. Из лаборатории отдельно отметьте 1–3 фрагмента, задайте manual language override для каждого, при необходимости измените текст. Проверьте точный outbound preview: scrub приблизительный, имена в свободном тексте нужно убрать вручную. Два независимых разрешения перед каждым запросом — PII review и передача локальной Gemma. По отдельной кнопке возможны предложения транслита/перевода/UNKNOWN; mentor opt-in не заменяет corpus consent. Исходный корпус immutable, AI-предложение только заметка, не установка/обучение. Снятие выбора/consent во время preflight отменяет передачу.

Offline: `--pss-007-a`, `--pss-007-b`, `--pss-007-c` у console runner. STA: `dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-007-controls`. Дополнительный static/privacy/encoding/scope gate: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-PSS007.ps1 -GitScope`. LM/UI/DPI/VS/Java статусы отдельно в отчёте, synthetic HTTP не подтверждает live Gemma.

## Качество пака и offline v3 PSS-008

После импорта на вкладке «Библиотека» откройте «Качество и покрытие». Отдельная Designer-форма показывает все объявленные topic/act, включая нулевые строки, pattern topic/act/source и clean template act/band/register/source. Topic у template отсутствует. Числа, остатки capacity, точные нормализованные дубли и ограниченный sample лексических соседей — advisory / NOT_SEMANTIC_VERIFIED / NOT_RUNTIME_PARITY. Анализ не меняет source, corpus, candidates или approval. Фильтры, сортировка, полный trace и отмена работают локально.

На вкладке «Ревью / экспорт» откройте отдельное «Предложение v3». Вручную выберите точные 1–20 CURRENT APPROVED и существующую пару semantic/conversation одной категории из manifest. Проверьте все ID, полные тексты и пути, затем дайте два независимых подтверждения, изначально NO. Изменение выбора/категории сбрасывает подтверждения; снятие подтверждения отменяет worker. Scope требует существующие topic/act в выбранных сегментах, Gender ANY, clean NONE, без placeholder, memory/fact и игровых действий. Консервативные проверки не заменяют редактора. Одна несовместимая запись блокирует всю партию.

Создаётся только физический `workspace/v3-proposals/id`: копия импортированных файлов и append-only изменения 1–2 существующих v3 XML, устойчивые `pss.v3.p/t.*` ID без override. Manifest, custom и остальные файлы сохраняют bytes/SHA. Receipt остаётся `STAGED_V3_UNVALIDATED / Java NOT_RUN / NOT_INSTALLED`.

После Release-сборки оператор отдельно запускает `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-PSS008-V3-Java.ps1 -StageRoot <полный finished path>`. GUI этот script не запускает. Нужны уже установленные JDK25/Ant и точно аудированный исходный build-контракт. Script копирует bounded allowlist в собственный физический oracle, проверяет native loadV3(custom=true), counts/hash, каждый новый ID/text/act/topic, выбор template и отрицательные duplicate/schema случаи. Все Java/Ant/temp/output находятся в shadow; исходный L2J остаётся read-only. Отсутствие зависимости или drift — BLOCKED_JAVA, без загрузок и повторов.

Proof и detached `java-attestation.json` привязаны к точным входам и журналам. Локальный случайный ключ каждого stage хранится только в private `workspace/java-attestations`, вне oracle и экспорта; HMAC блокирует подделку из публичных metadata/hash. Это подтверждение честного локального оператора, не защита от владельца ОС, читающего ключ и самостоятельно подделывающего MAC. Старый unsigned proof не принимается для handoff. Ключ, XML и реальные proof/logs нельзя публиковать.

В форме вручную выберите `java-validation.json`, дождитесь полного preview файлов и SHA, отдельно подтвердите третий NO checkbox и создайте `workspace/release-candidates/id`. Здесь только `proposed`, побайтовые исходные `backup`, hash-manifest и текстовый checklist: `OFFLINE_HANDOFF_NOT_INSTALLED / JAVA_CONTENT_ONLY_NOT_RUNTIME_READY`. Для будущего ручного применения нужна отдельная задача и разрешение владельца. Изменённый, unsigned, failed или не соответствующий stage proof отвергается целиком.

Focused routes: `--pss-008-a`, `--pss-008-b`, `--pss-008-c`; STA через тот же WindowsDesktop runtimeconfig: `--pss-008-controls`. Статические/encoding/source/scope guards: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-PSS008.ps1 -GitScope`. Результаты и отдельные LM/UI/DPI/VS статусы — в reports/PSS-008-final.md и PSS-008-ui.md.

## Защита исходников
`PackReader` только читает. `WorkspaceStore` пишет лишь в `%LOCALAPPDATA%\PhantomSemanticStudio\workspace`, проверяя непересечение с source/repository. Junction/symlink-пути отклоняются. Второй экземпляр блокируется workspace.lock. JSON сохраняется через временный файл и замену. Это программные guards, не отдельная OS sandbox против злонамеренного процесса, меняющего пути во время записи.
JSON экспорт только в workspace/exports; XML staging только в workspace/proposals. Исполняемые Core/GUI не содержат Process.Start, shell, Ant, Java, Git, сервера или доступа к игровой БД. Java operator script отделён от GUI и проверяет только shadow. Нет метода установки/публикации. Отпечаток источника повторно проверяется; изменение текста аннулирует approval, изменённое одобрение не пропускается молча.
Не меняются ни исходные v1/v2, ни действующий v3, ни custom. PSS-008 готовит отдельное v3 предложение; применение требует отдельного будущего ручного процесса.

## Что пока не готово
- Полный визуальный smoke 100%/150% DPI и реальный LM Studio: точные результаты и незакрытые gates приведены в отчётах PSS-002 final/ui. Local build/console/static PASS не заменяет живую Gemma или UI acceptance.
- Полный смысловой охват корпуса, грамматический анализ и истинность игровых фактов. PSS-005 добавляет ручную advisory экспертизу до 12 references; лексический shortlist может пропустить синонимы, а мнение Gemma не доказывает отсутствие повторов.
- Полноценная Java/runtime-parity: отдельный content gate не подтверждает поведение игры или условия выбора реплики. Реальные результаты PSS-003 перечислены отдельно в final report.
- Runtime gender/persona-фильтры. JSON сохраняет редакционные ограничения; XML staging блокирует Gender != ANY и любой placeholder.
- Новые act/topic, fact/recall, игровые действия, мат/mature-авторинг, автоматический монтаж manifest, публикация/rollback рабочего сервера. Не добавлять их тихо в первую задачу.
- Массовый анализ корпуса и обучение весов. PSS-007 допускает только 1–3 явно проверенных public отрывка в отдельном локальном запросе; предложения остаются редакционными.

## Структура
`src/PhantomSemanticStudio.Core` — reader, безопасные пути, workspace, проверка кандидатов, клиент LM Studio, inspector, review export.
`src/PhantomSemanticStudio.WinForms` — стандартная форма и events.
`tests/PhantomSemanticStudio.Tests` — console runner с ненулевым exit code при ошибке; нет пакетов xUnit/NUnit.
`docs` — границы архитектуры и source audit.
`reports/BASELINE_VERIFICATION.md` — честный статус исходной поставки.
`BASELINE_MANIFEST.json` — SHA-256 каждого файла исходной поставки.

После Codex ревью ведётся непосредственно в публичном GitHub main по exact commit/diff, inventory и журналам отчёта. Codex не создаёт review/source ZIP и не запускает New-ReviewBundle. Пользовательский JSON review ZIP приложения сохраняется. Репозиторий L2J не используется как remote нового приложения. STOP после PSS-010; следующая задача не начинается автоматически.
