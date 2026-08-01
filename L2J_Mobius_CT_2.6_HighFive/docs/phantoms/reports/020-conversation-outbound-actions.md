# Goal 020 Checkpoint 2 — исходящие ответы и действия

## Статус и граф

- Status: `SUCCESS` для реализованного scope; terminal gate status указан ниже.
- Required parent: `21ba300fc612f9777891912f80efc633f5b6db18`.
- Checkpoint 1 implementation: `e7ba469e63caa6dee113278087258fab005a435a`.
- Subject: `feat(phantoms): activate conversation responses and actions`.
- Branch: `feature/phantom-world`.
- Seed: `20002002`.
- Commit SHA: self, exact SHA публикуется post-commit verifier без amend.

## Результат

Checkpoint 1 зафиксирован review-решением `ACCEPT_WITH_ACTIVATION_GATE`, а
verifier 020c1 закреплён на принятом дереве. PHANTOM-only fast path теперь
отбрасывает real/generated recipients до queue/batch/context/DB. Delayed
promotion и overflow terminalization имеют точный bounded lifecycle.

Planner атомарно сохраняет `conversation.state` вместе с PREPARED entry нового
`conversation.execution`. Execution service работает в общем scheduler, считает
каждую внешнюю/durable boundary и восстанавливается только страницами component
type до 256 rows.

Строгий content-addressed XML определяет 13 proposal contracts, Goal mappings,
channels/slots/targets, templates, TTL, retry и hard bounds. Query читает текущие
Game Knowledge/topology/Party facts. Invite/leave/travel создают только
conversation-owned Goal; busy Goal не заменяется. ACCEPT/REFUSE использует exact
pending invitation и canonical Party response. Support/assist/regroup остаются
`DEFERRED` до Goal 024.

WHISPER/PARTY/GENERAL/TRADE отправляются через текущий `IChatHandler` под
`Origin.PHANTOM_GENERATED`. До handler call сохраняется `DISPATCHING`; restart
переводит его в `UNCERTAIN` без resend. Generated callbacks не входят в planner.

## Архитектурные решения

- Durable component — единственная истина; plan sink является только wake signal.
- Full plan ID и terminal receipts сохраняют at-most-once внутри replay horizon.
  При 16 live receipts новый terminal entry не вытесняет старый, а создаёт
  безопасный capacity backpressure.
- Goal ID детерминирован из plan/profile/proposal; ownership требует purpose,
  reason и четыре части plan hash.
- Исчезнувшая после restart ACCEPT invitation не повторяется: результат
  `UNCERTAIN`, ACTIVE Goal не отменяется вслепую.
- Query facts ограничены до 128 UTF-8 байт до применения русского data-template.
- Player/Party/chat handlers/schema не изменялись; fake GameClient отсутствует.

## Scope

- New production/data: 8 (limit 18).
- Changed production/data/config: 16 (limit 34).
- Total: 37 (limit 60).
- Schema/migration/config keys: отсутствуют.
- Production DB: не использовалась; только `l2jmobiush5_phantom_test`.

Новые production/data:

- `high-five-ru-conversation-execution-v1.xml`;
- `L2jPhantomConversationExecutionPort.java`;
- `PhantomConversationExecutionCatalog.java`;
- `PhantomConversationExecutionCodec.java`;
- `PhantomConversationExecutionModel.java`;
- `PhantomConversationExecutionPort.java`;
- `PhantomConversationExecutionService.java`;
- `PhantomConversationExecutionStore.java`.

## Read-first evidence

Обязательные task/master/workflow документы, Checkpoint 1 tree/verifier, точные
chat/conversation/Goal/Party/Knowledge/topology/materialization/system symbols и
локальные тестовые аналоги прочитаны до изменений. README в module root и
`docs/phantoms/CONTEXT_INDEX.md` отсутствуют.

Дополнительные exact files/symbols сверх initial READ_SET (12):

1. `PartyInvitationService.java` — canonical response outcome.
2. `PartyInvitationDelivery.java` — exact managed identity/terminal callback.
3. `PhantomPartyModel.java` — generation, role requirements и Goal evidence.
4. `PhantomGameKnowledgeModel.java` — accepted content/source fact shapes.
5. `build.xml` — локальный test/verifier wiring.
6. `PhantomTestLauncher.java` — focused-mode dispatch pattern.
7. `verify-task-020c1.ps1` — descendant-compatible verifier pattern.
8. Checkpoint 1 report — terminal evidence/report style.
9. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` — статусы и архитектурные границы.
10. `docs/PHANTOM_BOTS_ROADMAP.md` — roadmap status boundary.
11. `PhantomConversationExecutionCodec.java` после реализации — worst-case audit.
12. `PhantomConversationExecutionService.java` после реализации — второй
    read-only budget/restart audit.

Локально переиспользованы sorted multi-component transaction, goal.runtime
optimistic store, shared composite scheduler, materialization action lease,
canonical Party invitation service, immutable Game Knowledge/topology queries и
зарегистрированные chat handlers.

## Focused verification

- compile-tests: PASS, 2108 production + 79 test sources.
- conversation-managed-ingress: PASS, 4/4.
- conversation-execution-catalog-codec: PASS, 3/3.
- conversation-handoff-durability: PASS, 2/2.
- conversation-query-execution: PASS, 3/3.
- conversation-party-actions: PASS, 4/4; canonical invitation 4/4.
- conversation-outbound-chat: PASS, 1/1.
- conversation-restart-idempotency: PASS, 2/2.
- conversation-execution-lifecycle-performance: PASS, 2/2.

Evidence включает 100 000 real callbacks без offer/context, один addressed
Phantom среди 100 real recipients, lease drift, dual-drop overflow cleanup,
10 000 durable codec records/signals, exact phase shutdown, replay receipt
capacity, DISPATCHING restart, четыре generated channels и отсутствие Goal writes
из query.

## Ограничения и риски

- High Five не подтверждает client delivery; `DISPATCHING` после crash навсегда
  становится `UNCERTAIN`, поэтому возможна потеря, но не автоматический duplicate.
- Receipt capacity намеренно может остановить новые планы одного профиля внутри
  replay horizon.
- Query сообщает только существующие bounded facts и не обещает route/purchase.
- Tactical support/assist/regroup остаётся вне scope до Goal 024.
- Независимый review Checkpoint 2 ещё не выполнен.

## Terminal results — изменяется после freeze

- Checkpoint status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Affected aggregate: PASS после исправления двух выявленных Party regression
  fixtures; финальный прогон `BUILD SUCCESSFUL`, 1:49.
- Verifier 020c1: `TASK020C1_VERIFIER_OK`, accepted tree завершён на
  `21ba300fc612f9777891912f80efc633f5b6db18`.
- Verifier 020c2 working: `TASK020C2_VERIFIER_OK`; scope 37, production 16,
  new production 8, policy SHA-256
  `F337EFA8D717B881232E9A154BC47F6277DB77E0D1129624D17809DC394F656F`.
- Единственный final checkpoint2 aggregate: PASS, `BUILD SUCCESSFUL`, 0:48.
- Единственный full `ant verify`: PASS, `BUILD SUCCESSFUL`, 13:00. Intentional
  negative harness controls напечатали ожидаемые локальные FAIL/Java Result.
- Standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 0:14; рабочие JAR скопированы
  в `dist/libs`.
- Mojibake-маркеры в изменённых файлах проверены: PASS, 37 files.
- Escaped Cyrillic в изменённых файлах проверены: PASS, 37 files.
- Ordinary commit/push: post-commit evidence, без amend.
- Два byte-identical accepted verifier 020c2: post-commit evidence.

## Git

Git использован только по task workflow для branch/parent/upstream, status/scope,
historical blobs/verifiers, diff guard, ordinary commit и push. Прямые команды:
`git rev-parse --show-toplevel`, `git status --short`, `git branch --show-current`,
`git rev-parse HEAD`, `git rev-parse --abbrev-ref --symbolic-full-name '@{u}'`,
`git diff --name-status`, `git ls-files --others --exclude-standard`,
`git diff --check`, `git diff --stat`, `git diff`, а после terminal audit —
`git add`, `git diff --cached --check`, `git diff --cached --name-status`,
`git diff --cached --stat`, `git diff --cached`, `git commit -m` и
`git push origin feature/phantom-world`. Verifier 020c1/020c2 дополнительно
использует собственные bounded read-only Git-команды для provenance/scope/blob/
ancestry/remote checks. Amend, rebase, squash, merge, reset, restore, force и
force-with-lease не использовались.

Следующий шаг после terminal gates — только независимый review; Goal 021/025 не
начат.
