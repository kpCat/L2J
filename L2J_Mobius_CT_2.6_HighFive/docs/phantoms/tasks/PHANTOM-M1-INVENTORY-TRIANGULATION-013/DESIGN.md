# DESIGN

## 1. Source-of-truth triangulation

Нельзя исправлять inventory по одному сравнению
`loaded Player != background`.

Для profile68 в одном exact claim/epoch снять четыре представления:

A. BACKGROUND
`PhantomBackgroundState.inventory.objects`

B. DB_PRE_LOAD
строки native `items` для characterObjectId до `Player.load`

C. PLAYER_LOADED
релевантные Item из `player.getInventory()` сразу после load/restore,
до historical refresh/store

D. DB_POST_LOAD
те же native `items` после Player.load, но до cleanup/store

Для каждого объекта фиксировать:
objectId, itemId, count, ItemLocation; плюс суммарный count по itemId.

Никаких паролей/полных payload.

## 2. Классы результата

### STATE_STALE
B == C == D, A отличается.
Background component stale относительно canonical/native.

Разрешён fix только через существующий BackgroundTransaction/component update path.
Не переписывать native items.

### CANONICAL_STALE
A == C, B отличается; либо A отражает доказанный committed background result,
а native DB не получил уже подтверждённую background item mutation.

Разрешён fix в `PhantomBackgroundTransaction` только если найдена точная пропущенная
atomic item mutation/verification boundary. Никакого materialization-time "copy A to DB".

### CAPTURE_PROJECTION_DRIFT
B == D и runtime Player semantically совпадает с B/D по itemId/count/location,
но `captureOwnedNative/tracking()` строит другую projection.

Разрешён fix в `L2jPhantomBackgroundAuthority` projection/filtering.
Нельзя менять реальный inventory.

### NATIVE_LOAD_NORMALIZATION
A == B до load, а C == D после load и Player.load детерминированно изменил canonical rows.
Разрешён fix только если transformation lossless по gameplay facts:
- ни один itemId/count не создан/потерян;
- equip/inventory location соответствует stock native semantics;
- object identity transformation объяснена stock load path;
- exact before/after regression существует.

Если transformation не доказана lossless -> BLOCKED_ARCHITECTURE.

### TRUE_GAMEPLAY_CONFLICT
Counts/items реально расходятся между background и native canonical.
Ничего автоматически не нормализовать.
STOP `BLOCKED_TRUE_INVENTORY_CONFLICT`.

## 3. Основной архитектурный принцип

Background simulation уже использует `PhantomBackgroundTransaction`, которая должна
атомарно мутировать canonical character/item state вместе с background state.

Поэтому materialization не должна "угадывать победителя".
Она должна встретить уже согласованные state + native DB.

Если mismatch возник раньше — чинить writer/boundary раньше, а не ослаблять
`afterPlayerLoad`.

## 4. TestAdmin = реальный Master admin

Это отдельная явно разрешённая локальная операция пользователя.

В PLAY `l2jmobiush5_localplay3`:
- сначала SELECT exact charId/name/account/accesslevel;
- разрешён только `charId=268492939 AND char_name='TestAdmin'`;
- `kpCat` и другие characters не менять;
- сохранить before/after evidence;
- установить `accesslevel=100`, если ещё не 100.

High Five `AccessLevels.xml`:
level100 = Master, isGM=true, giveDamage=true, takeAggro=true, gainExp=true.

Новый observe013 clone создаётся ПОСЛЕ этого, чтобы TestAdmin наследовал accesslevel100.

В private observe013 runtime ONLY выставить:
`GMStartupBuilderHide = False`
`GMStartupInvisible = False`
`GMStartupInvulnerable = False`
`GMStartupSilence = False`

Shipped source `General.ini` не менять.
Это нужно, чтобы TestAdmin оставался нормальным видимым боевым наблюдателем,
но имел admin commands.

Human locality GM не исключает: production supplier берёт любой online Player
без headless outbound session.

## 5. REAL Player save/shutdown rule

Никогда не останавливать GameServer, пока TestAdmin IN_GAME.

Перед runtime shutdown:
1. если TestAdmin online — попросить пользователя:
   `Выйди до окна выбора персонажа и напиши "вышел".`
2. WAIT.
3. подтвердить `characters.online=0`;
4. зафиксировать post-logout `level/exp/sp/x/y/z` из clone DB;
5. только затем graceful stop exact owned Game/Login.

Если logout/store не подтверждён — оставить сервер работающим и STOP/BLOCKED.
Никакого force с online REAL Player.
