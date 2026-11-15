# План015 для review владельца

Актуально: PARTY_MP_POLICY_PLAN.md отдельно утверждён владельцем, code91109ae9268908a4e384763befe2dee2834ddec3 реализован в8 разрешённых paths. Итог PARTIAL015: MP policy доказана native3060/2806, full TRAIN seal не получен. RESULT.md/ACCEPTANCE_RESULT.md определяют текущее состояние. Первоначальный утверждённый вариантA ниже сохраняется как история исследования раннего наблюдения; он не заменяет более позднее явное MP policy approval.

Статус: `APPROVED_VARIANT_A`. Владелец утвердил ранние native-факты после дешёвой materialization, до natural recovery, с девятью обязательными условиями исходного контракта и самостоятельным продолжением015. Это план, а не итог015. Goal014 принят владельцем как PARTIAL; его история не меняется. Задача015 — №9/13, после выдачи остаётся4.

Исполнение: самостоятельно в текущем чате, существующий Source004, TDD и один независимый review. Spec: TASK.md, CANONICAL_MP_CONTRACT.md и уточнение владельца A. Дальнейшие routine gates не требуют повторного разрешения.

**Последующее уточнение владельца о реализме:** перед закреплением раннего MP отказа выполнен read-only аудит. Смысл forecast как доказательства невозможности законно начать бой требует поправки. Текущий этап — `OWNER_APPROVAL_REQUIRED` для PLAN_AMENDMENT_REALISM.md; прототип не подключён к runtime planning. Прежнее одобрение A и сделанная работа сохранены как история текущего015.

## Подтверждённая исходная точка

- GitHub connector и fresh fetch подтвердили `feature/phantom-world = 9a4495b9dbe24f53cfe7357d43be801fd2067512`.
- SOURCE004 на том же HEAD, исходный status чистый. Работа с кодом и публикация планируются только в этом существующем checkout.
- MAIN остаётся на `7e997636dbac9d5e1754e00c793cd6fac858b0c6` с историческим untracked dirt. Его исходники и refs не синхронизировались.
- TEST006 остаётся на `42d6b675a9cf57faa4df8a74cc52a8b4adda97fa` с шестью прежними изменёнными исходниками014. Их физические SHA256 совпали с SOURCE004. Это сохранённые изменения014, не новая работа015.
- Canonical TEST006 Preflight: exit0, Geo203/protected207/инструменты/порты PASS. Source004 script с TEST006 root сначала корректно отказал `TRAIN002_EXACT_OWN_ROOT_REQUIRED`; guard сохранён, дальнейший Preflight выполнялся из TEST006.
- Cold Export и существующий reader пяти SELECT: exit0, 183 строки, SHA256 `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`.
- Selected14: `7D3C8FC1F2AE5FC199CFB7517F71743189FDD3FBB4B959A318753637E2D3BC37`; terminal Adam: `6A34B7EE503F35DFF75E61BA59892BE823F72E45402E1A20F4036B9740ADE844`. Cold checkpoint показывает acceptedTEST2/DRAFT14/903updates/223epochs. Automatic/outcome выключены, phase OFF.
- Новых readiness/GS/CUT/FIT/EVAL: 0. Cold SQL Export — отдельная разрешённая проверка G0, не readiness и не игровой эпизод.

G0 пока не объявляется полностью PASS: отдельная текущая проверка pointer12 и окончательные process-exit receipts ещё необходимы.

## Прочитанные источники и локальные аналоги

Прочитан полный обязательный пакет015, root AGENTS и модульный Agents, workflow и task-package standard, README, актуальное введение PHANTOM_CURRENT_STATUS, релевантные разделы master plan и roadmap, RESULT/PROFILING/SKILL/BUFF/EVIDENCE014 и F3_RESULT013.

Из кода прослежены `L2jPhantomPartyBackend.groupViable`, native member/capability capture, `PhantomPartyFarmEligibility`, роли `PhantomAutonomousFarmParty`, `PhantomPartyTactics`, `PhantomCombatCapabilityResolver`, `PhantomSkillInventory`, `AutonomousTrainingV1`, `ActionAdvantageV2`, source discovery/planCells/admitHeadlessCell/collectEpisode, Runtime.prepareAcquired/recoverAcquired, canonical nativePlanningCombat/nativePlanningTarget, relevant PlayerStat/Creature/Calculator/functions и существующие test/build/Training owner routes.

Переиспользуемые аналоги:

1. `PhantomSkillInventory.Fact/Availability`: неизвестность типизирована и не превращается в разрешение или ложное отрицание.
2. `PhantomPartyFarmEligibility.requiredActions` и существующие native planning estimates: единая арифметика, без второго TEST combat engine.
3. Ранний HEAL deny014, существующие immutable receipts, visitation/cursor и полный pre-CUT recheck: отказ учитывается в denominator, живой admission остаётся обязательным.

Дополнительных AGENTS в целевых Java/test подпапках, code-map и pattern-файлов не найдено. Повторный поиск не планируется. SDK/зависимости/стек не меняются: Java25, Ant, существующий runner, последовательные Ant-запуски, без общего verify и чужих БД.

## Обнаруженная граница архитектуры

В текущем порядке `collectEpisode` сначала воспроизводит roster, затем `prepareAcquired` материализует его и ждёт полного естественного восстановления. Только затем создаётся свежий target и вызывается `prepareAcquiredOutcome`, включая native Party preparation/admission. В `planCells` до этого остаётся planning exemplar; discovery replicas уже удалены.

Следствия:

- До roster/materialization нет того же реального набора owned Player, его native MemberSnapshot и будущей свежей target identity, которые использует штатный `groupViable`.
- `farmContributor` зависит от readyNow. Низкий MP может временно убрать участника из DPS; `groupViable` отдельно фильтрует skills через `checkDoCastConditions`. Вызов на истощённом составе не даёт доказанного расхода после восстановления.
- Набор selected skills определяется существующим resolver и learned level, а расход использует maximum cost, minimum contribution/interval из доступных selected skills. Простой перенос старого required3060 не доказывает новый lower bound.
- `getMaxMp()` — текущий stat, а не upper bound всех разрешённых будущих состояний. Более того, `PlayerStat.getMaxMp` при изменении `_oldMaxMp` вызывает `status.setCurrentMp(status.getCurrentMp())`, запуская регенерацию. `nativePlanningCombat` использует этот getter. Прямой вызов всего live planner нельзя объявить чистой функцией только по имени getter.
- Stat functions могут иметь apply conditions; source reproducibility исключает сохранённые effects/reuse/summons, но сама по себе не является сертификатом неизменности всех будущих native stat/condition inputs.

Это не доказательство невозможности обучить Party. Это доказательство того, что доступные сейчас факты не позволяют честно объявить исторический ранний отрицательный прогноз `PROVEN_IMPOSSIBLE`. Для него текущий доказанный verdict — `UNKNOWN`.

## Два варианта решения

### A — раннее наблюдение реального состава перед recovery

Рекомендуемый путь для дальнейшей инженерной проверки, **требующий явного согласования изменения порядка TEST preparation**. В пределах существующего Runtime разделить materialization/anchor, recovery и encounter preparation; получить факты реального roster и свежего target до длительного recovery. При необходимости штатное Party formation выполняется в этом раннем TEST этапе, обычным owner, без выдачи ресурсов или эффектов. Его возможность при текущем состоянии тоже требуется доказать; отказ не обходится.

Цена: roster reproduction/materialization останутся. Исторически это около1–3сек на arm против134сек recovery. Полное устранение materialization не обещается. Изменяется именно граница TEST подготовки относительно PIPELINE015, поэтому данный вариант не считается молча уже утверждённым.

Внутри `L2jPhantomPartyBackend` выделяется одна общая pure projection из уже захваченных immutable canonical facts. Production сохраняет прежние filters, порядок арифметики и строгий `>`80% threshold. Preview не вызывает getters с эффектами, checkDoCastConditions, SQL, RNG или action owners: необходимые факты должны быть захвачены отдельно существующим native owner и доказаны неизменными для recovery-only horizon. Нет подмены currentMp будущим MP.

Предлагаемая новая оболочка, не существующий API репозитория:

```java
public enum NecessaryMpStatus { PROVEN_IMPOSSIBLE, UNKNOWN, MAYBE }
public record NecessaryMpPreview(NecessaryMpStatus status, String reason,
    String factsSha, int memberIndex, double requiredMpLowerBound,
    double admissibleMpUpperBound) { }
```

Оба bound известны только при доказанном состоянии; UNKNOWN не публикует выдуманное число. Concrete capture/forecast method signatures должны быть зафиксированы после выбора границы A: нельзя заранее закреплять интерфейс, принимающий произвольные TEST числа или фиктивный MemberSnapshot как canonical proof.

Доказательные обязанности до positive native slot:

1. Зафиксировать реальный roster/owner/classIndex/learned/enchant/loadout/action alternatives/target/config и source fingerprints.
2. Подтвердить весь допустимый pre-CUT horizon. При возможном новом buff/gear/condition/target drift, неизвестном stat function, смене owner или неполном наборе альтернатив — UNKNOWN.
3. Не считать MP-limited текущие readyNow окончательным составом. Если сохранность contribution/cost после восстановления нельзя доказать через общий canonical расчёт — UNKNOWN.
4. Получить настоящий `R_min` и `M_max`, а не current required/current max. Только `R_min > .8*M_max` может дать ранний отказ. Hash сам по себе не является доказательством bounds.
5. Восстановление и CUT после UNKNOWN/MAYBE идут прежним путём; перед CUT повторяются все native guards и physical parity.

Если эти обязанности для exact014 не удаётся закрыть минимальным extraction, остановиться с `BLOCKED_CANONICAL_PREVIEW_UNPROVEN`; readiness не расходовать. Расширение на Creature/PlayerStat/новый stat interpreter не разрешено этим вариантом.

### B — сохранить первоначальную границу и закрыть честный design blocker

Сохранить строгий порядок «до воспроизведения/materialization» без нового future-state authority. На exact014 оставить UNKNOWN; не добавлять декоративный API, который не способен доказать нужный отказ. Оформить `PARTIAL_DESIGN_BLOCKED`, отсутствие live TRAIN и две архитектурные альтернативы; опубликовать только аудит/документацию в feature. Production и TEST source остаются прежними.

Цена: цель устранения Party MP ожиданий015 не выполнена, FULL015 запрещён. Выигрыш: нет ложных исключений, изменения native semantics или расхода readiness на заведомо недоказанный прогноз. Это вариант остановки на текущем контрактном ограничении, а не просьба увеличить timeout.

## Bounded source scope для варианта A

Не больше8 существующих путей; это один TRAIN/Party admission pipeline. Добавление иных путей требует owner review. Менять только необходимые:

| Путь внутри High Five | Изменение |
|---|---|
| `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` | Общая pure forecast arithmetic и typed negative witness; production semantics прежние |
| `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` | Early preview, exact pin/receipts, сохранение denominator/cursor и полный native recheck |
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` | Согласованная TEST граница до recovery; owner lifecycle и cleanup без bypass |
| `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java` | Canonical arithmetic/parity/unknown focused regression |
| `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java` | False-exclusion, stale facts и incomplete cohort contract cases |
| `tools/phantom-runtime/training/Training.ps1` | Отдельные issued caps015, без сброса historical014, максимум480+720сек |
| `tools/phantom-runtime/training/Test-Training.ps1` | Budget/owner/cancel/no-replay focused contracts015 |
| `build.xml` | Один focused DB-free target015 при необходимости |

`PhantomPartyFarmEligibility.java` пока исключён из planned changes: существующий requiredActions уже переиспользуется. PhantomPartyBackend и другие production files не меняются. Новые source files/компоненты/потоки/глобальные caches не создаются. Баффы, objective, thresholds, SQL/schema, MAIN/PLAY и защищённые поставки вне scope.

## Порядок исполнения после решения владельца

1. Завершить G0, внешние побайтовые backups с SHA и exact scope inventory; source/test refs не переписывать автоматически.
2. Зафиксировать выбранную границу, точные capture/forecast signatures и доказательство bounds. До доказательства — UNKNOWN, без native сессии.
3. DB-free RED: три focused семейства — historical refusal/strict boundary; lawful recovery/buff/cheaper alternatives; stale/unknown/owner/nonfinite/no mutation. Compile failure не считать behavioral RED.
4. Минимальный общий helper GREEN. Проверить unchanged normal Party semantics и existing support contracts. Ant/JAR только SOURCE004, последовательно, без protected dist.
5. TEST integration и отдельный015 budget/lifecycle GREEN; exact hashes и backup перед копированием в TEST006. Никакого применения исторических readiness как новых admissions.
6. Только при positive rigorous proof G1/G2: readiness1 ≤480сек all-inclusive; readiness2 ≤720сек при разрешённом focused correction. Всего ≤1200сек, без третьего slot. Два causal fixes максимум, новый независимый третий blocker — STOP.
7. Полный G3: ≥3 различные paired cells, ≥2класса, Solo+Party, ≥2 реальных alternatives, обе свежие физически matched A/B. Partial arm не считается cell/seal. Без G3 — GS0.
8. После G3 только: ≤3GS/60native-минут, настоящий TRAIN, неизменный ActionAdvantageV2. Один FIT при достаточном fresh TRAIN/TUNING/AUDIT; selected weights и terminal Adam отдельно. Production promotion0, новая EVAL0, game advantage NO_EVAL/INCONCLUSIVE.
9. Cold postflight пяти SELECT с историческими строками, hashes/Geo/protected/OFF/process exits/ports, отдельные encoding checks. Создать RESULT/EVIDENCE/COMMANDS/ACCEPTANCE_RESULT/HANDOFF_RESULT, report и новую roadmap запись9/13, осталось4. Scoped commit/non-force feature push, независимый GitHub HEAD/parent/diff/full source/evidence readback. Final manual gate REQUIRED.

## Gate текущего этапа

`PLAN.md`015 прямо требует «propose small exact implementation plan и manual owner approval». Владелец выбрал A и разрешил самостоятельное продолжение. Промежуточный plan gate принят владельцем; финальная приёмка G0–G8 за владельцем сохраняется.

## Зафиксированные интерфейсы и исполнимые шаги A

Независимый read-only builder review подтвердил условную реализуемость узкого guard, без гарантии, что actual014 удовлетворяет ему. Existing `PhantomPartyCoordinator.dispatchTactics` при trainingAdmission=false и отсутствии tacticalCombat возвращает0 до maintenance. Используется именно этот действующий fence; новой production policy нет.

- [x] Внешние byte backups восьми разрешённых исходников с SHA256.
- [x] Focused RED `phantom-learning015-test`: behavioral assertion `CANONICAL_NECESSARY_MP_CONTRACT_MISSING`, exit1, не compile failure.
- [ ] GREEN conditional arithmetic: private `necessaryMpStatus(double requiredLowerBound, double maximumMpUpperBound)` и общий `partyMpBudgetExceeded(double required, double available)`. Production получает прежний IEEE-754 strict comparison без новых проверок; только preview валидирует конечность bounds. Unit3060/2806 доказывает арифметику при доказанных bounds, но сам по себе не является native forecast.
- [ ] Guard RED→GREEN: private `recoveryInvariantFunction(AbstractFunction)` допускает только проверенные exact function classes с null apply condition и конечным значением. Conditional/unknown functions должны дать UNKNOWN, а не интерпретироваться повторно. Проверяются все calculators, включая зависимости MEN/INT/level.
- [ ] Public `previewRecoveryMp(PartySnapshot party, PhantomDomainRef source, int targetObjectId, BooleanSupplier recoveryOnly, String sourceFactsSha)` возвращает `NecessaryMpPreview` с nullable bounds. Рoster/target реальные и owned; callback связан с existing Runtime pre-CUT fence, а не является сертификатом чисел. Source/config fingerprints фиксируются существующими immutable receipts.
- [ ] Единственный typed observer `BiConsumer<Integer,Double>` добавляется к private overload `groupViable`; старый overload передаёт null. Observer получает ровно тот required MP, который проверяет production. Это устраняет необходимость копировать или повторно реализовывать DPS/duration/skill-selection алгоритм. Preview использует тот же canonical метод только после доказанных read-only invariants; обычные вызовы сохраняют исходные filters, порядок и результат.
- [ ] Для всех потенциальных contributors проверить весь selected loadout: current readiness уже достаточна, learned refs точные, cast preconditions отсутствуют, ресурсы и cooldown не скрывают будущий DPS. Любое исключение/неполнота → UNKNOWN. Будущее восстановление не подменяет текущие MP.
- [ ] Capture только после ordinary formation snapshots и окончательного opening placement. После anchor/refresh до preview stats не меняются; native maxima caches установлены штатным snapshot. Специальный warmup ради preview запрещён. При недоказанности этой последовательности → UNKNOWN. Запрещены active/timed effects, timed equipment, transformation и непроверенные stat inputs. Проверить currentMP≤maxMP.
- [ ] Runtime разносит cheap preparation и recovery в existing методы/overload. Fresh target и final placement переносятся перед recovery. Если ранняя Party formation не проходит из-за текущего восстанавливаемого состояния, это UNKNOWN и прежняя полная preparation, а не false refusal. Перед CUT остаются все прежние повторные native checks.
- [ ] Далее budget015/targeted tests/JAR, только затем допустимая readiness; actual014 guard failure сохраняется как UNKNOWN. Если строгий negative на actual014 так и не доказан, headless/GS не расходуются ради ожидания.

Review focus: скрытый будущий contributor; arbitrary skill/conditional stat; zone drift после teleport; lazy maxima cache и timed effects; incomplete/cancelled A/B не создаёт seal. Каждый пункт проверяется соответствующим guard/contract и сохраняет UNKNOWN при неполных фактах.
