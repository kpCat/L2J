# TASK028 Implementation Plan
> Исполнитель: superpowers:executing-plans, inline, без субагентов.
**Goal:** две полные natural continuity-сцены и native away/background/return.
**Architecture:** существующие farm/lifecycle owners; control отделён от telemetry.
**Tech stack:** High Five, JDK25, Ant, Windows, MariaDB127.0.0.1:3308, Python3.
**Spec:** DESIGN.md + STATE_MACHINE.md + ACCEPTANCE_CHANGE.md.

## Global constraints / review focus
TASK/SOURCE_MAP обязательны. Особое внимание: duplicate native MOVE после timeout;
потеря initial epoch; heartbeat/TTL drift; ложный REST вместо idle; retirement при
незавершённом earned callback. Эти риски покрыты T03/T04, T06/B06, T01/T05, B04/B05,
L01/L03 соответственно. Production AI не меняется одновременно с runtime capture.

## A — read/proof & transport, 0–60мин
- [ ] Verify exact base/worktree и прочитать только перечисленные source/evidence.
- [ ] Построить две строки REQUEST028_DIAGNOSIS с точными old request IDs из ROOT_CAUSES.
      Отделить report fact от first exception UNKNOWN. Не общий audit всех журналов.
- [ ] T01 expiry genuine RED, исправить metadata без продления реального lifetime.
- [ ] T02/T03/T04/T05/T06: bounded snapshot, exact receipt, heartbeat, no replay.
- [ ] Один явный runner/collector028 вместо вложенной генерации legacy scripts.
      Reuse Control027 native start/stop/SQL proof implementation; wrapper-level
      adaptation only own paths/IDs, без reflection production mutation.
- [ ] Freeze/commit transport changes; clean build если менялся Java.

## B — первый server proof, не позднее90мин
- [ ] Собственная clone028b, START Synthetic с immediately armed heartbeat.
- [ ] Bounded native MOVE и обратный MOVE observer по короткому проверенному dry пути;
      exact accepted receipt→actual arrival. Это setup-control proof, не soft-returnPASS.
- [ ] 60–90с live telemetry без periodic mailbox census, unbroken same-session samples.
- [ ] First native failure — dumps+first edge; никакой guessed AI patch.
- [ ] Capture stopped/keepalive outcome; не тратить90мин на повторные invalid fixtures.

## C — continuity changes, примерно90–230мин
- [ ] До baseline полностью enrol natural cohort и short diagnosis разных состояний.
- [ ] На доказанные in-scope roots писать composed RED из B01–B10, менять владельца
      в существующих adapters. По одному проверяемому изменению, затем broader suite.
- [ ] Подготовить dedicated away/return budget и dry endpoints для двух выбранных actors.
- [ ] Устранить только доказанный retention/return failure; не открывать новый combat AI.
- [ ] Собственные semantic commits exact-path, clean builds; server runtime не hotpatch.
- [ ] Short probes после meaningful change, не гонять complete world suite после строки.

## D — regression & freeze, к270мин
- [ ] New focused scenarios, existing027 lifecycle11/11, 18 regression routes и
      LocalPlay tests. Required examples см. REGRESSION_POLICY.md.
- [ ] Semantic scope review: каждое изменение относится к конкретному RED/сценарию.
- [ ] Final committed SHA, clean JAR, source+assets+config+observer hashes.
      После freeze никаких Java/test/evaluator semantic edits между финальными сценами.

## E — final proof, 270–330мин
- [ ] Scene A360–420с: natural4..8, primaries заранее, whole-cohort state obligations.
- [ ] Scene B360–420с: независимый session/район/пара, тот же frozenSHA.
- [ ] Native soft-away/background/return отдельный same-session episode с budget<=480с.
- [ ] Whole-group owned snapshot→SQL и два real process sameDB restarts, provenance
      при законном background travel после release. Не повторять ранее passed raw files.
- [ ] Freeze matrix flags отдельно; incomplete scene остаётся incomplete даже7/8.

## F — final30мин
- [ ] Healthy stop027, pending/retained/real identities/ports проверены отдельно.
- [ ] RESULT/HANDOFF/MORNING, milestone gates, raw legacy scores и V2 раздельно.
- [ ] UTF-8+mojibake+escaped-Cyrillic, privacy+scope manifests, exact commit/push.
- [ ] Одно итоговое сообщение GREEN/BLOCKED/FAILED. Нет auto continuation.

Если prerequisite safety не решён — не тратить оставшиеся часы на unrelated polish.
Публиковать точный blocker и реально достигнутые проверки; не сужать критерий после
наблюдения ради зелёного названия.
