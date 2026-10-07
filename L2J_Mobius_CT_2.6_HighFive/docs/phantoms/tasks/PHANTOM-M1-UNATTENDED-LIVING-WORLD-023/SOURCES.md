# Pinned source provenance

Все ссылки ниже относительны к:
https://github.com/kpCat/L2J/blob/fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6/L2J_Mobius_CT_2.6_HighFive/

java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java
  capture/captureCombatRecipients/requireCapturedEarnedParticipants/reserve/ParticipantWork.
java/org/l2jmobius/gameserver/model/actor/Attackable.java
  calculateRewards, damage>1, native drops/addExpAndSp/party distribution.
java/org/l2jmobius/gameserver/model/actor/tasks/creature/MagicUseTask.java
  run switch phase1/2/3; no exception-finalizer logic in current method.
test/java/org/l2jmobius/gameserver/phantoms/player/PhantomM1DynamicRecipientChecks.java
  run; managed OPEN/SEALED grouped in old negative expectations.
java/org/l2jmobius/gameserver/localplay/LocalPlaySyntheticHumanSession.java
  requireFree/start/valid/execute/close.
java/org/l2jmobius/gameserver/localplay/LocalPlaySyntheticHumanService.java
  start/poll/process/close;525s/5runs/400requests/30s heartbeat.
java/org/l2jmobius/gameserver/localplay/LocalPlayPilotService.java
  startConfigured / _synthetic; real lease separate.
java/org/l2jmobius/gameserver/config/custom/LocalPlayPilotConfig.java
  isSyntheticEnabled/syntheticObjectId/syntheticName, autoattach character allowlist.
tools/phantom-local-play/Start-LocalPlaySynthetic.ps1
  Start RunId, private context, fresh control incarnation.
tools/phantom-local-play/Invoke-LocalPlayPilot.ps1
  ActorMode Synthetic, RunId, heartbeat/sequence/results.
dist/game/config/Custom/PersonalCharacterQoL.ini
  AllowedAccounts is REAL personal QoL permissions, not locality/Pilot.
docs/phantoms/tasks/PHANTOM-M1-NATIVE-FARM-CONTINUATION-022/
  RESULT.md, ROOT_CAUSE_PROOF.md, EVIDENCE022a.json, ROUND3-REGRESSIONS.tsv,
  PATCH_LEDGER.tsv, ENGINEERING_REVIEW.md, PUBLICATION022b.json.

Source-derived facts и proposed architectural changes явно разделены в READ_FIRST/DESIGN.
