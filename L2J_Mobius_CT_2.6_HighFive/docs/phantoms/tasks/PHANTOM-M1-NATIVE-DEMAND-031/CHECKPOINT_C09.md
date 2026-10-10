# C09 — passive checkpoint receipt and current ecology probe

Captured checkpoint UTC: 2026-10-10T00:40Z (approximate; Git commit timestamp is authoritative).

The user's six-hour extension sets the deadline approximately 2026-10-10T10:31Z. C08 inline FINALIZED SQL is withdrawn: it was not demonstrated safe, and start10 produced zero EXACT_FINALIZED_HOOK views. A jcmd thread snapshot without an active SQL frame is insufficient to prove safety. Ordinary checkpoint capture now enqueues an immutable native receipt only, catches diagnostic failure without injecting it into native execution, and records hook elapsed/max duration. No ordinary SQL/I/O/waits or other actor locks are added to that hook. Actual duration remains to be measured on start11.

SQL views execute on the exporter, with capture thread and begin/end monotonic timestamps and two-second SELECT query timeouts. Read-only repeatable-read transactions use no FOR UPDATE and roll back/close after export. Pool acquisition can still take the configured pool timeout; it is outside the checkpoint path. Late SQL views are not asserted equal to an earlier native boundary. Offline lineage must account for intervening immutable background/projection commits; incomplete chains remain unproven, not data loss. Planned crash-window SQL is an explicit fault-scene exception, excluded from gameplay timing, and uses the same query timeouts.

Game35536/Login21472 (start10) stopped through stock controls. Typed cleanup DONE/COMPLETE captured319899138906200 before all pool shutdown(false), retained/transactions/leases/references0, proofFailure empty. First sameDB startup occurred, but whole-group persistence was not established and is not PASS. Builds4, full compilations5, GameServer starts10. Production JAR remains50EB05335B128E02A0CA641FCC422CC2EC7A3A98BBD94DB71DD40F74EA1D72A9.

Current first cold guards from start10: 33/133/172 calendar online, PNC COMPLETED, FARM allowed, ecology cursor29859857 below requested29859860; 107 catchup.travel.no_route. Neither is treated as a backend exception. The next bounded probe examines actual ecology entry/demand/queue progression for preselected subjects, using existing Probe031 and read-only non-suspending JDI fields. No repeated acceptance warm-up without a new causal observation. ReadNativeState031 and collector compilation passed. No production guard changed.

C08 heading00:17Z was approximate; next clock read00:16:22Z showed it about one minute ahead. Use actual runtime/Git timestamps for evidence.

M1=OPEN; REAL_FINAL=NOT_RUN; continuity, away/background/return, complete persistence/restarts/crashes remain unpassed. Mandatory frozen regressions retain C07 93/93 and lifecycle11/11; current changes are task diagnostics only.
