# Fix scope

At most TWO semantic production files from:
- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java
- java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java
- java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java
- java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java
- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java

Modify only files directly proven by the selected trace.

Forbidden:
Player.java, AutoPlayTaskManager, AutoUseTaskManager, ThreadPool, DB schema/direct repair,
new combat engine, fake targets, manual Phantom targeting, unconditional setAutoPlaying hacks.

If root cause is outside: BLOCKED_NATIVE_OR_OUTSIDE_SCOPE.
