# PSS-009 — последовательность A/B/C, без угадывания

## A. Instrument -> RED -> причина

- Подготовь scoped plan, прочитай источник, запиши исходный HEAD, file SHA/status и свойства DPI/масштаба **не меняя** Windows settings.
- Добавь test-only route в существующий `tests/PhantomSemanticStudio.Tests/Program.cs`, например `--pss-009-layout`; код размести в отдельном `Pss009Layout.cs` (partial Program), WindowsDesktop runtimeconfig от WinForms проекта.
- Действительная MainForm, `.Show()` на тестовом desktop/STA, TabControl.SelectedIndex0..5, DoEvents/PerformLayout и повтор 5..0; source/user workspace строго не трогать; process-only ignored fixture.
- Логируй только имена controls + размеры + `DeviceDpi`, без source paths/LLM/tokens/corpus. Начальный RED должен показать конкретные недоступные кнопки/controls; при невозможности живого test route — описать blockers, не выдавать static grep за реальный RED.
- Проверить root hypothesis `tabCandidates.Size` против других страниц, сравнить вычисленные Bounds и Parent.ClientRectangle на стартовых и после selection событиях. Указать точную root cause; другие hypotheses только после falsification.

## B. Fix -> GREEN -> structural guard

- Сначала поправить минимально необходимое в `MainForm.Designer.cs` (при необходимости `.resx`, MainForm.cs только для заслуженного lifecycle fix), следуя `LAYOUT_DESIGN.md`. Дизайнерские свойства одной страницы должны быть согласованы с остальными пятью.
- GREEN на том же живом STA route с проверкой кнопки карты качества и генераторных/диалоговых/экспортных кнопок; повторить tab switch, resize, min view и Cancel.
- Добавить static verifier проверяющий actual six-page layout baseline/invariants (но static — secondary). Может быть `scripts/Verify-PSS009.ps1`. Случайная mutation (удалённый размер/анкер одной страницы) ДОЛЖНА дать RED, после отката mutation GREEN.
- Если обнаружены дополнительные доказанные дефекты модальных окон/масштаба, исправлять отдельными минимальными коммитируемыми изменениями, не создавать новую форму.

## C. Real display matrix, regression and publish

- Live control bounds: нормальное окно, повторный таб, minimize/restore/maximize, уменьшенный viewport; 100/150% real DPI только если доступен физический desktop. `AutoScroll` допускает достижимость, но требует assert клавиатуры/прокрутки, иначе FAIL.
- `scripts/Build-Verify.ps1`: 0 errors/0 warnings, прежние >=122 PASS плюс свои; отдельно `--pss-004-controls`, `--pss-005-controls`, `--pss-006-controls`, `--pss-007-controls`, `--pss-008-controls` (не вместо нового route).
- Попытка VS Designer safe open/save/reopen для MainForm и остальных изменённых; без доступа к VS явно `VS_DESIGNER_NOT_TESTED`. Физическую проверку экранов владельцем оформить в `PSS-009-ui.md` как manual gate, если не выполнена.
- Read-only review только modified scope, никаких пользовательских архивов/screenshots/private paths в public GitHub. Safe evidence — counts, geometry, failure names и process exit codes.
- Только Studio git: exact owned diff, ordinary commit, non-force push `origin HEAD:refs/heads/main`, remote/public SHA equality. Report/STOP.
