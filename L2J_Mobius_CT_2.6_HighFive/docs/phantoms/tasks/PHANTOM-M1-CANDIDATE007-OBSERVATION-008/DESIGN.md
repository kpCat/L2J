# Архитектурное решение координатора для008

## Что установлено по source

Base — docs-only checkpoint3fd4aa5; кандидат лежит внутри task007/candidate-paused/files.
Его snapshot не равен исполняемому source обычного HEAD. Сборка просто HEAD без overlay
запустила бы прежнюю программу, а не результат14 часов работы.

1. Candidate `PhantomVisibleAutoPlay.start()` уже проверяет exact materialized Player,
   active goal и pending store, конфигурирует native settings и регистрирует одну policy
   в AutoPlayTaskManager и AutoUseTaskManager. Здоровье Session проверяет обе регистрации.
2. AutoPlay выбирает ближайшую допустимую цель через World/GeoEngine, передаёт ATTACK
   в PlayerAI и подбирает доступные предметы через Player.doPickupItem. Для caster-классов
   AutoPlay не обязан давать melee — offensive skills исполняет AutoUse.useMagic.
3. PlayerAI исполняет и продолжает intentions. Его onIntentionActive переводит в IDLE:
   наличие одного PlayerAI само по себе не означает автономный выбор мобов.
4. Большое расширение007 относится прежде всего к native-work ownership, checkpoints,
   callbacks и их наблюдению. Нельзя честно описывать его как целиком заново написанный
   damage engine. Но широкое вмешательство в stock lifetime увеличило цену интеграции.
5. FakePlayer-ветка есть в AttackableAI.thinkActive: NPC выбирает цель/подходит к drop,
   использует getFakePlayerDrops и pickupMe(npc). Это **не** замена обычному Player inventory.
   Guard aggression проверяет karma и native LOS. Из этих веток можно брать идеи
   ограничений/приоритетов позднее, но не копировать NPC AI или втягивать PK в M1.

Адреса и прочитанные диапазоны — SOURCE_MAP. Source подтверждает существование пути,
но не доказывает сегодняшнюю непрерывную игру всего candidate007.

## Выбор сейчас

Сохраняем007 без отката и без новых patches. Наблюдаем его в изолированной копии мира.
Дешёвый proof использует уже существующий production bridge, а не новый test-driven
combat loop, вручную вызванный materialize/DecisionEngine/AutoPlay.start.

PASS пяти циклов означает: **нынешняя интеграция умеет пользоваться native-механикой**.
Он НЕ доказывает, что нетронутый upstream Mobius выполнит то же без всех hooks007,
и НЕ разрешает после PASS удалить ownership/owned-store guards.
Если materialization/admission вообще не дошли до AutoPlay, гипотеза AutoPlay остаётся
NOT_REACHED. Это не доказательство необходимости писать другой combat engine.

## Направление после наблюдения, не реализация008

Целевая граница: намерения/локальные priorities и bounded supervision — в phantoms;
законность действия, hit/cast, MP/cooldown, movement/pickup и rewards — native Mobius.
Имена Brain/CombatPolicy/MovementPolicy/RecoveryPolicy/Adapter пока описывают роли,
а не задание создать ещё пять подсистем. Сначала переиспользовать существующие классы.

Межзональные переходы без REAL допустимы в background. Физическое путешествие через
полкарты/GK не gate базового M1. Видимое локальное движение остаётся настоящим.
Нельзя скрывать под этим teleport/demat рядом с наблюдателем или потерю заработанного.
Сохранность и один владелец Player остаются обязательными, но каждый guard/test оценивается
по реальному нарушаемому контракту, а не потому, что когда-то попал в task007.

## Почему отдельная БД

У007 есть открытые persistence/historical failures. Выдавать ему рабочую PLAY DB для
наблюдения без принятого fix нельзя. Создаём disposable clone, а не очередной mutable
TEST fixture с восстановлением124 таблиц. Никаких crash injections/full WORLD в008.
Текущие READY/RETIRED/goal/anchor данные копируются, не переписываются ради красивой сцены.

Малый эксперимент:4 наблюдаемых профиля, максимум8 materialized в private runtime.
PopulationTarget/ActiveTarget/MaxScheduled, rates, hashes и поведения сохраняются.
Cap8 — явно заявленная операционная разница от PLAY, не доказательство масштаба1280.
Если cap мешает другим нужным actors, записать ограничение, не повышать его во время run.

## Три ловушки инструментов

- Build-LocalPlay.ps1 меняет population120/160/240, включает conversation/market/QoL:
  **не запускать его как готовый preset над копией текущей БД**.
- Stop-LocalPlay.ps1 после CloseMainWindow может выполнить Stop-Process -Force:
  **не применять его вслепую к исходному PLAY**. Нужен проверенный graceful stop.
- UI InGame, InputSentUnverified, server clientVisible и Pilot SUCCEEDED — разные факты.
  Ни один не подменяет реально просмотренный снимок/видео и native progress.
