# PSS-004 — точные регрессии (RED → GREEN)

Новый route `--pss-004` в существующем console runner. Список можно дополнить, но не уменьшать смысловые инварианты. Искусственные данные — только в test fixture/temp Studio, не в L2J.

1. **default empty**: даже если `allPeers` содержит 25, 500 и 5000 `APPROVED`, без отмеченных ID `SelectExactIds(..., [])` возвращает отказ; никогда не выбирает все автоматически.
2. **arbitrary subset**: 25/100 APPROVED с действующими fingerprints; выбрать конкретные 2–3 ID (включая самый последний) → результат ровно эти ID, отсортированы Ordinal, ни один чужой.
3. **20/21**: 20 отмеченных — в пределах интерфейсного cap; 21 — чёткий отказ до Stager, source и workspace не меняются; не повышать лимит.
4. **duplicate/nonexistent/null/empty**: дубли одного ID, неизвестный ID, пустой/null ID, дубли IDs в allPeers — всю операцию отклонять, не возвращать урезанное подмножество.
5. **stale/changed approval**: изменить любой важный параметр `Candidate` после approval (Text, Note, Gender, ReviewedAtUtc), оставить Status APPROVED → отказ; актуальные не страдают и не меняют статус.
6. **DRAFT/REJECTED**: добавить ID не-approved кандидата к корректному набору → отказ всего набора; никаких silent omissions.
7. **immutable selection**: результат не меняется при последующей мутации входного `List<string>`; метод не изменяет `Candidates`, `ReviewNote`, `ApprovedFingerprint` или `SessionState`.
8. **real stage subset, not all**: в `StageFixture` PSS-003 создать >20 валидных approved тестовых кандидатов, выбрать только 1 или 2; `IsolatedPackStager.Create` завершает stage именно для них; Source unchanged, stage приросты ровно 1/2, receipt Candidates содержит только selected IDs; чужие approved не вставлены в XML.
9. **stager peer guard retained**: если среди selected/other peers возникают exact duplicate, stale fingerprint или source drift, прежние Stager guards не ослаблены; при selected invalid — нет finished stage.
10. **filter persistence (UI state)**: отметить несколько, фильтровать список, скрыть одну выбранную строку, снять другую, вернуть фильтр → отмеченные ID и полная selected preview корректны. Если невозможно проверить UI интерактивно, проверить чистую модель checked-ID и отдельно честно зафиксировать UI_NOT_TESTED. Не имитировать UI clicks как реальные.
11. **cancellation**: Cancel/крестик/modal `DialogResult.Cancel`, отказ в одном из двух подтверждений, Cancel в стадии → нет нового finished stage и никакого изменения session/approvals.
12. **editor/source preserved**: отмена выбора не приводит к потерянным unsaved edits на кандидате; existing `ResolvePendingEdit` поведение сохраняется, source stamps если actual L2J source route запускался — 65/65 equals.
13. **Designer**: новый `StageSelectionForm.Designer.cs` и `.resx` существуют; конструктор только `InitializeComponent`, static controls и event handlers, нет циклов/IO/фабрик в `InitializeComponent`. Сохранён старый MainForm Designer 6 tabs. `scripts/Verify-Designer.ps1` (существующий) + новый guard.
14. **compatibility**: прежние 69+ C# проверки (PSS-001/002/003) проходят без изменений смысловых контрактов; review JSON exporter остаётся отдельной функцией; если Java не запускался в этом checkpoint, `JAVA_NOT_RUN`, а не PASS.

**Test execution:** сперва scoped `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release -- --pss-004` с осмысленным RED, затем GREEN. Финально один `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Verify.ps1`. Для новой формы — `scripts/Verify-PSS004.ps1`. UI/DPI и Visual Studio Designer — отдельная фактическая проверка или `NOT_TESTED`, без нагнетания вечного BLOCKED при отсутствии automation.
