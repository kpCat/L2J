# TEST CASES — Task 004

## A. Output/session unit tests

1. null packet fails;
2. default null-client session remains no-op;
3. stale attachment cannot detach newer session;
4. detach restores client-bound adapter;
5. headless custom effect count exactly one;
6. throwing effect propagates;
7. no-effect packet safely discarded;
8. bounded recursion rejects cycle;
9. bounded recorder capacity/drop;
10. recorder disabled allocates no buffer where practical.

## B. Actual packet effects

1. HTML action cache is changed by actual HTML packet;
2. TutorialCloseHtml clears actual validation state;
3. ItemList sends nested ExQuestItemList and terminates;
4. CreatureSay actual snoop/observer effect;
5. no network class/write counter is touched.

## C. Identity lease

1. first phantom claim succeeds;
2. second phantom claim fails;
3. real reservation blocks phantom;
4. phantom blocks real reservation;
5. stale token cannot release current owner;
6. close is idempotent;
7. load failure releases;
8. cleanup releases;
9. concurrent deterministic barrier has one winner;
10. actual GameClient load path contains same gate;
11. CharacterSelect failed bind releases reservation;
12. Disconnection final cleanup releases;
13. current real-real World path remains bounded/unchanged.

## D. Test environment/fixture

1. config loads from dist/game;
2. test DB guard/fingerprint passes;
3. no GameServer/LoginServer/ConnectionManager;
4. account fixture exact;
5. canonical Player.create succeeds;
6. create object cleanup;
7. canonical Player.load succeeds;
8. final character/account cleanup twice;
9. zero fixture-owned DB residue.

## E. Materialization

1. claim before load;
2. canonical Player object;
3. getClient remains null;
4. headless session attached;
5. inventory restored;
6. skills restored;
7. deliberate online/session policy;
8. World registration exact once;
9. object/player lookup returns same object;
10. state ACTIVE;
11. admission open;
12. no TCP/network writes.

## F. Observer visibility

1. materialize observer and subject near each other;
2. subject appears in World;
3. observer output records expected visibility packet(s);
4. broadcast does not depend on fake GameClient;
5. dematerialization emits/removes visibility consistently;
6. World contains neither after cleanup.

## G. Safe action/conservation

1. action rejected before ACTIVE;
2. baseline item count;
3. canonical add succeeds;
4. exact +1;
5. canonical remove succeeds;
6. returns baseline;
7. Runnable/request packet not used;
8. action-vs-dematerialization closes admission;
9. failed action leaves baseline;
10. reload remains baseline.

## H. Cleanup/restart

1. store;
2. delete;
3. output detached;
4. identity released last;
5. DB online false;
6. World absent;
7. autosave absent;
8. audited futures cancelled/done/null;
9. no party/trade/request/instance residue;
10. cleanup second call no-op;
11. reload same object ID/data;
12. final cleanup.

## I. Failure injection

For every point in `FAILURE_MATRIX.md`:

- expected failure;
- cleanup once;
- cleanup twice;
- every residue assertion GREEN;
- no item delta.

## J. Performance smoke

- warm-up one fixture;
- measured one fixture;
- ten sequential fixture materializations;
- materialize/dematerialize p50/max or individual values;
- no concurrent ten-player DB load burst;
- no World/lease/autosave/thread growth;
- bounded packet recorder;
- timeout <= 240 seconds.

## K. Full regression

- Task 002/002A suites;
- Task 003 skeleton suite;
- headless unit/integration/performance;
- aggregate `ant verify`;
- `ant jar`;
- production JAR test entries zero.
