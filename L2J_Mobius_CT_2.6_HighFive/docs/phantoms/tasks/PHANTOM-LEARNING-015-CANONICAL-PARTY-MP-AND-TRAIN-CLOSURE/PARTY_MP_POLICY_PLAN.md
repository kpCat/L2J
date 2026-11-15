# Goal015 — минимальное исправление общей Party MP policy

Статус: `APPROVED_FOR_IMPLEMENTATION`. Владелец отдельно утвердил этот план и одну общую production Party MP policy дельту с предварительным DB-free RED→GREEN. Все прочие native constraints, восемь paths и исходные бюджеты сохраняются. Первоначальный вариантA, предыдущие RED→GREEN и аудит остаются историей; нового Goal нет.

> План отдельно утверждён и исполнен по `superpowers:executing-plans`. Код/DB-free/native MP proof готовы; полный TRAIN не запечатан, итог PARTIAL015. Чеклист ниже сохраняет исходные плановые шаги; фактическое выполнение и ограничения определяют RESULT.md, PROGRESS.md и ACCEPTANCE_RESULT.md.

**Цель:** разрешить обычному каноническому Party admission пропускать законный бой, у которого прогнозируемого MP на весь бой недостаточно, если остальные действующие gates пройдены. Риск истощения MP сохраняется явно; успешный бой не гарантируется.

**Архитектура:** единственное изменение решения находится в `L2jPhantomPartyBackend.groupViable`, общем для начального выбора цели и continuing encounter. Общий расчёт MP остаётся прогнозом. Текущая native законность действия, прогноз MP и существующая оценка HP/угроз — разные результаты. TEST получает готовый канонический прогноз через observer того же вызова, без своей формулы и без второго combat engine.

**Стек:** JDK25, Ant, существующие Java/PowerShell runners; новые зависимости, проекты, production flags, ветки, worktrees и клоны не нужны.

**Основание:** восемь требований владельца от2026-11-15, REALISM_AUDIT.md, TASK.md/CANONICAL_MP_CONTRACT.md и PLAN.md. Этот план запрашивает ровно одно поведенческое исключение из прежнего «production unchanged»: MP forecast перестаёт быть самостоятельным отказом. Исходные task-файлы не переписываются; после утверждения исключение фиксируется отдельной записью.

## 1. Проверенные источники и предел данных

Прочитаны SOURCE004 backend/callers, `PhantomAutonomousFarmParty`, `PhantomPartyFarmEligibility`, `PhantomPartyTactics`, `L2jPhantomBackgroundAuthority`, `PhantomBackgroundModel`, native `Creature/CreatureStat/CreatureStatus`, `Formulas`, `L2jCombatBackend`, Runtime acquired preparation/admission, существующие Checks/PartySuite/build и обязательный пакет015. Shots/supply/pets/recovery/death routes перечислены в REALISM_AUDIT.md. Локальные аналоги: private pure helpers backend; `TacticalRisk.accepts`; existing reflection contracts и source wiring guards в test runner. Новых project/API предположений для плана не требуется.

SOURCE004 — baseline9a4495b9 и собственные незакоммиченные изменения015. В основном checkout сохранён чужой dirt. Повторный fetch для этой выдачи не выполнялся; перед публикацией обязателен fresh fetch с учётом параллельной Studio-интеграции. Studio не относится к015.

### Конкретный случай3060/2806

Исторические RESULT/EVIDENCE014 фиксируют два class103 Party refusals после134460.161ms и134543.559ms recovery; суммарно269003.720ms. MP estimate3060, actual fullMP2806, policy allowance2244.8. В этом read-only проходе private `preparation-cost014.json` повторно дал SHA256 `B28A5630D74B8243A200778E9D5C6E41145B44FC547DFC10188E14050C16D99B`, совпадающий с архивом014. Это исторические receipts, не новая сессия или новое обучение.

Разложение отказа на известных числах:

| Величина | Значение | Что это доказывает |
|---|---:|---|
| Прогноз минус полный MP |3060−2806=254MP, около9.05% полного MP| Прогноз превышает начальный полный запас; это не проверка одного каста. |
| Зарезервированные20% |561.2MP| Действующий reserve дополнительно снижает allowance. |
| Прогноз минус allowance |815.2MP, около36.31% allowance| Именно это сравнение породило `WAIT_GROUP_MP`. |

В backend`:650–652` выбран минимум DPS, минимум interval и максимум MP-cost из selected skills. Эти экстремумы могут принадлежать разным навыкам; получившееся произведение не описывает одну фактически исполненную последовательность. `nativePlanningCombat` дополнительно берёт max(base cost, modified native cost), поэтому native удешевление ниже base не уменьшает этот forecast. Снимать одну лишь границу80% недостаточно:3060 всё равно больше2806.

Отдельных damage/interval/cost/shot-charge traces для обоих отказавших roster в рассмотренных сохранённых stdout нет. Поэтому не утверждаются точный процент завышения расхода, конкретная более дешёвая ротация или победа этих Party. Число254 — расхождение прогноза с запасом, а не измеренная ошибка прогноза.

### Реальные факторы

- Текущие native buff/equipment/passive modifiers уже входят в stats. Forecast не моделирует истечение баффов и повторный buff по времени боя.
- Native Soulshot меняет physical damage; Blessed Spiritshot меняет native magic damage. Plain-hit forecast не учитывает charge. Реального наличия, grade, оплаты и расхода у данного roster из одного коэффициента формулы вывести нельзя.
- Native MP regeneration измерима, но не вычитается из этого full-fight бюджета. В `groupViable` вычитается HP regen цели из DPS.
- Recharge и pet support реально существуют, но зависят от actual learned skill/actor/target/ресурсов/cooldown и native исполнения; future MP credit не гарантируется. В этом forecast их нет. Наличие healer/recharge capability не означает выполненную заливку.

Минимальное исправление не пытается обещать точный будущий расход или добавить фиктивные shots/recharge. Сначала устраняется ошибочный смысл hard gate. Изменение combat formula или политика выбора экономной ротации в этот план не включаются.

## 2. Три отдельных контракта и поведенческая дельта

1. **Законность конкретного действия.** Существующие actual learned skills, readyNow, native `checkDoCastConditions`/target conditions, MP/HP/item/reuse/weapon/status, owner, source, Party, Geo/LOS и actual executor остаются authority. Preview/forecast ничего не разрешает. Отказ одного навыка не делает незаконной существующую законную альтернативу; отсутствующая альтернатива не выдумывается.
2. **Расчётная потребность в MP.** Существующие `actions * costs[index]` и allowance0.8*currentMP сохраняются как наблюдаемый прогноз риска текущего контекста. Типизированный результат: `UNKNOWN`, `WITHIN_ESTIMATE`, `FORECAST_SHORTFALL`. WITHIN_ESTIMATE не гарантирует достаточный MP на реальный бой.
3. **Решение Party policy.** При пройденных остальных gates один FORECAST_SHORTFALL не возвращает false. Native cast всё равно отклоняется, если MP на этот cast недостаточно. Сохраняются остальные действующие risk/HP/heal/ammo/threat/duration checks; они не объявляются доказательством игровой незаконности.

Единственная намеренная дельта обычного сервера: ветка `WAIT_GROUP_MP` больше не запрещает первоначальный pull или continuation только из-за full-fight MP forecast. Общая строка`:748` перестаёт возвращать false по этому признаку. Нет TEST-only bypass, поднятия MP ceiling, изменения80% на скрытый новый порог, изменения RNG или различающейся A/B семантики.

При отсутствии реального damage contributor/законного текущего действия, неверном owner/target или остальных действующих отказах группа не получает автоматический допуск. В бою сохраняется возможность израсходовать MP, не суметь выполнить следующий cast, проиграть и погибнуть. Автоматическая помощь или победа не предполагаются.

Дополнительно проверен путь до executor: Party target selection вызывает `PhantomEngagementPlanner.rewardingFarmTarget`, не Solo `viable`. `L2jCombatBackend.safePartyApproach` проверяет World identity/дистанцию/геометрию и для дистанции≤1200 возвращает true без full-fight MP расчёта. `castLearning` повторяет learned/current native checks. Поэтому proposed изменение убирает реальный Party admission blocker, а не только меняет diagnostic label. Long approach navigation и прочие gates сохраняются; их прохождение для конкретного roster заранее не обещается.

## 3. Предлагаемые точные API и переиспользование

Ниже — **проектируемые** имена/сигнатуры в существующем backend, не утверждение об уже существующем API:

```java
public enum MpRisk { UNKNOWN, WITHIN_ESTIMATE, FORECAST_SHORTFALL }
public record PartyMpForecast(int targetObjectId, int actorObjectId, int memberIndex,
    double estimatedFightMp, double observedCurrentMp, double observedMaximumMp,
    double reserveAllowanceMp, MpRisk risk) { }
private record MemberRiskAssessment(MpRisk mpRisk, boolean memberPolicyAccepted) { }
private static MpRisk assessFightMp(double estimatedFightMp, double observedCurrentMp);
private static MemberRiskAssessment assessMemberRisk(long profileId, boolean covered,
    double estimatedFightMp, double observedCurrentMp, double incomingDamage, double currentHp);
```

`assessFightMp` использует прежнюю общую IEEE-754 арифметику строгого >0.8; nonfinite/negative inputs дают UNKNOWN. Это чистый расчёт, не lower/upper-bound proof. `assessMemberRisk` переиспользует `DEFAULT_TACTICAL_RISK.accepts(profileId, incomingDamage, currentHp, covered)`, не копирует HP-формулу. В GREEN memberPolicyAccepted зависит от этой сохранённой HP policy, а не от MP risk. Ammunition check остаётся перед ним на прежнем месте. Native damage/threat/HP inputs вычисляются прежним production кодом.

В private `groupViable` заменить экспериментальный `BiConsumer<Integer,Double>` observer на `Consumer<PartyMpForecast>`. Обычные вызовы передают null. Consumer вызывается в том же canonical проходе, не управляет boolean admission и не смешивается с refusal witness. Никаких новых постоянных maps/cache или unbounded logs.

Observed maxima — реальные native facts уже подготовленного персонажа, не future ceiling. Pure helper не читает Player/World и не вызывает getMaxMp; diagnostic capture не вызывает специальные setters/warmup ради «чистоты». Неизвестное или устаревшее observation отражается UNKNOWN. Никакой текущий риск не используется как долговременный admission cache.

Добавить overload только в конкретном `L2jPhantomPartyBackend` (interface `PhantomPartyBackend.java` не меняется):

```java
public OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers,
    PhantomDomainRef source, Consumer<String> witness, Consumer<PartyMpForecast> mpWitness);
public boolean autonomousFarmEncounterReady(PartySnapshot party, Set<MemberRef> supportMembers,
    PhantomDomainRef source, int targetObjectId, Consumer<String> refusal,
    Consumer<PartyMpForecast> mpWitness);
```

Их существующие overloads делегируют с null observer. Оба вызывают тот же groupViable. Runtime `acquiredPartyAdmission()` использует первый overload вместо своего существующего вызова и собирает только готовые forecast records. Повторная native encounter-проверка использует тот же контракт. Не выбирать цель из forecast receipt, не выдавать CUT по observer и не повторять groupViable отдельным TEST-калькулятором.

В unused прототипе015 `NecessaryMpStatus/Preview/previewRecoveryMp` больше не должно остаться MP verdict `PROVEN_IMPOSSIBLE` или ложных имён requiredLowerBound/maximumUpperBound. Его не подключать. Заменить собственный экспериментальный API указанным current-fact observer; оставить полезные canonical selection/math/owner/source checks в соответствующих существующих admission путях. Future stat/effect closure не используется как запрет риска. История исследований и catalog evidence не удаляются; прежний conditional arithmetic GREEN не переименовывается в доказательство реального боя.

## 4. Точный file scope после утверждения

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`; source правится в существующем SOURCE004. Это8 source/test/script/build paths из прежнего maximum allowlist9; production behavioral exception только в первом файле.

| Файл | Разрешаемое назначение |
|---|---|
| `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` | Единственная MP policy дельта; общий typed risk/helper/observer; замена собственного unused future-proof prototype. |
| `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java` | Три focused DB-free группы ниже, без своего combat forecast. |
| `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java` | Сохранённая поправка datapack fixture015, существующие Party/support regressions; focused wiring assertion по локальному паттерну. |
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` | Только acquired Runtime admission observer/receipt и pre-CUT recheck; без нового исполнителя или изменения production buffs. |
| `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` | Persist risk отдельно от admitted/refusal; source/JAR/physical provenance; уже подготовленные budget015 contracts. Новый FORECAST_SHORTFALL не retry/early-deny reason; legacy reason handling сохраняется для истории. |
| `tools/phantom-runtime/training/Training.ps1` | Сохранить уже подготовленный отдельный bounded readiness015 lifecycle; MP формул и новых стартов не добавлять. |
| `tools/phantom-runtime/training/Test-Training.ps1` | Сохранить focused15/legacy14 budget checks. |
| `build.xml` | Существующий focused015 target: добавить/обновить ровно три группы; никакого общего verify. |

`PhantomPartyFarmEligibility.java` читается, не меняется. Combat executor/Creature/Formulas, Solo policy, capability resolver, buffs/recharge/pets, commerce/subsidy/death strategy, SQL schemas/rows и Studio не входят в mutation scope. Документация015/итоговый report/roadmap остаётся отдельным уже разрешённым scope. При необходимости другого production файла — остановка с точным обоснованием, без молчаливого расширения.

## 5. RED→GREEN — три focused группы

### A. `party-mp-policy015`: поведение общей policy

- [ ] В существующем Checks runner добавить тест предлагаемого canonical `assessMemberRisk` через существующий reflection pattern. Не использовать MemoryBackend с заранее заданным admitted=true.
- [ ] После отдельного утверждения выделить helper с сохранением старого MP+HP решения; остальные admission paths направить через него. Зафиксировать **поведенческий RED**: реальный helper ещё возвращает false для3060/2806. Одного missing-method/compile RED недостаточно.
- [ ] Применить ровно MP policy дельту и получить GREEN этой таблицы:

| Fixture facts | Ожидаемый результат |
|---|---|
| estimate3060, currentMP2806; profile1, covered=false, incoming10, HP100; прочие gates готовы | FORECAST_SHORTFALL и memberPolicyAccepted=true. В fixture current=max2806. Это replay агрегата014, не воспроизведение его всех native stats. |
| estimate1000, currentMP2806; те же HP facts | WITHIN_ESTIMATE и true; обычный достаточный случай сохранён. |
| estimate3060, currentMP2806; incoming200, HP100 | FORECAST_SHORTFALL и false по прежней HP-risk policy. MP-риск не отменяет другой отказ. |
| estimate2244.8, currentMP2806 | Строгое равенство не считается shortfall; прежняя > арифметика сохранена. |
| NaN/Infinity/negative critical MP facts | UNKNOWN; это не early refusal или боевое разрешение. Для TRAIN неизвестные critical actual resources не дают seal. |

### B. `native-cast-mp015`: технически невозможное действие

- [ ] В том же test file вложенный unspawned `Creature` fixture: существующий `Creature(int objectId, CreatureTemplate)` с явным ID и `CreatureTemplate(StatSet)`, без Player.create/load, IdManager allocator, World publication, SQL или GameServer. Реализовать только обязательные abstract fixture methods; **не override checkDoCastConditions или native MP cost**.
- [ ] Использовать настоящий `Skill(StatSet)` и native `CreatureStat.getMpConsume/getMpInitialConsume`. Для template maxMP2806 установить fixture HP/MP ровно в максимум через `getStatus().setCurrentHp(maxHp, false)` и `getStatus().setCurrentMp(maxMp, false)`; это только явно синтетическая DB-free unit fixture, не TRAIN actor или grant. Не инициировать неполное восстановление/regen. Новый fork этой группы в build.xml запускается с `dir="${datapack}/game"` по существующему catalog015 pattern: доступ к statBonus.xml не зависит от cwd вызывающего shell.
- [ ] Native `checkDoCastConditions` возвращает false при единственном требуемом cast cost2807 и MP2806. Без weapon/альтернатив readyNow=false сохраняется в существующем `farmContributor` predicate; группе не выдумывается другой damage contributor. Это технически невозможный требуемый каст, не прогноз проигрыша.
- [ ] При cast cost100 и MP2806 native проверка MP проходит; при missing skill/null — false. Стоимость100 — unit input, **не** заявленная цена первого каста historical014. Ни одна проверка не снимает остальные Player-specific checks.
- [ ] Проверить отсутствие инициализации DatabaseFactory до/после, отсутствие World publication и scheduling fixture. Если это невозможно на существующем runtime без нового production seam, остановиться до live: не заменять native gate собственным сравнением и не выдавать отсутствующий тест за PASS.

### C. `party-mp-wiring015`: wiring и обычный Party

- [ ] Source guard по существующему локальному pattern подтверждает оба canonical callers, использование общего helper/observer, отсутствие ветки `WAIT_GROUP_MP -> return false` и отсутствие forecast-as-CUT permission в Runtime/trainer. Это статическая проверка wiring, **не** native integration proof.
- [ ] Выполнить существующие Party tactics14/14, post002-support9/9, learning014 и headless015 lifecycle checks. Технически невозможный target/owner, lost learned/ready capability и текущий cast MP остаются отказами своих existing native gates. Не переписывать ожидаемый результат теста ради зелёного статуса.
- [ ] Pure helper tests + wiring + native Creature fixture доказывают ровно эту MP policy дельту и сохранность проверяемого native MP gate. Они не доказывают полный проход исторического roster через World/Geo/Party; это будет проверяться только законным readiness в прежнем бюджете.

Пять review risks покрыты выше: shortfall при законном cast; insufficient current-cast MP/отсутствие альтернатив; unchanged HP/owner/target отказ; equality/nonfinite/stale facts; observer подменяет admission или расходится между initial/continuing и A/B. Ни один test stub не используется как TRAIN evidence.

## 6. Последовательность исполнения после отдельного утверждения

- [ ] Зафиксировать owner approval именно этого semantic exception и обновить IMPLEMENTATION_PLAN/acceptance interpretation: R01 parity включает одну объявленную MP-дельту; R02 больше не требует ложного MP PROVEN_IMPOSSIBLE; остальные G0–G8 сохранены. G1/G2 не считать пройденными заранее.
- [ ] Выполнить RED→GREEN выше. Команда из SOURCE004: `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning015-test`. Последовательно: `powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training.ps1 -Readiness015`, затем тот же script `-Readiness014`. Build/JAR и content validation — штатными targeted командами перед TEST copy, не поверх protected delivery. Сейчас эти новые проверки не запускались.
- [ ] Runtime receipts отдельно сохраняют native admitted/refusal, actual current/max MP и MP forecast/risk; реальный selected skill/level, actual shots grade/Blessed/charge/stock, buff remaining и actual support/pet presence/readiness фиксируются наблюдением. Не выводить их из capability flag или намерения купить; неизвестность сохраняется. Нет free items, гарантированного recharge или изменения stats.
- [ ] До readiness подтвердить G0 preservation, весь offline gate, owner/exit safety и exact source/JAR copies. Не менять generation/physical hashes и candidate denominator ради PASS. Existing fingerprint уже включает JAR SHA и prepared directory configSha/JarSha; исторические013/014 admissions после другой policy не переиспользуются.
- [ ] Только в исходном readiness allowance015: максимум2 сеанса480/720сек,20мин включая startup/cleanup, без reset. Проверить реальный native Party admission и оба свежих физически matched A/B arms. Если другой native gate отказывает, записать его точный reason; не объявлять исторический3060 roster теперь допущенным до такого факта и не продлевать ожидание.
- [ ] Полный G3 по-прежнему: ≥3 разные cells, ≥2 classes, Solo+Party, реальные изученные альтернативы и актуальная физическая сопоставимость. Только после действующего разрешения и полного G3 применим исходный предел≤3GS/60native-мин и≤1FIT; эта выдача плана не запускает readiness/GS/FIT. Новая независимая EVAL и promotion запрещены.
- [ ] Scope/diff, две encoding scans, targeted regressions, G0 postflight, отдельные selected/terminal Adam и честный G0–G8 verdict. Перед публикацией fresh `git fetch origin feature/phantom-world`, inspect актуального origin и ancestry; аккуратное согласование feature history с сохранением dirt/Studio commits. Без force/reset/перезаписи published history. Реальный конфликт — точные файлы и причина владельцу. После ordinary push — независимый GitHub HEAD/diff/source/evidence readback. Следующий Goal не начинать.

## 7. Дальнейшие обязательные требования, не реализуемые молча в015

- Все фантомы20+: постоянно подходящие грейду weapon Soulshots для physical и Blessed Spiritshots для magic; подтверждённые native charge/consumption и платное пополнение через реальную экономику. Ordinary SPS fallback и продолжение legacy starter subsidy после20 требуют отдельного явного решения. Сейчас закрытие этого требования не заявляется.
- После трёх смертей в сопоставимых условиях — durable failure context и обязательный пересмотр стратегии: другие мобы, поиск Party/баффера/recharge/support pet либо снабжение. Сброс retry counters этого не заменяет; бесконечный повтор не допускается целевой архитектурой.
- Для Solo — цена MP простоя и истечения баффов; для Party — фактическая готовность поддержки, её ресурсы и реальные эффекты. Нет фиктивной поддержки в оценке.

## 8. Последствия и запрашиваемое решение

На обычном сервере возрастёт число допустимых pulls/continuations с прогнозным MP shortfall и вероятность фактического истощения/поражения. Это разрешённый риск при сохранённых native constraints. Точная эффективность, распределение смертей и TRAIN completeness пока UNKNOWN. Другие консервативные HP/duration/ammo gates остаются, поэтому один MP fix не гарантирует полного реализма или G3.

Утверждено владельцем **только** описанное общее MP policy exception, API/test/receipt scope и последовательность с исходными бюджетами. GameServer starts разрешены лишь после полного законного G3. Полезная работа сохранена; FULL015 и рост интеллекта не заявляются.
