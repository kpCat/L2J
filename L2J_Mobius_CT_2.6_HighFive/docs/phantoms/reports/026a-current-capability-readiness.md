# Goal 026A — current capability readiness

## Status

`SUCCESS`

## Summary

Закрыт только `R026A-01`: hard required RAID/EPIC capability теперь учитывается лишь при одновременном выполнении `intrinsic && learned && readyNow`. Мёртвый или текущий недоступный healer/resurrector больше не может дать `GROUP_READY`.

Goal 017 production code, CP1 architecture и historical corpus не изменялись и не переаудировались.

## Changed files

- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidReadinessService.java` — в существующий capability predicate добавлен `readyNow`.
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidReadinessSuite.java` — добавлен отдельный seed/mode и пять focused readiness cases.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — зарегистрирован только новый focused suite route.
- `build.xml` — добавлены seed `26002611` и отдельная focused Ant-цель.
- `docs/phantoms/tasks/026a-current-capability-readiness/*` — включён предоставленный task package.
- `docs/phantoms/reports/026a-current-capability-readiness.md` — этот отчёт.

## Architecture decisions

- Переиспользован существующий stateless `satisfies(...)` seam; новый слой и redesign Goal 017 не добавлялись.
- Capability key, `minimumRank`, `minimumCount` и optional semantics сохранены.
- `readyNow=false` исключает member capability из satisfying member count независимо от `intrinsic` и `learned`.

## DB / migrations

Изменений БД и миграций нет. Рабочая БД не использовалась.

## Configs

Production config не менялся. В `build.xml` добавлен только deterministic test seed `phantom.goal026a.seed=26002611`.

## Commands and results

- `git status --short --branch` — ветка `feature/phantom-world`, upstream `origin/feature/phantom-world`; до начала изменений обнаружен только untracked task package 026A.
- `git rev-parse --show-toplevel HEAD --abbrev-ref HEAD` — required parent подтверждён: `1f056dfd97969b463a9f7140a08f160e8fc16a74`.
- `ant phantom-raid-current-capability-readiness-test` — target не стартовал: `ant` отсутствует в `PATH`.
- JDK 25 + локальный `org.apache.tools.ant.launch.Launcher`, target `phantom-raid-current-capability-readiness-test` — первый запуск дошёл до suite и завершился до cases на invalid synthetic `minimumCount=2` fixture.
- Тот же focused target, единственный разрешённый rerun после исправления собственного fixture — `BUILD SUCCESSFUL`, 5/5, seed `26002611`, 18 s.
- JDK 25 + тот же Ant launcher, target `jar` — единственный запуск `jar`, `BUILD SUCCESSFUL`, 17 s.
- Plain `ant verify`, Goal 025 aggregate, Goal 011/017 regressions и broad tests не запускались согласно TASK.
- `git diff --check`, exact diff, scope inventory и две отдельные encoding-проверки выполнены перед commit; итоговые результаты: без ошибок и вне-scope файлов.

## Focused test results

1. Required healer с `intrinsic=true`, `learned=true`, `readyNow=false` и dead snapshot: `GROUP_INCAPABLE`.
2. Required resurrection с `readyNow=false`: `GROUP_INCAPABLE`.
3. Required capabilities с `readyNow=true`: `GROUP_READY`.
4. Rank и satisfying member count продолжают блокировать readiness.
5. Optional capability с `readyNow=false` остаётся unsatisfied evidence, но не блокирует `GROUP_READY`.

## Performance measurements

Отдельный performance target не запускался и не требовался. Focused rerun занял 18 s; `jar` занял 17 s.

## Deviations

- Штатный `apply_patch` был недоступен из-за Windows sandbox/WindowsApps `Access denied`; exact unified diffs применены через `git apply` и затем полностью проверены.
- Первый реальный focused запуск выявил invalid новый test fixture; fixture удалён, count проверяется на валидном существующем `minimumCount=1`, после чего использован единственный разрешённый rerun.
- Обычный `ant` отсутствует в `PATH`; существующий локальный Ant launcher из IntelliJ запущен через JDK 25.

## Limitations and risks

- Исправление ограничено пассивной CP1 readiness assessment и не меняет формирование Goal 017 `readyNow` evidence.
- CP1 остаётся `CHANGES_REQUIRED` до независимого review Goal 026A; CP2+ остаются `NOT_STARTED`.

## Delivery

- Branch: `feature/phantom-world`.
- Required parent: `1f056dfd97969b463a9f7140a08f160e8fc16a74`.
- Subject: `fix(phantoms): require current raid capability readiness`.
- Commit SHA: commit, содержащий этот отчёт; exact post-commit SHA указан в финальной delivery-сводке, поскольку commit не может содержать собственный SHA.
- Remote HEAD: после ordinary push должен совпасть с delivery commit; exact значение проверяется post-push и указывается в финальной delivery-сводке.
- Verdict: `R026A-01 CLOSED`; Goal 026 CP1 — `CHANGES_REQUIRED_PENDING_026A_INDEPENDENT_REVIEW`; CP2+ — `NOT_STARTED`.
- occurred_context_compaction: `no`.

## Next step

Независимый review exact Goal 026A commit. Следующий checkpoint не начинать до review.
