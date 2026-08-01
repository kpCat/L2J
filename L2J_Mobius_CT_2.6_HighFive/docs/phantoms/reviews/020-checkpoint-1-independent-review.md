# Goal 020 Checkpoint 1 — independent review

- Review status: `PENDING_INDEPENDENT_REVIEW`.
- Implementation commit: `e7ba469e63caa6dee113278087258fab005a435a`.
- Completion commit: to be filled from the final accepted tree.
- Required completion subject: `fix(phantoms): complete conversation planning safety`.

## Review boundary

The independent reviewer should verify only Goal 020 Checkpoint 1 conversation
observation/planning safety. Checkpoint 2, outbound/action execution, Goal 021
and Goal 025 are outside this review.

Required evidence:

- synchronous final-filtered `Say2` delivery set closed by `DISPATCH_CLOSED`;
- no election before close and no delivery accepted after close;
- bounded resumable shared-pulse phase machine without full batch scans;
- no external dependency callback under the conversation index monitor;
- typed `SAVED`, `DUPLICATE`, `FAILED`, `AUTHORITY_STALE` persistence;
- temporal oldest-to-newest recent observation hashes;
- one durable mutation and at most one observer-only plan per dispatch;
- no outbound `CreatureSay`, goal or gameplay action execution;
- cumulative and completion scope evidence from verifier 020c1;
- one authorized final `ant verify`, standalone `ant jar`, and two byte-identical
  post-commit verifier outputs.

## Independent decision

To be completed by a reviewer who did not implement this completion.
