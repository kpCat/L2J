# Goal037 — Full High Five quest rates audit

Status: SUCCESS

## Scope and read-first evidence

- Required parent and remote baseline: `6b90ece75f13a7b04af3264580b9eb43e9336283` on `feature/phantom-world`.
- The complete `dist/game/data/scripts/quests/**/*.java` tree was inventoried; this was not a sample-quest audit.
- Initial read-only freeze: 543 Java sources, 4,323,198 bytes. Local evidence
  `.phantom-local/logs/goal037/source-fact-freeze.tsv` is 85,721 bytes with SHA-256
  `377297a5e1189216e1b6bbc256926b24fe45db2c4577624f4ef4796861463a1a`.
- Final audited corpus: 543 Java sources, 4,346,125 bytes. Exactly 331 quest
  sources changed; each changed call site is present in the committed AST manifest.
- The bounded exception to the ordinary 8–10-file guideline is required by the
  task's explicit 100% corpus contract. No schema, provider, LLM, UI, shipped rate
  default or production database change is included.

## Deterministic AST inventory

`QuestRatesAstAuditor` uses the JDK compiler tree API and the actual item XML
facts. It parses every quest Java source, expands local constants and
`registerQuestItems` arrays, creates stable path/line/column/expression
fingerprints, and rejects parse failures, missing/stale/new sources, missing or
stale sites, duplicates, unknown rows and unresolved semantic decisions.

Final inventory:

| Class | Sites |
|---|---:|
| Objective collection | 3,184 |
| Control/key/singleton | 1,503 |
| Terminal item reward | 573 |
| Terminal Adena reward | 387 |
| Terminal XP/SP reward | 308 |
| Fixed script mechanic | 633 |
| Normal drop | 0 |
| Spoil | 0 |
| Manor | 0 |
| Not a reward | 478 |
| Unsupported / requires evidence | 0 |
| **Total** | **7,066** |

Decision counts are `CANONICAL=4,021`, `FIXED_BY_GOAL037=1,507`,
`EXPLICIT_FIXED_EXCEPTION=1,538`, `REQUIRES_CORRECTION=0`. The 1,538 fixed
exceptions are source-hashed evidence, chiefly script-native chance, cap
observations and fixed mechanics; they are not hidden bypasses.

Committed manifests:

| Manifest | Bytes | SHA-256 |
|---|---:|---|
| `test/resources/phantoms/quest-rates/goal037-quest-inventory.tsv` | 119,385 | `9489793f8a09c6ff1fb155a7946c38bc8b5d87a655ff370c9fbaa942e4290b29` |
| `test/resources/phantoms/quest-rates/goal037-rate-sites.tsv` | 3,143,684 | `5579cef06424e1fa8bd016b38153f6853e4bf15444a6cb8aa67057de5393ca45` |

The static suite passed `2/2`, including seven negative controls for missing,
stale, new, duplicate and malformed source/site state. TSV order and LF output
are generated deterministically.

## Proven corrections

Exactly 1,876 rate defects were corrected:

- 1,503 control/key/singleton grants now use explicit no-rate semantics. This
  includes the direct Q636 quest mutation; control multiplicity is no longer
  coupled to `QuestItemDropAmountMultiplier`.
- 369 true terminal item rewards now use canonical `rewardItems`, preserving the
  existing item-category rate owners.
- Four exact-cap objective grants use `giveQuestItemsUpTo`: Q102 cap 10, Q152 cap
  5, and both Q401 collection caps 10/20. Amount is scaled once and clamped to the
  remaining cap.

No raw chance expression, quest condition, shipped multiplier default or
storyline state machine was changed. In particular,
`QuestItemDropAmountMultiplier` never multiplies script-native chance.

`Quest` gained the smallest backward-compatible helpers:

- `giveItemsWithoutQuestRate` overloads matching existing item/enchant/attribute
  grant shapes;
- `giveQuestItemsUpTo(Player, itemId, count, cap)` for exact cap-aware objective
  collection.

The existing `giveItems` contract remains unchanged for all callers.

## Player / Phantom parity

- Q102 and Q152 native ACTIVE flows passed at 1x and at the distinct non-1x
  profile `questItem=3,reward=5,xp=2,sp=3,adena=4`. Objectives reached exact caps,
  terminal item/XP/SP/Adena rewards used their owners, and control tokens remained
  singleton.
- BACKGROUND Q102/Q152 applies the current quest-item amount multiplier to the
  authoritative `maximumCount` once. Same-seed 1x/non-1x runs retain identical
  chance/RNG state; non-1x grants three and clamps the final delta at the cap.
- Q401 uses cap-aware objective collection while Auron/Simplon letters, swords,
  Warrior Guild Mark and final Medallion remain singletons. The canonical
  Fighter-to-Warrior owner consumes exactly one Medallion.
- Pailaka Q128 passed native conds 1–9 with its sword/book/essence lifecycle
  controls unmultiplied; terminal item rewards followed the non-1x reward rate.
- Ordinary Background XP/SP formula, death drop, configured per-item chance and
  amount overrides, spoil and manor paths passed focused parity. The explicit
  per-item regression used distinct chance/player-chance and amount/player-amount
  factors and proved raw chance remains separate.

## Refreshed source pins

| Source/catalog | SHA-256 |
|---|---|
| Q102 source | `ac2d5c6eb9082bb605df535cdd8c854b54ced6a4b5ebd4d59aaff38bbb8d137d` |
| Q152 source | `bfdde72c661d13106301d3421effb4e19d886e5db7f33fe7d4de6cf44b3e22c6` |
| Q401 source | `b2072d71b8f0d16ca3ce128a0bfc4f0341579c41ee7419bda6461901dbb72187` |
| Q128 source | `1a4eb79a4e9b8f24c699b2a1f9535366a7dab7e90733bf0a5361dcadaee839b4` |
| Goal021 quest collection catalog | `e9b5e5d0038414d892a64971425601807910526aeb073d19d59039072dc4247b` |
| Goal036 supported-content catalog | `9d6e0eafbdfaf7a173ddfc6559f4affea9113d3649db467a9ff395469b793002` |

Both Goal021 and Goal036 catalogs retain exact source-hash validation. Their
stale-hash negative controls pass; no whitelist or pinning rule was weakened.

## Automated gates

- Goal037 core: static `2/2`, corpus compile/load `1/1`, helpers `2/2`, native
  non-1x `8/8`.
- Goal036 native 1x: `8/8`.
- Goal021 affected acquisition: catalog/ACTIVE/BACKGROUND/restart/manor/topology/
  knowledge/performance routes all passed.
- Ordinary acquisition Background parity: `5/5`; Background model `7/7` and
  lifecycle `4/4`.
- Final focused/affected aggregate with the permanent ordinary-parity dependency:
  `95/95`, zero failures across 20 suite reports.
- Production-compatible corpus compilation/loading uses the actual server order:
  handler master followed by one `ScriptEngine.executeScriptList()` compiler/load
  pass. It loaded all 543 audited sources without a per-quest GameServer loop.
- Fresh full `ant -q verify`: PASS, exit `0`, 31 minutes 1 second.
- Standalone final `ant -q jar`: PASS, exit `0`, 21 seconds. The resulting
  `dist/libs/GameServer.jar` is 8,874,132 bytes with SHA-256
  `bcb59631ca12aa68d305e119bcd7442b4fb40df50e081a173e6018d8c4113724`.

The JDK 25 compiler may print its known Windows zipfs close-time
`AccessDeniedException` for the already copied `LoginServer.jar`; Ant compilation
continues successfully and the terminal exit code remains authoritative.

## Safety and final checks

- Every DB-backed gate used only `127.0.0.1:3308/l2jmobiush5_phantom_test` with
  the dedicated `l2j_phantom_test` user and existing schema manifest.
- Production `l2jmobiush5` was never selected. `prepare-phantom-test-db` was not
  executed.
- Fixture cleanup reports zero owned character, quest, item, instance and lease
  residue.
- Mojibake markers in changed files: checked separately, zero matches.
- Escaped Cyrillic in changed files: checked separately, zero matches.
- Strict XML load, UTF-8, deterministic LF-only TSV and `git diff --check`: PASS.
- The three pre-existing user-owned tracked paths recorded at preflight are not
  task changes and are excluded from staging.
- Goal038 remains `NOT_STARTED`; it is only the next planned explicit Goal.
