# Independent review Goal 017 — terminal verification

Статус: `ACCEPTED_BASELINE_PENDING_MICRO_VERIFICATION`

Independent review принимает completion architecture и lifecycle-safety
изменения commit
`0015a5ffd0c10a99514732ef52b969a39ac62eb7`.

Для terminal micro-completion оставлены только:

1. reusable SOLO после exact non-accepted JOIN terminal;
2. безопасный минимум party pulse budget 10;
3. подтверждённая test-only race ожидания population materialization.

Production population/materialization, `Player`, `Party`, schema, другие хроники
и будущие Goals не входят в correction. Population finding исправляется только
terminal predicate `ACTIVE + playerRetained + worldPresent` в server integration
test.

Acceptance требует dynamic refusal/timeout/new-goal/stale-callback evidence,
nine-member boundary, три последовательных population PASS, cumulative Party
suite, verifiers 016/017, один frozen-tree `ant verify`, standalone `ant jar`,
ordinary child commit/push и два byte-identical post-commit verifier 017.
