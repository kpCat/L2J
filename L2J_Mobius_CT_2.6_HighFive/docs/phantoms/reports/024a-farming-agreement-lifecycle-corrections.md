# Goal 024A — farming agreement lifecycle corrections

## Status

`SUCCESS — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required parent: `2603776c6996007b147f93e4c7e79f145ceb8a89`
Branch: `feature/phantom-world`
Required commit subject: `fix(phantoms): harden farming agreement lifecycle`
Seed: `24002402`
Goal 024: `CHANGES_REQUIRED` до независимого review Goal024A
Goal 025+: `NOT_STARTED`

## Summary

Закрыты только R024A-01/02/03 без замены принятого Goal024 kernel. Pre-final arbitration
truth теперь инвалидирует draft при material drift, а bilateral FINAL сохраняет remaining/progress
как historical evidence и связывается с live Goal021 по pair, goal/revision, source, ResourceKey,
stable authority, exactPair, causal TTL — без equality current remaining.

Новая negotiation требует fresh Goal010 perceptibility. Exact начатая pair сохраняет bounded
`CausalPerceptionReceipt`; после OFFER/FINAL one-hop visibility может исчезнуть до TTL.
Loser-first restart exact-load/revalidates persisted counterpart по ID без profile/listProfiles/World
scan и без scheduler pulse holder.

Manual boolean outcome authority удалён. Reconciliation наблюдает Goal021 lifecycle: реальный MOVE
проходит существующий SWITCH/`switchSource` один раз, затем old claim освобождается и exact bilateral
agreement становится FULFILLED. WAIT/SHARE переживают progress; TTL даёт EXPIRED; authority drift —
STALE. BROKEN без objective exact breach evidence не создаётся. Goal018 delivery имеет durable
per-owner retry truth и deterministic event IDs.

## Read-first evidence

Полностью прочитаны обязательные `AGENTS.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`,
`docs/PHANTOM_BOTS_ROADMAP.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`,
`docs/phantoms/TASK_PACKAGE_STANDARD.md`, `docs/phantoms/CODEX_REPORT_TEMPLATE.md`,
Goal024 report/review/contract и весь task package
`docs/phantoms/tasks/024a-farming-agreement-lifecycle-corrections/`.

Прочитаны целевые production/test call paths: farming model/codec/store/service/decision/port,
Goal021 acquisition state/store/service/decision, Goal010 topology snapshots/perceptibility,
Goal017 Party evidence, Goal018 social store/service/events, Goal020 farming facts/query,
profile component CAS, launcher и Goal024 Ant/verifiers.

Локальные аналоги: Goal024 lower-id → higher-id bilateral CAS и restart mirror; Goal010
generation/hash/profile-sequence perceptibility evidence; Goal021 `DirectiveKind.SWITCH` и
`switchSource`; Goal018 deterministic idempotent social receipts; Goal021 private legacy encoder.
Переиспользованы эти существующие паттерны. README, code-map и отдельные pattern-файлы для целевой
farming подсистемы не найдены.

## Changed files

Production:

- farming `PhantomFarmingModel.java`, `PhantomFarmingStateCodec.java`,
  `PhantomFarmingStore.java`, `PhantomFarmingService.java`;
- acquisition `PhantomAcquisitionService.java`.

Tests/build:

- `PhantomFarmingSuite.java`, `PhantomAcquisitionSuite.java`, `PhantomTestLauncher.java`;
- `PhantomCombatServerIntegrationSuite.java`: bounded historical manor fixture correction;
- `build.xml`, `tools/phantoms/verify-task-024a.ps1`.

Process/docs:

- master plan, roadmap, farming architecture contract;
- reviews 024/024A и этот report;
- нормативный task package `docs/phantoms/tasks/024a-farming-agreement-lifecycle-corrections/`,
  предоставленный untracked на baseline и включаемый без переписывания.

Это justified exact call-path expansion без искусственного числового лимита файлов. Исторический
manor fixture входит в exact failing acquisition call path; production Combat/Manor не менялись.
Другие artifact families и хроники не затронуты.

## Architecture decisions

- Schema `farming.conflict` повышена до v2: causal receipt, stable authorities и social mask.
  v1 читается legacy-untrusted; fresh exact current pair может безопасно мигрировать.
- `ConflictObservation` — узкий read-only Goal021 seam. Farming не получает acquisition store
  и не меняет selected Source.
- Runtime claims остаются bounded cache; persisted exact counterpart ID является restart truth.
- Live gate и existing-final paths сравнивают stable authority напрямую, поэтому занятый pair claim
  или fail-neutral reconciliation не может обойти authority drift check.
- Terminal transition пишется bilateral lower→higher; one-sided terminal не authoritative.
- Social mask не входит в bilateral identity, поэтому idempotent retry не ломает exactPair.
- FULFILLED подтверждается Goal021 completion/release/source move. Без достоверного breach evidence
  неоднозначный исход классифицируется EXPIRED/STALE, а BROKEN не выдумывается.

## Database changes

SQL/migrations отсутствуют. Используется существующий versioned profile component CAS. Production DB
`l2jmobiush5` не изменяется; DB regressions допускаются только через guarded
`l2jmobiush5_phantom_test`.

## Configuration changes

Конфиги и policy XML не менялись. Existing Goal024 policy bounds и feature behavior сохранены.

## Commands executed

```text
git status --short --branch
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse --abbrev-ref --symbolic-full-name @{upstream}
git show -s --format=%H%n%P%n%s HEAD
git diff --check

.phantom-local/apache-ant-1.10.17/bin/ant.bat -q compile
.phantom-local/apache-ant-1.10.17/bin/ant.bat -q phantom-farming-resource-policy-test
.phantom-local/apache-ant-1.10.17/bin/ant.bat -q phantom-farming-goal024a-lifecycle-test
.phantom-local/apache-ant-1.10.17/bin/ant.bat -q phantom-farming-goal024a-restart-test
.phantom-local/apache-ant-1.10.17/bin/ant.bat -q phantom-farming-goal024a-acquisition-integration-test
.phantom-local/apache-ant-1.10.17/bin/ant.bat -q phantom-farming-goal024a-focused-test
.phantom-local/apache-ant-1.10.17/bin/ant.bat -q phantom-farming-goal024-test
```

Два диагностических запуска исходного Goal024 aggregate были прерваны внешним timeout оболочки
на 64 и 304 секундах без test failure/terminal evidence и не засчитаны.

Final sequence без промежуточных изменений:

```text
ant -q phantom-farming-goal024a-focused-test
ant -q phantom-farming-goal024-test
ant -q phantom-farming-goal024a-affected-test
ant -q phantom-static-verify-023
ant -q phantom-static-verify-023a
ant -q phantom-static-verify-023b
ant -q phantom-static-verify-023c
ant -q phantom-static-verify-024
powershell -File tools/phantoms/verify-task-024a.ps1 -WorkingTree
ant -q phantom-farming-goal024a-test
ant -q verify
ant -q jar
```

Delivery commands:

```text
git diff --check
git status --short
git diff --name-only
git diff --stat
git diff --numstat
git diff -- exact-goal024a-paths
git add -- exact-goal024a-paths
git commit -m 'fix(phantoms): harden farming agreement lifecycle'
git push origin feature/phantom-world
powershell 5.1 verify-task-024a.ps1
PowerShell 7 verify-task-024a.ps1
```

Verifier выполняет bounded Git inspection exact parent/subject/branch/scope/remote и diff check.
Reset/rebase/amend/merge/force не используются.

## Test results

Pre-freeze:

- compile: PASS;
- Goal024A lifecycle/restart/acquisition focused aggregate: PASS, 36.9 s;
- SHARE-after-progress, WAIT-after-progress/completion, OFFER/RESPONSE drift и perceptibility
  loss after OFFER/final: PASS;
- loser-first WAIT/MOVE restart before holder pulse: PASS;
- real Goal021 SWITCH/switchSource + automatic FULFILLED + fresh new claim: PASS;
- expiry, authority drift, durable social retry, terminal bilateral fault matrix: PASS;
- v1 safe migration и legacy-untrusted visibility-unknown fail closed: PASS;
- original Goal024 focused regressions after compatibility correction: PASS.
- bounded historical manor fixture after exact item-skill correction: PASS 10/10 targeted runs,
  45–55 секунд каждый; оба cases прошли 20/20.
- final aggregate до refreeze выявил повторный historical `acquisition-active-spoil.03` handoff
  failure на seed `21002101`; targeted diagnostic сразу после него прошёл 3/3. Ранее тот же case
  останавливался с HP=1 и session `ACTIVE/FIGHTING`. Stable no-action probe оказался неверным:
  canonical Spoil имеет `nextActionAttack=true`. Перед независимой Combat-фазой fixture теперь
  использует существующий test-owned `resetActor(false) → relocate → invul → ensureWeapon` pattern;
  recovery assertions выполняются до reset и не ослаблены.
- active-spoil fixture после phase-reset correction: PASS 10/10 targeted runs, 41–50 секунд
  каждый; три cases прошли 30/30.
- один original aggregate после этого остановился в quiet mode на build target
  `acquisition-quest-active` (`build.xml:1559`), поэтому case-level assertion не был доступен.
  Без изменений exact target прошёл 4/4 за 56 секунд, затем diagnostic original aggregate в том
  же меж-suite порядке прошёл полностью за 6:19; воспроизводимый defect не подтверждён.
- Второй frozen original Goal024 aggregate остановился в historical
  `acquisition-manor-active.01`, seed `21002102`: canonical sow timing не подтвердил расход seed.
  Три targeted повтора без изменений прошли 3/3 за 46/51/45 секунд, однако на следующем freeze
  тот же failure повторился в plain `verify`. Аудит подтвердил `Sowing reuseDelay=10000`: после
  неудачного sow следующий target получал dispatch на 0/4/8 секундах при disabled exact skill;
  item handler при этом возвращал `true`, хотя `Player.useMagic()` не принимал cast. Fixture теперь
  перед новой целью использует local pattern quiescence + exact test-owned skill enable и MP reset;
  тот же reset применяется к Harvester, чей handler скрывает отказ `useMagic()` таким же образом.

Final freeze gate, обязательный перед commit:

```text
focused Goal024A: PASS
original Goal024 aggregate: PASS
exact Goal010/017/018/020/021/023C affected regressions: PASS
historical verifiers 023/023A/023B/023C/024: PASS
working verifier 024A: PASS
one final Goal024A aggregate: PASS
one plain ant verify: PASS
standalone ant jar: PASS
freeze unchanged: PASS
post-commit PowerShell 5.1 / verified PowerShell 7 stdout: byte-identical PASS
production DB guard: PASS
```

Commit выполняется только если каждая строка final gate фактически подтверждена. Если после freeze
требуется релевантная правка, freeze пересоздаётся и последовательность повторяется с объяснением.

Mojibake-маркеры в изменённых файлах проверены: PASS.
Escaped Cyrillic в изменённых файлах проверены: PASS.

## Performance measurements

Новый worker/timer/Future и per-phantom scheduler отсутствуют. Exact restart загружает один
counterpart по ID; reconciliation bounded одной pair и existing history bound. Focused aggregate
занял 36.9 секунды на текущем хосте; отдельный performance SLA task не требовал.

## Deviations from TASK.md

- Отклонений по product scope нет.
- Native apply-patch sandbox helper на Windows не мог читать workspace из-за ACL. Изменения
  применялись официальным Codex apply-patch driver через локальный PowerShell; прямой файловой
  перезаписи не выполнялось.
- Plain ant отсутствует в PATH; используется repository-local Apache Ant 1.10.17 с теми же
  plain target semantics и без дополнительных properties.
- Два pre-freeze Goal024 aggregate прогона не засчитаны из-за timeout оболочки; это не заменяет
  обязательный успешный final aggregate.
- Первый staged diff check обнаружил Markdown hard-break trailing spaces в новом отчёте, который
  до staging был untracked. Пробелы удалены; freeze пересоздан, полная final sequence повторена.
- При повторной sequence historical manor-active проявил timing failure на seed 21002102; три
  immediate targeted повтора прошли. После direct-authority correction тот же failure повторился
  в обязательном plain `verify`, поэтому простой retry отклонён. Exact audit связал сбой с
  canonical 10-секундным Sowing cooldown между bounded targets. Первая коррекция с item reuse clear
  прошла 4/5 targeted runs, затем тот же failure воспроизвёлся ещё раз: `Seed.onItemUse()` скрывает
  отказ `Player.useMagic()` на disabled skill. Exact skill correction стабилизировала sow в 4/4
  следующих runs; пятый выявил симметричный masked `Player.useMagic()` boundary у Harvester.
  Минимально изменён только manor integration fixture: перед новой целью/harvest он дожидается
  quiescence, включает exact test-owned item skill и восстанавливает MP по существующему fixture
  pattern; production Combat/Manor не изменены.
- Финальный staged code review выявил fail-open gap при busy pair reconciliation: live agreement
  path не повторял stable authority comparison напрямую. Добавлены direct checks в обоих live
  binding paths и pinned verifier; после focused подтверждения создан новый freeze и вся sequence
  повторена.
- На следующем freeze final aggregate повторно остановился в historical active-spoil recovery
  case. Exact audit показал однократную проверку quiescence между observable Spoil и Combat при
  canonical `nextActionAttack=true`; она могла принять краткий промежуток до post-cast AI transition.
  Промежуточные stable `IDLE/no target` и stable no-action варианты корректно провалились: первый
  расширял cleanup contract, второй конфликтовал с canonical `nextActionAttack=true`. В уже
  изменяемом integration fixture независимые Spoil/Combat фазы теперь разделены существующим
  test-owned actor reset pattern. Production Combat/acquisition не менялись; нужен новый freeze.
- На freeze после fixture corrections original aggregate один раз завершился на quiet-mode
  `acquisition-quest-active` process без case-level assertion. Immediate exact route прошёл 4/4,
  а full-order diagnostic aggregate — полностью за 6:19 на unchanged code. Изменений по этому
  единичному сигналу не внесено; evidence зафиксирован report-only и tree refrozen.

## Known limitations

- Goal024/024A требуют независимого review; self-accept отсутствует.
- Objective BROKEN producer не добавлялся: в разрешённом call path нет достоверного breach evidence.
- Geodata/pathfinding, direct PvP и Goal025 не входят в Goal024A.

## Risks

- Schema v1 fail-closed до fresh exact revalidation; недоступная legacy visibility не даст ALLOW.
- Social retry зависит от будущего bounded farming pulse/gate, но durable pending truth не теряется.
- Goal021 observation matrix покрывает current/completed/released/moved/authority drift и restart faults.

## Git

- Branch: `feature/phantom-world`.
- Commit SHA: self-reference; определяется ordinary direct-child commit с required subject.
- Push result: обязателен; post-commit verifier требует `origin/feature/phantom-world == HEAD`.

Точный SHA невозможно записать внутрь того же commit без amend/второго commit. Финальный terminal
handoff и committed verifier дают проверяемое SHA/push evidence.

## Recommended next step

Независимый review по `docs/phantoms/reviews/024a-independent-review.md`. Goal025+ не начинать,
пока Goal024A не получит отдельное решение.
