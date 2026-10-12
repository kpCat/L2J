# GOAL038 — A1, рабочая интеграция локальных действий

BASE=`aa63dafb79e19a531b5620af195764c823bd9539`; remote=`experiment/m1-candidate007-observe008`.
OWN=`C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.
MAIN=`C:\Users\ZBook\L2J_Mobius` read-only. Только HighFive/JDK25/Ant.
Модель: GPT-6.1 Sol, reasoning Very High; новый чат, без субагентов.

Выполни согласованное решение DESIGN, не внедряй весь roadmap сразу. Нужно связать:
FARM intent → действие по фактическим возможностям → позиция выполнения → stock route
→ фактическое прибытие → штатный AutoPlay/AutoUse → результат → bounded reevaluation.
Зафиксировать и устранить классы stale-target / disconnected reachability / stuck
continuation, которые реально воспроизводятся на baseline. Tail357 и route253 —
исторические свидетельства, а не разрешение hardcode этих персонажей.

Один in-scope guard не повод остановиться: разрешены несколько связанных fixes с
RED→GREEN и проверкой последствий. Но нельзя придумывать причину исторического отказа
без raw, выдавать гипотезу за факт или запускать бесконечную кампанию до M1 GREEN.

Пакет представляет архитектурное решение координатора. Отправка этого GOAL пользователем
утверждает DESIGN/PLAN/SOURCE_MAP; повторное согласование того же дизайна не требуется.
Основная реализация внутри phantoms; два условных hook-файла AutoPlay/AutoUse ограничены
выбором/проверкой target и yield, не combat math. Никакого расширения Player/Creature/
ThreadPool/EventDispatcher/Party/schema. Никаких новых библиотек/сетевого клиента/C#.

Используй existing native identity/leases/target-generation, navigation queue, cooldown
и progress tracker, вместо второго scheduler/нового протокола. Сохрани035 inventory250,
036 earned/drain,037 useful debt. No SQL-heal, fake HP/MP/EXP/SP, world teleport, bypass,
forced target exclusivity, increase of existing45/90/120 deadlines или evidence limits.

Автономно, без REAL_LOGIN/arm/вопроса «в игре». Старые DB/архивы только read/export;
пишешь только новые038 DB и OWN exact paths. Уборка диска PAUSED; worktree/DB сохранить.

Ориентир4–6ч; максимум8ч от старта. После5ч30м не начинать новую semantic-ветвь;
после6ч30м полный freeze; последние45мин cleanup/push. До8 full GameServer starts,
до4 production-кандидатов, до4 full-сцен. Native targeted JVM отдельно.
Два финальных запуска и две full-сцены резервируются с начала; не потратить их на setup.
До60мин — короткий runtime либо уже валидный causal native RED, а не час новых helpers.
Неизменный FAIL не повторять ради удачи. При невозможности безопасной реализации —
bounded отчёт BLOCKED_ARCHITECTURE с фактом, а не новая система за оставшееся время.

GREEN_ACTION_LOOP038 требует native scenarios + две independent natural группы6–8,
all-enrolled, unchanged CONTINUITY_V2, FULL_SAVE и здоровый global drain на одном Jar.
Частичная инженерная вертикаль отмечается PARTIAL_IMPLEMENTED, не M1 GREEN.
Отрицательные runtime cases (нет пути/смерть) проверяются отдельно, а не скрываются.

В любом исходе: exact commit+normal push; RESULT_HANDOFF038/EVIDENCE_INDEX038/CLEANUP038,
свои JVM штатно STOPPED, hashes и приватный выбранный архив. Никаких branch merge/delete,
new worktree, чистки чужих файлов/БД или продолжения следующей задачи.
STOP_AUTHORITY=TASK038_CONTRACT. M1=OPEN; REAL_FINAL=NOT_RUN.
