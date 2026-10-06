# TASK021 — актуальная цель и безопасный локальный фарм

Required base: `2bf2936083bf6b081104c3e03c254ab8f07afc87`
Branch: `experiment/m1-candidate007-observe008`
Codex: GPT-6.1 Sol, High. Новый диалог, без субагентов.

Прочитать TASK.md, DESIGN.md, PATCH_GUIDE.md, PLAN.md и SCENARIOS.md один раз.
Это одна инженерная задача на цикл visible farm intent, не новый аудит M1.

Исправить связанный класс:
`persisted goal != runtime goal → устаревший travel → terminal failure → вечный start_retry`.

TASK020 доказал: profile110/object268485779 живой, level4; runtime revision28,
persisted revision29; currentActionGuard=DECISION_GOAL_MISMATCH;
travel.native_segment_water_entry; farm counters=0. Причина появления revision29 и
точная геометрия отвергнутого сегмента пока не установлены. Не объявлять геодату плохой.

Результат: актуальная goal revision во всех участниках; запрещённый маршрут не
исполняется; его окончательный отказ приводит к ограниченному выбору другой локальной
цели через существующий planner, а не к повтору той же ошибки. Затем штатный
AutoPlay/AutoUse/PlayerAI выполняет не менее пяти последовательных native farm cycles.

Это сознательное уточнение прежнего scope: revision coherence, terminal propagation,
разрешённая владельцем смена локального плана входят в ОДНУ задачу. Они не являются
основанием просить отдельный TASK022. Новая проблема вне SOURCE_MAP — STOP.

Не отключать water/GeoEngine/ownership guards, не завершать catchup искусственно,
не управлять Phantom тестовыми командами. PLAY — SELECT/export only.
После целевых проверок — один fresh observe021 и один ручной вход TestAdmin.
M1 остаётся OPEN. Exact-path commit + normal push обязательны при любом исходе.
