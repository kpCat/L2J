# M1 Full Closeout040 — план реализации

> Для исполнителя: выполнять последовательно в текущем Codex-диалоге без субагентов. При наличии superpowers использовать executing-plans, но не запускать новые согласования/чужой workspace/отдельный план вместо исполнения этого утверждённого пакета. Прямые ограничения TASK040 старше рекомендаций навыков.

**Цель:** закрыть реальные дефекты M1, получить единую проверенную серверную сборку и завершить личную приёмку пользователя.

**Архитектура:** существующий Phantom action/navigation/lifecycle/background contour и native executor, без второго AI/нового persistence протокола. Observer и Synthetic transport имеют разные полномочия и lifetimes.

**Стек:** текущие Java25/Ant/MariaDB из runtime039; только High Five, без новых зависимостей.

**Спецификация:** TASK.md, DESIGN.md, ACCEPTANCE.md; полномочия SOURCE_MAP.tsv и SCENARIOS.md.

## Общие ограничения

BASE040 `96bf5d2cba14bf6ad561f334ec49f9010716681b`; OWN и MAIN точно из TASK.md. 12 часов максимум; без субагентов; PLAY/MAIN/старые DB/raw read-only; no reset/clean/stash/rebase/force; exact-path commit и normal push. CRASH_AUTHORIZATION=DENIED_INHERITED, пока пользователь отдельно не разрешил040 crash. Не ослаблять исходные gates или игровые инварианты.

## Главные риски review

- Устаревший navigation/attack/logout callback не должен менять заменившую его epoch/session. Проверки в шагах2 и5.
- Законная death/recovery не должна выдать скрытый HP heal или потерять заработанный native результат. Шаги3 и4.
- Отсутствие REAL не должно оставлять ложный synthetic anchor, блокирующий background, или выключать пассивное наблюдение. Шаги3 и5.
- Restart после pending store не должен повторять reward или «исправляться» ручным SQL. Шаг4.
- Здоровый игровой процесс не должен завершаться cleanup-ом теста, окончанием goal, истечением observer action TTL или переходом к отчёту. Шаг7.

## Шаг1. Read-first и работающий короткий baseline

**Файлы:** CONTEXT.md; исходные039 RESULT/CHECKPOINT/ACCEPTANCE/EVIDENCE_INDEX; текущий SOURCE_MAP; выбранные production/test files. Новый CHECKPOINT040.md в OWN task family, private `.phantom-local/ops040/`.

**Вход:** BASE, сохранённые runtime manifests039, текущие процессы/порты и immutable raw.
**Выход:** точные команды сборки/тестов; actual owners/invariants; allowlist DB/runtime040; приоритетная причинная карта, без нового многотомного плана.

- [ ] Проверить repo/worktree/HEAD/remote, исходные чужие changes/untracked, наличие согласованной JDK/Ant, свободный диск, OWN runtime identity, PLAY isolation.
- [ ] Установить G6 authority до первого запуска. Не зависать из-за этого на всех остальных шагах.
- [ ] Скопировать только пакет040 в OWN, один раз проверить входной manifest. Прочитать AGENTS.md применимой области и не нарушать более строгие safety правила.
- [ ] Прочитать CONTINUITY_V2, зафиксировать hash и реальные thresholds/CLI; не переписывать evaluator.
- [ ] Разрешить каждый путь SOURCE_MAP в BASE; выписать методы/владельцев/тестовые команды для выбранных blockers. Символы из PATCH_GUIDE — подсказки, не выдуманные готовые API.
- [ ] Подготовить согласованные новые040 клоны из сохранённого проверенного источника. Переиспользовать existing setup scripts с exact ownership checks; никаких wildcard DBA-прав.
- [ ] Запустить короткий current native probe выбранного дефекта. Прежде нового сценария убедиться, что необходимые collectors действительно дают свежие данные. Setup error не объявлять игровым FAIL.

**Готовность шага:** можно причинно проверить первый blocker, не тратя часы на повторную упаковку/полный просмотр876336 entries.

## Шаг2. Navigation и action-loop до настоящего результата

**Файлы:** PhantomEngagementPlanner/Controller, PhantomVisibleAutoPlay/FarmTravel, существующий navigation seam, ограниченные optional hooks AutoPlay/AutoUse; новые tests040 по необходимости.

**Вход:** current goal/capability/target/epoch/navigation outcome/native progress.
**Выход:** штатный useful action или явный законный переход/перепланирование, без нового executor.

- [ ] Написать причинный RED на подтверждённый blocker: stale generation, closed direct route с обходом, некорректное ожидание ресурсов или потерянный navigation handoff. Не выбирать причину заранее по названию теста.
- [ ] Проверить RED на исходном production JAR/контролируемой source-версии, а не на неверно собранном fixture. Broken setup отмечать отдельно.
- [ ] Исправить владельца причины, проверить callers, ресурсы/target-generation/lease; сохранить037 debt и039 progress ordering.
- [ ] GREEN + соседний negative: no-route, wrong instance, target dead/replaced или native busy. У каждого asserted outcome должна быть игровая причина.
- [ ] Native melee/caster: реально пройти допустимый маршрут, нанести damage и завершить≥5cycles. Проверить применимый native loot, не подделывая выпадение.
- [ ] Для полезного изменения сделать exact-path code/test checkpoint commit, чистую сборку из него и provenance. Не использовать `git add .`.

**Готовность шага:** G1 можно замкнуть на финальной серии; группа не будет выдавать чужой EXP за собственную активность. При появлении второго схожего сбоя применять ограниченный самоанализ ROOT_CAUSES, а не правку по ID.

## Шаг3. Полный lifetime: смерть и уход в фон с возвратом

**Файлы:** существующие PhantomHistoricalBackgroundService/Planner, PhantomBackgroundDecision/Service, PhantomSystem, HumanLocalityControl, MaterializationService/RetentionPolicy и связанные current owners.

**Вход:** мягкий уход locality; native earned/drain; canonical goal/state; реально прошедшее время.
**Выход:** корректная завершённая native epoch, productive background и связанная новая полезная native epoch того же profile.

- [ ] На фактическом57-подобном кейсе установить: native не отпустил, background не возобновился, нет доступной работы, producer не применился или observer оставил ложный human anchor.
- [ ] Причинный тест на установленный потерянный переход. Исправить именно его, не делать периодический «реаниматор всего».
- [ ] Native death→штатное восстановление→useful damage; отдельно soft away→фон с реальной работой→возврат→useful damage/cycles. Смерть в фоне обрабатывать законно, не выбирать нового удобного profile после исхода.
- [ ] Отрицательные проверки: hard retention на earned work; duplicate resume; stale old session; остановка во время передачи ownership; observer old logout после reconnect.
- [ ] Проверить сохранность full items/skills, goal/context и отсутствие двойных rewards на всем переходе. Внутри G2 исходные epochs сохраняются; через G4 законные новые epochs связываются.

**Готовность шага:** устранён gameplay blocker G4, а не только исправлен его exporter.

## Шаг4. Exact save, два sameDB restart и допустимый recovery

**Файлы:** existing commit/owned-store/recovery owners в phantoms, observer witness seam, текущие verifiers. Native earned/core классы защищены SOURCE_MAP.

**Вход:** действительные owning commits, PREPARE/native/FINALIZE, stopped consistent SQL, persisted goals/context.
**Выход:** полные G3/G5 и истинный статус G6, не inferred из process exit.

- [ ] Сначала проверить, можно ли использовать уже работающий039 witness без изменения. Не внедрять новый observer только из-за имени040.
- [ ] При реальном recovery/commit defect — причинный RED, минимальное исправление существующего owner, GREEN, rollback/unknown/stale negative. Ничего не засчитывать до confirmed commit.
- [ ] На финальном кандидате выполнить stopped all-birth cut, actual global COMPLETE/retained0 до остановки pools; объяснить поздний SQL exact/linked.
- [ ] Два actual normal sameDB restart без reimport/reset/reseed, сравнение state/context/items/skills/goal/native position и reward lineage, реальное полезное продолжение после загрузки.
- [ ] G6: читать сохранённые inputs; разрешённый fault-OFF recovery только на новой040 копии. Нет exact input — не конструировать crash-state SQL-ом.
- [ ] При сохранённом запрете зафиксировать BLOCKED_AUTHORITY у недоступной части G6 и продолжить остальные шаги. При отдельном разрешении исполнить только ограниченную ветку SCENARIOS.md.

**Готовность шага:** conservation/restart действительно работают; нет ложного общего GREEN из частичного recovery.

## Шаг5. Автономный observer и автоматическое REAL-прилипание

**Файлы:** фактический существующий observer owner, LocalPlayPilotService/SyntheticHumanService/M1Observation по необходимости, existing locality wiring и private harness. Сначала найти owner, не создавать новый по предположению.

**Вход:** mode/focus, actual connection/session generation, position/instance, native/Synthetic state.
**Выход:** observer работает без игрока, ждёт REAL без активного агента, атомарно переносит фокус, корректно отсоединяет старую сессию.

- [ ] Прочитать действующую autonomous/REAL логику и срок жизни. Отделить пассивный observer от525s Synthetic action-session и consent.
- [ ] Проверить переходы offline→synthetic/autonomous, REAL arrival→binding, logout→autonomous, reconnect same character→new connection, stale logout→не отвязывать новую. Synthetic fixture не объявлять actual REAL.
- [ ] При дефекте поправить существующее lifecycle wiring. Не удлинять action TTL/подделывать heartbeat; не перехватывать управление пользователем.
- [ ] Проверить отсутствие второго ложного locality anchor, двойного рождения фантомов и ошибочного background handoff.
- [ ] Остановить управляющий тестовый wrapper/heartbeat законным способом; пассивный observer/сервер должны остаться способны принять настоящего игрока.

**Готовность шага:** O040_SERVER PASS; ACTUAL_REAL_BINDING остаётся NOT_RUN до личного входа пользователя.

## Шаг6. Один финальный кандидат и конечная матрица

**Файлы:** frozen committed product source/tests, текущие runtime конфиги/каталоги/evaluator, GATES040.tsv.

- [ ] Зафиксировать CODE_COMMIT, production JAR SHA256, config/catalog/evaluator hashes и состав deployed classes. Не подменять production JAR тестовыми classpath.
- [ ] Выполнить две natural группы6–8, предварительно зафиксировав ВСЕХ enrolled и правила первичных melee/caster. Не исключать failed actor задним числом.
- [ ] Использовать неизменённый CONTINUITY_V2 и исходное окно (в039 —380s; точные параметры считать из принятого launcher/evaluator). Не менять проценты/лимиты ради PASS.
- [ ] Совместить финальные G1/G2/G3/G4 наблюдения, где сценарии совместимы; не считать смерть внутри стабильного G2 успехом этого G2.
- [ ] Закончить финальные G5 и разрешённую часть G6; прогнать original93/93 и11/11 плюс все relevant protected/affected/new suites из ACCEPTANCE.
- [ ] Один adversarial self-review полного diff: конкуренция, capabilities/navigation, сохранность, observer, privacy/safety. Исправления review требуют нужной повторной проверки.
- [ ] Любая новая semantic/config/evaluator правка инвалидирует связанные строки матрицы. Старые результаты сохраняются с их фактическим candidate_id.

**Готовность шага:** известен статус каждого gate; нет cherry-pick результатов разных несовместимых сборок.

## Шаг7. Запущенный acceptance runtime, публикация и личная приёмка

**Файлы:** HANDOFF.md; итоговые RESULT040.md/FIX_REVIEW040.md/GATES040.tsv/REAL_FINAL040.md; private runtime manifest и компактный review ZIP.

- [ ] Штатно завершить тестовые эпизоды. Запустить один здоровый финальный/preview runtime из той же candidate-сборки на своей040 БД. Убедиться, что process cleanup-тестов не остановит его.
- [ ] Проверить слушающие game/login порты, привязку DB, отсутствие опасных pending faults, способность actual login через существующую конфигурацию; пароли не публиковать.
- [ ] Оставить observer в безопасном автономном/passive режиме, готовом к REAL; не оставлять сценарий, который через TTL/финальный finally выключит сервер.
- [ ] Exact-path commit и normal non-force push результатов, проверить local=remote. Не включать raw/секреты/чужие untracked. Отдельно связать CODE_COMMIT и итоговый REPORT_COMMIT.
- [ ] Собрать и прочитать компактный TASK040-review.zip по HANDOFF; не пересобирать гигантский039 archive. Сообщить пользователю фактический статус и короткое действие «зайди в клиент».
- [ ] После настоящего входа, в продолжении этого же TASK040, проверить actual REAL_LOGIN/EnterWorld, observer binding и видимую полезную игру. Пользователь лично подтверждает результат; не синтезировать его согласие.
- [ ] Только G0–G8+O040_SERVER+REAL_FINAL все PASS дают CLOSED. При закрытии — короткий doc-only commit/push, без нового semantic deploy и без автоматического выключения сервера.

**Готовность задачи:** M1 закрыт только фактически; любой остаток назван честно и не превращён в новую бессрочную кампанию.
