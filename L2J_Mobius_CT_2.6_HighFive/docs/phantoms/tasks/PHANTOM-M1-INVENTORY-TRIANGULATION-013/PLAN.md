# PLAN

## Phase A — preserve 012R facts and isolate worktree (<=10m)

1. Verify remote experiment HEAD is exact required base 88b7dd...
2. Main checkout 012R local diffs are FOREIGN READ-ONLY.
3. Read RESULT.md/HANDOFF_RESULT.md and copy the exact five inventory differences
   into task013 evidence.
4. Create a new isolated worktree from required base.
5. No staging/commit in main checkout.

## Phase B — A/B/C/D native triangulation (<=30m)

1. Reproduce exact profile68 READY path.
2. Capture A/B immediately before native load.
3. Perform real Player.load through existing materialization/lifecycle.
4. Capture C/D at the first afterPlayerLoad boundary before any cleanup/store.
5. Produce `INVENTORY_TRIANGULATION.tsv`.
6. Every one of the five prior diffs must be classified.
7. Compare counts by object and by itemId.

No production edit before classification.

## Phase C — minimal fix (<=35m)

Based on DESIGN class:
- STATE_STALE -> fix only background state writer/refresh boundary.
- CANONICAL_STALE -> fix exact BackgroundTransaction item writer bug.
- CAPTURE_PROJECTION_DRIFT -> fix only Authority projection/filter.
- NATIVE_LOAD_NORMALIZATION -> only lossless/proven adaptation in Phantom layer.
- TRUE_GAMEPLAY_CONFLICT -> STOP/BLOCKED, no fix.

Max production changed files: 2.
No Player/Inventory/Item core modifications.

Then rerun exact profile68 probe.
Required: five diffs = 0 and autoGet still exact.

Run:
- focused historical native-context lane;
- task011 ecology 18/18;
- cheap handoff regression 6/6;
- `ant -q jar`.

## Phase D — make TestAdmin Master (<=5m)

Explicit user authorization exists for this exact local character.

On PLAY only:
```sql
SELECT charId,char_name,account_name,accesslevel
FROM characters
WHERE charId=268492939 AND char_name='TestAdmin';
```

Require exactly one row.

If accesslevel != 100:
```sql
UPDATE characters
SET accesslevel=100
WHERE charId=268492939
  AND char_name='TestAdmin'
  AND accesslevel=<observed_before>;
```

Require affected rows=1.
Verify after SELECT.
Do not touch `kpCat` or any other character.

Record only non-secret fields in evidence.

## Phase E — fresh observe013 runtime (<=25m preparation)

Fresh clone:
`l2jmobiush5_localplay_observe013`

Use committed task013 jar.
Effective Phantom config:
1280 / active8 / maxMaterialized8 / scheduled10000.

Pilot:
enabled, autoAttach, allowlist TestAdmin, Synthetic=false.

Private runtime General.ini overrides:
GMStartupBuilderHide=False
GMStartupInvisible=False
GMStartupInvulnerable=False
GMStartupSilence=False

Before asking user to login prove:
- Phantom System ENABLED;
- clone TestAdmin accesslevel=100;
- Login/Game exact owned;
- no old conflicting listeners.

Then ask:
`Сервер готов. Войди вручную TestAdmin и напиши "в игре".`

## Phase F — visible gate (<=120s + max3m)

After user says "в игре":
- IN_GAME;
- REAL_LOGIN;
- Pilot ARMED_IDLE, no arm code;
- TestAdmin accesslevel=100/isGM true if observable.

Wait max120 seconds for natural Phantom.

PASS requires one exact Phantom:
materialized=true
worldPresent=true
objectId>0
epoch>0.

If PASS:
max3 minutes passive observation of:
Decision selected?
AutoPlay attached/running?
moving/target/casting/attacking flags?
Do not fix gameplay.

If no PASS:
record earliest exact boundary and STOP.

## Phase G — safe REAL logout / cleanup (<=10m)

If TestAdmin still IN_GAME:
ask user:
`Выйди до окна выбора персонажа и напиши "вышел".`

WAIT.

Verify clone DB online=0 and record level/exp/sp/x/y/z.
Only then graceful stop owned Game/Login.

If logout not confirmed, leave servers running.
No force while REAL player online.

Publish exact-path source/test/docs commits and normal push experiment branch.
