# ACCEPTANCE044 — приёмка без разрастания доказательств

| Gate | PASS только если |
|---|---|
| A0 SAFE | BASE/remote/status verified, own044a isolated, protected040a/old raw/schema/client untouched; no crash/network operations |
| A1 SIGNAL_OWNER | Actual sourceKey, sequence, requiredState, remaining/expiry и owner path конкретного ACTIVE сигнала после settled away (D1 если достаточно raw; иначе один current native short trace). Не выдавать source guess за PASS |
| A2 BACKGROUND_TIMING | Для C1 корректно установлен реальный expected due/producer path: scheduler cadence vs alternative catch-up, actual eligible state; отсутствие work за77с не выдано за bug без проверки due |
| A3 PRODUCT_FIX | При actual source defect: causal RED, минимальный owner fix, GREEN и negative without weakening combat/earned/party; если defect не доказан — NOT_APPLICABLE, product не менять |
| A4 G4_NATIVE | Same pre-enrolled profile earned native→legal release/full owning store→actual **committed producer** после real eligible time→legal same-profile useful native return; no duplicate/SQL heal. Если A1/A2 PASS но native не полон — A4=NOT_VERIFIED, M1_G4=OPEN |
| A5 CONSERVATION/REGRESS | Если были собственные ordinary runs: full all-birth stopped save/global drain/RUN_END no dropped; final jar-first93/93/lifecycle11/11/soft3 + affected tests. Current JAR mismatch не выдавать за exact G2 reuse; changed gameplay semantics требуют new G2; historical results retained |
| A6 DELIVERY | Only exact scoped changed paths; compact verified ZIP, ordinary commit+push, tracked clean, own JVM0 and protected040a unchanged |

**Не создавать обязанность до3ч непременно получить A4:** задача прежде всего разблокирует исправление причин G4. Если источник недоступен/оказался законным, не внедрять patch ради зелёного статуса. TASK044=`PASS` только при выполнении A0–A6 и G4; иначе `CAUSE_ESTABLISHED`/`PARTIAL_VERIFIED` с точными оставшимися проверками. M1=CLOSED/READY_FOR_M2 запрещено до всех исходных G1/G3/G5/G6/G7/G8/R1/O_REAL/USER_ACCEPTANCE.

Исторические FAIL и raw042/043 не изменять. Причинная гипотеза о том, что `topology.combat` держал D1, — только гипотеза до actual key. `expired` по monotonic-clock нельзя приписывать прошлой версии sql. `activeSources=1` не указывает owner.
