# Сценарии и RED→GREEN

Три standalone suites, existing compile-tests/test APIs. Не создавать новую test framework. Регрессионные сценарии ниже обязательны как coverage; production менять только в случаях с подтверждённым RED. Existing passing semantics не «улучшать» профилактически.

## S01–S07: выполнение и регистрация

S01 Freshness: lastDecisionReason=autoplay_running и старый age при native autoPlay=false не дают live RUNNING. Отдельный fresh snapshot отражает оба значения без изменения Player.
S02 Composed continuation: реальные production binding/current-goal hooks → manager ticks → native cast/attack → reward → next target. Проверить завершение последнего dispatched work, а не только starts. Никаких успешных mock AutoPlay/AutoUse.
S03 Current temporary checkpoint: никакого нового combat/pickup под closed admission; после реального успешного checkpoint тот же current owner может продолжить. Временная пауза не маскирует failure/permanent drain.
S04 Revoked/stale session: queued old tick/stop после создания replacement не меняет successor registration/target/epoch. Оба manager registries согласованы.
S05 REAL control: policy==null сохраняет обычную ветку, login/AutoPlay/AutoUse не зависят от Phantom permission. Допустим native connected-independent REAL-like fixture с явной маркировкой, не fake REAL_LOGIN.
S06 Fault isolation: два actor в одном настоящем pool; остановка/revocation/exception одного не прекращает tick/next target другого. Не считать общий pool «здоровым» только по contains(player).
S07 Exactly-once finish: queued cancel, running cancel, callback throw, rejected submission. Не остаются running tickets после возвращения тела; EARNED не теряется; context unwind совпадает с captured owner. Для доказанного deadlock — latch-driven RED и completion timeout, не случайный sleep.

## S08–S12: evidence и награды

S08 Phase lifecycle: временной budget истёк → firstUnprovenReason=точная причина, sticky флаг сохраняется. Реальное завершение эпизода до deadline очищает только свой token; stale completion не очищает successor. Invalidated sensor не «чинится» baseline reset.
S09 Reward ordering: damage/kill/EXP/SP/next-target в допустимых native порядках; cycle ровно один. Никакой цикл от self-heal, агро, попытки каста, чужого убийства без native reward, ручной вставки EXP. objectId повторно использован с другим spawnGeneration → другая цель.
S10 Capacity/foreign epoch: MAX_TARGETS boundary и неизвестный target честно UNPROVEN; старый owner/epoch не пишет в replacement. Не увеличивать16ради теста.
S11 Native reward truth: успешные killed/reward данные сверены с реальными Player EXP/SP и после штатного store с canonical rows. Изменение уровня не считается reset опыта; использовать native actualExp/Sp writer значения. Не присваивать counters по таблице EXP.
S12 Loot: реальный native drop или явно помеченный controlled TEST item, штатный pickup/autoloot, фактическая inventory delta. Защищённый чужой item остаётся нетронутым. TEST setup не считается естественным connected loot.

## S13–S16: конфликтующая внешняя работа и завершение

S13 Если Q00255 подтверждён — no-state onKill возвращает безопасный допустимый результат без запуска новой quest/выдачи items; stateful REAL проходит прежнюю ветку. Если причинность для выбранного stall не подтверждена — NOT_CAUSAL, script source не менять.
S14 First failure truth: сопоставить owner.firstNativeIncident с исходным throw/stop, не заменять поздним cleanup exception. Не очищать failure и outstanding ради продолжения.
S15 Stop живого farm actor: прекращаются его exact registrations, выбранные running callbacks завершаются, новые ordinary roots закрыты. Выполненные earned rewards сохраняются. Никакого self-drain. Отдельный общий retained actor фиксируется, не стирается.
S16 Cohort progression: два/несколько native actors не должны взаимно лишать всех следующей цели после одного убийства; фактическое распределение наград остаётся stock. Не требовать награды каждому участнику чужого kill.

## Golden native smoke — перед ручным входом

Существующая composed environment из TASK018/020/021; production bindVisibleLife и штатные AutoPlay/AutoUse с реальными timer/cast callbacks. Fixture может поставить тестового actor и законных mob на тестовую карту, но не наносить урон/давать EXP/последовательно запускать manager вместо production workflow. Отдельно показать path, который fixture не покрывает; не объявлять его полным production connected proof.

Минимум5последовательных cycles + запас ещё1next-target, actualEXP/SP, direct inventory check, clean owned stop. Механический ручной вызов startAutoPlay и 5doDie НЕ smoke. Если fixture не умеет нужный production loop, reuse уже существующий production-style driver; не разрабатывать новый harness вне бюджета.

## Обязательная регрессия

TASK021 intent10/10 + local7/7; TASK02018/18; TASK01814/14; ecology30/30; handoff6/6; decision-core36/36; decision-persistence23/23; recorder3/3; background lifecycle4/4. Дополнительно только затронутые existing native ownership/evidence/AutoPlay suites.

Известные GK6/7, native travel0/1 и NATIVE_WORK_SELF_DRAIN22/23 не превращаются в GREEN. Если выбранный fix затрагивает связанный failing test — прогнать и проверить root; иначе записать как прежний долг с base evidence без повторного широкого прогона всей WORLD-инфраструктуры.
