# Goal 021 Checkpoint 1 — review package

- Статус реализации: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Terminal closure: `FINALIZED_PENDING_INDEPENDENT_REVIEW`; terminal gates завершены, но только независимый reviewer может выставить `ACCEPT`.
- Ветка: `feature/phantom-world`.
- Seed: `21002101`.
- Accepted parent: `d48dccb42dcfe5993f1c852e021086e498c0622d`.
- Foundation: `bf0cc37b2af7023f3709f635ae4350306b892597`.
- Safety completion: `c764382485d27391a6449aa4843d4f684efc1f12`.
- Final closure: `6556400433020a2833b9d19e0a6c6ac5db2499eb`, subject `fix(phantoms): close acquisition recovery and recipe truth`.
- Micro-completion subject: `fix(phantoms): enforce acquisition ambiguity and probe bounds`.

Это первый из двух заранее запланированных checkpoint Goal 021, не Goal 021A/021B.

## Что проверять

- `REJECTED` spoil/sweep: dispatch persisted до cast; один exact failure на rejection; lease release; threshold 3; no blind recast; deterministic switch/retarget с сохранением baseline-derived progress.
- `COMBAT_SUBMITTED`: exact derived owner и target проверяются до observation/consume; foreign session неизменна; missing session восстанавливается только по live target, owned spoiled corpse или inventory growth; недостаточная evidence ограничена и завершается `UNCERTAIN/BLOCKED`.
- Recipe truth: bounded probe до exact active/background inventory read, затем один final plan; максимум 4 alternatives, depth 6, 48 nodes на alternative и 128 exact IDs; failed probe исключает recipe до inventory/final planner, recipe-only сохраняет `BLOCKED`, mixed Goal продолжает по executable source.
- Preferred method и ambiguity: bonus входит в score до ambiguity, не обходит eligibility/cooldown; threshold применяется также к death/death и spoil/spoil, а `sourceId` используется только для stable order.
- Aggregate: должен зависеть от исходных восьми routes, safety routes и final closure routes; Goal routes используют только `phantom.goal021c1.seed`.
- Exact commit graph: foundation → safety → pinned ordinary closure `655640…` → ровно один ordinary micro-completion; future HEAD допустим как descendant micro-completion.

## Жёсткие границы

- Нет второго Combat loop и direct inventory mutation из acquisition package.
- Background item/state/Goal/acquisition mutation остаётся одной существующей transaction.
- `Player.java`, `Party.java`, schema, skill/quest handlers и другие хроники не меняются.
- Manor/quest остаются `DEFERRED_CHECKPOINT_2`.
- Craft/trade/private stores/enchant execution отсутствует и относится к Goal 022.
- Только test DB; global seed override для plain `ant verify` запрещён.

## Независимый gate

Подтверждены:

- 18 изменённых файлов, из них 11 production/data и 0 new production;
- ordered focused и affected routes — PASS;
- historical verifier 020c2 и working verifier 021c1 — PASS в PowerShell 5.1 и 7.6;
- первый final acquisition aggregate — PASS за 6:37;
- после реального stale-authority fix второй и последний разрешённый aggregate — FAIL на недействительном test-only Spoil level override;
- исправленный test-owned Monster сохраняет canonical NPC/drop template, гарантирует Spoil cast и восстанавливает template level до kill/drop calculation; focused active-spoil после исправления — PASS 3/3;
- второй plain `ant verify` — PASS за 13:44, standalone `ant jar` — PASS за 17 секунд;
- исторический запрет третьего aggregate соблюдён; новая отдельная bounded micro-completion authority разрешила один новый final run после двух точечных safety fixes;
- same-method death/spoil ambiguity, cross-method preference, eligibility/cooldown и repeated byte identity — PASS;
- 4 individually admissible recipe alternatives образуют union 132 IDs; real `PhantomAcquisitionService.plan` сохраняет recipe-only `BLOCKED` без inventory read и выбирает death в mixed Goal — PASS;
- micro scope: 6 existing files, 2 production, 0 new production/data; cumulative scope остаётся 18/11/0;
- единственный final micro aggregate — PASS за 5:55 с Goal seed `21002101`;
- после freeze plain `ant verify` без global seed override — PASS за 13:20; standalone `ant jar` — PASS за 17 секунд.

Независимый review должен проверить scope/verifier, pushed micro-completion commit и два byte-identical post-commit verifier outputs. Точный micro SHA фиксируется post-commit verifier и terminal handoff, а не self-referential amend отчёта. Только отдельное решение reviewer может заменить pending status на `ACCEPT`.
