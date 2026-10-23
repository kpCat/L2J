# OWN073 runtime evidence

Game source97cefafc451a97e80c37254889b387c20ecf4764, Game SHA C59D4E9CFB579EBFF80C0AC1F5C4412D673B271C2E2AD59D5223DD8D8248B695. Each OWN073 schema bootstrapped once from stock; no old schema import.

| RELEASE episode | Native ready UTC | Stock shutdown log UTC | Game/Login PID | Result |
|---|---|---|---|---|
| Main | 2026-10-23 20:15:28 | 20:20:44 | 29616/2804 | Normal START/STOP exit0 |
| SameDB/sameJAR restart | 2026-10-23 20:22:42 | 20:24:50 | 20372/34308 | Normal START/STOP exit0; no reseed |

Ready-to-shutdown5m16s/2m08s, boot33s/34s. Log times describe stock shutdown entry, not an invented exact process-exit timestamp. Controller verifies PID incarnation, process termination, listeners and DB0. Stock logger calls this entry “GM shutdown”; no human/GM account was created or used.

[Before](main-before-restart073.tsv)/[after](main-after-restart073.tsv) snapshots use the installed read-only probe after quiescence.16identities SHA907f6c2cb3da96343678963d10165ecc2799d71550638ec0539c009491c89bd6 and16profiles SHA4a4bccdfa2d3981980e52f9b2f329a265981bd5b6c1be5ee464bc578d0cfc68d remain equal. Items117→121 and component payloads change with native activity. No offline merchant bootstrap:0quota/owners/items both times; premium/offline switches and rates retained from stock.

Vortexbra/profile2/actor268484865:level2→3, EXP87→367, SP6→14, HP91→30. Four second-snapshot profiles are dead; no HP/EXP/position restoration. This bounded ordinary Main continuation does not prove paid20+ procurement or full world acceptance.

Final [TEST](test-final-status073.json)/[RELEASE](release-final-status073.json): both Game/Login STOPPED, own databaseConnections0. Independent CIM exact task073.control/native Java count0; listeners2106/7777/9153/2253/7953/9253=0. All native materialization drains STOPPED. Lightweight SQL inspection opens no ThreadPool; its first version logged an uninitialized-pool shutdown warning, retained in raw logs and corrected in later TEST helper.

Measured TEST actor268480990 remains alive after normal respawn:335HP,1747A, original objects/root revision1/unpaid expired child revision2. Early profile2 retains20genuine Adena and exact inventory on another JVM. No paid1463 receipt. CORE073=PARTIAL.

Candidate:`D:\PhantomWorld\Earned-Life-073-candidate`; contained START.cmd/STATUS.cmd/STOP.cmd are the operator interface. Native fixture and operator TEST are separate OWN073 physical roots sharing only TEST073 schema. Each of3roots has203physical GeoData. Credentials/hexid remain private, outside Git.
