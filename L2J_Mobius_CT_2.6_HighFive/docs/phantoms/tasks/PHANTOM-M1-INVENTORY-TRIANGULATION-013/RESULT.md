# RESULT013

```text
TASK_RESULT=ENGINEERING_GREEN_RUNTIME_REQUIRED
REQUIRED_BASE_SHA=88b7dd76643cb80b78246668cd16052e740a55aa
BASE_SHA=ae6f0236f7f16039eb4b1e03f4df009943c59b91
BASE_EXCEPTION=USER_APPROVED_AFTER_EXACT_EVIDENCE_ONLY_DIFF
CLASSIFICATION=CAPTURE_PROJECTION_DRIFT
FIVE_DIFFS_BEFORE=5
FIVE_DIFFS_AFTER=0
AUTOGET_EXACT=true
FOCUSED_NATIVE=GREEN_1_1
NATIVE_CONTEXT_CONTRACTS=GREEN_3_3
ECOLOGY_18_18=GREEN
HANDOFF_REGRESSION_6_6=GREEN
BUILD=GREEN_ant_-q_jar
PRODUCTION_FILES=1
CORE_FILES_CHANGED=0
TESTADMIN_PLAY_BEFORE_ACCESS=0
TESTADMIN_PLAY_AFTER_ACCESS=100
KPCAT_TOUCHED=false
OBSERVE013_CLONE=REQUIRED
USER_LOGIN=REQUIRED
REAL_LOGIN=REQUIRED
AUTOATTACH=REQUIRED
MATERIALIZATION_120S=REQUIRED
FIRST_PROFILE=REQUIRED
FIRST_OBJECT=REQUIRED
FIRST_EPOCH=REQUIRED
WORLD_PRESENT=REQUIRED
REAL_LOGOUT_CONFIRMED=OBSERVE011_ONLY
PERSISTED_AFTER_LOGOUT=OBSERVE011_ONLINE0_LEVEL12_EXP138026_SP13880_X44131_Y42673_Z-3488
RUNTIME_STOP=OBSERVE011_STOCK_GRACEFUL_GAME14472_LOGIN17672
M1=OPEN
```

## Triangulation и fix

До production edits exact profile68/object268485428/epoch87812159084400 воспроизвёл все пять исходных diff: objects268496061/item1795/count140;268567849/1868/50;268568616/1864/10;270235841/1833/50;270570952/1873/10. Каждый = CAPTURE_PROJECTION_DRIFT. B=D, полный C=B по native locations и totals; A=B под committed tracking IDs. Это разница projection, реальные items/counts/locations не расходятся и не нормализованы.

Один production файл Authority сохраняет committed tracking IDs только при owned native arrival READY/DEAD, читая фактические Player item values. Обычный gameplay capture использует текущий goal. Core, transaction writers, full inventory hash и owned-store guards не менялись.
INVENTORY_TRIANGULATION.tsv — immutable pre-fix evidence; INVENTORY_TRIANGULATION_AFTER.tsv — отдельный zero-diff run.

## Native proof

Full historical-context suite: exit0, 1/1. Exact pre-load admitted goal/claim не меняется при load/store; свежий COMPLETED/SUPPORTED scalar proof получен до RUNNING, test nativeLoads1, original NORMAL fenced, intervalOrdinal0.
Тест различает DEAD native scalar eligibility и READY background work eligibility по существующему producer contract. Также учитывает только доказанный stale-authority renewal goal14→15 ДО admission: admitted goal/plan защищён exact comparison; никакого production renewal fix.

Полный TEST before/after aggregate E5EE30F33A9EF870383E1AEB335CCD2CBA6C418020BEB33D28DF882CD4802C55. Все native owners/infrastructure остановлены, restore journal отсутствует. Ecology18/18, native contracts3/3, handoff6/6 подтверждены приложенными existing runner reports. Jar exit0, 28s.

## TestAdmin и сохранение REAL Player

Exact PLAY SELECT/CAS/SELECT: только charId268492939/nameTestAdmin/accounttestadmin, accesslevel0→100, affectedRows1. online0/level12/exp138026/sp13880/x44131/y42673/z-3488 неизменны. Другие characters не входят в UPDATE predicate.
Пользователь подтвердил logout observe011 словом «вышел». Перед shutdown повторно проверены online0 и сохранённые поля, DB real-online count0; private agent также отказал бы при online REAL Player в World. Использованы штатные public shutdown методы без force. Game14472 и Login17672 завершены, listeners освобождены. Первый общий agent не загрузился в Login JVM из-за отсутствия Player classes; отдельный Login-only helper завершил штатный stop. Original PLAY runtime files и FOREIGN012R evidence не редактировались.

Runtime gate observe013 пока REQUIRED. До готовности сервера не заявляется materialization PASS или M1 closure.
