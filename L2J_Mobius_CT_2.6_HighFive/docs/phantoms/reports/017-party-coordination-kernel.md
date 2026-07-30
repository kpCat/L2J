# Goal 017 — завершение lifecycle safety Party

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

Goal 017 продолжен без 017A/017B/017C последним micro-completion от required
parent `0015a5ffd0c10a99514732ef52b969a39ac62eb7` в ветке
`feature/phantom-world`.

Independent review принял completion architecture и baseline commit. Micro scope
закрывает reusable terminal formation, safe party pulse minimum и
подтверждённую test-only population race.

Исправлены только доказанные lifecycle-safety findings:

- двусторонний expiry и exact-once terminal invitation lifecycle;
- durable managed requester/invitee preparation по точной invitation identity;
- idempotent form/invite и lifecycle command retry;
- leave, expel, leader transfer и travel с exact goal/generation/target guards;
- operation/persistence/shutdown claims;
- background party gate;
- bounded maximum role matching;
- exact-target support capability и typed skill execution;
- route topology/deadline/missing-member guards;
- индексированный bounded coordinator pulse.

`Player.java`, `Party.java`, schema/migrations, другие хроники и будущие Goals не
изменялись. Старый 27-target affected aggregate не запускался.

## READ_SET

По completion package выполнен точечный read-first аудит:

1. `PartyInvitationDelivery`, `PartyInvitationService` и оба packet handler;
2. только точные request-поля `Player` и membership methods `Party`;
3. coordinator, decision, backend, role matcher, tactics, route и state model;
4. combat external ownership и production L2J backend;
5. background acquire/commit и `PhantomSystem` composition/shutdown;
6. Party suite/launcher/build targets/verifiers;
7. локальные real DB/materialization/client integration fixtures.

Master plan и исходный Goal 017 package намеренно не перечитывались по прямому
указанию задачи. Module README и дополнительный корневой AGENTS.md не найдены.

Переиспользованы локальные паттерны:

- outbound/session seam обычного `Player`;
- canonical `PartyInvitationService` и публичные операции `Party`;
- optimistic profile component store;
- combat/background two-phase ownership claims;
- общий scheduler pulse без нового worker/thread/future.

## Changed files

Production: 18 файлов, из них 2 новых.

- invitation core: `PartyInvitationDelivery`, `PartyInvitationService`;
- party: coordinator, backend, decision, model, matcher, route, tactics и
  participation port;
- combat: actor lease, service, L2J backend и typed support action;
- background service, semantic acts и `PhantomSystem`.

Tests/tools/docs:

- `PhantomPartySuite`;
- новый `PhantomPartyServerIntegrationSuite`;
- `PhantomTestLauncher`;
- verifier 017;
- architecture contract и этот отчёт;
- шесть файлов completion package.

Итоговый scope: 30 файлов. Production <=18, new production <=3, total <=30.

Terminal micro-completion: 10 файлов, из них 3 production/config; новых
production файлов нет. Из предыдущего completion-кода меняется только
`PhantomPartyCoordinator`.

## Architecture decisions

Invitation reservation имеет состояния RESERVED/PUBLISHED/TERMINAL. `prepare`
исполняется до prompt/enqueue; все terminal paths отсоединяют оба индекса и
посылают один callback, если managed хотя бы одна сторона. Закрытие delivery
registration отменяет все принадлежащие ему входящие и исходящие invitations.

Coordinator сохраняет exact invitation sequence на leader и managed member до
публикации. Stale identity не может abort/commit новую операцию. Lifecycle
команды сначала выполняют canonical mutation, затем наблюдают postcondition и
обновляют claims.

Pulse использует `groupId -> sorted claims`, due-group queue и отдельные bounded
terminal/inbound/tactical-release queues. В pulse-path отсутствуют полные
сканирования managed state.

Maximum matcher оптимизирует required count, total score, optional count и
лексический tie-break. Exact-target support проходит с capability/variant/scope
и skill ID/level через combat ownership с повторной L2J validation.

## DB, configs, migrations

- тестовая БД: только `l2jmobiush5_phantom_test`;
- production БД `l2jmobiush5` не изменялась;
- schema/migrations отсутствуют;
- новых config keys нет;
- `PhantomPartyOperationsPerPulse` остаётся budget authority: диапазон
  `10..10000`, default 64.

## Tests

Новые dynamic проверки:

- requester-side expiry очищает оба invitation index;
- cancel/retry получает новую exact identity;
- managed requester и invitee получают prepare/terminal exactly once;
- materialized Phantom и обычный World Player образуют реальную `Party`;
- ordinary outbound session получает настоящий `AskJoinParty`;
- `party.state` записывается в MariaDB и загружается новым store adapter;
- canonical transfer/expel/leave дают реальные postconditions;
- transfer/leave claims и generation проверяются через coordinator;
- missing route member не двигает waypoint и не допускает ARRIVED;
- 64-group pulse никогда не превышает budget;
- maximum-matching counterexample заполняет обе required vacancy.
- refusal и timeout сохраняют exact ABORTED evidence, завершают старую form goal
  и делают leader SOLO reusable только для новой ACTIVE goal;
- stale decision/callback не повторяет invite и не abort-ит новую operation;
- nine-member canonical group делает progress при budget 10, budget 9 отклонён;
- population server integration ожидает terminal
  `ACTIVE + playerRetained + worldPresent`, а не раннюю publication entry.

Source-string проверки не засчитывались как integration evidence.

## Verification results

Development:

- compile/compile-tests: PASS;
- focused Party dynamic и real server integration: PASS;
- final `phantom-party-test`: PASS, 36/36.

Pre-freeze gates:

- семь exact affected regressions: PASS после единственного предусмотренного
  population preflight; первый population affected-run поймал известный
  несвязанный Goal 016 flake, точный report проверен, код не менялся;
- combat preflight: PASS, 20/20;
- population preflight: PASS, 1/1;
- verifier 016: `TASK016_VERIFIER_OK`;
- working verifier 017: `TASK017_VERIFIER_OK`, scope 30, production 18,
  new production 2.

Исторические terminal frozen-tree results baseline commit:

- terminal disposition: `PARTIAL`;
- единственный final full `ant verify`: FAIL через 12:12 только на известном
  preflight-green population flake `Materialized population entry does not retain
  a real Player`;
- разрешённый единственный exact targeted retry: FAIL с тем же симптомом; broad
  rerun и второй full verify не выполнялись;
- standalone `ant jar`: `BUILD SUCCESSFUL`, 0:17;
- два post-commit verifier 017 и их byte identity сообщаются в финальном ответе.

Terminal micro-completion gates:

- compile/compile-tests: PASS;
- focused party lifecycle: PASS, 9/9;
- config/skeleton: PASS, 13/13;
- population server integration: PASS 3/3 sequential runs, 1/1 each;
- final `phantom-party-test`: PASS, 40/40;
- verifier 016: `TASK016_VERIFIER_OK`;
- working verifier 017: `TASK017_VERIFIER_OK`, scope 10, production 3,
  new production 0;
- final frozen-tree `ant verify`: PASS, `BUILD SUCCESSFUL`, 11:55;
- standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 0:15;
- ordinary child commit/push и два byte-identical verifier выполняются
  post-report; commit SHA, push result и verifier hash сообщаются в финальном
  ответе без amend отчёта.

## Performance

Dynamic pulse fixture: 64 groups, budget 10, 100 pulses; maximum examined не
превышает budget. Party performance target дополнительно использует 100000
composite pulses, 10000 synthetic profiles и 1000 groups.

Новых threads, executors, timers, scheduled futures и per-profile workers нет.

## Deviations, limitations, risks

- Cross-profile ACID не заявляется: используется bounded optimistic saga.
- Реальный лидер остаётся observation-only для route execution.
- Геодата и fallback navigation не менялись.
- Global matchmaking, Rift, background rewards, text/LLM, clans, PvP/economy и
  Goals 018/019/020/023/025 вне scope.
- Soft usage target 650k превышен. Причина: bounded completion потребовал
  перепроверить крупный coordinator lifecycle, добавить real DB/materialized
  Party/ordinary-client fixture, выполнить тяжёлые server-integration regressions
  и отдельно диагностировать известный population flake перед code freeze.
- Soft ceiling terminal micro-completion: 250k; отсчёт ведётся отдельно от
  исторического Goal usage.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: 0;
- escaped Cyrillic в изменённых файлах проверены отдельно: 0.

## Git

Разрешённые read-only Git-команды использовались для required parent, branch,
upstream, scope inventory, exact diff и verifier guard. История не изменялась до
ordinary final commit.

- branch: `feature/phantom-world`;
- required micro parent: `0015a5ffd0c10a99514732ef52b969a39ac62eb7`;
- subject: `fix(phantoms): finalize party terminal verification`;
- commit SHA и push result сообщаются в финальном ответе: report не изменяется
  после ordinary commit и не требует amend;
- force push/amend/rebase/squash/merge: не используются.

## Next step

После полного успеха — независимый review Goal 017. Следующий Goal до принятия
gate не начинается.
