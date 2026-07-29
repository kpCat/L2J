# Goal 015 — bounded completion review

## Verdict

`BLOCKED`

Технические reconciliation gates реализованы и проходят focused-проверки.
Capability нельзя объявить полностью успешной: deterministic audit текущего
production corpus не нашёл ни одной допустимой exact anchor/NPC пары для
успешного normal-solo background farming.

## Reviewed boundary

- Exact parent: `d41950922f6ceec53aca0326e6210e45353e0bc0`.
- Branch: `feature/phantom-world`.
- Seed: `15001501`.
- DB: только `l2jmobiush5_phantom_test`.
- Goal остаётся одной capability; Goal 015A/015B не создавались.
- Scope не расширялся на party, spoil, manor, raids, PvP, Goal 016/017/025,
  loaders, schema, config, datapack или geodata.

## Seven completion findings

1. Materialization ownership имеет ровно один terminal callback после успешного
   `beforeMaterialize`; abort идемпотентно восстанавливает свежий `READY`/`DEAD`
   либо переводит несовпадающее durable состояние в `INCONSISTENT`.
2. Materialization shutdown разрешён только после полной background quiescence:
   operations, leases, transactions, retained leases и `MATERIALIZING` равны
   нулю; dematerialization допускается во время `STOPPING`.
3. Schema v2 хранит compact mutable projection и paperdoll proofs, а typed
   conflict основан на hash всех заблокированных item rows. Проверены более
   100 unrelated objects, payload не больше 4096 bytes и 50 переходов.
4. Shot/resource contract читается из текущих `ItemTemplate`, item handler,
   commerce `SupplyFact`, weapon crystal/type и summon facts; Adena, неверный
   тип/grade/count и произвольные item IDs отклоняются.
5. Полный current-corpus audit нашёл только `giran.farming.22859` / NPC `22859`.
   Его drop IDs `8600–8614`, `10655–10657`, `13028` имеют excluded
   immediate/timed semantics. Supported production pair count: `0`.
6. WARM recovery использует bounded canonical town teleport с cancel/timeout,
   проверяет exact destination до store и сохраняет coordinates/vitals без
   бесплатных EXP/SP/items.
7. Real login после захвата штатного `REAL_LOGIN` lease fail-closed для любого
   durable non-`MATERIALIZED` background state и fail-closed при невозможности
   проверки.

## Blocking evidence

Отсутствие успешной production пары объективно нельзя устранить в разрешённом
scope: для этого требуется отдельное разрешение на topology/datapack data.
Подменять цель, ослаблять authoritative drop validation или начинать следующий
Goal запрещено. До появления поддерживаемой exact пары Goal 015 остаётся
`BLOCKED`, даже при зелёных technical tests.

## Review disposition

Это implementation evidence, а не self-acceptance. Следующий допустимый шаг —
отдельная data-задача на production farming pair, затем повтор production audit
и независимый review Goal 015.
