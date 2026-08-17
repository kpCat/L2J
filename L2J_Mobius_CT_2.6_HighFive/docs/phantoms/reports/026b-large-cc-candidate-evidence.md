# Goal 026B — bound raid candidate Party evidence

## Status

`SUCCESS — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal 026 Checkpoint 3 остаётся `CHANGES_REQUIRED` до независимого review 026B. Goal 026 overall — `IN_PROGRESS`; Checkpoint 4+ — `NOT_STARTED`.

`occurred_context_compaction: no`

## Summary

Закрыт только `R026B-01`. `PhantomRaidRecruitmentService` больше не передаёт весь CommandChannel как одну candidate Party: из `CurrentForceSnapshot.parties()` выбирается ровно одна Party, содержащая exact candidate, а в `CandidateAssessment` попадают только её максимум 9 members.

Exact leader Party внутри большого CommandChannel получает `NOT_STANDALONE_PARTY`; non-leader — `NOT_EXACT_PARTY_LEADER`. Missing, ambiguous или внутренне несогласованная own-Party evidence возвращает typed `EVIDENCE_UNAVAILABLE` с пустым bounded roster. Исключение из корректного `CandidateAssessment.partyMemberCount <= 9` не ослаблялось.

Standalone hard contribution, useful-members, excess и selection order не менялись.

## Read-first pass

Прочитаны `Agents.md`, полный `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, workflow/task-package contracts, весь пакет 026B, prior independent review, предыдущий CP3 report, production recruitment/model contracts, Goal 017 `CurrentForceSnapshot`/`PartySnapshot` и backend copy logic, весь focused recruitment suite и exact Ant route.

Корневой `README.md`, parent `AGENTS.md` и task-specific `CONTEXT.md`/`ARCHITECTURE.md`/`ACCEPTANCE.md` не найдены. Локальные паттерны: Goal 017 membership-based Party resolution, существующий `PartySnapshot` bound `<= 9`, corrective goal seed wiring и stateless focused recruitment service.

## Changed files

- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidRecruitmentService.java` — unique own-Party resolution, bounded evidence и typed fail-closed handling.
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidRecruitmentSuite.java` — large-CC exact-leader/non-leader regression evidence, ambiguous own-Party case и standalone selection control.
- `build.xml` — deterministic Goal 026B seed `26002632` подключён к существующему focused target.
- `docs/phantoms/tasks/026b-large-cc-candidate-evidence/*` — предоставленный task package.
- `docs/phantoms/reports/026b-large-cc-candidate-evidence.md` — этот отчёт.

## Architecture decisions

- Exact own Party определяется как единственный `PartySnapshot`, `members()` которого содержит candidate.
- Leader validation использует `candidateParty.leader()`, а не aggregate `CurrentForceSnapshot.partyLeader()` или весь CC roster.
- Candidate member refs и capability snapshots ограничены exact own Party; `partyMembers`, contribution и bounds используют этот roster.
- При нуле или нескольких matching Parties, duplicate refs либо неполном snapshot mapping состояние не угадывается: `EVIDENCE_UNAVAILABLE`, пустой roster.
- Проверка `NOT_EXACT_PARTY_LEADER` выполняется до `NOT_STANDALONE_PARTY`, поэтому non-leader большого CC получает точный typed reject.
- `CandidateAssessment` и его invariant не изменялись; Party не дробится; comparator/scoring/selection code не изменялся.

## DB, migrations, configs

Изменений БД, migrations и production config нет. Рабочая БД не использовалась. Добавлен только deterministic test seed в `build.xml`.

## Commands and results

- `git status --short --branch`, branch/upstream/HEAD checks — branch `feature/phantom-world`, upstream `origin/feature/phantom-world`, required parent `c9d2d429b1a7655d36676f8f8496de53d9cff11d`; сохранены unrelated untracked CP2 launcher/manifest.
- Installed JDK 25 + local `org.apache.tools.ant.launch.Launcher`, target `phantom-raid-recruitment-test` — `BUILD SUCCESSFUL`, `9/9`, seed `26002632`, 19 s; выполнен один раз, rerun не потребовался.
- Тот же launcher, target `jar` — `BUILD SUCCESSFUL`, 17 s; выполнен ровно один раз.
- `git diff --check` — PASS.
- Strict UTF-8 decoding exact scope — PASS.
- Mojibake markers в изменённых файлах проверены — совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены — совпадений нет.

Не запускались запрещённые CP3 aggregate 14/14, CP1 regression, CP2 gates, broad Goal 017, plain `ant verify`, Goal 025 aggregate, all-Phantom и stress routes.

## Focused R026B-01 evidence

1. Exact Party leader в 12-member CommandChannel: no exception, `NOT_STANDALONE_PARTY`, `partyMemberCount=6`, roster содержит только exact own Party.
2. Non-leader в 10-member CommandChannel: no exception, `NOT_EXACT_PARTY_LEADER`, `partyMemberCount=5`, roster содержит только exact own Party.
3. Candidate, одновременно присутствующий в двух `PartySnapshot`, fail closed: `EVIDENCE_UNAVAILABLE`, `partyMemberCount=0`.
4. Existing standalone candidate остаётся `RECRUITABLE` и выбран тем же winner; остальные existing scoring/order assertions прошли.

## Performance and bounds

Новый поиск ограничен максимум 18 Party snapshots и 144 force members существующего Goal 017 contract; own Party ограничена 9 members. Потоки, scheduler/worker, DB I/O, World/profile scans и hot-path logging не добавлены. Отдельный performance target не запускался и не требовался.

## Deviations

- Системный `apply_patch` не смог читать workspace из-за Windows sandbox ACL. Bounded `git apply` fallback также не смог сопоставить CRLF working tree; изменения внесены exact-line replacement с проверкой ожидаемого исходного блока и затем проверены полным bounded diff.
- `ant` отсутствует в `PATH`; использованы установленный JDK 25 и локальный Ant launcher IntelliJ, как в предыдущем corrective goal.

## Limitations and risks

- Изменение не открывает новый CP3 audit и не меняет discovery, consent, invite fallback, navigation/combat/retreat/loot, persistence или scheduling.
- CP1/CP2/Goal 017 production не изменялись.
- Checkpoint 3 остаётся `CHANGES_REQUIRED` до independent review exact Goal 026B commit.

## Delivery

- Branch: `feature/phantom-world`.
- Required parent: `c9d2d429b1a7655d36676f8f8496de53d9cff11d`.
- Subject: `fix(phantoms): bound raid candidate party evidence`.
- Commit SHA: commit, содержащий этот отчёт; exact post-commit SHA указывается в финальной delivery-сводке, поскольку commit не может содержать собственный SHA.
- Remote HEAD: после ordinary push должен совпасть с delivery commit; exact значение проверяется post-push и указывается в финальной delivery-сводке.
- Verdict: `R026B-01 CLOSED`; 026B = `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; CP3 = `CHANGES_REQUIRED_PENDING_026B_INDEPENDENT_REVIEW`.
- occurred_context_compaction: `no`.

## Next step

Независимый review exact Goal 026B commit. Checkpoint 4 не начинать до принятия текущего gate.

`GOAL_026B_RAID_CANDIDATE_EVIDENCE_FIXED_PENDING_INDEPENDENT_REVIEW`
