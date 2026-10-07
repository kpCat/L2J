# Подтверждено и не подтверждено

## A: неправильная классификация нового native действия
TASK023 final SceneA411/663: owned HitTask → Attackable.addDamage → AttackableAI retaliation
→ Creature.doAttack → runCombat → strict run → NATIVE_EARNED_RECIPIENT_NOT_CAPTURED.
Creature.doAttack использует `runCombat(... "attack-frontend", ...doAttackNative(target))`.
PlayerNativeWork.requireCapturedEarnedParticipants ищет ANY earned frame во всём ambient
context и требует current(owner) для новых явных participants. Вложенный синхронный запуск
НОВОЙ атаки NPC ошибочно трактуется как старое delayed продолжение. Сама строгая защита
старого HitTask нужна. Не отключать её глобально.

## B: geometry prerequisite before durable PREPARE
captureOwnedNative → capture(... nativePersistence=true) → exactAnchor(player, previous)
до формирования NativeCapture. exactAnchor либо принимает прежнюю FARM area/anchor, либо
требует ровно один atAnchor. Закономерное движение за границу модели не является порчей
реального Player, но сейчас запрещает даже snapshot/PREPARE сохранения заработанного.
281 live26919/1827 vs saved25937/1720;459 также не сохранён. Это не bug сравнения счётчиков:
live/SQL записаны отдельно. Старые live totals не являются безопасным replay source.

## C: stopped dependency during POST_STORE
PhantomSystem сначала topology.beginStop, позднее materialization.shutdown.
Topology.registerProfile/updateProfile требуют runningView. У1176 native save был успешен,
а POST_STORE завершился NOT_RUNNING. Это не потеря его EXP; это незавершённый lifecycle.
Нельзя объявлять ошибку FINALIZE, если FINALIZE уже committed, или повторять начисления.

## D: world geometry vs farm intent
281/459/1176 перестали работать с LOCAL_FARM_UNAVAILABLE/travel.journey_deadline.
281 ownerOPEN, guardsCLEAR, target0, AutoPlay registration false, shortTargets4/longTargets11.
Причина расхождения candidates пока не полностью доказана: census не тождественен planner
eligibility. Точные code smells: расстояние до ЦЕНТРА anchor≤2000 вместо расстояния до
FARM area, исключение всего npc@anchor после провала одного пути, deadline до проверки
достигнутой полезной позиции, искусственный farmingStandpoint около canonical center.
Эти варианты проверить отдельными RED; не заявлять все четыре доказанными заранее.

## Что не установлено
Нет подтверждения общей порчи GeoEngine. Нет разрешения ослабить earned store guards.
DEATH/soft-return/restart/crash не PASS; B-scene не состоялась. Последние empty drains не
исправляют провал store пятерых живых акторов. Relevant aggregate4/7 и schema barrier —
реальные пробелы доказательства, не «unrelated debt» по умолчанию.
