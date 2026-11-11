# PHANTOM-LEARNING-004 Implementation Plan

> **For agentic workers:** execute inline with superpowers:executing-plans, focused RED→GREEN tests and a final independent review.

**Goal:** Общее ядро V3 DAMAGE+HEAL с долговременным optimizer/replay, честными PvE-исходами и двумя native-сессиями.

**Architecture:** Существующие Combat/Party остаются владельцами действий. Immutable TacticalModelV3 сохраняет прежний codec; отдельный versioned optimizer checkpoint продолжает те же веса. TacticalStoreV3 транзакционно связывает опыт, frozen replay и checkpoint. Existing Director управляет сбором и offline оценкой.

**Tech Stack:** Java25, Ant, собственная MariaDB11.4, существующий Windows Training Director; без новых dependencies.

**Spec:** TASK.md, ARCHITECTURE.md, CHECKPOINT_AND_RESUME.md, ACCEPTANCE.md и остальные исходные документы пакета004.

## Global Constraints

- Base 06d42410fbfe1ca9b7f462b0816e0da1784630d6; независимый clone, только wip/phantom-learning-004.
- D:\Codex\repos\L2J-phantom-world READ-ONLY. MAIN/PLAY, старые SQL/Geo/releases и защищённые refs не изменяются.
- V1 и accepted003 bytes immutable. V3 feature/action float semantics и codec не переименовываются.
- Никаких новых skill formulas, owner/executor, Win32 launcher, provider или Semantic Pack.
- SQL/обучение только после native drain. OFF/SHADOW, explicit apply-test, никакого auto-ON.
- UNKNOWN не превращается в успех/ноль. AUDIT одноразовый и не участвует в fit.
- Пользователь явно поручил автономное выполнение; дополнительных промежуточных approval/manual TRAIN gates нет.

## Scope и решения

Единственный допустимый подход: расширить имеющиеся четыре шва, указанные WORKFLOW_FAST. Отдельная новая модель/executor либо переустройство launcher нарушили бы reuse и сохранность003. Четыре этапа ниже относятся к одной обучающей вертикали, не к независимым продуктам.

Bounded exception к8–10 файлам: Model/optimizer durability; DAMAGE adapter/receipt; replay/episode accounting; Trainer/Director integration. Перед каждым этапом фиксируется exact allowlist после чтения ближайшего кода. Исторические пакеты остаются read-only; новые исходные документы004 распакованы без изменения bytes.

## Review Focus

- Crash между weight update и SQL commit: восстановить только полностью committed state, не засчитать шаг дважды.
- Прежний accepted003 без Adam: явный MOMENTS_INITIALIZED; следующие004 checkpoint обязаны содержать реальные m/v/update count.
- Больше4096 исторических событий: bounded fit window, сохранение ledger, fresh audit без leakage.
- DAMAGE/HEAL имеют разные reward scales: family-aware calibration/quality; никакого общего ложного улучшения.
- Native ISSUE/timeout не является completion/kill; реальные terminal facts и UNKNOWN должны сохраняться отдельно.

## Последовательность

```mermaid
sequenceDiagram
    participant O as Combat/Party owner
    participant M as Shared immutable V3
    participant N as Native Skill lifecycle
    participant S as Owned durable store
    participant T as Offline trainer
    O->>M: lawful candidates + bounded observation
    M-->>O: ranking within current owner
    O->>N: final legality/lease recheck + cast
    N-->>S: exact effect receipt + episode settlement after drain
    S->>T: frozen stratified replay + fresh audit + committed optimizer
    T->>S: atomic immutable checkpoint + DRAFT + lineage
    Note over S,T: STOP/RESUME restores last committed boundary; explicit apply-test remains separate
```

### Task1: Incremental optimizer contract

Files: modify TacticalModelV3.java; create test PhantomLearning004ModelChecks.java. Keep original train/bytes/restore compatible.

- [ ] RED: bit-exact batch1→serialize→restore→batch2 versus continuous two batches, parent bytes immutable, corrupt/truncated/wrong-schema rejected.
- [ ] Implement authenticated versioned optimizer with model, Adam m/v, update count and safe batch cursor. Explicit fresh-moment migration from compatible003.
- [ ] Deterministic epoch order derived from persisted seed/epoch and exact dataset identity; bounded batch stepping outside gameplay.
- [ ] Verify focused004 checks and existing003 model contracts; command/exit/SHA in PROGRESS.

### Task2: Durable replay and training integration

Files: TacticalStoreV3.java, TacticalTrainerV3.java, phantom_tactical_v3.sql; focused004 store checks, minimal new checkpoint/replay contract file only if needed.

- [ ] RED: stale parent/partial checkpoint/no double commit; historical ledger beyond4096 produces bounded stratified fit window, no audit reuse.
- [ ] Persist optimizer lineage, frozen sample keys/schema/campaign boundary atomically with candidate; resume same FROZEN run after interrupted offline worker.
- [ ] Import exact accepted003 archive with validated hashes; first migration explicitly zero moments, subsequent attempts restore committed moments.
- [ ] Per-family/class quality and prior/constant metrics; reject unsupported/fake labels and retain losing DRAFT.

### Task3: Shared V3 DAMAGE and real episode contracts

Files: TacticalPolicyV3.java, existing Combat issueAction/backend/lease seams, native evidence adapter, TacticalStoreV3 and existing focused tests. Read exact APIs before adding signatures.

- [ ] RED: lawful DAMAGE candidates/recheck, stale/cooling/dead/MP rejection; shared checkpoint, HEAL emergency unchanged.
- [ ] Forward true LearningCastEvidence outcome into V3 envelope with rich observation; no V1-vector relabeling or second damage formula.
- [ ] Add versioned Solo/Party episode START/SETTLE summary with native target/participant death, duration, damage/heal/MP and explicit unsupported metrics.
- [ ] Run affected Combat/Party/learning DB-free contracts; V1 and003 binary SHA unchanged.

### Task4: Autonomous curriculum and native acceptance

Files: existing PhantomTrainingNative/Actors/Scenarios and learning003 native fixtures or focused004 fixture; Training.ps1/TrainingV3.ini/RUNBOOK only relevant seams.

- [ ] Generate lazy fresh contexts/actors, at least2 real dimensions/family; fresh collection segment identity after resume, no erased CUT fences.
- [ ] Preflight existing G0/own SQL/Geo once; collect native DAMAGE Solo/Party and HEAL97/105 using actual legal alternatives and exact receipts.
- [ ] Complete real Solo and Party encounters; matched stock/prior comparison, UNKNOWN where unsupported.
- [ ] SessionA fit/checkpoint/STOP; fresh-process sessionB RESUME/new events/fit/STOP. Controlled exact-owned worker interruption and cold recovery.
- [ ] Warm128 logical actor4/8/16 benchmark plus native timings/allocations/throughput/resources; report true sample counts.

### Task5: Delivery

Files: new report PHANTOM-LEARNING-004.md; PLAN/PROGRESS/RESULT/HANDOFF, concise evidence/fixtures, SCOPE_ALLOWLIST/SCOPE_GUARD and artifact archive under module docs.

- [ ] Final affected checks, F7/F8 where relevant, native STOP/ports free, protected hashes unchanged.
- [ ] Separate full mojibake and escaped Cyrillic checks; exact scope diff and no secrets/datadir/restricted binaries.
- [ ] Independent review, fix substantive findings with regression proof.
- [ ] Scoped commits and ordinary non-force push exclusively wip/phantom-learning-004; verify remote GitHub HEAD.
- [ ] FULL004_CORE only if G0–G9 passed; otherwise precise PARTIAL, no weakened gates.
