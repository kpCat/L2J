# Phantom World: текущий статус

Дата сверки: 2026-09-09. Source of truth для release slice — `test/resources/phantoms/release/goal030-release-coverage.tsv` и принятый `docs/phantoms/reports/030-checkpoint-3-release-decision.md`; для operator reset/tuning и Roadmap v3 — `docs/phantoms/reports/032-phantom-reset-operator-control.md`; для historical Goal033 blocker — `docs/phantoms/reports/033-living-population-ecology.md`; для текущего повтора Goal033 — `docs/phantoms/reports/033-living-population-ecology-resume.md`; для historical Goal033A blocker — `docs/phantoms/reports/033A-causal-background-catchup.md`; для Goal033A SUCCESS — `docs/phantoms/reports/033A-causal-background-catchup-resume.md`; для закрывшего topology blocker Goal033A1 — `docs/phantoms/reports/033A1-canonical-population-topology-ingress.md`; для Goal034 closure8 `SUCCESS` — `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-closure8-final.md`; для Goal035 `SUCCESS` — `docs/phantoms/reports/035-siege-gameplay.md`; для Goal036 `SUCCESS` — `docs/phantoms/reports/036-bounded-quests-instances.md`. Статусы полного vision основаны на production code/data/tests, а не на историческом номере Goal.

| Capability/domain | Implementation status | Release evidence | Known limitation | Next action |
|---|---|---|---|---|
| **Goal030 accepted 20-domain release slice** | **20 covered / 0 pending; ACCEPT** | Goal030 CP1/CP2/CP3 | Shipped disabled/fail-closed; это не весь original vision | Поддерживать release gates |
| **Goal032 Phantom-only reset/reseed** | **SUCCESS** | Goal032 ownership 3/3; reset/reseed 2/2; focused regressions | Shared state без безопасного owner блокирует reset | Поддерживать regression gates |
| **Goal033 living population ecology** | **SUCCESS** | Focused 7/7; production-composed LIVING 10/5 2/2; Goal033A 4/4; Goal032/031/030CP3 DB regressions PASS на `l2jmobiush5_phantom_test` с cleanup | Goal036 доказал только bounded Q401 Fighter→Warrior; universal profession realism не заявлен | Поддерживать accepted regressions; production `l2jmobiush5` не использовать |
| **Goal033A causal historical Background catch-up** | **SUCCESS** | Real-data planner, lifecycle baseline, atomic minute cursor, restart/death semantics и fences; focused tests + final jar PASS | Не является отдельным ecology engine | Используется Goal033 |
| **Goal033A1 canonical population topology ingress** | **SUCCESS; topology blocker CLOSED BY Goal033A1** | 38/38 exact ingress; 7 real farms; 80/80 factual Background edges; Goal033A1 4/4; focused regressions PASS | Historical catch-up закрыт Goal033A | Используется Goal033A и Goal033 |
| **Goal034 automated black-box local stack acceptance** | **SUCCESS на closure8** | Standalone manor `2/2`; checkpoint2 aggregate `56/56`; fresh full verify и standalone jar PASS; real run `20260908-135425-0b7d8b31` — gen1/gen2 `5/5/5`, два native restart/drain, continuity и exact cleanup PASS | Verify-only `[Player._skillListTask]` не воспроизведён (`NOT_REPRODUCED`); semantic fix не вносился | Поддерживать accepted regression evidence |
| Fresh bootstrap | IMPLEMENTED_AND_RELEASE_COVERED | matrix `fresh-bootstrap`; `prepare-phantom-test-db` | Production schema apply только явным installer action | Preflight перед запуском |
| Population | IMPLEMENTED_AND_RELEASE_COVERED | matrix `population`; `phantom-population-server-integration-test` | Target/caps задаются config | Preset 10/5 для local play |
| Progression | IMPLEMENTED_AND_RELEASE_COVERED | matrix `progression`; `phantom-progression-production-composition-test` | Использует accepted High Five capability catalog | Поддерживать catalog parity |
| Activity/materialization | IMPLEMENTED_AND_RELEASE_COVERED | matrix `activity-materialization`; Goal030 CP2 | Число real `Player` ограничено cap | Следить за status/overload |
| Topology/navigation/knowledge | IMPLEMENTED_AND_RELEASE_COVERED | matrix `topology-navigation-knowledge`; focused parity tests | Без geodata навигация DEGRADED | Добавлять geodata локально, не коммитить |
| Combat | IMPLEMENTED_AND_RELEASE_COVERED | matrix `combat`; `phantom-combat-server-integration-test`; Goal035 siege branch; Goal036 owner-tagged content branch | Goal035 ограничен bounded Giran; Goal036 fallback доступен только exact content session | Сохранять native combat owner |
| Farming | IMPLEMENTED_AND_RELEASE_COVERED | matrix `farming`; `phantom-farming-goal024a-test` | Bounded policies/claims | Только focused extensions |
| Acquisition/spoil | IMPLEMENTED_AND_RELEASE_COVERED | matrix `acquisition-spoil`; Goal021 tests | Не generic quest solver | Не расширять молча до quests |
| Craft/trade/commerce/economy | IMPLEMENTED_AND_RELEASE_COVERED | matrix `craft-trade-commerce-economy`; Goal014/022 tests | Bounded reservation/offer lifecycle | Сохранять anti-dup contracts |
| Party | IMPLEMENTED_AND_RELEASE_COVERED | matrix `party`; `phantom-party-server-integration-test` | Accepted recruitment/consent scope | Переиспользовать native Party |
| Rift | IMPLEMENTED_AND_RELEASE_COVERED | matrix `rift`; `phantom-rift-goal023c-test` | Только accepted Dimensional Rift route | Separate slice для иных instances |
| PvP/PK/karma | IMPLEMENTED_AND_RELEASE_COVERED | matrix `pvp`; `phantom-pvp-goal025a-test` | Policy ограничивает escalation | Сохранять safety gates |
| Raid | IMPLEMENTED_AND_RELEASE_COVERED | matrix `raid`; Goal026 encounter tests | Accepted encounter profiles, не sieges | Не смешивать с bounded Goal035 siege owner |
| Conversation/semantic/social | IMPLEMENTED_AND_RELEASE_COVERED | matrix `conversation-semantic-social`; Goal018–020 tests | RU corpus bounded/versioned | Расширять только с corpus evidence |
| Clans/alliances/reputation/wars | IMPLEMENTED_AND_RELEASE_COVERED | matrix `clans-alliances-reputation-wars`; Goal027 CP1/CP2; Goal035 native attacker registration | Siege extension ограничен Giran Goal035 | Goal027 считается ACCEPT через Goal030 gate |
| Restart/failure recovery | IMPLEMENTED_AND_RELEASE_COVERED | matrix `restart-failure-recovery`; Goal030 CP3 | Shutdown может требовать bounded retry | Drain до STOPPED перед restart |
| Operator observability/replay | IMPLEMENTED_AND_RELEASE_COVERED | matrix `operator-observability-replay`; Goal028 targets | Trace disabled by default | Включать diagnostics точечно |
| Scale/soak/overload | IMPLEMENTED_AND_RELEASE_COVERED | matrix `scale-soak-overload`; Goal029 CP2/CP3 | Accepted envelope не является новым gameplay | Не повторять soak без причины |
| Disabled regression | IMPLEMENTED_AND_RELEASE_COVERED | matrix `disabled-regression`; Goal030 CP1 | Safe checkout не создаёт population | Явно применять preset |
| Rollback/release control | IMPLEMENTED_AND_RELEASE_COVERED | matrix `rollback-release-control`; Goal030 CP3 | Transient shutdown допускает retained-owner retry | `drain` -> `disable` |
| **original master-plan full vision** | **Не входит целиком в Goal030 release slice** | Сверка Goal031 code/data/tests | Нижеследующие gaps нельзя называть готовыми | Отдельные gameplay slices и full gate |
| Siege AI: registration/schedule/gathering/roles/attack/defense/retreat | IMPLEMENTED_BOUNDED_GOAL035; SUCCESS | Giran castleId=3; native `Castle`/`Siege`/`SiegeScheduleData`; focused `8/8`; affected routes `6/6`; full verify/jar PASS | Один bounded Giran vertical; capture/ownership transfer и universal multi-castle AI не обещаны | Расширять только отдельной явной задачей |
| Q102/Q152 kill/collection item-drop acquisition subset | IMPLEMENTED_BOUNDED_GOAL036; SUCCESS | Existing acquisition catalog/service composed into native Q102/Q152 start→completion; focused Goal036 `8/8` | Subset remains the only safe Background collection owner | Сохранять native QuestState/reward/chance authority |
| Generic whitelist quest adapter | IMPLEMENTED_BOUNDED_GOAL036; SUCCESS | Exact fail-closed catalog and caller-driven lifecycle for Q102/Q152 only | Не universal quest solver; arbitrary quest/HTML/bypass unsupported | Расширять только explicit content task |
| Class quest automation | IMPLEMENTED_BOUNDED_GOAL036; SUCCESS | Q401 + Fighter→Warrior only through `village_master.ElfHumanFighterChange1` | Иные class quests/transfers unsupported | Расширять только через audited normal-player owner |
| Kamaloka | IMPLEMENTED_BOUNDED_GOAL036; SUCCESS | Native template 57 lifecycle, boss callback, reuse/exit and cleanup | Только audited template 57 | Расширять отдельным audited template slice |
| Pailaka | IMPLEMENTED_BOUNDED_GOAL036; SUCCESS | Native Q128 / template 43 lifecycle, weapon handoffs, bosses, rewards/exit | Только Song of Ice and Fire | Расширять отдельным audited content slice |
| Full-scope release gate после gameplay gaps | DEFERRED_NOT_IMPLEMENTED | Goal030 gate относится только к 20-domain slice | Нельзя переименовывать Goal030 в full vision | Goal039 после Goal034–038 |

## Roadmap v5

| Goal | Статус | Bounded outcome |
|---|---|---|
| Goal034 — Automated black-box local stack acceptance | SUCCESS | Closure8: verify-only `[Player._skillListTask]` не воспроизведён (`NOT_REPRODUCED`); standalone manor `2/2`, checkpoint2 aggregate `56/56`, fresh full verify и standalone jar PASS; real run `20260908-135425-0b7d8b31` дал gen1/gen2 `5/5/5` с одинаковыми ID, два native restart/drain и exact cleanup без forced kill; production DB unused |
| Goal035 — Siege gameplay slice | SUCCESS | Bounded Giran castleId=3: native registration/schedule/sides/zones/doors, factual gathering, capped roles, shared Combat/Navigation/Party support, retreat/recovery/restart cleanup; focused `8/8` |
| Goal036 — Bounded quests/instances slice | SUCCESS | Exact whitelist: native Q102/Q152 completion, Q401 + canonical Fighter→Warrior, Kamaloka 57, Pailaka Q128/template 43; focused `8/8`, affected gates, fresh verify and standalone jar PASS; без universal solver |
| Goal037 — Full High Five quest-script inventory + rates normalization/parity | PLANNED | 100% `dist/game/data/scripts/quests/**`, no unclassified quest; reward/drop/rate-path и control/key/singleton exception classification; structural/AST-style corpus audit, compile/load corpus, deterministic 1x/non-1x matrix и representative real-server mechanics; Player/Phantom ACTIVE/BACKGROUND parity where applicable; canonical rates authoritative, bypass = diagnostic/gate |
| Goal038 — Humanized Russian Semantic Pack + social/custom conversation | PLANNED | Natural RU social/off-topic conversation, relationship progression, bounded personal memory, humor/sarcasm/teasing, follow-ups, emotion-sensitive reactions, personal ↔ game transitions, light flirt, contextual profanity с personality/relationship/intensity и anti-repeat; versioned custom overrides без Java recompilation, strict fail-closed core/custom validation; optional mature opt-in, shipped `OFF`; no runtime LLM/internet |
| Goal039 — Final full-vision release gate + freeze | PLANNED | Единственный final exam: safe install/config/defaults, Goal034 real stack, ecology/restart/recovery, siege, quests/instances, rates parity, Humanized/custom packs, scale/rollback и documentation consistency |

После успешного Goal036 остаются два content/feature stage — Goal037–Goal038
— и единственный final exam Goal039. После `ACCEPT` Goal039 статус становится
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`; automatic Goal040+ запрещены.

Текущее следующее действие: Goal037 — следующий planned feature Goal, но он
остаётся `NOT_STARTED` и требует отдельной явной задачи. Goal036 не начинал
Goal037 и не менял Roadmap v5 scope Goal037–039.

Shipped config по-прежнему `EnablePhantomSystem=False`, population/ACTIVE `0/0`; destructive auto-reset flag отсутствует. Reset вызывается только GM-командой после read-only preview и одноразового confirm. Automated tests работают только с allowlisted test DB; ручная игра пользователя остаётся финальной experience validation, а не промежуточным техническим gate.

`UNKNOWN_REQUIRES_EVIDENCE` после аудита не осталось: каждый спорный пункт либо имеет конкретный owner/test, либо явно не найден и отложен. Эта таблица не меняет исторические ACCEPT baselines.
