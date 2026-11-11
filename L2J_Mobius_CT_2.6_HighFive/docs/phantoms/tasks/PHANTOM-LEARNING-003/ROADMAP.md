# Дорожная карта после 003

LEARNING-001 — доказана обучаемая Offensive Policy с настоящими кастами и cold SQL. LEARNING-002 — создан автономный TRAIN Director, 34 эпизода/117 native events, TEST checkpoint, restart/STOP. Операторская ручная проверка cmd-скриптов на Windows по решению владельца отложена; это не обнуляет автоматически техническую приёмку.

**003 (этот пакет):** общий тактический V3 контекст, 4–8 МиБ модель и реально обучаемый HEAL target/skill в Party. DAMAGE V1 fallback работает. Остальные действия typed, но без ложных обучающих receipts.

**004:** автоматическое массовое обучение по различным профессиям и навыкам, обязательное контрольное PvE + PVP (при готовности права и сцены) + crowd-control exact receipts; обучение последовательностям Move/Heal/Control/Retreat/Summon; GPU trainer на RTX A5000 отдельно от GameServer, наращивание benchmark/coverage, сохранение/импорт с тестовой защитой. Не обещать FULL all game in 004; формировать отдельные измеримые режимные suites, в том числе Summoner vs archer/mage/Prophet, Cardinal rescue, dagger flank, Destroyer/Tyrant limits.

**130** остаётся историческим PARTIAL до самостоятельной нативной приёмки, новая обучающая система не заменяет техническую корректность Party/Navigation/leases, но предоставляет инструменты для измерения и будущего improved tactics.

**131–136** — Party PvP/PK/Spoil, кланы, рейды, осады, TW, Olympiad. Использовать единый V3 action context + native legality/reward, когда соответствующие режимы будут реальны. 250 offline merchants вне cap 128; honest economics preserved.

**Позднее Semantic Pack:** отдельный русский dialogue model/backend. Возможно использовать имеющуюся большую Gemma 4 26B A4B как **teacher для создания сценариев диалога** с последующей проверкой фактов/лицензии и обучения малой модели; нельзя «вырезать 8 МБ весов» из огромной LM и получить готовые разговорные знания. Отдельный языковой бюджет, чат throttling, отсутствие GPU contention with GameServer.

**Позднее route policy:** нейросеть выбирает высокоуровневый waypoint / стоимость / безопасность, но штатная GeoData и pathfinder обязательно подтверждают физическую проходимость. Не давать модели телепортировать ботов или игнорировать obstruction. Никакой pathfinding-heavy neural inference на каждый игровой тик.
