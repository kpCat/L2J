# Independent review — Task 004 headless Player feasibility spike

## Verdict

```text
Technical feasibility: ACCEPT
Commit verdict: FIX_REQUIRED
Revert: NOT_REQUIRED
ADR 0001: Proposed
Task 004A: REQUIRED
Task 005: NOT_STARTED
```

Минимальный outbound/session seam признан технически состоятельным:
канонический `Player` создаётся, загружается и материализуется без fake
`GameClient`/`Connection`; headless path не пишет в сеть и выполняет
`ServerPacket.runImpl(Player)` ровно один раз. Canonical world visibility,
inventory action, persistence, collision semantics и dedicated test DB
подтверждены. Revert доказанной seam-части не требуется.

Commit нельзя принять без bounded safety hotfix по четырём findings ниже.

## Findings

### P1 CharacterSelect/onDisconnection race

`CharacterSelect` удерживал `playerLock` на load/bind, а
`GameClient.onDisconnection()` не использовал тот же lock. Disconnect мог
завершиться между load и bind, после чего selection снова привязывал Player к
уже отключённому client. Task 004A должен сериализовать оба пути одним lock и
проверять `AUTHENTICATED` после входа в lock.

### P1 fail-open lease release

`Disconnection` освобождал REAL_LOGIN lease в unconditional final paths даже
после исключения или неполного удаления Player. Это допускало нового owner при
оставшемся World/autosave/client residue. Task 004A должен освобождать lease
только после общей read-only проверки cleanup postconditions и удерживать его
при ошибке.

### P1 materializer fail-open cleanup

Materializer гарантировал detach/release/clear через `finally`, поэтому
store/delete failure мог потерять PHANTOM ownership и ссылку, необходимую для
retry. Task 004A должен удерживать Player, outbound и identity при operation
failure, разрешать повторный cleanup и завершаться `STORED` только после полного
успеха.

### P2 disabled compatibility and terminal state

При выключенном Phantom system обычный login не должен получать новый
REAL_LOGIN lease или менять legacy arbitration. Исключение — уже существующий
PHANTOM owner, которого необходимо защитить. Кроме того, успешный cleanup должен
иметь однозначное terminal state `STORED`, а повторный вызов быть no-op.

## Required closure

Task 004A ограничен перечисленными findings. DB schema, configs, production DB,
fake network objects, Goal 005 и изменение статуса ADR не разрешены.

После успешной реализации допустима рекомендация
`FEASIBLE_WITH_SEAM_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; независимый manual
gate остаётся обязательным.
