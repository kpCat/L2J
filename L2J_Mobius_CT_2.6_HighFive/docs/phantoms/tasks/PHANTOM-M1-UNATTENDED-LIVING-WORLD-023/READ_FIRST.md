# Независимый read-first: что подтверждено, а что нет

Зафиксирован HEAD fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6. Его parent/source:
f6b94ff2ea89bbb464f6f6372701dd19f70cf1e2. Connected022a исполнял более ранний aff089ed…;
финальная правка022 в настоящем runtime пока не проверена. Не запускать старую сборку.

1. RESULT/ROOT_CAUSE_PROOF022: actual first failure NATIVE_EARNED_RECIPIENT_NOT_CAPTURED
в MagicUseTask → callSkill; затем owner DRAINING → unregister → stale seq7/CAST.
Причинность отказа подтверждена, но конкретный поздний aggro participant не снят.
2. Последний PlayerNativeWork.captureCombatRecipients исключает damage<=1, но
requireCapturedEarnedParticipants по-прежнему отвергает КАЖДОГО нового managed участника,
если в ambient EARNED context нет его ticket. Это недостаточно для обычного совместного боя.
3. Старый PhantomM1DynamicRecipientChecks.run для managed OPEN и SEALED ожидает failure
после позднего native party/transfer change. Он проверяет запрещающий контракт, а не
способность OPEN-персонажей законно завершить изменившийся бой. TASK023 явно уточняет
этот контракт (DESIGN.md), сохраняя отрицательные проверки stale/sealed/foreign.
4. Attackable.calculateRewards использует stock damage>1, range/death, party и native
addExpAndSp. Не рассчитывать XP/loot в новом Phantom-коде.
5. MagicUseTask.run просто вызывает phase1/2/3; отсутствие phase3 после exception не
равно зависшему executor. Не лечить новым глобальным ThreadPool/watchdog.
6. LocalPlaySyntheticHumanSession уже существует: Player.load, lease LOCALPLAY_TEST_HUMAN,
GameClient=null, no headless outbound, native online/spawn, execute через PilotActions.
Close восстанавливает origin/vitals самого observer и штатно сохраняет/удаляет его.
7. LocalPlaySyntheticHumanService уже имеет private mailbox/ACL/PID+incarnation guard,
5 START на JVM, deadline525s, heartbeat30s, max400 команд. Их НЕ увеличивать ради ночи.
8. Start-LocalPlaySynthetic.ps1 и Invoke-LocalPlayPilot.ps1 -ActorMode Synthetic уже есть.
LocalPlayPilotConfig: EnableLocalPlaySyntheticHuman, LocalPlaySyntheticCharacterObjectId,
LocalPlaySyntheticCharacterName. Night runner обязан явно выбрать Synthetic.
9. PersonalCharacterQoL.ini AllowedAccounts выдаёт личные QoL разрешённым REAL игрокам.
Это не права observer/Pilot, не сигнал физического присутствия и не новая identity.
Не связывать включение мира с этим allowlist и не менять этот файл для TASK023.
10. Final022: dynamic current1/7 vs base2/7 НЕ GREEN. Secondary3/5/base3/5, loot8/9,
GK6/7, travel0/1, self-drain22/23 — раскрытые долги. Проблемы relevant к M1 не списывать
«старые», а проверить в нормальных воспроизводимых fixtures в пределах scope.
11. TEST5cycles не равны естественной группе: prior golden fixture отключает NPC AI и
подаёт decision work тестовым driver. Natural acceptance только на полном сервере со
штатным scheduler, живыми spawned NPC с включённым AI и synthetic locality.

Координатор не запускал Windows сервер и не доказал, что новый final022 уже устойчив.
Вложенный proposal-код проверен отдельно, не является production/native evidence.
