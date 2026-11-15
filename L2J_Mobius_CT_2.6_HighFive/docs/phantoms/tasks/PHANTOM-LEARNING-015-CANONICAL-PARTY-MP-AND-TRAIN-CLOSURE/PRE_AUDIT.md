# PRE_AUDIT — исходные подтверждённые факты (GitHub HEAD 9a4495b9)

## Почему 015 не просто «ещё одна сессия»
Из `RESULT.md`/`PROFILING_RESULT.md`:
- Baseline014 readiness S1 272357 ms, natural recovery 199668.479 ms; S2 515549 ms, recovery 413013.777 ms. Это существенный bottleneck, не SQL/материализация.
- Ранний доказанный HEAL отказ исправлен: `FULL_RECOVERY_HAS_NO_WOUNDED_HEAL_RECIPIENT`, ~0.1742 ms вместо ~134458.889 ms ожидания. **Не откатывать**, не переписывать систему баффов.
- Два Party для class103 тратят 134460.161 + 134543.559 ms и затем получают `WAIT_GROUP_MP`, required 3060, actual fully recovered2806, 80% allowance2244.8. Парность/физические условия не позволяют слепо удалять произвольных членов.
- S2 сформировала 1 complete Solo A/B (class94, real DAMAGE 1230:28 и1275:14) и одну Party STOCK-arm (class110, 4 реальные альтернативы), но 0 sealed training admissions; G3 FAIL, GS0 FIT0.
- Уровень и заточка берутся из `PhantomSkillInventory.acquired(Player.getAllSkills())`. Hydro Blast1235:28 присутствует у class103; не забывать inherited skills и не считать все theoretically available acquired.

## Native production authority
`L2jPhantomPartyBackend.autonomousFarmTarget` / `autonomousFarmEncounterReady` используют общую приватную `groupViable(List<Player>,List<MemberSnapshot>,Creature,witness,boolean continuing)`.
В `groupViable` MP check расположен после расчёта DPS, regen, duration, target threats, healer, ammos. Для каждого участника `actions = requiredActions(millis, intervals[index])`, затем `actions*costs[index] > player.getCurrentMp()*0.8` даёт `WAIT_GROUP_MP`. Предшествующие вычисления зависят от exact learned loadout, target champion/native stats, контекста Party и выбранной tactics policy. **Не переносить её как независимую TEST формулу.**

`PhantomPartyTactics` осуществляет поддерживающий cast/maintenance; `PhantomSupportEffectAuthority` устанавливает реальный buff status. До014 PASS9/9 read-only support fixture; fighter/mage/hybrid optimal buff selection = UNKNOWN; production не менять.

`PhantomAutonomousTrainingNative.planCells` строит меню по реальным sources/learned abilities, повторяет candidates, затем `admitHeadlessCell` делает законные A/B; перед actual native CUT полные checks. HEAD013/014 хранит cooperative stop, sealed proofs. Их bypass недопустим.

## Точная граница знания
3060 vs2244.8 или 3060 vs2244.8 **не означает** неизменяемой невозможности для других loadout/NPC/состояний. Максимальный MP может зависеть от неучтённых баффов/эффектов; произведение count*cost — от native прогнозов. Preview может отвечать `UNKNOWN`, но не `PROVEN_IMPOSSIBLE` на основании простой эвристики. Результат старого Party WAIT — **факт для прежних конкретных условий**, не whitelist всей профессии.

## Версионирование и исторические данные
`feature/phantom-world` `9a4495b9dbe24f53cfe7357d43be801fd2067512`, parent `1f9d2f1a54418a1a3a601bf2eebcf67e9f14f7ec`. 014 manual принят владельцем как PARTIAL в переписке; возможная документация manual gate в репо может быть исторической. SOURCE004/TEST006 verify before mutation. Latest SQL183 rows SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`; confirmed accepted2/DRAFT14/Adam903/223/apps48/grad16/pointer12. Preserve bytes/proofs.
