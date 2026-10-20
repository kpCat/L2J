# PSS-007 — review and rulings

Один свежий read-only whole-change reviewer, gpt-6-astra HIGH, по executing-plans/requesting-code-review. Он прочитал task, baseline, новые Core/UI/tests и bounded diff; файлов/корпуса не менял, сборки параллельно не запускал. Вердикт до исправления: Critical 0, Important 4. Отдельный повторный review не запускался.

## Исправленные Important

1. Context_Changed не сбрасывал scope review после Band/Register. Теперь сбрасывает; Save также повторно проверяет актуальный checkbox после async source preflight. STA: review → изменение Band/Register → сохранено 0, новый review → 1 lesson.
2. Mentor/corpus consent захватывался до await. Теперь Begin использует актуальные checkbox, отзыв разрешения отменяет token/version и не принимает результат. Программное потребление разрешений после запроса не инвалидирует уже принятый совет. STA: mentor consent, corpus PII или sharing сняты во время preflight → суммарно POST0.
3. Перенос mentor/corpus suggestion заменял dirty note без выбора. Теперь оба используют существующий ResolveNoteAsync и повторно проверяют совет после await. STA: Cancel сохраняет текст, Discard заменяет без save; Save сохраняет прежний scoped lesson до вставки mentor suggestion.
4. Clear Selection во время corpus transfer оставлял captured rows. Теперь Clear/изменение отметок отменяют worker, selection version и review проверяются до detach и перед DialogResult. STA: transfer → Clear → SelectedExcerpts0, форма остаётся открытой.

[UI RED](PSS-007-review-controls-red.txt): 3 PASS / 4 FAIL, exit1. [UI GREEN](PSS-007-review-controls-green.txt): 7 PASS / 0 FAIL, exit0. Тесты выполнены на настоящих standard STA controls с synthetic источниками.

Дополнительная собственная проверка выявила частичную мутацию world при слишком длинной clarification note. Теперь сначала валидируется полный текст с префиксом. [Core RED](PSS-007-review-core-red.txt): 2/1, world ошибочно REAL после отказа; [GREEN](PSS-007-review-core-green.txt): 3/0, world/revision/transcript/pending сохранены после отказа.

## Rulings / limits

- Inspector transactional copy/commit, literal catalog + IDs, bounded strict reused LM transport, отдельные роли, общий budget20 и explicit private save признаны согласованными со scope. Это чтение, не live/model/manual proof.
- Auto Mentor не вводился: ambiguous turn показывает возможность отдельного наставника, manual click требует opt-in и reviewed preview. Task допускает manual путь. Optional DRAFT route заменён существующим scoped lesson route; approval не создаётся.
- Shared model budget сохраняется при Clear; одно успешное резервирование относится к одной попытке, автоматических retry нет. Отмена уже отправленного POST не отзывает переданные bytes, но блокирует принятие результата. До POST отзыв согласия доказан STA.
- Private save — explicit version1 export без restore/migration; WorkspaceStore session schema не менялась. Нет claims об OS-level hostile TOCTOU либо идеальном обезличивании.
- Тестовый SendKeys оказался ненадёжен для modal MessageBox: зависший собственный runner завершён после проверки PID/command line; [частичный harness log](PSS-007-review-controls-harness.txt) не GREEN. Затем test-only owner-scoped GetLastActivePopup/SendMessageWM_COMMAND выбирает кнопку только собственного MessageBox, без глобальной клавиатуры или переключения других приложений. Production Core/GUI PInvoke не содержит. API подтверждены [Microsoft GetLastActivePopup](https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-getlastactivepopup), [SendMessageW](https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-sendmessagew), [WM_COMMAND](https://learn.microsoft.com/windows/win32/menurc/wm-command).
- Запуск STA через обычный dotnet run не имеет WindowsDesktop runtime; loader failure не RED продукта. Правильный route использует WinForms runtimeconfig, как старые STA checks. Исторические diagnostic/layout logs сохранены и не выданы за acceptance.
- Первый legacy PSS006-controls запуск дал IndexOutOfRange без stack; повтор с выводом уже накопленного Failures прошёл 2/0, поэтому точная исходная строка не доказана. Чтение выявило конкретный harness риск: Pump после Close обращается к Controls.Find даже у уже disposed формы. Применён ближайший Pss007Controls паттерн IsDisposed guard; async-close assertions не убраны, production lifecycle не менялся. Pss006Controls.cs включён в exact scope как bounded test-harness exception. [Первый regression](PSS-007-controls-regression-red.txt) не GREEN; [diagnostic](PSS-007-controls-regression-diagnostic.txt) и final regression отдельны.

Declined to judge у reviewer: итоговая Release сборка, новый verifier/final reports, publication, live LM, physical UI/DPI/VS, actual corpus, Java/server. Root проверяет build/static/scope/publication отдельно. Остальные manual/live статусы остаются отдельными и не наследуются. Финальные результаты в [final](PSS-007-final.md).

Reviewer Git inspection: `git diff --stat`, `git status --short`, `git diff -- src/PhantomSemanticStudio.WinForms/ChatCorpusForm.cs src/PhantomSemanticStudio.WinForms/MainForm.cs`, разрешено PSS-007; untracked sources прочитаны напрямую. Никаких Git mutations от reviewer.
