# PSS-005 — read-only review

Fresh-context reviewer: отдельный Codex subagent по executing-plans / requesting-code-review. Base/precommit HEAD `5d1aedaed38d048e3313c0d8be7d5f81f4db2df2`. Reviewer не менял файлы/index/HEAD, не запускал тесты, не открывал L2J. Только scoped `git diff --` и `git status --porcelain=v1 -uall`.

**Critical: 0. Important: 2, оба исправлены parent с RED/GREEN. Minor: 1 deferred.** Первичный verdict with-fixes, повторного ревью не было; подтверждение исправлений — регрессии, static guard и финальные suites.

1. ShowCandidate не ловил DecoderFallbackException strict UTF-8 reader. Выбор сохранённого advisory при повреждённом source мог выйти из BeginInvoke. Реальный control route меняет физический synthetic XML на invalid bytes, переключает DataGridView public CurrentCell; RED exception → catch DecoderFallbackException → GREEN STALE без crash. Approvals/session сохраняются.
2. После SaveCandidateVersion/BindCandidates безусловный FormatSemanticShortlist(current=true) перезаписывал STALE свежего reread. Narrow static regression guard RED → строка удалена → GREEN. UI сохраняет результат ShowCandidate и не повышает актуальность по предположению. Control route отдельно подтверждает STALE при изменённом physical source. Static guard не выдан за deterministic race reproduction между save и rebind.

Deferred Minor: выбор saved evidence синхронно перечитывает pack и выполняет scout дважды на UI-потоке. На большом источнике возможна задержка и недоступность Cancel на время selection refresh. Явные кнопки выполняют работу через cancellable RunAsync/Task.Run. Перестройка selection refresh не входит в финальный fix pass; ограничение остаётся видимым в final/UI отчётах.

## Rulings по Declined to judge

- Reviewer не запускал build/tests: parent подтвердил фактические exit/counts штатным runner и Build-Verify. Цена ошибки — регрессия; evidence журналов публикуется.
- Live/UI/DPI/VS не выполнялись reviewer: parent разделяет один BAD_RESPONSE live POST и выгрузку, actual controls PASS и interactive/DPI/VS NOT_TESTED. Цена ошибки — ложная приёмка; статусы не повышаются.
- Java/runtime/XML install readiness вне PSS-005: frozen owners/исторические gates сохранены, Java NOT_RUN, XML не опубликован. Цена ошибки — опасное применение; никакого apply/install path нет.
- README/final reports готовились parent: сверены с final поведением, ограничениями и логами до commit. Цена ошибки — неверные ожидания оператора.
- Commit/push/remote ещё не выполнены на момент review: exact allowlist/index и remote SHA проверяются в closing sequence; outcome только после фактической команды. Цена ошибки — неверный checkpoint; no force.

Evidence: PSS-005-review-controls-red.txt, PSS-005-review-display-red.txt, PSS-005-controls.txt, PSS-005-designer.txt и PSS-005-build-final.txt.
