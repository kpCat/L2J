# Durable pre-CUT catalog protocol

1. Own SQL training-attempt count is the required catalog generation. Each new
   persisted model attempt requires genuinely new ordinary Player/profile IDs.
   Old actors, rewards and SQL history remain unchanged and inactive.
2. Preparation uses `catalogs/<generation>-<uuid>/journal.properties`: exact owner,
   generation, selector hash, phase PREPARING/INCOMPLETE/SEALED. Actor/scene manifests
   are immutable snapshots inside this directory; payload seal covers both manifests
   and actual selector facts. Only a fully SEALED snapshot is eligible.
3. On restart, reuse an exact sealed generation and selector snapshot. A prior own
   PREPARING snapshot is marked INCOMPLETE, preserving its journal and any SQL actors.
   New allocation starts at actual MAX(profile_id)+1, never at guessed old IDs.
4. An owned account uses idempotent INSERT IGNORE. Appending requires exact own
   database identity and verification of all prior sealed snapshots. No DROP/reset,
   SQL reward rewriting, old actor initialization, or historical manifest overwrite.
5. Publish `active-catalog.txt` only after payload/seal/journal verification, using
   atomic replacement. Native opens exactly that sealed snapshot and checks current
   SQL generation. Crash after seal but before publication can safely publish it later.
6. Bounds: maxTrainingCohorts default32, maxStoredActors default4096 and bounded
   profile placeholder allocation. Capacity exhaustion pauses with an explicit reason.
7. Config seed/modes/budgets can reload at the next stopped boundary. Selector changes
   create a new owned pre-CUT snapshot with new actors, preserving existing snapshots.
   Ordinal-only metadata is never considered new audit evidence.

Scene identity remains the stable physical source/class/level/loadout/position/skills/
helper-count hash. Its TRAIN/TUNING/AUDIT partition is permanent across generations.
Fresh actual Player/profile IDs create the new held-out cohort; generation/UUID or
ordinal-only seed labels cannot turn a reused actor/scene into fresh audit evidence.
Catalog seed changes the selection of actual variants within each fixed partition.

The payload seal also binds the catalog directory identity and `prepared-actors.tsv`
(all successfully created actors, including discarded scene candidates). The owned
allocation journal is written before any Player/profile creation. A crash after Player
creation but before profile linking cannot collide with the next cohort's character
names: names use bounded cohort-attempt and local creation identities, while 001 names
remain unchanged. Profile sequence is re-read after a partially failed candidate.

An unpublished initial journal may be truncated before its owner line. Recovery is
restricted to a validated own catalog directory containing only initial pending journal
files and its quarantine; any manifest/payload or complete foreign owner still fails
closed. Raw pending files remain untouched. Exact copies and SHA-256 sidecars are
verified under `quarantine/<pending-name>.raw[.sha256]` before publishing an own
INCOMPLETE journal. Interrupted quarantine copies are retained; none becomes SEALED
or supplies allocation authority. The third focused catalog test covers ownerless
`Generation=0`, byte/hash preservation, and refusal of explicit foreign ownership.
