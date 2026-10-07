# Конечный контракт TASK023

Нельзя объявлять успех всей пачки на основании2kills одного actor либо TEST с passive NPC.

ENGINEERING_PASS:
- собственные RED и исправления N02/N03/N04/N06 и ordinary controls;
- no unexplained changed-path regression; dynamic 1/7→2/7 не достаточная цель, valid
  свежие соответствующие контракты должны быть GREEN, старые ожидания — задокументированы;
- lifecycle N/L targeted gates, owned store и stale-epoch safety;
- clean build exact committed SHA, scoped diff/UTF8 guards;
- TEST exact restore, PLAY_WRITES=0, no fake progression.

SERVER_M1_PASS:
- ENGINEERING_PASS;
- full-server synthetic SceneA и SceneB на ОДНОМ frozen SHA/config без fixes между ними;
- >=4 natural cohort, preselected2 primary по>=5полных same-epoch cycles в каждом scene;
- progress остальных не скрыт, нет permanent idle/unbounded retry/водных забегов;
- применимый native loot с stock protection/capacity; raw inventory corroboration;
- death/recovery, soft leave/background/remat, graceful restart, owned crash consistency;
- штатный final drain retained0, tickets/timers0, exact persisted state/receipts.

Каждое поле отдельно: FARM_A_PASS, FARM_B_PASS, COHORT_PASS, LOOT_PASS, DEATH_PASS,
SOFT_RETURN_PASS, RESTART_PASS, CRASH_PASS, CLEANUP_PASS, ENGINEERING_PASS.
UNKNOWN/NOT_OBSERVED не равно PASS. Independent unrelated old failures могут быть отмечены
KNOWN_UNRELATED_DEBT только с unchanged-base доказательством и обоснованием нерелевантности;
earned/transfer/store/lifecycle false regressions к M1 относятся и не игнорируются.

TASK_RESULT=GREEN только при SERVER_M1_PASS=true.
Тогда M1=WAITING_FINAL_CLIENT, а не CLOSED: визуальный pop-in/поведение client renderer
сегодня не проверены. REAL_FINAL_PASS=NOT_RUN. Подготовить запуск для10–15мин реальной
приёмки утром без предварительного нового refactor.
Иначе TASK_RESULT=BLOCKED либо FAILED с точным failed gate/последним безопасным состоянием.
Никакой формулировки «цель достигнута» при farm/cleanup=false.

Не разрешается менять эти thresholds/определения, обнулять sensor, пересоздавать epoch
primary во время замера либо отбрасывать неуспешных членов cohort для GREEN.

## Прежние продуктовые имена сохраняются
NEW_MATERIALIZATION — собственный Player появился через production lifecycle.
CONTACT — native observer locality действительно включает/удерживает эту когорту.
NATIVE_LIFE — повторяемые полезные native действия, не флаг AutoPlay и не self-heal.
COHORT — таблица всей замороженной группы, не единственный удачный actor.
SOFT_RETURN — native departure/background/reentry без скрытого reset/teleport Phantom.
RESTORE/STOP — exact persistence, no leak/retained при штатной остановке.
Все эти gates остаются требованиями, никакое новое название SERVER_M1_PASS их не отменяет.

В natural farm scenes каждый actor должен иметь хотя бы один reward-bearing target
transition в ПОСЛЕДНИЕ120s. Ранний burst и затем минуты idle не являются continuous PASS.
Отдельный death/long-rest сценарий проверяется отдельно, не переименовывается в farm PASS.
