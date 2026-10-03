# State machine / invariants

## Materialization lifecycle — сохранить существующий

STORED → CLAIMED → LOADING → MATERIALIZING → ACTIVE → DEMATERIALIZING → STORED.
Ошибка → FAILED с удержанием ресурсов до доказанного cleanup.
Ключ actor instance: (profileId, objectId, materializedAtNanos); Player reference также exact.

ACTIVE: admission может выдать lease только актуальному owner и текущему runtime goal.
DEMATERIALIZING: новые lease закрыты; admitted work дренируется; native work quiescent;
owned-store snapshot/commit; native delete; outbound/identity release после postconditions.
FAILED: admission не открывается лишь потому, что pendingIntent=null или новый human рядом.
STORED: нет World/Player/tasks/owned lease. История incident остаётся detached.

## Native session sub-state

ABSENT → REGISTERING(P1) → PAIRED(P1) → RETIRING(P1) → ABSENT.
Replacement P1→P2 имеет отдельную identity; callbacks P1 могут удалять только P1.
PAIRED: AutoPlay policy=P1 && AutoUse policy=P1 && Session policy=P1.
PARTIAL(P1): хотя бы одна регистрация отсутствует; running=false, repair under owner.
Missing policy + managed Player: SKIP, НЕ stock branch.
Stale Player epoch: reject; старый policy не переносится на новый instance.

## Failure record

NO_INCIDENT → FIRST(E1) → RETRY(E2,...) с first=E1.
Успешный cleanup не удаляет forensic запись из bounded service archive.
Событие при cleanup после primary materialization/store error добавляется secondary,
не становится ложно первичным. Error продолжает распространяться.

## Journey

PLANNED → NATIVE_PROGRESS → ARRIVED/capture → NEXT_LEG или FARM.
RETRY_WAIT сохраняет общий attempt budget exact goal/revision.
TERMINAL → TYPED_RESOLUTION → cooldown/replan/incident/store-resume.
TERMINAL → новый идентичный Journey с обнулением бюджета запрещён.

## Lock / callback invariants

Manager lock: только registration state, никогда wait/drain/DB/native combat.
Никакого вложенного AutoPlay-lock → AutoUse-lock и reverse.
Lifecycle/action ownership не заменяется manager monitor.
Каждый late callback проверяет ожидаемый owner/epoch; current lookup не выдаёт P2 права P1.
Post-finalize authoritative runtime mutation не маскируется snapshot rewrite.
