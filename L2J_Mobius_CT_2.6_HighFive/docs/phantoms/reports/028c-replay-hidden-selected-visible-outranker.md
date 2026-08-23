# Goal 028C — replay hidden-selected visible-outranker corrective

## Status

- Delivery status: `SUCCESS`.
- Goal 028C: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 Checkpoint 5: `CHANGES_REQUIRED_PENDING_028C_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS`.
- Required parent: exact `74aa421fafc6fc3ed05bf687f904e53bf85753b0`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Исправлен ровно один найденный independent CP5 review дефект pure candidate verification. Для non-null selected candidate проверка visible `ELIGIBLE` outranker теперь выполняется до ветки missing selected row. Поэтому отсутствие selected candidate в retained top-8 больше не скрывает прямое противоречие, уже доказуемое captured `decision.score()` и `decision.candidateKey()`.

Null-selected, canonical ordering, duplicate keys, digest, bundle validation, health replay, capture, process slot и Admin semantics не изменялись.

## Changed files

1. `java/org/l2jmobius/gameserver/phantoms/PhantomDecisionReplay.java` — существующий pure visible-outranker scan перенесён перед missing-selected => `UNVERIFIABLE`.
2. `test/java/org/l2jmobius/gameserver/phantoms/PhantomDeterministicDecisionReplayGoal028Checkpoint5Suite.java` — добавлены четыре focused CP5 regression cases.
3. `docs/phantoms/reports/028c-replay-hidden-selected-visible-outranker.md` — этот отчёт.

User-owned untracked task packages оставлены read-only и не входят в staging/commit.

## Candidate rule proof

После неизменённых canonical order и unique-key checks алгоритм сохраняет неизменённую null-selected ветку. Для non-null selected key каждый visible candidate проверяется по exact canonical winner relation:

- `candidate.status() == ELIGIBLE` и `candidate.score() > decision.score()` => `MISMATCH`;
- либо equal score и `candidate.candidateKey().compareTo(decision.candidateKey()) < 0` => `MISMATCH`;
- это выполняется независимо от присутствия selected row в top-8;
- затем present selected row обязан иметь exact `ELIGIBLE` и exact captured score, иначе `MISMATCH`;
- absent selected row без visible outranker => `UNVERIFIABLE`;
- present consistent selected row без outranker => `VERIFIED`.

Проверка ограничена существующим bounded top-8 list и не меняет selector, capture или replay schema.

## CP5 regression outcomes

Focused suite прошла `7/7`, seed `28002805`. Внутри `candidate-tri-state` подтверждены новые cases:

1. hidden selected `candidate.z/600` + visible eligible `candidate.a/700` => replay `FAIL`, candidate `MISMATCH`.
2. hidden selected `candidate.z/700` + visible eligible `candidate.a/700` => replay `FAIL`, candidate `MISMATCH` по lexicographic tie-break.
3. hidden selected `candidate.z/900` + visible eligible `candidate.a/700` => replay `PASS`, candidate `UNVERIFIABLE`.
4. hidden selected `candidate.a/700` + visible eligible `candidate.z/700` => replay `PASS`, candidate `UNVERIFIABLE`.

Существующие visible-selected, null-selected и noncanonical-order assertions также прошли в том же scenario.

## Commands and test results

Baseline read-only Git checks:

- `git status --short` — user-owned untracked packages обнаружены и сохранены;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse HEAD` — exact required parent PASS;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`.

Final gates в exact required order:

1. `ant phantom-deterministic-replay-goal028cp5-test` через project-local Apache Ant 1.10.17 — `BUILD SUCCESSFUL`, CP5 `7/7`, seed `28002805`, 20 seconds.
2. Ровно один final `ant jar` после final source/test changes — `BUILD SUCCESSFUL`, 17 seconds; LoginServer, GameServer и DatabaseInstaller jars собраны, server jars скопированы в рабочий `dist/libs`.

Первый вызов bare `ant` завершился до Ant/test execution, потому что executable отсутствовал в `PATH`; после bounded discovery тот же focused target был запущен через `.phantom-local/apache-ant-1.10.17/bin/ant.bat` и прошёл. Это не test failure и не дополнительный jar invocation.

CP1–CP4 не запускались повторно: TASK 028C явно требует только исправление pure CP5 candidate check и запрещает их rerun. DB/domain/performance/stress/soak также не запускались, потому что production change не касается этих подсистем и TASK явно исключает эти gates.

## DB, config, architecture and performance

DB не открывалась и не изменялась. Migration/config/schema/persistence artifacts отсутствуют. Новые API, зависимости, потоки, timers, I/O и внешние integrations не добавлялись. Runtime complexity остаётся bounded linear scan существующих максимум восьми candidate explanations.

## Static, encoding and scope

- `apply_patch` не вызывался;
- source/test правки выполнены unique exact-anchor UTF-8-no-BOM temp + same-directory atomic rename;
- первая попытка `File.Replace` безопасно остановилась до замены из-за null backup-path binding, temp был удалён; успешная повторная правка использовала `File.Move(..., overwrite: true)`;
- `git -c core.whitespace=cr-at-eol diff --check` — PASS;
- exact changed/staged allowlist — PASS;
- no-other-chronicle scope — PASS;
- UTF-8 BOM в changed files отсутствует;
- temporary Goal028C edit artifacts отсутствуют;
- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

## Deviations, limitations and risks

- Replay остаётся проверкой только retained diagnostic evidence. `UNVERIFIABLE` по-прежнему означает недостаточность top-8 evidence, если direct visible outranker отсутствует.
- CP5 сохраняет `CHANGES_REQUIRED_PENDING_028C_INDEPENDENT_REVIEW`; acceptance может выставить только следующий independent review.
- Goal 028 остаётся `IN_PROGRESS`; следующий Goal/Slice не начинался.

## Git and delivery

Git-команды разрешены TASK для exact parent/branch/upstream, bounded diff/scope, ordinary commit и push. Использованы baseline status/rev-parse, bounded diff/name/status/diff-check, exact allowlist add, staged verification, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не использовались.

Preferred commit subject: `fix(phantoms): reject replay visible outrankers`.

Commit SHA и push result приводятся в финальном сообщении: self-referential SHA нельзя записать внутрь того же atomic report-bearing commit.

## Next step

Independent review Goal 028C. До review Goal028C остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, CP5 — `CHANGES_REQUIRED_PENDING_028C_INDEPENDENT_REVIEW`, Goal028 — `IN_PROGRESS`.
