# Goal 020 Checkpoint 1 — наблюдение чата и планирование диалога

## Терминальные результаты

- Status: `PARTIAL`.
- Final checkpoint aggregate: `PASS`, единственный запуск, `BUILD SUCCESSFUL` за 1:24.
- Full `ant verify`: `PARTIAL`. Первый invocation выявил bounded-receipt regression
  в historical social clamp test; exact fix и retry прошли. Второй invocation прошёл
  всю test-матрицу, но вернул exit 1 на устаревшем exact-remote check verifier 018.
- После минимального descendant-compatible fix exact static tail 018/019/020c1: `PASS`.
  Третий full verify не запускался: лимит task-пакета исчерпан.
- Standalone `ant jar`: `PASS`, `BUILD SUCCESSFUL` за 14 секунд.
- Commit/push: выполняются после фиксации этого отчёта; SHA и remote evidence
  публикуются в финальном handoff, чтобы не создавать второй child/amend.
- Два accepted-verifier запуска: выполняются post-push; для `PARTIAL` verifier печатает
  отдельный детерминированный token и не утверждает completion.

## Summary

Реализован только заранее запланированный Checkpoint 1 Goal 020: фактическая доставка
клиентского чата наблюдается после финальной фильтрации, а bounded conversation-контур
выбирает одного наблюдателя и сохраняет только immutable response/action plans.
Ни один план не отправляется и не исполняется.

Goal 019 принят как `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; его verifier закреплён
за accepted-коммитом `384b521f2cd29f4162c9aca9116eb0ff40cbd681` и проверяет
исторические blobs на любых потомках. Goal 018 остаётся
`ACCEPT_WITH_ACTIVATION_GATE`, а требуемые activation gates закрыты тестами Goal 020.

## Graph и scope

- Ветка: `feature/phantom-world`.
- Required parent: `384b521f2cd29f4162c9aca9116eb0ff40cbd681`.
- Единственный допустимый child subject: `feat(phantoms): add conversation observation and planning`.
- Scope: 52 файла; production/data/config: 26; новых production/data: 11.
- Другие хроники, геодата, schema, `Player.java`, `Party.java` и существующие chat handlers не менялись.
- User task package из девяти файлов сохранён и включён в scope.

## Прочитанные файлы и локальные аналоги

Прочитаны обязательные master plan, workflow/task-package contracts, весь текущий task
package, accepted reports Goal 018/019, а также целевые profile/social/semantic/chat/
materialization/party/system исходники и их focused fixtures.

Переиспользованы локальные паттерны:

- profile component optimistic transactions для атомарного `social.state` + `social.receipts`;
- двухфазный `beginStop`/`finishStop` и shared scheduler pulse;
- immutable semantic generation с строгими hash/domain/slot contracts;
- bounded state codec и observer-only sink вместо action facade.

Дополнительно прочитаны точечно:

- `PhantomPartyCoordinator.java` — доказать first JOIN transition и его ordering;
- `PhantomServerShutdownHandoffSuite.java` — сохранить серверный drain order;
- `PhantomIdentityLeaseRegistry.java` — переиспользовать identity ingress lease;
- `PhantomActivityWorkSinkBridge.java` — переиспользовать общий pulse без worker на диалог;
- `build.xml` — сохранить cumulative Ant wiring;
- `verify-task-018.ps1` и `verify-task-019.ps1` — descendant-compatible verifier pattern.

Непроверенных API библиотек не использовано; новые зависимости и внешние provider-ы не добавлялись.

## Changed files

- Generic chat seam: `ChatObservationService.java`, `Say2.java`, `CreatureSay.java`.
- Conversation: семь Java-файлов под `phantoms/conversation` и два data-файла.
- Social/profile: repository atomic mutation, receipt ledger, store/service/event result.
- Semantic: strict identity/slot/pattern/budget/start-drain и fragment continuation.
- Party/system: точечный first JOIN transition, lifecycle composition и snapshot.
- Tests/build: восемь focused routes, affected/final aggregates и cumulative verify.
- Docs/tools: master status, Goal 019 review/verifier, architecture contract, task package,
  этот отчёт и `verify-task-020c1.ps1`.

## Architecture decisions

- `Say2` создаёт dispatch scope только после всех существующих фильтров и закрывает его
  в `finally` непосредственно вокруг текущего `IChatHandler.onChat`.
- Текстовый `CreatureSay` захватывает immutable dispatch descriptor при создании;
  recipient callback вызывается после штатного snoop только для реально доставленного пакета.
- Generic chat service не зависит от `phantoms`; установлен только один bounded observer.
- Conversation объединяет recipient callbacks по dispatch ID на один shared pulse, затем
  детерминированно выбирает private recipient, party leader/min profile или unique vocative.
- `conversation.state` ограничен 4096 байтами, восемью sessions, восемью recent hashes и
  четырьмя pending slots; authority generation включает semantic/social/conversation hashes.
- Clarification продолжает только ожидаемые slot families через `resolveFragment`, не выбирая
  новый intent из фрагмента.
- Три social/personality modifiers выбирают style; template selection включает catalog hash.
- Persistence выполняется до публикации immutable observer-only plan.

## Activation gates Goal 018/019

- `social.state` и `social.receipts` обновляются одной транзакцией с optimistic versions.
- Ledger хранит до 96 exact full-hash receipts; stale/out-of-order event сохраняет причинность
  и не изменяет relationship delta задним числом.
- Party JOIN публикуется ровно при первом exact `OBSERVED -> COMMITTED` переходе.
- Semantic identity, namespace slots, pattern topology и candidate budget fail closed.
- Start/drain учитывает параллельный start claim и все operations.
- Production authority test разрешает все семь grounded families через реальные
  game-knowledge/topology/party-role seams.

## DB, migrations и config

- Использовалась только `l2jmobiush5_phantom_test`; seed Goal: `20002001`.
- Production DB автоматическими тестами не изменялась.
- Schema/migrations отсутствуют; новые данные живут в существующей profile-component таблице.
- Новых config keys нет. Phantom World по-прежнему выключен существующим feature flag.

## Focused и affected test results

- `phantom-social-activation-test`: PASS, 3/3.
- `phantom-semantic-activation-test`: PASS, 3/3.
- `phantom-chat-observation-test`: PASS, 2/2.
- `phantom-conversation-catalog-codec-test`: PASS, 2/2.
- `phantom-conversation-understanding-test`: PASS, 2/2.
- `phantom-conversation-social-style-test`: PASS, 1/1.
- `phantom-conversation-chat-integration-test`: PASS, 2/2.
- `phantom-conversation-lifecycle-performance-smoke`: PASS, 3/3.
- `phantom-profile-persistence-test`: PASS, 18/18.
- `phantom-social-party-integration-test`: PASS, 3/3.
- `phantom-semantic-test`: PASS, все focused semantic sections.
- `phantom-server-shutdown-handoff-test`: PASS, 7/7.
- `phantom-conversation-checkpoint1-affected-test`: PASS.

## Performance measurements

- 100,000 generic non-managed deliveries прошли bounded ignore path без DB и plan output.
- 100,000 mixed deliveries сохранили queue/batch/cache/state bounds и максимум 32 операции/pulse.
- Worst-case receipts: `6 + 96 * 42 = 4038` байт, меньше component limit 4096.
- Worst-case conversation state codec подтверждён focused boundary tests на 4096 байтах.
- Отдельный thread/executor/future для фантома или диалога не создаётся.

## Commands

- Восемь exact focused Ant targets запускались отдельно; после lifecycle-hardening повторены
  chat observation, lifecycle/performance, shutdown handoff и semantic activation.
- Выполнен exact `phantom-conversation-checkpoint1-affected-test`.
- `powershell -File tools/phantoms/verify-task-019.ps1` выполнен до production edits.
- Terminal aggregate/verify/jar и post-commit verifier evidence находятся только в
  разделе «Терминальные результаты».

Git-команды использовались, потому что task прямо требует graph/scope guard, commit и push:
`git status`, `git branch --show-current`, `git rev-parse`, `git diff --name-status`,
`git diff --name-only`, `git diff --check`, `git ls-files --others`, а verifier-скрипты
дополнительно используют read-only `git show`, `git log`, `git merge-base` и `git ls-tree`.
История ещё не изменялась на момент формирования основной части отчёта.

## Deviations и bounded exceptions

- `PhantomSocialEventSink.java` изменён для обязательного structured `STALE`, хотя literal
  allowlist перечислял social store/service/receipt family; без этого causality не выражается.
- `PhantomSemanticGrounding.java` изменён для canonical uppercase production authority hashes
  и real authority gate; файл был в обязательном READ_SET, но не в literal existing allowlist.
- `PhantomMaterializationService.java` получил разрешённый task-пакетом узкий read-only accessor.
- Три managed-profile corpus fixtures переведены на строгий numeric `profile:<id>` identity.
- `verify-task-018.ps1` получил минимальный descendant-compatible remote ancestry check:
  cumulative `ant verify` доказал, что старое exact-remote условие уже ложно на accepted Goal 019.
- Scope остаётся внутри всех трёх жёстких лимитов; архитектурное расширение не вводилось.

## Limitations, risks и next step

- Checkpoint 1 намеренно не создаёт outbound `CreatureSay`, goals или action execution.
- Party/movement/combat/trade/inventory действия не вызываются и не резервируются.
- Plan sink хранит только bounded counters/last immutable plan; это не очередь исполнения.
- Callback отражает `ServerPacket.runImpl(Player)` delivery; сетевое подтверждение клиента
  протоколом High Five не существует и не имитируется.
- Goal 020 Checkpoint 2, action/outbound delivery и Goal 021 остаются `NOT_STARTED`.
- Следующий шаг — независимый review этого checkpoint; до принятия gate новый slice не начинать.
