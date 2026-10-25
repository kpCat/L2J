# ACCEPTANCE091 — честные gates

`TOTAL091=SERVER_VERIFIED | PARTIAL | BLOCKED`. Отдельно писать `IMPLEMENTED`, `FOCUSED_VERIFIED`, `SOURCE_GEO_VERIFIED`, `NATIVE_VERIFIED`, `OWNER_QA_PENDING`.

| Gate | Правило PASS | Если не доказано |
|---|---|---|
| P0 safety/baseline | actual Git HEAD/AGENTS/taskSHA, bounded allowed paths, shadow isolated, 203 Geo, TEST091 absent-before + guarded provision, config090 immutable | `BLOCKED_INPUT`/`PARTIAL`, никакого опасного обхода |
| P1 native source → plan | certified topology vs native foreground явно разделены; legal source Human/Elf/Orc, разные farm regions/candidates и 1+ GK legs без fake edges/credits; old routes preserved | quote/graph-only = `SOURCE_ONLY`, не PASS |
| P2 physical first mile | real qualified Player H/E/O starts at native area, physically issues MOVE_TO and reaches GK source via finite proven WALK episodes incl Elf Z-layer; each waypoint timestamp+XYZ/native segments; no invisible warp | HermeticGeo source-only = `SOURCE_PROVEN`, не gameplay PASS |
| P3 paid GK | как минимум **2 разных Player families** реально выполнили NORMAL NPC action, fee>0, exact before−after Adena, actual landing, no duplicate; free transport отдельно | quote>0 или Player near NPC ≠ debit |
| P4 remote farm | все H/E/O доходят до выбранных удалённых farm targets, native attack HP change/kill, earned XP/SP и/или Adena/loot according to native, actual identity continuity | prepared Player/level/gear ≠ farm reward |
| P5 restart & idempotence | хотя бы одно начатое paid multi-stage путешествие: durable checkpoint, graceful stop, same DB/new JVM/same code, continued travel with same goal/profile/earned wallet+items, **no second fee**; ambiguous fee/action negative fails closed | coordinates-only restore ≠ paid journey recovery |
| P6 safety + performance | targeted native insufficient Adena, wrong NPC/index, siege/karma, stale goal/epoch, death/party/store, collision/doors/water/Z, duplicate callbacks; bounded queue/query/retry/no uncontrolled CPU loops; old codec byte compatibility | static test missing ⇒ precise NOT_RUN; never mark safe without negative |
| P7 build/delivery | Java25 Ant shadow compile/JAR + affected focused tests, sanitized receipts, one report, exact-scoped commit, normal non-force push, local/upstream/live parity, outside tracked/untracked preserved | block deliver if uncompiled production |

`FULL091=SERVER_VERIFIED` допускается **только при P0–P7 PASS**, а `OWNER_QA_PENDING` до игры через клиент не снимается. Если H/E/O любой не прошёл, Gatekeeper оплаты нет, рейда нет или restart не доказан — `TOTAL091=PARTIAL` с точной причиной. Доказать новый стройный маршрут при 0 фактических Player действий недостаточно.

Не упрощать P2/P4 до одной заранее поставленной координаты. Не требовать в TASK091 всех классов, PvP, рейдов и полного 1→85 — это другие продуктовые области.
