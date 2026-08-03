# Goal 022 Checkpoint 1 — independent review

## Статус handoff

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

Foundation принят как `d02dc8429e88ef507347fc2e3860b0528844ae68` поверх
Goal 021 baseline `043844c0fd7a0bfcac0d5f58461a21633b032332`. Lifecycle
completion принят как `9e2bd551ecc03647641c16e393694b9a0cb51e60`, authority completion —
как `20fe8daccfb5000b5b970bff7b3555a4051e5dbc`. Terminal completion должен быть
его обычным единственным ребёнком с subject
`fix(phantoms): close participant economy lifecycle ordering`.

## Что проверять независимо

- participant-neutral operation/reservation/audit schema и stable lock order;
- authoritative participant set = initiator union reservation profiles,
  sorted/distinct/bounded 4, с exact immutable profile-to-character links;
- единый lifecycle lock order profiles ascending → operation → canonical
  reservations → participant/link re-read для transition/renew/reconcile/
  expiry/cancel/shutdown/materialization/dispatch;
- background dispatch не pre-lock инициатора, а participant drift fail-stop
  завершается до item/vital/Goal mutation;
- reservation-only participant пересекает real `beforeMaterialize`/`beforeStore`
  boundary; DISPATCHING/OBSERVING и multiple active fail closed без mutation;
- обе противоположные concurrent lock orders проходят по 100 итераций;
- отсутствие expiry/re-dispatch после canonical dispatch;
- exact Goal 021 `RecipePlan` handoff и `RecipeManager` observer ownership;
- ordinary `RequestEnchantItem` parity после делегирования в canonical service;
- deterministic background craft/enchant conservation и risk policy;
- NPC commerce, acquisition/background и materialization conflict boundaries;
- отсутствие packet invocation, fake `GameClient`, новых workers/tasks и scope
  Checkpoint 2;
- два byte-identical запуска verifier 022c1 после push.
- один immutable RecipePlan проходит active и background progress 1→2→3 через
  distinct operation IDs и restart между попытками;
- canonical craft failure и rare different-ID не приписываются target Goal;
- все 12 fault points для actual background craft и enchant откатываются до
  commit, а `AFTER_COMMIT` сохраняет committed ownership;
- каждый reservation participant строго связан со своим character и участвует
  в active-operation exclusivity;
- active effect-before-Goal и Goal-before-audit windows не redispatch canonical
  mutation после restart.
- новый plan возобновляет exact `RESERVED`/`DISPATCHING`, но никогда не
  redispatch `OBSERVING`; cancellation/shutdown terminalize operation без claims;
- craft/enchant authority перечисляет все outcome/cost/eligibility facts с
  explicit serialization, и изменение каждого meaningful fact меняет hash;
- active authority drift A→B fail-stops до RecipeManager/EnchantItemService и
  не меняет resources;
- `EnchantItemService` владеет transaction/store/ownership/validity guards до
  consumption, ordinary packet остаётся byte-identical lifecycle completion;
- canonical Adena является replacement evidence и отдельным reservation,
  destructive risk/expense gates не подменяются declared Goal reserve;
- normal/rare output stacks резервируются и сверяются exact, а distinct
  non-stackable object IDs одного item template не конфликтуют друг с другом.

Этот файл не содержит self-accept. Независимый reviewer должен записать
отдельный verdict; Goal 022 Checkpoint 2 до этого не начинается.
