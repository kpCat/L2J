# Read-first аудит координатора

Проверенный remote HEAD `6ebe1d93f4ee168cd8952f0416dc920f26c430ac`; parent `41692152ca6944176be8014c307702b7d2a0a6bc`.
Источники перечислены в REFERENCES.md. Никакой полной build/run на машине
координатора не было: native outcomes здесь — published evidence Codex025.

## Установленные результаты TASK025
1. Cold retained452 resolved production-путём; actual second restart exact.
2. Final SEALED→SQL обеих групп8/8; actual group-d restart1/2 оба8/8.
3. Natural farm A0/8 и B2/8. Это НЕ «все боты не убивали»: B872 имеет54 cycles,
   54kills, +4494EXP/+516SP, tail120Rewards11, maxIdle24.26s; его индивидуальный PASS
   не переносится на группу.
4. D876: owner OPEN, checkpoint COMPLETED/RESUME, incidents пусты, 5cycles и7kills,
   maxIdle197.5s, tail0, travel.journey_deadline + LOCAL_FARM_UNAVAILABLE.
   Target rejections exact5 и0dead/invul/noLos/noForwardPath в последнем snapshot.
   Отдельные снимки не атомарны: это вход к воспроизведению, не доказательство
   безопасного обхода маршрута.
5. B450: AutoPlay/AutoUse продолжают тикать, owner OPEN, lowMP17.11/181,
   13cycles, maxIdle177s, REGEN/PHASE_DEADLINE; в конце cast-launch RESERVED ~2.36s.
   Последняя запись не доказывает зависший cast: это может быть новый нормальный каст.
6. E callback ON_ATTACKABLE_KILL был RESERVED при drain timeout. Producer UNKNOWN.
7. N02/S12: candidate native2/3 против base3/3; secondary получает EXP, killSequence0.
   Producer UNKNOWN. В N02 используется second.callSkill напрямую; это проверяемое
   отличие от original doCast, но оно не объясняет candidate-vs-base само по себе.
8. Crash872: receiptIntegrity/finalized true; post-recovery SELECT показывает новые XYZ
   при state version уже5817 вместо prepared5803. Сам resolver-writer ещё не пойман.
   Нельзя объявлять DATA_LOSS либо автоматически считать поздние координаты нормальными.

## Проверенные source constraints
- VisibleFarmTravel требует exact getHeight(x,y,z)==z и позже walk(...,radius=0).
  Terminal attempt проверяется до оценки текущей usable position.
- VisibleAutoPlay.noTargetExpired проверяет полезный прогресс, но НЕ имеет отдельного
  stateful resource policy. Через30s делает abortAttack/abortCast/stopMove, через90s
  отдаёт stalled; после overflow nativeProgress возвращает null. Это не доказанный
  producer450, но реальный конфликт между generic stall repair и возможным законным
  восстановлением MP, который надо проверить.
- Stock AutoPlay для mage не делает auto-hit и отдаёт offensive skills AutoUse.
- NativeEventWork регистрирует EARNED EVENT через PlayerNativeWork; сам dispatcher
  не надо переписывать. RESERVED!=RUNNING: timeout не разрешает дописать complete().
- Наблюдатель025 зависим от корректных producer-level evidence. «Исправить N02»
  ослаблением assert/ростом deadline запрещено.

## Решение
Три доказательных вертикали внутри одного пакета:
F — actual local opportunity/resource plan → native execution → повторяемый progress;
E — truthful evidence и exact published callback completion;
R — preservation/regression и observation на границе recovery до ordinary writers.
Не трогать заново решённые cold recovery/owned-store архитектурные гарантии.
