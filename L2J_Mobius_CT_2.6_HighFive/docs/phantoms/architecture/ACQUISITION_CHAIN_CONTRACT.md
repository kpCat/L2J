# Контракт acquisition chain — Goal 021 Checkpoint 1

## Граница checkpoint

Checkpoint 1 реализует bounded kernel для `acquire.item`: authoritative
death-drop и spoil/sweep execution, recipe ingredient planning и одинаковую
модель прогресса для active/background. Manor и quest collection имеют статус
`DEFERRED_CHECKPOINT_2`. Craft, trade, private store и enchant не исполняются и
остаются границей Goal 022.

Phantom World остаётся выключенным штатным глобальным feature flag. Новых
worker, scheduler port, schema и второго combat loop нет. `Player`, `Party`,
skill handlers и quest handlers не изменяются.

## Источники истины

- `high-five-acquisition-v1.xml` — строгая versioned policy: методы, лимиты,
  scoring и switching thresholds. Неизвестные элементы/атрибуты, XXE,
  дубликаты и неверный порядок отклоняются.
- Game Knowledge — единственный источник item/drop/spoil/recipe/NPC/spawn facts.
- Topology — единственный источник instance-0 node/anchor и travel identity.
- Progression catalog — источник exact spoil/sweep/craft capability evidence.
- Canonical inventory — единственный источник baseline и observed amount.

Planner использует bounded index pages и хранит не corpus, а максимум восемь
ranked source identity. Recipe planner строит DAG максимум из 48 уникальных
узлов, глубиной не более 6 и с максимум 32 дефицитами. Recipe path ничего не
крафтит и не изменяет inventory/Goal.

## Durable state и прогресс

Компонент `acquisition.state` имеет schema version 1 и worst-case envelope не
более 4096 bytes. Он хранит hashes пяти authority generations, Goal identity,
target item, immutable baseline, последний authoritative count, bounded
candidates/receipts, selected source, switch count и одну persisted phase.

Прогресс всегда вычисляется так:

```text
progress = min(requiredAmount, max(0, authoritativeCount - baselineCount))
```

Receipt не является источником количества предметов. Он лишь связывает
operation/source/phase с before/after observation и terminal result. Exact
replay сверяет durable background receipt, acquisition state и Goal projection.

## Active spoil/sweep

Active-контур владеет ровно одним persisted переходом за Decision step:

```text
target claim → canonical spoil → existing Combat kill → canonical sweep → verify
```

Acquisition использует `PhantomCombatService` и его ownership lease; отдельной
атаки или AI loop нет. Target identity включает object/NPC/instance и
проверяется перед каждым side effect. Dispatch phases сохраняются до вызова,
поэтому restart не повторяет uncertain spoil/sweep вслепую. Sweep допустим
только после observed spoil и terminal existing Combat на том же corpse.

## Background parity и atomicity

Background authority преобразует тот же выбранный Game Knowledge fact в
`PhantomBackgroundModel.Target`. Selected death-drop/spoil имеет origin
`ACQUISITION_TARGET`, остальные death drops — `INCIDENTAL_DEATH_DROP`.
Отсутствующая spoil/sweep capability, unsupported context, capacity rejection
или failure дают нулевой acquisition delta.

Единственный mutation owner — существующий `PhantomBackgroundTransaction`.
Одна DB transaction блокирует profile → Goal → acquisition → background →
canonical character/skills/items и атомарно пишет:

```text
canonical item/progress/vitals + background VERIFY_PENDING
+ Goal projection + acquisition observation/receipt
```

Pre-commit failure откатывает всё. Post-commit unknown восстанавливается через
background `VERIFY_PENDING`; exact replay допускается только при совпадении
operation receipt, ожидаемых next row versions, полного acquisition payload и
Goal projection. Capacity проверяется до засчитывания acquisition progress.

## Switching и lifecycle

Switch выполняется детерминированно по score/source ID, только без retained
target/external/travel claim. Partial progress и baseline сохраняются. Failure
threshold, cooldown и максимум четыре switch берутся из typed policy. Authority
drift перестраивает bounded identities по новым hashes; exhausted alternatives
дают terminal failure, а не случайный source.

Service не владеет thread/executor/future. `beginStop` отменяет navigation и
external actions; `finishStop` требует нулевые transition, external и navigation
claims.
