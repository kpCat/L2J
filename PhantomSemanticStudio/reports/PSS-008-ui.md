# PSS-008 — UI evidence and manual gate

Технология: .NET10 WinForms, standard controls. Две отдельные Designer-формы: PackQualityForm и V3ProposalForm, каждая с .cs/.Designer.cs/.resx, nesting в существующем csproj. Конструкторы содержат только InitializeComponent. MainForm сохраняет шесть вкладок и существующий custom staging.

| Gate | Фактический статус |
|---|---|
| Static Designer / events / ctor / resx / nesting | PASS, Verify-PSS008 -StaticOnly |
| Новые STA controls | PASS2/0, реальная WinForms message pump, synthetic fixture |
| Legacy STA004/005/006/007 | PASS4/0,3/0,2/0,7/0; отдельные final stdout, не physical acceptance |
| Физический UI / 100% DPI | NOT_TESTED |
| Физический UI / 150% DPI | NOT_TESTED |
| Visual Studio Designer round-trip | NOT_TESTED |
| Live Gemma | BLOCKED_LM; lms ps --json вернул []; POST0 |

STA проверяет реальные controls: read-only quality/details, async Analyze, фильтры, Cancel и сохранение предыдущего результата, resize до MinimumSize без пересечения основных зон. Proposal по умолчанию не имеет выбранной manifest pair; все три consent NO, Stage/Release disabled. Реальный modal StageSelectionForm выбирает ровно два CURRENT APPROVED через CheckBox/ListView; полный текст виден в preview. Изменение pair сбрасывает подтверждения. Stage → отзыв editorial consent отменяет worker и оставляет zero finished output. Новое явное подтверждение → real Core stage, receipt2, Java NOT_RUN, source SHA неизменны; consents снова NO и Release disabled.

Static gate проверяет publication proof/hash/preview после обоих awaited validations и последнего cancellation check. Это проверка исходника; race при отмене второго proof await не объявляется физически воспроизведённым UI smoke. Genuine native proof и offline handoff проверены отдельно настоящим Core operator route, не OpenFileDialog automation.

Native computer surface в этом сеансе недоступна. STA не показывает читаемость на реальном мониторе и не подтверждает Visual Studio Designer. Глобальные DPI/Windows/VS настройки не менялись; screenshots не изготовлялись.

## Обязательный ручной checklist оператора

1. Открыть приложение, импортировать исходник read-only. На «Библиотека» открыть карту качества. Проверить declared empty scopes, filters/topic/act/source, сортировку чисел, trace, capacity и advisory статусы. Запустить Analyze и Cancel; сменить фильтр/источник и убедиться, что поздний результат не перерисовывает новый контекст.
2. На «Ревью / экспорт» открыть предложение v3. Проверить default NO, пустую pair, disabled Stage. Выбрать 1–20 CURRENT APPROVED вручную, затем pair и полный exact preview. Переключение pair должно снова снять два подтверждения. Несовместимый batch должен отвергаться целиком. Проверить отмену и отсутствие source/session изменений.
3. Создать только own finished stage. GUI показывает STAGED_V3_UNVALIDATED / Java NOT_RUN / NOT_INSTALLED. Вручную отдельно выполнить operator Java script; не запускать его из L2J. Выбрать его java-validation.json через OpenFileDialog. Дождаться полного preview exact file/original/proposed/proof SHA. Failed/unsigned/altered proof не должен открывать release gate.
4. Третий consent NO до ручного review. После явного подтверждения создаётся только offline proposed/backup/manifest/checklist. Проверить байтовые backups, NOT_INSTALLED и отсутствие установщика/команд установки. Не применять к L2J.
5. При 100% и 150% DPI проверить обе формы и MainForm: длинные ID/path/hash/русский текст, minimum size, resize, прокрутки, TAB order, доступность Cancel и checks, отсутствие перекрытий. Не менять DPI автоматически.
6. Открыть MainForm, PackQualityForm и V3ProposalForm в VS Designer, проверить resx/nesting, сделать безопасный round-trip без переноса layout в runtime. Статус PASS ставит только оператор после реальной проверки.
7. Когда пользователь сам загрузит нужную Gemma и разрешит один tiny запрос, проверить существующий conversation route с review/consent. Не загружать модель автоматически. Реальные сообщения/ответы не публиковать.

STOP после PSS-008. Этот checklist не принимает manual gate от имени пользователя и не разрешает установку.
