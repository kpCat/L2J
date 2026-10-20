# Правила работы с Phantom Semantic Studio

Рабочий root: C:\Users\ZBook\PhantomSemanticStudio\.
Это НЕ часть kpCat/L2J. Не распаковывать и не создавать git-ветку программы внутри L2J.

## Неприкосновенная область
C:\Users\ZBook\L2J_Mobius\ целиком, включая High Five, его dist, .phantom-local, серверы, базы, geodata, конфиги и текущий git worktree. Только точечное чтение humanized-каталога/загрузчика. Ни runtime, ни Codex не записывают туда.

## Безопасность программы
- Нет метода/кнопки установки, автопубликации, overwrite core/custom, auto-approve, auto-repair.
- Все записи только в собственный workspace; source/workspace disjoint; reparse guards.
- JSON review export остаётся REVIEW_ONLY. XML-предложение только в отдельной физической копии workspace: STAGED_UNVALIDATED / Java NOT_RUN до отдельного доказательства настоящим Java loader. PASS_JAVA_STAGED означает только content gate, никогда готовность к установке/игровому runtime.
- Модель не генерирует/исполняет код, не получает tools и filesystem. Один bounded JSON-запрос без автоматических повторов.
- Правка отменяет approval; approval связан с content hash и source fingerprint. Нет автоматического rebase кандидатов.
- Не скрывать ошибки/пропущенные проверки под GREEN.

## WinForms
MainForm.cs / MainForm.Designer.cs / MainForm.resx. Standard controls. Constructor только InitializeComponent. Статическая явная инициализация, layout и events в InitializeComponent; никаких циклов, условного UI, LINQ, lambdas, I/O, async, DI и вспомогательных фабрик controls. Не переносить форму в runtime BuildUi.

## Проверки и Git
Сначала docs/DESIGN_RU.md, docs/SOURCE_AUDIT_RU.md, reports/BASELINE_VERIFICATION.md и выбранная задача.
Минимум scripts/Build-Verify.ps1 + реальные targeted проверки задачи. Не запускать Ant/JAR/сервер/БД внутри L2J и не гонять unrelated suites. PSS-003 разрешает отдельный operator scripts/Test-PSS003-Java.ps1: только физический shadow в workspace Studio, narrow copy allowlist, штатный DB-free content target и explicit loadV3(custom=true), все outputs/temp внутри shadow. GUI никогда не запускает Java/Ant/shell.
Git только в собственном root программы. Сохранять user dirt. Без reset/clean/stash/rebase/amend/force и git add . .
Не использовать origin kpCat/L2J для нового приложения. Если собственного origin нет — PUSH_NOT_CONFIGURED с честным отчётом.

## Ревью результатов Codex
Никаких review ZIP, source ZIP, review bundle или архивов кода/сгенерированного XML от Codex. Не запускать scripts/New-ReviewBundle.ps1. Ассистент проверяет результат непосредственно в публичном GitHub kpCat/PhantomSemanticStudio: origin/main, exact commit/diff, точный inventory, отчёт и журналы тестов. Переданный пользователем task ZIP служит только для инструкций и не является архивом ревью.
Пользовательская функция REVIEW_ONLY JSON ZIP в приложении сохраняется: она отдельна от code review и не устанавливает XML. Исторические отчёты/архивы не переписывать и не удалять без отдельной задачи.
