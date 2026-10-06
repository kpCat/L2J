# TASK017 engineering evidence

Required base: `0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7`.
Remote branch: `experiment/m1-candidate007-observe008`.

Применены systematic-debugging, test-driven-development и verification-before-completion.
DB-free regression использует настоящий PhantomHumanLocalityControl.onPulse,
существующие topology snapshot/TestBackend и PhantomTestLauncher. Singleton recorder
включается штатным config load в отдельном private test working directory.
Production DI/API не расширялись.

RED: 0/3, все три ошибки — OFFLINE ids1..8 занимают watch slots.
GREEN: 3/3. Проверены 12 OFFLINE probes, 0/3/9 ONLINE candidates, max8 slots,
predicate invocation sequence, candidate membership, physical callbacks, local state,
signal source/state/sequence/TTL/result и diagnostics OFF без retention.
Existing recorder: 3/3 GREEN. `ant -q compile-tests` и `ant -q jar` exit0.
Два существующих removal warning System.runFinalization вне scope.

Production diff: один guard вокруг watch/record. _online.test вызывается в прежнем
месте ровно один раз; return online и весь остальной код byte-normalized равны base.
Нет gameplay/materialization/persistence semantic fix.

Mojibake-маркеры в изменённых файлах проверены отдельно через Verify017.py.
Escaped Cyrillic в изменённых файлах проверены отдельно через Verify017.py.
Результат exact scope/source/encoding/diff guard сохраняется в STATIC_VERIFY.log.

Runtime пока pending. M1=OPEN. Automatic continuation=false.
