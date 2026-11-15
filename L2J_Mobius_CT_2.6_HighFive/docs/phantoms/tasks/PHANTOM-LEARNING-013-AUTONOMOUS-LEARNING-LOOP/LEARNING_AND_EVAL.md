# LEARNING_AND_EVAL013 — правдивое обучение и проверка

## Обучение

- При G0 сделать cold export: модель acceptedTEST2, working DRAFT14, Adam903/223, dataset/replay, 48 applications и pointer12 — сравнить exact фактический current state с предыдущими hashes.
- Native TRAIN получает только реально оплаченные действия с proper actor/target/skill/owner identity, exact HP/MP, terminal reason, однократным credit; pre-CUT отказ не превращать в поражение.
- `ActionAdvantageV2`: минимально необходимы 3 независимых TRAIN control blocks/cell (leave-own-block-out), отдельные splits и достаточные fresh classes/situations. При недостатке — `INSUFFICIENT_EVIDENCE`, не снижать `sufficient()`.
- Проверить диагностически: долю TRAIN candidates с ≥2 legal alternatives, число actually paid exploration alternatives, balance по классам, class+skill+grade+level, насколько часто policy всё равно fallback к stock, качество меток, пропуски terminal, optimizer gradient norm, saturation и selectedEpoch0. Не писать фейковый MSE и не переводить устаревший AUDIT в TRAIN.
- Если нужно улучшение objective/reward/feature schema, выполнить на дешёвых тестах причинный аудит; **не менять** historical V2 revision, просить решение владельца. Возможность ухудшения качества естественна.

## Независимая V2 EVAL

- Перед CUT предъявить manifest source/JAR/config/parent/candidate/seed/provenance. Подтвердить Solo/Party, две категории относительной нагрузки, не менее 2 terminal-comparable AB/BA pairs каждого направления если хватает законных данных. Смерть/смерть, смерть/победа, bounded timeout — валидные терминальные исходы при полном доказательстве, а cancelled/stale/unknown не сравнивать.
- `stock` и кандидат в matched arms имеют одинаковые pre-CUT physical facts и independent native RNG. Не замораживать результат, не нормализовать HP/MP после CUT, не подбирать профили по побеждаемости.
- Все pairs и варианты (включая отрицательные исходы) входят в denominator. Реально полученные `paidLegalDivergences` vs `fallback` отдельно от `rankChanged`.
- Нормативное доказательство преимущества требует достаточного независимого n и заранее объявленных effect sizes/CI; 2 пары Solo +2 Party дают только `INCONCLUSIVE`. Если нет фактических divergent paid decisions — `NO_POLICY_DIVERGENCE`, если нет достаточного сравнения — `INSUFFICIENT_POWER`.
- Сравнение новой модели с **best TEST** не эквивалентно сравнению со STOCK: для заявления new>best необходимо собственное matched native comparison с best либо явно отметить UNKNOWN. Не выдумывать третье плечо в пределах 2-arm V2. Старую лучшую модель не перезаписывать.

## Результат каждой итерации

В одном machine-readable отчёте: `accepted`, `working`, `selectedEpoch`, `optimizerContinuationSha`, `optimizerUpdates`, `train/controls/tuning/audit`, `auditCandidate/teacher/constant`, `V2 candidates`, `V2 result`, `policyPaidDivergence`, `coverage`, `n`, `uncertainty`, `bestDecision`, `stopReason`, `historicalUntouched`.

Запрещено называть `FULL013` доказательством сильной стратегии или утверждать, что V3 управляет обычными фармерами, PvP или рейдами.
