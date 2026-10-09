# Plan inventory continuity030 Implementation Plan

> For executor: использовать executing-plans inline; субагенты запрещены.
**Goal:** корректная смена farm mutation footprint с естественным admission и полным
доказательством native/background/return без cumulative32 cap.
**Architecture:** existing authority/service/transaction + metadata projection preflight;
existing observer/exporter with active-only retention and append-only ledger.
**Tech:** JDK25, Ant, MariaDB3308, Windows/PowerShell, existing Python helper.
**Spec:** DESIGN.md, TASK.md, SCENARIOS.md.

## Review Focus
- Подлинная corruption не маскируется как projection drift (P06).
- RNG/XP/receipt не сдвигаются metadata-only refresh (P07/P09).
- Collector отказ не меняет native исполнение (E06).
- Terminal epoch освобождается только после export acknowledgement (E05/E08).
- Current geographic/schedule eligibility отдельно от старого prewarm target (R01).

## 0. Pin/read ≤15мин
- [ ] Exact remote/base/worktree; task scope и локальные AGENTS прочитать один раз.
- [ ] RESULT/HANDOFF/ROOT_CAUSES029 и SOURCE_MAP, latest saved witnesses.
- [ ] Записать CAUSE_MATRIX030.tsv: C6 vs finalC7, hypothesis vs fact, observer vs product.
- [ ] До новой producer-правки воспроизвести P02; P01 evidence лучше использовать прямо.

## 1. Product RED→GREEN ≤60мин (deadline full server75мин)
- [ ] P02/P04/P06 RED / controls на composed native fixture, не mock model-success.
- [ ] Implement A/B DESIGN минимально; если smaller existing producer fix proven — его.
- [ ] P07/P08/P09 + targeted native return. Run/record genuine RED and GREEN exit codes.
- [ ] Одновременно сохранить footprint/candidate evidence на immutable clone, без нового
      runtime для каждого поля. Неразобранные причины не переименовывать в «это геодата».
- [ ] Source commit и clean build; early full-server75min probe. Без product fix можно
      выполнить честный baseline probe; deadline не превращать в ложный PASS.

## 2. Prospective collector repair ≤100мин total
- [ ] E01–E10 на existing collector, включить >32distinct/128epochs BEFORE product rerun.
- [ ] Один adapter030 с active-registry/stream; максимум один exporter, one audit helper.
- [ ] Measure active references/highwater; first fault sticky, native parity with hooks OFF/ON.
- [ ] Минимум engine-native lifecycle E test, не только приложенный offline helper.

## 3. Admission & vertical fix ≤150мин total
- [ ] Fresh own clone из immutable export. Не переиспользовать старую brokenclone как
      единственный доказательный positive endpoint.
- [ ] Current region/ranked eligible candidate cluster before outcomes, fixed cohort.
- [ ] Exact first guard and lifecycle for absent original029eight. Metadata conflict
      fix входит в scope; бездоказательно открывать admission — запрещено.
- [ ] Связанные поправки ordinary projection dispatch внутри SOURCE_MAP разрешены.
- [ ] No new semantic work after150min. Freeze source AND observer+evaluator manifests.

## 4. Acceptance & closure ≤240мин total
- [ ] Current matrix92/92 +11/11, new focused tests, clean committed build.
- [ ] Two full scenes and separate bounded away/return within actual Synthetic TTL.
- [ ] Whole receipt union from first birth through final stop; no ring/disk/export gaps.
- [ ] SameDB two restarts, inherited crash tests only on explicit same-SHA changed-dep proof;
      actual R07 under prerequisites if time. NOT_RUN preserved otherwise.
- [ ] Final diff/scope/evaluator/hash/encoding audit, one report, exact-path publish.
- [ ] Safe stop even after FAILED/BLOCKED. No repeated 'blocker confirmed' turns or questions.

No hard cap on in-scope RED/fix count. If architecture hypothesis fails repeatedly,
record counterevidence and STOP rather than extending native/world scope.
