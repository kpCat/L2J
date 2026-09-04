# Goal 033 — Living population ecology

## Status

- Delivery status: `BLOCKED`.
- Blocker: `BLOCKED_033_CAUSAL_CATCHUP_ENTRYPOINT_REQUIRED`.
- Goal033 overall: `BLOCKED`; living population ecology не реализована и не включена.
- Goal032: `SUCCESS`, regression evidence сохранён.
- Product Java, XML, config и schema не изменялись: half-working implementation не оставлена.

## Exact baseline/branch

- Только `L2J_Mobius_CT_2.6_HighFive`, ветка `feature/phantom-world`, upstream `origin/feature/phantom-world`.
- Required parent, local `HEAD` и remote после fetch: `a1ef5cce4a6e152f9d3050a88a51efde1199246d`.
- Baseline gate: PASS, divergence отсутствует.
- Existing untracked task packages оставлены read-only и не входят в staging.

## Pre-audit findings

Read-first pass охватил `Agents.md`, master plan, Roadmap, workflow/task-package contracts, весь task package Goal033, current status/operator tuning/quick-start, Goal029 scale evidence, Goal032 report, shipped/local config, Population XML/catalog/state/codec/store/manager, PhantomSystem composition, Background authority/model/service/transaction/goal contract, Progression profession seams, Social create/store seams и production-composed harnesses Goal030/031/032.

Module-level `README.md` не найден; прочитан repository-level `README.md`. Отдельные code-map/pattern-файлы не найдены.

Ключевые факты:

1. `PhantomPopulationCatalog` уже владеет weighted archetypes/schedules; `PhantomPopulationManager` — единственный target/create/return/retire owner на общем Scheduler pulse.
2. `population.state` schema v2 и existing creation saga менять не требуется. `PhantomProfileRepository` уже даёт optimistic profile components, а Goal032 удаляет их через ownership/cascade.
3. `PhantomSocialService.createState()` создаёт traits только при отсутствии durable state; это безопасный будущий initializer seam.
4. `PhantomProgressionService` после level gate возвращает `CANONICAL_QUEST_REQUIRED`; Goal033 не может подделывать class quest.
5. Population level histogram сейчас публикует level 1 и не обновляется из canonical progression, поэтому он не доказывает living spread.
6. Goal029 подтверждает envelope до 10 000 scheduled, 128 profiles/pulse и 32 materialized, но не разрешает отдельный scheduler/unbounded catch-up.

## Existing reusable owners

- persistence: `phantom_profile_components`/`PhantomProfileRepository`;
- lifecycle: `PhantomPopulationManager` и existing creation/retirement saga;
- schedules: `PhantomPopulationCatalog`;
- canonical rewards: `PhantomBackgroundModel`, `L2jPhantomBackgroundAuthority`, `PhantomBackgroundTransaction`, `ExperienceData` и server rates;
- social: `PhantomSocialService`/`PhantomSocialStore`;
- runtime/restart: `PhantomSystem`, Scheduler, Decision, Materialization.

Будущий `population.ecology` owner должен вызываться из existing Population control pulse, без своего thread/timer/Scheduler.

## Anti-rubber-band evidence

Source audit не нашёл human level/EXP как population/progression target и не нашёл global player-level aggregation. Найденные `Player.getLevel()` читают только собственный canonical Phantom: creation invariant, Progression snapshot и Background capture/hash.

В Population/Background нет `setLevel`, `setExp`, `addExpAndSp` или `addExp`. Умножения в `PhantomBackgroundModel.calculateRewards()` — existing server/combat reward factors, не ecology pace. Human proximity может влиять на relevance/materialization, human level — нет.

## Ecology data model

Production component не добавлен из-за blocker. После unblock допустима отдельная schema v1: `authorityHash`, `presetId`, `assignmentOrdinal`, `virtualJoinEpochMinute`, `paceKind`, `paceBasisPoints`, `personalityKind`, `disposition`, `catchupCursorEpochMinute`, `turnoverEligibleEpochMinute`, `ecologyGeneration`.

Assignment immutable после successful insert; изменяемы только explicit cursor/disposition lifecycle fields через optimistic row version. Preset change не reroll existing profiles; reset удаляет component через existing cascade.

## Presets and exact distributions

`FRESH`, `LIVING`, `MATURE` и exact weights не добавлены: публиковать XML до существования исполняемого causal catch-up contract было бы ложным product API. После unblock strict catalog должен ссылаться на existing schedule IDs/social trait keys и не копировать EXP/NPC/drop data.

## Pace causal model

Требуемая модель: `productive work = schedule windows × bounded pace participation`; reward одного canonical action не меняется. `CASUAL/REGULAR/FAST/OUTLIER` могут различать только число/длительность productive windows, budget и downtime. `exp *= pace`, target-level seeding и free resources запрещены. Модель не реализована до unblock.

## Historical catch-up model

Обязательная causal path в baseline отсутствует. `PhantomBackgroundService.farm()` требует одновременно durable ACTIVE `farm.background` goal, exact source `<npcId>@<anchorId>`, NPC target/selected topology anchor, ранее захваченный READY Background state, authority hashes, activity generation и tick sequence. `PhantomBackgroundGoalSpec` прямо декларирует: Background никогда не выбирает target/anchor. Initial state строится только через materialization lifecycle.

Проверенные соседи пробел не закрывают:

- `PhantomFarmingService` координирует conflicts для уже выбранных Acquisition sources;
- `PhantomAcquisitionService` планирует item-driven goals, не generic leveling history;
- Population bootstrap не содержит background target/anchor;
- natural Decision farming не даёт bounded deterministic replay `virtualJoin -> now` и restart cursor.

Значит, текущий bounded scope не может pre-age population причинно. Direct SQL/level setter, synthetic EXP, hardcoded NPC/anchor или бесплатные расходники нарушили бы TASK. По §17 выбран `BLOCKED`.

### Minimal unblock

Отдельная bounded prerequisite-задача должна добавить внутри Background/Progression boundaries:

1. `HistoricalBackgroundPlan`/port с deterministic canonical NPC/anchor selection из existing GameKnowledge/Topology;
2. safe initial Background capture для нового character без публикации «готового ветерана»;
3. bounded batch API с time/work budget и committed interval cursor;
4. exactly-once commit через existing operation key/transaction с inventory/resource costs и hash validation;
5. materialization admission fence до завершения catch-up;
6. restart/failure proof: chunked run равен uninterrupted run, interval не применяется дважды.

После acceptance prerequisite Goal033 можно повторить без redesign Population/Social.

## Newcomer/turnover/archive lifecycle

Не реализован: без causal aging turnover дал бы только замену level-1 identities. Будущий lifecycle должен использовать existing Population pulse/creation saga, хранить `ARCHIVED` без delete, исключать archive из automatic `returnRetired`, проверять materialized/party/raid/clan-critical fences и при archive cap приостанавливать churn.

## Personality integration

Не реализована. Безопасный seam — deterministic bounded trait vector только при создании отсутствующего `social.state`; existing social state нельзя переписывать на restart/preset change.

## Profession/class-transfer limitation

Goal033 не выдаёт completed class quest, не меняет class через SQL и не добавляет universal shortcut. Current Progression корректно публикует `CANONICAL_QUEST_REQUIRED`; full class-transfer lifecycle остаётся Goal036.

## Config/operator UX

Config/status не изменялись. Shipped state остаётся `EnablePhantomSystem=False`, population/ACTIVE `0/0`; ecology keys отсутствуют и не включают незавершённый feature. Local 10/5 preset не заявляет `LIVING`. После unblock нужны fail-closed enable/preset/world-age/archive-limit и bounded aggregate status.

## Performance budget

Production performance не изменён. Future catch-up обязан использовать existing pulse, fixed profiles/chunks/actions per pulse, без periodic full scan и per-profile threads/tasks/futures; cursor сохраняется после каждого committed interval, histograms обновляются incrementally и остаются внутри Goal029 envelope.

## Changed files

1. `docs/PHANTOM_BOTS_ROADMAP.md` — prescribed blocker и prerequisite.
2. `docs/phantoms/PHANTOM_CURRENT_STATUS.md` — current BLOCKED truth.
3. `docs/phantoms/reports/033-living-population-ecology.md` — этот audit report.

Production Java/tests/XML/INI/schema и другие chronicles не изменялись. Bounded exception по artifact count не потребовался.

## Tests/real results

Read-only audit: human-level policy coupling — zero; direct level/EXP setters in Population/Background — zero; Background entrypoint/goal-contract gap — confirmed.

Executed:

1. `.phantom-local/apache-ant-1.10.17/bin/ant.bat compile-tests` — PASS, 2220 production + 131 test sources; два прежних Goal029 `System.runFinalization()` warnings.
2. `phantom-population-reset-ownership-goal032-test` — PASS `3/3`, seed `32003201`.
3. `phantom-population-reset-reseed-goal032-test` — PASS `2/2`, seed `32003202`.
4. `phantom-local-play-readiness-test` — PASS `3/3`, seed `31003101`.
5. `phantom-restart-failure-recovery-goal030cp3-test` — PASS `3/3`, seed `30003003`.
6. `phantom-release-decision-rollback-goal030cp3-test` — PASS `3/3`, seed `30003004`.
7. Combined guarded invocation — BUILD SUCCESSFUL, 5 minutes 9 seconds.
8. Первый phantom-population-reset-documentation-goal032-test — FAIL: exact historical substring Следующий Goal033 ecology был удалён из current status.
9. После минимального восстановления historical parity при сохранённом BLOCKED status тот же documentation validator — PASS 1/1, seed 32003203, BUILD SUCCESSFUL.

Goal033 success suites, histogram simulation и final `jar` не запускались: causal predecessor отсутствует, hard acceptance chain не может честно начаться. Full soak не запускался.

## Production DB statement

Production DB `l2jmobiush5` не использовалась и не изменялась. DB-mutating regressions выполнялись только через existing guard против `127.0.0.1:3308/l2jmobiush5_phantom_test`; suites выполнили cleanup. Schema/migrations не менялись.

## Known limitations

- Living low/mid/high distribution, catch-up fence, continuous newcomers/archive и personality mix отсутствуют.
- Current Population level histogram не является dynamic canonical progression truth.
- Goal033 незавершён до causal Background prerequisite и нового полного run.
- Windows sandbox process startup возвращал `CryptUnprotectData failed: 2148073483`; read/build/test выполнялись разрешённым elevated process. Это не product blocker.

## Static, encoding and scope

- `apply_patch` был вызван один раз, но sandbox helper завершился до mutation из-за `CryptUnprotectData`; effective edit выполнен bounded UTF-8 chunk fallback с same-directory atomic move.
- No-other-chronicle: только HighFive; task packages read-only.
- mojibake-маркеры в изменённых файлах проверены: PASS.
- escaped Cyrillic в изменённых файлах проверены: PASS.
- strict UTF-8 decode: PASS; BOM отсутствует; temporary `*.goal033.tmp` отсутствуют.
- `git diff --check`: PASS; exact-path staging и push выполняются после финальной проверки.

## Commit/push

TASK разрешает baseline Git inspection, bounded diff/scope verification, exact-path staging, ordinary commit и push. Reset/rebase/force не используются.

Commit subject: `phantom(goal-033): add living population ecology`.

Commit SHA и push result будут в финальном сообщении; report-bearing commit не может самоссылочно содержать SHA.

## Итоговый блок

```text
Human-level rubber band: ABSENT
Living low/mid/high ecology: NO
Continuous newcomers: NO
Causal historical catch-up: NO
Personality archetype mix: NO
Goal032 reset/reseed compatible: YES
Production DB used by tests: NO
Next Goal: 034 — Automated black-box local stack acceptance
```
