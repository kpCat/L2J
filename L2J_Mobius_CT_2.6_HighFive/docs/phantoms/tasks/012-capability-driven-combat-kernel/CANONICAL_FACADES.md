# CANONICAL FACADES — Goal 012

Production combat code must use server-side canonical routes:

- materialization `tryAcquireAction`;
- PlayerAI ATTACK intention;
- PlayerAI/CreatureAI CAST intention;
- PlayerAI PICK_UP intention;
- existing shot item handler/auto-use path;
- restricted ordinary town teleport/revive logic.

Forbidden:

- client-packet simulation;
- combat package `sendPacket`;
- direct damage/HP/MP/EXP mutation;
- direct inventory add/remove for shots or loot;
- direct skill effects;
- direct drop calculation;
- Player/Creature/AI core changes.

Canonical methods may internally schedule server tasks and broadcast ordinary
observer packets.
