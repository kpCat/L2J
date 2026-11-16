# План исполнения 019

Утверждённая архитектура: Java25, существующий Combat Runtime, отдельный линейный V4, default OFF. Новые worktree, scheduler, SQL writes и изменение V3/Adam исключены. SOURCE f7638db7; TEST с чужими правками не обновлять.

1. G1: read-only decode сохранённых native016 terminal envelopes и извлечение отдельных TacticalStoreV3.Row; exact SHA settled016, episode/actor/owner/target. EXP/SP не являются входом V4. Исходные Party text records без typed replay не принимать.
2. Один focused combined test: native update/score, cold restore/replay/tamper, lawful rank/fallback. Затем ActionFeedbackV4, ActionValueV4, ActionLearningV4: не более 256 коэффициентов, Huber SGD, immutable checkpoint и event ledger, atomic move. Отдельный read-only SQL accessor только при необходимости.
3. Точечный TEST opt-in DAMAGE hook в TacticalPolicyV3 с существующим 5ms бюджетом и stock rescue. HEAL пока fallback. Один Ant target, один native entrypoint к существующему Runtime.
4. Максимум один GS boot/run/stop до 8 минут; только собственный runtime, без старой кампании. Если безопасный native route недоступен — PARTIAL с точной причиной.
5. Сверить preservation, кодировки и exact scope; краткий отчёт, scoped commit/non-force push feature/phantom-world; независимый GitHub HEAD/parents/diff/full bodies, manual REQUIRED. Goal020 не начинать.

Проверки: каждая offline до120с, суммарно до12мин. G1 подтверждён существующим diagnose-cohort (exit0, 27 genuine receipts) за первые5мин. Контроль real sample → update до35мин. Ошибка update, двойной credit, повреждённый checkpoint, аварийное состояние или illegal candidate должны приводить к отказу/fallback. Полный suite запрещён заданием; targeted checks имеют приоритет над общими skill defaults.
