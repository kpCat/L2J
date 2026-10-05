# PLAN

## A — implementation (<=35 min)
1. Verify exact base/head.
2. Read five modify files + GameClient identity path.
3. Add A01–A08 targeted tests and get focused RED.
4. Implement config/service/hook.
5. Focused GREEN.
6. `ant -q jar`.
7. exact-path commit to experiment branch.
Scope expansion outside SOURCE_MAP modify rows => BLOCKED.

## B — private runtime (<=20 min)
1. Fresh clone PLAY -> `l2jmobiush5_localplay_observe010`, PLAY only SELECT/export.
2. Assemble private runtime with committed task010 JAR.
3. Apply only documented runtime overrides.
4. Before asking user to login, prove Java Phantom config is ENABLED and effective 1280/8/8/10000.
5. Start exact owned Login/Game.

## C — manual login + observation (<=25 min)
1. Tell user: `Сервер готов. Войди вручную персонажем TestAdmin и напиши "в игре".`
2. WAIT.
3. Then verify IN_GAME, REAL_LOGIN owner, Pilot ARMED_IDLE, exact TestAdmin object id,
   and absence of required arm command.
4. Observe 10–15 min; attempt 5 farm cycles.
5. No AI fixes.

## D — cleanup/report (<=10 min)
1. Stop active run with existing server tooling if needed.
2. User may close client; disconnect must revoke autoattach.
3. Verify OFF/no session.
4. Graceful exact runtime stop.
5. Ask before exact-PID force if graceful stop is unavailable.
6. RESULT/HANDOFF exact-path normal push.
