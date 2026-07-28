# Goal 013A — Progression capability extensibility hardening

## Статус и baseline

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

- branch: `feature/phantom-world`;
- required parent: `ca50ea28f233e41343035977c55c98129e5d113a`;
- independently accepted pre-013 baseline: `8dba87e9c1d5828376b80c1ea16c4578726d4947`;
- subject: `fix(phantoms): harden progression capability extensibility`;
- commit: текущий ordinary child; exact SHA и push result фиксируются во внешнем final handoff, потому что этот отчёт входит в сам commit;
- Goal 013: `FIX_REQUIRED`;
- Goal 013A: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 014: `NOT_STARTED`, blocked by independent acceptance of Goal 013A;
- Goal 015/017/025: `NOT_STARTED`.

Required parent и `origin/feature/phantom-world` перед изменениями совпадали с
`ca50ea28f233e41343035977c55c98129e5d113a`. Pre-existing worktree содержал
только предоставленный untracked task package Goal 013A; он идентифицирован и
включён в exact scope.

## Read-first и переиспользованные seams

Полностью прочитаны обязательные master plan, roadmap, workflow/task/report
contracts, Goal 013 package/report/contract, Goal 013A package, normalized
DR-01…DR-05, все progression production/tests/verifier и непосредственно
связанные canonical `Player`, `Skill`, `SkillTreeData`, `ItemData`, inventory,
subclass, summon/servitor/pet/BabyPet/cubic APIs.

Переиспользованы:

- exact materialization `ActionLease`;
- immutable record/catalog/hash pattern Goal 013;
- canonical `Player.getSkills`, inventory, skill condition/reuse и equip APIs;
- fail-closed operation/result contracts;
- ordinary Game Knowledge builder/query dependency order;
- bounded cursor/page pattern;
- suffix-goal static verifier chain в `build.xml`.

Parent-level `AGENTS.md` в корне Git-репозитория не найден; действующий
High Five `Agents.md` прочитан. Новые API, dependencies и архитектурные слои не
изобретались.

## Disposition независимых findings

- `F-013-01`: закрыт stable `variantKey`, uniqueness
  `(classId, capabilityKey, variantKey)`, exact `actionSkill` и сохранение всех
  same-group variants.
- `F-013-02`: закрыт копированием `Skill.itemConsumeId/count`, charges и
  maximum soul consumption facts; effective item requirement объединяется без
  double count.
- `F-013-03`: dead equippable-subset check удалён; все positive references
  валидируются против полного immutable `ItemData` ID set.
- `F-013-04`: cubic не имеет body/commands; servitor/pet own skills и
  heal/recharge/buff/damage/control evidence сохранены; live body — immutable
  typed snapshot.
- `F-013-05`: universal score/top-64 удалён; owned equipment доступен через
  lease-bound filtered paging до 64 objects/page со stable object-ID cursor.
- `F-013-06`: добавлена ordinary production Game Knowledge + progression
  composition suite с independent identity/provenance enumeration.
- `F-013-07`: repeated required item IDs агрегируются; при более чем одном
  distinct item canonical atomic seam не доказан, поэтому request fail-closed
  до side effects. `OnPlayerSkillLearn` следует только после reconciliation.
- `F-013-08`: resolver сохраняет variants, проверяет все supported skills и не
  завершает поиск на первом blocked/high-rank evidence; rank остаётся metadata.

Дополнительный CP contract закрыт отдельными `currentCp`/`maximumCp` в
canonical Player combat snapshot. Значения копируются из exact `Player` под
lease, следующий snapshot видит mutation, предыдущий остаётся immutable.
Transient canonical `currentCp > maximumCp` не нормализуется. Controlled
actors не получают Player CP, cubic не получает body, profile persistence не
изменялась.

## Production composition и hashes

Focused production composition на seed `130013`:

- ordinary Game Knowledge variants: `37`;
- curated progression variants: `18`;
- total exact variants: `55`;
- class graph:
  `B91E569EFE5886519A999C84215BAD432A747A11EB5943298A1EC652AF076C49`;
- skill learning:
  `6A22326996332FFA92B7075CF81F568471578DDFB40C57827CC0A1F9B40A54FC`;
- skill mechanics:
  `A57E96FE7479242E9262CB67C298C498B0AA46E474D8198C32664489D8C36503`;
- equipment:
  `CE415CFEE5ADE2A370BB8B2A64D7D9D9306403D229E2C28A37944CC72C5FED49`;
- summon/pet:
  `41513A5B4A5516CEAC589A4B63CBEDC1FE27F070FDB3220258336443687098E0`;
- capability variants:
  `D94630E5E76F974672D6F612678AA54337F094DFEC384B6A81EB6A45B827E83D`;
- combined:
  `9E740E140C30748D165F49C93B91520B10698841999DDCCCC347CAE6830423FF`.

Три production rebuild дали один combined hash. Synthetic catalog suite имеет
отдельный fixture-only hash
`F5B82690BE49203F01574834802182B903402418449865007F022912499B47C1`;
он не заявляется production catalog hash.

## Focused tests

Использован явный Ant property argument `-Dphantom.test.seed=130013`.

- progression extensibility: `15/15`;
- production composition: `9/9`;
- progression catalog: `60/60`;
- progression parity: `32/32`;
- capability runtime: `40/40`;
- progression operations: `36/36`;
- real progression server integration: `28/28`;
- progression performance: `2/2`;
- combat core после CP regression: `50/50`;
- real combat server integration: `20/20`.

Real integration доказала >64 equipment objects, complete paging/filtering,
lower-grade reachability, main → subclass → main isolation, ordinary skill
isolation, separate certification, Servitor/BabyPet body и cubic absent body.
CP case доказал exact current/max getters, next-snapshot freshness, no mutation
и отсутствие HP/MP/CP mixing. Disabled backend inert.

При первом CP integration новая проверка `currentCp <= maximumCp` вызвала
`BACKEND_FAILURE` в unrelated combat cases после canonical class/level
transition. Current server допускает такое transient состояние; invented
normalization удалена, regression case добавлен, повторные real/core runs
прошли.

## Performance и lifecycle

Focused performance на seed `130013`:

```text
catalogBuilds=3
classQueries=100000
skillQueries=100000
capabilityEvaluations=100000
equipmentPageFilterQueries=50000
summonPetQueries=50000
operations=10000
elapsedMillis=25343
maximumOwnedEquipmentPage=1
operationsAfter=0
actorLeasesAfter=0
workers=0
tasks=0
futures=0
```

Bound `25 343 ms <= 120 000 ms` выполнен. В production progression не добавлены
executor/thread/task/Future, loader/file/DB scans в evaluator или hot-path
logging.

## Cumulative gates

`ant` отсутствует в PATH; использован bundled Apache Ant из IntelliJ:

```text
java -cp "<IntelliJ ant-launcher.jar>;<IntelliJ ant.jar>" org.apache.tools.ant.Main ...
```

Итоговые обязательные результаты:

- focused targets с seed `130013`: PASS, counts перечислены выше;
- exact cumulative `ant verify` без seed override: PASS, `5 minutes 8 seconds`;
- standalone `ant jar`: PASS, `17 seconds`;
- `GameServer.jar` содержит production progression/combat classes и не содержит
  test classes;
- Goal 013A verifier: `70/70`.

Промежуточные неуспешные команды сохранены честно:

1. literal `ant compile-tests` не запустился, потому что Ant отсутствует в PATH;
2. два timed shell launches оставили concurrent Ant children; остановлены
   только exact Ant/test PIDs, после чего sequential compile прошёл;
3. `ant verify` с ошибочно глобальным seed `130013` остановился на historical
   harness checksum; task требует этот seed для focused suites, а cumulative
   command — отдельно;
4. первый corrected cumulative run дошёл до historical Goal 013 verifier,
   который не понимает corrective child; build-chain расширена новым 013A
   verifier по существующему suffix-goal pattern;
5. следующий exact cumulative run один раз воспроизвёл asynchronous historical
   `combat-server-integration.02`; тот же case ранее и в финальном clean retry
   прошёл. Production combat cleanup не изменялся.

Финальный exact cumulative retry завершился `BUILD SUCCESSFUL`.

## Verifier

`tools/phantoms/verify-task-013a.ps1` является deterministic read-only
semantic verifier. Он проверяет XML triple identity, multi-variant survival,
resource propagation, cubic/body shape, equipment paging contract,
skill-learning fail-closed ordering, CP shape/copy, production composition,
tests, exact allowlist, DB/scope, UTF-8/encoding и JAR contents.

Два финальных запуска:

- run 1: `70/70`;
- run 2: `70/70`;
- byte comparison: `IDENTICAL`;
- SHA-256:
  `EF3EBCCFAE225C8E53EF8FEE89B3C7B1315461247F4529E9D678BEBFEEEC95DA`.

## DB, config, scope и changed files

- test DB: только `l2jmobiush5_phantom_test`;
- schema aggregate:
  `20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E`;
- production DB не использовалась;
- migrations/schema/config: без изменений;
- accepted Game Knowledge production/data: без изменений;
- server core, `PhantomSystem`, `Shutdown`: без изменений;
- scheduler/persistence/materialization/identity: без изменений;
- root `.gitignore`: без изменений;
- geodata/`.l2j`: не добавлялись;
- другие хроники: без изменений;
- Goal 014/015/017/025 production work: не начат.

Exact changed scope:

- `build.xml`;
- `dist/game/data/phantoms/progression/high-five-capabilities-v1.xml`;
- `java/org/l2jmobius/gameserver/phantoms/progression/*.java`;
- три разрешённых combat backend/resolver files;
- Goal 013/013A related `PhantomProgression*`, `PhantomCapability*` и четыре
  непосредственно связанные combat suites;
- `PhantomTestLauncher.java`;
- `tools/phantoms/verify-task-013a.ps1`;
- progression contract, roadmap, пять normalized DR files;
- Goal 013A task package, включая CP addendum и актуальный manifest;
- этот отчёт.

Это bounded exception больше 10 files, прямо заданный task allowlist и
обязательным CP addendum. Независимые artifact families и housekeeping не
добавлены.

## Ограничения и future contracts

- multi-distinct-item skill learning остаётся fail-closed, пока canonical
  atomic inventory seam не доказан;
- `maximumSoulConsumeCount` — authoritative ceiling, не invented minimum;
  обязательные soul conditions остаются за canonical dynamic condition;
- Goal 014 должна вывести CP potion supplies/vendors/restrictions/currency/cost
  только из authoritative item/NPC/buylist/multisell data;
- Goal 015 reconciliation не должна бесплатно сбрасывать/восстанавливать CP;
- Goal 025 определит PvP CP → HP, regen, potion reuse/economy и Olympiad
  doctrine;
- Goal 013A не определяет tactical desirability, equipment/PvP doctrine,
  summon commands или background simulation;
- unresolved blockers: нет;
- independent acceptance не выполнялась этим commit.

## Push и next step

Ordinary commit и push в `origin/feature/phantom-world` выполняются после
pre-commit scope/diff/encoding gate. Force push, amend, rebase, squash и merge
не используются.

Следующий допустимый шаг — только независимое review Goal 013A. Goal 014
остаётся заблокированной до acceptance.

`GOAL_013A_PROGRESSION_CAPABILITY_EXTENSIBILITY_HARDENED`
