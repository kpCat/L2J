# Goal034 closure 3 acceptance

PASS только если sandbox до spawn содержит полный canonical `data` snapshot и required startup resources; working canonical data unchanged; verify/jar green; LoginServer READY; GameServer gen1 полностью READY/registered; Phantom managed=10/ACTIVE=5; native graceful restart+drain; GameServer gen2 READY с теми же identities/ecology и без duplicates; exact cleanup/no orphans; production DB unused; Goal034 status=SUCCESS; report/commit/non-force push завершены.
