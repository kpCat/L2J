# Импорт ChatGPT и offline платформа v4

Система работает без LM Studio, Codex, сети и внешней модели. ChatGPT создаёт файл отдельно; Studio/CLI проверяют его локально. Java использует только проверенный compiled pack. Текст файла не имеет полномочий исполнять команды, выдавать предметы или подтверждать чужое согласие.

## Через Studio

Соберите `PhantomSemanticStudio.sln` в Release и запустите `PhantomSemanticStudio.exe` из `src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows`. Откройте вкладку «Настройки», укажите свой High Five source и откройте «Импортировать файл ChatGPT / категории v4». Импорт старого пака и подключение LM Studio для этой операции не требуются.

Выберите `.jsonl` или папку. «Проверить и просмотреть» показывает category/mode, принятые записи, дубли, ошибки с файлом/строкой и примеры. Ошибка блокирует принятие всей партии. «Принять в workspace» — отдельное явное действие, связанное с hash проверенных байтов и registry. Изменённый файл нужно проверить повторно. Повторный импорт даёт ноль новых IDs. Исходные bytes сохраняются как immutable preimages.

«Собрать и проверить» создаёт отдельный versioned stage с индексами и manifest. В отчёте coverage входы, ответы и инициативы показаны раздельно. «Пример ответа» — ограниченный редакционный C# preview; реальный Java consumer проверяется отдельным операторским инструментом. Stage ещё не установлен в игру.

Для каждой исходящей category/mode/stance/event и всех указанных отношений build требует безопасный fallback: ANY, без facets, NONE/GENERAL и спокойный tone (CALM, DRY, WARM, SHY, FORMAL или DIRECT). Обычный спокойный ответ уже подходит; один fallback может покрывать несколько relations. Сообщение `CONTEXT_FALLBACK_REQUIRED` означает, что нужно добавить такой ответ и повторить сборку; уже принятые файлы сохраняются. Это обеспечивает доступность нейтрального ответа при ограниченном подборе в большом корпусе.

Подбор ограничен по CPU и не перебирает весь корпус. Недавние ответы исключаются; если безопасные неповторённые варианты исчерпаны, допустимо молчание. Для живого диалога полезно несколько разнообразных безопасных вариантов, а не один fallback на всю тему.

Рабочие файлы находятся во внешнем `%LOCALAPPDATA%/PhantomSemanticStudio/workspace/semantic-v4`. Запись во весь Git root L2J и пересекающиеся/symlink/junction пути запрещена. GUI не запускает Java, Ant, git, shell или CLI.

## Через CLI

Команды ниже запускаются из корня checkout. Путь пользователя и буква диска не зашиты в программу:

```powershell
dotnet build .\PhantomSemanticStudio\PhantomSemanticStudio.sln -c Release --nologo
$cli = Join-Path (Get-Location) 'PhantomSemanticStudio/src/PhantomSemanticStudio.Cli/bin/Release/net10.0/PhantomSemanticStudio.Cli.dll'
$source = Join-Path (Get-Location) 'L2J_Mobius_CT_2.6_HighFive'
$workspace = Join-Path $env:LOCALAPPDATA 'PhantomSemanticStudio/workspace/v4'
$batch = 'C:\YOUR_FILES\chatgpt.jsonl' # замените только путь к полученному файлу
dotnet $cli validate $batch --workspace $workspace --source $source
dotnet $cli import $batch --dry-run --workspace $workspace --source $source
dotnet $cli import $batch --workspace $workspace --source $source
dotnet $cli build --workspace $workspace --source $source
dotnet $cli stats --workspace $workspace --source $source
dotnet $cli categories --workspace $workspace --source $source
dotnet $cli preview --category hybrid.trade.absurd_offers --persona calm --relation UNKNOWN --workspace $workspace --source $source
dotnet $cli export-passport (Join-Path $workspace 'CHATGPT_GENERATION_RU.md') --workspace $workspace --source $source
```

Для папки передайте её абсолютный путь вместо `$batch`. `validate` проверяет файлы; `import --dry-run` дополнительно сравнивает с workspace без принятия. `build` возвращает абсолютный `StagePath`; проверка конкретного stage:

```powershell
dotnet $cli verify $stage --workspace $workspace --source $source
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantoms\Test-SemanticV4Content.ps1 -StageRoot $stage
```

`$stage` — путь из результата build. Java proof компилирует реальный production consumer в отдельном shadow, без GameServer, DB и native world. Имена JSONL-файлов не определяют output paths. Нужны SDK.NET10 и JDK25; новые библиотеки не устанавливаются.

## Новая мини-категория

В форме укажите существующий родитель, новый полный ASCII key и русское название. Нажмите «Предложить», прочитайте показанные act/intent и hash, проверьте смысл/дубли, отметьте подтверждение и нажмите «Зарегистрировать проверенную категорию». Правка полей отменяет review. Затем экспортируйте один обновлённый паспорт. Он содержит текущий approved registry, enum, голоса и события; правка Java и участие Codex не нужны.

CLI предоставляет тот же путь:

```powershell
dotnet $cli taxonomy propose --parent hybrid.trade --key hybrid.trade.owner_reviewed --label 'Проверенная владельцем тема' --act hybrid.trade.owner_reviewed.reply --intent hybrid.trade.owner_reviewed.observe --workspace $workspace --source $source
dotnet $cli taxonomy approve --proposal $proposalPath --hash $reviewedHash --reviewed --workspace $workspace --source $source
dotnet $cli export-passport (Join-Path $workspace 'CHATGPT_GENERATION_RU.md') --workspace $workspace --source $source
```

`$proposalPath` и `$reviewedHash` берутся из результата propose после ручного просмотра. Неизвестная категория в JSONL не регистрируется автоматически. Новая категория сама по себе не даёт реплик или распознавания: импортируйте отдельные REPLY/TRIGGER batches, затем build/verify. INITIATIVE требует зарегистрированного реального события.

## Ограничения и подключение

Синтаксическая проверка не доказывает художественное качество и смысл каждого текста. Точные нормализованные дубли блокируются/пропускаются по диагностике; лексическое сходство требует редакционного review. Неизвестная фраза может остаться без ответа. Шутки и угрозы не превращаются в торговлю или игровые действия. Неизвестное согласие человека не предполагается; mature/profanity и отношения ограничивают выбор. Если `relations` опущен, запись подходит только `UNKNOWN`; для более широкого охвата паспорт просит явно перечислить допустимые отношения. Исходящий текст ограничен возможностями существующего chat writer: максимум 100 Unicode code points с учётом разрешённой подстановки имени.

v4 выключен по умолчанию: `EnablePhantomSemanticV4=False` (отсутствующий ключ тоже OFF). Проверенный stage не публикуется автоматически. Владелец отдельно принимает manifest/hashes и выполняет действующий reviewed operator promotion с owner/byte/preimage guards в своей поставке; эта задача не меняет PLAY, TEST094, рабочий datapack или личные настройки. Только после такой отдельной проверки копия stage может занимать `data/phantoms/semantic/humanized/v4` в операторской поставке; явный `EnablePhantomSemanticV4=True` подхватывается только при startup. Повреждённый v4 при startup отклоняется с сохранением legacy backend. Наличие offline PASS не означает live-game приёмку: `REAL_GAME_QA_PENDING`; новый внешний чат ChatGPT — `OWNER_CHAT_QA_PENDING`.
