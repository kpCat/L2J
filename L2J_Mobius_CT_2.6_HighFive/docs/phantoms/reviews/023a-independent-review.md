# Goal 023A — independent review

Status: `CHANGES_REQUIRED`

Проверен baseline `563752f6844076fdbaeb3be7c5cae979c757960a`, parent `840e159a989f6372da9c471c915413f1e4470daf`.

Goal 023A сохранил и существенно улучшил принятые части Goal 023: exact pre-invite revalidation, `rift.preparation` schema v2 и v1 replan, full canonical invitation identity/expiry, typed terminal outcomes и semantic facts, Phantom-first bounded discovery, Goal 018 relationship modifier, bounded metrics и отдельный `ENSURE_PARTY_BINDING` stage.

Production acceptance остаётся заблокирован двумя findings:

- `R023B-01`: Goal 017 content binding не учитывает planner-pending route ownership и persisted `PLANNING`/`MOVING`/`REGROUPING` при `ROUTE + COMMITTED`; binding может стереть live route, допустить второй route request или `READY_TO_ENTER` до terminal cleanup.
- `R023B-02`: production `PhantomRiftService.evaluateManagedInvitation(...)` не обновляет полную current eligibility exact invitee для всё ещё отсутствующей vacancy, а canonical integration test подменяет actual Rift provider на `ignored -> ACCEPT`.

```text
Goal 023 baseline 840e159a989f6372da9c471c915413f1e4470daf:
CHANGES_REQUIRED

Goal 023A baseline 563752f6844076fdbaeb3be7c5cae979c757960a:
CHANGES_REQUIRED

Goal 024+: NOT_STARTED
```

Следующий допустимый шаг — только corrective Goal 023B и его последующее независимое review.
