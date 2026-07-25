# TEST CASES — Task 002A

## Lock

- cross-process holder acquires;
- contender exits busy/config;
- lock file exists;
- owner token unchanged;
- no JDBC marker;
- holder exits;
- second contender acquires;
- abnormal holder termination releases OS lock;
- bounded timeout cleanup.

## Schema manifest unit

- same scripts same aggregate;
- one changed script changes aggregate;
- atomic roundtrip;
- missing file reject;
- malformed version reject;
- malformed counts reject;
- malformed hash reject;
- stale count reject;
- stale hash reject.

## Schema freshness negative

- valid test config;
- sentinel driver;
- stale local manifest;
- bootstrap rejects;
- exit 2;
- marker absent;
- driver loads 0;
- connection attempts 0.

## DB integration

- current/local manifest match;
- DB metadata row exists;
- schema version exact;
- script/statement counts exact;
- aggregate exact;
- remaining prior 8 DB tests pass;
- pool final close.

## Lifecycle

- suite beforeAll creates marker;
- beforeAll throws configuration exception;
- afterAll deletes marker;
- expected exit 2;
- marker absent;
- report contains before-all failure;
- cleanup failure, when injected separately, appears as additional result.

## JDBC query

Valid:

- no query;
- generated four-key query.

Reject:

- user;
- password;
- Password;
- percent-encoded password;
- password1;
- password2;
- password3;
- unknown key;
- duplicate key;
- blank key;
- blank value;
- encoded separator ambiguity.

## Redaction

- named Password;
- JDBC userinfo;
- query user/password/password1/2/3;
- SQL IDENTIFIED BY single quote;
- SQL IDENTIFIED BY double quote.

## Full

```text
ant test
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-provisioning-lock-control
ant phantom-schema-freshness-negative-control
ant phantom-lifecycle-negative-control
prepare-test-db
ant phantom-db-test
ant scenario
ant performance
ant verify
ant jar
```
