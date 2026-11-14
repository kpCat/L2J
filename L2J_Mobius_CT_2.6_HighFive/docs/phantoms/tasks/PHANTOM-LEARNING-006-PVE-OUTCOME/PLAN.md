# PHANTOM-LEARNING-006-PVE-OUTCOME — план и проектное решение

Статус: APPROVED_EXECUTED_PARTIAL. Пользователь подтвердил план и автономное выполнение. Фиксированная collection завершена; TRAIN3<16 не позволяет fit. Результат и ограничения — RESULT.md; final manual gate REQUIRED.

**Цель:** доказать native PvE outcome → immutable terminal reward → durable Adam update → новая DRAFT V3 → законное изменённое действие GameServer.

**Архитектура:** расширяются существующие TacticalStoreV3/TacticalTrainerV3, Training Director и native evaluator. Combat исполняет DAMAGE, PartyTactics исполняет HEAL; новая receipt только наблюдает. Один immutable V3 instance, без SQL/fit/network в горячем пути.

**Стек:** High Five, JDK25, Ant1.10.17, MariaDB11.4.13, Geo203. Изменение архитектуры сети, зависимостей, Studio/UI, Player/Attackable/Party writers не предполагается.

**Спецификация:** полное сообщение пользователя `/goal PHANTOM-LEARNING-006-PVE-OUTCOME`; архив не нужен. Выполнение после подтверждения этого плана — в текущем диалоге, одним implementer с независимым финальным review. Обязательный gate перед реализацией задан пользовательскими AGENTS.md instructions: «если задача крупная, сначала предложи план и дождись подтверждения».

## Проверенные исходные условия

- Новый независимый clone `L2J-learning-006`, ветка `wip/phantom-learning-006`, HEAD `dfcba154fa1f459be6082a559744d2b4a5c9b47a`, tracked/untracked dirt до новых 006 artifacts отсутствовал.
- `git ls-remote` подтвердил этот SHA у feature/phantom-world и wip/phantom-learning-005; ветка 006 до создания отсутствовала. Независимый GitHub connector readback подтвердил HEAD feature. 005 source `4a910ada…` и 004 base `dff800f4…` — ancestors.
- Основной checkout: тот же HEAD, feature/phantom-world, многочисленный untracked dirt сохранён. Там не выполнялись writes, build или server runs.
- Исходный protected005 manifest SHA `a523f7ffe87682d2c1bbb0d23f40cad8db9634f7f2bfced83232c3e6703d754a`. Read-only аудит:206 unchanged, ровно 1 ожидаемый drift build.xml. Diff от faec7216 до pinned HEAD содержит 17 добавленных Ant строк двух targets.
- Новый private immutable scoped manifest: `246ac12cd718e698b18e6ab2b338622b24d505f75ba492eb1cd348bca2e335cd`. Старые manifests не переписаны; byte-exact005 copy и новая copy сохранены отдельно с read-only attribute. До реализации 006 runtime guard их не использует.
- SHA build.xml: старый `13b0144d55d5d1d195b0d859f785e8c9c66dfb22d6190d9f2982a2bef36c7756`, новый `04eb66eb74e16a5203489ed14e00bf2de593d83ad5cba7a5cd75049e741335c4`.
- Geo203: все 203 physical files,1063452308bytes, exact SHA каждого проверен; manifest SHA `a8a4b73bc7b53bed28f545cd889f489492b585b369f8e84988f8db8a91c973d5`.
- JDK25.0.4.1+1, Ant1.10.17, installed MariaDB binary11.4.13.0 подтверждены. На момент инвентаризации java.exe/mariadbd.exe processes не обнаружены. Полный runtime preflight, новые порты и TEST006 ещё не проверены.

## Read-first и локальные аналоги

Прочитаны root AGENTS.md/README.md, module docs/phantoms/PHANTOM_CURRENT_STATUS.md и CODEX_WORKFLOW_CONTRACT.md; отчёты 004/005;004 RESULT/HANDOFF/artifacts README;005 RESULT/HANDOFF/ACCEPTANCE/CONSOLIDATION/BASELINE005_EXCEPTION/COMMANDS/EVIDENCE и eval005d report/receipts/timings; module build.xml.

Прочитаны релевантные участки TacticalPolicyV3, TacticalEpisodeV3, TacticalReceiptV3, TacticalReplayV3, TacticalStoreV3, TacticalTrainerV3, TacticalModelV3, LearningPolicy, LearningCastEvidence, TrainingConfig, SQL phantom_tactical_v3; PhantomLearning005Native/Evaluation/Ledger/Checks, PhantomLearning001ShadowNative/ActorFixture, PhantomTrainingNative, Learning004DurabilityChecks; Training.ps1/TrainingOwner.ps1/TrainingV3.ini/Evaluation005.ini; native writers Player.addExpAndSp, Attackable kill boundary, PlayerNativeEvidence reward/kill.

Вышестоящие и module-specific AGENTS.md не найдены. Актуальные learning-specific code-map/pattern-файлы не найдены; найденный historical017 party code-map к новой задаче не применяется. README и релевантные docs найдены; повторных поисков отсутствующих файлов не нужно.

Переиспользуются три конкретных паттерна:

1. 004 native CUT → existing GameServer encounter → afterNativeDrain settlement; точные PlayerNativeEvidence epochs/kill/credited EXP/SP.
2. TacticalStoreV3.transaction/save: lock frozen run/current optimizer parent → model+Adam+run-state commit в одной транзакции.
3. 005 immutable native arms/pairs ledger, physical pre-CUT fingerprints и fresh process resume; исторический EVAL005 остаётся неизменным.

## Диагностика 005: доказанное и UNKNOWN

`evidence/diagnostics005.json` содержит 16arms, exact source/log SHA и строки реальных логов.

-11arm reasons NATIVE_KILL,1 NATIVE_ACTOR_DIED,2 EVIDENCE_REJECTED_1,2 NO_TRACKED_NATIVE_EFFECT. У обоих rejected stock Party arms raw terminal.outcome=COMPLETED/NATIVE_KILL. Не менять их историческую квалификацию.
- Сохранённые cast logs:7 `execution-lease` на effects boundary и 2 `no-exact-hp` на publish boundary. Две последние записи согласуются с rejected count двух stock arms; текущие rejected logs не несут полного arm/event identity, поэтому их индивидуальная attribution по времени не равна доказанной exact linkage. Исправлять native lease guard не предлагается.
- Solo pair3: оба admitted targets в 42.591 м, ordinaryOpening=false. Existing soloOpeningTargets требует distance2D>=100. Target pin исключает другие lawful NPC; idle deadline45 с без encounter приводит к отсутствию эффектов. Это pre-encounter refusal, не подтверждённое поражение и не вина модели.
- Party:8 scoring calls, по 1 на arm; timed calls20, включая ранние выходы без scoring. Learned0 max19.0314 мс, snapshot5.0737 мс, encoding12.8523 мс. Остальные 7 maxima1.851–3.0563 мс. Есть как минимум один timed call>5 мс; точное число overBudget и причины всех ранних выходов в 005 не сохранены.
- Offered count, safeToRank causes, margin, CPU-slot/cooldown counts нельзя восстановить из агрегированных timings. Ставить им 0 запрещено. Они остаются UNKNOWN до новой 006 instrumentation.
-005 summarize требует deaths!=null для ADVANTAGE_SUPPORTED, а writer явно записывает deaths=null. Сохраняем UNKNOWN; lower bound не подставляется в deaths.

Новые 006 records независимо хранят `nativeOutcome`, `actionEvidenceStatus`, `trainingEligibility`, `pairEligibility`. Rejected конкретный action не отменяет canonical kill, но этот action никогда не получает training credit.

## Подход и границы HEAL

Рекомендуемый путь — существующая неэкстренная lawful поверхность. safeToRank, emergency rescue,5 мс,2000 мс cooldown, margin0.025 и ownership сохраняются. До CUT готовятся две нуждающиеся в лечении цели, после CUT только штатные owners. Это ограничивает область обучения, зато не меняет поведение лекаря под атакой.

Альтернатива — разрешить ranking части атакуемых целей, оставив emergency rescue первым. Это потенциально увеличивает число боевых HEAL choices, но требует отдельного согласования механики, проверок focus fire/interrupt/actor safety/priority/owner cancellation и собственного сравнения rescue latency. Она НЕ входит в предлагаемый scope и не будет реализована без согласия владельца. Доказательств, что она необходима для 006, сейчас нет.

Отдельный Director/trainer/evaluator не нужен. Новые typed receipts и bounded terminal dataset нужны, чтобы не менять исторический смысл action reward и избежать повторного обучения одного terminal credit.

## TerminalRewardReceiptV1 и точная reward-функция

Новый immutable envelope хранит schema1, episode/dataset/split, ownerNonce+owner generation, actor/profile epochs, instance, native CUT/first terminal/SETTLE monotonic timestamps, settlement JSON SHA, canonical kill identities, canonical credited EXP/SP deltas, linked action event keys+receipt SHA, useful HEAL, observed paid MP, observedDeathsLowerBound и terminalDead, known masks, normalized components, reward config SHA и bounded R. Generation и точные boundaries отсутствуют в historical TacticalEpisodeV3 — собираются новым companion record, старый JSON остаётся truthful false.

Точный terminal boundary собирается в allowlisted native fixture существующим синхронным ON_CREATURE_DEATH observer из Creature.doDie; локальный ConsumerEventListener аналог — PhantomCombatServerIntegrationSuite около строки 6135. Listener фильтрует exact actor/target epoch+instance, записывает monotonic timestamp, после drain проверяет canonical kill/credited writers. Poll timestamp evaluationTerminal не выдаётся за точное время смерти. Для timeout используется объявленный monotonic deadline при доказанно начатом encounter.

Точное время эффекта не выводится из receipt.elapsedNanos или времени публикации. Companion observation собирает начало/конец существующих native write scopes: LearningCastEvidence.directDamage/actualDamage и HealEvidenceV3.nativeEffect/hpWritten/mpWritten. Сохраняются action identity, native HP/MP write intervals, effect amount и linkage к published valid receipt. Для lethal DAMAGE смерть может наблюдаться внутри того же write scope до публикации receipt: такой завершающий удар допускается только при exact same target/epoch/native death linkage. Независимый HEAL/write interval, пересекающий terminal boundary и не имеющий доказуемого порядка, остаётся AMBIGUOUS_BOUNDARY и не получает credit. Это исключает ошибочное отсечение законного завершающего удара и выдачу позднего HEAL за предшествующий исход.

Версия reward: `GOAL_TIME_EXP_SP_HEAL_OBSERVED_MP_DEATH_LB_V1` — ограниченная observed эффективность, не прибыль/полная смертность/полный расход Party. Конфиг фиксируется в reward-config.v1.json до native эксперимента; SHA вычисляется по точным UTF-8 bytes файла и входит в campaign/dataset/fit pins. После EVAL конфиг неизменен.

Для clip01(x)=min(1,max(0,x)):

`t=clip01((terminalNanos-cutNanos)/120000000000)`

`e=clip01(canonicalCreditedExp/100000)`; `s=clip01(canonicalCreditedSp/20000)`

`h=clip01(exactUsefulHeal/10000)`; `m=clip01(exactObservedMpDebit/2000)`

`R=clip(0.65*g - 0.15*t + 0.08*e + 0.04*s + 0.06*h - 0.02*m,-1,+1)`

g=+1 только при exact canonical target kill; g=-1 только при доказанно начатом native encounter с terminal TIMEOUT/FAILED. При observedDeathsLowerBound>0 или terminalDead=true применяется override R=-1: положительные компоненты не компенсируют наблюдаемую смерть. Если смерти не замечены, completeDeathCount всё равно UNKNOWN.

Обязательны exact owner/epoch/instance/boundaries и canonical outcome; для kill EXP/SP должны иметь подтверждённые credited deltas. Отсутствие exact earnings writer либо overflow исключает receipt из fit. Canonical zero допускается только при доказанной непрерывности writer/epoch; баланс персонажа не используется. Полная смертность,aden а/loot/consumables/whole-party damage/total MP остаются UNKNOWN и не входят в эту ограниченную функцию.

HEAL и MP — сумма только linked completed paid native actions; overheal ограничивается фактически восстановленным HP. Ноль observed HEAL означает отсутствие credit в наблюдаемом наборе, не отсутствие любого лечения. Отменённые/нулевые/unpaid действия сохраняются отдельно и не получают HEAL/terminal action targets.

Native kill дедуплицируется по objectId+spawnGeneration+instance, не по числу участников. Unknown/STOP/PAUSE/NO_LEGAL_TARGET/pre-CUT SKIP не считаются проигрышем и не обучают.

Attribution: undiscounted Monte-Carlo return, gamma=1: каждому выбранному lawful executed action данного eligible TRAIN episode присваивается один terminal R. Контрфактические варианты не получают target. Временное окно action observation/effect должно целиком соответствовать CUT→first terminal; опоздавшие drain-only effects сохраняются как evidence, но не обучают этот receipt. Это корреляционная credit assignment: влияние партнёров и переизбыток samples длинных эпизодов остаются ограничением. Число действий не выдаётся за число независимых эпизодов.

Нельзя заменить receipt.reward(): старые TacticalReceiptV3 по-прежнему проверяют локальную HP/MP/time формулу. TerminalReplayV1 формирует Example с отдельным target и сохраняет source receipt bytes неизменными.

## Dataset, атомарность и checkpoint8

TRAIN/TUNING/AUDIT/EVAL назначаются до CUT; actor/context/episode не пересекают split. EVAL005 и новые EVAL006 never fit. TUNING используется только для selection; AUDIT — отчёт/quality, без gradients или подбора reward weights.

Минимальная SQL необходимость: добавить таблицы immutable terminal receipts и terminal fit applications. Existing event/episode tables не доказывают receipt consumption; без unique terminal reward identity нельзя гарантировать exactly-once. Native receipt — STAGED/NOT_APPLIED до fit commit. В одной existing save transaction проверяются frozen dataset/optimizer ancestry, вставляются model+Adam и unique applications. Только commit создаёт новую APPLIED запись с terminalRewardUsedForTraining=true. Historical settlement false не перезаписывается.

Natural episode identity для unique receipt/application: hash(installationOwner + GameServer ownerNonce + native owner generation + canonical episodeId), без dataset/config/split. Первое stage навсегда связывает её с exact settlementSha, actor epochs, target epoch/instance и immutable payload; альтернативный settlement/config/dataset/split для той же identity отклоняется, byte-identical retry идемпотентен. Для goal дополнительно unique hash(ownerNonce + NPC objectId + spawnGeneration + instanceId): переименование episode не даёт второй kill credit. Existing event_episodes PRIMARY KEY(event_key) и events UNIQUE(action_key) проверяют единственного владельца каждого фактически выполненного action; новый dataset/config не освобождает эти identities. APPLIED unique key — natural episode identity, а receipt SHA является проверкой целостности, не ключом exactly-once. Только одна целевая NPC incarnation допустима в этом bounded encounter contract; посторонние kills сохраняются, но не участвуют в его reward.

Повторный freeze не включает уже APPLIED receipts. Interrupted FROZEN run восстанавливает тот же dataset/parent; если commit состоялся, readback возвращает готовый commit без повторного fit. Все mini-batches/epochs одного fit — один объявленный use terminal receipt, resume не создаёт второй use. TUNING/AUDIT тоже имеют durable used split identity, чтобы бывший independent sample не ушёл в TRAIN.

Существующий continueTraining требует TRAIN>=16 и TUNING>=8 action examples; ограничение не снижается. Replay<=4096examples, frozen bytes<=64MiB; raw ledger/disk limits сохраняются. Новый objective/dataset revision отделён от 004 action labels; старый replay хранится, но не переименовывается в terminal targets.

Checkpoint8 ZIP SHA `f689100ec7e20aebcefb91648c9b5109c8fb82c67fc0d5b30343923639bdc798` подтверждён. Он содержит Adam8 (769updates/124epochs), selected8, replay/context/failures и ссылку на parent7, но не полные parent rows/blobs4–7. Existing latestOptimization проверяет всю SQL цепь. Поэтому просто импортировать Adam8 в пустую SQL и отключить traversal нельзя.

Предпочтительный перенос lineage: подтвердить STOP/отсутствие любых writers004; сделать byte-verified physical copy её холодного SQL datadir в приватный 006 shadow, старый datadir только читать. Запустить только собственный shadow с новой конфигурацией/портом/held-handle identity; штатным MariaDB dump получить exact tactical tables и связанный training provenance, импортировать их в новую canonical006 TEST SQL. Зафиксировать SHA входного/выходного table export и accepted1/2/checkpoints4–8 до/после. Старые owner/profile identities сохраняются как история и не применяются к fresh006 actors. Старую БД не запускать. Неполнота переноса или неподтверждённый STOP — fail closed, без random initialization. Конкретный table closure и существующие SQL identity helper вызовы ещё требуют ограниченной проверки перед копированием; это технический audit шага 1, не разрешение обходить guard.

Альтернатива внешнего checkpoint8 trust anchor потребовала бы нового import-format и изменения ancestry contract. Она не выбрана: полная цепь уже имеется в сохранённой 004 БД. Если безопасная cold copy недоступна, зафиксировать BLOCKED_LINEAGE, не выбирать альтернативу молча.

Новый кандидат остаётся DRAFT. Исследовательский native006 test получает immutable exact id/SHA + terminal config/dataset/fit proof через явный TEST-only opt-in. accepted() и apply-test quality gates не ослабляются; accepted2 и checkpoint8 не переименовываются. Один и тот же frozen candidate используется во всей независимой A/B кампании.

## Bounded exception и точные source paths

Это один связанный product outcome, а не независимые подсистемы. Объём>10 файлов объективен: receipt, dataset/commit, наблюдение, native collection/evaluation и существующий supervisor должны согласовать одну identity. Bounded exception: **22 source/config/test paths**, фактически изменены17. После утверждения20-file плана перед правками объяснена необходимость21-го schema upgrader и22-го actor allocation helper. Добавлена byte-exact SQL-before fixture в новом task; старые fixtures не меняются. Первоначальные11 task/report/config/evidence paths дополнены компактным native/cold/test evidence и архивом; exact paths — SCOPE_ALLOWLIST.txt. Build.xml, TacticalEpisodeV3, TacticalReceiptV3, TacticalModelV3, legacy TacticalReplayV3, player/reward writers и Studio не меняются.

Все paths ниже относительно module root:

1. java/org/l2jmobius/gameserver/phantoms/learning/TerminalRewardReceiptV1.java — новый validated immutable contract/config/components/known masks.
2. java/org/l2jmobius/gameserver/phantoms/learning/TerminalReplayV1.java — новый bounded offline projection source actions→terminal Example, split/use validation.
3. java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java — supplemental receipt/dataset/application transactions, exact DRAFT test readback;004 branches сохраняются.
4. java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java — terminal objective route того же trainer и export/readback, continuation existing Adam.
5. java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java — reason counters/bounded samples и immutable diagnostics; eligibility predicates не меняются.
6. java/org/l2jmobius/gameserver/phantoms/learning/LearningPolicy.java — bounded observer отказов evidence с identity.
7. java/org/l2jmobius/gameserver/phantoms/learning/LearningCastEvidence.java — передача существующей причины отказа observer без изменения legality/lease.
8. dist/db_installer/sql/game/phantom_tactical_v3.sql — две additive006 tables, только own TEST installer.
9. test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Native.java —006 terminal collection companion поверх existing encounters.
10. test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java — typed boundary/result observation и pre-CUT target eligibility; исторический 004 JSON не меняется.
11. test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java —006 opt-in dispatch внутри существующего native worker.
12. test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java —006 profile нового candidate, те же physical matching/encounter helpers, явное разделение четырёх статусов.
13. test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java — versioned006 modes/outcome/evidence eligibility, safety UNKNOWN.
14. test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java —006 version/campaign pins без изменения чтения 005.
15. test/java/org/l2jmobius/gameserver/phantoms/learning/Learning006OutcomeChecks.java — focused R01–R08/R12 и own SQL cold/durability entrypoints.
16. test/java/org/l2jmobius/tests/phantoms/PhantomLearning006Checks.java — focused R09–R11 и evaluator/Director boundary controls.
17. tools/phantom-runtime/training/Training.ps1 —006 phase/config dispatch, существующие STOP/PAUSE/RESUME/report/export.
18. tools/phantom-runtime/training/TrainingOwner.ps1 —006-specific pinned baseline selection, прежние 001–005 pins неизменны.
19. tools/phantom-runtime/training/Outcome006.ini — новый явный profile, loopback33357/18001/19301 после проверки свободных портов, maxActive5/heap4096/cpu4.
20. java/org/l2jmobius/gameserver/phantoms/learning/HealEvidenceV3.java — companion native HP/MP write timestamps без изменения исторического Receipt/reward/owner contract. Добавлен по independent review: publication time не доказывает порядок относительно terminal.
21. test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Schema.java —006-only exact additive migration с проверкой immutable old SQL fixture и hash прежних10 tables; historical004 path сохраняется.
22. test/java/org/l2jmobius/tests/phantoms/PhantomLearning003Damage.java — необходимый006-only AUTO_INCREMENT floor выше imported actor IDs2088. Исключение объявлено перед изменением; old helper branches сохранены. Всего реально изменены17 source/config/test paths; остальные planned paths не менялись, следующие фазы не реализованы без достаточного TRAIN/candidate.

Новые API ниже являются предлагаемыми сигнатурами, не утверждением об их существовании:

-TerminalRewardReceiptV1.validate(...) / bytes() / sha256(): immutable receipt после проверки companion boundaries+canonical settlement+exact action list.
-TerminalReplayV1.freeze(...): immutable dataset identity, source SHA,split,target; использует existing TacticalModelV3.Example.
-TacticalStoreV3.stageTerminal(receipt), freezeTerminal(), saveTerminal(...): существующий transaction helper; APPLIED marker только после model/optimizer commit.
-TacticalTrainerV3.evaluate(root): выбирает objective из явно opted-in006 config, прежний 004 path unchanged.
-TacticalPolicyV3.diagnostics(): immutable counters и bounded samples, без SQL, logging каждого tick и изменения return decisions.

## Выполнение: пять связанных этапов

### 1. Ownership, перенос lineage, diagnostics

- [x] Remote/HEAD/ancestry, fresh clone/branch, baseline207 и Geo203 audit.
- [ ] После утверждения плана подключить 006-only manifest pin; wrong pin/unexpected drift negative controls R12.
- [ ] Проверить full table closure checkpoint lineage и byte-verified cold copy004→own shadow→own006 import. Не запускать старые процессы.
- [ ] Readback existing optimizationParent=8, optimizerSHA56cc26c4…,updates769,epochs124, отдельные selected/terminal weights; accepted1/2 immutable.
- [ ] Ввести R09 reason instrumentation без изменения HEAL gates. Причины: offered<2,safe predicate,actor/target under attack,emergency,cooldown,CPU-slot,budget5ms,margin,stale owner,invalid target,skill readiness,LOS,same-as-stock. Счётчики allow-list reasons, bounded sampled identity; не приписывать downstream отказ scorer.

### 2. RED→GREEN receipt, dataset, commit

- [ ] R01–R05: strict native kill identity/dedupe, death lower bound/UNKNOWN,time window/prepare/drain,credited deltas,paid useful HEAL; проверить точную формулу и masks.
- [ ] R06–R08: foreign nonce/generation/epoch/instance,duplicate event/receipt,split contamination и optimizer lineage/cold bytes. Настоящие SQL negatives — только собственная SQL, rollback; DB-free fixtures не выдаются за native proofs.
- [ ] Минимальная реализация21-file allowlist, без новой model topology или изменения historical rewards.
- [ ] SQL application marker и model/Adam в одной транзакции. Controlled offline interruption до commit; fresh process retry должен дать один use/model.

### 3. Ограниченная native TRAIN и fit

- [ ] Одна заранее объявленная collection:8 TRAIN (4Solo+4Party),4TUNING (2+2),4AUDIT (2+2), fresh actors/spawn epochs и минимум 2 разных контекста каждого режима. Targeted minimum:2Solo+2Party TRAIN; полный denominator16 attempts сохраняется.
- [ ] Контексты: existing canonical94 caster77/78,A/S,21798/21799 при source preflight; existing Party90/94/105 roles. Для Solo target должен проходить весь существующий opening filter, distance>=100 до CUT. Для Party две lawful injured цели/LOS до CUT; после CUT никакого setup/forced skill.
- [ ] Если collected TRAIN<16 или TUNING<8 lawful action examples, fit не начинается; ограничение не обходить, бюджет автоматически не увеличивать ради PASS.
- [ ] Actual fit existing continueTraining от terminal Adam8. Новый objective использует только 006 terminal labels; old replay сохраняется как история. Candidate+Adam+applications exact readback. Candidate DRAFT, historical accepted неизменны.

### 4. Frozen native intervention и независимая A/B

- [ ] Один новый 006 campaign:4pairs Solo V3 +4pairs Party V3, balancedA→B/B→A, max16arms. Candidate frozen до кампании, все pre-CUT facts физически совпадают; stock GameServer RNG независим.
- [ ]2 отдельные stock-vs-stock control pairs (по 1 на режим),max4arms, не смешивать с A/B. Определение divergence требует отличающегося real selected skill/target и native paid effect.
- [ ] Changed action proof можно получить в тех же A/B arms, без отдельного дорогого run. Legal alternatives/stock/neural/margin/modelSHA/eventSHA/native cast/HP/MP/terminal linkage обязательны.
- [ ] Любые failed/incomplete/unmatched сохраняются. Не переносить 005 EVAL в 006 fit и не закрывать 005. Если нет изменённого V3 action — PARTIAL/NO_POLICY_DIVERGENCE.
- [ ] При n<20 либо UNKNOWN full deaths вывод ADVANTAGE_SUPPORTED запрещён. Нижние bounds и terminal dead публикуются отдельно. Малый A/B smoke обычно даст INCONCLUSIVE; регрессия не скрывается.

### 5. Cold resume, review и доставка

- [ ] STOP своим Director; fresh Director+worker RESUME, same immutable receipts/dataset/optimizer SHA, no duplicate applications и no finished-arm rerun. Автономность доказывается ограниченным прогоном, без ночного ожидания и ручного TRAIN.cmd.
- [ ] Canonical `ant -f <module>/build.xml compile-tests`, затем `ant -f <module>/build.xml jar`, строго последовательно; targeted006 mains,28existing005 contracts,143affected assertions и F7/F8 по existing routes. Общий ant verify не заменяет эти проверки.
- [ ] Actual policy p50/p95/p99 с n: snapshot/encode/score/dispatch/CPU/allocations/fallback/overBudget; JVM CPU/peakRAM отдельно от native encounter и bootstrap. Disk/replay/optimizer export размеры и throughput; unsupported metrics=UNKNOWN.
- [ ] Независимый whole-change code review; исправить подтверждённые findings и повторить только затронутые gates.
- [ ] Exact source+artifact allowlist guard, обе отдельные encoding проверки, secret/binary exclusion, G0–G8 по отдельности. Required manual gate не отмечать принятым за владельца.
- [ ] Scoped commit/push только wip/phantom-learning-006, non-force; independent GitHub readback HEAD и файлов. Feature/master untouched. Итог FULL006_OUTCOME_CORE только при всех доказанных G0–G8, иначе PARTIAL с конкретной причиной.

## Review focus и пока непроверенное

- Точный closure SQL history для холодного переноса: потеря одного raw replay/parent blob должна блокировать импорт, а не возвращать accepted2.
- Native first terminal и drain расходятся: late effects не должны получить credit за другой исход; это отдельная R03/R06 проверка.
- Один Party kill имеет несколько creditedActor receipts: goal credit один, персональные EXP/SP суммируются только по verified actor epochs; R01/R04.
- Два процесса пытаются применить один receipt: unique application и expected optimizer parent должны атомарно запретить второй commit; R06/R08.
-006 versioned evaluator не должен менять 005 JSON/ledger/summary или переносить AUDIT в TRAIN; R07/R10/R11.

Независимый builder-check нашёл два существенных пробела: источник точного terminal/effect time и natural exactly-once identity вне config/dataset оболочки. Оба уточнены выше; source allowlist расширен с 19 до 20 только для необходимого HealEvidenceV3 companion. По ancestry8/selected-versus-terminal weights/сохранности 005 reviewer существенных противоречий не выявил. Это review проекта решения, не приёмка реализации или G0–G8.

Фактическое выполнение: preflight, full SQL history transfer/ancestry8, canonical builds, focused checks,9native terminal receipts и cold staged-receipt/Adam8 readback выполнены. Native denominator16 сохранён. TRAIN3<16: actual fit/newDRAFT/changed006action/A-B NOT_RUN, full006 не заявлен. Подробная хронология и ошибки — PROGRESS.md; G0–G8 и следующий единственный шаг — RESULT.md/HANDOFF_OUTPUT.md. Чек-лист выше сохраняет исходные условия утверждённого плана и не заменяет финальные evidence.
