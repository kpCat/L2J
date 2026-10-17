# DESIGN / Implementation Plan059 — законченные команды, развитие и encounters

> Для Codex: исполнять inline по `executing-plans`, без субагентов. Этот пакет — заранее подготовленный execution brief; отправленный пользователем `/goal` утверждает его. Локальные решения внутри scope не требуют нового вопроса. При неизвестной архитектуре сначала узкий read-first, не выдуманные interfaces.

**Goal:** закончить М3 и безопасное immediate-SP learning, затем двигать М4–М7 завершёнными игровыми сценариями.
**Architecture:** существующие conversation/party/route/combat/progression owners, shared scheduler, native Player/Party/Quest/Skill и durable transactions; production не знает task IDs.
**Tech Stack:** Java25, Ant, High Five datapack, MariaDB на existing058 lanes, WinPS5.1.
**Spec:** GOAL.md + AUTHORITY.md + исходный DESIGN_PLAN058; утверждённые конечные факты — ACCEPTANCE.md.

## Review focus
- Приказ чужого/бывшего лидера, отрицание и неоднозначный адресат: никакого захвата контроля.
- Команда во время cast/movement: отменять только собственную route работу, earned action не теряется.
- Earned SP + autosave + повтор learning + subclass switch: без двойной платы, потери earned или отката ранга.
- Длительный бой: полная шкала HP, MP/reuse/consumables/aggro и естественные script timers, не один удачный cast.
- Restart/leave/закрытие instance: stale command/reward не повторяется, owned work не утекает; условия для ручной проверки не выдумываются.

## P0. Поднять продолжение (ориентир до 30 минут)
- [ ] Прочитать текущий RESULT058 и свои local checkpoints, подтвердить BASE/OWN и exact058t/058a. Зафиксировать минимальный baseline без rerun101/перечисления старых миллионов файлов.
- [ ] Принять AUTHORITY059 delegation existing058 lanes, сохранить один stopped058a backup; setup и guard-тесты только изменённых bindings. Не переименовывать реальный058 namespace и не создавать059t/059a.
- [ ] Выбрать первые M3 native actors из существующих подготовленных TEST partners; учесть actual role/skill/roster/resources. Сохранить короткий case manifest. Начать первое исправление/сценарий, а не новую многочасовую подготовку.

## P1. М3 — команды должны управлять поведением
**Owners:** `PhantomConversationService`, `L2jPhantomConversationExecutionPort`, execution catalog/model/store; `PhantomPartyCoordinator`, `PhantomPartyRouteCoordinator`, party state/store; RU semantic/conversation XML. Ближайшие tests: `PhantomConversationIntegrationSuite`, `PhantomConversationExecutionSuite`, `PhantomSocialMemory058`, `PhantomPartySuite`, moving-follow/composition058.

### P1a. HOLD / RESUME
- [ ] RED: current native companion follows leader; адресное «стой здесь» не меняет режим. Добавить тест через существующий ingress, а не напрямую private field. Три роли в одной canonical mixed TEST party; контролируемый human-like partner обозначается TEST.
- [ ] Ввести минимальное typed command state **в текущем party owner**: HOLD с anchor X/Y/Z/instance и RESUME_FOLLOW. Состав identity: actor, canonical group/generation, authorized speaker, monotonic command/version. Использовать существующее persisted party/goal state либо небольшой versioned payload; не параллельную static-map команд без lifecycle.
- [ ] HOLD фиксирует точку после безопасной передачи своего follow/move owner. Разрешено отменить/остановить только своё follow movement через существующий lease. Уже принятый cast/attack заканчивается; до перехода можно честно ответить «сейчас закончу», нельзя сказать «стою», пока действие не принято owner.
- [ ] В HOLD не начинать новое преследование лидера/цели; обычная поддержка с места допустима по native range. Заранее документировать радиус допустимого hold-перемещения (предлагаемый128 игровых единиц; окончательный bounded default зафиксировать до теста). Не имитировать HOLD root/paralysis или постоянным SQL/TP возвратом на anchor.
- [ ] HOLD учитывается всеми новыми movement-producing действиями текущей party: follow, assist-погоня и spacing не должны обходить anchor через другой CombatService путь. Цель вне разрешённой области — корректное ожидание/отказ, а не скрытое перемещение. Проверить это одним negative с выбранной лидером далёкой целью.
- [ ] RESUME отменяет прежний hold intent и возобновляет shared follow к текущему лидеру вокруг препятствия. Не возобновлять прежние waypoints другого group/instance/epoch. Лидер-человек не управляется фантомом.
- [ ] Admission: обычный признанный лидер даёт command своим companions; другой персонаж/бывший лидер/stale generation/чужой instance отвергаются. PARTY явное «всем» адресует текущий roster; directed whisper/name — одного адресата, не всех. Лидер получает одну итоговую реплику вместо хора повторов.
- [ ] Повтор команды идемпотентен. «Не иди за мной» не превращается в положительный FOLLOW; неизвестная/неоднозначная просьба даёт отказ/уточнение без скрытой мутации. Не расширять grammar до произвольного чат-скриптинга.
- [ ] Native sequence: follow → hold (лидер уходит,20с наблюдения после safe boundary, anchor соблюдается) → resume → obstacle route → assist full stock kill. Повторить HOLD во время занятого cast: эффект/cost ровно один, затем корректный hold и resume. Не использовать heal/abort fixture после начала измерения.

### P1b. Реальные ответы, память и lifecycle
- [ ] Пройти конечный набор058 S3: greeting, «го кач/не пойду/позже», hold/follow, heal/buff/MP/res просьбы, отказ/full/busy/unavailable/повтор/опечатки/отрицания/адресат. Если запрошенного навыка нет — честный отказ; не добавление навыка. Существующие поддержки М2 переиспользуются.
- [ ] Для функциональных реплик разделить accepted/pending/completed/refused. При отказе cast или неготовом skill не отправлять «вылечил/воскресил». Native delivery, а не только выбранный response template, — положительный факт.
- [ ] Персона/настроение/отношение влияют на формулировку и допустимые preferences. Проверить хотя бы два различающихся контекста и bound повторов; memory только доказанные scoped факты. Никакого обязательного LLM/морфологического фреймворка вместо завершения действий.
- [ ] Память знакомого и совместного party события выдерживает ordinary save/restart и изоляцию второго собеседника. Read-query не записывает новую ложную память; повторный callback не удваивает событие.
- [ ] HOLD после normal restart не должен ожить для чужой/новой party. При наличии сохранённого прежнего намерения переавторизовать по current membership; если party не восстановилась, намерение становится недействующим до нового законного command. Не фабриковать native party ради восстановления HOLD.
- [ ] Leave/expel/leader change/logout/death очищают старую authority/lease; сохраняют own FARM continuation. Targeted М2 regressions и current native command composition → self-review → publish. Полный М3 SERVER_VERIFIED только при закрытых S3, не по одному HOLD.

## P2. Immediate-SP learning — общий приоритет
**Owners:** `PhantomClassSkillLearningTransaction.execute/lockAndValidate`, `L2jProgressionBackend`, `PhantomProgressionService`; связанные native `Player`/stat/store/skill writers. Tests: existing progression transaction suites и earned/paid058 сценарий в `PhantomCombatServerIntegrationSuite` (mode/seed сверить в Launcher).

- [ ] Воспроизвести RED058: штатный kill/earned SP → немедленный обычный запрос обучения у подходящего trainer **без ручного предварительного save**. Зафиксировать runtime SP, durable SP, class/index, skill rank, item ownership и текущего native writer.
- [ ] Установить причинную связь между earned update, autosave и durable learning. Текущее сравнение SP — защита от потери состояния; его не удалять. Одна критическая секция над player не доказывает сериализацию всех остальных writers.
- [ ] Обеспечить штатную ограниченную reconciliation/save boundary перед оплатой либо расширить существующий единый ownership/transaction protocol, если это действительно нужно. Конкретное решение выбрать после проверки writers/lock order. Runtime и БД синхронизируются автоматически; чужой конфликт остаётся отказом или bounded retry, не blind overwrite.
- [ ] Не выполнять `UPDATE sp=<runtime>` ради совпадения перед каждой покупкой. Не держать игровые lock во время ожидания, callback которого требует тот же lock. Не сбрасывать EXP/SP из старого snapshot и не принимать любое расхождение за заработанный бонус.
- [ ] GREEN: immediate request eventually succeeds без внешнего save; фактическая плата и skill effect/current-rank. Повтор/двойной запрос не берут плату ещё раз; нехватка SP/items, stale class/epoch и настоящий конфликт сохраняют отказ.
- [ ] Контролируемо проверить gain/autosave одновременно с learning, две конкурирующие покупки, main/subclass, insufficient/rollback и повтор после ordinary restart. Учёт: итоговый SP = исходный + реально earned − стоимость всех и только committed обучений; items и skills соответствуют. Не запускать G6/OS crash.
- [ ] Неопределённый исход commit/ошибка после commit не допускает слепого повторного списания: сначала сверка durable результата через текущий owner. Адресный тест ошибки reconciliation допустим в058t, без аварии JVM и без нового WAL/двойного persistence engine.
- [ ] Один natural learn→save/restart→effect сценарий на новом JAR, affected ordinary-human test. Исправление не завершает кампанию: опубликовать и продолжать.

## P3. М4 — законченный ограниченный PvP flow
**Owners:** существующие PvP policies/service, affiliations/clan/party, threat facts и общий CombatService/Engagement. Tests: `PhantomPvpSuite`, native PvP058 в Combat integration.

- [ ] Сначала используем уже подтверждённые actual HP/CP threat и distance058; не писать их заново. Добавить недостающее решение retreat/regroup/end-conflict, обычные flags/karma и context party/clan/war.
- [ ] Объявленные свои TEST participants: attacker, защищаемый member и helpers. В одном native эпизоде — законный ответ/focus и отход при низких ресурсах к достижимой точке, прекращение stale pursuit; затем восстановление/повторное полезное действие. Реальные movement/damage/resources, не статус RETREAT без движения.
- [ ] Negative мирная зона/недопустимая цель/смена группы/чужой instance/устаревшая угроза; human target не переназначается NPC-taunt'ом. Отдельные clan/war guards проходят только native legal path; не объявлять войну защищённым реальным игрокам.
- [ ] Для смерти/PK последствий — фактический native исход, правильные flags/karma и ordinary save/reload. Не провоцировать массовую вечную месть всех профилей. Servitor coverage и неподдержанное перечислить; complete М4 только согласно всем заявленным S4 требованиям.

## P4. М5 — один поддержанный рейд до результата
**Owners:** current RaidReadiness/Recruitment/Assembly/Decision/AttemptRuntime, party tactics/support, shared combat/nav. Начать с saved tank-death058 и позднего early-heal fix, а не с полного повторного поиска причин.

- [ ] До enrolment определить настоящий supported boss/script/level/полные HP, законный состав≤9, нужные learned ranks/gear/consumables и feasible маршрут. Подготовленные классы и вещи — честный TEST input, не earned. Не обязаны использовать прежние4actors против прежнего босса, но старые FAIL сохранить, новый input объявить заранее.
- [ ] Пройти штатные readiness→сбор/роли→approach→encounter. Во время боя: aggro tank, срочный heal, MP/recharge, правомерные buffs и DD duty cycle. Не загонять caster/archer в бесконечный kite и не сбивать им действия.
- [ ] Первые60–90с: оценить useful DPS, net boss HP/regen, запас MP и время завершения. Если score/timer/gear делают победу неосуществимой, сначала причина; не пускать много одинаковых десятиминутных прогонов. Для новой независимой сцены допустим законно подготовленный состав, но не изменение HP/drop/rate/script во время старой.
- [ ] Положительный сценарий довести до stock death/kill credit и правильной штатной reward/drop handling. Случайный нулевой drop допустим при фактическом native RNG; не требовать гарантированную вещь и не подменять награду. Повтор terminal callback/save/reload не выдаёт второй reward.
- [ ] Regroup/потеря участника/восстановление — отдельный адресный native case, а не требование умереть в каждом удачном kill. Общий roster после восстановления и useful continuation. Один tactics subset не закрывает S5.

## P5. М6 — Kamaloka и Rift без обрыва на входе
**Owners:** существующие Rift/quest-instance adapters, canonical NPC/scripts/party/instance/reuse. Поддержанные058 inputs: Kamaloka template57 и Rift type1 — сначала проверить текущий code/catalog.

- [ ] Для нового encounter до измерения проверить levels/fees/skills/gear всех участников и native group EXP restrictions. Прежний Rift43vs30 — не оправдание отключать разницу уровней; взять законный новый input. Human-like tester может двигаться/вносить вклад через обычные TEST native actions; не обязан стоять манекеном до смерти.
- [ ] Kamaloka: actual entry→бой до полного stock encounter end→награда/очистка/штатный выход→проверенный reuse отказ при преждевременном входе. Таймер окна определить по реальному boss HP/DPS/script, не жёстко600с, если законная победа занимает дольше. Прежний058 FAIL не переписывать.
- [ ] Rift: native recruitment/fee/shared room→полезные kills с положенными reward/EXP→script room/exit lifecycle. Смерть/leave/disconnect/closing и cleanup — адресные negatives с сохранением идентичности; не сокращать native lifetime или выдавать искусственную EXP.
- [ ] Автоматическое readiness→entry требуется от product adapter; fixture может подать намерение, но не выполнить вместо AI весь сценарий вручную. Проверять gate/fees/consent/instanceId, а не дергать script bypass в обход условий.
- [ ] Если штатный полный timer не помещается в оставшееся время, завершить достигнутое и пометить точную непроверенную границу. Не объявлять весь М6 по одному entry/одному мобу.

## P6. М7 — автономная карьера после P2
**Owners:** existing progression/catalog, quest/instance/acquisition authorities, `SkillTreeData`, village_master scripts, inventory/equipment и shared navigation.

- [ ] Сначала end-to-end earned SP/EXP→решение идти к trainer→native travel→paid learn→native field return→использование нового ранга. Мирную зону не обходить прямой установкой XYZ посреди measurement. Подготовка actor рядом с границей уровня до теста допустима.
- [ ] Затем классовые переходы20/40/76 через поддержанные native quests/условия. Для конкретного маршрута отдельно записать покрытие: starting class, quest/transfer, уровень и конечный class. Прямая выдача classId — fixture, не успех перехода.
- [ ] Subclass/Noblesse: настоящие native eligibility/quest/cost/switch/save условия. Унаследованный AutoNoblesse/QoL не считать выполненным quest. Никакого универсального solver ради охвата; закончить один явно поддержанный маршрут и сохранить точный partial coverage.
- [ ] Skills везде из current learned state: смена class/subclass/gear/learn/reload инвалидирует недоступное. Дешёвый старый навык может быть уместен при низкой MP; не удалять его из Player. Higher-rank не выбирается до реального learn и не используется после утраты.
- [ ] М7 SERVER_VERIFIED только при полном объявленном покрытии058 S7; один исправленный SP/одна первая профессия — полезный законченный subset, а не полная карьера.

## P7. Финальное окно
- [ ] Заморозить semantic candidate до начала delivery reserve; не добавлять косметику/новый milestone в финале. Проверить affected М2 и все заявленные new server facts на текущей версии.
- [ ] Один clean committed build Game/Login/datapack/tools; final regression — baseline selection058 + относящиеся новые/старые защиты, полный счётчик без переименования omitted tests в PASS. Ошибки лечить адресно, общий rerun — только по causal impact.
- [ ] Stopped export058a→новая release059a один раз; START/STATUS→save/STOP→sameDB START→полезное наблюдение→STOP. Сочетать с уже необходимым current integration restart, когда scope идентичен, а не создавать копии доказательств.
- [ ] Доставка содержит инструкцию: совместимый client port, account/character без пароля вGit, точные partners/роли/команды и последний проверенный World anchor. Сохранённый SQL XYZ не выдавать за гарантированное появление. Подготовку QA проверить обычным native runtime path; невозможность увидеть партнёров — отдельный QA_SETUP_UNVERIFIED, а не обещание пользователю искать их часами.
- [ ] Если вход выбран на2106, подтвердить свободный порт и новый exact mapping, старые057/058 не стартуют и не меняются. Штатные controls должны работать и после перезапуска без Codex/private helpers.
- [ ] Один RESULT, known issues, honest milestones/coverage/delivery source/JAR и safe next step; exact publication, state STOPPED. Неполные свои изменения безопасно сохранить, не потерять.
