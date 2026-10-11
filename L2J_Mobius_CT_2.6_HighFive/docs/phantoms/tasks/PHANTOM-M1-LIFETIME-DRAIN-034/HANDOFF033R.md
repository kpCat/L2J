# Зафиксированные входные данные TASK033R (не выдавать за собственное воспроизведение)

Публичный frozen result: https://github.com/kpCat/L2J/blob/44139ca556ad3d5ac35309d5fe222269d3ada256/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/RESULT_HANDOFF033R.md

- Final branch HEAD: `44139ca556ad3d5ac35309d5fe222269d3ada256`. R3 production commit `51cf051b3d20fbb83e6c0e16ce8c7bcad7af52cb`, final report commit не содержит нового production Jar.
- On R3 clean Jar: native obstacle route → original Player arrival → ≥5 consecutive real farm cycles for primary123; actual 10 cycles, EXP+3458/SP+161. Matrix 93/93 and lifecycle 11/11 reported PASS.
- Final natural R3 scene: initial cohort `123/328/375/437/492/601`, strict whole `0/6` for COMPLETE_LINEAGE_REQUIRED and calendar departures. Эти неудачи — другой gate; не смешивать их с cleanup.
- **Все 6 scene lifetimes** в C79: `DETACHED`, outstanding=0, pendingTimers=0. При этом shutdown глобальной materialization: `retained3`, FAIL.
- Final clone до Synthetic already reproduced bounded inventory cleanup incidents with object IDs `268484533/268484616`, mentioned as examples in report; полный набор retained3/первые incidents не установлен публикацией. Нельзя заранее считать все три legacy или сопоставлять их с этими двумя по догадке.
- Global retained4 на более раннем R2, retained3 на R3. Возможно разный cohort, причина не установлена.
- C76V native FINALIZED fields 6/6 совпали с stopped SQL, pending owned stores=0; ошибка валидатора — unhealthy subsystem drain. Не превращать этот факт в доказательство отсутствия retained identity.
- Own Game/Login OS stopped штатно. TASK033R worktree удалён, raw review вынесен наружу с SHA-256; a/b/c/t базы сохранены.
- `returnLanding` runtime random town/offset50: транспортный gate не закрыт. Не заниматься им в TASK034.

**Разделять 5 разных вещей:** (A) active materialization entry, (B) retained native identity lease, (C) cleanup incident archive entry, включая finished, (D) failed materialization/cleanup result и (E) SQL persisted background/catchup state. Они могут коррелировать, но не равны по определению.

Нужно установить точные actor/profile/objectId/epoch, source first incident, current blocked cleanup phase, наличие живого Player/World/AutoSave, native owner/leases, state+goal и цепочку до shutdown. Одна лишь поздняя выборка SQL или агрегат `retained3` причины не доказывает.
