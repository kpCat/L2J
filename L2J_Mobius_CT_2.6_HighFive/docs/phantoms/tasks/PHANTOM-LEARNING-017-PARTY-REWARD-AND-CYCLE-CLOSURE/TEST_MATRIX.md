# TEST_MATRIX017 — необходимые доказательства

| ID | Проверка | Ожидаемое |
|---|---|---|
| R01 | G0 offline 183 rows and selected/Adam/pointer | Exact historical SHA; no own/foreign drift |
| R02 | 51 saved slots, 399 paid, 27 terminal, 84 exclusions | Accurate read-only census; no fake new credits |
| R03 | Reproduce Party action exclusion with actual raw + unchanged constructor | Exact violated condition or `UNPROVEN_MISSING_SOURCE`, never guessed |
| R04 | Non-Party valid action baseline | Original valid path remains unchanged |
| R05 | Wrong target objectId/spawnGeneration/instance | Fail |
| R06 | Wrong actor owner/epoch/profile or foreign Party target | Fail |
| R07 | Missing/late/duplicate paid event or timing | Fail |
| R08 | No target kill, invalid terminal, unknown EXP/SP where required | Fail; do not fabricate terminal |
| R09 | Valid Party played action with authentic target/time | Passed as receipt in native TEST, or offline proof marked `NOT_NATIVE` |
| R10 | Distinguish 46 Party kill actor credits vs 29 true NPC kills | No double-count |
| R11 | `DONE016` after cold NO_FIT; old regex RED → bounded green | Exact phase and proof only |
| R12 | Legacy DONE016 without frozen016 and unknown other phases | Fail |
| R13 | Replay cold FIT with existing fit-reserved/result/checkpoint | 0 additional FIT/SQL/GS; idempotent closure |
| R14 | Tampered/missing reward, checkpoint, model/optimizer, owner, external path | STOP with exact reason; no auto repair |
| R15 | `ActionAdvantageV2` dataset sufficiency counts by split | Old 016 upper-bound <=15 gradient, <=7 AUDIT and FIT0 |
| R16 | Next campaign planning coverage without outcome peeking | Honest positive or UNATTAINABLE; no hidden threshold drop |
| R17 | Focused Java/PowerShell test regression + build | PASS only with actual exit0; no general `ant verify` |
| R18 | Bounded optional GS native Party (≤1/20min) | At least one valid actual paid Party terminal receipt; else PARTIAL |
| R19 | Frozen SQL/Geo/models, OFF/owned0, GitHub source readback | PASS, documented evidence and exact SHA |

### Достоверность результатов

- Fixture с synthetic `TacticalReceiptV3` — **только контракт**, не native Party доказательство.
- Replay подлинного raw016 — **подтверждение корректности сохранённых фактов**, но **не новое game action / credit**.
- Иммутабельный новый receipt native017 должен иметь свою физическую source и корректные stamps; один receipt не доказывает общего качества модели.
- `IllegalArgumentException` без указания условия — FAIL диагностики.
- Успешное техническое завершение director без FIT не доказывает новое обучение.
