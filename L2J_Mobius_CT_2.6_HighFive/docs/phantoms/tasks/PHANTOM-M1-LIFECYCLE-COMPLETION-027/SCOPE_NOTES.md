# Scope interpretation
Production allowlist contains13 exact existing paths, not permission to edit all13.
Preferred semantic core: System/Background/OrdinaryDeath/Materialization only.
Conditional file edits need same-slice RED + reason in FIX_LEDGER before commit, not a new user approval.
No new production helper required. One nested data type in existing class preferred.
If a small helper is necessary, only
`java/org/l2jmobius/gameserver/phantoms/player/PhantomLifecycleCompletion.java`
is preauthorized, maximum14 production paths total. Not a new scheduler/framework.

At most2 new suite files and4 existing test edits above. If lane-specific existing test is
not present in base, do not guess replacement: use the actually existing026 private lane
route read-first, and document test-only equivalent path (maximum1 such substitution).
No semantic scopes added under wording «fixture prerequisite».

No forced add of directories. Ignored authored task-file staging only by exact filename
when AGENTS/task permits, include reason; never stage secrets/dumps/private DB/JAR.
No root .gitattributes/git config modification. Reuse task-local byte-preservation rules.
