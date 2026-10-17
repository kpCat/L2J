# ACCEPTANCE059 — замороженные конечные факты

## Как читать результат
Вести отдельно `implementation`, `serverEvidence`, `ownerQA`. Допустимы NOT_STARTED, IMPLEMENTED_UNVERIFIED, PARTIAL, SERVER_VERIFIED и OWNER_QA_PENDING. Отдельный CASE_PASS не поднимает весь milestone. М2 сохраняется в058 scope при успешных affected проверках; обнаруженная регрессия явно учитывается. М1/G4/G6 остаются deferred, не новые blockers059.

**Критерии058 не удаляются.** Эта таблица раскрывает его оставшиеся сценарии. Для финального полного M3–M7 свериться с исходными S3…S7 в `.../PHANTOM-M2-M7-CAMPAIGN-058/ACCEPTANCE.md` и DESIGN_PLAN058. Непокрытое явно PARTIAL, а не незаметно суженный «полный» этап.

| ID | Положительный результат | Обязательные границы |
|---|---|---|
| C3-HOLD | Native follow→доставленная команда→safe HOLD в объявленном anchor радиусе20с при уходящем лидере→RESUME→native obstacle-follow/полезное действие | Нет TP/root, вмешательства в чужой cast/move; actual XYZ/result, не only ACK |
| C3-AUTH | Directed speaker/party leader управляет нужным companion; PARTY всем — только roster; повтор идемпотентен | Wrong sender/former leader/instance/generation/negation/ambiguous address не инициируют unintended action |
| C3-BUSY | Команда во время cast/attack исполняется после корректной границы; эффект/cost один; support со стойки законен | Нет потерянных callbacks, двойного FARM/party owner, ложного «сделано» |
| C3-SOCIAL | Исходный S3: реальные delivery и context variants, scoped joint/personal memory после restart, bounded initiative/no loops | No fabricated memory; counterpart isolation; unknown/unsupported/refused честно, query без ложных записей |
| C3-LIFE | Leave/expel/leader change/logout очищают старый приказ; restart сохраняет intent по версии и переавторизует либо законно инвалидирует | Не восстанавливать несуществующую native Party/чужую authority; ordinary FARM может продолжиться |
| L7-SP | Earned SP→immediate normal learn без ручного save; новый skill/rank/effect, correct SP/items, normal reload | Конфликтный stale state по-прежнему отвергается, нет blind sync/дублирования |
| L7-RACE | Gain/autosave/двойное learning/class-index смена сериализованы; committed расход соответствует результату | Insufficient/rollback/duplicate/restart не теряют earned и не дают бесплатный skill; existing human controls |
| P4-FLOW | Native defense/focus→resource-aware retreat→regroup/end conflict; flags/karma/death/save в объявленном PvP scope | Ally/peace/war/clan/instance/stale threats соблюдены; human taunt не как NPC; servitor coverage честно |
| R5-FULL | Поддержанный raid: readiness/сбор/роли/агро/support→полный native death/result/stock reward→regroup | Нет reward replay, headcount подмены, искусственного HP/GM gear во время боя; отдельный failure/recovery case |
| I6-KAMA | Штатная Kamaloka entry→victory→правильная reward/reuse/closing/exit | Wrong leader/level/fee/reuse/instance, death/logout handling, no timer skip |
| I6-RIFT | Rift entry/fee→полезный native combat/положенный reward→штатный room/exit lifecycle | TEST human не потерян; death/leave/disconnect/closing identity, no duplicate fee/reward |
| L7-TRAVEL | Field earned→решение обучения→trainer travel/paid learn→field return→новый effect | Без ручного TP внутри измерения/боя из Peace/пропажи gear/SP |
| L7-CAREER | Законченные реальные20/40/76 и subclass/noblesse пути в полном заявленном scope | Уровень/skill fixture не natural career; Q401 не всё М7; QoL не квест |
| KEEP-M2 | Delivered invitation/consent, role follow/assist/support, learned refresh, leave/rejoin/ownership текущей сборки | Current native affected cases; предыдущие101 не магическое полное доказательство |
| DELIVERY | Clean committed cumulative ordinary candidate059 + known issues, configs/data/tools/SQL, проверенные собственные controls/save/restart | Защищённые057/058 не изменены; candidate STOPPED; actual source/JAR и usable QA input |

## Правила измерения
1. До входа в сцену зафиксировать actors/roles, seeded preconditions, current code/JAR, цель/полный знаменатель, ожидаемый effect и deadline. Seed/mode брать из source. Изменившийся input — новый явно независимый episode; старый FAIL сохраняется.
2. Fixtures уровней/вещей/MP/HP допустимы до сцены. Во время неё — только штатные действия/расходы/награды; не спасать живучесть прямым heal, не довыдать стрелы или SP. Native spells/consumables самой группы разрешены по игре.
3. Никакого обязательного380s М1 для каждого нового action; C3 hold20s — проверка устойчивости команды, не многоминутный soak. Raid/instance window обосновывается script limits/реальным ETA и заранее фиксируется. Срез полного боя на полпути остаётся PARTIAL.
4. Native result = фактическое изменение World/effect/cost/reward/owner и нужной durable границы. ISSUED/CAST/READY, строка «готово» или одинаковый XYZ вSQL отдельно недостаточны. Test double подтверждает server path, не пользовательский network/UI session.
5. Old FAIL058 не переклассифицировать без объяснения; 109 исторических FAIL не считать109 дефектами. Различить invalid input, rejected legal operation, tooling failure и продуктовый RED.
6. При reuse доказательства сравнивать затронутую семантику, data/config и loaded code, а не только имя класса/число тестов. Final baseline selection101 расширяется новыми case IDs; не удалять требуемые cases, чтобы новый итог опять стал101/101.
7. Случайный нулевой drop — штатный исход, когда доказана вызванная reward pipeline и отсутствие дубля. Поддержанный encounter с неразумным подбором состава — не повод менять boss balance.
8. Сохранность ordinary state после новых операций обязательна даже при отложенном G6. Если mutation известным образом теряет/дублирует данные, зависимую feature не выдавать как пригодную. Безопасные независимые результаты сохранить.

## Минимальный итог кампании и ранний выход
Основная ожидаемая ценность — завершённые C3/S3 и L7-SP/L7-RACE; затем реальные end-to-end P4/R5/I6/L7 по приоритету. Это не основание остановиться сразу после двух первых successes, пока есть бюджет и полезные шаги. Полнота каждого milestone определяется исходным scope, не временем.

Завершить раньше можно при исчерпанном безопасном доступном scope, внешнем stop/лимите либо начале измеренного delivery reserve. Написать, что именно мешает каждой оставшейся независимой ветке. Не закрывать goal полностью, если технически PARTIAL; честный partial delivery не равен неудаче всей работы.

## Финальная самостоятельная проверка
- Final чистый build и current regression; native основные058/059 scenarios не зависят от `.phantom-local` product overlays, fake user privileges или модифицированного клиента.
- Новая release059a создана из stopped consistent058a export; сверить identity/schema/data перед первым ordinary boot. Потом обычная игровая активность может менять данные, не требовать побайтовой неподвижности всей живой БД.
- Новый candidate: Start→fresh Status/observer→штатный save/Stop→sameDB Start→useful smoke→Stop. Не оставлять fixture TEST producers/worker threads. Нужны actual absence и отсутствие pending writer своей среды, а не только надпись STOPPED.
- В START_HERE_RU: точные Login/Game/control, куда смотреть пароль (не сам секрет), TestAdmin/kpCat allowlist, имена/роли доступных партнёров и команды «стой/за мной/го пати». Обозначить non-GM access: //GM-команды не предлагать access0.
- Последнее подтверждённое live presence партнёров и условия их появления хранить отдельно от saved XYZ. Если доказать обычное появление не удалось, явно QA_SETUP_UNVERIFIED и точный следующий шаг, без заявления что пользователь гарантированно увидит их.
- Оба старых candidate и release DB сохранены. Никакого review ZIP. Runtime ZIP без secrets/raw/БД только после действительно полногоМ7; иначе обычный пригодный каталог059.
