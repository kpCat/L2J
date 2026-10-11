# RESULT_HANDOFF036

RESULT036=GREEN_CURRENT_PATH. M1=OPEN; REAL_FINAL=NOT_RUN.
semanticFix=NONE; historical27Cause=UNKNOWN; currentFirstFailure=NONE в положительном native/live пути.
Это проверка текущей цепочки, не утверждение об исправлении исторического incident27.

BASE=c0e86fa2950012ae2ee898b841524fa160869a77.
SOURCE_SHA=1d71344180cc8ff4e12466471efc853ee915dd62.
JAR_SHA=F72E7AD13D3963ADE3C4BEEE9647F30EE97EE5020E0939F7AD6601110993BC24.
Branch=experiment/m1-candidate007-observe008; OWN=phantom-dev, detached; MAIN только read.
MODEL: пользователь запросил GPT-6.1 Sol / Very High; настройка приложения отдельно не аттестована.
START_UTC=2026-10-11T13:55:47Z. elapsed/publication SHA: внешний final receipt после commit.
Successful production Jar builds=2; failed compile attempts=1 без Jar; full GameServer starts=2; full scenes=1.
Первый полный сервер запущен к14:08:46Z. Субагенты не использовались.

## Прочитано и переиспользовано

Прочитаны AGENTS.md модуля, master plan, CODEX_WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD и весь пакет036;
accepted035 отчёт/индекс/runner и ops035; PlayerNativeWork, Creature/MagicUseTask, PhantomNativeWorkScope,
PhantomMaterializedPlayer/materialization, native Handoff/continuation/dynamic-recipient fixtures026/027,
inventory hash/state/model/codec/context и существующие build/test targets.
AGENTS.md в корне checkout/его предках и промежуточных docs-папках не найден; повторный поиск не выполнялся.
Локальные аналоги: dispatch EVENT trace; Handoff.Fixture; NativeLifetime; runner035/observer032; reload035.
JDK25, Ant1.10.17, MariaDB11.4/127.0.0.1:3308; только новые036a/b/t, без миграций и нового runtime слоя.
Не менялись shared TEST, старые DB, пулы/очереди, задержки, tactics/rates, PlayerNativeWork/MagicUseTask и inventory035.
Число импортированных документов — явное task-family исключение GIT036. Код изменён в пяти exact paths.

## Изменения

- PhantomNativeWorkScope: existing bounded EVENT ring8 дополнен шестью точными cast continuation kinds;
  scalar parent/depth/semantics, sample clock и completed count. Diagnostic dispatchStage под existing monitor
  не привязывает finished Future повторно после COMPLETED/CANCELLED. Earned/start/cancel/drain semantics прежние.
- Headless environment: exact036t manifest/config/cwd/export/JDBC и exclusive-lane guard; прежние guards сохранены.
- Q15: expected grant выводится из фактического quest-item template и QUEST_ITEM_DROP_AMOUNT_MULTIPLIER;
  добавлен счётчик exactly-once delivery. Штатный Quest.giveItems не изменён; requested1 при rate20 даёт20.
- NativeCastDrain036Suite + package-local Checks: actual doCast/MagicUseTask через существующие fixtures,
  pending/running drain, abort, parent/root checks, zero-delay, original body fault и sibling, cooperation/corpse,
  ordinary Player и diagnostics-off. Native fixture lifetimes включают epochs458193146298400/458201327777500/
  458210321648600 и отдельные two-caster lifetimes; вывод не ограничен profile27.

## Диагноз A–H и clocks

Base E01 RED: cast-launch есть в nativeOwnerWork, но отсутствует в EVENT-only dispatch trace.
Base E07 RED: реальный zero-delay callback завершился; поздний SUBMITTED восстанавливает diagnostic Future reference.
J1 и итоговый J2 GREEN. Это diagnostic corrections, не доказательство потери earned body на base.
Current classification A; pending launch C: TEST delay7624ms и live delay3599ms положительны,
до due owner удерживается в DRAINING; launch→hit→finalizer приняты как transitive EARNED, новый root отклонён.
G: release выполняется после FINALIZED, затем stock memory inventory unload; control API требует повторного poll
с одним исходным deadline. E10/S05 подтверждает, что повторный вызов не продлевает общий deadline.
B/D/E/F/H не установлены как current semantic defect; unknown callback не replay, пулы/deadlines не менялись.

Пример actual live ticket878/cast-launch/epoch457715115727600, parent877/depth2/EARNED:
reserve457738078903900; submit457738078918800; executorEntered457741678179400;
start457741678180800; bodyExit457741678394500; end457741678394600; delay3599ms; SUCCESS.
Future в момент finish может иметь done=false: FutureTask публикует done после выхода wrapper.
NONE не означает отсутствие submit. Скалярные snapshots NONATOMIC_VOLATILE, work/dispatch/evidence clocks разные;
ring8 и helper dispatch budget1024 не увеличены. Нельзя читать отсутствие в bounded ring как отсутствие исполнения.
Настоящее native тело/поток подтверждено MagicUseTask stack в E04/E08; per-ticket thread name отдельно не записывается.

Historical27: object268484461/epoch404179606830700, parent403903739619700 terminal verified;
ticket9692 cast-launch RESERVED/EARNED/depth2/age3459ms. Work clock404391086713600 старее capture404396162199900.
Без actual Future/due и original publisher failure это не различает C/D/E/F. Ранние failures36/45 не доказывают
причину27. Выбранные034 inputs SHA проверены; historical cause остаётся UNKNOWN.

## Текущий full GameServer и conservation

FINAL_SCENE036b: natural pre-World Synthetic setup,380.63s, cohort6,371 samples,maxGap1.09s,2 mailbox commands.
actual caster27/object268484461/epoch457715115727600:54 distinct COMPLETED native launches;
51 cumulative native farm cycles,46 после baseline, native EXP/SP delta13485/1550 за наблюдение.
CAST_DRAIN=PASS; FULL_SAVE=PASS; GLOBAL_DRAIN=DONE/COMPLETE retained0, все три pools ещё live на typed boundary.
All370 native births имеют exact terminal;1678 ledger frames имеют continuous sequence/run и raw SHA bindings;
RUN_END exporter_drained=true,dropped0,failure empty. World/AutoSave/lease отсутствуют ПОСЛЕ FINALIZED.

| profile | epoch | FULL items | final EXP/SP | final state row |
|---|---:|---:|---|---:|
|27|457715115727600|57|43920/7876|30898|
|28|457715135780600|137|98915/12543|21556|
|40|457715172606200|57|46985/8286|39810|
|57|457715193072800|15|17344/1777|13919|
|106|457715210629400|44|26530/3733|13799|
|173|457715257617300|27|17433/1816|8250|

Для всех6 sealed full inventory hash/serialized state/native context version+digest/SQL EXP/SP/vitals/позиция
совпадают; receipt связывает PREPARED/FINALIZED и terminal. Награды/HP/MP не компенсировались.
E12 current036b36/epoch457663255552500 и45/epoch457665135725400: REGISTER/PREPARED/FINALIZED/SQL
сохраняют точные252 строки (249 INVENTORY+3 PAPERDOLL,0 quest,nonquest252,cap250).
H36=e64e08cbfe2e93d5496212ae08d2efb8694c0ad4ed1fcda14c0b0e0959c9d18c;
H45=c0205ddbdeec5acb027ff34f2d9a47de94bc4eb046ea07111d9d68c13f7c16b4.
SameDB native reload2/2:252/native context COMPLETED,cap250/new-slot refused, state bytes unchanged.
TERMINAL memory items0 ожидаемы после Player.deleteMe→inventory.deleteMe; SQL/reload252 не потеряны.

COHORT_CONTINUITY=FAIL по unchanged CONTINUITY_V2:27 PASS;28/40/57/106/173 сохранены со всеми faults,
включая недостаточные cycles/tail rewards/complete lineage,173 NATIVE_UNPROVEN.
LEGACY_STRICT_FARM=NOT_APPLICABLE_COUNT6. Общий living-world PASS не заявляется и evaluator не ослаблялся.

## Финальные проверки

| Проверка J2 | Результат |
|---|---|
| native036 composed |9/9|
| original lifecycle027 acceptance, включая S05 |11/11|
| relatedQ15 teardown |1/1|
| inventory035 focused |6/6|
| projection030 / capacity031 |4/4;2/2|
| unchanged model / SQL / compact |9/9;11/11;2/2|
| required matrix18 routes |93/93|
| native252 sameDB reload |2/2|
| raw ledger/FULL_SAVE/current E12 rows |PASS|

E01/E02/E03/E04/E05/E06/E07/E08/E09/E11 покрыты composed9 и actual trace;
E10 — unchanged S05;E12 — focused6/current exact rows/reload2;E13 — scene380s.
E09 intentionally FAILED_TEST_DISPOSAL_AFTER_QUIESCENCE сохраняет original first failure и negative ownership;
здоровый уже опубликованный sibling завершается. Это не healthy global shutdown proof; здоровый full shutdown доказан отдельно.

## Раскрытые неуспешные попытки и NOT_RUN

Сохранены base E01/E07 diagnostic RED и Q15 RED; J1_NATIVE0362/4 (TEST не poll control API),
J1_ADDITIONAL0363/4 (два stock79HP hits законно убили NPC126HP). Исправлены только test premises;
J1_SHARED036b1/1, composed J1/J2 9/9. E03 первая ранняя fixture close имела suppressed drain timeout;
это не доказательство producer defect. Self-generated duplicate guard tail вызвал compile42 errors и был
восстановлен exact base bytes + four bounded edits; первый Jar compile упал на пропущенном constructor call,
после исправления оба clean Jar builds успешны. Старые runFinalization warnings сохранены.
Private publication parser/whitespace gates исправлены; immutable SOURCE_MAP.tsv пустые завершающие TSV cells
сохранены по manifest, whitespace exception ограничен этим файлом. Initial private inventory proof ошибочно
требовал252 после stock memory unload; raw FAILED сохранён, corrected phase proof PASS.

NOT_RUN: второй optional scene; следующий task/Goal; общий24-case suite на неподходящей DB;
historical old fixture/повторный10000 corpus (codec/protected035 unchanged, текущие focused guards выполнены);
semantic cast fix, navigation/tactics/rate changes; REAL_FINAL. Нельзя переносить PASS035 на gameplay036.

## Завершение

OS cleanup=PASS: own/all JVM0, listeners7777/2106/9014/5031 отсутствуют,036a/b/t idle и сохранены;
304 original runtime035 files,7 reused helpers,14 MAIN packet files и untouched SOURCE_MAP paths проверены.
Все27 final test provenance records привязаны к одному J2 Jar.
Archive=TASK036-review.zip,58539855 bytes,7819 ZIP entries size/SHA verified.
SHA256=99cc1cfba46b98cdca949ce6a07fa1d6d9efe26aba9b5a3af4fde0434252abe8.
Путь: C:/Users/ZBook/.codex/visualizations/2026/10/11/01a12b3f-7a16-7301-a9ab-116daca1d868/.
Final publication SHA/local=remote находится в TASK036-publication.json после commit; SOURCE_SHA выше — build freeze.
Disk inventory/audit остаётся PAUSED, удаления0; worktree и036a/b/t сохранены.
Public Git содержит только exact code/test/task family; raw/logs/Jars/private helpers находятся во внешнем архиве.
mojibake-маркеры в изменённых файлах проверены отдельной проверкой.
escaped Cyrillic в изменённых файлах проверены отдельной проверкой.
Manual M1/final gate не отмечен passed; следующий task автоматически не запущен.
