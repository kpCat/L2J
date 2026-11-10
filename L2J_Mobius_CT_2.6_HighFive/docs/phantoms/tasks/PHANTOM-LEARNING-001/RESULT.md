# RESULT — PHANTOM-LEARNING-001

**PARTIAL.** Коммит реализации `518318c89c6937c80818b4035df6b0fc9523f9ce` на `wip/phantom-learning`, база `c5b81f89ce02030eff8441395b0411137a110b2f`.

- Реальная обучаемая MLP. Независимый синтетический audit показывает отдачу **0,80** против **0,25** и MSE **0,000432** против **0,446780**.
- Production CombatService меняет выбор в Solo и Party на управляемом backend. SQL checkpoint 2 в новом JVM повторяет выбор. Синтетическая модель осталась DRAFT. В MariaDB нет принятых моделей.
- 178 проверок прошли. p95 **7,3 мкс** для выбора по 4 готовым векторам. Один clean JAR, GameServer bootstrap не запускался, собственная MariaDB остановлена.
- **NATIVE_UNPROVEN.** Требуются точные квитанции игрового урона, обучение на них и проверка принятой модели после SQL reload. Полные F7/F8, Geo203 и protected207 не запускались.

[Полный отчёт](../../reports/PHANTOM-LEARNING-001.md) · [Передача контекста для 002](HANDOFF.md)
