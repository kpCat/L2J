# Roadmap: M1 не заменяет M2–M7

Цель038 — только общая локальная исполнительная связь. Ниже product milestones,
а не новые списки задач на100 файлов и не утверждение реализации по названию класса.

| Этап | Пользовательский результат | Что переиспользуется/сохраняется |
|---|---|---|
|M1 Living World|native materialization→местное движение→фарм/loot→bounded recovery→смерть/возврат→soft exit→фон→return; save/restart/crash; real final|Player,existing decision/navigation/background/owned-store.038 закрывает только локальный action-loop и группы|
|M2 Real Companion|«го кач?»→смысл/ответ→party→follow/assist/heal/buff/recharge/res→partychat; сам приглашает|existing semantic/party route/tactics/capabilities; same action/position result, real-target constraints, новая role-aware приёмка|
|M3 Social Life|инициатива речи,характер/настроение,память/отношения,русскийL2сленг|existing semantic/social/affiliation; preferences меняют utility, не native legal guards|
|M4 PvP/PK/Gangs/Clans|focus/defense/retreat,свои/чужие,месть/риск,войны|existing PvpPolicy/ClanService/target snapshots; действие с другой policy, не PvE hardcode|
|M5 Raids/coordination|роли/assist/heal/buff/regroup/death recovery|existing RaidDecision/Readiness/Recruitment/Assembly/PartyTactics; shared attack законна|
|M6 Instances/Rift/Kamaloka|REAL+Phantom остаются одной группой/instance; native entry/exit|same identity/target includesinstance; native teleporter/script transitions; нельзя угадывать этаж поXY|
|M7 Progression|20/40/76,skill learning,subclass/noblesse,долгая жизнь|existing progression/catalog/capability facts; re-resolve на фактической смене class/skill/gear|
|M8 Gear|получение/покупка/craft/quest/drop/enchant и настоящее экипирование|native inventory/экономические резервации,не выдача из воздуха|
|M9 Economy|buy/sell,private/offline shops,mail/COD,редкие предметы|native conservation/limit policy; virtualraid не бесплатный duplicated epic loot|
|M10 Integration/performance|распределение,плотность,имена,сложныеgeo,1280→3000,позже10000|внешний нагрузочный этап после native behavior; отдельные budgets/профилирование|

## Контракт расширения
- Role policy выбирает action и объект/группу; universal controller решает approach/continuation.
- Validity зависит от действия: enemy attack, friendly heal, corpse resurrect/Sweep различаются.
- У всех action есть owner epoch/goal version/target identity/instance, native precondition и
  observable result. Skill damage/heal/cooldown и item operations всегда от Mobius.
- Party/combat owner takeover прекращает FARM новые roots; старые earned завершаются.
- Utility ratings не grant privileges. Personality не меняет IDENTITY/SQL/native legality.
- Существующие M2–M7 классы сначала ревьюить/переиспользовать, не клонировать по новому имени.

## Оставшийся маршрут до M1 после038
1) Общая local action chain и повторяемые natural группы.
2) Явно выбранная lifecycle-campaign: away→productivebackground→return; death/reentry.
3) На финальном Jar conservation/дваsameDBrestart/crash окна с lineage,без lateSQL подмены.
4) WAITING_REAL_FINAL → один согласованный realclient gate → только затем M1=CLOSED.
Это4 результата, НЕ обещание ровно4 Codex задач. Новые механизмы при отсутствии дефекта не писать.

Увеличение radius не входит038: later отдельный prewarm/visibility/dematerialization hysteresis
и cap/perf тест. Увеличение толькорадиуса не гарантирует больше active actors при cap8 и может
перенести конкурентную нагрузку; это не средство скрыть idle/locality bug.
