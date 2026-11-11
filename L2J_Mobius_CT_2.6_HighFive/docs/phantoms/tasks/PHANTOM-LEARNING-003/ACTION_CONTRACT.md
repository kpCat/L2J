# Контракт действий и механика Party

Основной принцип: модель НЕ создаёт команды и не обходит сервер. Она ранжирует строго перечисленные реальные допустимые действия, исполнение — только через существующий штатный владелец.

## LegalActionV3
- action family + optional subfamily + version; owner (`Combat`, `PartyTactics`, `Navigation`, `Progression`, …); actor profile/object epoch/session generation; target object/spawn+group generation; skill/level, action cost, capabilities, cooldown/mana/target/LOS/range validity; `training_receipt_kind` и источник; `criticalSafety` flag.
- Candidate generation в bounded budget; actor owner и target identity проверяются перед и после scorer, а в native dispatch ещё раз штатно. No candidate = legacy; one candidate = direct stock; confidence/missing data/latency failure = legacy.
- `HEAL` поддерживает себя или текущего союзника, а также выбор из двух допустимых healing spells если оба изучены; штатные restrictions Resurrection/scroll не переопределять.

## Маршрут поддержки без конфликта owners
`PhantomPartyTactics.plan` создаёт существующие HEAL/RESURRECT/RECHARGE; `PhantomPartyCoordinator` распределяет им lease; обучаемый ranker V3 получает только их законные кандидаты для конкретного actor, не захватывая чужой Combat/Action owner. Если emergency rescue больше приоритета нетоксичного action, штатный rescue выигрывает; neural tie-break/selection — только когда безопасны обе альтернативы. Не рассматривать успешное изменение выбора как доказательство правильной тактики, пока нет independent outcome.

Владелец архитектурного решения: native Party leader rights. Neural scorer не получает полномочия распоряжаться человеческими членами Party, менять target участника с human owner, инициировать неточного PvP/PK или изменять клановые роли.

## Нативный пример HEAL
В тестовой независимой Party Cardinal (проверить реальный класс и доступные native навыки по каталогу, не заполнять искусственно) имеет по крайней мере два допустимых лечебных варианта — target A/target B либо два законных заклинания для повреждённого союзника. Последующее действие реально списывает MP и меняет HP. Известный `PlayerNativeEvidence` уже содержит для части Heal exact-native receipt; если нужен точечный observer, он должен быть пассивным и не менять расчёт HP. Отсутствие эффекта/дедлайн/ошибка cast -> UNKNOWN, не false positive.

Пример не должен быть «заколдовали фиктивный HP расход после Cut». Если pre-CUT установлены начальные HP ради тестового состояния, это вход теста, а НЕ reward. Учебный сигнал только от последующего настоящего Player.useMagic/HP writer при достоверном owner.

## Будущее, не выполнять в 003
CONTROL/DEBUFF: separate native status application+duration/cleanse/immune evidence. MOVE: native travel/effective reachable, route/time/risk, never free teleport. RESURRECT: native target alive after accepted skill/scroll (cast time distinguished). SUMMON: summon generation + own target and health, downtime, cooldown. SPOIL/SWEEP: canonical droplist, corpse rights, adena/EXP/SP; обязательства не выключаются моделью. PvP/PK/olympiad: stock legality and karma, hidden enemy inventory not visible. Эти примеры являются требованиями на формат, а не предъявленными к сдаче обученными способностями.
