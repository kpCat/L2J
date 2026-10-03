# Обязательные сценарии actual source

Все fault/fixtures/barriers только TEST gate. Никакой инъекции ошибки в PLAY.
RED — ожидаемая assertion failure реального неправильного поведения, не compilation error.
Тесты межпоточных окон используют latch/barrier с bounded timeout, не random sleep.

| ID | Scenario / assertion |
|---|---|
| P01 | Actual pool iterator выбрал managed Player, stop удалил policy/pool, loop продолжен: no native action, no noop lease |
| P02 | Managed Player после снятия outbound/boundary attachments: stale loop не становится stock; no retained static Player tombstone |
| P03 | REAL/offline native Player без Phantom policy сохраняет stock AutoPlay/AutoUse; synthetic не классифицируется по client==null |
| P04 | P1 captured → P2 samePlayer registered → P1 lease null: P2 membership и flags сохранены |
| P05 | P1 throws during native tick после P2 registration: catch обоих managers не снимает P2 |
| P06 | Conditional-stop comparison/CAS paused → concurrent P2 start: ни одного final P2-without-membership outcome |
| P07 | Visible Session publish/start/stop overlap: stale expectedSession rollback не удаляет current session; no nested-manager deadlock |
| P08 | AutoUse отсутствует, AutoPlay=true: running=false; next legal owner step ремонтирует pair без rematerialization |
| P09 | Real native mage после pair repair наносит attributed damage/получает progress через stock skills; старый policy не действует на новый epoch |
| E01 | FIRST beforeStore A; retry fails B: first=A/latest=B, exact profile/object/epoch |
| E02 | store throws A, finally stopAllTasks throws B: caller получает A, suppressed содержит B |
| E03 | native Player store/resume A + boundary.afterStore B: native primary не замаскирован; completed flag корректен |
| E04 | materialize primary Error + abort cleanup RuntimeException/Error: primary сохранён, ownership не потерян |
| E05 | cleanup завершился и service entry removed: detached first record ещё доступен; no Player/Throwable refs |
| E06 | Огромный cause/suppressed/Unicode/newlines: bounds/truncation, formatter не throws, census stays within envelope limit |
| T01 | journey_deadline terminal попадает в production resolution callback, не теряется в routeFailure whitelist |
| T02 | native_move_rejected/gatekeeper_absent повторены: общий same-revision budget не обнуляется новым Journey |
| T03 | navigation timeout/overload: bounded cooldown, не permanent topology mutation и не горячий retry loop |
| T04 | stale/missing/protocol terminal: exact identity incident; new revision может иметь fresh attempt, old не вмешивается |
| T05 | Replan затем настоящий production handler проходит travel → native AutoPlay; тот же materialization epoch, полезный farm |
| Q01 | admitted tick draining + native delayed hit/cast/reward при cleanup: authoritative snapshot не меняется после PREPARE |
| Q02 | Native reward законно закончился до store: он сохранён ровно один раз, EXP/SP/inventory/canonical совпадают |
| Q03 | After native store / before FINALIZE crash fault: existing intent resumed; no recapture from mutable runtime |
| Q04 | FINALIZE success + injected runtime mutation: mismatch still detected, pending absence не скрывает original incident |
| Q05 | death/recovery/forced stop/World removal with pending work: finite safe cleanup либо retained failure; no early lease release |
| A01 | 4 eligible, один FAILED admissionclosed без travel reason: COHORT cannot PASS |
| A02 | Self-heal cast + monster target + zero attributed damage/progress: NATIVE_LIFE cannot PASS |
| A03 | Другой Phantom/REAL наносит урон: selected proof remains absent |
| A04 | Первый sample содержит старый aggro damage: baseline only, не fresh proof |
| A05 | Неполная census pagination/exception/identity change: UNPROVEN или RED, не зелёный subset |
| A06 | Умерший monster очищает aggro; native kill/reward доказан exact actor: полезный факт не теряется и не удваивается |
| A07 | Dead/recovering actor: показывать реальный recovery progress; не fail только за death и не исключать вечный corpse |
| A08 | Happy path >=4 natural TEST-mode eligible participants, >=2 usefulfarm, exact selectedproof, continuity/RESTORE/STOP PASS |

## Existing regression, не переписывать

Owned-store C/D, canonical/background parity, autosave producer guard, action drain,
production materialization23 previous cases, Pilot7 previous cases, real-login identity,
headless shutdown, native travel revision, old-policy-new-Player rejection,
new-materialization/pop-in timing, softreturn, human-locality retention, foreign-diff safety.

## Точное доказательство исторического FAILED

Model/source RED не даёт права написать «110/142/175 причина найдена».
Нужен old log with exact exception либо new diagnostic run с тем же lifecycle producer
и matching profile/epoch/stage evidence. Если old exception утрачен — явно оставить
HISTORICAL_FIRST_EXCEPTION=UNAVAILABLE; CURRENT_DEFECTS=PROVEN_AND_FIXED по actual TEST.
