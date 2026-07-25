# ARCHITECTURE — Task 002A

## 1. Ownership-safe lock

```text
FileChannel.open(lockPath, CREATE, READ, WRITE)
  -> tryLock()
     -> null/busy: close channel, reject, do not mutate file
     -> acquired:
          write owner token while lock held
          run provisioning
          release own FileLock/channel
```

Persistent ignored file is acceptable. OS lock, not file existence/PID deletion, is ownership truth.

## 2. Schema freshness

```text
Repository SQL inventory
  -> Snapshot(version/count/statements/hash)
  -> provision DB
  -> store snapshot row in test DB
  -> atomic write .phantom-local/schema-manifest.properties

DB suite bootstrap
  -> guard config
  -> recompute repository snapshot
  -> read local snapshot
  -> exact compare
  -> only then Hikari
  -> query DB snapshot row
  -> exact compare
```

## 3. Lifecycle

```text
register suite
  -> beforeAll attempted
  -> tests if beforeAll succeeded
  -> afterAll attempted in finally whenever lifecycle started
  -> collect both original and cleanup failures
```

## 4. Query allowlist

Allowed:

```text
useSSL
allowPublicKeyRetrieval
serverTimezone
characterEncoding
```

Case-sensitive canonical names are preferred. Duplicate keys are rejected case-insensitively.

## 5. Secrets

Sanitizer covers:

```text
Password=...
jdbc:*://user:password@
?password=...
&password1=...
IDENTIFIED BY '...'
```
