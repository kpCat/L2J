# GENERATION OWNERSHIP — Goal 010A

Use one topology read/write generation coordinator.

```text
update/event:
  read lease → exact query/generation → commit/deliver → release

reload:
  candidate outside locks
  → write lease
  → rebuild registered memberships
  → invalidate old fixed sources
  → atomic snapshot/membership swap
  → release
```

No old-generation membership or event signal crosses a successful swap. Never
call the scheduler while holding service or registry monitors.
