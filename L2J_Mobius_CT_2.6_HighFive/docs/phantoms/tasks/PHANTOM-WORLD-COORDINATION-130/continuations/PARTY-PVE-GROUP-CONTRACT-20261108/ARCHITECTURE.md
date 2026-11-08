# Архитектурный контракт реализации — согласовано с пользователем

## 0. Primary invariants
1. **Individual Solo viability и group PvE eligibility — разные вопросы.** Для Solo продолжать использовать текущий `L2jPhantomBackgroundAuthority.viableTarget()` и native/no-free-reward guards. Для Party source discovery ни один кандидат не обязан самостоятельно победить NPC.
2. **Изучено/доступно ≠ потенциально.** Durable class/learned skill/equipment могут доказать потенциальную роль; native `MemberSnapshot` доказывает реальную пригодность `readyNow`. Нет Player — нет права утверждать readyNow.
3. **Групповые роли не обязаны быть уникальны.** Guardian может вносить реальный weapon DPS, spoiler — damage+loot, support — допустимый secondary damage; одновременно зафиксировать одну основную логическую роль без потери возможности выполнять другие законные действия.
4. **Лидерство — административное право stock Party, а не урон.** Native Party `leader` должен соответствовать штатной структуре invite/accept. Не назначать человеку/фантому чужие права.
5. **No fake survivability.** Агрессия танка — только штатный skill и реальная observed threat. Healing — только штатный Skill/MP+HP delta и его actual effect, без виртуального восстановления. Если текущая группа не готова — WAIT/REPLAN или добор реального участника.
6. **Lifecycle CAS** и Background receipts/owned STORE/F7/F8/SQL identity неприкосновенны.
7. **Стабильный groupId при 0 World**; native epoch/Party может завершиться; без обнуления правильной логической группы.

## 1. Единственное правило уровней для Party FARM
Для каждого участника i с player-level `L_i`, допустим NPC level `N ∈ [L_i−4,L_i+4]`.
Общий диапазон: `minNPC = max(1, max_i(L_i) − 4)`, `maxNPC = min_i(L_i) + 4`. Он существует, когда `minNPC <= maxNPC`.
Не ограничивать NPC верхней границей level85 (персонажи максимум85, монстры могут быть 86+). Для Solo не менять `LOWER_LEVEL_TIERS=2,5,10` и `LEVEL_RADIUS=2`.

| Уровни состава | Полоса NPC (inclusive) |
|---|---|
| [76,76] | 72..80 |
| [74,78] | 74..78 |
| [72,80] | 76..76 |
| [76,81] | 77..80 |
| [70,79] | EMPTY (не совместимы для общего farm, без телепорта/фальшивой цели) |
| [85,85] | 81..89 при наличии законных monster/spawn |

Реализация: маленькая чистая функция в существующей party/planner policy, single source of truth; вызывается при per-member source enumeration, формировании roster, activity revision, preflight и native target selection. При формировании group общая полоса MUST пересчитываться и подтверждаться перед CAS/публикацией Goal. После уровня-up пересчитать и REPLAN, не продолжать старую неподходящую цель.

## 2. Target discovery в 0-World
A. `previewLogicalRendezvous` читает ограниченный snapshot Goal/Background/Catchup, классовую направленность и durable learned capability evidence. Не создаёт Player, не меняет SQL, не дергает каждый tick все 5000.
B. `rendezvousSources(profileId, state, ...)` в **PARTY** режиме опирается на `suitableTargets` уровня member±4, L2J MONSTER/attackable/targetable, legitimate spawn area/anchor, структурный route + GK/Geo policy. **Не вызывает solo viableTarget**; не превращает `UNKNOWN` route в verified reachable и не делает paid GK бесплатно.
C. Пересечение реальных `source` у roster + range intersection. Никакого top-64-truncation до проверки общего источника: если источник существует в следующей bounded странице, использовать existing paging/cursor/fairness (лимиты обоснованные и нет full scan). 16-й peer после 15 unsuitable не пропадает.
D. Если есть только tank+healer, отсутствие доказанного DD readiness не делает PARTY admission фальшивым: LogicalParty может быть кандидатом в добор, но native PvE без достаточного общего урона запрещён. Отдельное reason, finite retry/nextDue, никакого бесконечного временного `RETRY`.
E. Идентичность общей цели — конкретный `npcId@anchorId` и допустимый уровень, не произвольная XYZ/посадка/отладочный spawn.

## 3. Stable logical roles и native projections
Развести три независимых атрибута (без обязательной новой БД-схемы):
- **Potential role**: прочитана из class lineage + durable skill/weapon/progression evidence; применяется для социальной совместимости в Background. Не означает готовую MP, действующее оружие и `readyNow`.
- **Native verified capabilities**: `L2jPhantomPartyBackend.nativeCapabilities()/MemberSnapshot`, read-only реальный Player и skill/equipment/MP/status.
- **Assigned role(s) for episode**: `PhantomPartyRoleMatcher.match`, текущая группа, один основной slot на персонажа, но фактический DPS/Heal/Tank/Spoil может быть мультифункциональным. Не подменять native при смерти/стане/регене.

Предлагаемые canonical primary role keys: `frontline.guardian`, `support.healer` / `support.recharge` / `support.enhancement`, `damage.melee` / `damage.ranged`, `utility.spoiler`. Если в текущих consumers есть legacy `damage/support`, сделать явную миграцию/совместимость и тест restart; НЕ удалять старую роль из persistent LogicalParty молча. `Member.role` — строка, проверь все exact-equals до изменения классификации.

Классовые archetypes — только подсказки. Для Phoenix Knight 70 learned Aggression + real weapon — потенциальный GUARDIAN + secondary DPS; для Cardinal 72 learned Heal — SUPPORT. Для dwarf со Spoil/Sweeper — SPOILER + optional DD. Результаты native `readyNow` добыть при materialization, не генерировать из персонажного класса.

Не свести SPOILER к бесполезному DD: если у группы есть действительный spoiler, предпочтения источников могут повышать ценность только монстров с **настоящими** spoil facts и доступными skill levels, но не выдумывать шанс, drop или item. Использовать `PhantomAcquisitionService` и stock corpse/sweep only, не писать второй state machine. Полная интеграционная групповая цепочка Spoil→kill→Sweep / конкуренция loot owners относится к TASK131; TASK130 обязана сохранить возможность и сделать корректную role/target evidence, без ложного полного Spoil PASS.

## 4. Лидер и roster
- LogicalParty допускает 2..9 реальных согласившихся пользователей системы (phantom profiles), no World precondition.
- Поиск лидера: выбрать deterministic eligible Profile/Party initiator, роль НЕ обязана равняться damage; до COMMIT проверить actual eligible, consent, [level range], objective/route, role potential, ownership.
- Лидер native Party — тот, кто реально исполняет официальный `PartyCoordinator.formForGoal`, invitation/accept; при смене native leader права штатные, без SQL-самоназначения.
- Нет обязательного хилера и нет обязательного танка. Два члена могут вести обычный фарм, если доказана реальная совместная жизнеспособность. Если недостаточна — группа ищет третий настоящий DD или более безопасный mob в пределах уровня ±4 (не понижать лимит до 66!) или WAIT.
- Поддерживать условные составы TANK+HEAL (может оказаться недостаточно DPS), DD+DD, DD+HEAL, TANK+DD, DD+SPOILER, TANK+DD+HEAL, TANK+DD+HEAL+SPOILER и 9-member. Каждый roster revalidate после native materialization; no false claim at zero World.
- Предпочтения seeking по уровням остаются прежними: <40 SOLO; 40–51 20%; 52–60 35%; 61–75 50%; 76–85 80%. Human invitations отдельная штатная семантика.

## 5. Party combat readiness — развить уже имеющийся groupViable
Фактическая цель: именно реальная цель в World, normal living Monster, без raid, targetability/reward/Geo, уровни ВСЕХ членов в ±4. Существующий `autonomousFarmTarget()` уже проверяет World/Geo/rewards и вызывает groupViable — дополняем, не дублируем.

- **Damage**: суммировать правдоподобный damage contribution всех имеющих настоящее оружие/skills и право действовать (в т.ч. guardian/spoiler, если могут атаковать); не исключать танка только потому, что он guardian, и не удваивать одного участника при multi-role. MP/shot/skill/attack ranges и реальные cooldown проверять перед actual action; projected DPS не считается настоящим убийством.
- **Aggro**: отдельные threat/target observations; если guardian имеет learned Aggression и ресурс, можно предложить тактику, НО для начала небезопасного боя не считать защиту остальных гарантированной до фактического native cast + наблюдения NPC target/AI. Изменения intent/target — через stock `L2jCombatBackend.npcAggroSkill/castNpcAggro` и существующего PartyCoordinator/tactics.
- **Healing**: поддержка оценивается из настоящих изученных skills, cast/MP/range/cooldowns, не добавляет HP в сохранённые состояния заранее. Actual healing evidence — реальные before/after HP+MP и actual native Heal (обычный человеческий Heal без регрессии).
- **Threat budget**: нельзя приписывать каждому участнику весь incoming damage одного и того же NPC без модели угрозы; но нельзя и обнулять урон DD/хилера. Консервативно учитывать реальные текущие attack targets, risk of aggro shift, добавочных мобов, hazards. Unknown state -> WAIT/REPLAN, никогда PASS на недоказанных защитных предпосылках.
- **Resource budget**: damage throughput и прогноз времени, нижняя оценка HP/MP, реальные offensive shots только за счёт имеющихся, spoilers не требуют быть главными DD. Отдельные reason codes insufficient DPS / MP / heal / threat / invalid target / awaiting tank aggro.
- Нет team-wide универсального баффа и «free mitigation»; actual rewarded combat via existing native Combat/economy. No synthetic EXP/SP, no fake corpse.

## 6. Lifecycle and boundaries
Вся Party-кандидатура предварительна до `REQUEST→YIELD→CHECKPOINT→CAS TRANSFER→RECOVER→ACTIVE` и exact native lease. Не начинать новую Party если Child Supply/Learning/Commerce owns effect; не разрывать ongoing Store/Trade/Combat.
Сохранять untagged F7 receipt checks, `_historicalBackgroundService.prepareCurrentAuthority` и COMPLETE historical hashes (already native verified). Group wake only typed Party demand; no arbitrary teleport/materialization.
При расхождении role/roster/source: bounded WAIT_REPLAN/nextDue, не «поменять цель на другую» после CUT, не терять groupId/receipts. При уходе наблюдателей безопасный materialization drain только после настоящего STOP/STORE, не на глазах/не пока combat, trade, Party с человеком.

## 7. Audit observability
Только существующие flight recorder/status/control ports + один bounded trace/counter API. Для каждой стадии: knowledge NPC candidate count, solo-filter in PARTY (должно быть 0 вызовов), anchors, routing categories, legal group overlap, member-level bounds, missing role evidence, groupViable reason, native result. Лимит ≤64 деталей/профиль/эпизод, aggregate counters; не записывать все 5000 профилей/каждый тик.
Не объявлять `NATIVE130_USEFUL_GROUP_PASS` без stock Party, mob damage, earned reward и соблюдения lifecycle; SQL/MODEL отдельно.
