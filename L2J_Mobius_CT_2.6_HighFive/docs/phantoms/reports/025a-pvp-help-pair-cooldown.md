# Goal 025A — PARTY help delivery и pair-scoped cooldown

Status: SUCCESS
Verdict: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Branch: feature/phantom-world
Required parent: 5656b9ce8c423f503d4a8b5d1046eb12929950d4
Commit subject: fix(phantoms): correct pvp help and pair cooldown
Commit SHA: фиксируется внешним final handoff, поскольку atomic commit не может содержать собственный SHA
Remote HEAD: проверяется после ordinary push и фиксируется внешним final handoff
Seed: 25002511

## Scope truth

- Goal025: CHANGES_REQUIRED pending 025A independent review.
- Goal025A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.
- Goal026+: NOT_STARTED.
- Goal024/024A: ACCEPT сохранён.
- Закрывались только R025A-01 и R025A-02; новый аудит Goal025 и поиск новых acceptance gaps не выполнялись.

## Read-first и локальные аналоги

PRIOR_INDEPENDENT_REVIEW.md прочитан первым. Затем полностью прочитаны AGENTS.md, PHANTOM_DEVELOPMENT_MASTER_PLAN.md, CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, весь task package 025A и отчёт Goal025. Родительский ../AGENTS.md, README.md и отдельный code-map/pattern-файл для коррекции не найдены.

Переиспользованы Goal017 PhantomPartyTactics/PvpProtectionEvidence exact target-member seam, Goal020 PhantomPvpConversationBridge/L2jPhantomConversationExecutionPort current Party membership gate, Goal025 Pvp service/context/model/store/policy и существующие focused suite/launcher/Ant patterns.

## Changed files

- build.xml
- java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java
- test/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpCorrectiveSuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
- docs/phantoms/tasks/025a-pvp-help-pair-cooldown/CODEX_LAUNCHER.txt
- docs/phantoms/tasks/025a-pvp-help-pair-cooldown/PACKAGE_MANIFEST.json
- docs/phantoms/tasks/025a-pvp-help-pair-cooldown/PRIOR_INDEPENDENT_REVIEW.md
- docs/phantoms/tasks/025a-pvp-help-pair-cooldown/TASK.md
- docs/phantoms/tasks/025a-pvp-help-pair-cooldown/TEST_CASES.md
- docs/phantoms/reports/025a-pvp-help-pair-cooldown.md

Это bounded exception к ориентиру 8–10 файлов: пять task-package файлов — неизменённый входной payload, один — обязательный отчёт; реализация ограничена одним production-файлом и четырьмя focused test/build файлами. Split нарушил бы atomic delivery.

## R025A-01 evidence/result

- HELP_REQUEST получает observed.helpCounterpart только для PARTY_DEFENSE.
- Допускается только положительный profile/character.object reference, не равный hostile conversation counterpart.
- Missing/non-party/hostile-substituted evidence fails closed до outbound submission; недолговечная submission также не запускает combat.
- WARNING/DISENGAGE продолжают использовать hostile conversationCounterpart; combat candidate не менялся.
- Focused core case 01 проверил exact M, отсутствие подстановки E, fail-closed negatives и hostile routing.
- Реальный Goal020 case передал exact current Party member в зарегистрированный PARTY handler и получил SENT; после выхода member из Party получил FAILED без новой generated delivery.

Result: R025A-01 CLOSED_PENDING_INDEPENDENT_REVIEW.

## R025A-02 evidence/result

- Текущий causal source/counterpart наблюдается до решения о persisted cooldown.
- Cooldown блокирует только same stable pair для proactive source до expiry.
- Cooldown(A) пропускает ACTUAL_ATTACK B, PARTY_DEFENSE B и proactive REVENGE B в обычный policy path.
- Same-pair proactive A остаётся gated; fresh same-pair ACTUAL_ATTACK/PARTY_DEFENSE остаётся reactive defense.
- Store/schema не расширены, per-pair collection не добавлена, invariant <=1 encounter/profile сохранён.
- Focused core cases 02/03 проверили B reactive/proactive, same-pair expiry и same-pair reactive defense.

Result: R025A-02 CLOSED_PENDING_INDEPENDENT_REVIEW.

## Architecture, DB, config и performance

Goal017 сохраняет ownership Party evidence, Goal020 — outbound/chat, Goal012 — combat, Goal025 — orchestration. Direct ChatHandler/packet/Player chat, worker/timer/Future/thread и unbounded map не добавлялись. SQL, migrations, production DB, config, policy, public schema и другие хроники не изменялись. Historical verify-task-014a.ps1 не изменялся. Legacy Monster production path не менялся.

## Verification

1. ant phantom-pvp-goal025a-test:
   - core 025A 3/3 PASS, seed 25002511;
   - два существующих Goal020 cases PASS;
   - новый case подтвердил SENT/FAILED, но run завершился FAILED на test-only ожидании ровно одной generated delivery; assertion исправлен на положительную delivery и отсутствие прироста после stale request;
   - combat target не стартовал из-за fail-fast.
2. ant phantom-conversation-outbound-chat-test phantom-pvp-goal025a-combat-regression-test:
   - Goal020 3/3 PASS, seed 20002002;
   - pvp combat/legacy Monster 3/3 PASS, seed 25002501;
   - BUILD SUCCESSFUL.
3. ant jar:
   - 2176 production sources;
   - BUILD SUCCESSFUL, 16 seconds.

Plain ant verify, Goal025 214-test aggregate, broad regressions и stress loops не запускались. Goal017 production files не менялись, отдельный Goal017 regression не требовался.

FINAL_DIFF_CHECK: PASS.
FINAL_SCOPE_CHECK: PASS — exact allowlist, только High Five.
FINAL_MOJIBAKE_SCAN: PASS — 0 markers.
FINAL_ESCAPED_CYRILLIC_SCAN: PASS — 0 patterns.
FINAL_FORBIDDEN_SCAN: PASS.

## Commands, deviations и limitations

TASK.md/AGENTS.md разрешили bounded git status/branch/upstream/rev-parse/diff/diff --check/ls-files/hash/show/log/apply/add/commit/push/ls-remote. Нативный apply_patch недоступен из-за Windows sandbox helper apply deny-read ACLs; edits выполнялись exact unified patches. Для отчёта после OEM-сбоя интерактивного stdin использована точечная проверенная UTF-8 запись только этого незакоммиченного файла.

Process deviation: correcting focused rerun двух top-level Ant targets повторил compile-tests из-за dependency semantics. Это превысило ориентир одной compile check, но не расширило test scope и не запускало запрещённые gates.

Полный серверный runtime не запускался; требуемая semantics покрыта deterministic и real Goal020 handler integration. Незавершённых implementation findings внутри R025A-01/02 нет. Commit SHA и remote HEAD будут в final handoff. Следующий шаг — независимый review Goal025A; Goal026+ не начинается.
