# REVIEW FINDING — Goal 010B

A topology profile registered in both scheduler and topology, but with no
perception events, receives STALE for all absent-source withdrawals.

Because Goal 010B accepts STALE only from `INACTIVE_CONFIRMED`, the profile
enters permanent cleanup-pending and valid reload is rejected. The fake focused
port defaults withdrawals to ACCEPTED and misses this real-adapter behavior.
