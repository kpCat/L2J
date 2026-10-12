# SCENARIOS040 — конечные игровые проверки

## Общие правила

Каждый episode имеет один manifest: `candidate_id, CODE_COMMIT, jar_sha256, config_hashes, evaluator_hash, DB, runtimeRoot, runtimeId, PID/startTime, scenario, enrolled identities, UTC/monotonic start/end`. Использовать существующий формат, расширять только необходимыми полями. Не создавать новый формат для каждого gate.

Enrolled identity: стабильный profile и текущие object/epoch/instance; отдельно target generation. Нельзя выбирать успешных задним числом. Исторические IDs039 не являются обязательной production whitelist; для нового natural сценария допустим заранее объявленный детерминированный выбор естественной группы по живым фактам. Failed участников внутри окна не заменять.

Условия natural scene фиксируются до исхода: фактические уровни/классы/экипировка/умения/цели, доступная геометрия, текущая locality, baseline approved population/config. Если до старта preconditions не выполнены, честный INVALID_PRECONDITION; затем диагностировать общий policy/сценарий, а не перебирать БД/календарь/seed до удобного состава.

Не уменьшать штатный мир до10 специально подобранных персонажей. Сохранять утверждённый текущий runtime profile;039 работал с1280 readiness. Старый10k GREEN не выдавать за новый10k acceptance040 и не запускать новый12-часовой scale-эпик.

## S0. Владение и свои БД

Только новые exact040 пространства, все под OWN module `.phantom-local/`:
- `contract040a` / `l2jmobiush5_phantom_test_040a` — основной ordinary сценарий, sameDB restart и финальный live/preview;
- `contract040b` / `l2jmobiush5_phantom_test_040b` — независимая natural группа;
- `contract040t` / `l2jmobiush5_phantom_test_040t` — fixture/native test environment;
- `contract040r72` / `l2jmobiush5_phantom_test_040r72` — только fault-OFF recovery сохранённого72 input при его наличии;
- `contract040r73` / `l2jmobiush5_phantom_test_040r73` — только fault-OFF recovery сохранённого73 input, если он действительно существует;
- `contract040x72/x73` / `l2jmobiush5_phantom_test_040x72/040x73` — НЕ создавать/запускать до отдельного crash-разрешения.

Это новые имена TASK040, не утверждение, что такие БД уже существуют. Существование любого из них до первого старта без подтверждённой собственной040 provenance — conflict, не разрешение overwrite. Не создавать неиспользуемые clones заранее.

Reuse известных scripts/runner032–039, но guards адаптировать только к перечисленным exact040 roots/schema names. Не разрешать произвольный DB prefix или любой localhost процесс. MariaDB3308 не останавливать. PLAY, старые036–039 и их экспорты остаются read-only; не запускать recovery оригинальногоx72 pending1.

Согласованный source export проверяется до импорта. Не считать копирование файлов живой DB согласованной копией. Не менять credentials/global grants/службы/firewall; использовать существующие разрешённые локальные механизмы. Логины/токены/пароли только private, не в Git/review ZIP.

## S1. Проверяемый action через препятствие

Заранее выбрать natural melee/caster с допустимой существующей capability и областью, где есть непрямая доступная action position. Использовать реальные GeoEngine/PathFinding и native moves; source-код не содержит ID выбранных actors/точек.

Для каждого primary: цель валидна → прямой подход блокирован/обход нужен → разрешённый путь/позиция → actual native damage →≥5 завершённых native cycles. Native loot: при drop/pickup/auto-loot applicability показать фактическое приобретение/штатное взаимодействие; при no-drop/full-stock-inventory фиксировать причину, не создавать подарок. Отдельный fixture может закрыть pickup/Sweep ветку, но не выдаётся за natural drop.

Отрицательные cases допускаются короткими native fixtures: dead/stale target, target другого instance/этажа, truly no-route, busy cast, outdated navigation generation. Ни один не должен приводить к проходу сквозь гео, бесконечному retry или чужому actor mutation. По возможности совмещать S1 с S2, не повторять5cycles ради второго документа.

## S2. Две целые natural группы

Две независимые ordinary группы6–8; не две выборки одного успешного primary. Реальная независимость документируется locality/cohort/runtime, не только новым runId. Baseline039 предписывает CONTINUITY_V2; его код/thresholds остаются неизменными.

Предварительно enroll ВСЕХ6–8. Два primary melee/caster обязаны иметь≥5cycles каждый, остальные≥2; остальные критерии исходного evaluator (idle/debt/rest/tail rewards/counters/epochs) сохраняются. Окно039 было380s: фактическую команду и все параметры взять из исходного принятого launcher, не выбирать новое меньшее окно.

Смерть, уход, потеря epoch или нулевая собственная работа участника не исключаются. Такой исход может быть корректной жизнью, но не PASS стабильного S2. Он остаётся FAIL S2 и проверяется отдельно в S3. Проблема исходных preconditions фиксируется до measurement, без подбора исхода.

После FAIL определить, сломана игра, выбор естественных предпосылок или collector. Следующий длинный run разрешён только после конкретного исправления/различающей гипотезы, а не с новым случайным cohort «на удачу».

## S3. Две вертикали полного lifetime

A. Death: actual native death → существующий штатный recovery с настоящим временем (в039 проверяли45s) → нормальное восстановленное native действие с useful damage/cycles. Контролируемый TEST doDie/stock recovery fixture маркируется TEST; natural death не подменяется fixture-heal.

B. Soft away: заранее selected живой профиль с действительной фоновой работой → observer/human locality штатно уходит → native earned/drain/save завершается → реальный background producer выполняет полезную работу по прошедшему времени → observer возвращается штатным путём → тот же stable profile законно получает native epoch и продолжает полезную работу.

Наблюдать reason/timestamps/producer fields, а не просто sleep90. Если фон начал работать, затем наступила смерть, показать реальную положительную работу и законный loss; затем довести этого же profile через recovery до useful return. Не требовать монотонного EXP при законной смерти.

Если работы нет из-за доменных предпосылок, это не автоматический баг, но productive gate не закрыт. Переход native→background не должен быть пропущен или выполнен дважды, исходные pending/earned не очищаются. При возвращении нельзя незаметно создать нового персонажа вместо прежнего.

## S4. Save и cross-boot на финальном кандидате

Собрать all-birth lifecycle до stopped cut. Дождаться штатного `COMPLETE/retained0` до pools stop и process exit. Consistent SELECT на той же своей DB после остановки всех writers; реальный полный state/context/items/skills/PNC и ownership lineage.

Далее два последовательных normal sameDB restart без import/reset/reseed. Для каждого показать actual boot, canonical restore, goal/context preservation или законный подтверждённый переход, отсутствие повторного применения earned reward и полезную native работу. После второго stop сравнить canonical ещё раз. Один и тот же boot нельзя назвать двумя проверками.

Можно использовать эти же stops для G3 и G5; не запускать отдельный identical server ради второй папки evidence. Final live boot после stopped cut — новый episode с явными active births, он не делает старый stopped cut ложным и не обязан завершить все свои будущие births для G8.

## S5. G6 при действующем запрете

Найти через039 EVIDENCE_INDEX исходный pre-recovery crash72 evidence и согласованный persisted input. Оригинал не менять. Имеющийся после нескольких recovery запусков dump не выдавать за crash-time preimage. Нет preimage — явно NOT_AVAILABLE для такого воспроизведения, не конструировать SQL-state.

На новой040r72-копии разрешён только обычный fault-OFF boot/stock stop, причинная диагностика/fix recovery и обычный второй restart для проверки его идемпотентности. Это ограниченный replay/recovery результат, не новый actual crash72. С73 аналогично лишь при наличии настоящего сохранённого input; из039 x73 NOT_RUN нельзя получить готовый73 input.

Запрет означает `G6=BLOCKED_AUTHORITY` для недоступной обязательной части. Ни unit fixture, ни process exit0, ни старый OS72 не заменяют оба actual final-candidate crash windows. Все другие gates продолжать; M1 не объявлять закрытым.

## S5A. Только после отдельного явного разрешения crash040

Эта секция НЕ является разрешением сама по себе. В CHECKPOINT040 записать точную новую пользовательскую формулировку, её область и время. Разрешение «продолжай» или отправка GOAL040 без явного crash-разрешения недостаточны.

Использовать только уже существующие72/73 fault windows и exact принадлежащие040x72/x73 clones, тот же final production JAR, verified injector/controller provenance. Не создавать новые fault types. До arming проверить PID+start-time+command-line/root+schema+ports, отсутствие REAL/других runtimes на этих процессах, согласованный baseline snapshot и время на recovery.

Предварительно enroll actor/epoch и подтверждённую earned работу. Каждый window: actual нужная boundary → разрешённое точечное завершение своего процесса → новый boot того же clone с fault OFF и до normal admission → actual recovery/canonical nativeXYZ/state/context/items/skills/goal/reward reconciliation → ещё один ordinary sameDB restart для idempotence. Все work/ownership должны иметь реальное происхождение.

Только полная пара72 и73 на текущем кандидате даёт G6 PASS. Любой guard/identity/DB ambiguity — не arm. Никакой wildcard kill по java/process-name и никакого намеренного прерывания actual acceptance runtime. После каждого episode injector разоружить и обычные оставшиеся процессы остановить штатно; файлы/БД сохранить.

## S6. Observer без REAL и fixtures binding

Проверить автономный observer без сетевого игрока, отсутствие ложного REAL_LOGIN, наличие одного текущего focus и корректное различие passive/Synthetic lifetime. Короткими контрактными/native fixtures проверить arrival/logout/reconnect/stale disconnect/instance switch. Фактический серверный branch настоящего REAL остаётся pending до S7, fixtures не выдаются за него.

Выход controlling wrapper не выключает сервер/пассивный observer. Закрытие Synthetic по своему525s TTL/heartbeat допустимо, но не должно закрыть готовый acceptance runtime или разрушить observer owner. Не обходить эти guards бесконечным heartbeat от Codex.

## S7. Последний этап: пользователь в настоящем клиенте

Когда autonomous gates готовы, предоставить запущенный stable runtime и короткую инструкцию из REAL_FINAL040.md. До этого не требовать участия пользователя. При незакрытом G6/другом gate вход может быть PREVIEW, не окончательным закрытием.

Нормальный LoginServer→GameServer→EnterWorld, server-side actual REAL session. Observer автоматически «прилипает» к ней; отдельный arm для пассивного наблюдения не нужен. Не брать управление персонажем без дополнительного явного consent; existing Pilot arm не подделывать.

Пользователь лично видит движение и обход, бой/смену целей/штатные паузы, отсутствие устойчивых зависаний и грубого pop-in/flicker рядом с ним. Для проверки locality он может немного отойти и вернуться; не заставлять часами выполнять QA-команды. Допускается уже существующий согласованный Pilot при отдельном consent, но он не доказывает визуальное качество вместо человека.

Наблюдатель собирает короткую ограниченную server-side запись actual binding/current session/native outcomes. Пользователь сообщает, что видел и принимает ли M1. Внешний вид/анимации/мерцание нельзя закрыть одним server log или Synthetic.

После реального обнаруженного дефекта — не CLOSED. В рамках оставшегося бюджета исправить и перепроверить затронутую часть; новый semantic кандидат требует новой соответствующей личной проверки. Не превращать её в многочасовую скрытую продолженную кампанию и не запускать041 автоматически.
