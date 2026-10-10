# Стартовые факты из TASK031 — не новый аудит всей истории

## Identity кандидата
Required base df6ee99c71a4c8b07ad05545b200e113d3b0d3c9.
Его parent b9ffac8af58e988e9e7c16f2f3a4c28546c3168c — финальный report/task helper.
Runtime source031:7e765ca426e089e5cd9837bde84d749236657420.
Java tree:df3732e6a5c2849f1ce83166a8735e2a5b2ad07e.
GameServer.jar SHA256:98C1A4D9C7379C8C0B2F7A04C886513F6461A2630D4498E6A197D2F7E262D5B7.
Эта identity дана опубликованным отчётом; перед reuse проверить фактические bytes/tree.

## Где лежат первичные данные
Старый worktree READ ONLY:
C:\Users\ZBook\.codex\worktrees\m1-native-demand-031\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\
Отчёты: docs/phantoms/tasks/PHANTOM-M1-NATIVE-DEMAND-031/RESULT_HANDOFF031.md,
CHECKPOINT_C21.md, CHECKPOINT_C24.md, CHECKPOINT_C26.md,
evidence/FINAL031_PUBLIC_SCALARS.json, evidence/C28_POST_NATIVE_PROJECTION_METADATA.json.
Private raw — evidence и .phantom-local/ops031; найти точные paths по этим отчётам,
не сканировать всю машину и не публиковать SQL/JFR/аккаунты.
Retained DB l2jmobiush5_localplay_contract031a/b/t — read/export only.

## Подтверждённое на конечном кандидате031 (reported; проверить referenced evidence)
93/93 обязательной matrix18 routes, lifecycle02711/11.
C26_FROZEN_CONTINUITY38:380.8903016с,372 samples,maxgap1.0465063с.
IDs3,43,95,117,121,155,195,204; primaries3/43. Общий результат6/8.
Cycles:12/2/41/18/18/18/25/21 соответственно.43 и117 FAIL.
Native finalized→SQL8/8; первый actual sameDB restart34:8/8.
Второй restart35: strict2/8, шесть отличий только XYZ; это OPEN, не доказанная потеря.
Последний stock shutdown DONE/COMPLETE перед pools; retained/pending/refs0.

## Незакрытые причины
43: owner/actionAdmission ACTIVE/open, alive, sitting=false, MP104, HP302;
visibleSuitable=false, AutoPlay-сессия уже отсутствует.
Первое FALSE внутри visibleFarmReady не снято. Три исходные группы условий:
- same-epoch cooldown;
- protocol/route/target exclusions (это ТРИ разные подпричины);
- stored-goal equality.
Поздний journey_deadline НЕ доказывает первый producer.
117:18cycles, USEFUL_PROGRESS_DEBT. Отдельная хронология пока не разобрана.

Away: старый Walk использовал 1Hz frame, хотя arrival0.1s уже показывал новое положение.
ReadArrivalFrame031 теперь используется в ОБОИХ местах. Source/task fix есть, runtime
GREEN нет; не реализовывать это заново. Старые false371.2479 и actual224 — counterexample.

Restart35: для117/121/155/195/204 найден committed projection c beforeRowVersion и
quietVersion=before+1; это НЕ полная lineage и НЕ объяснение изменения координат.
Для95 такой последний projection receipt не найден в просмотренном каталоге.

Crash72/73 на final Jar98C1 NOT_RUN. На прежнем Jar87 есть частичные proof;
AFTER_NATIVE имел diagnostic barrier timeout. Старое не переносить на текущий код.

## Не открывать заново без нового RED
Inventory157→165 representation, rollDrops empty-list guard, EXP-only target settlement,
Synthetic initial location, честная совместная атака, stock standUp affordable sitting,
снятие опасного SQL из checkpoint, basic drain027 — уже реализованы.
C21 на другом Jar и другой группе прошёл8/8+save8/8+restart8/8+8/8.
C26=6/8 не доказательство regression относительно C21: изменились и код, и cohort.
Не откатывать к C21 на основании одного удобного результата.
