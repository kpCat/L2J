# Контракт наблюдения чата и планирования разговора

## Статус и граница

Goal 020 Checkpoint 1 реализует только наблюдение фактической доставки чата и
построение observer-only планов. Он не отправляет `CreatureSay`, не исполняет
semantic proposal, не создаёт gameplay goal и не меняет party, movement,
combat, trade или inventory.

Каждый `ConversationActionProposal` имеет
`authorization = CHECKPOINT_2_REQUIRED`. Значение `ACCEPTED` у semantic result
означает допустимость структурированного плана, но не разрешение на действие.

Goal 020 action/outbound checkpoint остаётся `NOT_STARTED`.

## Источник наблюдения

`ChatObservationService` — generic non-Phantom seam в `model.chat`. Он не
зависит от Phantom-кода и хранит только один registration, `ThreadLocal`
dispatch scope и фиксированные агрегированные метрики.

Для клиентского сообщения порядок такой:

1. `Say2` выполняет штатные проверки и фильтры.
2. Непосредственно перед вызовом финального `IChatHandler` открывается scope с
   immutable primitives и origin `CLIENT_CHAT`.
3. Созданный внутри scope текстовый `CreatureSay` захватывает descriptor только
   при точном совпадении sender, channel и уже отфильтрованного text.
4. `CreatureSay.runImpl(Player)` сначала выполняет штатный `broadcastSnoop`, а
   затем публикует callback для фактического recipient.
5. Scope закрывается в `finally`.

Packet, `Player`, handler и `World` не сохраняются в observation. NPC/system
конструкторы `CreatureSay` не участвуют. Зарезервированный origin
`PHANTOM_GENERATED` в этом checkpoint не публикуется и не принимается.

Nested и mismatched scope fail closed. Detach registration запрещает новые
claims и дожидается уже начавшихся callbacks, в том числе при interrupt с
последующим восстановлением interrupt flag.

## Ingress и shared pulse

Callback conversation service выполняет только:

- проверку origin и поддерживаемого channel;
- проверку `PHANTOM` ownership фактического recipient через
  `PhantomIdentityLeaseRegistry`;
- копирование bounded immutable observation в `ArrayBlockingQueue`.

На delivery thread нет semantic/social/context/DB вызовов.

Существующий общий scheduler управляет обработкой. Новые thread, executor,
Future и scheduled task не создаются. Каждый pulse имеет максимум 32 учтённых
операции. Poll и batch mutation резервируются вместе, поэтому исчерпание budget
не теряет уже извлечённую queue entry.

Новый dispatch ждёт минимум следующий shared pulse. Recipient observations
агрегируются по dispatch ID; несовпадающий descriptor или overflow даёт ноль
планов.

Election выполняется максимум один раз:

- `WHISPER`: единственный фактически доставленный managed recipient;
- `PARTY`: managed canonical leader среди actual recipients, иначе минимальный
  positive profile ID;
- `GENERAL`/`TRADE`: единственный exact display-name vocative с ограниченной
  ведущей или хвостовой punctuation form.

Нечёткое address election запрещено. Сообщение от того же managed profile
игнорируется.

## Immutable context

`L2jPhantomConversationContextPort` получает elected observer через штатный
materialization action lease, копирует только immutable значения и освобождает
lease до возврата. Контекст содержит exact profile/object identity speaker,
channel, canonical Party leader/members, speaker как единственный chat-derived
nearby/recent candidate, exact topology node и предыдущие принятые slots.

Conversation core не владеет `Player`, `Party`, packet, DB connection или
mutable world object. Если lease или exact recipient mapping уже неактуальны,
observation отбрасывается без записи.

## Semantic continuation

Обычный ход вызывает `understand(text, context)`. При live pending
clarification complete new intent имеет приоритет; иначе вызывается bounded
`resolveFragment` только для missing slot set. Совмещаются лишь совместимые
typed slots, а исходные pack/corpus/knowledge/topology/role hashes сохраняются.

Expiry или authority drift уничтожают право продолжать старую интерпретацию.
`REJECTED` никогда не создаёт proposal. Candidate-budget exhaustion даёт
`clarify.complexity`, а не partial winner.

## Social phrasing

Conversation читает только:

- `conversation.warmth`;
- `conflict.escalation`;
- `party.invite.preference`.

Они выбирают только style/template и suppression acknowledgement. Они не могут
изменить intent, target, slots, acceptance или authorization. Ошибка social
даёт neutral style и фиксированную метрику.

Шаблон выбирается детерминированно из owner profile ID, observation hash,
response act, style и conversation catalog hash. Текст берётся только из
strict XML, не превышает 100 code points и 400 UTF-8 bytes, не содержит control,
item-link byte 8 или markup и никогда не отправляется.

## Durable state и plan boundary

`conversation.state`, schema version 1, использует существующую таблицу profile
components без migration. Compact codec fail closed для version, order,
duplicate, range, truncated и trailing data. Payload ограничен 4096 bytes.

State содержит authority hashes, monotonic logical minute, максимум восемь
sessions и восемь observation hashes. Raw chat и rendered response не
сохраняются.

Conversation service — единственный writer. Используются 64 fixed stripes,
optimistic reload/retry максимум три и bounded access-order cache. Callback
semantic/social/context/plan не выполняется под profile stripe lock.

State становится durable до `PhantomConversationPlanSink.publish`. Production
sink считает только агрегированные plans/proposals. Он не имеет send/action
методов. Plan-sink failure виден в метрике и не меняет gameplay.

## Activation contracts Goal 018/019

`social.state` и `social.receipts` изменяются одной JDBC transaction через
generic sorted multi-component mutation. Ledger хранит максимум 96 exact
APPLIED/STALE receipts и укладывается в 4096 bytes. Expired event получает
durable STALE receipt без emotional/agreement delta; live late event применяет
aged delta, не двигая logical time назад.

`party.member.joined` допускается только для подтверждённого первого перехода
одной exact JOIN identity `CANONICAL_OBSERVED → COMMITTED`. Pending operation не
может быть закоммичена раньше terminal observation; stable pulse, restart,
manifest/role/route refresh повторно событие не создают.

Semantic identity принимает positive decimal `profile`/`character.object`
границы, slot namespace обязан соответствовать SlotType, duplicate slot types и
unsafe slot-first/adjacent/duplicate/oversized patterns fail closed. Production
grounding canonicalizes валидные SHA-256 authority hashes и проверяется на
реальных topology/Game Knowledge/party-role sources.

## Lifecycle

Startup загружает conversation data и регистрирует observer только после
social, semantic и Party authorities, но до старта shared scheduler. Disabled
Phantom World не загружает conversation/social/semantic файлы и не касается
conversation DB.

Shutdown сначала detach/drain conversation registration, queue, pulse и
persistence claims; затем останавливает Party, social и semantic authorities.
Snapshots `PhantomSystem` и configured shutdown evidence показывают bounded
conversation и generic chat state.
