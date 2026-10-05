# DESIGN

## Семантика

`REAL_LOGIN` — свойство настоящего GameClient и не зависит от Pilot allowlist.
Pilot allowlist — только право Codex использовать конкретного REAL Player как оператора.

## Config

Shipped defaults fail-closed:

```ini
EnableLocalPlayPilot = False
EnableLocalPlayPilotAutoAttach = False
LocalPlayPilotAutoAttachCharacters =
EnableLocalPlaySyntheticHuman = False
```

`LocalPlayPilotAutoAttachCharacters` — comma-separated character names:
trim; пустые фрагменты игнорировать; максимум 32 имени; длина 1..32;
сравнение case-insensitive через Locale.ROOT. Некорректный список выключает
только auto-attach, не обычный Pilot/manual arm.

## Hook

В `EnterWorld.runImpl()` сразу после:

```java
// EnterWorld has finished.
player.setEnteredWorld();
```

вызвать:

```java
LocalPlayPilotService.getInstance().onRealClientEntered(player);
```

Ошибка Pilot не должна ломать login.

## Service

Добавить:

```java
public synchronized boolean onRealClientEntered(Player player)
```

Требования:
- Pilot enabled;
- auto-attach enabled;
- character в allowlist;
- poller активен;
- mailboxSafe();
- validManifest();
- ownedProcess();
- существующий `realClient(player)` == true;
- не отбирать lease у другого Player.

Повторный вызов для уже attached exact client/player — idempotent true.

`arm.properties` не создавать и не читать.
Переиспользовать `LocalPlayPilotLease`: допустимо создать внутренний случайный nonce
и немедленно вызвать существующий `lease.arm(...)`. Nonce не логировать.
На успехе заполнить те же `_client/_player/_lease/_actions/_expiresUtcMillis`
и `writeSession()`. Existing Get-LocalPlayPilot должен показывать `ARMED_IDLE`.

Disconnect уже вызывает revoke и остаётся штатным автоматическим OFF.
Manual `.playtest arm` и `.playtest off` сохраняются как fallback.

## Runtime observation

Создать свежую clone DB `l2jmobiush5_localplay_observe010` из PLAY read-only export.
Private runtime:

```ini
EnablePhantomSystem = True
PhantomPopulationTarget = 1280
PhantomPopulationActiveTarget = 8
MaxMaterializedPhantoms = 8
MaxScheduledPhantomProfiles = 10000
EnableLocalPlayPilot = True
EnableLocalPlayPilotAutoAttach = True
LocalPlayPilotAutoAttachCharacters = TestAdmin
EnableLocalPlaySyntheticHuman = False
```

Не менять rates/goals/NPC/geodata/scheduler budgets.
8/8 — только контролируемый observation runtime; durable target остаётся 1280.

Если для auto-attach понадобится менять Player, identity registry, ThreadPool,
AutoPlay, AutoUse, background/history, EventDispatcher или movement — BLOCKED.
