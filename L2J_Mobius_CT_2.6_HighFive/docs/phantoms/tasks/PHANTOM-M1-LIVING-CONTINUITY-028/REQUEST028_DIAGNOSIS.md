# Exact REQUEST028 diagnosis

Два original027 runtime прочитаны только READ/export. mailbox_audit не dispatch/replay.
Raw request/session/heartbeat copies и SHA256 находятся в evidence/REQUEST028_OLD.

| Case | Identity | Exact outcome | First cause |
|---|---|---|---|
| D read | f400cdc9-df2a-4dfa-ad63-d2d0a96903c3, session/run76118b57-396c-40ad-ace5-fe9549242642, sequence141, SNAPSHOT_PHANTOMS | INBOX_OBSERVED_NOT_PROVEN_EXECUTED; processing/journal/result/temp-result отсутствуют; nextSequence141 | UNKNOWN; final STOPPED reason SYNTHETIC_WATCHDOG_OR_IDENTITY не различает mailbox/identity/TTL/heartbeat |
| G move | 77b3663e-f2d8-49b9-9020-050f08299a21, session/run65364c0a-fe08-4f90-ab31-239da838ff4b, sequence4, MOVE_SELF43831/42673/-3488 | INBOX_OBSERVED_NOT_PROVEN_EXECUTED; processing/journal/result/temp-result отсутствуют; nextSequence4 | UNKNOWN; тот же общий reason; MOVE execution/arrival UNPROVEN |

D request создан20:15:05.758301 UTC, deadline20:15:15.7333524 UTC. Последний heartbeat1791490515376; final state last-write20:15:18.3699783 UTC. G request создан21:21:18.8629849 UTC, deadline21:21:28.8288873 UTC. Последний heartbeat1791494488868; final state last-write21:21:31.4322813 UTC. Native actor object268492939, exact PID/start ticks сохранены в raw session records. Final state может переписываться STOP/shutdown; его timestamp не выдается за первый момент закрытия.

Ближайшие два preceding results каждого session SUCCEEDED. Перед последним successful G STATUS21:21:17.6288945 actor worldPresent/online=true, owner LOCALPLAY_TEST_HUMAN, XYZ44131/42673/-3488, HP274/MP345. Ближайшие D results также показывают native observer online/worldPresent и тот же XYZ. Server game/log scoped exception search не нашёл corresponding exception stack. Ближайшая unrelated exception не объявляется причиной.

Классификация: sender RESULT_TIMEOUT + native session CLOSED with unclassified original guard; не NATIVE_EXCEPTION и не доказанный TTL/HEARTBEAT. По source process выполняет atomic move→claim→sequence++ перед action, поэтому retained inbox+unchanged sequence поддерживают отсутствие admission на сохранённой границе; это source inference, не continuous execution measurement. Автоматический replay запрещён даже здесь.

E separate lost-heartbeat episode не объясняет эти requests. Metadata600s drift — отдельный воспроизведённый дефект, не диагноз original D/G. В028 first guard диагностируется без изменения порядка/closure и без продления525s/30s/400sequence.
