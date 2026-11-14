# PHANTOM-LEARNING-009 — автономный TRAIN и acquired inventory


/goal PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER

Статус: **PARTIAL009_REAL_FIT_NO_TUNING_IMPROVEMENT_EVAL_AND_NATIVE_LIMIT_FAILED**. Final manual gate **REQUIRED**. FULL009 не подтверждён.
008 остаётся PARTIAL / NO_TUNING_CHECKPOINT_IMPROVEMENT с отдельным непринятым gate.
Исходный HEAD85b1c3558e35c1df0a7625fcf5a9e5c119c18b96; только wip/phantom-learning-009.
Exact scope39:25source/config/test и14delivery. PLAN38 и39-й Party path явно утверждены владельцем.


Срез UTC: 2026-11-14T00:16:32.076036+00:00. Director: STOPPED / Собственные процессы остановлены и порты свободны..
Новых fit по journal: 1; cumulative GS starts: 10/10; консервативно списано 96.720667/90native min, pending=False. Последний charge включает задержку recovery; точное время выхода EVAL JVM неизвестно, строгий90min gate FAILED.
Получен реальный autonomous SQL checkpoint DRAFT14: Adam887/207→903/223,48applications/16gradient episodes,3classes. Холодный rollback до COMMIT и recovery после COMMIT проверены; после EVAL raw weights/Adam/apps совпадают, повторного credit нет. TUNING не улучшился, best selected epoch0; selected14 — прежние terminal12 weights, новый terminal Adam14 другой. AUDIT MSE14=0.014208027 против collection teacher12=0.005066568; улучшение поведения не доказано. Завершённых полных F cycles0: EVAL и graceful exit сорвали переход к cycle CHECKPOINT. Долговечность нового SQL checkpoint подтверждена cold export.
Независимый EVAL GS10:0lawful cells/222planning refusals/0episodes/0pairs; evaluation.complete отсутствует. batch.complete подтверждает trainingStateUnchanged=true, cold export подтверждает отсутствие градиента. После budget stop получен STOP_UNCONFIRMED; JVM позднее вышел сам, identity guard отказал до Kill. Финально STOPPED/ports0/OFF. Списано консервативно5803240ms=96.720667min при разрешённых90; эта верхняя оценка включает задержку recovery после выхода. Точное время native exit не доказано; строгий90min gate FAILED. Новых GS/сбора/обучения после указания владельца не выполнялось.

## Реализованные контракты

Read-only acquired inventory отделяет theoretical learn facts, actual learned skill versions, metadata/support и текущую native readiness. Нет фиксированной class→Skill ID allowlist. Canonical parent skills/enchant/SkillData сохранены; неизвестные поля остаются UNKNOWN.
Один acquired CapabilitySource передаётся существующим TEST Combat и PHANTOM Party backend. Старый публичный3arg constructor сохранён; optional4arg TRAIN source запрещён без opt-in. MAIN/PLAY поведение не включалось.
Lawful scenario adapter использует actual source snapshots, canonical NPC/spawn/reward/topology/Geo и существующие Runtime/Combat/Party owners. Exact source/JAR/shortlist/context/split pins до CUT, immutable negative results.
Director связывает collection, existing V3 fit, independent native EVAL и cold checkpoint; persistent journal, existing SQL transactions, owner locks и процессные guards. GUI — тонкий async CLI client.

## Поддерживаемые механики

|Механика|Discovery|Execution / training readiness|
|---|---|---|
|Safe plain single-target DAMAGE|Все actual learned refs|Existing native Combat; actual readiness/forecast/terminal credit обязательны|
|Single-target immediate HEAL|Полный фактический inventory|Existing Party stock skill + V3 recipient choice; neural rotation всех HEAL skills не доказана|
|Item-paid HEAL|Item ID/count и live inventory|Existing adapter допускается при настоящем расходе; whole economy UNKNOWN|
|Пассивные эффекты|Сохранены в inventory и native stats|Не предлагаются как cast actions|
|Buff/debuff/control/AoE/DoT/recharge/cleanse/resurrection|Категории/known metadata|Новые train adapters отсутствуют, не переименованы в DAMAGE/HEAL|
|Summons/pets/PvP/raids/siege/Olympiad|Обнаружение не равно исполнению|Не реализованы в009|

## Discovery и acquired coverage

|Измерение|Результат|
|---|---:|
|Canonical discovered classes|103|
|Theoretical learn facts|43749|
|Unique theoretical skill versions|7172|
|Initial persisted actors / classes|1006 /6|
|Initial persisted skill rows, all class indexes|55668|
|Первый verified native ingress|10fresh clones /5classes /570getAllSkills entries|

Theoretical counts не являются learned counts. Actor MAGE1 с непригодным saved state не исправлялся. Population неоднородна: native inventory counts различаются даже у одного класса. Acquired origin UNKNOWN не выдумывается. V3 action feature skill-level /100 с clipping сохраняет прежнюю schema; registry enchant identity не доказывает neural различимость всех routes.

|Actual class|Observed skills per incarnation|Observed category counts (разные snapshots)|
|---|---|---|
|90|40|{"CONTROL": 3, "DEBUFF": 3, "HEAL": 1, "OTHER_ACTIVE": 5, "PASSIVE": 14, "SINGLE_TARGET_DAMAGE": 7, "UNKNOWN": 7}|
|94|60,62,65|{"AOE_DAMAGE": 4, "CONTROL": 3, "DEBUFF": 8, "HEAL": 2, "OTHER_ACTIVE": 5, "PASSIVE": 20, "SINGLE_TARGET_DAMAGE": 12, "UNKNOWN": 6} / {"AOE_DAMAGE": 4, "CONTROL": 3, "DEBUFF": 8, "HEAL": 2, "OTHER_ACTIVE": 5, "PASSIVE": 21, "SINGLE_TARGET_DAMAGE": 13, "UNKNOWN": 6} / {"AOE_DAMAGE": 4, "CONTROL": 3, "DEBUFF": 9, "HEAL": 2, "OTHER_ACTIVE": 6, "PASSIVE": 22, "SINGLE_TARGET_DAMAGE": 13, "UNKNOWN": 6}|
|103|61,64|{"AOE_DAMAGE": 4, "CONTROL": 3, "DEBUFF": 5, "HEAL": 2, "OTHER_ACTIVE": 4, "PASSIVE": 21, "SINGLE_TARGET_DAMAGE": 17, "UNKNOWN": 5} / {"AOE_DAMAGE": 4, "CONTROL": 3, "DEBUFF": 6, "HEAL": 2, "OTHER_ACTIVE": 5, "PASSIVE": 22, "SINGLE_TARGET_DAMAGE": 17, "UNKNOWN": 5}|
|105|53|{"CONTROL": 4, "DEBUFF": 2, "HEAL": 9, "MOBILITY_ESCAPE": 2, "OTHER_ACTIVE": 4, "PASSIVE": 20, "RESURRECTION": 1, "SINGLE_TARGET_DAMAGE": 5, "UNKNOWN": 6}|
|110|64,67|{"AOE_DAMAGE": 3, "CONTROL": 3, "DEBUFF": 7, "HEAL": 2, "OTHER_ACTIVE": 5, "PASSIVE": 23, "SINGLE_TARGET_DAMAGE": 15, "UNKNOWN": 6} / {"AOE_DAMAGE": 3, "CONTROL": 3, "DEBUFF": 8, "HEAL": 2, "OTHER_ACTIVE": 6, "PASSIVE": 24, "SINGLE_TARGET_DAMAGE": 15, "UNKNOWN": 6}|

|Skill/enchant coverage|Факт|
|---|---|
|Inherited learned refs|HydroBlast1235:28 фактически сохранён у103, без повторного auto-learning|
|Rank refresh / enchant / subclass identity|Focused contract fixtures GREEN; actual acquired enchanted versions в paid native receipts: 1230:101|
|Точные actual levels|Полные native inventories с canonicalLevel включены в архив|
|Origin / unknown metadata|UNKNOWN/null сохранены, не угаданы по Skill ID|

## Реальные TRAIN / TUNING / AUDIT results

Ниже native episode outcomes по immutable cohort schedule. Это число эпизодов, не gradient targets. Полные slots/splits и selection/settlement diagnostics в evidence archive; атомарные SQL totals — отдельно в EVIDENCE/postflight export.

|Dataset|Scope|Family|Outcome|Exclusion|n|
|---|---|---|---|---|---:|
|autonomous009-goal009-20261113-c000001|PARTY|HEAL|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_MATCHED_PHYSICAL_FACTS_DIFFER|1|
|autonomous009-goal009-20261113-c000001|PARTY|HEAL|PRE_CUT_REFUSED|IllegalStateException|1|
|autonomous009-goal009-20261113-c000001|PARTY|HEAL|INCOMPLETE|—|1|
|autonomous009-goal009-20261113-b-c000001|PARTY|DAMAGE|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_OFFSET_GEO|12|
|autonomous009-goal009-20261113-b-c000001|PARTY|DAMAGE|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED|36|
|autonomous009-goal009-20261113-b-c000001|PARTY|HEAL|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_OFFSET_GEO|6|
|autonomous009-goal009-20261113-b-c000001|PARTY|HEAL|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED|6|
|autonomous009-goal009-20261113-b-c000001|SOLO|DAMAGE|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_MATCHED_PHYSICAL_FACTS_DIFFER|30|
|autonomous009-goal009-20261113-b-c000001|SOLO|DAMAGE|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_OFFSET_GEO|3|
|autonomous009-goal009-20261113-b-c000001|SOLO|DAMAGE|FAILED|—|1|
|autonomous009-goal009-20261113-b-c000001|SOLO|DAMAGE|INCOMPLETE|—|2|
|autonomous009-goal009-20261113-b-c000002|SOLO|DAMAGE|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_MATCHED_PHYSICAL_FACTS_DIFFER|2|
|autonomous009-goal009-20261113-b-c000002|SOLO|DAMAGE|PRE_CUT_REFUSED|IllegalStateException|1|
|autonomous009-goal009-20261113-b-c000002|SOLO|DAMAGE|INCOMPLETE|—|1|
|autonomous009-goal009-20261113-c-c000001|PARTY|DAMAGE|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED|48|
|autonomous009-goal009-20261113-c-c000001|PARTY|HEAL|PRE_CUT_REFUSED|AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED|12|
|autonomous009-goal009-20261113-c-c000001|SOLO|DAMAGE|FAILED|—|6|
|autonomous009-goal009-20261113-c-c000001|SOLO|DAMAGE|INCOMPLETE|—|30|
|autonomous009-goal009-20261114-e-c000001|PARTY|DAMAGE|INCOMPLETE|—|24|
|autonomous009-goal009-20261114-e-c000001|PARTY|HEAL|COMPLETED|—|1|
|autonomous009-goal009-20261114-e-c000001|PARTY|HEAL|INCOMPLETE|—|11|
|autonomous009-goal009-20261114-e-c000001|SOLO|DAMAGE|COMPLETED|—|9|
|autonomous009-goal009-20261114-e-c000001|SOLO|DAMAGE|INCOMPLETE|—|15|
|autonomous009-goal009-20261114-f-c000001|PARTY|DAMAGE|INCOMPLETE|—|17|
|autonomous009-goal009-20261114-f-c000001|PARTY|HEAL|INCOMPLETE|—|17|
|autonomous009-goal009-20261114-f-c000001|SOLO|DAMAGE|COMPLETED|—|2|
|autonomous009-goal009-20261114-f-c000001|SOLO|DAMAGE|INCOMPLETE|—|15|

## Автономные циклы и starts

|Campaign|Cumulative GS starts|Completed cycles|New fits|Cumulative native min|
|---|---:|---:|---:|---:|
|autonomous009-goal009-20261113|2|0|0|7.967033|
|autonomous009-goal009-20261113-b|4|1|0|16.810917|
|autonomous009-goal009-20261113-c|5|1|0|35.604667|
|autonomous009-goal009-20261113-d|6|1|0|37.196967|
|autonomous009-goal009-20261114-e|8|1|0|62.899933|
|autonomous009-goal009-20261114-f|10|0|1|96.720667|

GS1 rejected SOLO level-band misuse до CUT; исправлен canonical forMember. GS2 useful HEAL без admitted terminal credit и matched-physical mismatch, реальная Pause/drain. GS3 B cohort1 иGS4 B cohort2 дали автоматический no-data loop без Codex между циклами. GS5 C завершила96результатов и cold checkpoint старой12без fit. GS6 D завершён с0lawful cells/121refusals. E затем дала60episodes без fit; F дала51episodes и настоящий fit. Все cumulative budgets сохранены; подробности EVIDENCE.
Cold checkpoint с прежними весами и progress reference не считаются новым trained checkpoint. GS7 E упал до CUT на out-of-world service NPC; raw neighbor regression RED→GREEN, bounds выбранных scenes и faction guards сохранены. GS8 и дальнейшие результаты E приведены в таблицах и EVIDENCE. Предел10/90 не сбрасывался.

## Model / optimizer lineage

|Артефакт|Факт|
|---|---|
|DRAFT12 selected weights|6f3dfa43ef8a8335c9ebee16498d7a6406de629c6a85bdd66755675fb55b9546|
|Adam12 raw|85cea56889abeef7b82807ddeba950f588d9bc4e84340c6d454a84bafe72670e|
|Terminal Adam12 weights|7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37|
|Initial optimizer counters|887updates /207epochs|
|Accepted TEST2|6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc|
|New fit count|1|
|Final working model version|14|
|Final working selected SHA|7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37|
|Final raw Adam SHA|6a34b7ee503f35dff75e61ba59892be823f72e45402e1a20f4036b9740ade844|
|Final terminal weights SHA|f830a1cc4ead23f36dd6153fd1d818bbb0ae3b830d9e666f1988a4c82f05e9bf|
|Final Adam updates / epochs|903 / 223|
|Production promotion|false|

Old cold export ZIP31be59ec…CRC/raw Adam verified. Полный stopped SQL byte backup513files/460368518bytes, manifestc6be117f… сохранён вне checkout/private ACL. Старые007/008 codecs и model blobs не переписывались.

## CPU / RAM / latency / disk

|Измерение|Доказательство / ограничение|
|---|---|
|Headless native wall|142313ms суммарно, учтено в90min|
|Headless peak working set|4219949056 /4529262592 /4607107072bytes, три probes|
|Native resource metrics|attempt/native-resources.json сохранены в compact evidence, показываются отдельно от Director|
|Director snapshot|cpuSeconds=11.906, workingSetBytes=133988352|
|Policy latency|Per-episode policyTiming p50/p95/p99/max/allocations; не усреднять p95 разных episodes|
|Safety fallback|5ms/.025/2000ms сохранены; warm read-only encoder/model до CUT|
|CPU budget|JVM processor bound и adaptive idle; не жёсткий мгновенный OS throttle|
|Memory/disk|Проверяются во время workers/native; суточный soak NOT_RUN|

Postflight currentOwnBytes=2772716649, availableDiskBytes=428055465984; исходный размер той же runtime не замерен, поэтому точный disk growth UNKNOWN. Peak/elapsed/CPU каждого последнего native attempt включены в native-evidence/summary.json. ДляD:elapsed93231ms,CPU201.3125s,peak4701855744bytes. Это реальные process metrics, не число logical/materialized actors.

## GUI operations

|Операция|Фактическая проверка|
|---|---|
|Add-Type Windows EXE / SHA manifest|PASS CompileOnly|
|Настоящее окно и CLI status|Прочитано|
|Кнопки Start/Status/Pause/Stop/Resume/Export|PASS:6реальных handlers,2окна/1Director,фактический GUI exit и продолжившийся GS game.ready/socket; WINFORMS_PERFORMCLICK_NOT_DESKTOP_INPUT|
|Два GUI / закрытие / повторное открытие|Реальные PID/creation identity и CLI receipts в gui-acceptance-e; закрытие обоих окон не остановило GS7|
|Visual Studio Designer round-trip|NOT_RUN|
|Computer Use|Прекращён по предпочтению владельца|

## Gates и публикация

|Gate|Фактическое состояние|
|---|---|
|G0|PASS_EXACT_BASE_SCOPE39_OWN_TEST_GEO203_PROTECTED207_FINAL_STOPPED|
|G1|PASS_CANONICAL103_CLASSES_ACTUAL_ACQUIRED5_CLASSES_UNSUPPORTED_EXPLICIT|
|G2|PARTIAL_REAL_MULTICLASS_DAMAGE_HEAL_E_F_BUT_EVAL_LAWFUL_COVERAGE_FAILED|
|G3|PARTIAL_ONE_ACTUAL_FIT_SQL_CHECKPOINT_NO_TWO_COMPLETED_TRAINED_CYCLES|
|G4|PARTIAL_COLD_TWO_COMMIT_FAULTS_EXACTLY_ONCE_PASS_NATIVE_STOP_UNCONFIRMED|
|G5|PASS_REAL_SIX_WINFORMS_HANDLERS_TWO_WINDOWS_CLOSE_INDEPENDENCE|
|G6|FAIL_NATIVE_EVAL_ZERO_LAWFUL_CELLS_ZERO_PAIRS_NO_BEHAVIOR_VERDICT|
|G7|FAIL_NATIVE90_LIMIT_PROOF_STOP_UNCONFIRMED_REGRESSIONS_AND_FINAL_CLEANUP_PASS|
|G8|PENDING_SCOPED009_PUBLICATION_AND_INDEPENDENT_GITHUB_READBACK|

Scoped commit/push разрешён TASK только в009. Exact Git commands, publication SHA и independent connector readback фиксируются в COMMANDS/EVIDENCE; merge/force/main публикации нет. Состояние008не принимается задним числом.
009—задача№3из13 после006R1; осталось10дообязательного пересмотра. Новый Goal/Slice не создан.

## Per-class readiness

|Class ID|Canonical name|Theoretical learn facts|Actual learned counts|Supported-for-train metadata counts|Readiness|
|---|---|---:|---|---|---|
|0|FIGHTER|56|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|1|WARRIOR|160|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|2|GLADIATOR|639|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|3|WARLORD|491|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|4|KNIGHT|149|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|5|PALADIN|575|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|6|DARK_AVENGER|558|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|7|ROGUE|155|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|8|TREASURE_HUNTER|436|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|9|HAWKEYE|399|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|10|MAGE|61|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|11|WIZARD|183|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|12|SORCERER|554|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|13|NECROMANCER|628|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|14|WARLOCK|540|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|15|CLERIC|181|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|16|BISHOP|676|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|17|PROPHET|510|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|18|ELVEN_FIGHTER|60|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|19|ELVEN_KNIGHT|151|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|20|TEMPLE_KNIGHT|545|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|21|SWORDSINGER|386|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|22|ELVEN_SCOUT|185|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|23|PLAINS_WALKER|530|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|24|SILVER_RANGER|513|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|25|ELVEN_MAGE|59|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|26|ELVEN_WIZARD|174|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|27|SPELLSINGER|600|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|28|ELEMENTAL_SUMMONER|536|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|29|ORACLE|184|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|30|ELDER|643|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|31|DARK_FIGHTER|60|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|32|PALUS_KNIGHT|152|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|33|SHILLIEN_KNIGHT|619|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|34|BLADEDANCER|430|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|35|ASSASSIN|187|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|36|ABYSS_WALKER|592|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|37|PHANTOM_RANGER|550|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|38|DARK_MAGE|62|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|39|DARK_WIZARD|183|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|40|SPELLHOWLER|602|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|41|PHANTOM_SUMMONER|562|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|42|SHILLIEN_ORACLE|186|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|43|SHILLIEN_ELDER|542|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|44|ORC_FIGHTER|49|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|45|ORC_RAIDER|160|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|46|DESTROYER|525|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|47|ORC_MONK|125|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|48|TYRANT|462|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|49|ORC_MAGE|53|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|50|ORC_SHAMAN|158|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|51|OVERLORD|648|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|52|WARCRYER|527|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|53|DWARVEN_FIGHTER|33|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|54|SCAVENGER|130|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|55|BOUNTY_HUNTER|584|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|56|ARTISAN|120|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|57|WARSMITH|467|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|88|DUELIST|662|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|89|DREADNOUGHT|512|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|90|PHOENIX_KNIGHT|609|40|4|ACTUAL_ACQUIRED; native admission отдельно|
|91|HELL_KNIGHT|592|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|92|SAGITTARIUS|418|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|93|ADVENTURER|459|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|94|ARCHMAGE|574|60,62,65|9|ACTUAL_ACQUIRED; native admission отдельно|
|95|SOULTAKER|649|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|96|ARCANA_LORD|571|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|97|CARDINAL|699|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|98|HIEROPHANT|530|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|99|EVA_TEMPLAR|578|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|100|SWORD_MUSE|414|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|101|WIND_RIDER|553|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|102|MOONLIGHT_SENTINEL|533|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|103|MYSTIC_MUSE|622|61,64|10|ACTUAL_ACQUIRED; native admission отдельно|
|104|ELEMENTAL_MASTER|566|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|105|EVA_SAINT|665|53|7,8|ACTUAL_ACQUIRED; native admission отдельно|
|106|SHILLIEN_TEMPLAR|652|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|107|SPECTRAL_DANCER|457|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|108|GHOST_HUNTER|615|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|109|GHOST_SENTINEL|570|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|110|STORM_SCREAMER|625|64,67|11|ACTUAL_ACQUIRED; native admission отдельно|
|111|SPECTRAL_MASTER|593|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|112|SHILLIEN_SAINT|564|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|113|TITAN|545|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|114|GRAND_KHAVATARI|484|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|115|DOMINATOR|672|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|116|DOOMCRYER|546|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|117|FORTUNE_SEEKER|616|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|118|MAESTRO|487|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|123|MALE_SOLDIER|54|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|124|FEMALE_SOLDIER|56|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|125|TROOPER|162|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|126|WARDER|171|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|127|BERSERKER|532|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|128|MALE_SOULBREAKER|555|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|129|FEMALE_SOULBREAKER|564|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|130|ARBALESTER|542|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|131|DOOMBRINGER|549|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|132|MALE_SOUL_HOUND|577|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|133|FEMALE_SOUL_HOUND|586|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|134|TRICKSTER|559|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|135|INSPECTOR|458|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|
|136|JUDICATOR|467|UNKNOWN|UNKNOWN|NO_ADMISSIBLE_ACTUAL_SOURCE; навыки не выдавались|

## Финальный итог по указанию владельца

**PARTIAL. Задача закрывается без новых GS, опыта, обучения и увеличения бюджета. FULL009 не достигнут; final manual gate REQUIRED.**

Получен реальный autonomous SQL checkpoint DRAFT14: Adam887/207→903/223,48applications/16gradient episodes,3classes. Холодный rollback до COMMIT и recovery после COMMIT проверены; после EVAL raw weights/Adam/apps совпадают, повторного credit нет. TUNING не улучшился, best selected epoch0; selected14 — прежние terminal12 weights, новый terminal Adam14 другой. AUDIT MSE14=0.014208027 против collection teacher12=0.005066568; улучшение поведения не доказано. Завершённых полных F cycles0: EVAL и graceful exit сорвали переход к cycle CHECKPOINT. Долговечность нового SQL checkpoint подтверждена cold export.

Независимый EVAL GS10:0lawful cells/222planning refusals/0episodes/0pairs; evaluation.complete отсутствует. batch.complete подтверждает trainingStateUnchanged=true, cold export подтверждает отсутствие градиента. После budget stop получен STOP_UNCONFIRMED; JVM позднее вышел сам, identity guard отказал до Kill. Финально STOPPED/ports0/OFF. Списано консервативно5803240ms=96.720667min при разрешённых90; эта верхняя оценка включает задержку recovery после выхода. Точное время native exit не доказано; строгий90min gate FAILED. Новых GS/сбора/обучения после указания владельца не выполнялось.

Итог безопасности: own Director/native/worker/SQL отсутствуют; порты33357/18001/19301 свободны; Geo203/protected207 exact hashes PASS. Config OFF, обе fault flags и GUI hold выключены; shutdown wait возвращён180s. Accepted TEST2 и MAIN/PLAY не менялись. Аварийный Kill не выполнялся: перед действием exact identity guard вернул PROCESS_UNAVAILABLE, JVM уже завершился; затем штатный Stop закрыл свой SQL.

Консервативный счётчик5803240ms включает время до pending recovery, в том числе задержку после фактического выхода JVM. Это не точный замер96.721мин активного JVM. Точное время выхода не установлено; соблюдение90мин не доказано, budget gate FAILED. Счётчик сохранён, не обнулён и не увеличен разрешённый ceiling. Starts10/10, pending=false. Нативных повторов не было.

Cold SQL working14 подтверждён двумя независимыми экспортами до и после recovery/EVAL: raw selected/Adam/targets/apps/labels совпали;48applications,16gradient episodes,3classes. Исторические117строк моделей/Adam/apps1–12 дали прежнийSHA E0BD464544A67AC4D14292DED4BF32EFBF82F6DDF86E2D4937264929063F2309. Отрицательные raw outcomes и legacy settlement flags не переписаны; фактический training credit определяется SQL applications.

`cold-checkpoint.json` содержит последний завершённый cycle pointer model12; после срыва EVAL фаза cycle CHECKPOINT не достигнута. Это отдельный незакрытый аспект статуса; долговечный SQL checkpoint14 и его terminal Adam903/223 проверены холодным экспортом, pointer вручную не переименовывался.

Незакрыты G2/G3/G4/G6/G7: неполная lawful coverage, нет двух завершённых обученных cycles, STOP_UNCONFIRMED,0EVAL pairs и провал native90-limit proof. G0/G1/G5 подтверждены в указанном bounded scope. G8 завершается только обычной публикацией009 и независимым GitHub readback. Низкий MSE и факт Adam updates не считаются улучшением поведения. VisualStudio Designer round-trip и24h soak NOT_RUN; nightReady=false.
