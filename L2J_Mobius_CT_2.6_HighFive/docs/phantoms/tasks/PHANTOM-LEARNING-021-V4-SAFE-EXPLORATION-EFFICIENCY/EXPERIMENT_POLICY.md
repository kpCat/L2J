# V4 Experiment Policy v1 — поведение и математика выбора

## Принцип

Один общий арбитр действий. **Не** цеплять параллельно старую `TrainExplorationV1` и новую V4: сейчас V4 получает `critical=true` при `_exploration != null` и возвращается к stock; двое арбитров меняют смысл propensity. Legacy route не ломать, для opt-in нового TEST применить ровно одну owner policy.

### Контекст

Принятые действия: immutable `TacticalObservationV3`, `List<LegalActionV3>` с >=2 legal/exact/current `DAMAGE` одного owner/actor, выбранный stock, текущая V4 score, `encounterId`, `actor profile/epoch`, scene + policy/seed, `TRAIN/TUNING/AUDIT`, safety signal и native deadline. Берём только ранее законные alternatives; не создаём несуществующие навыки.

### Выбор

- BASELINE: stock, `propensity=1`, reason `CONTROL_STOCK`; используем для контрольных или явного baseline. Обязательно флаг изначальной политики.
- MODEL: V4 в ON с прежним margin/budget fallback; если V4 выбрала stock, это всё ещё MODEL, не эксперимент.
- EXPLORE_COVERAGE: только TEST TRAIN, заранее (до CUT) фиксируется policy ID, seed, deterministic rotation по неизменному набору skills и cap1. На первой **безопасной** eligible-возможности выбери ещё не исследованный не-stock навык из offered, если он реально доступен; для этого условного deterministic decision `P(selected)=1`. Никакой подгонки seed после выполнения и никаких попыток подменить action/effect. После одного принятого альтернативного решения режим BASELINE; если безопасность не позволяет — stock. Это обеспечивает проверяемый эксперимент без надежды на удачный random draw, но НЕ даёт randomized causal advantage proof.
- EXPLORE_EPSILON: только явно разрешённый TEST TRAIN, safety GREEN. Вероятность *исследования* `ε` из `[0.05,0.20]`, рекомендованный старт `ε=0.15`; **до CUT фиксируется политика, seed и cap, а после CUT для каждого решения — выбор ДО native cast**; stock получает `P(stock)=1-ε`, одна из `k-1` альтернатив — `P(each alt)=ε/(k-1)`. Это распределение верно только если альтернативы не отфильтрованы после `draw`. Log `k`, `ε`, index, exact propensity, draw, policy ID и reason. Если policy неeligible из-за cap/critical, действительное распределение становится BASELINE P(stock)=1. Без манипуляции seed по исходам; seed/plan закреплён до боя. Динамический decision собирается до исполнения навыка, bounded buffering; после drain — atomic publication и SHA, неполный буфер не даёт training credit.
- Версии `COVERAGE_TEST_V1` и `EPSILON_V1` нельзя путать: deterministic coverage с P=1 не считать случайной выборкой, не использовать её как IPS-взвешенную epsilon-оценку. В дальнейшем допускаются другие versioned policies, но только с реальными известными probabilities.
- В TRAIN допустим контролируемый fallback от MODEL к EXPLORE, но записывать единственного decision-owner и выбор режима до применения. Не списывать изменение skill на V4 model preference, если оно произошло из-за EXPLORE.
- TUNING/AUDIT: никакой stochastic exploration, никаких gradient credits. Их логика определяется отдельным frozen evaluation protocol, не текущим TRAIN.

### Безопасность

- Отказ при отсутствующем/stale owner, illegal skill/target/level, недостатке HP/MP, exhaustion, stun/silence, missing LOS/Geo, on-cooldown, unknown candidate, current cast, critical heal/rescue. Fallback **исходный stock**; не выдавать HP/MP, не менять крит/урон/прохождение.
- Не выбирать `RETREAT/BSoE/Return` для PvP; в021 изучается только DAMAGE/Solo PvE. При появлении новых families отдельные adapters проверяют family safety, не наследуют damage HP threshold слепо.
- Для COVERAGE_TEST_V1 max1 безопасный альтернативный decision (после его совершения следующий baseline). Для EPSILON_V1 bounded experimental budget из нескольких (или bounded cap ≤20% noncritical eligible actions при будущих циклах); после исчерпания cap режим `BASELINE` с `propensity=1`, не приписывать ему ε. Максимальный overhead выбора ≤ существующего native5ms budget; никакого синхронного fsync на каждом касте. Не гарантировать альтернативу ценой обхода gate.
- Успех native proof: EXACT decision captured before native cast and published with post-drain SHA → GameServer executed selected alternative (`selected != stock`) → native receipt with actualHp/actualMp/time and same owner/generation/event; + baseline control executed safely. Если эксперимент отклонён сервером — считать отказ и не использовать как paid effect.

### Важное уточнение

Один случайный крит/kill не доказывает superiority. Нельзя записывать «навык A лучше навыка B» из одного боя. Контрфактический результат невыполненного навыка неизвестен. Использовать частоты, контролируемые сопоставимые сцены и uncertainty; IPS/importance weighting только если записана честная известная propensity. В021 не менять training weight-семантику V4 на основании недоказанной контрфактики.
