# Goal 026B focused tests

## Large CommandChannel exact leader
Candidate own Party <=9; another Party makes total CC >=10. Expect no exception,
`NOT_STANDALONE_PARTY`, and CandidateAssessment contains only own-Party members.

## Large CommandChannel non-leader
MemberRef belongs to one Party in total CC >=10 but is not that Party leader.
Expect no exception, `NOT_EXACT_PARTY_LEADER`, bounded own-Party evidence.

## Standalone control
Keep one representative existing standalone candidate assertion proving exact
contribution and selected winner unchanged. No new feature cases.
