# Goal 023A — Rift production integration corrections

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Execution gate: `COMPLETED`; ожидается независимое review.

## Summary

Corrective Goal 023A закрывает production blockers independent review Goal 023 при сохранении factual Rift catalog/readiness и side-effect-free entry seam. Required parent: `840e159a989f6372da9c471c915413f1e4470daf`; branch: `feature/phantom-world`; required commit subject: `fix(phantoms): harden rift recruitment integration`; deterministic seed: `23002311`.

Goal 023 baseline зафиксирован как `CHANGES_REQUIRED`. Goal 024+ не начат.

## Read-first audit и локальные аналоги

Прочитаны обязательные master/roadmap/workflow/task/report документы, весь supplied Goal 023A package, Goal 023 contract/report, текущие Rift production/test seams и точные Goal 017/018/020 integration points. README в рабочем модуле, code-map и отдельные pattern-файлы не найдены; повторный поиск не выполнялся.

Переиспользованы локальные паттерны:

- Goal 017 `PhantomPartyCoordinator` как единственный владелец party claims/saga;
- canonical `PartyInvitationService` identity, expiry и terminal outcomes;
- Goal 018 read-only social query/evidence;
- Goal 020 typed conversation facts;
- profile-component optimistic persistence и bounded codec;
- existing shared Decision pulse без новых workers.

Непроверенным до terminal gates остаётся только итоговый aggregate/plain verify/jar/post-commit verifier sequence; он будет зафиксирован ниже после выполнения.

## Findings closure

| Finding | Реализация | Evidence |
|---|---|---|
| R023A-01 | Goal017-owned `bindContentGoal`/`observeContentBinding` принимает exact уже существующую canonical mixed Party, сохраняет group/generation/revision/manifest и не пересоздаёт её. | `rift023a-party-binding`, production integration 05 |
| R023A-02 | Target-side policy `ACCEPT/REFUSE/DEFER`, explicit conversation/join precedence, default non-accepting; ordinary real Player не управляется policy. | `rift023a-managed-consent`, Goal017/020 regressions |
| R023A-03 | Перед invite повторно читаются preparation/goal/source, exact roster/vacancy/candidate facts/claim и binding. | `rift023a-preinvite-revalidation` |
| R023A-04 | `rift.preparation` schema v2 хранит binding/candidate/invitation receipts; v1 decode разрешён, но помечается untrusted и replans до mutation. | `rift023a-restart-migration` |
| R023A-05 | Policy default 15000 ms; effective timeout ограничен canonical expiry; terminal mapping exact typed. | `rift023a-invitation-authority` |
| R023A-06 | Route/READY требуют exact stable binding и отсутствие conflict/pending; Goal020 получает `RIFT_INVITE_REQUEST`/`RIFT_INVITE_REFUSED`. | `rift023a-semantic-facts`, `rift023a-route-binding` |
| R023A-07 | Phantom-first discovery применяется до cap 32; relationship modifier берётся из Goal018 либо нейтральный typed fallback. | `rift023a-candidate-ordering` |
| R023A-08 | Добавлены real production-seam acceptance test, девять modes, exact affected aggregate, полный bounded metric set и status docs. | final aggregate/verifiers |

## Production call flow

До исправления: `rift.prepare -> ensureFormation -> candidate snapshot -> invite`, при этом существующая Party могла завершаться `CLAIM_EXISTS`, consent target-а не был production-owned, а restart receipt был sequence-only.

После исправления: `readiness snapshot -> ENSURE_PARTY_BINDING -> persist exact binding -> pre-invite full revalidation -> Goal017 canonical invite -> target-side managed consent -> persist full invitation identity/expiry -> reconcile exact terminal -> stable binding recheck -> route -> READY_TO_ENTER`. Последний статус остаётся read-only и не вызывает Rift entry/mutation.

## State matrices

Binding:

| Состояние | Действие |
|---|---|
| exact canonical roster + exact goal, stable | adopt/idempotent, invite/route разрешены |
| pending membership/route operation | `PENDING`, mutation/READY запрещены |
| conflicting claim/roster/generation/manifest | `CONFLICT`, fresh snapshot/replan |
| v1 receipt | legacy-untrusted, replan до mutation |

Consent precedence:

| Target/context | Результат |
|---|---|
| explicit conversation response или exact join goal владеет accept | `DEFER` policy; explicit path authoritative |
| managed Phantom + exact Rift offer + eligible | `ACCEPT` |
| managed Phantom + exact refusal policy | `REFUSE` |
| stale/missing/conflicting evidence | `DEFER` |
| ordinary real Player | policy `UNSUPPORTED`, auto-accept отсутствует |

Terminal mapping:

| Canonical reason | Rift status |
|---|---|
| `party.invite.refused` | `REFUSED` |
| `party.invite.expired` | `EXPIRED` |
| `party.invite.cancelled` / delivery closed | `CANCELLED` |
| committed/observed membership | `ACCEPTED` |
| unknown exact rejection | `REJECTED` |

## Persistence and bounds

Schema v2 добавлена в существующий profile component, без SQL/migration. Full invitation receipt содержит sequence, requester object id, invitee object id и canonical expiry. Payload/history bounds сохранены. Candidate evaluation capped at 32; performance mode выполняет 100000 binding/model operations и 10000 candidate/restart operations. Metrics имеют фиксированную cardinality: readiness dimensions, accepted/refused/expired, candidate rejected, roster/source stale и binding conflicts.

## Changed files

Production/data (13):

- `PhantomSystem.java`;
- `L2jPhantomConversationExecutionPort.java`;
- `PhantomPartyCoordinator.java`;
- Rift: `L2jPhantomRiftBackend.java`, `L2jPhantomRiftPartyPort.java`, `PhantomRiftBackend.java`, `PhantomRiftMetrics.java`, `PhantomRiftModel.java`, `PhantomRiftReadinessService.java`, `PhantomRiftService.java`, `PhantomRiftStateCodec.java`, `PhantomRiftStore.java`;
- `dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml`.

Tests/build/tools (6): `PhantomPartyServerIntegrationSuite.java`, `PhantomRiftSuite.java`, new `PhantomRiftCorrectionsSuite.java`, `PhantomTestLauncher.java`, `build.xml`, new `verify-task-023a.ps1`.

Docs/package (13): восемь supplied task-package файлов, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/PHANTOM_BOTS_ROADMAP.md`, reviews 023/023A и этот report. Total exact scope: 32; new production/data: 0; changed production/data/config: 13; new test files: 1; new SQL: 0; other chronicles: 0.

## Commands and test results

Development evidence before final freeze:

- nine focused modes, seed 23002311: PASS; candidate-ordering был один раз повторён после исправления test-only false-positive assertion на Javadoc token;
- original `phantom-rift-goal023-test`: PASS после одного test-backend compatibility correction; historical verifier 023 PASS;
- exact Goal017/020 affected aggregate: BUILD SUCCESSFUL, 36 s; canonical invitation 6/6, recovery 6/6, roles 6/6, route 5/5, lifecycle 11/11, server integration 5/5, conversation actions 7/7, query 3/3;
- acceptance case 05 использует real headless-materialized `Player`, real `PhantomPartyCoordinator`, `L2jPhantomRiftPartyPort` и canonical `PartyInvitationService`; fake `GameClient` отсутствует.

- единственный final `ant phantom-rift-goal023a-test`: `BUILD SUCCESSFUL`, 1 min 13 s;
- freeze manifest: 19 exact source/test/build/verifier файлов;
- единственный plain `ant verify`: `BUILD SUCCESSFUL`, 17 min 56 s; intentional negative-control Java exit codes ожидаемы и aggregate зелёный;
- standalone `ant jar`: `BUILD SUCCESSFUL`, 17 s;
- post-verify/jar freeze comparison: `GOAL023A_FREEZE_UNCHANGED`;
- mojibake-маркеры в 32 изменённых файлах проверены отдельно: 0 совпадений;
- escaped Cyrillic в изменённых user-facing строках проверены отдельно: 0 совпадений; два raw technical prefix совпадения находятся только в regex самого verifier-а;
- historical verifier 023 и working-tree verifier 023A: PASS; `scope=32`, `changed_production_data=13`, `new_production_data=0`.

PowerShell 5.1 и проверенный локальный PowerShell 7 запускают post-commit verifier; требование — byte-identical stdout. Фактические SHA-256/stdout сообщаются в финальном handoff, потому что commit, содержащий этот отчёт, ещё не существует и self-referential SHA нельзя записать в тот же ordinary commit.

## Source freeze hashes

```text
D77AEFB9112A9EC00AD7056A5DB0E8E7B7057F8056CD72619A421CA907F8F6E2  build.xml
F57AA8CC9790552B34A7FBA1941825D6BD956B5BCDAEC373F3A317EF7EC5BCE5  dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml
D5D1F1DB9B59554C043DB50D7A3A1B0640E38F5B0B4858E9B0829B644732ED21  java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
4E5020E76E4048D4EC3EB92956D94A400B18089888DF15D1CC9AE8B228A05306  java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java
9BA46468A6DB828379E3443A49FBCA186E04D7817574E2FAFCBB7DB626851412  java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
A93AA79A3784C3DF62660C05994ABEE40E058F0AC262F634B3379202B271A2E1  java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftBackend.java
8ACA92462B9A0072B8B75225728811A034D72EDFE38E42873476C5C5554F4683  java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java
441A9502EB7566AC624AE369E307BE91ACCB842541357EDE926A5D152196C432  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftBackend.java
C5176694615DFFC2B56D4F657FC790D9FE94297DCDE54F6CAECD5DD46861C92D  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftMetrics.java
D073C919771652536C97F1DE91D8FABAFCC03124CE026D9700025D3A5C83F7D2  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftModel.java
65F9FD1A13D40885BB8D3D58DBC775B8893AD548B1F903476B290702B2B664B8  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftReadinessService.java
C8BA30B061F9BD79654049B6584078A6FFC7B9164CE63F1D96A919305368D52E  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java
980F647FA0A0C938B4F855EDC5F2AA97BCE0F015081B5EA674E058B98E816222  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftStateCodec.java
DAF52696F9256A671494479E8085F6ADCAE2FC862FDC41C5146BB463957B7205  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftStore.java
BE63C52AF59FE286867A89E0CC4759E06323ADEEF554DC5519FA2310E1509A0E  test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java
61ABFDAA3C1CBD0F2E912FB5DCF0C5FBD477A45AE70D69C2A4AA03AD28224F8C  test/java/org/l2jmobius/tests/phantoms/PhantomRiftSuite.java
2AEEC38E95DF2148A0B79DFBE59A268E89C96610D0BB2372883F892B891832C6  test/java/org/l2jmobius/tests/phantoms/PhantomRiftCorrectionsSuite.java
B7299DB68AB79ADBFF15066C13D34343665BCB4B9BAAE6C9BC1653394724069C  test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
AD60852366F45AB149428D88E104E84C1DB07DB5294B29D8640071AB55B60AD1  tools/phantoms/verify-task-023a.ps1
```

## Database, configuration and side effects

Production DB `l2jmobiush5` не изменялась. Acceptance server integration использовала существующий test DB guard/fixture. SQL, geodata, other chronicles, Rift entry, item consume, teleport/room/spawn/combat mutations не добавлены. Новых config keys нет; существующий `inviteTimeoutMillis` изменён с 30000 на 15000.

## Deviations

`apply_patch` не смог читать workspace из-за Windows sandbox ACL (`helper_unknown_error`); после обязательной попытки применены exact-string UTF-8 edits через PowerShell с проверкой единственного совпадения. Архитектурный scope не расширялся.

## Known limitations and risks

- Goal 023/023A не является ACCEPT до независимого review.
- `READY_TO_ENTER` по-прежнему preparation-only; реальный Rift entry намеренно вне scope.
- Геодата отсутствует и не проверялась, навигационный runtime gate остаётся прежним.
- Goal 024+ не начат.

## Git

- Branch: `feature/phantom-world`.
- Parent: `840e159a989f6372da9c471c915413f1e4470daf`.
- Commit SHA/push/remote equality: создаются после включения отчёта в единственный ordinary commit; результат будет дан в финальном handoff без второго self-referential commit.
- Required subject: `fix(phantoms): harden rift recruitment integration`.

## Recommended next step

Только независимое review Goal 023A. Goal 024 не начинать.