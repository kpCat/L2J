# Failure matrix — Goal 021 Checkpoint 2

| Boundary | Failure | Required result |
|---|---|---|
| manor static/runtime | seed fact mismatch | authority stale |
| manor config | disabled | method ineligible |
| manor area | wrong castle | source ineligible |
| requirements | no seed/harvester | typed blocked |
| active target | dead/raid/chest/wrong NPC/instance | target unavailable |
| sow dispatch | uncertain side effect | reconcile, no blind seed use |
| sow | failed | exact seed loss, bounded retry |
| combat | foreign/missing session | accepted Checkpoint 1 recovery rules |
| harvest | wrong seeder/not seeded | fail closed |
| harvest dispatch | uncertain | reconcile crop/cast/corpse evidence |
| manor background | seed count changed | rollback all |
| quest catalog | source hash drift | rule stale |
| quest state | absent/not STARTED/wrong cond | source ineligible |
| quest callback | deadline with no item | one source failure |
| quest active | script changes state unexpectedly | fail closed |
| quest background | quest rows drift | rollback all |
| quest rule | hidden side effect found | reject rule/startup |
| transition | active uncertain ownership | background blocked |
| capacity | item weight/slot/cap | typed blocked, no partial grant |
| shutdown | in-flight boundary | drain or durable uncertainty |
