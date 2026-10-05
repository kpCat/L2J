# ACCEPTANCE

Task014 GREEN = DIAGNOSIS GREEN, not M1 CLOSED.

Required:
- exact GOOD `561c84...` backed by archived connected evidence;
- current BAD source/evidence backed;
- complete critical-path codemap;
- GOOD..BAD critical regression matrix;
- recorder observation-only and off by default;
- recorder targeted tests GREEN;
- jar GREEN;
- one actual TestAdmin observe014 causal trace;
- FIRST_LOST_EDGE named from trace, not guess;
- <=12 suspect commit shortlist tied to that edge;
- bounded JFR text summary;
- no semantic gameplay/materialization fix;
- user logout/save verified before graceful stop.

If trace cannot identify first lost edge:
`BLOCKED_TRACE_INSUFFICIENT`.
Do not infer.
M1=OPEN.
