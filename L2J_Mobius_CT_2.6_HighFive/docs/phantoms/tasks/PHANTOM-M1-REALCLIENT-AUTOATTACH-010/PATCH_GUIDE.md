# PATCH GUIDE

## LocalPlayPilotConfig.java

Добавить:

```java
public static boolean isAutoAttachEnabled()
public static boolean isAutoAttachCharacter(String name)
```

Auto-attach не должен автоматически включать `_enabled`.

## LocalPlayPilotService.java

Добавить:

```java
public synchronized boolean onRealClientEntered(Player player)
```

Не ослаблять `realClient(player)`: online, not headless, client non-null/non-detached,
client.player==player, IN_GAME, identity owner REAL_LOGIN.

Вынести общую финальную установку lease/session из `arm(...)` в private helper можно,
но manual arm должен остаться функционально прежним.

## EnterWorld.java

После `player.setEnteredWorld();` — один вызов auto-attach service.

## LocalPlayPilot.ini

Добавить:

```ini
EnableLocalPlayPilotAutoAttach = False
LocalPlayPilotAutoAttachCharacters =
```

## LocalPlayPilotSuite.java

Минимум:
A01 disabled -> no session
A02 allowlist miss -> no session
A03 allowlisted REAL_LOGIN IN_GAME -> ARMED_IDLE
A04 headless/detached/not-IN_GAME -> no attach
A05 another client lease -> no stealing
A06 same exact client -> idempotent success
A07 disconnect -> revoke/OFF
A08 manual arm still works when autoattach disabled and permit valid
