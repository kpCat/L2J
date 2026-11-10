# RESULT — PHANTOM-LEARNING-001

**PARTIAL.** Коммит реализации `518318c89c6937c80818b4035df6b0fc9523f9ce` на `wip/phantom-learning`, база `c5b81f89ce02030eff8441395b0411137a110b2f`.

- Реальная обучаемая MLP. Независимый синтетический audit показывает отдачу **0,80** против **0,25** и MSE **0,000432** против **0,446780**.
- Production CombatService меняет выбор в Solo и Party на управляемом backend. SQL checkpoint 2 в новом JVM повторяет выбор. Синтетическая модель осталась DRAFT. В MariaDB нет принятых моделей.
- 178 проверок прошли. p95 **7,3 мкс** для выбора по 4 готовым векторам. Замер полного native decision path остаётся открытым.
- Реальный Solo SHADOW записал **7 NATIVE / EXACT_CAST_HP** по навыкам 1230 и 1339 в трёх отдельных контурах. SQL readback после STOP подтвердил уникальные ключи. В `cycle2` runner потребил terminal claim `LOW_HP_STOPPED`. NPC добил актёра до следующего боя.
- Bind исправлен в `9fe1c1b1ac70fef8c7e8f2f1e9eef888a2d33a05`. Новый GameServer подтвердил единственный фактический сокет `127.0.0.1:17961`. Geo203 и protected207 проверены. Исторические попытки и readback сохранены.
- Реальная автономная Party сформировалась в `cycle4`. Два штатных приглашения приняты. `WAIT_TANK_AGGRO` остановил группу до начала боя. Native-сбор в Party, независимый audit, принятие и холодное применение модели остаются открыты. F7/F8, Combat ownership, Party tactics и PvP прошли DB-free проверки. Полные native F7/F8, Heal и PvP/PK остаются открыты.
- В финальном headless preflight штатный прогноз допустил TRAIN к Hames Orc Shaman, а TUNING отказал. SQL `cycle7` вернул **0 примеров и 0 моделей**. Штатный trainer отказал до обучения. GameServer и MariaDB остановлены, три порта собственного контура свободны.

[Полный отчёт](../../reports/PHANTOM-LEARNING-001.md) · [Передача контекста для завершения 001](HANDOFF.md)
