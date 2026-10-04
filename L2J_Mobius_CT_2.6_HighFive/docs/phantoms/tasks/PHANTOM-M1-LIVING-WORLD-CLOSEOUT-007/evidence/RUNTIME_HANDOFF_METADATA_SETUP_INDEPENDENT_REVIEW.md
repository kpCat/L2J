# Independent review: Handoff metadata setup

Bounded nonauthor source verdict: Critical 0 / Important 0 for the two TEST setup corrections. No native producer RED, promoted Ant result or whole engineering/W GREEN is supplied by this review.

| Read-only input | SHA-256 |
| --- | --- |
| Published `test/java/org/l2jmobius/tests/phantoms/PhantomM1RuntimeHandoffSuite.java` | 10EB36714AB5B2B3FD09FBD135FAD4675AA8DD48216162F5A12F1AC59A9E22C6 |
| Same TEST source `.pending` | BB17E7A91441E736A462ADECF1957FBDC6E559A2A2BD6EEDE3279445A24EA1F2 |
| Fresh private compile source `.phantom-local/task007-root-handoff-metadata-check/source/PhantomM1RuntimeHandoffSuite.java` | BB17E7A91441E736A462ADECF1957FBDC6E559A2A2BD6EEDE3279445A24EA1F2 |
| Unchanged published Ecology | 4C48937E87904A34323CAA56679828430123F282FDEC03046E2D102F02859DF4 |
| Local Goal033 metadata setup analogue | 139F826187C9981B9084A376651269E8E374A2D84C04A5F7760D2C1B5F3E0104 |
| Preserved `red-handoff-regression03.txt` | 790AD29486B2E99C80B5E59A5E6632497516A94218D1F5D5E682327716BAD6E7 |
| Preserved `red-handoff-regression03.xml` | 51979CE7ED91D66410D348D073E0A02C9901B03300A0BFC9DB29B4A85304BD14 |
| Private candidate `reports/m1-runtime-handoff.txt` | 152DB71981C82CCFA8BBF2836451F180F24212A58E1BBFC3A4FB18B4298D10A2 |
| Private candidate `reports/m1-runtime-handoff.xml` | E03401EB89B7E8BF7F0C40E4F2BE622BEDED8AE36734537612C59CAF2CA0AB47 |

Read-first covered the two exact TEST methods and file comparison, the original Goal033 `loadMetadata` analogue, unchanged Ecology hash, preserved original failure report and private candidate evidence. The exact central scope row preceded this report. No source/pending edit, Ant/JVM execution, database operation or Git command was performed.

The comparison shows only first metadata-worker assertions and a subsequent original manager pulse/queued-worker execution in cases07 `beginRejection` and09 `partialRetryBudget`. Both fixtures start cold, so the first worker now correctly exercises the metadata-only census. Each added assertion requires lastPulseIntervals0. Case09 additionally requires the original backing catchup intervalOrdinal0 at that point, preventing setup from consuming the13-interval request.

The next original manager.onPulse/worker enters the intended productive path. Case07 still requires the exact original `catchup.claim.stale` reason rather than `ecology.commit_pending`. Case09 still requires exactly13 committed intervals in the shared pulse budget and intervalOrdinal13, with its unchanged memory-port advance behavior and no matching-request restart. There is no altered clock, retry loop, weakened assertion, forced cursor/outcome, reduced interval target or production change.

This follows the existing Goal033 explicit metadata setup before productive assertions. It adds one deterministic worker transition to these cold single-profile fixtures; it does not reinterpret metadata as historical execution or use repeated pulses until a condition happens. The original registry identities, all other cases and stop paths remain unchanged.

The preserved original report was independently read:13/15 PASS, with07 expected stale reason but commit_pending and09 expected13 but0 intervals. These are the observed first-metadata-only fixture premises, not new native product RED credit. The private DB-free Launcher report was independently read:15/15 PASS, and both file hashes match the frozen private result above. The reviewer did not execute that run.

Root's promotion and original Ant verification remain separate, as do native RED/GREEN, affected regressions, full World/cohort and final committed artifact acceptance. No gate beyond this bounded source review is marked passed.

Mojibake markers in this report and the central scope ledger were checked separately.

Escaped Cyrillic and XML escaped Cyrillic in this report and the central scope ledger were checked separately.

Both scans found no matches. Git commands used: none. Engineering GREEN: not claimed.
