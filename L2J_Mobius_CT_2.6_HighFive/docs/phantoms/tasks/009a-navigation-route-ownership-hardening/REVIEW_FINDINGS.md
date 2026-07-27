# REVIEW FINDINGS — Goal 009

## P1 — unvalidated computed route

The service validates structure and distance but not door/fence-aware movement
between computed waypoints. It appends exact destination without validating that
segment. A blocked route may be published and cached as PATH_FOUND.

## P1 — backend before preflight

Expired and route-budget-impossible requests perform capability and direct
GeoEngine work before rejection.

## P1 — dispatch/stop race

Worker ownership is claimed under the service monitor, but dispatcher invocation
happens later. STOPPING may overtake that gap.

## P2 — incomplete diagnostics

Configured shutdown snapshot and final severe log describe materialization only,
even when navigation workers are the sole shutdown blocker.
