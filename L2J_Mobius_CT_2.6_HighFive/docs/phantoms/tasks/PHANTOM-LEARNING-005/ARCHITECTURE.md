# Архитектура: встроить comparator, не новый GameServer

Цепочка: `pre-CUT facts -> legal native actor/world setup -> pair manifest -> two independent native arms -> effect+terminal receipts -> match validator -> immutable paired ledger -> offline result+uncertainty`.

## Единственный источник истины
Использовать существующий `GameServer`, `Player`, `Skill`, `CombatService`, `PartyTactics`, `TacticalPolicyV3`, V1 `LearningPolicy`, `TacticalEpisodeV3`, native actor fixtures и G0 Director. Никакого второго симулятора боя или формул урона, КД, маг защиты и лечения. Не использовать контролируемое добавление HP после боевого CUT.

## Раздельные режимы исследования
**A. SOLO_DAMAGE_V1** — stock `LearningPolicy.OFF/SHADOW` без перестановки; B — ранее `ACCEPTED` V1 DAMAGE model1 с точным SHA в TEST ON. В обоих случаях V3 либо выключена, либо строго одинаково безопасна; нельзя непреднамеренно приписать V1 результат V3.

**B. PARTY_HEAL_V3** — A: stock PartyTactics HEAL, B: точная `ACCEPTED_TEST` V3 model2 HEAL с законными альтернативами, при неизменной Damage-policy обеих сторон. Если baseline имеет другой V1 mode, выровнять его прежде CUT. Нельзя считать V3 model2 обученной DAMAGE только потому, что у общего scorer есть Family.DAMAGE.

**C. Optional**: только если A/B завершены, совместный режим V1 DAMAGE + V3 HEAL с отдельной маркировкой `COMBINED`; не обязателен для FULL, не нарушает evidence inference.

## Model admission
Загружать модель лишь после readback immutable approved id/SHA/schema/fingerprint/status из **own TEST** либо exact проверенного публичного ZIP с безопасным TEST-only import; источники и SHA фиксируются. Не читать raw DRAFT8 как active, не переквалифицировать без существующего `apply-test` / независимых gate. Никогда auto MAIN promotion; допущенные модели ON только в own isolated native trial, исходные V3/learning configs остаются SHADOW/OFF по умолчанию.

## Минимальные модификации
Реализовать версионированные `EvaluationPlan/ArmReceipt/PairResult` (названия после аудита исходников) в test/eval слое; optional SQL таблицы только при необходимости для resumption; reuse 004 episode/receipt serializer там, где точные поля подходят. Не переписывать `Training.ps1` Windows G0/Job, Core, Party, Geo, rates/skills. Не дублировать Actor/profiles/World рендер. Побочная абстракция допускается только после доказанного кода seam, а не на всякий случай.

## Вне критериев
Планировщик NIGHT/LEAGUE 006+, PvP и multi-class rollout — будущие интеграции. 005 не вводит «вторую модель на каждый класс» и не меняет 4.52MiB семейство/архитектуру.
