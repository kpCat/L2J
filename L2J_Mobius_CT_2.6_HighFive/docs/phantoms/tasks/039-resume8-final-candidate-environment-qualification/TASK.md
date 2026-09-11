# Goal039 Resume 8 — final candidate environment qualification and freeze

## 1. Идентификатор

Goal039 Resume 8.

Required parent:
`539688cda76c06bf48528f210cbff03524818871`

Branch:
`feature/phantom-world`

Blocker:
`GOAL039_RESUME7_FRESH_VERIFY_CANDIDATE_ENVIRONMENT_FAILURE`

Family:
`FINAL_CANDIDATE_ISOLATION_AND_FILESYSTEM_STABILITY`

Это не Goal040.

## 2. Цель

Закрыть доказанный environment blocker final clean candidate, получить один
полностью успешный fresh `ant verify`, собрать standalone final JAR, прогнать
fresh Goal034 real stack на этом exact JAR и завершить Goal039 freeze.

Не расширять Phantom World.

## 3. Зависимости

Resume 7 опубликован и является required parent.

Его historical-documentation correction принята как вход.

26/28 текущей Goal039 matrix уже PASS.

## 4. Контекст

Обязательно прочитать CONTEXT.md, ROOT_CAUSE.md,
CANDIDATE_ENVIRONMENT.md, EVIDENCE_REUSE.md, TEST_PLAN.md и ACCEPTANCE.md.

## 5. Обязательный предварительный аудит

До изменений:

- подтвердить branch/HEAD/origin;
- прочитать current Goal039 report/matrix;
- проверить exact Resume-7 changed-file set;
- проверить `verify-task-014.ps1` Git-root behavior;
- проверить `build.xml` jar/test/verify/Goal034 dependencies;
- проверить DB guard `toRealPath()` safety;
- проверить локальный Resume-7 evidence manifest, если он сохранился;
- зафиксировать dirty-tree fingerprint.

## 6. Архитектурное решение

Release candidate = нормальный отдельный Git clone с собственным `.git`,
созданный сразу в окончательном внешнем path.

Candidate не перемещается.

До expensive verify обязателен environment qualification canary.

Исторические verifiers и DB guard НЕ ослабляются.

## 7. Scope

Разрешено:

- TEST-only update Goal039 `REQUIRED_PARENT` -> `539688cda76c06bf48528f210cbff03524818871`;
- task/evidence/report/matrix/final-freeze docs;
- disposable isolated candidate outside operator repo;
- bounded local clone command defined in CANDIDATE_ENVIRONMENT.md;
- safe local test DB usage;
- final Goal034 processes under exact test ownership.

Expected production changes: 0.

## 8. Out of scope

Запрещено:

- production feature changes;
- build.xml changes;
- DB guard weakening;
- Goal014 verifier weakening;
- Java downgrade/upgrade workaround;
- shipped config/data workaround;
- production DB;
- DB provisioning;
- Goal040;
- unrelated cleanup/refactor.

## 9. Требуемые изменения

До verify:

1. только Goal039 TEST provenance constant.

После successful verify/JAR/Goal034:

2. Goal039 report;
3. Goal039 matrix;
4. master plan;
5. roadmap;
6. current status;
7. handoff;
8. final freeze file.

Task package staging разрешено только как documentation input according to current
Goal039 pattern.

## 10. Конфиги

Shipped runtime config не менять.

Test config:
только candidate `.phantom-local/Database.test.ini`.

Не печатать password.

Safe shipped requirements остаются:
system OFF, population/ACTIVE 0/0, diagnostics OFF, mature OFF.

## 11. Производительность

Не повторять дорогие Resume-7 domain/scale/rollback gates при выполненном
EVIDENCE_REUSE contract.

Environment canaries должны выполняться до full verify.

## 12. Конкурентность и lifecycle

Каждый Ant/Java/PowerShell process candidate должен быть дождался exit.

Не использовать wildcard Java kills.

Goal034 cleanup обязан доказать no orphan / forced=false.

Candidate не перемещать после появления процессов/архивов.

## 13. БД и транзакции

Только:
`127.0.0.1:3308/l2jmobiush5_phantom_test`
user `l2j_phantom_test`.

Production DB запрещена даже для read/probe.

`prepare-phantom-test-db` запрещён.

DB guard не менять.

## 14. Автоматические тесты

Выполнить TEST_PLAN.md.

Mandatory fresh:
candidate qualification -> full verify -> standalone jar -> Goal034 real stack ->
Goal039 documentation/freeze validation.

## 15. Команды проверки

Использовать существующие Ant targets.

Минимальный qualification set:

- `ant -q jar` CANARY_ONLY;
- JDK `jar tf` для LoginServer/GameServer;
- `ant -q compile-tests`;
- `ant -q test` два раза подряд;
- `ant -q phantom-static-verify-014`;
- Goal039 static;
- DB guard/preflight.

После qualification:
`ant verify`.

После verify:
`ant -q jar` = FINAL.

Затем Goal034 exact target без повторного jar.

## 16. Критерии приёмки

Все критерии ACCEPTANCE.md обязательны.

Final matrix 28/28.
Exact marker.
No Goal040.

## 17. Формат отчёта

Обновить:
`docs/phantoms/reports/039-final-full-vision-release-gate.md`

Отдельно записать:

- environment root cause;
- candidate path topology;
- qualification canaries;
- reused Resume-7 evidence;
- fresh verify result;
- final JAR hashes/bytes;
- Goal034 run ID + lifecycle/cleanup;
- final matrix;
- production DB statement;
- final marker.

## 18. Commit/push

Operator Git:

Разрешены bounded:
fetch, branch/rev-parse/status/diff/log/show, exact-path add, ordinary commit,
non-force push.

Disposable candidate:
одна явно разрешённая normal local `git clone` с initial checkout; read-only
branch/rev-parse/status/show/diff after that.

Operator repo forbidden:
reset, restore, checkout, clean, stash, rebase, merge, amend, force push,
history rewrite.

SUCCESS commit:
`phantom(goal-039): freeze declared full vision`

BLOCKED commit:
`phantom(goal-039): record resume 8 blocker`

## 19. Поведение при блокировке

Same environment family:
follow ROOT_CAUSE.md bounded qualification/retry budget.

New substantive independent blocker:
одна focused confirmation и STOP.

Не чинить вторую независимую family в Resume 8.

При ACCEPT:
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`
и Roadmap v5 закончен.

После ACCEPT:
не создавать Resume 9 и не создавать Goal040.
