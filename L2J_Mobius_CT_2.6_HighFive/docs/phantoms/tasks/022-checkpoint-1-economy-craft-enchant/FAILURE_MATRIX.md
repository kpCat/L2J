# Failure matrix — Goal 022 Checkpoint 1

| Boundary | Failure | Required result |
|---|---|---|
| quote | stale recipe/enchant authority | replan, no reservation |
| reserve | conflicting live claim | retry/replan, no mutation |
| reserve | expiry before dispatch | EXPIRED and released |
| dispatch marker | persistence failure | no canonical action |
| active craft | rejected before maker starts | ABORTED/replan |
| active craft | ingredients consumed, no terminal callback | reconcile; never redispatch blindly |
| active craft | exact success/failure observed | COMMITTED |
| background craft | any write fault | full rollback |
| enchant | scroll/support disappeared | abort before dispatch |
| enchant | target changed after reservation | abort/inconsistent |
| enchant success | crash after mutation | exact-after reconciliation |
| enchant safe fail | scroll consumed, target same | committed safe failure |
| enchant blessed fail | target reset to zero | committed reset |
| enchant ordinary fail | target destroyed/crystals granted | committed destruction |
| enchant ambiguous | partial noncanonical facts | INCONSISTENT |
| transition | active dispatch exists | materialize/dematerialize blocked |
| shutdown | reserved only | abort/release |
| shutdown | dispatched | reconcile or persist uncertainty |
| replay | exact terminal operation | idempotent result |
| Goal drift | revision/source changed | stale, no new effect |
