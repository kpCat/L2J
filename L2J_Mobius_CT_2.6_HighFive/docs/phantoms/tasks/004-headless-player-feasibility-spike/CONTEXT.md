# CONTEXT — Task 004

## Accepted baseline

```text
Branch: feature/phantom-world
Commit: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
Parent: eb008f2216b3e8381c0181d71ce200bbf4907ac7
Advancement: approved documentation-only PHANTOM_BOTS_ROADMAP
Task 003: ACCEPT
Task 004: ALLOWED
ADR 0001: Proposed
Task 005: NOT_STARTED
```

## Proven facts from Task 001 audit

- `Player` constructors are private.
- Constructor starts vitality scheduling.
- `Player.load` restores canonical inventory, skills and related state, sets the
  in-memory online flag and registers autosave before World spawn.
- `Player.sendPacket` currently does nothing when client is null.
- `GameClient.sendPacket` writes the packet and then invokes
  `ServerPacket.runImpl(Player)`.
- `ServerPacket.runImpl(Player)` is public.
- Effect-bearing families include HTML packets, CreatureSay, ItemList and
  TutorialCloseHtml.
- GameClient requires a real Connection and owns network/session/LoginServer/HWID
  lifecycle.
- Offline play/trade prove canonical null/detached-client Player operation, but
  do not provide general session semantics.
- EnterWorld mixes client/session and domain initialization.
- World duplicate cleanup is destructive and cannot be normal arbitration.
- deleteMe performs broad best-effort cleanup.
- Player load starts canonical task/autosave ownership that must be measured.

## Current Task 003 skeleton

- default config false;
- no configured instance while disabled;
- one inert bounded queue if explicitly enabled;
- no Player/DB/network integration yet.

Task 004 may reuse lifecycle/metrics concepts but must not auto-materialize a
Player from GameServer.

## Test DB

Current test infrastructure provides:

- exact test DB guard before Hikari;
- durable schema fingerprint;
- dedicated user;
- strict repository schema;
- DB integration and cleanup;
- forked JDK-only test runner.

Task 004 must use it and may explicitly re-provision only if freshness requires.

## Future requirements boundary

The following are mandatory future architecture but out of scope now:

- domain-aware Semantic Pack and Game Knowledge;
- explicit bot goals;
- ACTIVE/NEARBY-PERCEPTIBLE/WARM/BACKGROUND/SLEEPING;
- topology-aware relevance;
- background causality/reconciliation;
- Utility AI and conversation policy.

Do not implement placeholders that prematurely constrain those tasks.
