# Goal 023 test matrix

## Factual authority
- six Rift types;
- room/spawn parity;
- NPC level envelope;
- GeneralConfig RIFT fields;
- exact entry resource and costs;
- source/config hash drift;
- strict parser failures.

## Roster/composition
- 1, 4, 5 and 9 member rosters;
- mixed Phantom/real;
- leader and membership changes;
- healer/frontline/damage/support vacancies;
- real capability evidence;
- no mandatory double assignment.

## Readiness
- dead member;
- low vitals;
- wrong instance;
- equipment not usable;
- missing shots;
- missing entry resource;
- sufficient supplies;
- route required/ready.

## Recruitment
- deterministic candidate ranking;
- one pending invite;
- Phantom accept/refuse;
- real accept/refuse/timeout;
- cooldown;
- full party;
- stale roster before invite;
- restart pending invite.

## Conversation
- missing-role fact;
- ready fact;
- roster change invalidates stale response.

## Performance
- 100k tier lookups;
- 100k nine-member readiness;
- 100k vacancy matches;
- 10k candidate searches;
- 10k restart reconciliations.
