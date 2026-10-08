# Autonomous runtime027

## Lanes и reuse
Base tools — docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/{Prepare,Control,Observe}026.ps1.
Сначала прочитать exact params/owner/hash guards; не исполнять старый Control026 с целью
изменять026 runtime. Новые thin wrappers меняют только own027 namespace и источник committedSHA.
Old worktree read-only:
`C:\Users\ZBook\.codex\worktrees\m1-sustained-026\L2J_Mobius`.

a own TEST; b short shutdown/death reproduction; c/d final natural scenes; e/f crashes;
g lifecycle scene; h retained cold recovery regression. 026c/d/e/h только read/export.
При отсутствии старого export — один current PLAY export, указать невозможность exact replay,
но продолжать source/native reproducible test. Shared TEST не трогать ради запуска.

## Настройки
Обычная конфигурация target1280 / active8 / maxMaterialized8 / maxScheduled10000.
Не снижать population target/rates и не фиксировать профили online. Diagnostic ON;
Synthetic ON; native TestAdmin clone access100, GM hide/invis/invul/silence false.
Все обычные mobs/AI включены. Player.client==null у synthetic не превращает его в Phantom.
Autoattach real TestAdmin сохранить, но ночью REAL login не требуется. kpCat не менять.

## Observer
Reuse026 command-counter и один heartbeat writer. Абсолютный TTL525s неизменён;
heartbeat его не продлевает. Разные lifecycle/farm сцены — разные own sessions.
До baseline можно выбрать фактическую область и разместить только synthetic observer
существующими командами. После baseline никаких указаний цели/атаки/телепорта фантомам.
Для soft-return — native dry route наблюдателя, старый async request при UNCERTAIN не replay.

## Cohort
Старые026 IDs110/142/175/260/275/278/404/447 всегда остаются longitudinal отчётом.
Для нового natural baseline включить первые8 online/local реально наблюдаемых actors
в детерминированном порядке, до получения успешных counters. Сохранить также каждый
eligible-but-unmaterialized запрос и причина capacity/readiness. Не resample после FAIL.
Если доступно меньше8, ENVIRONMENT_GAP с фактическим числом; не fake spawn/grant.

Старый026 evaluator неизменён и отдельно запускается. Death/remat gates описывают новую
эпоху явно; нельзя собрать пять циклов из разных epochs и выдать same-epoch PASS.
Если было lawful scheduled offline — отдельно доказать timeline, не просто label.

## Shutdown proof
До первого stop запустить nonblocking event trace и baseline полного списка retained
owners, включая игроков вне World. При pending S phase снять exact blocker, current
operation/transition/death counts и queue-entry времени до закрытия pools.
В event hooks: no disk/DB/foreign monitors. Ограниченный кольцевой snapshot; export снаружи.
jcmd/JFR pauses не использовать как доказательство естественной queue latency;
контрольный healthy stop повторяется без dump pauses на том же committed коде.
Ни native callback не вызывать вручную, ни Future не cancel/complete ради drain.

SEALED native snapshot должен предшествовать SQL-сравнению. World absence не равен
missing native value. No live lifetime during interval — доказать durable/receipt state
на обеих границах отдельно, не генерировать фиктивный SEALED witness.
Обычные законные изменения после release не сравнивать с PREPARE как corruption.

## Финал
Первый controlled stop: pool remains alive until accepted lifecycle complete or actual
bounded failure. Graceful process exit и HEALTHY_DRAIN — два отдельных результата.
По умолчанию own JVMs STOPPED. В MORNING — проверенная команда для сохранённого runtime,
не обещание готового сервера. Private credentials/JAR/raw DB не коммитить.
