# Goal 017 — Party coordination kernel

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

Реализована одна цельная capability без 017A/017B: canonical party invitation
для packet handlers и Phantom, durable leader/member claims, Phantom-only
recovery, contextual roles/vacancies, typed semantic acts, shared route/regroup
и party tactics через общий combat external-action ownership.

Поддержаны Phantom↔Phantom, Phantom→real и real→Phantom. Consent реального
игрока после restart не восстанавливается. `Player.java`, schema и другие
хроники не изменялись.

До Goal 017 зафиксирован независимый verdict Goal 016:
`ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`, без нового suffix.

## READ_SET — exact current-code audit

Прочитаны обязательные master plan, workflow/task standards, весь task package
017 и report 016. Точечно проверены:

1. `RequestJoinParty`, `RequestAnswerJoinParty`, `Party` и нужные участки
   `Player`;
2. OfflinePlay party restore и AutoPlay follow/assist;
3. materialization ActionLease и outbound session;
4. goal store/decision engine/scheduler control;
5. progression capability catalog/evaluator;
6. combat sessions, respawn, actor lease и L2J backend;
7. navigation service/progress и topology snapshot;
8. profile component optimistic repository;
9. population/background/commerce ownership patterns;
10. текущие tests/build/verifiers.

Локальные паттерны: bounded binary component codec, optimistic row version,
immutable backend snapshots, ActionLease, exact goal identity, shared scheduler
control, fixed aggregate metrics.

## Scope / changed files

Production:

- `model/groups/PartyInvitationDelivery.java`, `PartyInvitationService.java`;
- оба party packet handlers;
- `phantoms/party/**` и `phantoms/semantic/**`;
- combat backend/service/actor lease/metrics;
- composite scheduler control и `PhantomSystem`;
- `PhantomPlayersConfig.java`, `PhantomPlayers.ini`;
- `high-five-party-roles-v1.xml`.

Tests/build:

- `PhantomPartySuite.java`, `PhantomTestLauncher.java`;
- девять focused targets, affected aggregate, final party aggregate;
- `verify-task-017.ps1`;
- descendant-compatible `verify-task-016.ps1`.

Docs:

- review Goal 016;
- этот report и `PARTY_COORDINATION_CONTRACT.md`;
- status-only roadmap/master-plan alignment;
- неизменённый task package 017.

## Invitation state machine

```text
validate → reserve exact request → client/managed delivery
→ refuse/disabled/cancel/expire
или revalidate → canonical Party commit → observe → durable commit
```

Packet handlers делегируют общему core service. Fake `GameClient`, handler
invocation и direct member-list mutation отсутствуют.

## Persistence state machine

```text
PREPARED → CANONICAL_PENDING → CANONICAL_OBSERVED → COMMITTED
                                               ↘ ABORTED
```

`party.state` schema 1, payload ≤4096, roster ≤9. Manifest/member claims
содержат generation/revision и hashes. Recovery удаляет real refs, не
воспроизводит real consent и детерминированно переизбирает Phantom leader.

## Role/vacancy evidence

Строгий XML связывает generic roles только со string capability keys.
Matcher сохраняет multi-capability facts и учитывает objective, readiness,
resources и runtime state. Вывод: FILLED/MISSING/OPTIONAL/UNSUPPORTED с
provenance и evidence hash. Class-ID switch отсутствует.

## Semantic acts

String-key acts имеют typed refs/slots, stable hash и generation guard. Текст,
LLM, personality и Semantic Pack parsing отсутствуют; act сам не мутирует state.

## Shared route и tactics

На группу существует один leader navigation request/manifest. Followers
используют shared waypoint или leader position при regroup. Snap, teleport,
cross-instance и background travel отсутствуют.

Assist/protect подтверждают normal monster. Heal/recharge/resurrect и один
explicit buff/song/dance используют exact progression action skill. Все
движения и tactics проходят через общий combat external lease.

## Config, DB и migrations

- `PhantomPartyOperationsPerPulse = 64`;
- диапазон `1..10000`, legacy missing default `64`;
- Phantom World по-прежнему выключен по умолчанию;
- schema/migrations отсутствуют;
- тесты используют только `l2jmobiush5_phantom_test`;
- deterministic seed `17001701`.

## Commands and results

Early read-only Git:

- `git status --short --branch`;
- `git branch --show-current`;
- `git rev-parse` для HEAD/upstream/required parent;
- `git merge-base --is-ancestor`;
- bounded `git show`, `git diff --check`, `git diff --name-only` для Goal 016.

Build/test:

- `ant compile`, `ant compile-tests` — PASS;
- все девять focused Goal 017 modes — PASS;
- `phantom-party-affected-test` — PASS, 27 affected targets;
- verifiers 014A/015/016/017 до cumulative run — PASS:
  `TASK014A_VERIFIER_OK`, `TASK015_VERIFIER_OK`, `TASK016_VERIFIER_OK`,
  `TASK017_VERIFIER_OK`, working scope 49;
- final `phantom-party-test` после последнего source fix — PASS, 32/32, 0:19;
- первый full `ant verify` — `BUILD SUCCESSFUL`, 10:47;
- после реального source/test/verifier fix разрешён второй full `ant verify`;
  он дошёл до unrelated historical flake
  `population-server-integration.01-real-create-materialize-sleep-retire`
  (`Materialized population entry does not retain a real Player`) и завершился
  FAIL за 10:40;
- единственный разрешённый exact targeted retry
  `phantom-population-server-integration-test` — PASS 1/1, 0:32; третьего full
  verify не было;
- финальный standalone `ant jar` — `BUILD SUCCESSFUL`, 0:15;
- два byte-identical post-commit verifier 017 выполняются после immutable
  report/ordinary commit; hashes и result приводятся в финальном ответе.

Первые короткие Ant invocations были остановлены process timeout до получения
результата и не заявляются как пройденные. Реальные fixes до cumulative run:
точное structural assertion packet-handler delegation, пустой operation
failure key, ожидаемый порядок vacancy statuses, requester/invitee reservation
без общего lock на external boundaries, route-scoped cancellation и
descendant-compatible verifier 014A. Финальный source fix добавил durable
Phantom member claim до canonical invite, terminal rollback и exact stale
leader-goal revision guard. Финальный performance review заменил
экспоненциальный role search на детерминированный
`O(requirements × members × capabilities)` matcher и закрепил приоритет
required vacancy над optional.

## Performance

- 100000 composite control pulses;
- synthetic 10000 profiles / 1000 groups;
- matcher bounded девятью members;
- pulse budget `64`;
- no per-party/profile worker, executor, timer или Future;
- fixed aggregate metrics без IDs в labels.

Точные elapsed measurements находятся в generated test reports.

## Deviations and limitations

- Cross-profile ACID не заявляется; используется phase saga и optimistic claims.
- Real leader — observation-only для route.
- Геодата не добавлялась; сохраняется принятый navigation fallback contract.
- Global matchmaking, Rift, background rewards, text/LLM, personality, clans,
  PvP, economy и Goals 018/019/020/023/025 вне scope.

## Scope guard

Разрешён bounded exception больше десяти файлов: задача сама определяет
несколько связанных integration seams одной capability. Изменений вне High Five
нет. `Player.java` и schema не изменены.

- Mojibake-маркеры в 49 изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в 49 изменённых файлах проверен отдельно: user-facing
  совпадений нет.

## Branch, commit, push

- branch: `feature/phantom-world`;
- required parent: `57caea2e5b5597c9a06b87cb8e868f227c4aa88e`;
- commit: SELF, subject `feat(phantoms): add party coordination kernel`;
- commit SHA и push result сообщаются в финальном ответе; report не изменяется
  после ordinary commit и не требует amend;
- force push/amend/rebase/squash/merge: не используются.

## Next step

Независимый review Goal 017. Goal 018/019/020/023/025 до принятия gate не
начинать.
