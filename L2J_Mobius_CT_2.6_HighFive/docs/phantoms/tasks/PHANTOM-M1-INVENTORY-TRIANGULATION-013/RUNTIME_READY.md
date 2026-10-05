# Observe013 ready evidence

Historical readiness evidence. Manual «в игре» was subsequently received; runtime PASS remained unproven and observe013 was gracefully stopped after confirmed logout/store. See RESULT.md and OBSERVATION.tsv. M1=OPEN.

Source commit c23915df10239bfab15ae49276e14833268b9afc was normal-pushed from the isolated worktree to experiment/m1-candidate007-observe008 (remote ae6f0236→c23915df102).
Fresh clone l2jmobiush5_localplay_observe013: characters10002, profiles10000, components50000 at import. Exact clone TestAdmin charId268492939/nameTestAdmin/accounttestadmin inherited accesslevel100, online0, level12/exp138026/sp13880/x44131/y42673/z-3488 before startup.

Private runtime: C:/Users/ZBook/.codex/worktrees/m1-inventory-013/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/observe013/runtime.
Runtime identity 405e938511620c2eb5c4f44577e0832a845f61a25b37ed4e028bad9387b0aed3.

Verified live at 2026-10-06T00:07:05+03:00:

| Role | PID | startTimeUtcTicks | Ports | Ownership |
|---|---:|---:|---|---|
| GameServer |26344|639268310472673448|7777|RUNNING / recordVerified=true|
| LoginServer |18056|639268310448297454|2106,9014|RUNNING / recordVerified=true|

GameServer startup completed at 00:05:13, registered as Bartz, 66 seconds. Pilot mailbox enabled at 00:05:04. Check-LocalPlay returned CONFIG PASS and exact owned listeners; old conflicting processes were gracefully stopped.

Private config verified from the actual runtime files:

```text
EnablePhantomSystem=True
PhantomPopulationTarget=1280
PhantomPopulationActiveTarget=8
MaxMaterializedPhantoms=8
MaxScheduledPhantomProfiles=10000
EnableLocalPlayPilot=True
EnableLocalPlayPilotAutoAttach=True
LocalPlayPilotAutoAttachCharacters=TestAdmin
EnableLocalPlaySyntheticHuman=False
GMStartupBuilderHide=False
GMStartupInvisible=False
GMStartupInvulnerable=False
GMStartupSilence=False
```

Game jar SHA256 9E0BCFF192DEDD7708A2D656E95DDEF8A72CBB6779BD5AFCD11FE27B7837A987; Login jar D82E588B1D047EA18B203885A53F6C5E31454B09558D37478EB3CF53A5CE12C7. Both correspond to the committed source build. Source PLAY config/libs/geodata/PID files passed the existing preparation script's before/after hash preservation check.

## Bounded private deployment corrections

The fresh Windows checkout converted population XML LF→CRLF. Catalog hashes are byte-sensitive: first startup refused the pinned v1 hash, second refused v2 authority.catalog_drift. Each runtime XML was compared against the already-working observe011 XML with EOL removed; full normalized content matched. Only private runtime bytes were restored from read-only observe011, with verified hashes:

- v1:23B12FC523DE83D1BDEA54A75677823B2E8129978792A9C9FEC684ACCFAA4748 (exact production-pinned predecessor);
- v2:D555AB8DDA0783D0736D3AADB0BD3D1B76481C73F0A34656CDF5C70FB88FBBC6 (existing catalog).

No source XML, production guard, population component, character or item was rewritten for these startup failures. Both partial starts were stopped via stock public graceful shutdown after exact DB online0/store checks. Private Stop-LocalPlay delegates to the no-force helper before its legacy code, so the startup orchestrator cannot use its original force fallback. A duplicate concurrent Login attach returned Premature EOF when the other graceful request had already exited the same verified process; process exit and free ports were verified.

Manual login question was presented after readiness. No arm operation and no natural materialization observation have been issued before the required user confirmation. The startup log's later Master-access message is not substituted for the user's «в игре» gate.
