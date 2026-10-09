# State machine029 — не новый общий контроллер

```
NATIVE(owner, epoch, running task)
  -- close new roots, finish accepted callbacks --> QUIESCENT
  -- existing PREPARE/native store/FINALIZE --> NATIVE_RELEASED
  -- exact no-native exclusive admission --> BACKGROUND_READY(context+policy)
       | position valid + ordinary policy
       +-- FARM --> ATOMIC(state+vitality+reward+receipt) --> BACKGROUND_READY
       | position invalid but lawful dry local route
       +-- TRAVEL --> ATOMIC(position/time only) --> BACKGROUND_READY
       | unsupported/unknown facts
       +-- typed NOT_ADMITTED (no reward, no fake version progress)
  -- new human demand / exact claim acquisition --> native Player load
  -- current captured state and refreshed goal --> NATIVE(new epoch)
```

Appearing demand cannot create concurrent background and native writers. Existing
state/identity leases plus DB CAS decide the winner. Save eligibility and simulation
eligibility are distinct; position/bonus uncertainty cannot discard native earned data.
Temporary checkpoint uses the same epoch and returns to NATIVE; it isn't a terminal
release. Terminal ledger row closes only when the actual lifetime is DETACHED.

A failed ordinary policy must have exact reason and supported next option, not hidden
loop. This task cannot label endless NOT_ADMITTED as valid background life.
