# Goal 012A — combat action ownership truth

## Status

```text
Status: ACCEPT
Architecture result: bounded safety closure independently accepted
Commit: 8dba87e9c1d5828376b80c1ea16c4578726d4947
Parent: 74dd973c167adf0a74e7af78ed7944e2518c16cb
Branch: feature/phantom-world
Subject: fix(phantoms): harden combat action ownership
Goal 012: ACCEPT after Goal 012A
Goal 012A: ACCEPT
Goal 013: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 014: NOT_STARTED
Goal 015: NOT_STARTED
```

## Итог

Независимое review подтвердило closure обязательных findings Goal 012:

- accepted dispatch имеет exact handle и единый gate с `STOPPING`;
- scheduled-not-started work отменяется, worker claim освобождается в top-level `finally`;
- canonical cleanup хранит exact owned attack/cast/pickup descriptor и не отменяет foreign action;
- failed cleanup сохраняет `ActionLease` для bounded retry;
- loot success требует положительного inventory/object evidence;
- selected skill ограничен безопасным hostile one-target route и повторно проверяется с exact session mode;
- respawn владеет exact plan token и повторно сверяет ownership после actor acquisition.

Server core, datapack, config, schema и другие хроники не менялись.

## Immutable handoff

```text
Commit: 8dba87e9c1d5828376b80c1ea16c4578726d4947
Parent: 74dd973c167adf0a74e7af78ed7944e2518c16cb
Combat core: 47/47 ×3
Ownership: 17/17 ×3
Action ownership: 33/33 ×3
Real integration: 19/19 ×2
Performance: 1/1 ×2
verify/jar: ×2
Post-commit verifier: 102/102 ×2
Verifier SHA-256:
7F5EFA1D3D506E73A5741010833DF82685A0530BBF24D0E7C9326F8514E81A16
Remote: exact
Verdict: ACCEPT
```

## Архитектурные решения

`DispatchResult`, `DispatchHandle`, `PhantomOwnedAction`, bounded cleanup state и plan-owned respawn остаются принятым контрактом. Combat service не регистрирует production candidate и не использует client packet handlers как внутренний API.

## DB, config и migrations

- production DB не использовалась;
- integration использовала только `l2jmobiush5_phantom_test`;
- migrations и config keys не добавлялись;
- geodata и datapack не менялись.

## Ограничения

Cleanup exhaustion остаётся явным `FAILED` ownership и требует operator reconciliation. PvP, party, raid, spoil, progression и commerce не входили в Goal 012A.

## Следующий gate

Goal 012 и 012A закрыты как `ACCEPT`. Реализация Goal 013 разрешена, но сама Goal 013 не принимает себя: её manual gate остаётся `PENDING_INDEPENDENT_REVIEW`. Goal 014/015 не начаты.

Mojibake-маркеры в изменённом файле проверены отдельно. Escaped Cyrillic в изменённом файле проверена отдельно.
