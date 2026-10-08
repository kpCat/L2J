# Acceptance028

GREEN028 допускается только при всех:
- TRANSPORT_CONTROL_PASS: completed request/identity/heartbeat/TTL tests, без повторной
  отправки uncertain native actions. Не просто игнорирование прежнего UNCERTAIN.
- SCENE_A/B_COMPLETE: две независимые natural scenes360–420с, один frozenSHA.
- FARM_CORE_PASS и CONTINUITY_V2_PASS по ACCEPTANCE_CHANGE: реальные циклы и accountable
  группа; legacy scores представлены отдельно без изменения их значения.
- SOFT_RETURN_PASS: подтверждены native away, отсутствие demand/hard holds после grace,
  owned FINALIZE/absence, один законный background step, return/new demand,
  rematerialization и новые cycles. Никакого ручного dematerialize/spawn ботов.
- WHOLE_GROUP_SAVE_PASS: latest terminal receipts ВСЕХ enrolled incarnations сопоставлены
  с exact SQL; lifecycle переходы расписаны отдельно. Два actual sameDB restart.
- 027 lifecycle11/11 + 18 regression routes и relevant LocalPlay tests на final code.
  Никакого «было зелёным перед последней semantic правкой».
- healthy shutdown COMPLETE до pools; retained0/pending0 на final stop; own JVM STOPPED.
- в финале нет unclassified native incident или safety-regression в затронутом пути.

Перепроверить benchmark production027 по неизменности semantics/affected tests.
Не воспроизводить все две crash lanes заново, если persistence/native ownership код
не изменялся; reused027 proof обозначить BASE027_PROOF + exact source dependency hashes.
Это не новый finalSHA crash PASS. При новых зависимостях/изменениях — gate OPEN.

TASK_RESULT=GREEN означает CONTINUITY028, а не автоматический M1_CLOSED.
M1=WAITING_FINAL_CLIENT только если MILESTONE_STATUS перечисляет все server gates как
проверенные либо обоснованно переиспользованные и нет открытых safety/behavior gaps.
Иначе M1=OPEN. REAL_CLIENT_FINAL=NOT_RUN, пока пользователь не прошёл клиентскую приёмку.
На final fail не затирать промежуточные улучшения: перечислить точную оставшуюся границу,
а не опять только «0/8» без причин.
