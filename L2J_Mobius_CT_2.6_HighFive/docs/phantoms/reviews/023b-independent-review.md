# Goal 023B — independent review handoff

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required parent: `563752f6844076fdbaeb3be7c5cae979c757960a`. Corrective implementation закрывает только два production blocker-а, не переоткрывая принятые части Goal 023A:

- `R023B-01`: Goal 017 публикует bounded `RouteActivity`; planner-pending и persisted `PLANNING`/`MOVING`/`REGROUPING` блокируют content rebinding, второй route и преждевременный `READY_TO_ENTER`; `ARRIVED`/`FAILED` требуют terminal cleanup до stable binding.
- `R023B-02`: production `PhantomRiftService.evaluateManagedInvitation(...)` выполняет exact current eligibility refresh перед `ACCEPT`, а canonical integration проходит через `PhantomPartyCoordinator`, `L2jPhantomRiftPartyPort` и `PartyInvitationService` с actual Rift provider.

Независимому reviewer необходимо отдельно подтвердить stale candidate, `REFUSE`, `DEFER`, explicit `party.join`/conversation precedence и отсутствие auto-accept для ordinary real Player. Этот handoff не является self-accept.

```text
Goal 023 baseline 840e159a989f6372da9c471c915413f1e4470daf: CHANGES_REQUIRED
Goal 023A baseline 563752f6844076fdbaeb3be7c5cae979c757960a: CHANGES_REQUIRED
Goal 023B: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 024+: NOT_STARTED
```

Следующий допустимый шаг — независимое review Goal 023B. Goal 024+ не начинать.
