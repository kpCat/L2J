# PSS-005 — тестовая матрица и независимые отрицательные проверки

В `tests/PhantomSemanticStudio.Tests/Pss005.cs` подключить `--pss-005` к существующему partial runner. Test runner не должен превращать ненулевой exit в успех. Никакой живой LM для обязательных fake HTTP случаев. Проверки должны вызывать новые production APIs, а не самодельную test-implementation.

1. **Offline without LM:** исходный `PackSnapshot` + DRAFT кандидат, GET/POST отсутствуют. Локальная выдача соответствует kind, ограничена 12, содержит provenance/score/RefKey, total considered отражает весь просмотренный same-kind корпус.
2. **Same kind + cross-act exact:** нормализованный точный дубль из другого act не исчезает; один Source и один Peer с одинаковым визуальным ID не сливаются. `REJECTED` peer не включён.
3. **Stable selection:** при разном порядке входных Entry/peers, одинаковые содержимое и fingerprints дают одинаковый ordered shortlist; tie-break Ordinal. Не O(N²); synthetic 5k patterns/20k templates bounded one-candidate smoke.
4. **Synonym counterexample:** «Как твои дела?» vs «Как поживаешь?» с малым лексическим перекрытием — независимо от retrieval result нельзя вернуть `NO_DUPLICATES`/автоматический GREEN, всегда `COVERAGE_LIMITED`.
5. **Positive strict semantic JSON:** fake server получает **ровно один POST** с exact selected model ID, temperature <=0.2, json_schema (не tools) и максимум 12 refs; валидный JSON всеми RefKey приводит только к `MODEL_ADVISORY_NOT_VERIFIED`, Candidate approval/status не меняются.
6. **Fail closed output:** duplicate JSON key, extra root/item fields, unknown/duplicate/missing RefKey, wrong `relation`, пустая/длинная причина, fenced/empty/oversize/truncated JSON, invalid/extra choice, `finish_reason=length`, refusal/tool_calls/function_call — 0 persisted reports.
7. **HTTP failures:** 401/403/400/500/302, server connection refused, timeout, cancellation — 0 reports/approvals modifications, token/prompt/body не появляется в диагностике. Нет retry или model substitution.
8. **Persistence compatibility:** прежний `SessionState.Version=1` без `SemanticReview` загрузить; новый advisory сохранить/перечитать через WorkspaceStore; превышение 12 verdicts, плохой статус/причина или слишком большой объект — fail closed, old session bytes preserved.
9. **Freshness:** candidate text/edit, act/kind/band/source change, new/changed active peer, изменённый pack stamp, изменённый expected ref TextHash invalidates stored evidence; `CandidateReview.Edit` сбрасывает evidence. Старые APPROVED не теряют approvals просто из-за анализа.
10. **Race and partial batch:** источник/peers/candidate меняются после получения shortlist или во время HTTP; full recheck до SaveSession блокирует запись, корректная старая session/approval не теряются. Retry вручную только пользователем.
11. **Existing regressions:** 77 historical ordinary plus PSS-005, PSS-004 selection 8/0, PSS-003 stage 10/0, PSS-002 HTTP strict, JSON REVIEW_ONLY, own source no-write guards; не запускать Ant/Java в L2J.
12. **Designer / UI:** static Designer contract, кнопки существуют/подключены, нет циклов/IO в InitializeComponent. При доступном Windows UI протестировать локальную выдачу без LM, реальную кнопку, cancel, stale report, сохранить edit, resize и DPI; другое явно NOT_TESTED.

**Реальный LM** — отдельное условное acceptance: если localhost доступен, только один явно начатый POST малой партии, краткий sanitized status без raw corpus. Если недоступен — `BLOCKED_LM` или `NOT_RUN` (в зависимости от фактической попытки), нельзя повторять автоматически.

Отдельно проверить mojibake и escaped Cyrillic на изменённых файлах. `reports` не должны включать исходные сообщения/оценки реального пользователя.
