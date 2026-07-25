# BASELINE — Task 001

## Git baseline

- Git root: `C:/Users/endim/L2J_Mobius`.
- Audited module: `L2J_Mobius_CT_2.6_HighFive`.
- Branch at the initial read: `master`; task branch was absent locally and remotely.
- Working branch: `feature/phantom-world`, created from actual `origin/master`.
- `HEAD` at task start: `16d61833b3983a3976583d0e4813e0de9457a52f`.
- `origin/master` at task start after `git fetch origin --prune`: `16d61833b3983a3976583d0e4813e0de9457a52f`.
- Review snapshot: `16d61833b3983a3976583d0e4813e0de9457a52f`.
- Drift: none.
- Initial status: `## master...origin/master` plus untracked
  `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/`.
- The untracked Task 001 package was the only pre-existing work. It was preserved and is an explicitly allowed task path.

## Environment

| Property | Observed value |
|---|---|
| OS | Microsoft Windows NT 10.0.19045.0, x64 |
| Java | `25.0.4` LTS, HotSpot `25.0.4+7-LTS-189` |
| Ant in `PATH` | absent |
| Ant used | Apache Ant `1.10.15`, downloaded to a temporary directory outside the repository |
| Audit seed | `20260725001` |

The temporary Ant distribution changed neither repository dependencies nor project files.

## Build

`ant -p` exposed the stable sorted targets:

`adding-core`, `adding-datapack`, `adding-readme`, `checkRequirements`,
`cleanup`, `compile`, `init`, `jar`.

The default target is `cleanup`. The audited `build.xml` requires source/target
Java 25 and `jar` depends on `compile`.

The required `ant jar` equivalent was executed with the temporary Ant binary.
It compiled 1,895 source files, created `LoginServer.jar`, `GameServer.jar` and
`DatabaseInstaller.jar`, copied the two server JARs into `dist/libs`, and ended
with exit code `0` (`BUILD SUCCESSFUL`, total time 13 seconds). Generated output
is ignored by Git and is not part of Task 001.

No `test`, `verify`, `phantom-scenario-test`, or
`phantom-performance-smoke` Ant target exists. No JUnit/TestNG/Mockito
dependency or Java test source tree was found. No root `.github` workflow or
`.gitlab-ci.yml` was found.

## Geodata and pathfinding

- `dist/game/data/geodata` exists, but contains only `Readme.txt`; no geodata
  region files are present. `geodataPresent` is therefore `false`.
- `dist/game/config/GeoEngine.ini:10` sets `PathFinding = 2`, so pathfinding is
  configured as enabled.
- `dist/game/config/GeoEngine.ini:13` points to `./data/geodata/`.

This is an observed mismatch: configuration enables runtime geodata-cell
pathfinding while the required region data is absent. No runtime server was
started to infer fallback behavior.

## Existing configuration

- Fake Players: `dist/game/config/Custom/FakePlayers.ini`;
  `EnableFakePlayers = False`, chat/shots and NPC-emulation switches are
  present.
- Offline play: `dist/game/config/Custom/OfflinePlay.ini`;
  `EnableOfflinePlayCommand = False`,
  `RestoreAutoPlayOffliners = True`.
- Offline trade: `dist/game/config/Custom/OfflineTrade.ini`;
  trade/craft and restore are enabled, realtime persistence is enabled.
- Future canonical Phantom config is reserved as
  `dist/game/config/Custom/PhantomPlayers.ini`, with
  `EnablePhantomSystem=false`. Task 001 does not create it.
- The future loader seam is `ConfigLoader.load()` adjacent to the existing
  custom config loads; the future lifecycle seam is `GameServer` startup after
  data/world prerequisites and before offline restoration. Both remain Task 003.

## Database isolation contract

No database process, JDBC URL, credentials, table contents, or live connection
was accessed. No database mutation was performed.

- Production database: `l2jmobiush5` — forbidden to tests.
- Reserved test database: `l2jmobiush5_phantom_test` — to be created only by
  Task 002.
- Current `DatabaseConfig.load()` reads the fixed relative
  `./config/Database.ini`; there is no observed command-line/system-property
  override. Task 002 must supply a separate test config path and fail before
  `DatabaseFactory` initialization if the parsed database name is
  `l2jmobiush5`.

## SHA-256 evidence

All hashes were computed from the audited working tree at commit
`16d61833b3983a3976583d0e4813e0de9457a52f`.

| File | SHA-256 |
|---|---|
| `build.xml` | `cd83d161d2c24bee1bdedc3a80dbc0bd681b72024e9a5656faaf24cca7f79960` |
| `java/org/l2jmobius/gameserver/model/actor/Player.java` | `fc569ff715b031e64b06ba6c7bd89d3934f1c5f81ce5766ab86d9cc9c75f2e54` |
| `java/org/l2jmobius/gameserver/network/GameClient.java` | `5c7958a1ecbba322791abdcfbd6fb25c73c7bf486e6129cffee40e59819855b1` |
| `java/org/l2jmobius/gameserver/network/Disconnection.java` | `d9febe2ddaba2c3416906dacb648dfc17c17cebc8a2fcac3aeed191c07f01d86` |
| `java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java` | `9c2d211c556ec9126ceda6f62a40845f76d147546cdfd65d2c9671c986001236` |
| `java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java` | `3a0bfd2bf6d8362c08e0f089232b65a7977a59576bd547342be429e421c00ee5` |
| `java/org/l2jmobius/gameserver/network/serverpackets/ServerPacket.java` | `aa76dec6f377b92047a2cee2c1e33a8e568e6ce837e8827f0033cbde575e72ac` |
| `java/org/l2jmobius/gameserver/data/sql/OfflinePlayTable.java` | `9185ac5a5426f172f4cb1f86e7ebcb6449a1598f0eeab85aa06a4b176d004ec0` |
| `java/org/l2jmobius/gameserver/data/sql/OfflineTraderTable.java` | `2b36a3aaba8520c06652164267aa61f372ac4e426cdf49d4fd4c4f081555003f` |
| `java/org/l2jmobius/gameserver/managers/FakePlayerChatManager.java` | `c91ce093deb21969dcce94e9a7c00354b4aaf14bef77ce0f359f6870e74add70` |
| `java/org/l2jmobius/gameserver/data/xml/NpcData.java` | `39d9d7f32bc75dfede8ed7203cdc63f3a420d36254149f8c83ec930cd542ea22` |
| `java/org/l2jmobius/gameserver/model/World.java` | `4ae2c1614fe09a69fbccf6dc6e3784f8320d9bc99f046cb632e69048d539d779` |
| `java/org/l2jmobius/commons/threads/ThreadPool.java` | `cf60f621b83d8d7315c71e53850d2c583db8026702750a013b81f359627800b2` |

## Known limitations

- This is a static code audit plus compile/package baseline. It did not start
  LoginServer/GameServer, connect a client, or execute a DB scenario.
- Null-client feasibility is supported by existing offline restoration paths,
  but the proposed seam remains `Proposed` until Task 004 exercises it.
- Source line numbers describe snapshot `16d61833…`; later drift must re-run
  the audit.
