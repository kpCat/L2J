# EXPERIMENT007 — протокол до первого native запуска

Статус **PROPOSED / OWNER_DESIGN_REVIEW_REQUIRED**. Код и игровые эксперименты ещё не реализованы. Версия предложения 1; изменение констант после collection требует новой явной версии и отдельного frozen dataset, без переинтерпретации старых records.

|Поле|Предложение|
|---|---|
|campaign / dataset|learning007-causal-v1 / learning007-advantage-v1|
|objective|CAUSAL_ACTION_ADVANTAGE_V1|
|parent|Terminal Adam9 f76ea960…; terminal weights dc82041d…;817updates/172epochs|
|exploration|TRAIN B only,epsilon.25,uniform non-stock alternatives; shippedOFF|
|opportunity|первое полностью eligible decision в episode; максимум 1, даже при coin=stock|
|safety|current owner/epochs/ready legal set/native permission; emergency excluded;5ms/cooldown2000/margin.025 сохранены|
|TRAIN|32block attempts,16Solo/16Party;64episodes;32B opportunities|
|held-out|8TUNING и 8AUDIT episodes; explorationOFF; independent identities|
|native limits|8GSstarts/90min total; collection≤3starts/60min, unused quota не повторяется|
|EVAL|4pair attempts/mode, proof включён;1stock-stock pair/mode;20arms total|
|activation|DRAFT остаётся TEST-only, production promotionfalse|

A/B порядок по 8TRAIN blocks внутри каждой из 4physical cells: AB,BA,AB,BA,BA,AB,BA,AB. Между cells чередуются Solo/Party; смещения времени сохраняются. Setup fingerprint требует exact class/level/skills/gear/HP/MP/CP/NPC/positions/Party/Geo/LOS/rates/objective. Actors и target spawn generations всегда свежие. Значения 4cells выбираются из фактического catalog до collection и входят в hashed protocol; они не подбираются по model scores или исходам.

На B при K candidates: `p(stock)=.75`, `p(each other)=.25/(K-1)`. На A stockp1. Записываются conditional propensityB и deterministic arm assignment отдельно; смесь A/B не выдаётся за propensityB. Gradient использует только B TRAIN, A — только TRAIN baseline. Выбор без effect, cast reject, stale owner, safety/budget/cooldown fallback сохраняется и не получает причинный target.

Для paid selected decision сохраняются полный legal set, stock/selected, owner/campaign/episode/block/cell/split, actor/target incarnations, модель/SHA, RNG draw и вероятность, eligibility/reason/fallback/budget, native receipt/action key/SHA, HP/MP write times, before/after/useful effect и canonical terminal boundary. Регистрируется максимум один такой target/episode; остальные native actions остаются обычной тактикой и raw evidence, без advantage credit.

Старый terminal rewardR неизменен. `L=clip(2*usefulHp/targetMaxHp-paidMp/500-effectElapsedNanos/10e9,-1,1)`. `U=.70*R+.30*L`. `b` — средний U A TRAIN того же physical cell, исключая свой matched block; минимум 3otherA. `y=clip(4*(U-b),-1,1)`. Вес B `min(4,1/(K*pB))`, делённый на средний TRAIN вес. Scale4 и weights.70/.30 фиксируются до trials; threshold.025 не используется для настройки reward.

Для baseline используются только actual paid A decisions с сопоставимым начальным context. Изменение ready set внутри decision сохраняется; его effect не приписывается отсутствующей альтернативе. Если eligible opportunity наступает в несопоставимом контексте, эпизод сохраняется как unmatched и не создаёт advantage target. Exact tolerance/key для decision context фиксируется в implementation contract до smoke; он не подбирается по outcomes.

TUNING/AUDIT target diagnostics используют замороженный TRAIN baseline без пересчёта по held-out. При отсутствующем matching context — UNKNOWN. TUNING выбирает checkpoint. G4 admission до EVAL: минимум 16fresh native held-out observations, минимум 4 в каждой комбинации TUNING/AUDIT×Solo/Party, каждый legal set≥2. На каждый режим — минимум 2neuralTop≠stock с gap≥.025, включая≥1TUNING и≥1AUDIT. Controlled disjoint observed outcomes должны давать изменение относительного порядка и корректный weighted gradient. Уверенный stock gate не закрывает. Только прошедший режим допускается к массовому native EVAL; иначе NOT_RUN/G6 незакрыт. AUDIT однократный admission/report, без objective/hyperparameter tuning; после defect fix нужен unused audit внутри старого бюджета, иначе PARTIAL. Отсутствие outcome альтернатив в held-out не скрывается; score gap не доказывает causal superiority.

Предварительные campaign STOP conditions: limit starts/wall/slots; неверный installation/campaign/split opt-in, manifest/durable budget failure или exploration фактически исполнена вне разрешённой safety; insufficient validated train/baselines; отсутствие distinct observed actions; incoherent lineage; независимый G4 не пройден. Обычные stale owner/epoch/readiness/safety transitions конкретного decision дают штатный fallback, NO_TARGET и spent opportunity; следующий episode только после current guards. Отдельный 5ms overrun также fallback, не обход. Остановка сохраняет raw records; красивые исходы не являются основанием повторять series. Новый budget или изменение игровых gates требует отдельного решения владельца.

Оценка EVAL: denominator attempted/completed/matched/paid divergence по режимам; paired deltas и диапазон/bootstrap interval с предупреждением о малой выборке; controls отдельно. Death/loot/adena/consumables без evidence UNKNOWN. Итог ADVANTAGE_SUPPORTED допустим только с осторожной ограниченной формулировкой; при малом n обычно INCONCLUSIVE/NO_ADVANTAGE_OBSERVED. Если самостоятельного neural≠stock нет — NO_POLICY_DIVERGENCE. Exploration receipt не закрывает learned G5.

Ограничения causal inference: stochastic damage/crit/AI/timing, неполное state observation, condition-on-eligible/paid bias, clippedIPS, smalln, matched physical setup не равен counterfactual. Один исследуемый decision уменьшает interference, но не устраняет её. Проигрыши/timeout/refusals остаются в census; нельзя исключать их из отчёта, даже если для них нет допустимого gradient.

Operational amendment до любых TUNING/AUDIT: фактический throughput ≈39.7s/episode и conservative headroom14.5мин требуют уменьшить TRAIN attempt ceiling с32 до24blocks в прежнем native budget. По6blocks/cell, первые6orders AB,BA,AB,BA,BA,AB; слоты48–63 явные NOT_RUN_BUDGET без fabricated episode/effect. Held-out8+8 сохраняются, private preparation upper bound следующего cold segment120s вместо420s. Immutable `protocol-amendment-budget-v1.json` до start3 фиксирует это решение. Objective/epsilon/gradient minimums/safety/90мин/60мин/3collection starts не меняются; при insufficient data остаётся PARTIAL.

Интерпретация ключа matching: exact pre-CUT physical cell и legal action shape задают policy-cell baseline. Decision-time непрерывные HP/MP/CP/distance не совпадают точно; не заявляется exact-state causal matching. A/B pre-draw gates одинаковы; stochastic state variance и paid-selection bias остаются ограничениями. G4 использует все paid held-out observations из frozen episode, сохраняет split и признаёт их корреляцию; gradient только для одного выбранного TRAIN action.
