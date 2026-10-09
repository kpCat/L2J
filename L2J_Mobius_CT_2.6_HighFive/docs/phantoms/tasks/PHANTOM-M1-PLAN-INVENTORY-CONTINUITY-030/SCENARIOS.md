# SCENARIOS030 — собственный RED и вертикаль

## P — plan/inventory
P01 retained witness118: извлечь payloads old state/goal/catchup/source hashes, доказать
    actual item118 provenance либо отметить UNKNOWN. Никакого SQL repair old clone.
P02 genuine composed producer: goalA с drop-setA → production смена наB, B содержит X
    внеA → controlled deterministic roll X. До фикса actual NON_MUTABLE_ID или эквивалент
    gateRED; после legitimate XP/SP/loot и дальнейший цикл. Не только fake Command.
P03 редкий lawful X присутствует в footprint до roll, включая roll без X.
P04 arbitrary Y не принадлежит B: отказ до предметных изменений. No self-authorization.
P05 native-owned receipt pending / active World / stale class / foreign lease -> no projection.
P06 реальный inventory hash/count/location drift: остаётся failure, не rehash-and-accept.
P07 projection-only commit сохраняет ВСЕ characters/items/skills/vitality/XYZ/clock/cursor
    byte-for-byte; меняются только projection-state binding и PNC state version/hash.
P08 repeat no-op не повышает version; CAS goal/catchup/generation races -> no overwrite.
P09 lost reply before/after metadata commit resolved exactly; zero duplicated rewards.
P10 full batch с новым projection и native rematerialization/load/store сохраняет items.
P11 same lifecycle с melee/magic ordinary plan без profile/NPC hardcode. Spoil policy
    проверять только при actual existing skill evidence, не добавлять M7 обучение.
P12 initial/historical/normal farm используют один preflight; unchanged native store
    attestation сохраняет старую проекцию до законного переключения.

## E — evidence (использовать prospective native events, не позднюю SQL)
E01 >32 distinct sequential births, >128 cumulative epochs, active<=8, все events на диске.
E02 смена telemetry cohort не теряет ни одного ранее enrolled scope и background edge.
E03 same profile new epoch; REGISTER,parent terminal и native receipt join проверяется
    по exact lifetime, не заменой enrolledInitialEpoch.
E04 two JVM runs с совпавшими numeric epoch namespace не смешиваются.
E05 temporary SEALED не terminal; terminal requires permanent DETACHED zero accepted work.
E06 full ring / capacity of ACTIVE records / FS error -> proof invalid, no effect on native
    gameplay/store outcome; first error sticky; controlled stop outside hook.
E07 conflicting duplicate -> audit FAIL; exact duplicate idempotent.
E08 terminal export ack frees strong Player reference; disk history remains complete.
E09 no FS/SQL/blocking in passive hooks; >128 epochs test measures active highwater/bytes.
E10 completed future receipt does not repair earlier missing receipt.

## R — actual server
R01 short probe <=75min on committed build: exact admission census plus observer age/XYZ.
R02 full scalar flow: farmA→new farm planB→legitimate mutation→background commit→return
    to native with >=5cycles primary, not old early kill samples.
R03 final two scenes360–420s, unchanged028 V2 + separate legacy results, fixed4..8 actors,
    two different preselected primaries, last120s progress, no missing/cohort replacement.
R04 away uses native observer movement, no fake login; absence+at least one productive
    background FARM each primary (not death_idle/travel-only), return all original cohort.
    Each member must pass unchanged whole-return accounting; primaries5cycles,new epochs.
R05 all lifetimes unioninclbaseline owners → latest exact native terminal receipt →
    chain of later background/projection commits → canonical SQL. Unknown gaps FAIL.
R06 two actual sameDB restart; preserve both initial and each stop result. No old seal
    compared directly to late SQL ignoring lawful intervening commits.
R07 actual crash AFTER_NATIVE/AFTER_FINALIZE on separate clones, current SHA, only after
    healthy coverage gate. Not run if prerequisite false; M1 staysOPEN.

Required regressions: existing18routes92cases and lifecycle027 acceptance11/11;
PNC1/PNC2, pending receipts,029native oracle/handoff; focused new P/E tests. Numbers are
base counts, append cases instead of replacing assertions. Не переоткрывать U1/U2 только
потому, что label UNKNOWN: новые regressions/probes называются NEW_PROOF, не old root fix.
