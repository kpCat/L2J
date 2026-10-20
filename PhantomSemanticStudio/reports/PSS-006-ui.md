# PSS-006 — UI evidence, 2026-10-08

**STA_CONTROLS_PASS / DESIGNER_STATIC_PASS. Physical UI, DPI_100, DPI_150 и VS_2026_DESIGNER_ROUND_TRIP: NOT_TESTED. Manual acceptance REQUIRED.**

Технология .NET 10 WinForms, standard controls. MainForm сохраняет шесть вкладок, новая static кнопка «Корпус чатов» на «Настройки» открывает отдельную ChatCorpusForm с workspace dependency через SetWorkspace после конструктора. Layout/создание controls/events только в InitializeComponent внутри Designer.cs. Оба конструктора только InitializeComponent; .resx и csproj nesting проверены. MainForm/StageSelectionForm прежние static gates также прошли.

## Проверки настоящих controls

Команда `dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-006-controls`: [final](PSS-006-controls-final.txt), **2 PASS / 0 FAIL, exit 0**. Существующий console runner запускает STA, показывает настоящие формы и вызывает только public controls; никакого shell reflection harness/private methods.

- A: synthetic source 7500 TEMPLATE; сохранённая evidence, selection dispatch RED 277 ms → GREEN final 76 ms (assert <150 ms); Cancel доступен. Два быстрых изменения не допускают late repaint прежнего кандидата; cancel, invalid UTF-8 и close дают STALE/без crash. session bytes unchanged. Это latency на fixture/STA, не доказательство живого mouse scenario на пользовательском паке.
- C: импорт synthetic 205 public lines, 100 rows/page, ровно 20 explicit отметок, 21-я отклоняется явно. Фильтр no-match скрывает строки, сохраняет только ручные отметки и полный правый preview; следующий page содержит 100. Минимальный и исходный размеры проверены public Bounds/ClientRectangle, critical controls внутри parent, list/selected не пересекаются. Cancel и close во время 100k import оставляют прежний committed corpus; session unchanged.
- Regression PSS-005: [3 PASS / 0 FAIL](PSS-006-controls-regression.txt), exit 0, semantic buttons/layout, offline report, approval/edit reset, physical fixture source drift/invalid UTF-8. Это controls regression, не физический desktop smoke.

Первый Search в STA route выполняется через public AccessibilityObject.DoDefaultAction: PerformClick в этой test message-loop ситуации не вызывал Click при видимой/активной кнопке. Последующие Search/Next и Import/Cancel используют PerformClick. Не менялись CausesValidation/валидация продукта ради теста; keyboard/mouse action этим не доказан.

## Physical UI / Designer limitations

Синтетический interactive route `--pss-006-interactive` импортирует 205 вымышленных строк в собственный ignored workspace и Application.Run отдельной формы. Actual ZIP/model/source calls=0. Sandbox process сначала не был targetable; собственный exact process завершён. Повтор с разрешённым desktop access дал targetable окно «Корпус чатов — приватный локальный анализ».

Computer Use (bundled @oai/sky): list_windows обнаружил окно, get_window_state → **FrameArrived timed out**. Повтор выбора/activate/capture → **window capture timed out**. Inputs не продолжались после второго отказа; собственный exact dotnet smoke PID завершён с повторной проверкой command line. Screenshot, mouse/keyboard и DPI не проверены. Настройки Windows/масштаб/безопасность не менялись.

Visual Studio найден только с чужим открытым проектом MKR. Studio solution/ChatCorpusForm в VS не открывались/не сохранялись/не переоткрывались. **VS round-trip NOT_TESTED**, наличие VS или успешная сборка не заменяет Designer proof. Статический [Verify-PSS006](PSS-006-designer.txt) exit 0 проверяет constructors/layout/events/nesting/resx, не запуск VS.

## Один manual gate для пользователя

1. Запустить Studio; «Настройки» → «Корпус чатов». Выбрать локальный ZIP стандартным диалогом; импорт, progress, Cancel и повторный import должны отвечать, прежний corpus остаётся доступен. Не загрузить модель автоматически.
2. Проверить channel/date/language/length/duplicate/noise/search и Previous/Next; показывается <=100 строк, статистика меняется. Отметить 1–20 public excerpts, проверить полный правый preview/hidden marks; 21-й явно блокируется, другой corpus очищает выбор. Тексты всегда SOURCE_MATERIAL_ONLY; нет кнопки training/approval/XML.
3. На физических 100% и 150% DPI проверить minimum/maximized/resize, доступность controls/captions/preview, мышь/клавиатуру. Затем в VS2026 открыть ChatCorpusForm в Designer, сохранить, закрыть, переоткрыть; проверить MainForm и шесть вкладок. Не считать этот gate принятым автоматически.
4. Проверить несколько быстрых selections stored evidence: pending STALE, актуальный advisory только для выбранного кандидата, edit/cancel/source drift не дают false CURRENT. Real Gemma остаётся отдельным manual live gate.

Упомянутый пользователем dotnet.exe crash произошёл в первоначальном ошибочном synthetic fixture: XML segment был записан не по тому пути. Исправлен путь fixture; настоящий RED затем получен отдельно. Windows dialog закрыт пользователем. Это не успешный тест и не изменение пользовательского ZIP/L2J. Промежуточные build lock errors собственного running smoke не являются final Release result.

STOP после PSS-006; PSS-007 не начат.
