# PSS-001 — UI evidence

- Visual Studio Enterprise 2026 18.0.11205.157, установленная .NET desktop среда. Пользователь открыл PhantomSemanticStudio.sln в отдельном окне.
- Designer: USER_VERIFIED. Прямое сообщение пользователя: «Да, шесть вкладок видны; сохранение и повторное открытие работают». Это свидетельство ручного round-trip, а не автоматический визуальный тест Codex.
- Standard MainForm.Designer.cs и MainForm.resx сохранены; constructor только InitializeComponent. Static Verify-Designer: PASS.
- Runtime: отдельный PhantomSemanticStudio.exe реально запущен через Computer Use. Accessibility tree показал Settings, все шесть tabs, точные source/endpoint/model defaults, собственный LocalAppData workspace и статус «Настройки готовы; импорт запускается вручную». Ctrl+Tab переключил вкладку на «Конструктор». Приложение закрыто Alt+F4; повторный list_windows подтвердил исчезновение окна.
- Runtime import/library/candidates/lesson/export взаимодействия: NOT_TESTED. Click не выполнялся из-за `coordinate input geometry is unavailable`; capture Visual Studio вернул `FrameArrived timed out` / `window capture timed out`. Доступен текст controls, но он не доказывает визуальный layout.
- 100% DPI visual smoke: NOT_TESTED. 150% DPI visual smoke: NOT_TESTED. Resize/overlap/clipping: NOT_TESTED. Масштаб Windows не менялся.
- Save/Discard/Cancel и post-current-cell guard: реализованы, но полный интерактивный regression маршрут не запускался. Нет заявления UI PASS по успешной сборке.

Свежий code review выявил потерю правки на SelectionChanged до обновления CurrentRow при Tab из последнего столбца. Исправлено: CurrentCellChanged, same-ID no-op, отложенный BeginInvoke для save/rebind/restore после завершения события. Подтверждение API: [Microsoft CurrentCellChanged](https://learn.microsoft.com/en-us/dotnet/api/system.windows.forms.datagridview.currentcellchanged?view=windowsdesktop-10.0), [SelectionChanged ordering](https://learn.microsoft.com/en-us/dotnet/api/system.windows.forms.datagridview.selectionchanged?view=windowsdesktop-10.0). Это исправление проверено сборкой/static contract; UI regression остаётся NOT_TESTED.

Независимое внешнее принятие: PENDING_INDEPENDENT_REVIEW. Code review subagent не является финальным пользовательским acceptance.
