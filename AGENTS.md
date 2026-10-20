# Phantom World — правила разработки

Основная ветка разработки и обычной публикации: **feature/phantom-world**. Не использовать experiment/m1-candidate007-observe008 или старые codex-ветки как baseline новых задач. master, origin/master, upstream/master и GitHub default branch не изменять.

## Структура и read-first

- `L2J_Mobius_CT_2.6_HighFive/` — сервер Java High Five и единственный игровой Semantic Engine.
- `PhantomSemanticStudio/` — исходники редактора C# WinForms / .NET10; отдельный бинарный процесс, не серверная зависимость.
- `Account_Manager/` — общий PHP-инструмент аккаунтов; `.idea/` — настройки IDE. Не удалять их без отдельного обоснованного задания.
- Сначала прочитать task, ближайшие инструкции и существующий аналог. Для High Five начать с `docs/phantoms/PHANTOM_CURRENT_STATUS.md` и `CODEX_WORKFLOW_CONTRACT.md`; для Studio — с её `AGENTS.md` и `README_RU.md`.
- Путь репозитория определять фактически. Не привязывать код, scripts или инструкции к имени пользователя Windows, букве диска или имени корневой папки.
- Scope ограничивать задачей. Не смешивать реорганизацию с новыми AI/UI-возможностями, заменой стека или широким refactor.

## Git и сохранность

Для bounded проверки ветки и scope разрешены `git rev-parse`, `git symbolic-ref`, `git status`, `git worktree list`, `git for-each-ref`, `git diff -- <exact paths>`, `git ls-tree` и `git merge-base`. При необходимости записи, commit/push или удаления refs требуется явное разрешение текущей задачи. Публикация только обычным push в feature/phantom-world.

Сохранять чужой dirt. До замены или переноса уникальных файлов — побайтовые копии вне checkout и проверка SHA-256. Не применять clean, reset --hard, массовый stash, force checkout, add ., Stage All, force push или переписывание истории. Исторические отчёты и task packages не переписывать ради текущего состояния.

## Сборки и проверки

Java: JDK25 и Ant. Из корня изолированного checkout:

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-humanized-v3-content-validate
```

`jar` пишет в корневой `build/` и копирует JAR в локальный `dist/libs`. Не запускать его поверх защищённой поставки. Ant-запуски выполнять последовательно. Общий `ant verify` содержит DB-проверки: не запускать его вместо targeted DB-free gate.

C#: SDK.NET10, C#14, WinForms; существующие зависимости и lock-файлы сохранить.

```powershell
dotnet build .\PhantomSemanticStudio\PhantomSemanticStudio.sln -c Debug --nologo
powershell -NoProfile -ExecutionPolicy Bypass -File .\PhantomSemanticStudio\scripts\Build-Verify.ps1
dotnet .\PhantomSemanticStudio\tests\PhantomSemanticStudio.Tests\bin\Release\net10.0\PhantomSemanticStudio.Tests.dll --task062
dotnet .\PhantomSemanticStudio\tests\PhantomSemanticStudio.Tests\bin\Release\net10.0\PhantomSemanticStudio.Tests.dll --pss-010
```

Tests — существующий консольный runner, не `dotnet test`. Статическая проверка Designer не доказывает GUI или Visual Studio Designer round-trip.

## Semantic Pack и безопасная интеграция

Живые данные: `L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/semantic/` и `conversation/`, включая humanized/v3 и custom. Канонический Java loader: `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java` внутри High Five. Reader/preview Studio не является вторым серверным движком и не доказывает Java parity.

Studio читает L2J; все рабочие записи — во внешнем `%LOCALAPPDATA%\PhantomSemanticStudio\workspace`. Весь Git root L2J защищён от runtime-записи Studio, source/workspace disjoint и reparse guards сохраняются. Approval и создание staged-предложения остаются явными; автоматического overwrite/promotion рабочего Semantic Pack нет.

Для готового допустимого stage оператор отдельно запускает `PhantomSemanticStudio/scripts/Test-PSS003-Java.ps1 -StageRoot <absolute-stage>` либо `Test-PSS008-V3-Java.ps1` для v3. Эти инструменты используют физический shadow, exact hashes и настоящий Java loader; никаких серверов/БД. Их pinned build/copy contract обязателен: BLOCKED_JAVA при несовпадении не обходить и не считать PASS. См. RESULT062 и ограничения Studio README.

Не запускать и не менять защищённые release057–061, их БД, MAIN/PLAY, клиент, общую MariaDB и чужие worktrees. Новая TEST-среда требует отдельного подтверждённого безопасного scope.

## Качество изменений

WinForms layout — в InitializeComponent внутри Designer; логика и runtime — в основных .cs. Конструкторы остаются безопасными для Designer. Для изменения поведения использовать 1–3 нужных focused tests в существующем runner.

Русский/украинский текст писать читаемой кириллицей, не Unicode/XML escapes. После правок выполнить отдельные проверки полного набора mojibake-маркеров и escaped Cyrillic. Результат сообщать честно: выполненные команды, exit codes, scope, commit/push и оставшиеся BLOCKED/NOT_RUN.
