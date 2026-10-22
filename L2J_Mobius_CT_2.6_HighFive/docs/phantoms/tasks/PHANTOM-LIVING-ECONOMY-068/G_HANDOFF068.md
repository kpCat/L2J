# G068 — личный native Rift

22.10.2026: **focused/native G=PASS** на рабочем dev JAR; committed-source delivery и финальный общий smoke принадлежат основному исполнителю068. Клиентская QA и настоящий сетевой logout не запускались. Статусы всей кампании этот файл не меняет.

## Поведение и scope

Независимый `EnablePersonalSoloRift=False` по умолчанию. Допуск переиспользует master, exact allowlist, exact access0 и существующие исключения Phantom/Background/headless. Льгота относится только к отсутствующей/одиночной Party. Общие min-party, native leader, fragments, capacity, waiting-room, jump limit и ordinary Party сохранены. Kamaloka/Labyrinth067 не переписаны.

Roster/pending transaction повторно проверяются до native оплаты. Внутренняя настоящая singleton Party создаётся после оплаты; при ошибке возвращаются только фактически списанные fragments. Cleanup распускает только собственную неизменную singleton Party, сохраняя выросшую/чужую группу.

Один native lifecycle monitor сериализует admission, invitation invite/accept/terminal, изменения Party и Rift timers. Комнаты имеют owner token; release включает unspawn и отвергает чужое/старое освобождение. Terminal idempotent; поколения и identity таймеров запрещают поздний spawn/jump. Native Spawn комнаты также проверяет session owner и принадлежность NPC: отложенный respawn прошлого владельца не оживляет NPC после переиспользования комнаты. Publication очереди native respawn сериализована с cancellation pass. Новый узкий `RespawnTaskManager.cancel(Npc)` удаляет только ещё не захваченный queued entry и его scheduled count; dispatch использует atomic conditional remove, поэтому cancel и dispatch не выигрывают один entry дважды. Уже захваченный callback остаётся owner-fenced и считает свой count сам. Local analog — `DecayTaskManager.cancel`. Групповой room jump переносит и dead bodies при наличии живого участника; участники после restart-point больше не входят в будущие jumps.

## Exact changed paths

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`. Связанное расширение более10 файлов разрешено AUTHORITY068.

- `java/org/l2jmobius/gameserver/managers/DimensionalRiftManager.java`
- `java/org/l2jmobius/gameserver/model/sevensigns/DimensionalRift.java`
- `java/org/l2jmobius/gameserver/model/sevensigns/DimensionalRiftRoom.java`
- `java/org/l2jmobius/gameserver/taskmanagers/RespawnTaskManager.java`
- `java/org/l2jmobius/gameserver/model/groups/Party.java`
- `java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestRestartPoint.java`
- `java/org/l2jmobius/gameserver/config/custom/PersonalCharacterQoLConfig.java`
- `java/org/l2jmobius/gameserver/qol/PersonalCharacterQoLService.java`
- `dist/game/config/Custom/PersonalCharacterQoL.ini`
- `test/java/org/l2jmobius/gameserver/qol/QoLRift068Checks.java`
- `test/java/org/l2jmobius/gameserver/qol/QoLRift068Native.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRift068Native.java`
- `test/java/org/l2jmobius/gameserver/qol/QoLSevenSignsAccessSuite.java`: necessary owner fixture и assertions вместо старого запрета любого personal Rift seam.
- Этот `G_HANDOFF068.md`.

`Player.java` изменил основной исполнитель: doDie вызывает `memberDead`, doRevive — `memberRessurected`, оба с captured Party/Rift под тем же monitor. Блок G не правил Player/GameServer/Shutdown/build.xml/PhantomTestLauncher и не менял общий Spawn.java. Native bypass `handlers/bypass/npc/Rift.java` прочитан, изменения не понадобились.

## Команды и доказательства

Команды выполнялись последовательно в выданном основном исполнителем Ant/native слоте. Ниже `repo` — фактический root, `module=repo/L2J_Mobius_CT_2.6_HighFive`, `task=repo/.phantom-local/task068`; classpath состоит из `task/build-dev/phantom-test/bin;task/build-dev/dist/libs/*;module/dist/libs/*` с абсолютными путями.

1. `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml -Dbuild=<task>/build-dev jar compile-tests` — **exit0,50s**; `compile-dev-rift-queue-proof.log`. Два прежних warning об obsolete `System.runFinalization` вне G. Предшествующие корректные dev snapshots: `compile-dev-rift-final.log`, `compile-dev-rift-queue-final.log`.
2. `java -cp <classpath> org.l2jmobius.gameserver.qol.QoLRift068Checks` — **exit0**, `reports/rift-focused-queue-final.log`: два DB-free focused cases. Два конкурентных acquire дают ровно одного owner; foreign/stale release отвергается; независимый child switch fail-closed сохраняет Kamaloka switch.
3. Из `<task>/native-test/game`: `java -Dphantom.task068.native=true -Dphantom.module.root=<module> -Dphantom.test.config=<task>/native-test/game/config/Database.ini -cp <classpath> org.l2jmobius.tests.phantoms.PhantomRift068Native` — **exit0**, `reports/rift-queued-respawn-green.log`. После сборки helper был узко перекомпилирован `javac` для дополнительного assertion обычного native respawn текущего владельца; product JAR не изменялся.

Native evidence: `OWN068_RIFT_SOLO`, `OWN068_RIFT_TERMINAL`, `OWN068_RIFT_ADMISSION`, `OWN068_RIFT_PARTY2`, `OWN068_RIFT_NATIVE_PASS`. Own schema `l2jmobiush5_phantom_test_068`, account `task068rift`, access0 actors `Rift068Owner`/`Rift068Peer`. Fixture invulnerability установлена до сцены только для lifecycle checks; combat/reward и сетевые packets не заявлены. Обычные production policy guards не ослаблялись.

Проверены native fee ровно один раз, manual room advance/max, spawn/exit, invalid type, missing fee/no singleton, nonallowlisted/Phantom/Background отказ, pending transaction/no charge, replaced/terminal timer actions, double kill, реальная native очередь respawn `scheduledCount>0`→terminal cancellation→`0`, old native respawn после нового owner запрещён, native respawn текущего владельца работает, solo death→teleport ACK→настоящий restart-point path→ACK→reentry, native leaveParty logout seam, grown singleton сохранён, Party2 nonleader/оба fees/dead body travel/in-room revive/exit/roster сохранён. Последовательный pending-transaction check использует штатные transaction fields; сетевой invitation/accept race не имитировался клиентом, атомарность acceptance→join обеспечена общим monitor и проверена чтением кода.

Финальный проверенный dev `GameServer.jar` SHA256: `BF06707259EB0ADEACC3E7FD9EF4D0216B1DEF98D290E75C308CF54D27DAD5FD`. Это не final committed-source SHA всей068. После запуска ThreadPool и Hikari закрыты штатно, JVM завершилась exit0. Последующие native/Ant слоты возвращены основному исполнителю.

## Честные ограничения и попытки

- У stock Manager отсутствуют самостоятельные player-level/reuse predicates: для них `NOT_APPLICABLE_NATIVE_GUARD`, новые ограничения не выдуманы.
- Первый native запуск: `reports/rift-native.log`, exit1 до сцены — restricted token не мог создать helper policy в user-only private dir. Fixture переведён на существующий immutable settings seam, ACL не менялись.
- Второй: `reports/rift-native-r2.log`, exit1 после SOLO PASS — harness вызывал revive до ACK native death-return teleport, поэтому следующий вход оставался dead. Исправлен порядок ACK→revive. Узкий `javac -encoding UTF-8 -cp <classpath> -d <testbin> <QoLRift068Native.java>` оба раза exit0, product JAR не подменялся.
- Третий: `reports/rift-native-r3.log`, exit0. Последующее self-review обнаружило ordinary dead-body travel regression; минимальный production fix и соответствующий Party2 regression проверены отдельной финальной сборкой/сценой выше.
- Feature-first compile absence API не выдаётся за product RED. Для очереди выполнен настоящий **RED→GREEN**: `reports/rift-queued-respawn-red.log`, exit1 — после terminal ожидался scheduled count0, фактически1; затем тот же production route плюс cancellation и обычный respawn текущего owner — `reports/rift-queued-respawn-green.log`, exit0. Проверки нового owner/config контракта GREEN; исходный067 timer/room lifecycle отдельно как RED не запускался.
- Независимого reviewer в G не было: выполнен self-review owner/fee/Party/terminal/callback/lock-order и совместимости corpse travel. Итоговый интеграционный review остаётся основному исполнителю.
- Mojibake-маркеры в изменённых файлах проверены отдельно, совпадений нет.
- Escaped Cyrillic/XML escaped Cyrillic в изменённых файлах проверены отдельно, совпадений нет.
- Git-команды исполнителем G не использовались; commit/push и финальный `diff --check` выполняет основной исполнитель по AUTHORITY068. Старые057–067 runtime/БД/клиент не использовались и не изменялись.

## Committed integration receipt

Root повторил native SOLO/TERMINAL/ADMISSION/PARTY2 на source b2803e3093e94f2e4b1f32819d7cddb57a044242 / Game8FE9B3046BBB677390571F1A1D2E3D54ABF05DB2C5389DFE108274FFE1EC9EE2: rift-native-committed-v3.log PASS без isolated production override. Final code935b334fe78e4548295354189eafe5144023d38f / Game828325E0311AE8DA74BF4480F58D41452BFCF92349191D003957FDA4011E5B75 имеет неизменные G class bytes по последовательным comparisons v3→v4→v5→v6→v7→v8→v9. Прежний dev hash не является финальным binding. Native leaveParty seam проверен; настоящий network logout/invitation race и полный бой всех комнат остаются NOT_RUN. Personal switches OFF/allowlists пустые, глобальный min-party bypass отсутствует. По финальной полной матрице G=PARTIAL_LOGOUT_PROOF, без отмены bounded native PASS.
