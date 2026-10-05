# Engineering011 — GREEN, runtime gate REQUIRED, M1 OPEN

Required base: `26061ccff5aa22450ff3c4a01a2636e660f879e9`.
Branch: `experiment/m1-candidate007-observe008`. Без субагентов.

Root cause подтверждён: REPLAN_REQUIRED/native_context.required переводил ecology
в terminal; ReconcileFirstActivityPort требовал complete до создания native Player.
ensureNativeContext может вернуть native requirement с PENDING snapshot; FAILED
snapshot также покрыт. Cached reason и request/window ownership проверяются вместе.

Изменены только PhantomHistoricalBackgroundService.java,
PhantomPopulationEcologyService.java и PhantomPopulationEcologyGoal033Suite.java.
Переиспользованы exact classifier, deferRetry, worker-cached snapshot и memory fixtures.
Native requirement сохраняет pending history, bounded background retry и demand.
Только requestMaterializationDue с текущим human demand получает complete=true,
advancedIntervals=0, ecology.native_materialization_required. Background gates и
permitsScheduling остаются строгими. Cursor/completion/requestPending не подделываются.
Player, PhantomSystem, native activity port, combat/movement/DB schema не изменены.

## Проверки

- Deterministic RED: seed33003300, 17 PASS / 1 FAIL; новый case18 ожидал true,
  получил false для native_context.required:coalesced. Компиляция прошла.
- Focused GREEN: тот же suite, 18/18, exit0. Case18 покрывает FAILED и PENDING,
  transaction.native_context_required, отсутствие demand, idempotence/нулевой receipt,
  закрытый background, generic recovery, bounded retries, live owner и обычный terminal.
- Affected cheap regression: существующий population-ecology-handoff-regression,
  seed33003300, 6/6, exit0; elapsed cursor, crash/restart, live boundary,
  stale authority, incomplete due и restored inventory.
- ant -q jar: exit0, BUILD SUCCESSFUL, 30s.
- Два прежних System.runFinalization removal warnings вне scope.
- WORLD/full native closure suites не запускались.

Ant использует только override build output:
`-Dbuild=C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/m1-011-build`.
Focused: ant -q [override] phantom-population-ecology-goal033-test.
Regression: java с теми же compiled bin/test bin/resources/libs, штатный
PhantomTestLauncher population-ecology-handoff-regression 33003300.
Build: ant -q [override] jar. Source остаются в experiment worktree.

Первая попытка остановлена: sandbox мешал Ant удалить прежний worktree build output.
Следующая sandbox-компиляция получила AccessDeniedException для JAR-зависимостей;
это infrastructure failure, не targeted RED. После escalation получен настоящий RED.
Первый GREEN остановился на слишком строгом общем retry assertion: PENDING использует
экспоненциальный backoff, FAILED — прежний 256-pulse probe. Пределы fixture уточнены
по этому контракту, production fix не расширялся. Финальный suite полностью GREEN.

Git разрешён прямым запросом и GIT.md task011. Использованы:
git status --short; git status --short --untracked-files=no;
git rev-parse --show-toplevel; git branch --show-current; git rev-parse HEAD;
git rev-parse --abbrev-ref --symbolic-full-name '@{u}'; git worktree list --porcelain;
git diff --check; git diff --stat; git diff --name-only;
git diff -- [два точных production-пути]; git diff -- [точный test-путь].
Перед commit: git remote get-url origin, exact diff/scope/encoding guard,
git add -- [три source-пути, ENGINEERING.md, RED.txt, GREEN.txt],
git diff --cached --check, git diff --cached --name-only,
git commit -m 'phantom(task-011): unblock native-context materialization demand',
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008.
Receipt commit/push и runtime evidence публикуются после фактических команд.

mojibake-маркеры в изменённых файлах проверены: precommit guard.
escaped Cyrillic в изменённых файлах проверены: precommit guard.

Runtime ещё не наблюдался. Следующий gate: fresh observe011, effective config,
startup readiness, затем ручной вход TestAdmin. M1 CLOSED не объявляется.
