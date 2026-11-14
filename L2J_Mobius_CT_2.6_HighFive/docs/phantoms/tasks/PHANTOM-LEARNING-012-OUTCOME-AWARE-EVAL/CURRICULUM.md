# CURRICULUM012 — законная сложность без outcome cherry-picking

## Цель

Испытательная задача не должна быть искусственно непобедимой, но и не должна превращаться в подбор моба, который гарантированно умирает от V3. Смерти — важная информация, не ошибка генератора.

## Три независимые стадии

1. **Eligibility**: конкретный Player с реальными learned skills/rank/enchant, loadout, owner, action/target readiness; NPC source, HP/level, Geo/LOS/faction/zone, canonical `request` constructor. Нет права заменять реального персонажа «идеальным».
2. **Difficulty estimate**: до CUT оценка доступного риска по штатным GameServer фактам — разность уровней, реальные NPC HP/атака/защита и сопротивления, role/support, MP reserve, угроза/число целей; если информация отсутствует — `UNKNOWN`, а не invented DPS. Договориться о ограниченных заранее указанных категориях без обучения на исходах EVAL.
3. **Sampling**: deterministic seed, заранее зафиксированные квоты и порядок AB/BA для Solo и Party; экспериментальные метаданные immutable, исходы A/B не влияют на выбор следующей цели в этой же immutable кампании.

Проверить, что новые категории сложности не дублируют и не ослабляют штатные gate `viableTarget`, `farmRewardAvailable`, правильный level gap, `magicLevel`, health/MP/shot resources и authority rules. Не переписывать игровой `PhantomEngagementPlanner` для TEST.

## Роли

Solo: персонаж действительно может атаковать и имеет ≥2 готовых native skills для policy comparison; не требовать предсказанную победу. Party: настоящий состав/leader/heal/offense capability/admission и минимальные 2 альтернативы только там, где требует та же action family. Не фиксировать class IDs 94/110 или NPC20965/20966 в новом общем алгоритме.

## Условные группы

`LOW_RISK`: легальный native target с относительно меньшей исходной угрозой. `ORDINARY`: сопоставимая угроза. `CHALLENGING`: повышенный риск. Это лишь **пример**, реальные правила и пороги должны опираться на canonical доступные факты и быть записаны **до анализа исходов текущей кампании**. Нет оснований классифицировать — `UNCLASSIFIED`, не фальсифицировать.

Целевой outcome/Solo death 011 используется **для проверки старой V1 ошибки и мотивации**, не для подбора границ сегментов и не для перебора seed, обеспечивающего learned advantage. Сегменты выбираются до нового native CUT. Если новых достаточных native sources нет — честное `COVERAGE_GAP`, без искусственного создания skills/loot/adena или снижения canonical constraints.

## Stop/cancellation

Только новые попытки, новый namespace, no reused reserved arms. Если stage/party cannot admit, STOP pre-CUT и aggregate counts. После CUT не менять target HP, actor HP, gear, buffs, class, timing или randomness; фиксировать terminal kill/death/timeout/cancel и все real consequences.
