# Advanced recruitment contract

## Vacancy-driven

Recruitment starts only from an exact required vacancy produced by the accepted
RoleMatcher.

## Candidate bound

At most 32 candidates per evaluation and no global World player scan.

## Mutation

Only the current party leader may delegate one invite to Goal 017.

```text
re-read roster
→ verify vacancy
→ verify candidate
→ delegate one invite
→ persist pending identity
→ observe canonical outcome
```

## Real players

A real player receives a canonical invite. Their response is never synthesized.

## Refusal

Refusal/timeout creates bounded cooldown evidence and causes selection of a
different candidate. It does not change canonical membership.

## Full party

Nine canonical members means FULL immediately; no invite path may run.
