# PSS-004 — безопасность, границы и Git

## Strict readonly

Protected root `C:\Users\ZBook\L2J_Mobius\` включая `L2J_Mobius_CT_2.6_HighFive` — **только чтение**. Никаких writes/renames/moves/temp/obj/build/git/Ant/server/DB/client внутри protected root. Тестовые stage создаются только под собственным Studio workspace/temp/artifacts; не удалять другие stages, не чистить пользовательский workspace. Не менять `.phantom-local`, другие хроники, configs и production DB.

## Static Designer

Любое изменение MainForm/StageSelectionForm: `.cs/.Designer.cs/.resx`, стандартные UI-контролы, конструктор только `InitializeComponent`, никакого runtime BuildUi и логики/циклов/IO/async/LINQ/factories в InitializeComponent. В новом диалоге Codex можно провести UI test, но нельзя объявить физический 100%/150% DPI по static Layout-only.

## Внутренние статусы

Core/GUI не выполняют Java/Ant/process/git, не публикуют XML в High Five, не меняют v1/v2/v3/custom. Только `IsolatedPackStager.Create(...)` (уже реализован) после явного выбора. Staging receipt `STAGED_UNVALIDATED`; operator `Test-PSS003-Java.ps1` исключительно внешний и вне scope новой кнопки. Не подменять статус Java по существованию старого scratch. Модель не получает файловых tools и не одобряет результат автоматически.

## Git

Repository root: `C:\Users\ZBook\PhantomSemanticStudio\`. Remote **только** `https://github.com/kpCat/PhantomSemanticStudio.git`, целевая `refs/heads/main`; локальная ветка `master` допустима. Before edits: `git status --porcelain=v1 -uall`, `git rev-parse --show-toplevel`, `git rev-parse HEAD`, `git branch --show-current`, `git remote -v`, `git ls-remote origin refs/heads/main`; BASE==`edc41128270c14578b9edf748a3c8bc0c6196623`. Никакого push в `kpCat/L2J`.

Не выполнять `reset/clean/stash/rebase/merge/amend/force`, массового `git add .`, global `safe.directory`, переписывания истории и удаления чужих файлов. Сохранять user dirt. Точные changed files по `reports/PSS-004-owned-files.txt` и `git diff --check`; `git add -- <exact paths>`; `git diff --cached --check`; обычный `git commit` и **`git push origin HEAD:refs/heads/main`**. Дождаться успеха, сверить `git ls-remote origin refs/heads/main` с local SHA, финальный `git status --porcelain=v1 -uall`. Если remote moved — stop `BLOCKED_REMOTE`, **не** forced push/rebase. Даже при BLOCKED — оставить безопасный отчёт и выполнить разрешённый обычный commit + попытку non-force push, если нет remote-конфликта.

Codex **не создаёт review ZIP / source ZIP / review bundle**. Ассистент проводит независимую ревизию только публичных commit/diff/report/evidence. Пользовательская функция JSON review ZIP приложения не удаляется и не запускается автоматически при сдаче.
