# PLAN EXECUTION — Goal 008

Plans contain 1..32 immutable typed steps and no executable callbacks. One
handler invocation maximum per scheduler work item. Result categories:
SUCCESS, RETRY, REPLAN, COMPLETE_GOAL, FAIL_GOAL, CANCELLED.

Generation is checked before and after handler invocation. Goal replacement,
detach, activity-generation change and stop cancel stale results cooperatively.
No thread interruption, executor, per-profile future or task.
