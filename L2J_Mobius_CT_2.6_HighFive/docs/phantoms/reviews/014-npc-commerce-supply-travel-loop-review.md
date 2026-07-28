# Review Goal 014 — NPC commerce, supplies, travel и sell loop

## Status

`FIX_REQUIRED after first review`

Проверенный implementation commit: `696689987276137f6a7f3661329171c9ee65e6f9`.

## Findings

### F1 — Commerce lifecycle ownership не был полностью доказан

Drain учитывал не все фактические operation, actor lease и persistence claim.
`finishStop()` мог объявить остановку до освобождения всей принятой работы.

### F2 — Persisted current goal не был единственным authority

Commerce receipt мог опираться на переданный goal без повторной проверки exact
persisted ACTIVE `profileId/goalId/revision` непосредственно перед `PREPARED`.
Terminal receipt rollover не различал все stale/conflict случаи достаточно
строго.

### F3 — Exact catalog lookup был ограничен первой страницей

Поиск exact buy offer и teleport route через page-0 ограничение `256` не
доказывал корректность для authoritative записей за пределами первой страницы.

### F4 — Production backend integration была недостаточно канонической

Требовалось доказательство настоящего `L2jCommerceBackend` на materialized
`Player` с реальными `Merchant`/`Teleporter`: buy, sell, NORMAL teleport, а
также conservation между runtime, DB, dematerialize/materialize и reload.

## Required bounded correction

Goal 014A закрывает только перечисленные findings: ownership counters/drain,
persisted goal authority и terminal rollover, exact unbounded catalog identity,
materialized-Player commerce integration и deterministic regression coverage.
Server core/loaders/packets, catalog rebuild, progression, Game Knowledge,
config/schema и будущие Goal не входят в correction.

## Review verdict

Goal 014 остаётся `FIX_REQUIRED after first review`. Реализация Goal 014A после
полного gate должна получить статус `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
