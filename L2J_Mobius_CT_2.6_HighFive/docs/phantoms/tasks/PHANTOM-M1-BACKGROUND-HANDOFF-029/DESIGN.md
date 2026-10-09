# DESIGN029 — одно владение состоянием, разные виды выполнения

## Цель и архитектурная граница
Goal остаётся долгосрочной миссией. Native target/path/tactic — короткоживущий план,
не ключ persistence. Native Player выполняет реальные действия, existing BackgroundModel
— уже существующую ограниченную виртуальную модель. Не заявлять точное равенство native
боевой длительности: оно никогда не было контрактом BACKGROUND_MODEL_V1.
Точные контракты здесь: bonuses/points/order/commit/ownership и provenance позиции.

## A. Допуск определяется операцией, а не одним simulationEligible
В existing PhantomNativeContext добавить versioned policy facts и тип операции
`FARM`, `TRAVEL`, `REST`. Предлагаемые небольшие типы в background:
`PhantomBackgroundSimulationPolicy` (immutable captured facts, fingerprint, capability),
`PhantomBackgroundRewardKernel` (чистая арифметика, без World/DB/Player).
Не второй scheduler/controller и не разрешения по именам профилей/классов/NPC.

Все операции требуют exact identity/class, завершённый context, exact state version/digest,
no pending owned receipt, exclusive existing background owner, no current native owner.
Допуск и commit обязаны проверять владение одной линейризуемой boundary. Cached
absence snapshot или одиночный boolean не дают право параллельной записи.
FARM дополнительно требует supported ordinary policy и пригодную factual farm position.
TRAVEL требует validated path/time/instance0; неподдержанный reward bonus не запрещает
travel, если travel НЕ изменяет EXP/SP/vitality/items/skills.
REST без соответствующей модели не включать ради счётчика: он не заменяет farmPASS.
Не пропускать PENDING/UNKNOWN и не объявлять V1 неизвестные effects обычными.

## B. Backward-compatible native context V2
Сохранить native owned receipt формат/bytes и PNC1 decode. Расширить только existing
background.native-context, version2 с bounded policy capsule (<=1024bytes, no strings
unbounded). Схему SQL и глобальный BACKGROUND_STATE modelVersion не менять.
PNC1 остаётся readable, его pending-before/after доказательства обязательны; legacy
SUPPORTED points1 работает как раньше. PNC1 non1 не повышается автоматически до FARM:
новая native capture обычным existing handoff устанавливает независимые policy facts.

V2 сохраняет existing identity/phase/stateRowVersion/stateDigest/preparedVersion/epoch/
receiptDigest и отдельные before/after policy snapshots для pending stores.
В facts: версия алгоритма, configured numeric rates/caps+fingerprint, vitalityEnabled,
integer canonical vitality, background float vitality, ordinary reward eligibility,
reward modifiers до cap, lucky/consume effect class, применимость/validity horizon,
position policy/anchor lineage. Exact состав фиксировать в CODEC_CONTRACT.md до patch.
Не восстанавливать независимые bonus factors из уже capped CombatFacts multiplier.

Миграция только read-compatible + controlled native attestation; no bulk rewrites.
При native→background точка отсчёта — integer vitality реально сохранённая stock Player,
который сам проектирует float через `(int)`. Дробь native после store не выдумывать.
Внутри background successive batches переносить float в V2: не округлять после каждого
kill. Materialization следует штатной integer SQL projection; новая native capture
заменяет прежний simulation float осознанно, не «лечит» рассогласование скрытой записью.
V2 default/unknown capsule не разрешает rewards. PNC1 trailing bytes/corruptV2 rejects.

## C. Ordinary vitality поддерживается на всём диапазоне1..20000
Не менять PlayerStat/Attackable formulas. Реальные scalar paths — oracle из SOURCE_REVIEW.
В existing model добавить ordinary reward-policy overload; legacy overloads сохранить.
Один virtual encounter:
1. Native-compatible base XP/SP/penalties из существующего reward path, один раз.
2. Bonus по vitality level ДО расхода; независимые modifiers складываются как PlayerStat,
   применяются native MAX_BONUS caps. Финальные Math.round/existing SP cast на своих местах.
3. Actual native addExp/sp semantics и level transition: не использовать старый level
   бесконечно; на изменении level/loadout закончить bounded batch и revalidate next facts.
4. Vitality loss через тот же native target/level/damage calculation и float arithmetic;
   учитывать ENABLE_VITALITY, useVitalityRate, lucky, consume0/sign, gain/lost rates.
   Невит/другие timed bonuses не становятся поддержанными без полного temporal model.
5. Next points и rewards входят в один transaction result. Никаких parallel commits.

Runtime-comparison нужен с обычным monster/native Player при разных исходных points,
включая16361 и421. Deterministic encounter может быть controlled TEST; final synthetic
farm — штатные NPC/AI/спавны. Не клонировать native rewards «для сравнения» в product БД.
Не выдавать approximate virtual damage model за точное равенство реального боя.
Policy failures должны быть конкретными, сохранять native store и не блокировать
безопасное no-reward travel. Фактический common ordinary context primaries обязан
получить FARM, иначе TASK029 background gateFAIL, не ограничиваться travel/version bump.

## D. Actual position ≠ anchor centre
Native FINALIZE сохраняет actual XYZ всегда; anchor — provenance, не замена координат.
Если actual point входит в validated farming area, FARM не требует центра anchor.
Если actual point вне area, разрешён только отдельный background TRAVEL из этого exact
point к существующей подходящей area с подтверждённым dry path, topology generation,
конечным временем и existing clock accounting. No teleport Player, т.к. его нет.
До travel completion координаты не переписывать; progression/item/vitality deltas0.
На commit revalidate exact origin, path/generation и отсутствие нового native owner.
Если REAL/Synthetic приближается во время расчёта, background txn завершается либо
отклоняется по existing owner/DB protocol, а materialization читает committed состояние.
Нельзя «повысить eligibility» простым присваиванием anchor или выключить water guard.
Дальний маршрут через мир не нужен; first candidate поиск bounded по существующим APIs.

## E. Atomic background publication
Расширить существующий PhantomBackgroundTransaction.Command/ordinary commit:
expected context+policy binding; proposed vitality state; op-specific checks; existing
stable lock order/profile/goal/catchup CAS. В одном DB commit записываются state,
canonical XP/SP/vitals/XYZ/items/skills, vitality_points и rebound contextV2.
До этого commit ничего из proposed state не публиковать в runtime/progress caches.
При ambiguous commit resolve exact operation id/hash с fresh read, не повторять rewards.
Native owned PREPARE→snapshot→canonical→FINALIZE не заменять. Current PNC1 pending
receipts должны разрешаться даже после upgrade. Crash tests в COMPATIBILITY обязательны.

## F. Receipt membership не зависит от telemetry view
Текущая cohort фиксирована для поведения. Отдельно bounded union receipt-enrollment
по (profileId,objectId,epoch); capacity32profiles/128lifetimes, never silent eviction.
REGISTER до возможного first checkpoint; новая epoch отдельная row с verified parent
transition. Изменение telemetry selection НЕ забывает old receipt enrollment.
Отдельные события: PREPARED/FINALIZED/ABORTED with exact receipt/version/hash;
terminal DETACHED/permanent seal не равно temporary SEALED. После terminal больше нет
обязательств этого native owner. Facts копируются в existing queue, FS/SQL вне hooks.

SQL сразу после witness может видеть более поздний commit. Поэтому точный путь:
sealed native payload→finalized receipt→optional linked subsequent background commits→
terminal sameDB state. Поздняя SQL не «восстанавливает» missing witness.
Минимальный numeric progression дополнить точной lineage. Старые872/1272 receipts
уже упущены: навсегда UNPROVEN028, новый run создаёт новые доказательства.

## G. Прежние UNKNOWN — ограниченные независимые regression gates
Route11: исходный precondition сравнить на base028/native test без изменения threshold.
Если fixture invalid — доказать controlled real late join/damage, исправить fixture,
сохранить old FAIL. Если product regression — source-mapped RED и минимальный fix.
Stop028: снять shutdownProgress/accepted work/first throwable ДО первого stock shutdown,
включая no-Synthetic sameDB restart. Успешный второй stop не объясняет первый.
Не увеличивать10s как замену root cause. Если reproduction недоступен — UNKNOWN остаётся,
новые healthy stops отмечаются NEW_PROOF, не «исправленный старый incident».

## H. Не сужать будущее поведение
Нет привязок к profile633/759/994/1159, NPC kelтиру, one skill, level1, точной farm-area.
Тестовый alternate policy (melee/magic preferred action) меняет выбор, но не owned-store
receipt/epoch protocol. Допуск FARM/TRAVEL зависит от facts операции, не от текущей
тактики. M2 group/instance context не молча симулируется solo; native store/lifecycle
из-за неподдерживаемого background policy не должен исчезать.
