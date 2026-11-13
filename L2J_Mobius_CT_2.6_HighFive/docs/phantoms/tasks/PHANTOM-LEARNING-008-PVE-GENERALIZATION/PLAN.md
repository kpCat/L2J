# PHANTOM-LEARNING-008 — read-first и план на согласование

Дата: 13 ноября 2026, Europe/Chisinau.

`/goal PHANTOM-LEARNING-008-PVE-GENERALIZATION`

Статус: PLAN_APPROVED. Владелец подтвердил план 13 ноября 2026: «Подтверждаю план». Исходные read-first факты ниже относятся к моменту согласования; текущее выполнение фиксируется в PROGRESS. Финальный manual gate REQUIRED.

## Цель и подход

Продолжить одну V3 от terminal Adam11, обучить законные DAMAGE-контексты 94/103 и отдельно проверить unseen110 и сохранность HEAL105. Использовать существующие native owners и receipts. Игровой verdict отделять от технической приёмки.

Единственный рекомендуемый подход в заданном scope — расширить TEST catalog/actor/admission и версионировать campaign/target contract. Простая замена classes в INI не устраняет ограничения. Глобальное расширение Combat, новая нейросеть и изменение skill trees не входят в предложение.

## Проверенные исходные факты

- GitHub connector: `wip/phantom-learning-007` identical точному `062862eca8e0bcedd4107d7b91720627307f736c`.
- GitHub connector: `feature/phantom-world` identical `7e997636dbac9d5e1754e00c793cd6fac858b0c6`. Ветки diverged: общий предок `dfcba154fa1f459be6082a559744d2b4a5c9b47a`; feature ahead 1, behind 6 относительно 007.
- TEST006: `D:\Codex\repos\L2J-learning-006`, HEAD `062862ec…`, ветка `wip/phantom-learning-007`; status показывает только untracked `.phantom-local/`. В основном checkout много чужих untracked artifacts; он не предназначен для реализации 008.
- Сохранённый cold-export `model-2-9ef28c7628b44c04891bf4926d780ef2.zip`: SHA-256 entry `working-weights.bin` = `7de6f5b6bd51f71a8bfce143422139e6b18da44cf81e6ea81f91728b0da206d0`; entry `optimizer.bin` = `f13d2f73ed7d0f3ffd38534b8ad413966b64cf7ba7c980a6ade2318da019d74d`.
- Manifest: optimizer 855 updates / 191 epochs; terminal optimizer weights `6f3dfa43ef8a8335c9ebee16498d7a6406de629c6a85bdd66755675fb55b9546`. Они отличаются от selected DRAFT11 weights — это ожидаемое различие, подменять их нельзя.
- Сохранённый director report: STOPPED generation25. Текущая проверка listening-портов 33357/18001/19301 не обнаружила слушателей. Это не свежая проверка всех PID/SQL ancestry.
- Canonical PlayerClass подтверждает 94/103/110/105.
- MysticMuse: Light Vortex1342 с 76, Ice Vortex1340 с 77; StormScreamer: Dark Vortex1343 с 76, Wind Vortex1341 с 77. Все указаны learnedByNpc в соответствующих class trees.
- Canonical skill XML: 1340/1341/1342 имеют ONE, MagicalDamage, cast6000ms, reuse15000ms, base MP84+21. У 1343 HpDrain: нельзя считать его тем же самым действием без native safety/mechanics проверки.
- NPC21798 и 21799 присутствуют как Monster level76. Их наличие в XML не доказывает доступный live spawn/Geo/LOS/reward.

## Два ограничения до native CUT

1. Текущий progression catalog получает для103 только DAMAGE1340, для110 только DAMAGE1341. Дополнительная authored DAMAGE rule1230 существует только для94. `PhantomCombatCapabilityResolver.resolve()` выбирает только catalog evidence; `PhantomTrainingScenarios.discover()` отбрасывает наборы менее двух skills. Следовательно, текущий маршрут не допускает новые классы к требуемому сравнению. Это ограничение admission/catalog, а не доказательство отсутствия у классов законных навыков.
2. `ActionAdvantageV1.Sample.context()` включает cell, class, level и exact legal skill shape. `targets()` требует минимум3 TRAIN_A в той же context. У полностью unseen110 их нет; новые TUNING physical cells также могут не иметь baseline. Нельзя стирать class/skill identity, смешивать несовместимые cells, подставлять baseline0 или брать AUDIT в TRAIN. Текущий trainer дополнительно жёстко закреплён на Adam9; exploration — на campaign007/DRAFT9.

До первого native старта должны существовать проверяемые решения обоих ограничений. Если они не укладываются в scope, остановка с точной диагностикой и честным BLOCKED/PARTIAL; native бюджет ради этого не расходуется. Ни один пункт G0–G8 пока не объявлен VERIFIED008.

## План выполнения внутри одного Goal

- [ ] 1. После подтверждения создать только `wip/phantom-learning-008` от точного SHA007 в существующем TEST006. Не создавать clone/worktree. Зафиксировать fingerprints исторических моделей, optimizer, applications, protected207/Geo203 и процессы. Свежая проверка SQL — только существующим owner-controlled TEST006 инструментом.
- [ ] 2. Добавить три focused contract-группы в существующий Java runner: canonical class/skill admission; split/cell/baseline isolation; exact Adam11/campaign pin и повторный credit. Сначала RED, затем минимальный GREEN. Исторические 007 codecs/replay/weights должны читаться без изменения.
- [ ] 3. Расширить TEST adapters. Получать кандидатов из действительных class trees/learned levels и native SkillData/safety/mechanics. Переиспользовать существующие CapabilitySource и canonical progression builder. Если нужен дополнительный capability artifact, он допустим только в private owned TEST data, с deterministic validation и одинаковым catalog для preparation и фактического Combat owner. Навыки насильно не выдавать; global game data не менять.
- [ ] 4. Зафиксировать до collection исполнимую objective/evaluation revision. Сохранить paid selected-action targets, реальные propensity, TRAIN-only baseline, точные class-aware cells, clipped weights и class-balanced accounting. Дешёвый тест обязан показать, как формируется каждый TUNING/AUDIT target без утечки; отсутствие baseline означает отказ, не фиктивный MSE. Если для unseen110 требуется ослабить эти правила, это blocker, а не рутинное изменение протокола.
- [ ] 5. До CUT запечатать deterministic bounded catalog, quotas, orders, campaign/model/Adam/JAR hashes, отрицательные контроли и накопительный budget. Выбирать сцены по canonical admission/coverage, без neural-score или outcome selection. Разделить TRAIN/TUNING/AUDIT/EVAL и identities до обучения.
- [ ] 6. Выполнить collection в пределах бюджета; failed/pre-CUT/timeout попытки входят в denominator. При отсутствии двух реально ready legal DAMAGE alternatives — отказ сцены. TRAIN-only exploration epsilon .25, максимум одна opportunity/episode; остальные splits exploration OFF.
- [ ] 7. До fit проверить native SQL ancestry, уникальность dataset и исторические markers. Продолжить от terminal Adam11. Checkpoint выбирать только по TUNING, затем frozen DRAFT и cold readback. Сравнить с DRAFT11 и TRAIN-derived constant по классам, variance и независимым episodes. Отрицательный AUDIT сохранить; promotion не выполнять.
- [ ] 8. Независимая native EVAL: stock против frozen candidate для94/103/110/HEAL105, actual paid receipts, balanced AB/BA и exact matched pre-CUT facts. Отдельно оценить scorer, legal set, cast, самостоятельный выбор и игровую пользу. Нет class103 learned divergence — PARTIAL/NO_NEW_CLASS_POLICY_DIVERGENCE.
- [ ] 9. Focused regressions, compile/JAR, SQL/applications, cold STOP/RESUME, F7/F8 и performance/resource evidence. Прежнюю fault injection не повторять без изменения атомарного контракта. Проверить обе категории кодировки и exact final scope. TEST STOPPED/ports free; создать отчёты и обновить счётчик задач. Scoped commit/non-force push только `wip/phantom-learning-008`, независимый GitHub readback. Final manual gate оставить REQUIRED владельцу.

## Предлагаемые квоты и пределы

| Фаза | Предложение |
|---|---|
| TRAIN | 32 эпизода =16 matched A/B pairs; по8pairs на94/103; по2 physical cells на класс, по4pairs/cell; порядок AB,BA,AB,BA |
| TUNING | 8 эпизодов, по4 на94/103; отдельные actors и заранее закреплённые условия |
| AUDIT | 8 эпизодов только110; ни одного110 gradient; labels/metrics только по доказанному контракту из шага4 |
| EVAL | 8 stock/candidate pairs =16arms: по2pairs на94/103/110/HEAL105, AB+BA для каждого направления |
| Controls | 2 stock/stock pairs =4arms: DAMAGE и HEAL; DRAFT11 native C-arms в основной бюджет не включать |
| Starts | максимум8;3 плановых collection и до1 explicit recovery из заранее выделенного резерва, не более3 EVAL/controls; оставшийся резерв до общего8 не создаёт новых quota |
| Wall time | максимум90 native minutes, из них collection максимум60; ошибки бюджет не обнуляют |

Это потолки попыток, не обещание успешных samples. TRAIN32 оставляет максимум16 B-episodes и32 paid action targets по новой008 revision ниже; потери могут не дать minimum16 action targets и минимум4 независимых Bepisodes на класс. Пороги не снижать, campaign не расширять. Приоритет оставшегося бюджета — независимые splits и EVAL. Каталог56slots (48collection episodes+8EVAL pair slots) и class SkillData/resolver/Geo proof закреплены headless PREPARE27, до любого GameServer. Первый collection segment ограничен12slot попытками, последующие — оставшимся каталогом и cumulative budget. Смена Director поколения не повторяет reserved slots.

Review до CUT: внешний cumulative deadline действует и во время bootstrap; до нового GS резервируются startup/preparation/episode/shutdown+150s. Spent time включает teardown. Неподтверждённый exit оставляет pending fail-closed. PREPARE27 protocol сохранён SHA-копией вне checkout и в private precut-revisions до final source/JAR binding; catalog, objective, quotas и Adam не менялись. Ни одного GS reservation/CUT к этому моменту нет.

## Bounded scope exception для согласования

Пакет по заданию уже требует более10 delivery/source files. Предлагается предел: до16 source/config/test paths в одном learning subsystem плюс10 delivery paths и один компактный evidence archive. Финальная exact allowlist — обязательный pre-edit и final scope guard; расширять её за этот предел без согласования нельзя.

Планируемые source/config/test paths относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `build.xml` — один focused008 target.
2. `java/org/l2jmobius/gameserver/phantoms/learning/TrainExplorationV1.java` — явные campaign/model pins с сохранением007.
3. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java` —008 routing, Adam11 и accounting.
4. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java` —008 admission/persistence по существующему атомарному паттерну.
5. `java/org/l2jmobius/gameserver/phantoms/learning/ActionAdvantage008.java` — предлагаемый versioned008 contract, только если совместимый контракт доказан на шаге4; файл пока не существует.
6. `test/java/org/l2jmobius/gameserver/phantoms/learning/Learning008Checks.java` — новый focused runner рядом с найденным Learning007Checks того же package.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning008Scenarios.java` — новый deterministic catalog/preflight.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning008Native.java` — новая bounded campaign, существующие native owners.
9. `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java` — actual GameServer routing008. Scenarios уже параметризован, переиспользуется без правок.
10. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java` — typed EVAL008 cold namespace.
11. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ActorFixture.java` — после native диагностики выявлен отдельный coldSolo range по фиксированным1230/1339; минимальное class-aware TEST исправление вместо не потребовавшегося Damage003. Source scope16, в утверждённом пределе.
12. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java` — подключение отдельной008 EVAL.
13. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java` — per-class matching/reporting.
14. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — обобщить три TEST admission проверки и recovery для canonical primary вместо обязательного Prominence1230; старый94 путь сохранить. Эта необходимая замена пути PhantomTrainingActors установлена при чтении actual Runtime после согласования, число source paths не увеличивает.
15. `tools/phantom-runtime/training/Training.ps1` — отдельный explicit opt-in008, накопительный budget.
16. `tools/phantom-runtime/training/Exploration008.ini` — новый shipped-OFF профиль.

Delivery: восемь запрошенных файлов в `docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/`, общий report `docs/phantoms/reports/PHANTOM-LEARNING-008-PVE-GENERALIZATION.md`, `docs/phantoms/COMBAT-AI-ROADMAP.md`; один scoped archive внутри task artifacts. Пути docs находятся внутри High Five, как в007. Private SQL/Geo/raw большие logs не публикуются.

Не входят: Combat/Party executors, global skill trees, V3 topology/feature codec, terminal reward formula, Studio/UI, MAIN/PLAY, release057–061, worktree109, feature/master и исторические task packages. Если потребуются иные production files, сначала показать точную причину; не расширять scope автоматически.

## Прочитано и переиспользование

Прочитаны корневые AGENTS.md и README.md, CODEX_WORKFLOW_CONTRACT.md; релевантная текущему baseline часть PHANTOM_CURRENT_STATUS.md; отчёт007, HANDOFF/COMMANDS и целевые разделы EVIDENCE007; COMBAT-AI-ROADMAP; build.xml; PhantomTrainingScenarios, целевые части actor fixture, Damage003, Native005/007; CapabilityResolver/Loadout/NativeCombatCapability, точки выбора CombatService/PartyTactics; целевые участки TacticalModel/Trainer/Store/Policy/Snapshot/Observation, TrainExplorationV1, ActionAdvantageV1, TerminalRewardReceiptV1, LegalActionV3; Training.ps1/Exploration007.ini; PlayerClass, SkillTreeData consumer paths, skill trees/capability XML/skill XML/NPC XML и progression builder/backend.

Аналоги: native matched A/B005/007; canonical learned-skill actor construction и reload001; TRAIN_A leave-block-out baseline007; SQL atomic optimizer/applications TacticalStoreV3. Стек Java25/Ant, safety5ms/margin.025/cooldown2000ms и HEAL emergency/safeToRank остаются прежними.

Вышестоящие AGENTS.md в `D:\`, `D:\Codex`, `D:\Codex\repos` не найдены; релевантный серверный code-map/pattern-файл не обнаружен. Исходный case-sensitive inventory ошибочно пропустил case-variant `L2J_Mobius_CT_2.6_HighFive/Agents.md`: review обнаружил его, перед финальной поставкой он прочитан вместе с PHANTOM_DEVELOPMENT_MASTER_PLAN.md и TASK_PACKAGE_STANDARD.md. Это отклонение read-first процесса, а не отсутствие файла. Устаревшие абсолютные пути/БД и generic feature workflow уступают явному TEST006/008 scope текущего задания. Task008 directory на момент согласования отсутствовал.

Непроверено: свежая SQL ancestry/application digest, полная baseline207/Geo203 hash validation, native actor/skill readiness, live target reachability, исполнимость unseen-AUDIT target revision, новые compile/regression/native outcomes. Сохранённый ZIP не заменяет новый cold SQL restore.

## Git: точные команды read-first

Разрешение: прямой раздел2 задания требует remote refs/ancestry/status; корневой AGENTS разрешает bounded read-only inspection. Записи refs, commit и push на этом этапе отсутствуют.

В основном checkout:

```text
git rev-parse --show-toplevel
git symbolic-ref --quiet --short HEAD
git status --short --branch
git for-each-ref --format='%(refname) %(objectname)' refs/remotes/origin/feature/phantom-world refs/remotes/origin/wip/phantom-learning-007 refs/heads/wip/phantom-learning-007 refs/heads/wip/phantom-learning-008
```

В TEST006:

```text
git rev-parse HEAD
git symbolic-ref --quiet --short HEAD
git status --short --branch
git for-each-ref --format='%(refname) %(objectname)' refs/remotes/origin/feature/phantom-world refs/remotes/origin/wip/phantom-learning-007 refs/heads/wip/phantom-learning-007 refs/heads/wip/phantom-learning-008
git merge-base --is-ancestor 7e997636dbac9d5e1754e00c793cd6fac858b0c6 062862eca8e0bcedd4107d7b91720627307f736c
```

Все перечисленные команды exit0, кроме merge-base exit128: feature SHA отсутствует в object database TEST006. Причина установлена без fetch/mutation; фактическая remote ancestry проверена GitHub compare connector (diverged, общий предок указан выше). GitHub branch search и compare — read-only, без публикации.

## Требуемое подтверждение

Корневые инструкции пользователя: «если задача крупная, сначала предложи план и дождись подтверждения». Раздел13 самой008: «покажи компактный план и запроси одно подтверждение». Подтверждение относится к предложенному TEST-only подходу, ограниченному scope и этапам одного Goal; не разрешает подменять научный контракт или обходить блокеры.

Подтверждение получено. Продолжать тот же Goal; принятые007 gates без причин не повторять. Финальная приёмка008 остаётся отдельным manual gate владельца.

## Objective ruling008 до native CUT

Final artifact guard использует exact allowlist ниже. Пути относительно модуля HighFive;16source/config/test +10delivery +1archive. Резерв Damage003 занят необходимым ActorFixture cold admission.

```text
SCOPE_ALLOWLIST_BEGIN
build.xml
java/org/l2jmobius/gameserver/phantoms/learning/TrainExplorationV1.java
java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java
java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java
java/org/l2jmobius/gameserver/phantoms/learning/ActionAdvantage008.java
test/java/org/l2jmobius/gameserver/phantoms/learning/Learning008Checks.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning008Scenarios.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning008Native.java
test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java
test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ActorFixture.java
tools/phantom-runtime/training/Training.ps1
tools/phantom-runtime/training/Exploration008.ini
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/PLAN.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/PROGRESS.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/ACCEPTANCE.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/RESULT.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/HANDOFF.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/EVIDENCE.json
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/COMMANDS.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/EXPERIMENT.md
docs/phantoms/reports/PHANTOM-LEARNING-008-PVE-GENERALIZATION.md
docs/phantoms/COMBAT-AI-ROADMAP.md
docs/phantoms/tasks/PHANTOM-LEARNING-008-PVE-GENERALIZATION/artifacts/learning008-evidence.zip
SCOPE_ALLOWLIST_END
```

Новая revision — factual residual utility, не доказанный causal advantage. U и scale4 из007 сохраняются; новая V3 не создаётся, Adam11/float schema прежние. TRAIN_A controls — только фактически paid начальные stock actions94/103. Exact class/level/skill/MP/cast/range/target/physical cell identities сохраняются. Для TRAIN target при наличии3 подходящих других TRAIN_A используется007 leave-block-out mean. Иначе применяется TRAIN-only forecast с исключением всего собственного matched block; для TUNING/AUDIT — frozen TRAIN-only forecast. Это статистическая nuisance estimate, не записанный исход невыполненного действия. Forecast: input существующие state128+stock-action64, kernel1+dot/192, lambda.01, class-balanced TRAIN_A weights; параметры закреплены до held-out данных. Report обязан отдельно показывать raw utility, baseline forecast/provenance, OOD distance, residual MSE и uncertainty. MSE против перенесённого baseline не доказывает преимущество над stock110.

Чтобы32TRAIN не требовали16безошибочных B-эпизодов,008 episode container допускает максимум2 первых по времени фактически paid DAMAGE receipts основного actor на Bepisode. Начальная exploration opportunity остаётся ровно одна, с фактическим p из007 (.25/(K−1) для альтернативы); последующий receipt допускается только после неё, с тем же frozen SHADOW/model pin и actual selected=stock, conditional propensity1. Никаких выдуманных Selection или повторных007 Sample. Это action-weighted factual support, не unbiased uniform-action estimate. Criteria определены до просмотра utility. Веса clipped IPS сначала нормируются внутри episode, затем поровну между94/103; длинный эпизод не получает больший общий вес. Минимум16TRAIN targets и минимум4независимых Bepisodes на каждый TRAIN class; required paid alternative каждого TRAIN class. У110 и HEAL105 gradient отсутствует. TUNING/AUDIT actor/episode counts и корреляция двух observations фиксируются отдельно. Один атомарный episode application marker связывает полный SHA списка target action keys и запрещает повторный credit.

Read-only независимый builder check этой revision не нашёл логического запрета, но подтвердил обязательность нового episode container, явных OOD limitations, отдельного описания propensity1 и episode-balanced weights. Ни один native CUT не выполнялся до этих rulings.

Независимый read-only builder check предложения: дополнительных вопросов владельцу до этого gate не выявлено. Отдельно подтверждены риски отсутствующего unseen baseline и недостаточного запаса TRAIN32; эти риски остаются проверками исполнителя до native, а не обещанием успешного результата.

- mojibake-маркеры в изменённых файлах проверены; единственный изменённый файл — этот план вне checkout, совпадений нет.
- escaped Cyrillic в изменённых файлах проверены; совпадений нет.

Проверки кодировки выполнены двумя отдельными rg вызовами, каждый вернул1 (совпадений нет). Compile/tests/native в этом read-first этапе NOT_RUN. Принятые gates007 не повторялись.
