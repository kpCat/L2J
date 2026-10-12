# PATCH_GUIDE040 — ориентиры точечных изменений

Это не готовый patch к непроверенным файлам. Перед изменением Codex читает актуальный метод/все callers и фиксирует точное место в FIX_REVIEW040. Сигнатуры новых production API здесь намеренно не выдумываются: задача требует исправить существующие owners, а не построить заранее новый слой.

## 1. Engagement / movement / resource pause

Прочитать сначала `PhantomEngagementPlanner.java`, `PhantomEngagementController.java`, `PhantomVisibleAutoPlay.java`, `PhantomVisibleFarmTravel.java`. Найти выбор capability/target/action position, command gate, consumption navigation result и точку записи useful progress. Сверить реально исполняемое действие AutoUse с тем, для которого рассчитаны range/LOS/ресурсы.

При confirmed fault предпочесть изменение перехода/предиката в одном владельце и общий immutable result, а не специальные watchdog/retry chains. Последовательность native progress observation до resource early return039 сохраняется. В Session monitor не переносить тяжёлые path/SQL/I/O операции.

Тесты должны отличать «нельзя исполнить сейчас» от «невозможно/исчерпано/устарело». Проверить stale callback после goal/target/epoch смены, depleted resource после выбора действия, target death до исполнения, blocked route, negative instance. Busy action не отменять только потому, что новый planner tick захотел другой target.

## 2. Locality / soft release / background resume

Прочитать `PhantomHumanLocalityControl`, `PhantomMaterializationRetentionPolicy`, `PhantomMaterializationService`, `PhantomHistoricalBackgroundService`, `PhantomBackgroundService`, их actual owners и wiring в `PhantomSystem`.

На57-подобном случае записать одну непрерывную цепочку: soft-away event → retention decision → owned-store/drain → terminal native epoch → eligible background decision → successful typed producer commit. Найти первый отсутствующий переход. Не создавать общий таймер «каждые N секунд заново материализовать».

На119-подобном случае продолжить ту же profile lineage через законную смерть. READY/stored/native birth без useful work недостаточно. Не лечить SQL, не пропускать death state, не усиливать приманкой observer или population.

## 3. Save / restart / recovery

Reuse existing `PhantomBackgroundTransaction` optional witness. Данные брать на настоящей locked canonical/commit границе. Успешный event — только после успешного commit; failed/unknown commit остаётся failed/unknown. Идентичность связывается с actual owning producer/operation, не с временем ближайшего похожего лога.

Если ordinary recovery неправильно принимает pending record, локальный fix должен сохранять current CAS/version/receipt ownership и idempotence. Код, который просто вычищает pending или повторяет reward до красивых цифр, запрещён. Native earned scopes/read-only protection не обходить reflection-патчем production.

Для G5 таблица сравнения полей должна показывать actual before/after и lawful transitions. Допустимое отсутствие изменения — отдельный доказанный случай, не потеря записи. Не требовать, чтобы каждый restart создавал новый reward/goal только ради lineage.

## 4. Два прочитанных source-фрагмента BASE040

`java/org/l2jmobius/gameserver/localplay/LocalPlaySyntheticHumanService.java`, blob `e1b434c6eaf92aa8bb282022083603bf7e7ccd3d`:

```java
_deadlineNanos = System.nanoTime() + 525_000_000_000L;
_expiresUtcMillis = System.currentTimeMillis() + 525000;
_state = "RUNNING"; _reason = "SYNTHETIC_NATIVE_PLAYER_STARTED";
```

В том же owner `poll()` закрывает session при TTL/heartbeat/identity/ownership failure, а `start()` отклоняет replay и превышение started-cap5. Это источник срока жизни Synthetic-session, не готовый контракт долгоживущего пассивного наблюдателя. Исправление «поставить Long.MAX_VALUE/увеличить cap» не принимается.

`java/org/l2jmobius/gameserver/localplay/LocalPlayM1Observation.java`, blob `a53e821ca43161ab1d60b346e2e7de4e951e6d47`:

```java
if ((observed == null) || (source == null) || (source == PositionSource.TRANSITION) || (source == PositionSource.UNAVAILABLE) || (actor.instanceId() != observed.instanceId()))
{
    return rejected(RouteReason.TARGET_TRANSITION);
}
```

Класс отдельно проверяет object/epoch и использует forward/pathfind проверки. Не превращать TRANSITION в выдуманную актуальную позицию и не удалять instance check, чтобы observer «всегда доходил». Этот класс — pure decisions, не runtime owner всего автоматического прилипания.

## 5. Новые тесты — только там, где выявлен пробел

Разрешены `test/java/org/l2jmobius/tests/phantoms/PhantomM1Closeout040Suite.java`, `PhantomObserverHandoff040Suite.java` и package-local `test/java/org/l2jmobius/gameserver/phantoms/PhantomM1Closeout040Checks.java` при необходимости. Не создавать все три ради структуры; предпочесть расширение подходящей существующей test family с сохранением прежних asserts.

Контрактные кейсы по фактически изменённым веткам:
- `blockedDirectRouteUsesReachableApproach` — настоящий обход с valid forward segments;
- `staleActionCannotAffectReplacementSession` — old generation не меняет новую;
- `earnedProgressSurvivesResourcePause` — реальный earned event не теряется/не удваивается;
- `softAwayResumesBackgroundExactlyOnce` — native release и background producer имеют одного owner;
- `backgroundDeathReturnsToUsefulNativeWork` — legal recovery, а не fixture HP reset;
- `sameDbRestartDoesNotDuplicateEarnedReward` — сравнение committed lineage через boot;
- `realReconnectRejectsOldDisconnect` — старый connection не отвязывает новый focus;
- `passiveObserverDoesNotRequireSyntheticHeartbeat` — пассивная готовность не умирает с action-session.

Названия — предлагаемые новые тесты, не заявление о существующих методах. Fixtures для scheduler/connection transitions не объявлять actual REAL_LOGIN или natural cohort. Native/frozen-candidate evidence требуется отдельно по ACCEPTANCE.

## 6. Команды и сборка

Найти реальные командные строки successful039 runners в EVIDENCE_INDEX039/локальных receipts. Зафиксировать актуальные executable paths, classpath order и аргументы в CHECKPOINT040 один раз. Не изобретать отсутствующие Ant targets; известный production target — `ant -noinput jar`. `clean` не использовать без фактического target и безопасной области.

Jar-first означает, что тест проверяет deployed production classes из финального GameServer.jar, а не случайно подложенные новые classes из test build. Test helpers не попадают в production runtime. Не запускать конкурентные сборки в одну build-directory.

Защитные snapshots035–039 проверять по актуальным BASE040 blobs, а не бездумно по pre039 SHA из старого SOURCE_MAP. Новый SHA не означает регрессию сам по себе; изменения причинно разбираются и покрываются тестом.
