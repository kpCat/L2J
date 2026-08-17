# Goal 026 CP4 — prior independent review

Accepted baseline: `88b7c031847c71abd4077423336caaa6bd179712`

Independent status:
- Goal025/025A: ACCEPT
- Goal026 CP1+026A: ACCEPT
- Goal026 CP2: ACCEPT
- Goal026 CP3+026B: ACCEPT
- Goal026 overall: IN_PROGRESS
- CP4: AUTHORIZED
- CP5+: NOT_STARTED

Goal026B independently closed R026B-01 on remote `88b7c031847c71abd4077423336caaa6bd179712`:
candidate assessment now extracts the exact own PartySnapshot from a large
CommandChannel, keeps <=9 Party evidence, typed-rejects large-CC leader/nonleader
and fails ambiguous membership closed. Standalone CP3 scoring is unchanged.

TASK SIZING DECISION:
CP4 is intentionally a LARGE substantive vertical slice. Do not split consent,
assembly, staging, gathering and Decision wiring into new microtasks. A suffix
corrective is created only after a demonstrated independent-review finding.
First automatic context compaction remains a STOP signal.
