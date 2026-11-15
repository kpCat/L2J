# BLOCKER013 — HEADLESS_PREPARATION_TIMEOUT

Статус: **PARTIAL013_PRE_NATIVE_ADMISSION_UNFINISHED**. Расход native0/4GS и0/4800000ms. Новый Goal не создан. Это технический timeout подготовки, не доказанный застой и не доказательство отсутствия всех законных сцен.

## Наблюдение

Final fixed-seed R2 Prepare из TEST006 на source `d12a506a520b958e2bac82c247c4da95c43d5b16` не завершил полный TRAIN admission за existing600s worker deadline. Wrapper вернул `TRAIN002_WORKER_TIMEOUT_NO_FORCE`, exit1. Сохранились2 admission-only proof,0CUT,0sealed training-admission013.json. TRAIN controls/FIT/new model/EVAL013 отсутствуют.

Два ранее установленных отказа остаются законными: HEAL без поддерживаемого stock offer; Party MP required3060/available2806. Их исправления меняют только классификацию/исключение до CUT, не eligibility. В R2 чистый MP отказ уже правильно назван `AUTONOMOUS_PRE_CUT_PARTY_MP_REFUSED`.

После600s generic worker timeout сам по себе не посылает cohort-scoped stop.request для standalone Prepare. На626s операторская оркестрация записала существующий cooperative stop.request в exact own R2 cohort. Worker штатно завершился с `AUTONOMOUS_PREPARATION_REQUESTED_DRAIN`; force kill не применялся. Затем canonical Stop, fresh Export/5SELECT и final OFF/Preflight подтвердили safety. Этот ручной drain не является доказательством автономного STOP/RESUME013.

## Причина и граница доказательств

New013 full pre-CUT cohort planning включает materialization, естественное восстановление ресурсов, оба плеча и отклонённые proposals. Он выполняется через прежний Invoke-TrainJava с default600s. Текущий timeout не согласован с полной стоимостью этой подготовки и её cohort cancellation. Нельзя подменить неполную подготовку PASS, увеличить patience или стартовать GS. Полученные2 proofs не доказывают sufficient cohort: минимум3cells при текущем reserve layout,>=2classes и>=2scopes ещё не подтверждены. Не доказано также, что увеличение времени даст нужные данные.

## Остановка по task

PLAN.md: «в рамках одного Goal допускается до двух непосредственных исправлений на доказанном causal path. Третий unrelated blocker ... — остановиться с diagnosis/вариантами». Два direct preflight исправления уже использованы; runtime timeout/cancellation — дополнительный control-plane blocker. Его исправление не выполнялось, budget не расширялся.

## Конкретные варианты review

1. Принять текущую PARTIAL-доставку. Defaults OFF, исходники и история сохранены; native/FIT/new EVAL не выполнены, FULL013 запрещён.
2. Отдельно разрешить третье bounded исправление внутри того же Goal и прежнего exact10: только Training.ps1, Native adapter и existing Test-Training.ps1. В существующем Director связать013 headless operation identity с cooperative cohort drain, заранее закрепить отдельный ограниченный headless deadline и его reserve, корректно различить timeout/NO_DATA; crash/STOP не должны повторять зарезервированные CUT. До реализации согласовать конкретный headless cap; текущий native4GS/80min не увеличивать. Добавить один focused timeout/drain/recovery contract в existing runner, затем один headless readiness pass. Это предложение, а не выполненная доработка и не обещание FULL.

Ни нового supervisor, ни objective/schema/reward, ни MP/Party/Geo guards, ни MAIN/PLAY здесь не предлагается менять. Дополнительные native запуски без полного legal admission запрещены.
