# GOAL031

Выполни TASK.md по DESIGN.md/PLAN.md/SOURCE_MAP.tsv/SCENARIOS.md.
Base `e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0`; branch `experiment/m1-candidate007-observe008`.
Модель GPT-6.1 Sol, High; новый чат; без субагентов.

Цель — исправить завышенный setup filter и получить точную текущую причину
отсутствия native-группы. Не обещание закрыть весьM1 за следующий запуск.

Длительность максимум120мин wall-clock, cleanup/publication последние15мин.
Autonomous Synthetic, без ручного входа/arm. До двух коротких probes.
Game/lifecycle/ownership semantics не менять. Production изменения только
DIAGNOSTIC_ONLY из SOURCE_MAP, один counterexample test-path. Setup fix — task-only.
После обнаружения следующего current producer вернуть evidence, не автономную правку.

Нет требования снова собирать весь source-map мира,20 новых helpers, два finalfarm
эпизода или crash-restarts. Existing collector/stop/input reusable. Main/PLAY/oldclone
не изменять. Exact-path commit+normal push результата обязательны.
M1=OPEN. Следующую задачу не начинать.

## DEBUG-FIRST OVERRIDE
Выполнять `DEBUG_PLAYBOOK.md` ПЕРЕД новой diagnostic production правкой.
Одна JVM с loopback-only JDWP при разрешённом task-owned launch; без GUI можно jdb.
До35мин первый probe. `jcmd`/JFR на живой JVM, при необходимости jdb narrow
exception/guard breakpoint. Не останавливать всю JVM долго, не менять Player/SQL
через debugger. HotSwap только observational same-method-body in allowed scope,
не равен production acceptance. Не более2 GameServer starts/120min total,
никаких повторных сборок/общих тестов по каждому неизвестному событию.
При первом semantic producer завершить диагностику с exact cause/stack/guard,
а не чинить игровой код без нового scope.
