# Goal 030C2B — native karma recovery strategy

## Status

`SUCCESS`

- Goal030C2A: `ACCEPT`.
- Goal030C2B: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal030: `IN_PROGRESS`.
- CP2: `NOT_STARTED_AFTER_HUMANIZATION`.

## Summary

Добавлен консервативный overlay восстановления кармы поверх штатного Goal025 PvP. Красный Phantom не начинает новые `FARMING_ESCALATION`/`REVENGE` вне clan war, но сохраняет party defense и штатную защиту при небезопасных условиях. При реальной атаке безопасный solo Phantom с закрытым EXP debt и нулевым риском item drop возвращает `YIELD`: PvP service не начинает combat, retreat, warning/help и не вызывает смерть. Внешний атакующий и native `Player.doDie` остаются единственными владельцами смерти и уменьшения кармы.

XP gain не объявляется способом прямого уменьшения кармы: в текущем `Player` такого перехода нет. Обычный gameplay нужен только для восстановления `currentExp` до canonical `expBeforeDeath`; пока `expBeforeDeath - currentExp > 0`, повторный намеренный `YIELD` запрещён.

## Native anchors

Точечно подтверждены ранее зафиксированные TASK anchors без широкого karma-аудита:

- `Player.doDie`: при `karma > 0` выполняет `karma < 200 ? 0 : karma - karma / 4`.
- `Player.getExpBeforeDeath()` доступен, watermark сохраняется штатным Player persistence.
- `onDieDropItem`: exposure начинается при `karma > 0` и `pkKills >= PvpConfig.KARMA_PK_LIMIT`, затем использует `RatesConfig.KARMA_RATE_DROP`, `KARMA_RATE_DROP_EQUIP`, `KARMA_RATE_DROP_EQUIP_WEAPON`, `KARMA_RATE_DROP_ITEM`, `KARMA_DROP_LIMIT`.
- Production Phantom-код не вызывает `Player.doDie`, `Player.setKarma`, прямую EXP/inventory mutation или teleport-to-guard.

## Policy и context

Strict content-addressed policy: `dist/game/data/phantoms/pvp/high-five-karma-recovery-v1.xml`, максимум 16 KiB, XXE отключён, unknown/malformed/out-of-range input отклоняется, runtime reload отсутствует.

Настройки: `suppressProactiveNonWar=true`, `yieldToActualAttack=true`, `allowYieldInClanWar=false`, `allowYieldWhileInParty=false`, `requireExperienceRecovered=true`, `maxIntentionalDeathDropRiskBasisPoints=0`.

`L2jPhantomKarmaRecoveryContext` выполняет O(1) resolution: exact `PhantomMaterializationService.find(profileId)` → exact `World.getPlayer(characterObjectId)` → exact counterpart `World.getPlayer(objectId)` → bilateral `ClanWarService.currentWar`. Нет DB query, World/ClanTable scan, cache, worker или timer.

Snapshot содержит `karma`, `pkKills`, `currentExp`, `expBeforeDeath`, `expDebt`, `deathDropRiskBasisPoints`, predicted native karma, party и active clan-war truth. Примеры native suite:

- до смерти: karma `800`, predicted `600`, EXP debt `0`, safe actual attack → `YIELD`;
- после native death: karma `600`, `expBeforeDeath=835862`, `currentExp=821533`, debt `14329` → `NORMAL/XP_DEBT`;
- karma `100` после native death → `0`, overlay немедленно возвращает clean `NORMAL`.

## Decision evidence

| Условие | Результат |
|---|---|
| unavailable или karma=0 | `NORMAL` |
| red `FARMING_ESCALATION`/`REVENGE`, нет clan war | `SUPPRESS_PROACTIVE` |
| proactive в active clan war | `NORMAL` |
| `PARTY_DEFENSE` | `NORMAL` |
| safe solo `ACTUAL_ATTACK`, debt=0, drop risk допустим | `YIELD` |
| EXP debt | `NORMAL/XP_DEBT` |
| party, clan war или drop exposure | `NORMAL/UNSAFE` |

`SUPPRESS_PROACTIVE` переводит существующий encounter в штатный bounded `COOLDOWN` с reason `pvp.karma_recovery.proactive_suppressed`, без warning/engage/social combat claim. `YIELD` оставляет существующий stage без нового schema/stage и считает каждый yielded pulse отдельным bounded aggregate metric event.

Active `ENGAGE` обрабатывается до recovery observation. Overlay не вызывает `PhantomCombatService.cancel`; действующий Goal025 combat не прерывается, стратегия применяется к следующему encounter.

## Native drop configuration

Guarded JVM загрузила canonical config: `KARMA_PK_LIMIT=6`, `KARMA_DROP_LIMIT=10`, rates `drop=40`, `equip=40`, `weapon=10`, `item=50`. Консервативный indicator равен `max(rate) * 100 = 5000 bp`; при policy maximum `0 bp` добровольная смерть запрещена.

## Metrics

`PhantomPvpService.ServiceSnapshot` расширен bounded `LongAdder` counters: observed red recovery snapshots, suppressed proactive encounters, yielded pulses, unsafe blocks и EXP-debt blocks. Для deduplication не добавлен per-pair set.

## Changed files

1. `java/org/l2jmobius/gameserver/phantoms/pvp/PhantomKarmaRecoveryPolicy.java`
2. `java/org/l2jmobius/gameserver/phantoms/pvp/PhantomKarmaRecoveryContextPort.java`
3. `java/org/l2jmobius/gameserver/phantoms/pvp/L2jPhantomKarmaRecoveryContext.java`
4. `java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java`
5. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
6. `dist/game/data/phantoms/pvp/high-five-karma-recovery-v1.xml`
7. `test/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomKarmaRecoveryPolicyGoal030C2BSuite.java`
8. `test/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomKarmaRecoveryNativeGoal030C2BSuite.java`
9. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
10. `build.xml`
11. `docs/phantoms/reports/030c2b-native-karma-recovery-strategy.md`

Bounded exception к обычному лимиту 8–10 файлов: один неделимый task package явно требует production policy/context/integration/XML, две focused suites, build/launcher и обязательный report. Другие artifact families и хроники не затронуты.

## DB, lifecycle и performance

DB schema/migrations отсутствуют. Native suite использует только guarded `l2jmobiush5_phantom_test`, provisioning не выполняется; cleanup ограничен owned fixture account/characters. Production hot path делает только bounded exact lookups и aggregate increments; нет DB, scans, cache, inventory pricing, allocations больших коллекций, worker/timer или per-Phantom scheduling.

## Commands and results

- Initial exact parent/status: branch `feature/phantom-world`, upstream `origin/feature/phantom-world`, HEAD `b1907677d7d37825eef9e75fe551d3370ab17a1f`; пользовательские untracked task packages оставлены read-only.
- `ant compile-tests`: launcher failure до Ant (`ant` отсутствует в PATH), compile не начинался.
- Apache Ant 1.10.15 загружен в локальный Maven cache; первый Maven invocation был отвергнут из-за PowerShell parsing unquoted `-Dartifact`, quoted retry успешен.
- Единственный фактический diagnostic `compile-tests`: `BUILD SUCCESSFUL`, 20 s, 2218 production + 120 test sources, два старых `System.runFinalization` warning.
- Новый policy target, первая попытка: 10/10, `BUILD SUCCESSFUL`, 18 s.
- Новый native target, первая попытка: 5/5, `BUILD SUCCESSFUL`, 33 s; `800→600`, `100→0`, EXP debt `14329`, drop risk `5000 bp`.
- Final exact gates: policy 10/10 (18 s) → native 5/5 (29 s) → Goal025 policy 2/2 (18 s) → admission 2/2 (17 s) → warning-social 1/1 (19 s), все `BUILD SUCCESSFUL`.
- Final jar: ровно один `jar`, `BUILD SUCCESSFUL`, 17 s; GameServer/LoginServer jars собраны и скопированы в рабочий `dist/libs`.

## Process counts

- pre-edit targeted `rg/find`: 3 команды (третья дала ожидаемый Windows glob diagnostic, но вернула build anchors);
- `apply_patch`: 1 invocation, ACL reject до чтения/мутации, applied changes `0`; retry не выполнялся;
- atomic UTF-8-no-BOM fallback: использован после обязательного ACL reject;
- фактический diagnostic compile/compile-tests cycle: 1;
- новые targets до final sequence: policy 1 run, native 1 run;
- speculative old reruns: 0;
- compaction: 0 observed;
- token usage: будет приведён из финального `/goal` результата, если runtime его возвращает.

## Deviations, limitations, risks

- Ant отсутствовал в PATH; gates запускаются тем же Ant 1.10.15 через `org.apache.tools.ant.launch.Launcher` из локального Maven cache.
- Active-combat no-cancel suite проверяет точный production ordering и отсутствие `_combat.cancel` в overlay block; native combat ownership не мутируется.
- Forced item-loss/gear stripping/valuation намеренно отсутствуют; при exposure >0 используется fail-closed `NORMAL/UNSAFE`.

## Branch, commit, push

- Branch: `feature/phantom-world`.
- Commit: atomic containing commit; exact SHA приводится в финальном handoff после создания commit.
- Push: обязательный post-commit push в `origin/feature/phantom-world`; точный результат приводится в финальном handoff.

## Next step

Независимый review Goal030C2B. После `ACCEPT` — регенерировать Goal030 CP2 на C2B commit; затем CP3 final release/restart/rollback. Новый humanization micro-goal внутри C2B не начинать.
