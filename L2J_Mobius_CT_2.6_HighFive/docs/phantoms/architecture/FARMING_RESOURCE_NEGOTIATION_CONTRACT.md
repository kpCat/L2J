# Farming Resource Negotiation Contract

## Статус и граница

Goal 024 добавляет только координацию конфликтующих farming-ресурсов между Phantom-профилями. Goal 021 остаётся единственным владельцем acquisition source, progress, remaining и переключения источника. Goal 010 остаётся владельцем topology/perceptibility, Goal 017 — Party state, Goal 018 — долговременной социальной памяти и истории, Goal 020 — языка и chat/query исполнения. Goal 025 не начат; `ESCALATE` является только semantic/social evidence и не запускает PvP, combat или navigation.

## Идентичность ресурса

Claim строится только из exact current `PhantomAcquisitionService.ConflictSnapshot`: Goal id/revision, current Source, progress, required и remaining. Рецепт не создаёт farming claim.

- `ROOM`: exact topology room node. Anchor и NPC в ключ не входят.
- `MOB_GROUP`: exact topology node + current Source anchor + NPC id.

Примеры:

- `ROOM|catacomb.room.17` конфликтует для разных NPC и anchors внутри одной exact room node.
- `MOB_GROUP|field.gludio.north|spawn.201|npc.20001` конфликтует только с тем же node, anchor и NPC.
- одинаковые anchor/NPC в разных topology nodes не конфликтуют.

Новая копия acquisition planner или remaining calculator запрещена. До bilateral FINAL изменение
remaining/progress/acquisition/social evidence инвалидирует или пересчитывает draft. После exact
bilateral FINAL remaining/progress являются historical arbitration evidence: обычный monotonic Goal021
progress не делает SHARE/WAIT/MOVE stale.

## Perceptibility

`PhantomTopologyService.perceptibleProfiles(observerProfileId, LOCAL_CHAT, limit)` — единственный новый seam. Он использует current generation lease, same-node/one-hop topology query и существующий profile registry node index, запрашивает не более `limit + 1`, исключает observer и возвращает стабильный bounded список. Policy ограничивает запрос 32 профилями. `listProfiles()`, `World.getPlayers()` и четвёртый источник topology signal ledger не используются.

Claim bucket сам по себе не доказывает новый конфликт: counterpart должен одновременно иметь exact
current claim того же ресурса и присутствовать в текущем bounded perceptibility query. Начатая exact
pair negotiation хранит `CausalPerceptionReceipt`: pair, topology generation/hash, оба node/sequence,
channel, observed/expiry minute и evidence hash. До causal TTL receipt разрешает продолжить только ту
же exact pair даже после исчезновения one-hop visibility; topology authority drift fail closed.

## Goal 021 gate

`PhantomFarmingConflictPort` вызывается только перед новым `TRAVEL_REQUIRED` или `TARGET_REQUIRED` resource work и повторно проверяется на непосредственных safe boundaries. Уже отправленное действие не прерывается.

- `ALLOW`, `SHARE` — Goal 021 продолжает существующий acquisition path.
- `NEGOTIATE`, `WAIT` — новое resource work не начинается; acquisition возвращает bounded replan/block.
- `MOVE`, `STALE` — делегируются существующему `acquisition.switchSource`; farming не переключает Source самостоятельно.
- незарегистрированный port — прежнее поведение Goal 021 (`ALLOW`).

Exact same Party membership из Goal 017 всегда даёт `SHARE` без bilateral rounds. Stale/unknown Party state не создаёт искусственное согласие.

## Bilateral protocol

Для Phantom↔Phantom применяется один deterministic protocol с одним active negotiation на профиль:

1. stable initiator — меньший profile id; agreement id выводится из обоих profile ids, exact resource и Goal bindings;
2. persisted `OFFER` у инициатора;
3. persisted `RESPONSE` у counterpart;
4. persisted `FINAL` у меньшего profile id;
5. exact mirror `FINAL` у большего profile id;
6. эффект разрешён только после повторного чтения и полного `exactPair` обоих receipts.

Порядок записи всегда lower id → higher id. Fault после любого persistence boundary безопасен:
после restart тот же agreement id восстанавливается, односторонний FINAL/terminal не имеет
противоречивого gate/social/query эффекта, а reconciliation дописывает exact mirror. Restart exact-load
делает только persisted counterpart по ID и не зависит от его scheduler pulse; profile/listProfiles/World
scans отсутствуют.

Receipt фиксирует оба historical remaining/progress, обе Goal revision/source binding, ResourceKey,
stable authority и causal receipt. Live binding проверяет pair, goal/revision, source, ResourceKey,
authority, exactPair и TTL, но не equality current remaining.

Arbitration стабильна и использует данные обеих сторон: remaining/progress, Goal priority, bounded alternatives, claim age, Goal 018 `goal.persistence`, `conflict.escalation`, relationship/cooperation и current Goal 010 perceptibility. Итоговый набор актов строго ограничен: `SHARE`, `WAIT`, `MOVE`, `REFUSE`, `ESCALATE`.

## Persistence и policy

Компонент `farming.conflict`, schema v2, хранится через существующий versioned profile component CAS.
Состояние bounded: один current claim, один active negotiation, не более четырёх agreement history
entries и policy-bounded alternatives. Codec детерминированно читает v2; v1 считается legacy-untrusted
и не авторизует эффект без fresh exact pair revalidation. Safe exact v1 может лениво мигрировать в v2;
SQL migration отсутствует. Startup scan отсутствует.

Strict hashed policy находится в `dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml`. Все XML attributes allowlisted и range-checked; неизвестные/дублированные элементы и XXE запрещены. Основные bounds: claim lease 3 минуты, agreement TTL 10, wait 5, cooldown 2, не более 3 rounds, 4 alternatives, 8 claimants на resource, 32 perceptible profiles, 4 history entries.

## Social и conversation ownership

Goal 018 получает idempotent события `farming.agreement.offered`, `farming.agreement.accepted`,
`farming.agreement.refused`, `farming.conflict.escalated` с exact agreement/resource identity;
generic `agreement.fulfilled`/`agreement.broken` остаются у Goal 018. Per-owner delivery bits являются
durable retry truth: transient social failure повторяет тот же deterministic event id. EXPIRED/STALE
не создают broken. Farming service не отправляет chat packets.

Goal 020 исполняет typed intent `farming.conflict.query` и получает не более восьми current facts: claim status, own/counterpart remaining, alternative, conflict, semantic act/escalation и exact bilateral agreement. Stale и one-sided state подавляются. Обычный human `Player` без Phantom profile/acquisition state не получает fabricated Goal, claim или auto-agreement.

## Corrective lifecycle Goal 024A

Manual `observeAgreementOutcome(..., boolean)` не является production authority и отсутствует.
Bounded reconciliation наблюдает фактический Goal021 lifecycle:

- real MOVE проходит существующий `DirectiveKind.SWITCH` / `switchSource` ровно один раз; после
  наблюдаемой смены Source old claim освобождается, agreement становится bilateral FULFILLED;
- WAIT переживает progress holder и становится FULFILLED при holder completion/release/move;
- SHARE переживает обычный progress и нормально завершается при completion/release;
- TTL даёт bilateral EXPIRED; incompatible authority drift — bilateral STALE;
- BROKEN допускается только по объективному exact breach evidence; неоднозначность не выдумывает breach.

## Lifecycle и performance

Сервис не создаёт worker, Future, timer или поток. Все операции запускаются существующим Decision pulse или Goal 021 gate и ограничены policy bounds. Shutdown сначала закрывает новые mutation claims, снимает static gate, затем очищает runtime claim leases и завершает сервис. Startup/shutdown failure также снимает gate до раннего возврата.

Performance smoke фиксирует 100 000 gate evaluations с bounded resource bucket/perceptibility/scoring work; DB используется только через lazy profile component persistence, production DB тестами не изменяется.
