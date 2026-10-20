# PSS-004 — UI и Designer verification

**CONTROL_CONTRACT_PASS: 4 PASS / 0 FAIL, exit 0. STATIC_DESIGNER_PASS. Интерактивный UI smoke / VS Designer round-trip / физические 100% и 150% DPI: NOT_TESTED.**

## Реальная проверка controls

Обычная Release сборка WinForms: compiler warnings 0 / errors 0. Новая форма использует Designer-authored ListView с checkboxes, TextBox поиска, полный read-only preview выбранных, отдельную детализацию строки, счётчик и две кнопки. `.Designer.cs`/`.resx` вложены в csproj. Оба конструктора остаются `InitializeComponent()` only. Шесть вкладок MainForm и его Designer/resx не изменялись.

В существующем runner добавлен STA-route без нового test project/пакетов. Он загружает собственную собранную форму и выполняет public операции настоящих controls (`Show`, `Checked`, `Text`, `PerformClick`, `Close`), вызывающие production events. Никаких private handlers через shell, нового production smoke API, fake UI controls или модели нет. Эти операции не являются действиями человека или Computer Use clicks.

```powershell
dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-004-controls
```

Evidence: [controls GREEN](PSS-004-controls.txt), [controls RED](PSS-004-controls-red.txt).

1. 25/500/5000 APPROVED: ни одной отметки по умолчанию, preview пуст, кнопка disabled; показано максимум 500 строк с честной статистикой.
2. Отмечены первый и последний из 25; поиск скрывает первый, снимается видимый последний, поиск очищается. Первый ID остаётся checked и в полном preview, последний снят. Preview содержит исходные тексты; изменение caller Candidate после SetCandidates не меняет копию формы. Закрытие даёт Cancel и пустой результат.
3. Stale approval и старый baseline остаются видимыми/отмеченными, продолжение disabled; автоматического снятия нет. 20 checked допускаются, 21 остаются отмеченными с disabled-кнопкой; явное снятие 21-го снова разрешает продолжение.
4. Отдельное подтверждение показывает точные исходные тексты, filter/list disabled, AcceptButton = «Нет». «Нет» даёт Cancel/empty; явное «Да» даёт OK и только два сортированных detached ID. Caller peers/approvals неизменны.

Дополнительный RED: временная mutation `checkedIds.Clear()` внутри BindRows воспроизвела потерю выбора при фильтрации и точном подтверждении: **2 PASS / 2 FAIL, exit 1**. Mutation удалена; повторная сборка и route дали **4 PASS / 0 FAIL, exit 0**. В итоговом коде её нет.

## Интерактивная попытка

Использован [computer-use skill](C:/Users/ZBook/.codex/plugins/cache/openai-bundled/computer-use/26.930.41038/skills/computer-use/SKILL.md), `@oai/sky` через node_repl. Подготовлен искусственный 65-file source в собственном `pss-tests-<id>` temp и 27 APPROVED (25 current, 1 stale, 1 old baseline); workspace только `artifacts/PSS-004/ui-run-2/workspace`, process-only `PSS_UI_SMOKE_WORKSPACE`. [Fixture evidence](PSS-004-ui-fixture.txt). Реальный L2J не импортировался.

Sandbox-запуск не предоставил targetable interactive window. Разрешённый запуск в interactive desktop был обнаружен `sky.list_windows`. Второй экземпляр корректно показал workspace.lock conflict; скрытый task-owned процесс был завершён. `get_window_state` смог прочитать UI Automation tree MainForm/startup-ошибки, но screenshot завершился **FrameArrived timed out**, click — **coordinate input geometry is unavailable**. После обновления returned window управление не восстановилось; дальнейшие попытки прекращены. Все созданные этой задачей процессы завершены; пользовательские приложения не трогались. Изменений session/approvals и finished stages от этой попытки нет.

Первоначальная synthetic source-копия внутри Studio Git root была отвергнута existing RepositoryBoundary/AssertDisjoint до workspace save: это ожидаемый guard, не основание ослаблять его. Исправлена только test fixture: source снаружи Git-root, в собственном temp. Пользовательский workspace не использовался.

Не выполнены реальные select/filter/cancel clicks в новой форме, Save/Discard/Cancel редактора, physical DPI visual QA и VS Designer open/save round-trip. Visual Studio 18 Enterprise обнаружен через existing vswhere; Designer не открывался при недоступном input. **NOT_TESTED**, никаких fake PASS. ResolvePendingEdit и существующий editor flow не изменены; console/control/static evidence не доказывает интерактивный acceptance.

## Подтверждения и ограничения

Первое прежнее exact-list решение сохранено как отдельное прокручиваемое modal confirmation на тех же Designer controls: 20 полных текстов и длинные review notes не должны обрезаться MessageBox. Default «Нет». Второе прежнее MessageBox — отдельная editorial attestation, default No; его текст сохранён. C# receipt остаётся STAGED_UNVALIDATED / Java NOT_RUN, смысловая проверка SEMANTIC_NOT_CHECKED. Java/LM из GUI не запускались. Их текущие статусы также NOT_RUN.
