# JFR

For observe014 only, GameServer may use JDK25 Java Flight Recorder.

Bound:
- settings=profile
- maxsize <=128MB
- maxage <=20m
- dumponexit=true
- `.jfr` under private `.phantom-local/observe014/`

Do not commit JFR.

Publish text summary only:
- duration/size;
- thread count;
- exception count/top classes;
- monitor/blocking facts relevant to Phantom scheduler/locality/materialization;
- CPU/thread summary;
- long park/block overlapping the causal trace interval.

JFR supplements, never replaces, the logical causal trace.
