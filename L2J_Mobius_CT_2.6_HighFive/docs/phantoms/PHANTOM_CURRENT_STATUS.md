# Phantom World: текущий статус

Дата сверки: 2026-09-04. Source of truth для release slice — `test/resources/phantoms/release/goal030-release-coverage.tsv` и принятый `docs/phantoms/reports/030-checkpoint-3-release-decision.md`; для operator reset/tuning и Roadmap v3 — `docs/phantoms/reports/032-phantom-reset-operator-control.md`; для Goal033 blocker — `docs/phantoms/reports/033-living-population-ecology.md`. Статусы полного vision основаны на production code/data/tests, а не на историческом номере Goal.

| Capability/domain | Implementation status | Release evidence | Known limitation | Next action |
|---|---|---|---|---|
| **Goal030 accepted 20-domain release slice** | **20 covered / 0 pending; ACCEPT** | Goal030 CP1/CP2/CP3 | Shipped disabled/fail-closed; это не весь original vision | Поддерживать release gates |
| **Goal032 Phantom-only reset/reseed** | **SUCCESS** | Goal032 ownership 3/3; reset/reseed 2/2; focused regressions | Shared state без безопасного owner блокирует reset | Следующий Goal033 ecology: BLOCKED; сохранять reset ownership |
| **Goal033 living population ecology** | **BLOCKED_033_CAUSAL_CATCHUP_ENTRYPOINT_REQUIRED** | Read-first Background/Population/Progression/Social audit; Goal032/031/030 regressions PASS | Нет bounded deterministic Background entrypoint для canonical target/anchor, exactly-once interval cursor и materialization fence; direct XP запрещён | Отдельный causal Background prerequisite, затем повторить Goal033 |
| Fresh bootstrap | IMPLEMENTED_AND_RELEASE_COVERED | matrix `fresh-bootstrap`; `prepare-phantom-test-db` | Production schema apply только явным installer action | Preflight перед запуском |
| Population | IMPLEMENTED_AND_RELEASE_COVERED | matrix `population`; `phantom-population-server-integration-test` | Target/caps задаются config | Preset 10/5 для local play |
| Progression | IMPLEMENTED_AND_RELEASE_COVERED | matrix `progression`; `phantom-progression-production-composition-test` | Использует accepted High Five capability catalog | Поддерживать catalog parity |
| Activity/materialization | IMPLEMENTED_AND_RELEASE_COVERED | matrix `activity-materialization`; Goal030 CP2 | Число real `Player` ограничено cap | Следить за status/overload |
| Topology/navigation/knowledge | IMPLEMENTED_AND_RELEASE_COVERED | matrix `topology-navigation-knowledge`; focused parity tests | Без geodata навигация DEGRADED | Добавлять geodata локально, не коммитить |
| Combat | IMPLEMENTED_AND_RELEASE_COVERED | matrix `combat`; `phantom-combat-server-integration-test` | Не является siege AI | Сохранять native combat owner |
| Farming | IMPLEMENTED_AND_RELEASE_COVERED | matrix `farming`; `phantom-farming-goal024a-test` | Bounded policies/claims | Только focused extensions |
| Acquisition/spoil | IMPLEMENTED_AND_RELEASE_COVERED | matrix `acquisition-spoil`; Goal021 tests | Не generic quest solver | Не расширять молча до quests |
| Craft/trade/commerce/economy | IMPLEMENTED_AND_RELEASE_COVERED | matrix `craft-trade-commerce-economy`; Goal014/022 tests | Bounded reservation/offer lifecycle | Сохранять anti-dup contracts |
| Party | IMPLEMENTED_AND_RELEASE_COVERED | matrix `party`; `phantom-party-server-integration-test` | Accepted recruitment/consent scope | Переиспользовать native Party |
| Rift | IMPLEMENTED_AND_RELEASE_COVERED | matrix `rift`; `phantom-rift-goal023c-test` | Только accepted Dimensional Rift route | Separate slice для иных instances |
| PvP/PK/karma | IMPLEMENTED_AND_RELEASE_COVERED | matrix `pvp`; `phantom-pvp-goal025a-test` | Policy ограничивает escalation | Сохранять safety gates |
| Raid | IMPLEMENTED_AND_RELEASE_COVERED | matrix `raid`; Goal026 encounter tests | Accepted encounter profiles, не sieges | Separate siege slice |
| Conversation/semantic/social | IMPLEMENTED_AND_RELEASE_COVERED | matrix `conversation-semantic-social`; Goal018–020 tests | RU corpus bounded/versioned | Расширять только с corpus evidence |
| Clans/alliances/reputation/wars | IMPLEMENTED_AND_RELEASE_COVERED | matrix `clans-alliances-reputation-wars`; Goal027 CP1/CP2 | Siege registration/attack отсутствуют | Goal027 считается ACCEPT через Goal030 gate |
| Restart/failure recovery | IMPLEMENTED_AND_RELEASE_COVERED | matrix `restart-failure-recovery`; Goal030 CP3 | Shutdown может требовать bounded retry | Drain до STOPPED перед restart |
| Operator observability/replay | IMPLEMENTED_AND_RELEASE_COVERED | matrix `operator-observability-replay`; Goal028 targets | Trace disabled by default | Включать diagnostics точечно |
| Scale/soak/overload | IMPLEMENTED_AND_RELEASE_COVERED | matrix `scale-soak-overload`; Goal029 CP2/CP3 | Accepted envelope не является новым gameplay | Не повторять soak без причины |
| Disabled regression | IMPLEMENTED_AND_RELEASE_COVERED | matrix `disabled-regression`; Goal030 CP1 | Safe checkout не создаёт population | Явно применять preset |
| Rollback/release control | IMPLEMENTED_AND_RELEASE_COVERED | matrix `rollback-release-control`; Goal030 CP3 | Transient shutdown допускает retained-owner retry | `drain` -> `disable` |
| **original master-plan full vision** | **Не входит целиком в Goal030 release slice** | Сверка Goal031 code/data/tests | Нижеследующие gaps нельзя называть готовыми | Отдельные gameplay slices и full gate |
| Siege AI: registration/schedule/gathering/roles/attack/defense/retreat | DEFERRED_NOT_IMPLEMENTED | Native `SiegeManager` существует, Phantom siege owner/data/test отсутствуют | Нет автономного siege lifecycle | Будущий отдельный siege Goal |
| Q102/Q152 kill/collection item-drop acquisition subset | IMPLEMENTED_OUTSIDE_MATRIX | `PhantomAcquisitionQuestCatalog`, `high-five-quest-collection-v1.xml`, `PhantomAcquisitionQuestSuite` | Quest уже должен быть STARTED; нет start/advance/complete | Сохранить как bounded acquisition evidence |
| Generic whitelist quest adapter | PARTIAL | Только два catalog-driven drop subset выше | Нет общего quest lifecycle adapter/solver | Будущий quest slice |
| Class quest automation | DEFERRED_NOT_IMPLEMENTED | Production owner/data/test не найдены | Нет class-transfer quest execution | Будущий quest slice |
| Kamaloka | DEFERRED_NOT_IMPLEMENTED | Phantom production owner/data/test не найдены | Instance flow не реализован | Будущий instance/quest slice |
| Pailaka | DEFERRED_NOT_IMPLEMENTED | Phantom production owner/data/test не найдены | Instance/quest flow не реализован | Будущий instance/quest slice |
| Full-scope release gate после gameplay gaps | DEFERRED_NOT_IMPLEMENTED | Goal030 gate относится только к 20-domain slice | Нельзя переименовывать Goal030 в full vision | Gate после siege и quest/instance slices |

## Roadmap v3

| Goal | Статус | Bounded outcome |
|---|---|---|
| Goal032 — Phantom-only reset/reseed + operator tuning | SUCCESS | Preview/confirm/cancel, exact private cleanup, optional reseed, tuning guide |
| Goal033 — Living population ecology | BLOCKED_033_CAUSAL_CATCHUP_ENTRYPOINT_REQUIRED | Product code/config не добавлены; нужен bounded Background planning/capture/cursor/materialization prerequisite без direct XP |
| Goal034 — Automated black-box local stack acceptance | PLANNED | Real LoginServer/GameServer on guarded throwaway DB без ручного QA |
| Goal035 — Siege gameplay slice | PLANNED | Native siege registration/schedule/gathering/roles/attack/defense/retreat |
| Goal036 — Bounded quests/instances slice | PLANNED | Whitelist lifecycle, class transfer, supported Kamaloka/Pailaka |
| Goal037 — Final full-vision release gate + freeze | PLANNED | Finite final acceptance; после ACCEPT нет automatic new goals |

Shipped config по-прежнему `EnablePhantomSystem=False`, population/ACTIVE `0/0`; destructive auto-reset flag отсутствует. Reset вызывается только GM-командой после read-only preview и одноразового confirm. Automated tests работают только с allowlisted test DB; ручная игра пользователя остаётся финальной experience validation, а не промежуточным техническим gate.

`UNKNOWN_REQUIRES_EVIDENCE` после аудита не осталось: каждый спорный пункт либо имеет конкретный owner/test, либо явно не найден и отложен. Эта таблица не меняет исторические ACCEPT baselines.
