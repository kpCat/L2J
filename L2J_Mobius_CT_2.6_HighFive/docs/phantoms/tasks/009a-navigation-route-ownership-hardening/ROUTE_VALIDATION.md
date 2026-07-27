# ROUTE VALIDATION — Goal 009A

```text
deadline + route-distance preflight
→ capability
→ initial direct test
→ bounded A*
→ normalize candidate path
→ validate every segment with current direct backend
→ cancellation/deadline checks between segments
→ cache/publish only after full validation
```

Automatically appended exact destination is a normal segment and must pass the
same validation.

Blocked segment returns `ROUTE_OBSTRUCTED`, sets A* cooldown and never carries a
route.
