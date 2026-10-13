# DESIGN042 — один native action/soft-handoff, без новых движков

## Договор результата
Не добывать PASS за счёт выбора заведомо успешных персонажей, уменьшения времени или смены правил. Действительный успех: две независимые natural группы CONTINUITY_V2 и связанный с ними проверенный полезный жизненный цикл в foreground/background того же стабильного профиля. Фактическая смерть является законным событием, но не заменяет продуктивный возврат.

## Отсутствие движения и уход из locality — разные состояния
Диагностировать actor276 по существующим `PhantomVisibleAutoPlay`, `PhantomEngagementController`, `PhantomHistoricalBackgroundService`, `PhantomHumanLocalityControl`, `PhantomMaterializationRetentionPolicy` и `PhantomMaterializationService`: реальные координаты native Player/anchor, distance/instance, action/route, owned/pending work, активный pin, drop reason, owner epoch. Сначала классифицировать **LEGAL_SOFT_RELEASE / ROUTE_NO_PROGRESS / STALE_LOCALITY / COLLECTOR_GAP / UNKNOWN**, затем только причинно обоснованный fix. Законный выход за границу зоны наблюдателя не является искусственно «сломанный AI».

## Групповое окно
Один prospectively selected cohort из6–8 (melee/caster primaries, остальные), минимум380s и исходные CONTINUITY_V2 thresholds (primary≥5cycles, остальные≥2, idle/debt/rest/reward/epoch), без динамического исключения участников. Два независимых cohorts: допускается reuse C24 только при exact same semantic codeJar/config/evaluator и соблюдении независимости с новой группой. Если надо менять native code, обе группы на одном **новом** frozen candidate перепроверяются. Любое FAIL остаётся FAIL в своей истории.

## Г4 — источник полезной жизни
До события заранее выбрать **одного живого** natural profile с реальной native работой и background eligibility. Stock soft human-away через проверенный existing Synthetic/locality route; при отсутствии REAL игрока autonomous observer не должен оставлять невидимый pin в старой зоне. Native earned завершён, FULL canonical сохранился, один background owning producer реально начислил пользу по прошедшему времени; при death учитывается законная потеря и выполняется recovery. Возврат той же stable identity в legal новую epoch → actual native damage/cycles, без дубликатов и двойных наград. Не создавать новый маршрут, scheduler, AI, persistence или отдельный управляющий Player.

## Регистр доказательств — ограниченный
Один существующий manifest/current collector, один компактный итоговый report. Не раздувать ring, batch, disk caps, радиусы45–90–120, Synthetic lease525s, calendar, population и реальный клиентский вход. При collector defect — минимальная правка только доказанной причины и один отрицательный testcase; не менять product runtime, чтобы «улучшить отчёт».

## Что не входит
Перехват/маршрутизация сетевых вызовов клиента, ввод/отладка реальной сессии, аварийные завершения любого Java процесса, G6 fault windows, MariaDB service операции, G5 cross-boot кампании, экономический/career AI, новые зависимости. Все будущие high-level функции — М2–М7.
