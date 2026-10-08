# Проверенные source excerpts
Base `9aeb4ac6c52970372f97637d26a5eb54c760ed1a`. URL-пути ниже для навигации; это точные выдержки, не готовые replacements.

## Synthetic: incompatible advertised/actual expiry
`java/org/l2jmobius/gameserver/localplay/LocalPlaySyntheticHumanService.java`
```java
_deadlineNanos = System.nanoTime() + 525_000_000_000L;
```
```java
state.setProperty("expiresUtcMillis", Long.toString(System.currentTimeMillis() + 600000));
```

## Native move acceptance
`java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java`
```java
actor.getAI().setIntention(Intention.MOVE_TO, new Location(x, y, z));
actor.onActionRequest();
return Outcome.of("ACCEPTED", "ARRIVAL_PENDING");
```

## Capture wrapper dependency
`docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/Observe026.ps1`
```powershell
function Census023{
    $visible026=@(VisibleCensus026)
    if(-not $script:fullStarted026){
```
Later the same function reads `full-native/full-cohort-latest.json`.

## Existing healthy session reentry
`java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java`
```java
if (autoPlay.running(profileId, goal)) { return PhantomStepResult.of(Type.SUCCESS, "background.visible.autoplay_continued"); }
final var arrival = travel.observeArrival(profileId, goal);
```

## Control not a second decision engine
`PhantomHistoricalBackgroundService.java`: `prepareVisibleDecision` first resolves
publication/checkpoint, compares exact stored goal to runtime, then `replacePlan`.
`PhantomMaterializationRetentionPolicy.java`: `Hold.hard()` excludes only RECENT_HUMAN;
ACTIVE_ACTION and NATIVE_VISIBLE are hard. Never remove these guards blindly.

Full source is in pinned repository. No source-file replicas or generated .patch that
can be applied without the RED/integration checks are included.
