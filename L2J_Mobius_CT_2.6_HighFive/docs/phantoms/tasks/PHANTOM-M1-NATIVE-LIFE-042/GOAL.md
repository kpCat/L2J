# GOAL042 — закрыть G2 и G4 М1 без аварийных испытаний

Прочти весь пакет TASK042 (`TASK.md`, `CONTEXT.md`, `DESIGN.md`, `ACCEPTANCE.md`, `PLAN.md`, `SOURCE_MAP.tsv`, `HANDOFF.md`) и необходимые исходные документы `Agents.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, `docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/CURRENT_STATE041.md` и `ACCEPTANCE.md`.

Это **новая узкая задача TASK042**, которая не отменяет исторические FAIL/OPEN TASK041 и не расширяет критерии М1. Запуск этой команды означает согласие пользователя на обычные ограниченные операции собственной игровой dev/test-среды для G2 и G4 в соответствии с пакетом. **Не даёт нового разрешения на crash/injector/forced termination** и не возобновляет приостановленную клиентскую маршрутизацию.

BASE/remote на момент подготовки: `a5674811479d087ec7f67ca62921ffa85d298263`, ветка `experiment/m1-candidate007-observe008`.
OWN repo: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`; module: `L2J_Mobius_CT_2.6_HighFive` в OWN. MAIN `C:\Users\ZBook\L2J_Mobius` — только чтение входного пакета. Работай в прежнем OWN, **не создавай новый worktree**, другие хроники запрещены.

Длительность до **4 часов wall-clock** от фактического начала. Без субагентов. При препятствии безопасную независимую часть продолжай, но не требуй дополнительных прав и не обходи ограничения платформы. Семантические изменения только по подтверждённой причине: RED → минимальная правка владельца → GREEN/negative → native outcome.

Главная конечная цель: закрыть две **настоящие** natural группы G2 и один полный продуктивный жизненный цикл G4 на одном финальном игровом кандидате. Сначала исследуй C8 failure profile276 и C24 PASS8/8; сохрани все прежние результаты. При допустимом соответствии финальному candidate C24 можно использовать как уже выполненную независимую группу без бессмысленного повтора; при изменении product semantics перепроверить оба cohorts.

**Строгий scope:** G2+G4, необходимый общий G1/регрессионный контроль затронутого кода, минимальный all-birth/save cut этих новых normal runs. **G3 в целом, G5, G6, R1/REAL_FINAL не объявлять закрытыми** на основании этой задачи; не запускать аварии, восстановление72/73, процессы финальной поставки или клиента.

Защитить: живой `040a`, все старые raw/DB, `041x72/041x73`, финальную 041 поставку, MAIN/PLAY и чужие изменения. Не делать SQL-heal, teleport-heal, подарки HP/MP/EXP/SP/items, clock/rate/radius/threshold changes или ID-specific production branches. Synthetic TTL=525s и оригинальный CONTINUITY_V2 остаются неизменными.

Финал: exact-path commit и обычный non-force push только полезных изменений, HEAD=remote, минимальный `RESULT042.md`, `CHECKPOINT042.md`, `GATES042.tsv`, `REVIEW042.md` и компактный проверенный `TASK042-review.zip` без гигантских прежних raw. Все собственные042 JVM штатно остановлены, собственные DB/raw оставлены. Итог: G2/G4 PASS или честно точные FAIL/NOT_VERIFIED; M1=OPEN и READY_FOR_M2=false, М2 не начинать.
