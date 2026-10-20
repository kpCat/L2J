# PSS-009 — восстановление видимого интерфейса WinForms / критический UI-layout fix

## Цель и запрет иллюзорного GREEN

Пользователь **ЛИЧНО запустил текущую программу** и обнаружил, что основные элементы пяти из шести вкладок (в особенности «Конструктор», «Диалог и обучение», «Библиотека», «Экспорт для ревью») уходят вправо и вниз за пределы окна. Кнопка «Карта качества / ёмкость (advisory)» в библиотеке невидима. Это реальная пользовательская регрессия, которую ранее не нашли 122 console PASS, static Designer и STA тесты отдельных модальных окон. Исправить компоновку, а не скрыть симптом/контролы.

**Это ровно одна новая PSS-009**, уровень reasoning HIGH, основная Codex (НЕ Spark), новый диалог. Не требовать повторного согласования и не начинать PSS-010. Внутренние gates A/B/C, в каждом RED/GREEN, итоговый независимый review.

Repo root `C:\Users\ZBook\PhantomSemanticStudio\`; required base / origin/main **cddf9a65ffc5b052c110b264c4fe3973f8e6a37c**; единственный origin `https://github.com/kpCat/PhantomSemanticStudio.git`. Пользовательская локальная ветка обычно master: НЕ переписывать её; publish `git push origin HEAD:refs/heads/main` non-force. Remote drift => BLOCKED_REMOTE.

## Read-first до edit

1. `AGENTS.md`, `README_RU.md`, `reports/PSS-008-final.md`, `reports/PSS-008-ui.md`, `reports/PSS-008-review.md`, `reports/PSS-008-owned-files.txt`.
2. Каждый документ пакета `docs/tasks/PSS-009/*`, контрольные суммы `PACKAGE_MANIFEST.json`.
3. **Целиком** `src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs` и релевантный `MainForm.cs`, `MainForm.resx`, `Program.cs`, `ApplicationConfiguration`/csproj; `scripts/Verify-Designer.ps1`, `scripts/Build-Verify.ps1`, `scripts/Verify-PSS008.ps1`, legacy `Pss004/005/006/007/008Controls.cs`.
4. Designer/код **всех пяти** других окон: `StageSelectionForm`, `DialogueLabForm`, `ChatCorpusForm`, `PackQualityForm`, `V3ProposalForm`. Сверить `AutoScaleDimensions/Mode`, layout parent/child, Anchor/Dock, MinimumSize, tab page initialization, tab order/long strings.
5. Read-only Git SHA/status/origin/branch; не изменять пользовательский workspace. Запись в L2J или запуск Java/Ant/GameServer/DB там категорически запрещены.

## Gate A — доказать первопричину на живых WinForms controls

Создать собственный STA layout test / probe, работающий в WindowsDesktop runtime, на РЕАЛЬНОЙ форме `MainForm` (через публичные WinForms controls, без mock HTML/рисования). Запускать под process-only `PSS_UI_SMOKE_WORKSPACE` с отдельным ignored workspace и synthetic/no-L2J content; никакой записи в пользовательский `%LOCALAPPDATA%` и в L2J.

**До исправления** воспроизвести RED: создать форму, показать, пройти все 6 TabPage, DoEvents/PerformLayout, измерить `TabControl.DisplayRectangle`, `TabPage.ClientRectangle/Bounds`, детей, базовый DPI/Font, и сохранить агрегированный sanitized diagnostic (без имен пользователя, токенов, raw text). Обязательно локализовать первый неверный layout transition. Исходная гипотеза в BASELINE_ROOT_CAUSE.md, НО не принимать её без подтверждения. Если редкий race, зафиксировать before/after switch и WindowState.

## Gate B — минимальный поддерживаемый VS Designer fix

Исправить инициализацию размеров TabPage и/или соответствующих контейнеров **до anchor layout**, обеспечить устойчивые рабочие размеры и порядок Dock/Anchor при first show, tab switching, restore/maximize/resize и смене DPI/Font. Исправить все 6 страниц; кнопки в неактивных вкладках должны стать видимыми при первом открытии, без изменения смысла исходников.

Поддерживать стандартные редактируемые WinForms `*.cs`, `*.Designer.cs`, `*.resx`; public parameterless ctor **ТОЛЬКО `InitializeComponent()`**. В `InitializeComponent()` только статическая явная designer-инициализация, `SuspendLayout/ResumeLayout`, `Controls.Add`, стандартные свойства. **НЕТ** loops/if/LINQ/async/factories/I/O/runtime `BuildUi`. Допустимы Designer-native `TableLayoutPanel`, `SplitContainer`, `Panel` и `AutoScroll` только если обоснованы, не ломают VS designer и тестами доказано, что скролл доступен. Не оборачивать всё тупо в один огромный scrollable Panel и не пытаться решить проблему увеличением окна или шрифтом 8pt.

Все existing buttons/events/status/DB/LM/corpus/approval/staging/Java/export semantics сохранять. No new NuGet, no background LM requests, no new tabs/features, no changes Core/Java unless demonstrably required for UI-only test fixture. Проверить окно «Настройки» и новый набор модальных форм также на clipping/overlap/scroll.

## Gate C — настоящие regression и честная ручная приёмка

1. Сделать воспроизводимый GREEN в `--pss-009-layout`, проверить все 6 вкладок **после первого переключения**, после повторного, при minimize/restore/maximize и разных размерах. Assert actual parent/client geometry + отсутствие критических пересечений, а НЕ grep `.Size` и НЕ тест лишь одного tabCandidates. Доказать, что `btnPackQuality`, `btnSearch`, генераторные кнопки, `btnPreview`, `btnTeach`, `btnDialogueLab`, `btnV3Proposal`, редактирование кандидатов, buttons настроек достижимы мышью/клавиатурой.
2. Проверить `1280x720`, `1366x768`, `1920x1080` как экран/viewport targets и DPI `100%`, `150%` (по возможности ещё текущий user DPI и 125%). На маленьком viewport допустима реальная прокрутка только если все критические элементы доступны. **НЕ** менять глобальное Windows DPI/дисплеи/чужие приложения для тестов без разрешения. Если физические DPI недоступны, зафиксировать `PHYSICAL_DPI_NOT_TESTED`, но выполнить тесты логических размеров, не называя их физическими.
3. Реальная физическая ручная/Computer Use screenshot/click проверка, если среда предоставляет desktop. Если capture/coordinates недоступны — ограничить попытки, не выдумывать PASS; предоставить оператору понятный manual checklist по всем вкладкам и отдельным диалогам. Static Designer PASS никогда не заменяет реальный VS2026 Designer open/save/reopen.
4. `scripts/Build-Verify.ps1` на финальном коде и legacy targeted controls `--pss-004/005/006/007/008-controls`; C# Release 0 warnings/errors. Новый focused red→green, negative layout regression (temporary test mutation only, затем восстановить), новая static guard на 6 страниц. Проверить `MainForm()`/другие ctors и `.resx`/csproj nested.
5. Privacy/encoding/scope verification: UTF-8 (mojibake marker и escaped Cyrillic отдельно), никаких private corpus/SQLite/ZIP/PII/screenshots/private paths в публичном Git. Legacy semantic/source/staging contract неизменен. В L2J разрешено максимум read-only path metadata для workspace guard; content scan/Ant/Java/изменение файлов не нужно.

## Приёмка и Git

Результаты и формальные статусы по `ACCEPTANCE.md`. В итоговом отчёте НЕ путать `LAYOUT_CONTRACT_PASS` с `PHYSICAL_UI_VERIFIED` и `VS_DESIGNER_ROUNDTRIP_VERIFIED`. Даже если UI automation не позволяет screenshot, Codex должен сделать максимально точный реальный STA layout regression и не скрывать блокировку.

Выход: `reports/PSS-009-plan.md`, `reports/PSS-009-final.md`, `reports/PSS-009-ui.md`, `reports/PSS-009-owned-files.txt`, агрегированные RED/GREEN stdout/layout diagnostics и обновлённый README при необходимости. В отчёте указать точные контролы/bounds, root cause, список новых проверок, legacy counts и отдельно manual gates. Task ZIP содержал только задачу; **Codex НЕ создаёт review/source ZIP** и не запускает `New-ReviewBundle`.

Можно несколько обычных локальных checkpoint commits, но **обязателен ordinary commit + non-force push origin/main**, сверка local/remote/public SHA, clean tree. Только exact `git add -- <own inventory>`, no force/reset/clean/stash/rebase/amend/broad `git add .`; ни Git, ни записи в L2J. При неустранимой проблеме — честный BLOCKED checkpoint и безопасный push результата. STOP сразу после PSS-009.
