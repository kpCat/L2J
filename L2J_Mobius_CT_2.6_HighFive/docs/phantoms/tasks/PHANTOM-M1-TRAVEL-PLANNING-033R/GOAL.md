# GOAL033R

/goal Выполни L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/GOAL.md.

Required base: df71140e659670e2e3d225605f64689a6d32d281
Branch: experiment/m1-candidate007-observe008
GPT-6.1 Sol, reasoning Very High. Новый чат, без субагентов.

Это ЗАМЕНА ещё не запущенного TASK033. Одобряю TASK/DESIGN/PLAN/SOURCE_MAP/SCENARIOS033R.
Старую установку «один route fix в двух файлах» не применять.
Цель — не дробить луч к anchor, а выбирать и исполнять безопасный план
WALK/существующий GK/законный RETURN_GK с последующим native-фармом.

Используй stock PathFinding/NodeBuffer A*, GeoEngine и native Player.
Координатору маршрута разрешён bounded graph доказанных local routes;
не писать второй геодвижок и не перебирать весь мир.
Точка/ребро-кандидат не равны проходимому пути. Можно сначала удаляться от цели.
Учесть instance/Z/слой/двери/воду; не менять geometry guards и не рисовать обход по ID5/19.

Нужны genuine RED→GREEN и реальный route→arrival→5cycles, не только buffer test.
Пеший detour и transport-mode проверяются отдельно.
/unstuck имеет delayed finalizer: до включения доказать owner/epoch/cancel/death/demat.
Реальный интервал читать из own runtime; в repo300, локальные30 не предполагать.
Не выдавать GM, adena, scroll или награды.

Emergency correction OFF в основной приёмке. Возможен только маленький
same-surface/swept-clear шаг по ESCAPE_AND_RECOVERY, не прыжок через стену.
Его отсутствие не подменять псевдо«готовой» безопасностью.

Автономно4–6ч, максимум8ч; первый full-server probe до60мин.
После6ч semantic freeze, последние30мин cleanup/publication.
Несколько связанных fixes внутри scope разрешены; не STOP после первого GREEN.
Не создавать новую инфраструктуру observer и не гонять93+11 после каждой правки.

Own worktree C:\Users\ZBook\.codex\worktrees\m1-travel-033r\L2J_Mobius;
suffix/detached exactbase разрешены. Main/PLAY/foreign/kpCat не трогать.
Own DB/process permissions — TASK/RUNBOOK. Existing Synthetic, без arm и моего входа.
Никаких SQL-heal/телепорта Phantom из runner/ослабления evaluator.

Exact-path commit + normal push обязательны при любом исходе.
Один RESULT_HANDOFF/MORNING, свои JVM STOPPED.
WALK/RETURN_GK/emergency/native farm результаты отдельно.
M1=OPEN; REAL_FINAL=NOT_RUN. STOP_AUTHORITY=TASK033R_CONTRACT.
Следующую задачу автоматически не начинать.


Полные права/ограничения: TASK.md, SOURCE_MAP.tsv, RUNBOOK.md, GIT.md.
