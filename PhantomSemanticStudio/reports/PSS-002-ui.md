# PSS-002 — UI evidence, 2026-10-08

**Runtime UI: PARTIAL_STARTUP_ONLY. Полный interaction gate: NOT_TESTED. 100% DPI: NOT_TESTED. 150% DPI: NOT_TESTED.** Масштаб Windows не менялся.

- Технология: .NET 10 WinForms, standard controls, PerMonitorV2, AutoScaleMode.Font. Constructor только InitializeComponent. Mode selector/Items/SelectedIndex=0 и все layout/events описаны статически в MainForm.Designer.cs.
- Final Build-Verify: static Verify-Designer PASS, exit 0, 6 designed tabs. Это не Visual Studio Designer round-trip. Новый Designer open/save/close/reopen: NOT_TESTED; PSS-001 USER_VERIFIED — историческое свидетельство, не текущий PASS.
- Реально запущена собранная форма, process 20012. Process-only PSS_UI_SMOKE_WORKSPACE указывает на C:\Users\ZBook\PhantomSemanticStudio\artifacts\PSS-002-ui-smoke\workspace. MainForm проверяет принадлежность artifacts собственной source-сборки, затем обычный WorkspaceStore применяет source/disjoint/reparse guards. Пользовательский LocalAppData workspace не использовался.
- Две искусственные DRAFT fixtures созданы только для smoke; это не ответы Gemma. Accessibility чтение показало Settings, правильные source/endpoint/model defaults, isolated workspace и статус «Настройки готовы; импорт запускается вручную»; шесть вкладок присутствовали в tree.
- Нажатие Import через Computer Use не выполнено: `coordinate input geometry is unavailable`. Screenshot: `FrameArrived timed out: timed out waiting on channel`; после обновления списка/окна одна recovery-попытка: `window capture timed out: timed out waiting on channel`.
- Shift+Tab и Ctrl+Tab были отправлены, но повторное наблюдение показывало прежнюю вкладку/фокус. Клавиатурный переход не доказан; PASS не присвоен. После завершения собственный smoke process остановлен по exact PID и проверенному exe path.

| Маршрут | PSS-002 evidence |
|---|---|
| Startup/settings/изоляция | PARTIAL: реальное окно и accessibility tree |
| UI Import/library/filter/selection | NOT_TESTED; targeted Core import 65 files отдельно PASS |
| Dialogue preview/Teach/ApplyLesson | NOT_TESTED интерактивно; scoped lesson persistence/scope assertions в console suite PASS |
| Type selector и генерация | Static Designer + HTTP/core contract PASS; выбор в реальном UI и live inference NOT_TESTED/BLOCKED_LM |
| Candidate keyboard selection/Save/Discard/Cancel/tab close | NOT_TESTED; существующий deferred CurrentCellChanged guard сохранён |
| Manual approval/review export/source drift | Core regression PASS, реальный UI NOT_TESTED |
| Resize/clipping/overlap/100%/150% physical DPI | NOT_TESTED: capture/input ограничения |

Read-only reviewer выявил collapse txtInstruction при новой строке type и минимальной высоте. Исправлено в Designer: topic/type расположены рядом, прежние 142px редактора восстановлены. Verify-PSS002 -LayoutOnly: RED exit 1 (0px), GREEN exit 0 (62px при консервативном non-client allowance 40px). Это статический sizing guard, не отрисовка и не доказательство 150% DPI.

Операторский gate: открыть MainForm в Designer, сохранить и повторно открыть; в изолированном workspace вручную пройти import → library → preview → teach/apply → type/generation → candidate edit; проверить клавиатурную смену строки с Save/Discard/Cancel, переключение вкладки/закрытие, approval и JSON review export с source drift. На уже настроенных 100% и 150% Windows проверить уменьшенное окно, labels/dropdowns/scrollbars/clipping. Глобальный DPI автоматически не менять. Статусы обновлять только после фактических наблюдений.

Independent acceptance: PENDING_INDEPENDENT_REVIEW. Java NOT_RUN, XML/install отсутствуют.
