# Goal 023 — Rift и advanced party recruitment

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

Goal 023 реализован как единый coherent slice поверх принятого Goal 017 Party kernel. Required parent: `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`. Требуемый commit subject: `feat(phantoms): add rift readiness and advanced party recruitment`. Deterministic test seed: `23002301`.

До реализации Goal 022 overall зафиксирован как ACCEPT с точным C1 timing-flake waiver, а verifier 022c2 сделан historical/descendant-compatible.

## Read-first audit

Прочитаны обязательные project/task документы, Goal 017/020/022 contracts и отчёты, `DimensionalRift.xml`, `DimensionalRift.xsd`, `General.ini`, `GeneralConfig.java`, `DimensionalRiftManager.java`, Party/Player и ближайшие Phantom party/conversation аналоги. README, code-map и отдельный CONTEXT-файл в рабочем модуле не найдены.

High Five factual result:

- 6 Rift types, по 9 ordinary rooms, 25 distinct NPC IDs, 77 spawn entries;
- level envelopes: 28–35, 38–45, 48–55, 58–65, 68–75, 78;
- entry item 7079; costs 18/21/24/27/30/33;
- current `RiftMinPartySize = 2`;
- exact runtime entry owner — `DimensionalRiftManager.start(Player, byte, Npc)`;
- readiness использует только добавленный side-effect-free snapshot owner-а.

Pinned SHA-256 provenance:

- `DimensionalRift.xml`: `BBAF488F3A9B5A7765716679B532223EBFB26877D1FE111D35F94DBE21349AD9`;
- `DimensionalRift.xsd`: `B8D4DC7235F72FA970116145A34371A4DB94B7E1F8517D39E782A38F25C5EE8F`;
- `General.ini`: `B3DB41E77B95BE588AAC9BF75A93FBF01019714C6E1AD58619E55F948C6178FE`;
- `GeneralConfig.java`: working-tree CRLF `94AC374844114C9D83A76B2175716710180FE5CA790234037C9C8764A3FB5957`; canonical Git-blob LF `B7C4B37244D7AAB6F4340BBD32C570728C86D1FDDFCDE0EE52570A32216FF9CF`.

Additional exact production read set (17 файлов, каждый открыт только для ближайшего authority/pattern):

- `GeneralConfig.java` — текущие `RIFT_*` поля и defaults;
- `DimensionalRiftManager.java` — exact entry owner, item/cost/min-size/capacity;
- `DimensionalRift.java` — lifecycle и room ownership;
- `DimensionalRiftRoom.java` — waiting/boss room facts;
- `Player.java` — live member state, inventory и vitals;
- `Party.java` — canonical roster/full-party authority;
- `PartyInvitationService.java` — ordinary consent boundary;
- `PhantomSystem.java` — существующая composition/registration point;
- `L2jPhantomPartyBackend.java` — materialized real/Phantom snapshot pattern;
- `PhantomPartyCoordinator.java` — accepted Goal 017 form/invite seam;
- `PhantomPartyRoleCatalog.java` — current role authority;
- `PhantomPartyRoleMatcher.java` — mandatory/optional vacancy matching;
- `PhantomPartyModel.java` — canonical member/operation types;
- `PhantomPartyRouteCoordinator.java` — shared route handoff;
- `L2jPhantomConversationExecutionPort.java` — Goal 020 typed query boundary;
- `PhantomProgressionCatalog.java` — equipment/capability factual lookup;
- `PhantomProgressionService.java` — current catalog lifecycle/read-only access.

## Architecture and scope

Добавлены strict factual catalog, Phantom-only composition policy, readiness/recruitment service, bounded profile codec/store, L2J read-only backend и Party adapter. `PhantomSystem` регистрирует `rift.prepare` в существующем decision registry. Goal 020 получает типизированные latest Rift facts через совместимый constructor overload.

Canonical live Party roster является authority. Candidate discovery ограничен 32, ranking детерминирован, одновременно существует не более одного pending invite. Реальный игрок никогда не auto-accept; Phantom использует обычный Goal 017 invitation path. READY_TO_ENTER не выполняет entry, consume, teleport, room jump или combat.

DB migrations и SQL отсутствуют. Используется только `l2jmobiush5_phantom_test`; production DB не изменяется. Новых config keys нет.

## Focused evidence

- восемь modes: catalog 4/4; roster 3/3; role/typed facts 4/4; recruitment 3/3; real consent 3/3; travel 3/3; restart 3/3; performance 2/2;
- Goal 017 affected: role 6/6, invite 6/6, recovery 6/6, route 5/5;
- Goal 020 affected: party actions 7/7, query execution 3/3.

Performance proof покрывает 100000 catalog/readiness/RoleMatcher операций и по 10000 candidate/restart операций. JVM на этой машине запускается с `-XX:+UseSerialGC`: Oracle JDK 25 G1 однажды завершился native crash до тестовой логики.

## Verification sequence

До freeze проверены focused modes, affected regressions и historical verifier 022c2. Historical 022c2 дал byte-identical stdout 503 bytes в PowerShell 5.1 и PowerShell 7.6.4; working verifier 023 — byte-identical stdout 394 bytes. Portable PowerShell 7.6.4 взят из официального release archive, SHA-256 `80832551C52809301E6071C8BAC977BEB5A2F1EC953EB4DB9F94DEB953333793`.

Terminal gates после freeze:

- единственный `phantom-rift-goal023-test`: `BUILD SUCCESSFUL`, 58 секунд;
- единственный plain `ant verify`: `BUILD SUCCESSFUL`, 17 минут 48 секунд;
- единственный standalone `ant jar`: `BUILD SUCCESSFUL`, 19 секунд;
- mojibake-маркеры в 37 изменённых файлах проверены отдельно: 0 совпадений;
- escaped Cyrillic/XML escaped Cyrillic в 37 изменённых файлах проверены отдельно: 0 совпадений.

Git: branch `feature/phantom-world`, parent `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`, один ordinary commit с указанным subject. Commit SHA, push result и два post-commit byte-identical verifier 023 runs возникают только после создания содержащего этот отчёт commit и сообщаются в финальном ответе; второй commit для самоссылочного обновления отчёта не создаётся.

## Limitations and risks

Это preparation-only slice. Независимый reviewer должен подтвердить границы entry owner, mixed roster, consent, cooldown/restart и отсутствие side effects. Goal 024 не начат.
