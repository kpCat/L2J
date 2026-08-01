# Goal 020 Checkpoint 1 — наблюдение чата и планирование диалога

## Статус

- Status: `SUCCESS`.
- Implementation commit: `e7ba469e63caa6dee113278087258fab005a435a`.
- Implementation parent: `384b521f2cd29f4162c9aca9116eb0ff40cbd681`.
- Implementation subject: `feat(phantoms): add conversation observation and planning`.
- Единственный разрешённый completion child subject:
  `fix(phantoms): complete conversation planning safety`.
- Ветка: `feature/phantom-world`.
- Seed: `20002001`.

## Результат completion

Checkpoint 1 завершён по bounded completion authority без переписывания
implementation commit. Наблюдение начинается после финальной фильтрации `Say2`,
`CreatureSay.runImpl(Player)` сообщает фактического получателя, а синхронный
`DISPATCH_CLOSED` закрывает recipient set до observer election.

Conversation planner ничего не отправляет и не исполняет. Он не создаёт outbound
`CreatureSay`, goal, party/movement/combat/trade/inventory действие. Результат —
только immutable observer-only response/action plan с
`CHECKPOINT_2_REQUIRED`.

## Доказанная delivery boundary

Точечно прочитаны `ServerPacket.runImpl`, `Player.sendPacket`, client-bound
`PlayerOutboundSession`, `GameClient.sendPacket`, а также General, Whisper, Party
и Trade handler paths. Все целевые send/broadcast обходы синхронны, а
`GameClient.sendPacket` вызывает `packet.runImpl(_player)` до возврата. Поэтому
`Say2`-scope закрывается после всех штатных recipient callbacks. Late delivery
после CLOSED учитывается как mismatch и не создаёт batch.

Невалидный/null/oversized final-filtered input создаёт inert scope с фиксированной
rejection-метрикой. Обычный handler всё равно вызывается ровно один раз. Callback
исключения изолированы, close идемпотентен.

## Resumable shared-pulse planner

Batch остаётся owned до `DONE`/`FAILED` и проходит фазы:

```text
COLLECTING, RESOLVING_OBSERVERS, ELECTING, LOADING_STATE,
BUILDING_CONTEXT, UNDERSTANDING, READING_SOCIAL,
PERSISTING, PUBLISHING, DONE, FAILED
```

Pulse ownership использует CAS `AtomicBoolean`. Bounded delayed/due queue и
membership set заменили полный scan `_batches`. После каждого operation batch
возвращается в хвост due queue, сохраняя cursor/generation/phase. Ни один внешний
context/materialization/topology/semantic/social/store/plan callback не выполняется
под index monitor.

Операции считаются точно: каждый observer lookup, election, load, context build,
semantic call, каждый из трёх social modifiers, persistence и publication.
Budget exhaustion не повторяет завершённую boundary и не теряет dispatch.

## Persistence и authority

Persistence outcomes типизированы как `SAVED`, `DUPLICATE`, `FAILED` и
`AUTHORITY_STALE`; publication разрешена только после `SAVED`. Optimistic conflict
перезагружает exact state отдельной budgeted операцией. Если observation уже
сохранён, losing worker завершает `DUPLICATE` без rewrite и второго plan.

Отсутствующий `conversation.state` создаётся. Существующий state с несовпадающей
authority generation завершается `AUTHORITY_STALE`: реальный DB payload и row
version не меняются, session/recent state не сбрасывается, plan/proposal не
публикуется. `recentObservationHashes` хранится oldest-to-newest; duplicate
отклоняется, eviction удаляет oldest, codec/restart сохраняют порядок без
лексической сортировки.

## Activation gates Goal 018/019

Принятая foundation из implementation commit сохранена без изменений:

- atomic `social.state` + `social.receipts`;
- stale/out-of-order causality;
- first exact JOIN emission;
- strict semantic identity/slot/pattern/budget/start-drain;
- real production authority test.

Goal 019 остаётся `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; verifier 019 остаётся
historical/descendant-compatible. Verifier 018 сохраняет descendant-compatible
remote ancestry.

## Scope

- Cumulative Goal 019 parent → final tree: 53 файла.
- Cumulative production/data/config: 26.
- Cumulative new production/data: 11.
- Completion scope: 11 файлов.
- Completion production: 4 файла.
- Completion new production/data: 0.
- Не менялись data, schema, social/semantic foundation, `Player.java`, `Party.java`,
  existing chat handlers и другие хроники.

Completion production files:

- `java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java`;
- `java/org/l2jmobius/gameserver/network/clientpackets/Say2.java`;
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java`;
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationModel.java`.

Tests/docs/tooling:

- `PhantomChatObservationSuite.java`;
- `PhantomConversationSuite.java`;
- `PhantomConversationIntegrationSuite.java`;
- `verify-task-020c1.ps1`;
- текущий `ARCHITECTURE.md`;
- `docs/phantoms/reviews/020-checkpoint-1-independent-review.md`;
- этот отчёт.

## Focused verification

- `ant compile`: PASS после одного исправления arity нового Snapshot record.
- `phantom-chat-observation-test`: PASS, 2/2.
- `phantom-conversation-catalog-codec-test`: PASS, 2/2.
- `phantom-conversation-understanding-test`: PASS, 2/2.
- `phantom-conversation-social-style-test`: PASS, 1/1.
- `phantom-conversation-chat-integration-test`: PASS, 5/5.
- `phantom-conversation-lifecycle-performance-smoke`: PASS, 4/4.
- `phantom-social-activation-test`: PASS, 3/3.
- `phantom-semantic-activation-test`: PASS, 3/3.
- `phantom-server-shutdown-handoff-test`: PASS, 7/7.

Focused regressions подтверждают 32 recipients на нескольких pulses, exhaustion
на каждой фазе, 256 batches без scan, interleaved A/B без смешивания, запрет
election до CLOSED, отсутствие внешнего callback под monitor, read-only authority
drift, inert invalid seam, optimistic duplicate, temporal codec/restart, shutdown
во всех operational phases и отсутствие outbound/actions.

## Terminal verification

- Verifier 018: PASS, `TASK018_VERIFIER_OK`.
- Verifier 019: PASS, `TASK019_VERIFIER_OK`.
- Verifier 020c1 precommit: PASS, `TASK020C1_VERIFIER_OK`, working mode,
  cumulative scope `53/26/11`, completion scope `11/4/0`.
- Final checkpoint aggregate: PASS, `BUILD SUCCESSFUL`, 1 минута 12 секунд.
- Единственный дополнительный full `ant verify`: PASS, `BUILD SUCCESSFUL`,
  13 минут 6 секунд. Второй full verify не запускался.
- Standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 13 секунд.
- Ordinary completion commit/push: `PENDING`.
- Два byte-identical accepted verifier 020c1: `PENDING`.

## DB, performance и ограничения

Использовалась только `l2jmobiush5_phantom_test`. Production DB не изменялась.
Schema/migrations/config keys отсутствуют. 256-batch regression сохранил bounds:
ingress 1024, batches 256, observers 32, operations/pulse 32. Отдельный worker,
executor, future или thread на conversation/phantom не добавлен.

Checkpoint 2, outbound/action execution, Goal 021 и Goal 025 не начинались.
Следующий шаг после успешного terminal evidence — только независимый review.

## Git

Разрешённые task authority git-команды используются для graph/scope/diff guards,
ordinary commit и push. Amend/rebase/squash/merge/force/force-with-lease не
используются. Completion commit — ровно один direct child, содержащий этот отчёт;
его exact SHA и remote evidence публикуются post-commit verifier и финальным
handoff без amend или второго commit.
