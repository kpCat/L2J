# Расширяемость после M1 — что обещаем и чего не обещаем

Три независимые причины изменения:
1. Behavior policy: какую цель/позицию/скилл выбрать и когда отдыхать. Использует
   existing DecisionEngine/VisibleAutoPlay/Travel. Не генерирует EXP и не пишет canonical.
2. Execution/lifecycle: native Player/epoch, принятие новой работы, завершение callbacks,
   store/demat. Не должен зависеть от названия тактики или конкретного NPC.
3. Background model: допустимая ограниченная модель farming/travel и её фактические
   входы. Обычная vitality — ресурс, не идентификатор разрешённого персонажа.

Scope029 не утверждает, что все эти границы уже идеальны. Пример технического долга028:
пауза новых roots при90s combat введена для native phase completion. Это инженерное
ограничение, а не «умная тактика». Здесь оно сохраняется регрессией, не скрывается.
Будущая корректная сегментация combat episodes потребует своего native test, но не
обязана менять schema хранения персонажа. Нельзя обещать отсутствие будущих интеграций.

M2 party/companion/assist изменит состав участников и intent ownership; эти переходы
требуют новых tests и явных policies, не отключения exact-recipient guards. Instances,
epics/экономика/progression тоже отдельные vertical milestones. Намеренно не в TASK029.

Анти-overfit checks029:
- нет profileID/nickname/NPCID/level1 special branch;
- normal vitality1..20000, несколько template/classes в tests;
- задачу FARM нельзя подменить одним заранее выбранным объектом;
- смена вида атаки не меняет receipt identity;
- unsupported background context не отменяет безопасный native store;
- thin adapters не превращаются во вторую копию damage/cooldown engine.

План после029 определяется actual gates: server bridge+proof → final real client.
Это не гарантия «осталось ровно две задачи», никакой даты закрытия в acceptance нет.
