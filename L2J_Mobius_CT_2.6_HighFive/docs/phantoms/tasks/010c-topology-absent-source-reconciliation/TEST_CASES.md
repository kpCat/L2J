# TEST CASES — Goal 010C

Use a real PhantomScheduler adapter for:

- fresh profile with no source activity;
- one active source plus two never-submitted sources;
- re-registration and newer accepted sequence;
- valid reload before any event;
- scheduler profile absent and all-three NOT_REGISTERED release.

Retain controlled fake tests proving POSSIBLY_ACTIVE/OWNERSHIP_UNCERTAIN STALE
still fail.
