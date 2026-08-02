# Goal 021 Checkpoint 1 — review package

- Статус реализации: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Terminal closure: `PARTIAL_AGGREGATE_RERUN_REQUIRED`; пакет нельзя принимать до нового разрешённого aggregate rerun финального дерева.
- Ветка: `feature/phantom-world`.
- Seed: `21002101`.
- Accepted parent: `d48dccb42dcfe5993f1c852e021086e498c0622d`.
- Foundation: `bf0cc37b2af7023f3709f635ae4350306b892597`.
- Safety completion: `c764382485d27391a6449aa4843d4f684efc1f12`.
- Final closure subject: `fix(phantoms): close acquisition recovery and recipe truth`.

Это первый из двух заранее запланированных checkpoint Goal 021, не Goal 021A/021B.

## Что проверять

- `REJECTED` spoil/sweep: dispatch persisted до cast; один exact failure на rejection; lease release; threshold 3; no blind recast; deterministic switch/retarget с сохранением baseline-derived progress.
- `COMBAT_SUBMITTED`: exact derived owner и target проверяются до observation/consume; foreign session неизменна; missing session восстанавливается только по live target, owned spoiled corpse или inventory growth; недостаточная evidence ограничена и завершается `UNCERTAIN/BLOCKED`.
- Recipe truth: bounded probe до exact active/background inventory read, затем один final plan; максимум 4 alternatives, depth 6, 48 nodes на alternative и 128 exact IDs; absent item равен нулю; maps immutable; shared inventory не расходуется дважды.
- Preferred method: bonus входит в score до ambiguity, не обходит eligibility/cooldown и сохраняет deterministic canonical order.
- Aggregate: должен зависеть от исходных восьми routes, safety routes и final closure routes; Goal routes используют только `phantom.goal021c1.seed`.
- Exact commit graph: foundation → safety → один ordinary closure commit; future HEAD допустим только как descendant closure.

## Жёсткие границы

- Нет второго Combat loop и direct inventory mutation из acquisition package.
- Background item/state/Goal/acquisition mutation остаётся одной существующей transaction.
- `Player.java`, `Party.java`, schema, skill/quest handlers и другие хроники не меняются.
- Manor/quest остаются `DEFERRED_CHECKPOINT_2`.
- Craft/trade/private stores/enchant execution отсутствует и относится к Goal 022.
- Только test DB; global seed override для plain `ant verify` запрещён.

## Независимый gate

До freeze подтверждены:

- 18 изменённых файлов, из них 11 production/data и 0 new production;
- ordered focused и affected routes — PASS;
- historical verifier 020c2 и working verifier 021c1 — PASS в PowerShell 5.1 и 7.6;
- первый final acquisition aggregate — PASS за 6:37;
- после реального stale-authority fix второй и последний разрешённый aggregate — FAIL на недействительном test-only Spoil level override;
- исправленный test-owned Monster сохраняет canonical NPC/drop template, гарантирует Spoil cast и восстанавливает template level до kill/drop calculation; focused active-spoil после исправления — PASS 3/3;
- второй plain `ant verify` — PASS за 13:44, standalone `ant jar` — PASS за 17 секунд;
- третьего aggregate нет: verification authority его прямо запрещает.

До независимого review требуется новая явная authority на один aggregate rerun. После его PASS review должен повторно проверить scope/verifier, plain `ant verify`, `ant jar`, pushed closure commit и два byte-identical post-commit verifier outputs. Точный closure SHA фиксируется post-commit verifier и terminal handoff, а не self-referential amend отчёта. Только отдельное решение reviewer может заменить pending status на `ACCEPT`.
