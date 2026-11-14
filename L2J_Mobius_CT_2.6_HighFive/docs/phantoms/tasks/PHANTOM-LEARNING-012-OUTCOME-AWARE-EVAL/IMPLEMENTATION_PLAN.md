# PHANTOM-LEARNING-012 — план для первого review

База: GitHub/source004/TEST006 `9e78e3db1153126c616812bfeb8ff5bdbb8c8278`, только `feature/phantom-world`.

## Read-first и уже полученные факты

Прочитаны root/module AGENTS, workflow, текущий status, package standard, релевантные архитектурные разделы master plan, README; все 11 файлов исходного пакета012; RESULT/SCENARIO_COVERAGE/COMMANDS011 и continuation evidence. Пакет012 найден только в корневом docs основного checkout; в module docs отсутствует. Parent AGENTS выше Git root не найдены. Отдельные code-map/pattern-файлы для learning не найдены при первичном inventory.

Локальные аналоги: immutable EVAL_ARM_V1 и native collection в PhantomAutonomousTrainingNative; typed facts/incarnations/effects и fail-closed rawField в PhantomLearning005Evaluation; native settlement/boundary в TacticalEpisodeV3 и TerminalRewardReceiptV1; acquiredScenes/orderedKeys и passive native admission011. Эти контракты переиспользуются, старые codecs и verdicts сохраняются.

Штатный DB-free TEST Preflight exit0: config/JDK25/MariaDB11.4/Geo203/protected207/ports. Текущий Director STOPPED, flags OFF, Java-процессов не обнаружено. Foreign mysqld8692 сохранён. Все исходные native011 files совпали с опубликованным011 hash inventory. 8 arms: Solo AB death/kill (+1), Solo BA death/death (0), Party AB/BA kill/kill (0). CUT/terminal/settled, incarnations, physical parity и 47 уникальных event keys проверены. Это диагностический аудит; typed V2 replay и полный разбор native action records ещё не выполнены. Исторические V1/G4 FAIL неизменны. GS012=0.

## Предлагаемое решение

Additive TEST-only V2 evaluator с отдельными EVAL_ARM_V2/EVAL_PAIR_V2 артефактами. Typed evidence отделяет admission/CUT/settlement от goal success, death и verified bounded timeout. Missing/cancelled/invalid evidence fail-closed; paid actions не являются обязательным условием настоящей смерти. Unknown остаётся null. V1 и production learning/reward APIs не меняются.

Новая явная opt-in настройка `evaluationOutcomeVersion=2` потребляется только TEST adapter и существующим Director; без неё прежний путь V1 остаётся прежним. V2 используется только при EvaluationOnly=true, exploration=false, phase EVAL, frozen authority и отдельном namespace012. Director проверяет именно V2 terminal denominator и AB/BA каждого направления, не legacy completedPairs.

## Архитектурный выбор сложности

Рекомендуемый вариант A: относительная стратификация полного bounded lawful pre-CUT списка по реальным доступным level gap, NPC HP и исходным ресурсам roster. Категории описывают относительную нагрузку внутри списка; вероятность победы/полный DPS/смертность UNKNOWN. Выбор двух разных доступных страт Solo и Party, неизменный seed и AB/BA по направлению, до любых новых исходов. Eligibility и native viability011 не ослабляются. Нет пригодного разнообразия — точный coverage gap до GS.

Вариант B: сегменты только по положению уровня NPC в уже разрешённом canonical roster level band. Проще и легче объяснить, но NPC одинакового уровня с разными HP/defense могут попасть в один сегмент; разнообразие сложности слабее. Неизвестная угроза также UNKNOWN.

Оба варианта не используют результаты011 для порогов, class/skill/NPC allowlists, HP/MP boosts или новые игровые механики. Предпочтение A — мой предлагаемый выбор, пока не решение владельца.

## Exact source allowlist

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — opt-in additive V2 collection/resume и curriculum hookup.
2. Новый `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationOutcomeV2.java` — typed contracts/comparator/immutable codec/statistics.
3. Новый `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationReplayV2.java` — bounded read-only authentic reader и derived namespace012.
4. Новый `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationOutcomeV2Checks.java` — focused behavioral/negative contract tests.
5. Новый `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationCurriculumV2.java` — deterministic pre-CUT stratification без исходов.
6. `build.xml` — focused DB-free `phantom-learning012-test` по существующему Ant pattern.
7. `tools/phantom-runtime/training/Training.ps1` — direct V2 proof/coverage opt-in, без изменения V1 proof.

Перед каждым изменением сохранить exact исходные bytes/SHA вне checkout. Bounded exception >10 общего числа файлов: 7 source paths плюс явно требуемая семьёй TASK012 документация, перенесённые original task docs, общий отчёт012 и counter-only COMBAT-AI-ROADMAP. Другие artifact families и подсистемы не меняются.

## Выполнение после согласования

- [ ] Закончить G0 frozen/SQL/model/pointer/process snapshot и exact source hashes; при несоответствии STOP до product edits.
- [ ] Побайтово скопировать единственный original package012 в source module docs без overwrite, проверить SHA каждого файла.
- [ ] Behavioral RED legacy death/kill на исходных receipts; минимальный V2 и GREEN по death/death, kill/kill, verified timeout, cancellation, reused identities/events, missing settlement, side/config/model mismatch, unknown metrics.
- [ ] Authentic typed replay всех8 original011 arms с exact receipt hashes и V1 byte parity; новый derived011-v2 namespace; отдельно раскрыть legacy supplemental Java Map text.
- [ ] Реализовать и проверить выбранный pre-CUT curriculum; запечатать новую012 EVAL-only config/catalog, provenance, seed, order, authority и fresh reservations.
- [ ] Последовательно выполнить targeted Ant012/011/autonomous009/008/007/JAR, canonical Prepare и Solo+Party readiness gate. `phantom-learning009-test`/010 как отдельных targets не обнаружено; использовать существующие exact autonomous contracts. Общий verify/DB suite не запускать.
- [ ] После успешного offline proof и полного preflight: один independent GS session на ≥2Solo+≥2Party пары; второй только при конкретном bounded исправлении; total ≤2starts/2100000ms boot+error+drain. Никаких повторов зарезервированных slots.
- [ ] STOP/OFF/owned0/portsfree, exact kernel exits/charges, frozen postflight и отдельные descriptive game statistics с n/uncertainty. Никакого advantage claim по четырём парам.
- [ ] Exact artifact/encoding/diff guards; scoped commits и ordinary push feature; независимый GitHub HEAD/parent/diff/full-source/evidence readback. Manual gate владельца REQUIRED.

## Непроверенное и ограничения

G0 live read-only SQL/model/pointer snapshot ещё не завершён. Нового V2 кода, tests, typed replay, нового curriculum/Prepare/native012 пока нет. Полнота terminal authenticity при zero paid events и доказательство native timeout потребуют отдельного нового durable boundary witness; нельзя выводить terminal time из отсутствующего timing. Исторические011 arms имеют timing, но это не универсальное правило V2. Exact raw paid-record reader требует проверок существующего Java record text, не выдуманного binary codec.

DRAFT14/Adam903/223/apps48/acceptedTEST2/pointer12, SQL и бюджеты009–011 immutable. Combat/Party/Player/Spawn/global farm read-only. MAIN/PLAY/protected releases не запускать. JDK25/Ant, без новых зависимостей. №6/13, после выдачи7; следующий Goal не начинать.
