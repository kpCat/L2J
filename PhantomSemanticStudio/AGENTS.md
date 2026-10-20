# Правила работы с Phantom Semantic Studio

Рабочий root — `PhantomSemanticStudio/` внутри общего репозитория kpCat/L2J. Основная ветка разработки и публикации — `feature/phantom-world`. Отдельный исторический origin сохранён в `IMPORT_ORIGIN.md`; основная дальнейшая разработка ведётся здесь. Сначала прочитать корневой `../AGENTS.md`.

## Граница источника и workspace

Соседний `../L2J_Mobius_CT_2.6_HighFive/` — серверный источник только для чтения. Studio не устанавливает и не перезаписывает Semantic Pack, конфиги, базы или серверные файлы. Runtime-защита распространяется на весь Git root L2J. Все пользовательские записи остаются во внешнем `%LOCALAPPDATA%\PhantomSemanticStudio\workspace`; source/workspace disjoint и reparse guards обязательны.

Изменение исходников самой Studio в этом репозитории разрешается явной задачей разработки. Это не разрешение runtime-программе записывать в L2J. Не изменять соседний сервер, релизы или БД без отдельно указанного scope.

## Безопасность программы

- Нет метода/кнопки установки, автопубликации, overwrite core/custom, auto-approve или auto-repair.
- JSON review export остаётся REVIEW_ONLY. XML-предложение создаётся только в физической копии workspace и остаётся STAGED_UNVALIDATED / Java NOT_RUN до отдельной реальной проверки.
- PASS_JAVA_STAGED означает только content gate конкретной копии; не готовность установки или игрового runtime.
- Модель не получает tools/filesystem и не генерирует исполняемый код. Сохраняются bounded JSON, ручной review, отмена и отсутствие автоматических повторов.
- Правка отменяет approval; approval связан с content hash и source fingerprint. Автоматического rebase кандидатов нет.
- Не скрывать пропущенные проверки под GREEN, не создавать второй Semantic Engine.

## WinForms

Существующие Form.cs / Form.Designer.cs / Form.resx и standard controls. Constructor — только InitializeComponent, без I/O и зависимостей. Layout/events — статически в InitializeComponent; runtime/async — в основном .cs. Не заменять Designer runtime-фабрикой controls и не менять UI в задаче переноса.

## Проверки и Git

Сначала README_RU.md, релевантный код и task. docs/DESIGN_RU.md описывает текущие границы; docs/SOURCE_AUDIT_RU.md и reports/BASELINE_VERIFICATION.md — исторический исходный аудит, не текущая приёмка.

Минимум scripts/Build-Verify.ps1: Release solution, Verify-Designer и существующий консольный test runner. Debug solution, --task062 и --pss-010 выполняются дополнительно для TASK062. Исторические Verify-PSSxxx и GitScope-checks привязаны к старым checkpoint/commit и не заменяют текущую проверку монорепозитория.

Java запускается только явно оператором в отдельном shadow через Test-PSS003-Java или Test-PSS008-V3-Java. Путь stage — вне защищённого L2J. Pinned build/copy allowlist нельзя молча расширять или перепривязывать: несовместимость даёт BLOCKED_JAVA. GUI никогда не запускает Ant/Java/shell. Серверы/БД/LM для обычных offline tests не нужны.

Git подчиняется корневому AGENTS и scope текущей задачи. Commit/push разрешены только задачей, публикация — ordinary non-force в feature/phantom-world. Сохранять пользовательский dirt; без clean/reset/stash/rebase/amend/force/add . .

Не создавать review ZIP/source ZIP и не запускать New-ReviewBundle.ps1 для ревью кода. Проверяемые commits публикуются в kpCat/L2J с точным scope и отчётом. Пользовательская REVIEW_ONLY JSON ZIP-функция приложения сохраняется. Исторические отчёты/архивы не переписывать.
