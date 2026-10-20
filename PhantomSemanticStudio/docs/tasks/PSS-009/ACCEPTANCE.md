# PSS-009 — acceptance, статусы и блокировки

## Обязательные для GREEN_LAYOUT_IMPLEMENTATION

- Git initial base == cddf9a65ffc5b052c110b264c4fe3973f8e6a37c; правильный репозиторий и exact diff.
- До исправления собственный reproduced failing geometry assertion (или очень конкретное, честное описание невозможности автоматизации, тогда нельзя говорить `ROOT_CAUSE_RUNTIME_PROVEN`).
- Исправлены все 6 MainForm tabs. `btnPackQuality`, `btnGenerate`, `btnPreview`, `btnDialogueLab`, `btnV3Proposal` и критические editor/settings actions доступны при первом и повторном выборе вкладок в реально созданной WinForms форме. Нет уходов за viewport вправо/вниз без достижимого scroll. `tabCandidates` не ухудшен.
- Реальные STA geometry assertions + негативные проверки возвращают PASS; проверяются не только созданные модальные окна, но и main TabControl / TabPage.
- `Build-Verify` Release **>=122 прошлых тестов сохранены, 0 failure, 0 warnings/errors**; новые focused counts отдельно. Все STA004/005/006/007/008 unchanged или 0 FAIL. Static Designer/build/UTF8 check отдельно.
- Сохранены controls names, event handlers, 6 tab-pages, `InitializeComponent` без cycles/IO/async, редактируемый VS Designer source/resx nesting.
- Нет L2J/git/java/data writes, private screenshot/PII/raw corpus/model data вне public Git.
- Ordinary commit и non-force push Studio origin/main, remote SHA == local и clean state.

## Не выдавать за результат автоматических тестов

- `PHYSICAL_UI_VERIFIED`: только при настоящем визуальном наблюдении/screenshot реального desktop и мыши/клавиатуры; если недоступны — `PHYSICAL_UI_NOT_TESTED`, а в ручном checklist перечислить вкладки и размеры.
- `DPI_100_VERIFIED` и `DPI_150_VERIFIED`: только после реальной физической проверки; изменение WindowSize/шрифта или умножение контрольных координат не равно физическому DPI.
- `VS_DESIGNER_ROUNDTRIP_VERIFIED`: только после фактического VS2026 Designer открыть/save/reopen + build; static verification не подмена.
- `LIVE_LM` / `JAVA_CONTENT`: не требуют запуска, пометить NOT_RUN/BLOCKED_LM без наследования PSS008 PASS.
- Если даже геометрия основного окна остаётся неверной — `FAILED_LAYOUT`/`BLOCKED`, не GREEN, независимо от 122/0.

## Отчёты

`reports/PSS-009-plan.md`, `reports/PSS-009-final.md`, `reports/PSS-009-ui.md`, `reports/PSS-009-owned-files.txt`; агрегированные `reports/PSS-009-layout-red.txt`, `reports/PSS-009-layout-green.txt`, `reports/PSS-009-build-final.txt`, и добросовестные legacy outcomes. Все реальные пути/скриншоты/параметры моделирования остаются в ignored artifacts; публично только synthetic controls, safe geometry values.
