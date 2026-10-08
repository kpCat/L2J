# M1 gate checklist (заполнять только evidence)
| Gate | TASK027 база | TASK028 | Evidence/provenance |
|---|---|---|---|
| native materialization/contact | достигнуто | NOT_RUN | |
| farm core и вся logical cohort | full scenes не пройдены | NOT_RUN | |
| local movement/stuck/resource recovery | partial | NOT_RUN | |
| native death/return/new cycles |450/45916/27cycles, scoped PASS | REUSE_UNVERIFIED | |
| cold DEAD/pending handoff | native composed PASS | REUSE_UNVERIFIED | |
| callbacks/drain/store | scoped PASS; old24354UNKNOWN | REUSE_UNVERIFIED | |
| full-group native→SQL | c/d/g8/8; ограничения restart832 | NOT_RUN | |
| sameDB restart/crash |027exact boundaries; lineage caveats | REUSE_UNVERIFIED | |
| soft away/background/return/remat | UNCERTAIN, noPASS | NOT_RUN | |
| final real client | NOT_RUN | NOT_RUN | |

TASK028 не должен запускать M2 и не обязан выдумывать final client evidence ночью.
Если все серверные строки имеют подтверждение, итог WAITING_FINAL_CLIENT, иначе OPEN.
