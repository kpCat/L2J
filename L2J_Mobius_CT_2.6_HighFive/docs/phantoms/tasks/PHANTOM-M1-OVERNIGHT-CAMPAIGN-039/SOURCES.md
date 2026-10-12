# Source-backed аудит перед TASK039

Repository: kpCat/L2J; branch: experiment/m1-candidate007-observe008.
Exact BASE:8bf4df23a23854b9ff57c13b83a8ab34f07aa4c2.
Remote ref и parent95c0572f прочитаны через GitHub connector. Координатор ничего не менял
в удалённом репозитории. Public038 report/evidence/implementation просмотрены.
Private Windows raw не запускался координатором: конкретные runtime root causes
остаются гипотезами до причинного native proof.

Прочитанные пути относительно L2J_Mobius_CT_2.6_HighFive:
- docs/phantoms/tasks/PHANTOM-M1-ACTION-LOOP-038/RESULT_HANDOFF038.md
- docs/phantoms/tasks/PHANTOM-M1-ACTION-LOOP-038/IMPLEMENTATION038.md
- docs/phantoms/tasks/PHANTOM-M1-ACTION-LOOP-038/tools/verify_lifetimes038.py
- docs/phantoms/tasks/PHANTOM-M1-ACTION-LOOP-038/Verify038.ps1
- java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementController.java
- java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementPlanner.java
- java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java (140–345)
- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java (1230–1610)
- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java (275–520,940–1188)
- java/org/l2jmobius/gameserver/localplay/LocalPlayPhantomStoreJournal.java (полный)

Шаблон точного source URL, где <path> заменяется строкой выше:
https://github.com/kpCat/L2J/blob/8bf4df23a23854b9ff57c13b83a8ab34f07aa4c2/L2J_Mobius_CT_2.6_HighFive/<path>

Заключение координатора: в коде есть разные моменты — успешный commit с проверкой
canonical и отложенный внешний SQL read. Их необходимо различать, сохраняя проверку
native полей и полного инвентаря. Потенциальный ранний return перед observeProgress
не объявлен доказанным producer437 без RED.

Пакет даёт полномочия на кампанию, а не утверждает универсальность action-loop во всех
локациях или эксплуатационную стабильность. Поиск внешних AI-библиотек заново не проводится.
