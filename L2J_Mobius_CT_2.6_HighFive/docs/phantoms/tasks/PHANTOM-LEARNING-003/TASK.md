# PHANTOM-LEARNING-003 — общий тактический нейроконтур 4–8 МиБ и первое обучаемое лечение

## 1. Идентификатор
`PHANTOM-LEARNING-003`. Одна самостоятельная задача в **новом** диалоге Codex. Уровень мышления **High**; при реальном multi-owner-архитектурном выборе — Very High. Результаты в отчёте на русском; Java идентификаторы на английском.

## 2. Цель, которую можно проверить
После SUCCESS-001 и READY_FOR_MANUAL_ACCEPTANCE-002 (операторская проверка 002 отложена владельцем) разработать **нейросетевую архитектуру тактики**, пригодную для разных профессий, уровней, экипировки, союзников, саммонов и противников, а не исключительно выбора Prominence/Fire Vortex. Встроить новую модель **порядка 4–8 МиБ весов** с двухступенчатым вычислением (состояние один раз → оценка ограниченных законных действий), версионированным хранением и сохранением старых весов. **Первая новая рабочая вертикаль — осмысленный выбор лечебного действия или получателя лечения в реальной Party с exact-native HP receipt.** После обучения и повторной загрузки модель должна влиять на этот выбор в разрешённой штатной ситуации и не нарушать правила боевой безопасности. Не выдавать untrained/рандомную сеть за обученный интеллект.

## 3. Зависимости и исходники
GitHub `https://github.com/kpCat/L2J`; модуль `L2J_Mobius_CT_2.6_HighFive`. Точный базовый HEAD `b7b5575457c12c1b0438bcbaa646675a67778391`, ветка `wip/phantom-learning-002`; актуальность сверить read-only. Локальный пользовательский Git-root `D:\Codex\repos\L2J-phantom-world` — READ ONLY. Историческая ветка `wip/coordination-130-preserved` = `c5b81f89ce02030eff8441395b0411137a110b2f`, `feature/phantom-world` = `faec72161f9e4f6231764e17d6f2637a4db080af`. Их не менять.

У LEARNING-002 есть независимый директор и Windows TEST-контур с 34 COMMITTED/35 INCOMPLETE/117 exact NATIVE events, TEST ACCEPTED v1 и действующей инструкцией `tools/phantom-runtime/training/RUNBOOK_RU.md`. Он не является доказательством общей нейросети или обучения хилов. Прочитать `docs/phantoms/tasks/PHANTOM-LEARNING-002/HANDOFF.md`, `docs/phantoms/reports/PHANTOM-LEARNING-002.md`, `DESIGN_DECISIONS.md`, `RESULT.md` и этот пакет. Не трогать `.phantom-local` предыдущего независимого clone, кроме разрешённого read-only анализа; native прогон в собственном TEST root LEARNING-003.

## 4. Контекст и требования владельца
5000 автономных профилей и >=128 реально видимых materialized отдельно от 250 офлайн-торговцев. Реальная экономика: EXP/SP, Adena, loot, spoil/sweeper, бесплатных наград нет. Живые Party, PvE/PvP/PK, кланы, рейды, TW, осады, Олимпиада. Важна индивидуальная тактика класса: Bishop/Cardinal с десятками навыков; Prophet sleep/root/MP burn/dispel; кинжальщик спина/фланг; лучник держит дальность; Destroyer/Tyrant лимиты; саммонер управляет хозяином + существом, Heal/UD/resummon; будущий подход к цели с GeoData. Нельзя навязать всем один дамажный алгоритм.

Нужно учитывать разницу уровней и экипировки **вместе**, например Archer 61 против Mage 68+: не предполагать универсальную победу лучника; учитывать реальные grade, M.Def бижутерии, маг. уровень атак, сопротивления, бафы/дебафы и класс. Результат не хардкодить: при экстремально разной экипировке исход может меняться.

## 5. Обязательный быстрый pre-audit
Прочитать только relevant bounded sections `Agents.md` (старые абсолютные пути могут быть устаревшими), `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, текущий HANDOFF002, SOURCE_MAP, существующие реальные source seams. Проверить `git status`, remote, SHA, отсутствие пользовательских правок в своём clone. Составить в PLAN короткую карту возможностей `action → owner → lawful candidates → native receipt → learning scope` до кодинга. Не начинать очередную полную архитектурную ревизию всех исторических задач.

## 6. Новое архитектурное решение
Создать **версионированный `TacticalObservationV3`** и `LegalActionV3` (ACTOR/ALLY/SUMMON/ENEMY, skills/effects/gear/geometry/observed past actions; exact sources + missing masks). Не хранить список из всех комбинаций. Генератор кандидатов возвращает только действия, доступные исходному GameServer/owner; модель выбирает ПРИОРИТЕТ И ЦЕЛЬ, а реальные Java-обработчики исполняют и ещё раз проверяют validity/cooldown/MP/LOS/range/lease. Сохранить исходную модель и codec V1 неизменными.

Нейросеть V3: **одна общая модель 4–8 МиБ (не 5000 сетей)**, общая для классов, с кодировщиком состояния и отдельным кодировщиком предложенного действия. Состояние и список союзников/врагов/саммонов кодировать один раз на решение, затем bounded дешёвый action ranking. Обучение по наблюдаемому реальному результату, checkpoint с новым идентификатором алгоритма, fingerprint схемы, SHA. Возможен pure Java CPU inference или существующая библиотека только при обоснованном benchmark и отсутствии существенного лицензирования/зависимостей. GPU-trainer оставить на 004; это не ограничивает будущий CUDA/ONNX экспорт.

## 7. Обязательная функциональность V3
A. Новый стабильный формат боевого снимка: класс/уровень; экипировка включая оружие, броню, **бижу**, заточку, фактические P/M Atk/Def, elemental/skill effects; конкретные бафы/дебафы с доступной длительностью и mask, cooldown и возможности; разница уровней/магических уровней; дальность, направление/спина-фланг и LOS/Geo; состав Party до 9, HP/MP/CP, саммон/питомец (bounded) и несколько противников (bounded). У real players только законно наблюдаемые сведения: никогда не читать скрытый чужой инвентарь/кулдауны, если они не наблюдаемы; при неизвестном — UNKNOWN, а не фиктивный факт. Одна и та же схема для SOLO и PARTY.
B. Каталог типов действий для будущих DAMAGE, HEAL, RECHARGE, RESURRECT_SKILL, CONTROL, DISPEL, MOVE/RETREAT, SUMMON, SPOIL, SWEEP и иных; версия, тип, skill/effect id, допустимая цель, доступные штатные owner APIs, cost, expected latency, status. **На этом этапе обучать и применять реально только DAMAGE (сохранить совместимость) + HEAL**. Остальные типы — типизированный контракт и legality tests, не маскировать отсутствие native атрибуции.
C. Ограниченный chooser с fallback: `OFF/SHADOW/ON`, never bypass emergency heals/stock priority; для хилера не перехватывать действующее action ownership, выбирать среди допустимых HEAL целей/вариантов на штатной Party-подсистеме. Сохранить простой fallback и не прерывать канонический каст. Никаких SQL запросов, Dijkstra/A* или обучения в combat tick.
D. **Нативный опыт лечения:** только HP реально восстановлено штатным `Skill`/Player/party receipt под совпавшим owner/epoch/skill/target и action generation; затраты MP/время/неэффективный overheal; отсутствие двойного credit. Не использовать `heal issued` без точного positive HP delta. Не ставить чужих под удары ради reward. В архитектуре предусмотреть последующие результаты CONTROL/RESURRECT/MOVE с отдельными receipt типами.
E. Отдельный trainer/checkpoint для V3, load/rollback/SQL+portable export. Не перезаписывать V1/002 и их принятую модель. Если данных мало, TRAIN и SHADOW доступны, но ON включать только для проверенного TEST-кандидата с independent audit и безопасным native proof. Запрещено «улучшение» от random init за счёт тестовой подгонки.

## 8. Что сделать в реальной игре
- Сохранить исходный native DAMAGE выбор Solo/Party из LEARNING-002; подтвердить простой штатный каст + receipt на изменённом JAR. НЕТ требования переобучать V3 DAMAGE: прежняя модель выступает production fallback.
- Подготовить в собственной TEST-среде действительно обученного Bishop/Cardinal или другой проверенной native class healer с двумя **законными** вариантами лечения (два допустимых союзника / два навыка, без искусственного post-CUT повреждения). По реальному HP-write и затратам MP доказать учебный HEAL sample; состав и возможности читаются из действительного High Five, не угадывать ID скиллов.
- До боя выполнить preflight: 2 admissible competing actions; после эпизода выдать раздельные train/tuning/audit; после обучения и холодной загрузки модель в одной законной ограниченной ситуации влияет на HEAL selection и native каст/HP. Если это конфликтует со спасением критически больного, фиксированное правило спасения выигрывает; подобрать допустимую сцену без риска. Нельзя рисовать фальшивое изменение действия.
- Чтобы исключить недельный цикл, использовать уже готовый Training Director и собственный новый TEST root, а не опять доказывать Windows Job / экспорт / SQL initial bootstrap; только changed scope + focused regression и краткий native smoke.

## 9. Конфиг
Поддержать `modelAlgorithmV3`, `featureSchemaV3`, `learningActionFamilies=DAMAGE,HEAL` и лимиты `maxEntities`, `maxActions`, `decisionCooldownMillis` с действительными безопасными defaults; пользовательские настройки для выбора модели/ветки меняются только на безопасной границе. Предпочесть отдельный `TrainingV3.ini` или расширение существующего `TRAIN_CONFIG.ini` с обратно совместимыми ключами; не сломать существующие TRAIN_*.cmd. Не добавлять ещё семь новых Windows launchers.

## 10. Производительность
Целевой вес 4–8 МиБ, контрольный SHA и форматы; размер не должен быть результатом нулевого дополнения. Профилировать 128 логических актёров и типичные 4/8/16 кандидатов на реальных feature layout, отдельно snapshot/encode/score/native dispatch. Измерить p50/p95/p99, allocations, CPU и worst failure, добавить ограничение вызовов на actor. **Если 4–8 МиБ не укладывается в разумный бюджет, адаптировать внутреннюю архитектуру или двухступенчатый отсев, а не включать медленную модель в GameServer.** В доказательстве FULL нужен реально пригодный производственный CPU-budget либо безопасный OFF/shadow. Ни GPU-инференс, ни IPC-демон на каждый тик сейчас не требуются.

## 11. Конкурентность/lifecycle
Повторно использовать действующие Combat/Party ownership/leases. Состояние отдельной модели immutable после загрузки. Никаких потоков на фантома, редактирования модели в игровом JVM, long-lived worker на каждого актёра. Проверки source generation, target identity, reclaim, player death, party roster изменение, skill reload, stop; fallback без вызова Java action при stale authority.

## 12. БД и транзакции
Версионированные immutable модели, feature schema/normalization hash, action space version, sample origin (`NATIVE` vs `SYNTHETIC`/`TEACHER`), action identity, outcome/reward, SHA256, dataset split, fresh independent test, source provenance, reversible admission. Отдельная migration только собственной TEST schema, без переписывания существующих 002 tables или player data. Не смешивать actual effect с supervised teacher. Нельзя автоматом переносить TEST веса в основной мир.

## 13. Тесты
Смотреть `TEST_CASES.md`. В приоритете чистые unit/model tests (<несколько минут), проверка V1 compatibility, канонический native Heal proof, один финальный targeted smoke. Не запускать снова full 002 lifetime/Crash/125 migrations/UI-close proofs, пока их код не менялся.

## 14. Приёмка
Требуется ВСЕ: V3 learner 4–8 МиБ (реальные матричные веса) и format SHA; переменный состав party/summon/enemy; реальные gear/level/effects/position факты и missing masks; безопасный legality engine; реальные HEAL receipt и хотя бы один новый обученный HEAL choice с native game effect после SQL reload; сохранение боевой V1/002 регрессии; independent audit как минимум по нескольким actor/context; измеренный CPU-budget без hot SQL; protected paths неизменны; отчёт с прямыми логами, SHA, scope и GitHub HEAD. Если техническая среда препятствует, Codex самостоятельно решает setup в собственном TEST, без искусственных лимитов native boots; не использовать подмену доказательств.

## 15. Отчёт
В `docs/phantoms/reports/PHANTOM-LEARNING-003.md` на русском: что было/стало в игре, какие классы/уровни/gear/effects проверены; какие HEAL/ATTACK реально исполнены, последовательность выбора и истинный HP/MP, ошибочные кандидаты, изменение после модели; сколько independent episodes, качество vs baseline и per-class; p95/CPU/RAM, SHA модели, полный список затронутых путей, P1/P2, что НЕ проверено, статус. Отдельно краткий `docs/phantoms/tasks/PHANTOM-LEARNING-003/HANDOFF.md` для 004.

## 16. Commit/push
Разрешены только scoped add, обычный commit и **non-force push** в новую `wip/phantom-learning-003`. Модельные веса тестовых актёров/SQL/logs — в excluded local. В Git допускаются маленькие manifest и примеры доказательств, но не огромные бинарники и секреты. Проверить ветки protected refs до/после, exact modified files, `git diff --check`, mojibake, escaped Cyrillic.

## 17. Если есть препятствие
Не останавливаться из-за собственных путей/JAR/test Geo/SQL — решать в рамках собственной среды. После одного причинного native провала локализовать безбюджетными targeted checks и затем запускать только проверочный native. **Не продлевать один диалог неделями**: при архитектурном конфликте приоритета HEAL/Combat owner описать выбор и безопасное решение; если требует изменения игровых правил, остановить этот риск и уведомить владельца. Never fake success.

## 18. Что НЕ входит
Не строить заново директор, LoginServer, native fixture, Гео, экономику, HEAL.java; не переписывать все профессии или L2J Mobius; не делать PvP/PK/олимпийские бои/осады/территориальные войны, обучаемый pathfinding или отдельную semantic LLM; не строить CUDA/GPU training в 003. Эти направления запланированы после готового общего интерфейса.

## 19. Решение о продвижении
`SUCCESS-003` не означает, что 8-МиБ модель знает Lineage II. Оно означает: универсальный совместимый формат, реально работающий выбранный HEAL, безопасное native исполнение и возможность без переделки архитектуры наращивать разнообразие. Длительное обучение и качество всех классов проверять отдельно. Ручная приёмка TRAIN_*.cmd от владельца отложена по его воле; не делать её новым блокером текущего goal.
