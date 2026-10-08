# RESULT025

```text
TASK_RESULT=FAILED
STOP_AUTHORITY=TASK025_CONTRACT
BASE_SHA=07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6
FINAL_CODE_SHA=41692152ca6944176be8014c307702b7d2a0a6bc
BRANCH=experiment/m1-candidate007-observe008
GAME_SERVER_ARTIFACT_SHA256=459B5F82F31E00658126D5D61A8B060B81958251D3A8BFCC9D61CC96E2078BC4
REMOTE_HEAD_VERIFIED_PRE_PACKET=41692152ca6944176be8014c307702b7d2a0a6bc
FINAL_ARTIFACT_COMMIT=exact task packet HEAD; post-commit SHA in final reply and local publication-final.json
PUBLICATION_RECEIPT=L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/publication-final.json
START_UTC=2026-10-07T21:14:36Z
PRODUCTION_FROZEN_UTC=2026-10-07T23:33:37Z
ACCEPTANCE_FINISHED_UTC=2026-10-08T01:07:19Z
ACCEPTANCE_WALL_MINUTES=232.73
FIRST_FULL_SERVER_PROBE_MINUTE=<30
FIRST_CHECKPOINT_CAUSE=own424/759 CAPTURE IllegalArgumentException combat; historical110 UNKNOWN
CHECKPOINT_PHASE=typed lifecycle ADMITTED..COMPLETED; first cause retained
COLD_RECOVERY_452=PASS
SECOND_RESTART_IDEMPOTENCE=PASS
LIVE_CHECKPOINT_PASS=PARTIAL
CALLBACK_DRAIN_CAUSE=E first stop EVENT:ON_ATTACKABLE_KILL:RESERVED timeout; exact producer UNKNOWN
CALLBACK_DRAIN_PASS=false
REGRESSION_PASS=false
FARM_A_PASS=false
FARM_B_PASS=false
COHORT_PASS=false
PERSISTENCE_PASS=true
SAME_DB_CANONICAL_PASS=true
RESTART_PASS=false
CLEANUP_PASS=false
DEATH_PASS=NOT_RUN
SOFT_RETURN_PASS=NOT_RUN
CRASH_PASS=false
SERVER_M1_PASS=false
REAL_FINAL_PASS=NOT_RUN
M1=OPEN
PENDING_OWNED_STORE=0 across a/b/c/d/e/h
RETAINED_ENTRIES=0 live entries after all own JVMs stopped; first E drain not PASS
OWNED_JVMS_LISTENERS=STOPPED; JVM0/listeners0 verified 2026-10-08T01:07:19Z
RUNTIME_STARTS=20
PLANNED_CRASHES=1
FORCE_USED=0
PLAY_WRITES=0
MANUAL_FINALIZE=0
DIAGNOSTIC_COUNTER_RESTORATION=0
NEXT_TASK_STARTED=false
```

## Доказанные причины и изменения

Historical110 first producer остаётся UNKNOWN. Новый полный stock GameServer probe
`C_R1_FIRST_PRODUCER_3` доказал близкий дефект у424/759: CAPTURE, combat predicate,
IllegalArgumentException, temporary SEALED без receipt. Это не доказательство того,
что именно такой producer был у110.

Изменены пять production файлов, перечисленных в SOURCE_MAP: BackgroundService,
HistoricalBackgroundService, VisibleFarmTravel, NativeWorkScope, условный
BackgroundDecision. Cold VERIFY_PENDING/exact receipt разрешается существующим
resolver до baseline и обычных FAILED/eligibility guards. Typed checkpoint связывает
exact profile/object/owner/epoch/goal/revision/request, сохраняет first stage/error и
публикует одно coalesced продолжение. WAIT_EARNED освобождает caller; continuation
использует существующий instant executor после quiescence, store не выполняется
inline из ticket completion. Existing stock registrations сохраняются только для
принятого typed control. Generic guards, permanent cleanup, unknown attempted writes
и receipt ownership остаются закрытыми.

R3 отдельно исправил false publication failure на fractional native vitals и stale
completed no-write request при новой goal. Сравнение использует существующую
canonical MEDIUMINT projection и отдельно pinned raw runtime; transaction resolver,
schema, награды, глобальные pools и EventDispatcher не изменены. Нет reopen только
по hasPending=false, новых journal/executor или замены XYZ на anchor.

Source commits: R1 `3f826eb2806c13e8f5440983e7df9db43c4b8603`,
R2 `71bef1ea6d48c52632a9e67b7f16b31d885a41ae`,
R3 `b8e6e537df441d75e34dd7ed9df7de44b0f753df`,
R4 `41692152ca6944176be8014c307702b7d2a0a6bc`. Каждый normal push подтверждён.
Production/test semantic freeze — R4; final artifact commit меняет только task packet.

## Cold452 и whole-group persistence

Первый practical endpoint выполнен в025a на новой retained024c копии: production
recovery452 → READY8424/context COMPLETED55; strict native snapshot, inventory,
skills и XYZ совпали. Второй actual sameDB restart сохранил эти версии и значения.
На final R4 создана ещё одна новая retained копия025h: исходный receipt452/8423
сохранён; `H_R4_FIRST_EXACT.json` и `H_R4_SECOND_EXACT.json` — PASS1/1, second
restart READY8427/context58 неизменён. Разница версий первого R4 запуска включает
обычный control lifecycle; native значения из retained receipt не восстановлены из логов.

Final сцены обе имеют strict full-group SEALED→SQL PASS8/8:
`D_R4_A_WHOLE_GROUP_EXACT.json`, `C_R4_C_B_WHOLE_GROUP_EXACT.json`.
Два actual sameDB process restart группы d дали PASS8/8 и PASS8/8:
`D_R4_GROUP_RESTART1_EXACT.json`, `D_R4_GROUP_RESTART2_EXACT.json`.
Читаются level/EXP/SP/expBeforeDeath, HP/MP/CP/max, native XYZ/heading, class/race/
vitality, полный inventory и skills, completed context/state/receipt identity.

Ранние отрицательные whole-group проверки не переписаны: R2 raw fractional witness;
C_R4_A7/8 и B2 7/8 со старым363 witness до последующей native смерти/прогресса.
Эти расхождения не объявлены потерей данных и не заменены новым ожидаемым anchor.

## Две final natural scenes на одном frozenSHA

| Scene | Seconds | Cohort | Primaries | Max sample gap | Farm |
|---|---:|---:|---|---:|---:|
| FINAL_R4_D_A | 360.5194381 | 8 | 424,633 | 3.6488958s PASS | 0/8 FAIL |
| FINAL_R4_C_B | 361.7400466 | 8 | 450,872 | 3.6543734s PASS | 2/8 FAIL |

Обе сцены используют frozen41692152, одинаковые stock runtime/config и observer hash
DD99D1AA3761849C041527930CCA54D82DC005EC6FDCDF925F7CDFBEA7243ED6;
pilot input hash46FEB473F4DF79EF4BC973DD243C0226C3294AC06EB31A60FE0CD8676077B722.
TTL525s, per-session caps и5 starts/JVM сохранены. Начальные полные группы, первые
две eligible primaries до baseline, missing/dead/changed actors и final120s failure
сохранены в baseline/cohort/result/all-samples. Порог5s/90s не ослаблен.
После наблюдённого COMPLETED получены новые sameepoch native rewards у6/8 d и8/8 c;
наблюдаемых orphan SEALED без pending/control —0. Это ограниченное census evidence,
не доказательство полного farm/cohort gate. C scene выполнена после второго actual
restart; полезная native жизнь есть, непрерывность всей группы не доказана.

Предыдущие watchdog aborts и сцены с max sampling gap>5s остались diagnostic RED.
Точный watchdog condition UNKNOWN. Task observer исправил command-counter scope,
instanceId setup, independent single heartbeat writer и measured sampling headroom;
heartbeat не продлевает absolute TTL и evaluator не менялся.

## Regression и drain

Final focused recovery8/8, stock drain/binding3/3, current intent10/10,
original retaliation3/3, native review2/2, ecology30/30 и6/6,
server shutdown8/8. Owned-store matrix PASS; pending/restart/native-Z/fighter/mage
targeted routes PASS. Полный список и paired base — FROZEN_R4_REGRESSION_SUMMARY.json.
Handoff15/15 выполнен на R3; final R4 отдельно проверяет ecology handoff6/6.

Новый relevant RED не объяснён: R3 lawful boundaries8/12 против base9/12;
R4 raw native2/3 против unchanged base3/3. N02/S12: secondary stock native kill
получает реальные EXP, но native killSequence=0. Это не paired TEST metadata barrier,
не результат для GREEN и не разрешение переписать READ-only native evidence semantics.
Unchanged RED не повторяется для удачи; first producer UNKNOWN.

Scope/admission/timer/dynamic fixtures имеют отдельные paired hardcoded shared TEST
identity barriers. Shared metadata/checksum не менялись. Legacy V12 оставил orphan
только в own fixture025b; original dump был guarded imported после full private backup,
без live product repair. Последующий focused local recovery2/2.

E первый graceful stop дал ACTION_DRAIN timeout `EVENT:ON_ATTACKABLE_KILL:RESERVED`,
до последующего subsystem stopped=true. Поэтому CALLBACK_DRAIN/CLEANUP не PASS.
Последующие c/d stop dumps показывают bounded cleanup и idle workers, не доказывают
конкретную причину E timeout. Нет объявленного executor starvation или global fix.

## Planned crash — отдельные флаги

AFTER_NATIVE025e действительно выполнен один раз на свежем JVM25128, exact epoch
149330882822900/profile872/receipt5803, REALcount0, exit72. Witness, receipt, SQL и
pre-dump hashes совпали: `CRASH_AFTER_NATIVE025_FRESH/planned-crash.properties`.
Before recovery: VERIFY_PENDING5803/context PENDING14, native AFTER canonical уже
записан. Production sameDB recovery убрал receipt и завершил context. Strict
`E_CRASH_RECOVERY1_EARLY_EXACT.json` имеет receiptIntegrity=true/finalized=true,
но native XYZ отличается:46195,41458,-3504 →53398,63278,-3480; остальные поля exact.
К моменту SELECT уже state5817/context28. Точная граница изменения XYZ не доказана;
поздний SELECT не объявлен exact proof до ordinary baseline/admission. No manual repair.
Полный CRASH_PASS=false; optional AFTER_FINALIZE не выполняется до закрытия первого
crash gate. Старый rejected attach на установленный observer не считается crash.
Второй actual crash-recovery restart тоже finalized/receiptIntegrity=true, strict XYZ
FAIL. Между first stopped SQL и second early SQL characters/items/skills/durable hex/
decoded TSV совпали byte-for-byte (E_CRASH_RECOVERY2_IDEMPOTENCE.json). Native
continuation872 не доказан: его не было в final census новой discovery. Последующие
empty/healthy stops не отменяют E first callback timeout.

Death/soft-return отдельные contract scenes NOT_RUN. Естественные смерти в final
cohorts не подменяют их gates. REAL client и M1 closure не заявлены.

## Closeout

Итог FAILED определяется выполненными final product RED, а не истечением бюджета.
Новые semantic edits остановлены: hypothesized geodata/sensor/callback/XYZ причины
не превращены в архитектурные fixes. Следующий task автоматически не запускается.
Own clones/evidence сохранены, main/foreign/kpCat не изменены, PLAY только SELECT/export.
Final process proof: FINAL_OWNED_PROCESS_LISTENER_PROOF.json — own JVM0, listeners0;
FINAL_OWNED_a/b/c/d/e/h_SQL/counts.tsv — pending0/online0. Stopped stock PID records
дают STALE_RECORD после снятия owner marker; это сохранено отдельно и не используется
как единственное доказательство остановки. Retained runtime entries после полного
process exit отсутствуют; это не исправление первого E drain gate.
Scope/encoding/publication evidence находится в этом же packet; git разрешён TASK.md.
Git-команды использовались; точные actual shell requests и разрешение — GIT_COMMANDS.md.
Final artifact commit/push SHA вычисляется после записи packet и сохраняется в own
publication-final.json и final reply, чтобы committed RESULT не ссылался на свой ещё
не существующий hash. Normal push только в заданную ветку, без force/merge/rebase.

- mojibake-маркеры в изменённых файлах проверены: authored0; raw diagnostic
  C_R3_633_NATIVE_GEO_READ.txt имеет290 старых строк-совпадений, сохранённых immutable.
- escaped Cyrillic в изменённых файлах проверены: authored0 и raw0.

UTF-8 strict decode PASS; FINAL_PACKET_QA.json содержит отдельные результаты двух
проверок, exact source allowlist и inventory exclusions. Source count10: production5,
new suites2, existing test edits2, helper1. Изменений вне SOURCE_MAP0, frozen source
diff empty. Inventory покрывает task files и десять source paths; его два output
файла исключены из собственной hash recursion. Максимальный raw файл менее5MiB.
Task-only .gitattributes следует byte-preservation паттерну024 и сохраняет hashes
raw evidence, frozen observer и inventory при Windows/Unix checkout.
Whole raw `git diff --cached --check` имеет CRLF/trailing-space diagnostics от
immutable console output. Повтор с per-command cr-at-eol отделил реальные raw spaces;
raw не переписан. Exact production/test diff и34 authored task paths отдельно
прошли whitespace check. Это delivery check, не изменение product evaluator/guards.
