# GOOD -> BAD REGRESSION MATRIX

Exact GOOD: `561c84a2dc23d6dd953e755e2fafcfbddcd5d395`.
Exact BAD source: `c23915df10239bfab15ae49276e14833268b9afc`.
Coordinator compare: 73 commits.

Create `REGRESSION_MATRIX.tsv` for every commit in this range that touches the M1 critical path.

Columns:
`ordinal,sha,date,subject,critical_area,files,semantic_change,could_break_visible_presence,could_block_before_materialize,could_block_after_materialize,confidence,evidence`

Areas:
REAL_IDENTITY
LOCALITY
PRESENCE
ECOLOGY
SCHEDULER
READINESS
MATERIALIZATION
BACKGROUND_ATTESTATION
WORLD_SPAWN
VISIBLE_AUTOPLAY
NATIVE_WORK
STORE_DEMAT
TOPOLOGY

Do not infer causality from file-touch alone.

After live trace names FIRST_LOST_EDGE:
- produce <=12 suspect SHAs that touch that exact edge or its direct prerequisite;
- if future behavioral bisect is warranted, suggest 5-7 checkpoint SHAs only.

Do NOT launch an automatic 73-commit runtime bisect in this task.
