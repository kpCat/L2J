# RESULT — PHANTOM-LEARNING-001

**PARTIAL.** Коммит реализации `518318c89c6937c80818b4035df6b0fc9523f9ce` на `wip/phantom-learning`, база `c5b81f89ce02030eff8441395b0411137a110b2f`.

- Реальная обучаемая MLP. Независимый синтетический audit показывает отдачу **0,80** против **0,25** и MSE **0,000432** против **0,446780**.
- Production CombatService меняет выбор в Solo и Party на управляемом backend. SQL checkpoint 2 в новом JVM повторяет выбор. Синтетическая модель осталась DRAFT. В MariaDB нет принятых моделей.
- 178 проверок прошли. p95 **7,3 мкс** для выбора по 4 готовым векторам. Замер полного native decision path остаётся открытым.
- Первый GameServer в SHADOW записал **3 NATIVE / EXACT_CAST_HP** в Solo для двух навыков. Отдельный SQL readback после STOP подтвердил 3 уникальных ключа, **0 Party** и **0 моделей**. Сбор остановился до Party по таймауту. Обучение на игровом опыте, принятие и загрузка модели остаются открыты.
- Geo203 и protected207 проверены. GameServer и собственная MariaDB штатно остановлены. Установлено, что штатный listener использует wildcard при loopback в конфиге. Launcher блокирует повтор. Использованы **1/2 clean JAR и 1/2 GameServer bootstrap**. Полные native F7/F8, Heal и PvP/PK остаются открыты.

[Полный отчёт](../../reports/PHANTOM-LEARNING-001.md) · [Передача контекста для завершения 001](HANDOFF.md)
