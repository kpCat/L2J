# PSS-007 — UI evidence, 2026-10-09

**STA_CONTROL_CONTRACT PASS: 7/0, exit0. Static Designer PASS. UI_INTERACTIVE / DPI_100 / DPI_150 / VS_DESIGNER_ROUND_TRIP NOT_TESTED. Manual gate REQUIRED; не принят автоматически.**

Окно DialogueLabForm отдельно от MainForm, вход на существующей вкладке «Диалог и обучение». Сохранены шесть старых вкладок, candidate editor, StageSelectionForm и ChatCorpusForm. Standard controls, static Designer layout/events/resx, constructor только InitializeComponent, dependencies через SetPack/SetWorkspace/SetModel после создания. Нет runtime BuildUi или I/O в Designer.

## Автоматизированные STA проверки

Команда после Release Build-Verify:

```
dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-007-controls
```

[Final controls](PSS-007-controls-final.txt), 7 PASS / 0 FAIL:

- A: реальная форма, mentor default OFF, AUTO; три синтетические реплики, YOU/PACK роли и trace с provenance/no-match. Cancel между worker и commit сохраняет input и количество turns, Clear очищает историю. Bounds основного dialogue layout в MinimumSize и текущем размере, transcript/trace без overlap. Это не DPI100/150 proof.
- B: OFF и отсутствующее preview permission дают POST0; явное включение + reviewed context дают ровно1 fake POST; вопрос отдельного MENTOR, confirmation REAL. Explicit scoped lesson append сохраняет прежний candidate approval byte-for-byte; labs directory появляется только после отдельного Save history.
- C: synthetic corpus, transfer2 после полного preview и review, без LM. В лаборатории checkbox1–3, manual masking, два отдельные permissions, fake POST1 только после них, general mentor OFF. Keyed suggestions, явное note→scoped lesson; immutable SQLite SHA, no Original/header/private/token outbound.
- Review: Band/Register снимают scope review; новый review разрешает save. Revoked mentor/corpus PII/sharing до окончания source preflight дают POST0. Dirty note Cancel/Discard/Save проверены в настоящем собственном MessageBox, для mentor и corpus вставки. Clear Selection во время transfer оставляет SelectedExcerpts0.

Explicit STA synchronization context переживает DoEvents teardown. Test-only owner-scoped modal commands не являются физическими mouse/keyboard действиями. Никаких глобальных SendKeys в final harness. Initial failed harness logs перечислены в [review](PSS-007-review.md); они не PASS.

Старые controls проверяются отдельными процессами: PSS004 exact stage selection, PSS005 saved advisory/approval/layout/source drift, PSS006 responsiveness/corpus page20/filter/cancel/close. Итоговые counts в [regression log](PSS-007-controls-regression.txt). Source/client/server Java не запускаются; всё synthetic.

Static command `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-PSS007.ps1 -StaticOnly`: [Designer evidence](PSS-007-designer.txt). Проверены Main/Stage/Corpus/Lab ctors, шесть tabs, event handlers, resx XML и project nesting. Это не Visual Studio Designer round-trip.

## Один незакрытый operator manual gate

Средства текущего сеанса не дают native physical GUI automation. Скриншоты/нажатия/VS round-trip не заявлены. Оператору нужно вручную:

1. На DPI100 и DPI150 открыть MainForm и лабораторию из старой вкладки, пройти A с match/no-match/functional; проверить readable trace, scrolling, MinSize, resize, tab order, кнопки без clipping/overlap.
2. Проверить B opt-in OFF/preview masking/permission, отдельные YOU/PACK/MENTOR/EDITOR_NOTE, уточнение GAME/REAL, stale/cancel/close, Save/Discard/Cancel dirty note; lesson только после точного scope review, history только explicit.
3. На synthetic корпусе вручную выбрать/скрыть фильтром/очистить 1–20, проверить полный preview и перенос. Выбрать 1–3, manual language override/masking, exact outbound preview и оба consent OFF по умолчанию; отменить preflight/отозвать consent. Не использовать личную переписку/имена.
4. Открыть MainForm, StageSelectionForm, ChatCorpusForm и DialogueLabForm в Visual Studio 2026 Designer; выполнить безопасный layout round-trip, проверить `.Designer.cs/.resx`, события и повторный build. Не подменять это static check.
5. Live LM отдельно: только уже загруженная точная Gemma и явное разрешение оператора, один tiny synthetic mentor/corpus запрос; malformed response считать BLOCKED, без retry/load/unload. Сейчас lms ps пустой, live request0.

JAVA_CONTENT NOT_RUN; SERVER_RUNTIME NOT_SERVER_READY; PACK_CATALOG_APPROXIMATE / NOT_JAVA_RUNTIME_PARITY независимо от результатов UI. STOP PSS-007, PSS-008 не начат.
