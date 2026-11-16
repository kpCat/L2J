# Эффективность: какие факты мы можем оценивать

**Не «победа любой ценой».** Цель — улучшать условную ожидаемую пользу действия в контексте, с ограничением на выживание и законность. Победа, убийство ключевой профессии, сохранение поддержки группы — важные outcomes, но не всё измеримо одним моментальным receipt. Случайность L2 (криты, резисты, попадания) требует повторений и честной статистики.

## Goal021 — только проверяемый DAMAGE/Solo PvE

- `TacticalReceiptV3`: exact полезный HP damage, actual paid MP, elapsed, actor/target, выбранный навыком action and owner; `TacticalReceiptV3.reward` — исходный action-local feedback, не EXP/SP и не whole-party reward.
- В новом `EfficiencyBreakdownV1`/метаданных отчёта: normalized damage relative to actual target maxHP, MP consumed, elapsed, after-cast actor resource state только при измерении, source context/class/level/target NPC/gear/known buffs, observed kill/actor death в отдельных fields с `known/unknown`.
- Учебный label 021 может оставаться строго равным исходному `ActionFeedbackV4.label()`; если вводится расширенное label, только новый schema/objective revision и отдельные веса, без изменения legacy 019/020 replay.
- Нулевой урон/фейл с отсутствующим подтверждённым writer не выдавать за native successful action. Нет time/MP? `UNKNOWN`, не0.
- Отчёт для повторяемых условий: список legal action alternatives + policies, count per skill, observed average/variance of effective damage/time/MP, optional confidence interval или честное INSUFFICIENT_SAMPLE_SIZE. Разделять baseline MODEL EXPLORE. Нельзя сравнивать средний raw damage на разных уровнях/NPC и выдавать за causal gain.

## Будущие роли — НЕ реализовывать в021

HEAL: useful HP, timeliness, предотвращение смерти — второй causal label требует подтверждённых событий и безопасного короткого окна. Самохил и PvP heal те же legal families, но разные задачи/состояния. Не вознаграждать задержанное лечение, чтобы добиться большего восстановления.

TANK: урон, предотвращённый союзникам, агрессия против мобов; просто получение урона без пользы не reward. PvP taunt/control имеет другие правила.

CONTROL/DEBUFF: native status application, probability (с учётом резистов), duration, реально прерванные/ограниченные действия с именованным uncertainty; отсутствие подтверждённой причинности не превращать в успех.

Party/PvP: групповая польза/роль цели (хилер, контроллер), HP/CP, survival, ресурсы. Личный EXP/SP в PvP не является условием. Auto BSoE/Return/массовый отказ от PvP не включать в policy.

**Важно для расширения:** новые family-specific `OutcomeAdapter` могут добавлять `known` metric fields; immutable `TacticalReceiptV3` и старые V4 checkpoints не изменять без явной schema migration. Приоритеты ролей не зашивать единственной нерасширяемой формулой в `ActionLearningV4`.
