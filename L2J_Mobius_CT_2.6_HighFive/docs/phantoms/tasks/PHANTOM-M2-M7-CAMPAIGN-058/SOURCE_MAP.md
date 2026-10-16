# SOURCE_MAP058 — проверено до выдачи, затем адресный read-first

Remote branch проверена при подготовке: `039d1dcb8b702285e4d0eaffbadec2cf9274d24b`. Пути относительно `L2J_Mobius_CT_2.6_HighFive`. Это навигация по исходникам, не утверждение готовности функций и не текущее состояние БД/Windows.

## Подтверждённые источники
| Источник | Что подтверждено чтением |
|---|---|
| `docs/phantoms/tasks/PHANTOM-M1-ACTION-LOOP-038/MILESTONES.md` | М2 companion → М3 social → М4 PvP/clans → М5 raids → М6 instances → М7 progression; М8 gear/М9 economy/М10 scale отдельно |
| `docs/phantoms/RELEASE_CONTRACT.md` | Накопительная ordinary поставка; classes не равны gameplay; прежний M1 gate заменяется только явной поправкой058 |
| `docs/phantoms/TASK_PACKAGE_STANDARD.md` | Формат TASK из19 разделов |
| `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md` | Initial exact-read<=12, symbol reads, короткие логи, targeted cadence, один final full verify |
| `java/org/l2jmobius/gameserver/model/chat/PlayerChatIngress.java` | `dispatchClient`, `dispatchPilot`; native channel delivery отдельно от DISPATCHED; pilot запрещает GM/slash и требует caller lease |
| `java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java` | Transport-neutral facade; invite/response identity, ACCEPTED/REFUSED/EXPIRED/REVALIDATION_FAILED; managed/client delivery различаются |
| `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java` | `invite/respond/leave/expel/transferLeader/observe/memberSnapshot/capabilities`, canonical PartySnapshot<=9 |
| `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` | Делегирование native PartyInvitationService через acquired Player; работа с live Party state, не своя фиктивная party |
| `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java` | Уже планирует heal/recharge/res/buff/protect/assist и dispatch через combat lease. До проверки нельзя объявлять отсутствующим или готовым wiring |
| `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java` | `observeActor`, `capabilities`, `professionTargets`; допустимый level даёт CANONICAL_QUEST_REQUIRED, а не сам profession transfer |
| `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java` | Native infrastructure/bootstrap, explicit config/owned lane; не запускать cleanup fixture на накопительной/user schema |

## Начальный product/test READ_SET (12)
1. `java/org/l2jmobius/gameserver/model/chat/PlayerChatIngress.java` — dispatch и observation boundary.
2. `java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java` — subscription/delivery; определить actual social ingress.
3. `java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java` — invite/respond/leave; cancellation/ownership.
4. `java/org/l2jmobius/gameserver/model/groups/PartyInvitationDelivery.java` — managed delivery implementation и регистрацию найти адресно.
5. `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java` — только нужные symbols: invitation/commit/follow/tactics/stop, не113KB целиком.
6. `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` — acquire/reference/invite/observe.
7. `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java` — plan/dispatch/support priority.
8. `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java` — follow/travel result/cancellation, уточнить symbols.
9. `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java` — current skills и learning mutations.
10. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — только constructor/composition party/social/action wiring.
11. `test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java` — ближайший native test pattern; свой fixture helper читать при необходимости дополнительно.
12. `build.xml` — только соответствующие targets/properties, никакого full dump.

Строки2/4/5/8/10/11 READ_SET — существующие точки из repo/его composition, их bodies не объявлены полностью отревьюенными автором пакета. Исполнитель обязан сверить необходимые участки перед правкой. Отсутствие exact symbol локально — уточнить по imports/references, не придумывать интерфейс.

## Поздние этапы: читать по необходимости, не заранее всё
М3: follow imports из PhantomSystem → semantic/conversation/social execution + ChatObservationService; существующие RU XML и `PhantomConversation*`, `PhantomSocial*`, `PhantomHumanized*` suites. М4: existing PvP/Clan affiliation services и `PhantomPvpSuite`. М5: existing RaidDecision/Readiness/Recruitment/Assembly/PartyTactics и соответствующие Raid suites. М6: existing Rift/QuestInstance services и native scripts; проверять actual supported template coverage. М7: progression backend/evaluator/catalog + `PhantomProgression*` suites/SkillTreeData/village_master. Для каждого открыть <=5 дополнительных связанных файлов за шаг и записать причину расширения, а не прекращать работу из-за формального quota.

Builder/runtime: `tools/phantom-runtime/Build-Package.ps1`, `Runtime.ps1`, `dist/START_HERE_RU.md`; реальные команды/отдельные порты уточнить по ним. Предыдущие tested normal tools доступны как snapshots057 — только reuse/точная058 adaptation, никаких G6 tools. Источники навыков/data являются истиной, forum-таблицы и догадки об уровне профессии не заменяют код.

## Ближайшие известные риски
M1 cap8/prewarm и медленный catch-up не доказаны исправленными; status counters не дают live XYZ. Наличие old accepted release docs от сентября не закрывает новые gameplay milestones. Состав/счётчики95/11 и running PID — historical до fresh проверки. Времена/regressions сохранять с semantic source и JAR, а не переносить PASS по совпадению имени функции.
