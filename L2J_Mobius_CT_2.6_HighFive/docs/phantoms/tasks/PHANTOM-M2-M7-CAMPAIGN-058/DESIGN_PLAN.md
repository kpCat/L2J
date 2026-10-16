# DESIGN / Implementation Plan058 — законченные игровые этапы

> Исполнять по шагам в текущем диалоге, без субагентов. Пакет является согласованным execution brief; не запрашивать новый brainstorming/approval для каждого локального решения внутри scope. Начальную архитектуру и ближайшие паттерны прочитать прежде реализации. Каждый этап имеет собственный критерий; поздние не мешают закончить ранний.

**Архитектура:** semantic/social решение → canonical invitation/party owner → общие action/navigation/native services → actual result → persistence. Реальные class/skill/gear facts обновляют способности. М2–М7 — интеграция этих владельцев в ordinary gameplay, не шесть конкурирующих AI движков. **Стек:** Java25, High Five, штатный Ant/datapack/SQL, WinPS5.1.

## P0. Подготовка без повторения М1
- [ ] Применить owner-forward amendment и 058 ownership. Нужны отдельные058t/058a, а не эксперимент на release057.
- [ ] Зафиксировать матрицу REUSED / PRESENT_BUT_NOT_WIRED / MISSING по М2 (коротко); найти точный переход, на котором пропадает действие реального игрока. Не читать весь repo.
- [ ] Подготовить reproducible native fixtures: по одному доступному human-like TEST leader и Phantom melee/caster/healer/buffer/recharger/archer по необходимости; уровни/классы/экипировка/изученные навыки согласованы со stock data. Non-GM для обычных правил. Использовать existing test bootstrap и native APIs, явные fixture IDs/seed/input до измерения.
- [ ] Fictional tester не маскирует `actualNetworkSession=true` и не получает роль настоящего пользователя в доказательствах. `dispatchPilot` требует свой действующий test lease и не принимает GM/slash commands; не снимать эти guards.
- [ ] Native setup не ждёт часами catch-up1280 или прокачку с1 уровня. Созданный level40 healer — FIXTURE_PRECONDITION, не доказанная естественная карьера. В реальном product никакого forceReady, бесконечного MP или дарения классов/предметов.

## P1. М2 — Real Companion (первый основной приоритет)
**Owners:** PlayerChatIngress/ChatObservationService, существующие semantic/conversation/social execution; PartyInvitationService/Delivery; PhantomPartyCoordinator/Backend/Tactics/Route; общие combat/support/navigation; PhantomSystem composition.

- [ ] Тест `M2-addressed-request-to-party`: обычный адресный «го кач?», «го пати», «пойдём вместе» через реальный server ingress. Получатель должен услышать разрешённый канал и дать доставленный ответ; успешный parse сам по себе не party. Один адресат/одна invitation, без приглашения всего сервера.
- [ ] Проверить оба пути: пользователь приглашает фантома через обычный механизм → managed delivery → решение → canonical accept; фантом сам предлагает/приглашает → пользователь принимает/отказывает через существующий service. Не autoaccept за реального человека; тестовый ответ явно TEST. Отказ/timeout/busy/full-party не создают membership и не вызывают spam.
- [ ] Follow/assist: native party с leader+2..3 Phantoms проходит проверенный маршрут с препятствием, лидер выбирает/меняет моба, фантомы помогают и после его смерти переходят дальше. Actual XYZ/action/damage и party membership, не только directive. Follow не превращается в телепорт; потеря LOS вызывает bounded path/replan, а не вечный бег в стену.
- [ ] Role support: хил раненого живого, баф отсутствующего/истекающего разрешённого эффекта, recharge допустимой цели, resurrection подходящего трупа с штатным запросом/условиями. Реальные HP/MP/effect/stand-up изменения и расходы; не простой вызов `castSupport`. Не лечить умершего вместо resurrect, не бафать бесконечно здоровый effect и не жертвовать срочным heal ради обычного buff.
- [ ] Partychat: короткие контекстные сообщения «иду», «мана кончилась», «подхилю», «не могу добежать» соответствуют actual outcome. Нельзя говорить «пригласил/воскресил», если native service отказал.
- [ ] Leave/expel/leader-change/logout-reconnect/death: old owner не продолжает следовать/хилить; чужой игрок не захватывает control; human-party pin освобождается по lifecycle. После leave фантом может вернуться к собственной цели без двойных FARM/party writers.
- [ ] Одна полная composition sequence + support role matrix + ordinary current JAR smoke и save/restart. SERVER_VERIFIED при всех требуемых М2 фактах; реальные UI/клики пользователя оставить QA_PENDING. При отсутствии wire исправлять owner, не добавлять test-only короткий путь.

## P2. М3 — речь, характер, память, отношения
**Owners:** существующие semantic/conversation/social/affiliation и их tracked RU XML/корпус, scoped persisted memory. Никакого runtime ChatGPT/LLM.

- [ ] Набор RU реплик и контрастов: приветствие, «го кач/не пойду/позже», «стой/за мной», «хиль/дай баф/мп», просьба/отказ/повтор, опечатка, отрицание. Учитывать адресата и канал: общий чат не должен заставлять всех8 повторять одно действие. Системные строки/debug NPC не являются обычными словами игрока.
- [ ] Варианты ответа меняются по текущей ситуации/характеру/отношению, не случайный шум. Короткие естественные фразы; ограничить повторы, rate и инициативу. Нет самоподдерживающегося бесконечного диалога Phantom↔Phantom.
- [ ] Запомнить достоверное совместное событие с конкретным собеседником, после normal restart восстановить память; не утекать между персонажами и не хранить неограниченный transcript. Настроение/отношения меняют preference, не native права/ID/награды.
- [ ] Интеграция с М2: понятный отказ при полной party/занятости, затем новая законная попытка; сообщения truthful. Отдельные corpus negatives и actual native deliveries подтверждены. Непонятное не выдаётся за уверенно распознанную команду.

## P3. М4 — PvP/PK, свои/чужие, группы/кланы
**Owners:** существующие PvP/social clan/relationship policies + общий target/engagement kernel; не отдельный PvE-копипаст.

- [ ] В собственных TEST actors показать защиту союзника, focus общей цели, риск/отступление и завершение конфликта; distinguish flagged/PK/peace/war по реальным правилам. Нельзя нападать на пользователя или другую работающую среду для испытания.
- [ ] Не всякий удар заставляет всю популяцию мстить. Membership/affiliation/персональная память/TTL определяют реакцию, cooldown ограничивает help/mass-aggression; hostility/stale target очищаются.
- [ ] Танк: агр приоритетен против NPC, контроль/дебафы вторичны по ситуации; в PvP не придумывать принудительный разворот человеческой цели, если stock skill его не даёт. Маг/лучник используют общую тактику дистанции ниже; союзника нельзя выбрать enemy.
- [ ] Показать canonical damage/death/karma/flag effects и normal save/restart; side-by-side negative no-PvP-in-peace/stale affiliation. SERVER_VERIFIED не означает протестированную осаду всех замков.

## P4. М5 — рейды и групповая координация
**Owners:** RaidReadiness/Recruitment/Assembly/Decision/encounter runtime, PartyTactics, CommandChannel service и общая навигация.

- [ ] Довести сбор и role assignment до реального stock encounter, а не списка READY. Общая цель/assist законны, не запрещать shared target. Танк удерживает/возвращает NPC-агро; поддержка лечит/бафает/recharge, DD выбирает допустимую позицию.
- [ ] Потеря агро/смерть участника/отрыв healer → корректная смена приоритета/regroup/retreat/recovery. Не тянуть рейд босса в непроходимый terrain и не сбрасывать его вручную ради теста. Reward и loot остаются stock, без двойных операций.
- [ ] Один существующий поддержанный encounter и матрица ролей/отказов; дополнительно champion NPC только когда действующий config/каталог его реально поддерживает. Не симулировать успех raid произвольным убийством fixture в SQL. Непроверенных боссов/эпиков не объявлять готовыми.

## P5. М6 — Rift/Kamaloka и instance lifecycle
**Owners:** существующие instance/Rift/quest adapters, scripts/teleporters и native party/instance transitions. Не универсальный quest solver.

- [ ] Смешанная TEST human-like + Phantom party проходит штатные условия входа, общий instanceId, маршрут/бой, возврат/выход/reuse. Native Player identity остаётся прежней. Внеinstance цель с теми жеXY недопустима.
- [ ] Проверить существующий supported Rift и Kamaloka template (подтвердить каталог, не придумать ID); перечислить coverage точно. Вход требует leader/level/party/items/cooldown: их тестируют, а не отключают. Смерть/leave/disconnect/закрытие instance не оставляют невидимую party и двойных наград.
- [ ] На остановке и следующем boot хранить штатное допустимое состояние, не возрождать закрытый instance ручным SQL. Ограничения долгого реального таймера явно PARTIAL; не менять часы сервера, чтобы успеть14ч.

## P6. М7 — карьера, классы и способности
**Owners:** PhantomProgressionService/Backend/CapabilityEvaluator, SkillTreeData/SkillData, Player skill learning, существующие village_master/quest adapters, inventory.

- [ ] Завершить законные цепочки первой/второй/третьей профессии20/40/76 для покрытого набора и документировать покрытие всего объявленного набора. `CANONICAL_QUEST_REQUIRED`/«знает нужный уровень» — не выполненная профессия. Использовать штатные quests/переходы; не менять classId напрямую в продукте.
- [ ] После обучения реально появляются/усиливаются skills, списываются положенные SP/items, сохраняются и восстанавливаются без повторной платы. Subclass/noblesse проходят настоящие ограничения/действующие QoL scope; не выдавать всем GM-привилегию.
- [ ] Общий resolver ниже используется во всех предыдущих М-этапах. Проверить естественный level-up/learn и re-materialization/restart на заданных заранее близких к границе fixtures. Fixtures высокого уровня разрешены как input, но не доказывают прохождение всех уровней с1.
- [ ] Для М7 целиком нужны все его обязательные составляющие; один Fighter→Warrior или одна новая способность — PARTIAL_M7, не универсальная карьерная система. Завершённые части сохранить и интегрировать, даже если весь М7 не помещается.

## Общая тактика и навыки — требования владельца, не бонус по остаточному времени
- Доступные действия брать из реально изученных skills/их уровней, активного класса/subclass, экипировки, MP/HP, reuse и расходников. Базовые навыки не считать одинаковыми для всех классов; близость стартового набора не оправдывает один вечный новичковый spell.
- При learn/level/class/subclass/gear/recovery менять generation фактов либо доказанно перечитывать их до решения. Кэш не должен оставлять старый skill rank или разрешать уже недоступный навык. Новый навык нельзя использовать до фактического изучения.
- Устаревший навык не удалять из Player автоматически: снизить utility при наличии лучшего уместного. Иногда дешёвый старый spell полезен при низком MP — выбирать по контексту, не только максимальному skillId/power.
- Маг между завершёнными кастами и откатами делает безопасные шаги назад/в сторону, если это улучшает дистанцию и не портит LOS/роль. Лучник держит рабочую дистанцию, стреляет и перемещается, не пятится бесконечно. Нельзя отменять собственный cast/shot ради постоянного «кайта».
- Агро/танк/дебафы применяются общей role policy в фарме, рейде и PvP с их разной legality. Не требовать, чтобы taunt работал на игрока как на NPC. Не переагривать лишние пачки ради красивого движения.
- Контрольные пары: learned rank1→rank2; subclass switch убирает недоступное; missing MP/item/cooldown отклоняет skill; healer/танк/лучник после перехода сохраняют роль. Ошибка этого общего слоя блокирует только реально зависимые заявления.

## P7. Завершение окна работы
Не начинать новый большой этап, если его минимальная проверка не помещается до резерва. Оставшееся время — исправление текущего, тесты и usable build, не заготовки ради цифры М7. Final clean build, affected/full regression по TASK, operator smoke и один normal preserve-progress restart; это может использовать последний уже нужный integration run. Новая preview STOPPED, старая057 работает. Подробнее — ACCEPTANCE.
