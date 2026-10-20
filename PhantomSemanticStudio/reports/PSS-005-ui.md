# PSS-005 — UI evidence

**DESIGNER_STATIC_PASS. CONTROL_CONTRACT_PASS: 3 PASS / 0 FAIL, exit 0. UI NOT_TESTED, DPI_100 NOT_TESTED, DPI_150 NOT_TESTED, VS_DESIGNER NOT_TESTED.** Manual acceptance автоматически не принят.

Две standard Button в MainForm.Designer.cs: «Найти похожие (локально)» и «Оценить смысл (Gemma)». Constructor только InitializeComponent, шесть вкладок, MainForm.resx/projects без изменений. Полная readonly прокручиваемая txtValidation сохраняет высоту 255; editor и review-note размеры сохранены. MaxLength=0 предотвращает обрезание полного отчёта. Размер tabCandidates задан статически 1252×712: hidden TabPage ранее сохранял default 200×100 anchor baseline.

## Actual control route

`dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-005-controls`

Existing console/STA pattern: public operations реальных Form/controls, WindowsDesktop runtimeconfig и Application.DoEvents. Не fake UI controls и не вызов private handlers через shell. Synthetic source в собственном temp, workspace только artifacts/PSS-005; пользовательский workspace не используется. LM endpoint fixture port 1; модельная кнопка в этом route не нажимается. Prefilled advisory искусственный, не live Gemma.

- Geometry в minimum и original resized размере: обе кнопки полностью внутри вкладки, не пересекаются друг с другом/validation. Изначальный RED обнаружил anchor overflow (right X=1960, Y=1173 при parent 1252×633); explicit candidate tab size исправил причину. Это геометрия текущего окружения, не физические 100%/150% DPI.
- Import → selected candidate → actual offline PerformClick: specific source RefKey/text и advisory/coverage появляются без сервера, session bytes/source stamps равны. Editor изменение немедленно показывает STALE; Save actual click создаёт DRAFT, очищает evidence/approval штатным Edit.
- Внешняя правка synthetic source → переключение actual grid CurrentCell → STALE. Invalid UTF-8 bytes → выбор saved advisory: независимый RED Exception → DecoderFallbackException catch → GREEN STALE без crash. Approved session/approval hash сохранены; fixture восстановлен.

Evidence: controls-red, controls-diagnostic, review-controls-red и final controls.txt. Static guard review-display-red → designer.txt подтверждает удаление unconditional current=true после свежего ShowCandidate; не утверждается deterministic runtime race test между save/rebind.

## Computer Use / interactive

Прочитан computer-use SKILL.md, guidance/api/confirmations. @oai/sky инициализирован; LM Studio UI window найден. После activate/get_window accessibility tree доступен, screenshot: FrameArrived timed out. Fresh list_windows/get_window recovery: window capture timed out. Продолжение по старым координатам не выполнялось.

Own WinForms executable запущен с process-only PSS_UI_SMOKE_WORKSPACE, synthetic fixture: PSS-005-ui-fixture.txt. Hidden startup PID 31452 не предоставил targetable окно (два list_windows, MainWindowHandle=0). Он завершён после проверки exact executable path. Пользовательские приложения не закрывались; synthetic candidate остался APPROVED, semantic evidence отсутствует. Интерактивные import/local/Gemma/cancel/edit clicks, keyboard navigation, physical DPI visual QA и VS Designer round-trip не выполнены. Screenshot файлов нет, fake PASS нет.

## Ограничения

Выбор saved evidence синхронно читает pack и выполняет scout дважды на UI-потоке: deferred Minor responsiveness, Cancel недоступен на время этого refresh. Явные кнопки используют RunAsync/Task.Run/cancellation. ResolvePendingEdit Save/Discard/Cancel сохранён; реальные человеческие clicks в диалоге не проверены.

Live HTTP отдельно: один synthetic POST завершился BLOCKED_LM / BAD_RESPONSE, 119 секунд, без новой evidence и с unchanged session bytes; повторов нет. Сервер JIT загрузил exact выбранную модель; `lms unload gemma-4-26b-a4b-it-ultra-uncensored-heretic` exit 0, `lms ps --json` exit 0 → `[]`. UI/quality/live PASS из этого не следует. Java NOT_RUN, XML не опубликован, staging/selection gates сохранены.
