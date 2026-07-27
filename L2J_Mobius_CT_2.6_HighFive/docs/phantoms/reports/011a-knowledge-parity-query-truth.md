# Goal 011A — knowledge parity and query truth hardening

## Status

```text
Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Baseline/parent: dc4659fea3e76a78841dfee0429bc4ab1ed2b185
Branch: feature/phantom-world
Subject: fix(phantoms): harden game knowledge parity and queries
Manual gate: PENDING_INDEPENDENT_REVIEW
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```

## Summary

Закрыты только findings независимого review Goal 011:

- drop/spoil ordinals теперь отражают exact runtime loader order;
- parity expected facts реконструируются напрямую из server loaders;
- recipe list не теряется при ambiguous recipe-item lookup;
- requested empty target filters возвращают empty page;
- public spawn-area views не содержат nested exact points;
- `TargetFact` ограничен `64` area summaries;
- lifecycle diagnostics публикуют component и combined hashes.

Zaken datapack correction, curated XML, server loaders, `PhantomSystem`,
config/schema и Goal 012/013 не менялись.

## Changed files

Production:

```text
java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.java
java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java
```

Tests/build/verifier:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeQueryTruthSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-011a.ps1
```

Documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md
docs/phantoms/reports/011-authoritative-game-knowledge.md
docs/phantoms/reports/011a-knowledge-parity-query-truth.md
docs/phantoms/reviews/011-authoritative-game-knowledge-review.md
docs/phantoms/tasks/011a-knowledge-parity-query-truth/**
```

## Architecture decisions

### Runtime/source ordinals

`NpcTemplate.getDropGroups()`, `DropGroupHolder.getDropList()`,
`NpcTemplate.getDropList()` и `NpcTemplate.getSpoilList()` копируются в exact
list order. Canonical sorting разрешена только после назначения runtime/source
ordinals. Per-NPC indexes также сортируются по source kind и ordinals.

### Independent parity

Expected item/NPC/drop/spoil/spawn/recipe facts строятся без повторного вызова
knowledge backend. Parity использует `ItemData`, `NpcData`, `SpawnTable`,
`MapRegionData` и `RecipeData` напрямую.

### Recipe ambiguity

Current High Five corpus содержит две recipe lists с recipe-item `5008`.
First-match item lookup неоднозначен. Backend обнаруживает duplicate item ID,
переключается на bounded public list-ID reconstruction, сверяет loaded count и
item-ID multiset и сохраняет обе уникальные list identities. Если reconstruction
неполна, build fail-closed с `ambiguity`. Item-only helper отдельно доказан как
fail-closed на duplicate ID.

### Query truth

Optional filter хранит два состояния: not requested (`null`) и requested exact
set. Requested отсутствующий index entry — empty set и immediate empty page.
Empty intersection не расширяется.

### Bounded public views

Internal `SpawnAreaFact` и complete indexes сохранены. Public `spawnAreas`
возвращает `SpawnAreaSummary` без representative points. `TargetFact` содержит
total count, truncation flag и максимум `64` summaries. Exact points доступны
только через `spawnFacts`, page limit `256`.

### Diagnostics

`ServiceSnapshot` содержит immutable `Hashes` с item, NPC/drop/spoil, spawn,
recipe, manor, class capability, content requirement, topology и combined
SHA-256. Inactive/failed значения равны `none`.

## DB and migrations

Production DB не использовалась. Новых migrations/config keys/schema changes
нет. Real-loader suites используют только allowlisted
`l2jmobiush5_phantom_test` через существующий harness.

## Counts and hashes

```text
items=19200
npcs=10482
deathDrops=56483
spoils=7335
spawnFacts=42283
spawnAreas=3864
recipes=1000
recipeIngredients=6258
manorFacts=258
classFacts=103
classCapabilities=37
contentRequirements=3

itemsHash=b1f91522bcd0dbc16aaa2e0207752a17dd1b8b348bbe2aebf45c35bb303ad435
npcDropSpoilHash=b1a5bc2ee6d9be11c1d5976701ad025a1435db67abae095517eb16b629089615
spawnHash=94280ba0e38d355ed55ebf22174b7d99c91edf2c22835dac972f299d574009df
recipeHash=ca467b38946328aecb3f23948c124305fbfccc5b4479c8e3b78e6c0509ef9594
manorHash=991eed8c95c8a723f0d2f08e75a46e36ed1180081e488c632a9a4b9367dd39dc
classCapabilityHash=e8e548fe90d8d9d0e9e852030bf4f48011aacaf892bad58da001be14534674d9
contentRequirementHash=4dd788339b9fe141dbc4073cb90ee8e53542ca39cff5b59efc6fc64f4e2a1c37
topologyHash=f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f
combinedHash=bada3c9f2de5c925e32dff959bcdfed0b9ed8060e508cc67072ae66ae952a554
```

## Commands and test results

Baseline:

```text
verify-task-011.ps1: 153 PASS / 1 FAIL
Reason: expected untracked Goal 011A package and user-owned untracked geodata.
ant verify: BLOCKED during old self-referential parity by Java heap OOM while
            203 user geodata regions were loaded under the old 2 GiB cap.
```

Focused implementation smoke:

```text
compile-tests: PASS
knowledge-core: 50/50 PASS
knowledge-parity: 21/21 PASS
knowledge-query-truth: 13/13 PASS
knowledge-content: 18/18 PASS
knowledge-performance: 8/8 PASS
phantom-skeleton: 12/12 PASS
```

Required repeat matrix:

```text
knowledge-core: 50/50 PASS ×3
knowledge-parity: 21/21 PASS ×2
knowledge-query-truth: 13/13 PASS ×3
knowledge-content: 18/18 PASS ×3
knowledge-performance: 8/8 PASS ×2
```

Cumulative and final pre-commit verification:

```text
all Goal 001–011 regression routes: PASS
ant verify: PASS
ant jar: PASS
verify-task-011a.ps1: 63/63 PASS ×2, byte-identical
verifier SHA-256:
6E7DF9745D070D83B48306C148EC58E08953C1894BC6B75842D9F46E962FBAA4
GameServer.jar: fresh, hardened knowledge entries present, test entries absent
```

Post-commit `verify`/`jar`/verifier ×2 и remote exact confirmation выполняются
после единственного commit и фиксируются в final handoff без изменения commit.

## Performance

Each performance run executes:

```text
100000 item source queries
100000 recipe reverse queries
100000 class capability queries
100000 bounded target queries
```

Первый smoke после hardening:

```text
service build: 890 ms
item source: 43 ms
recipe reverse: 27 ms
class capability: 12 ms
bounded target: 10806 ms
loader/file/DB access after build: 0
```

Второй smoke:

```text
service build: 915 ms
item source: 69 ms
recipe reverse: 28 ms
class capability: 12 ms
bounded target: 10515 ms
loader/file/DB access after build: 0
```

Оба deterministic summaries имеют одинаковые counts и component/combined
hashes. SHA-256 последнего summary:

```text
5567CA820C858419E5AFF418B4F893479916523FBEFB1F2E765434C1D77582B5
```

## Encoding checks

- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверен: совпадений нет.
- Старый mojibake в изменяемых Goal 011 contract/report заменён нормальной
  читаемой кириллицей.

## Deviations, limitations and risks

- User-owned untracked `dist/game/data/geodata/*.l2j` не изменялись и не входят
  в commit. Для real-loader JVM heap cap knowledge routes поднят с `2 GiB` до
  `4 GiB`, потому что присутствующая геодата сама занимает больше прежнего cap.
- Bounded list-ID reconstruction использует `maximumRecipes` как fail-closed
  scan ceiling. Если future datapack разместит required list ID выше ceiling,
  startup отклонит неполную reconstruction вместо silent omission.
- Runtime effective drop probability, current spawn state и reachability не
  вычисляются.
- Manual gate не принимается этим task.

## Git

Разрешённые bounded git inspections выполнялись по TASK: baseline HEAD/remote,
branch/upstream, exact parent/stat/diff, worktree inventory, scope/diff checks.
История не переписывалась.

```text
Commit SHA: one ordinary child of dc4659fe; exact SHA in final handoff
Push: ordinary origin/feature/phantom-world; exact result in final handoff
```

## Next step

Независимо проверить Goal 011A. До принятия:

```text
Goal 011A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```
