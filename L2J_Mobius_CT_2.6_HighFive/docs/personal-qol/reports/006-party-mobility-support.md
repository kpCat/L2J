# L2-QOL-006 evidence report

Дата: 2026-09-14

Branch: `feature/phantom-world`

Required parent: `0b1d88165dd747d3325d7bbb6c86f16d5bef88c4`

Результат: **SUCCESS**

## Scope и read-first

До production edits полностью прочитан task package L2-QOL-006 (`TASK.md`, `ACCEPTANCE.md`, `CONTEXT.md`, `FEATURE_CONTRACT.md`, `SOURCE_AUDIT.md`, `TEST_PLAN.md`, manifest и launcher), корневой `README.md`, module `readme.txt`, Personal QoL status/roadmap/operator guide, отчёты QOL-004/QOL-005, текущие config/service/board/test/build owners и найденные native PM, party invitation, Summon Friend, heal, resurrection, karma и party-membership owners. Отдельного on-disk `AGENTS.md`, code-map и pattern-файлов не найдено; применены инструкции из task prompt.

Локальные паттерны: существующие `PersonalCharacterQoLConfig`/`PersonalCharacterQoLService`, shipped-OFF и backward-compatible strict parsing, allowlist real Player с исключением headless Phantom, категоризированный `_bbsqol` board с fail-closed parser, `World` online identity, `Party.containsPlayer`, `Player.fullRestore`, `Player.doRevive` и `Player.setKarma`. Native chat, invitation и teleport owners не оборачивались новым транспортом.

Bounded exception к обычному лимиту 8–10 файлов составляет 12 task-owned файлов: одна production subsystem требует config, service, существующий Alt+B surface, focused suite/build wiring и три обязательных status/operator/evidence документа. Независимые artifact families, broad refactor, client patch, DB migration и Phantom production edits отсутствуют.

## Capability census и классификация

| capability | stock entry point | stock уже достаточен | доказанный gap | owner/seam | сохранённые ограничения | evidence | classification |
|---|---|---|---|---|---|---|---|
| remote private message | `Say2` → `ChatHandler` → `ChatWhisper` | да | нет | stock chat pipeline | target lookup, block list, chat ban/jail, event hooks, filter, length, logging | source census + focused no-op contract | `NO_CHANGE_REQUIRED_ALREADY_NATIVE` |
| exact-name remote party invite | `RequestJoinParty` → `World.getPlayer(name)` → `PartyInvitationService` | да | нет | stock invitation state machine; acceptance/refusal owner остаётся `RequestAnswerJoinParty` | leader/full/busy/pending, block/event/jail/Olympiad/cursed/Rift checks, timeout, accept/refuse, mutation-time revalidation, managed delivery | source census + focused no-op contract + full party integration 10/10 | `NO_CHANGE_REQUIRED_ALREADY_NATIVE` |
| self-only Summon Friend | skill `1403` с `myPartyExceptMe` → `CallPc` → `SummonRequestHolder` | да | нет | stock actor-location summon/consent flow | current party target, source/destination zones, instance, Olympiad/event, jail, vehicle, Rift, resources и QOL-005 Seven Signs | source census + focused no-op contract + QOL-005 freeze | `NO_CHANGE_REQUIRED_ALREADY_NATIVE` |
| heal self/own party | отдельного bounded personal action нет | нет | Alt+B personal support отсутствует | `PersonalPartySupportService` → `Player.fullRestore()` | living self/current own-party online Player, actor/target state gates, live identity/membership revalidation | actual board focused case | `CHANGE_REQUIRED` |
| resurrect self/own party | только обычные skill/request owners | нет | bounded personal action отсутствует | `PersonalPartySupportService` → narrow `Player.doRevive()` | dead target, resurrection block, live identity/membership, no XP restore | actual board focused case | `CHANGE_REQUIRED` |
| negative personal reputation cleanup | authoritative H5 field — positive `karma` penalty | нет | bounded personal action отсутствует | `PersonalPartySupportService` → `Player.setKarma(0)` | only `karma > 0`; no PvP/PK/clan/fame/recommendation/Seven Signs/inventory/skills/quests mutation | actual board focused case + unrelated-state snapshot | `CHANGE_REQUIRED` |

## Реализация и границы

- `EnablePersonalPartySupport=False` поставляется выключенным. Отсутствующий legacy key означает `False`; malformed value fail-closed отключает только QOL-006. Master switch и прежний character/account allowlist остаются обязательными.
- Новый service допускает privileged actor только если это текущий online object того же allowlisted real personal `Player`. Ordinary Player и headless Phantom никогда не становятся actor.
- Alt+B использует существующий раздел `Расходники и утилиты`. Строгий route: `_bbsqol;party;heal|resurrect|reputation;<positiveObjectId>`. Unknown, malformed и forged values не мутируют состояние.
- Board snapshot только отображает self/current party. Перед каждой mutation target заново разрешается через `World`, затем проверяются actor identity, feature eligibility, текущая `Party`, `target.getParty()`, `containsPlayer(actor)` и `containsPlayer(target)`.
- Heal доступен только живой цели и вызывает `fullRestore()` для HP/MP/CP. Resurrection доступен только мёртвой незаблокированной цели и вызывает `doRevive()` без overload с XP power. Karma cleanup вызывает `setKarma(0)` только при `getKarma() > 0`; zero остаётся no-op.
- Actor должен быть жив. Actor и target отклоняются при casting/combat, duel, Olympiad, siege/PvP zone или PvP flag, event, store, teleport/observer/vehicle, jail, cursed weapon, nonzero instance и Dimensional Rift.
- Для actor с karma сохранён общий Community Board deny, кроме минимального маршрута к QOL overview/utilities и exact reputation action; heal и другие board actions по-прежнему блокируются stock `COMMUNITYBOARD_KARMA_DISABLED`.
- Arbitrary coordinates/teleport, direct party insertion, invite auto-accept, cross-party support, NPC/pet/summon target, XP restoration, global rule relaxation, client patch и Phantom AI/lifecycle changes отсутствуют.

## Focused и regression evidence

Qualified candidate: `C:\Users\ZBook\L2J_QOL006_RC_20260914_1755_a0a02c`; normal clean clone на required parent с exact eight-file code/config/test overlay. Для Windows raw-byte guards шесть неизменённых SQL inputs были byte-materialized из чистого operator checkout, сохранив нулевой Git diff и schema aggregate `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`. Два неизменённых pinned semantic inputs также byte-materialized без Git diff: XML `16C749B9E151E7D5FE7D702989A71DFC2AB3EEDDE9FA103C40B7D01A36E66A18`, TSV `2B7676BCCFD4395C267BC298E2F2C8DAE265E23CEE76D76853504BF7172F935E`. Goal039 canonical raw artifact остался на ожидаемом hash. Это validation-only materialization, не product changes.

Использовалась только заранее подготовленная allowlisted test DB `127.0.0.1:3308/l2jmobiush5_phantom_test` с dedicated test user. Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не запускался.

- `ant -q compile-tests`: **PASS**, 151 test sources; только два существующих JDK removal warnings.
- `ant -q qol-party-support-test`: **PASS**, 6/6, seed `1006001`; shipped OFF/legacy/malformed/real-only identity, native PM/invite/Summon Friend census, actual Alt+B heal/res/karma actions, stale/outsider/forged/event denial и unrelated-state stability.
- `ant -q qol-006-affected-test`: **PASS**, `BUILD SUCCESSFUL`, 12 минут 49 секунд; 34 свежих reports, `BAD_LINES=0`.
- `ant -q qol-006-freeze-test`: **PASS**, `BUILD SUCCESSFUL`, 1 минута 14 секунд; 16 свежих QOL-005/Goal037–039 reports, `BAD_LINES=0`. В aggregate встречается ожидаемый `Java Result: 2` от штатного negative control.
- fresh `ant verify`: **PASS**, `BUILD SUCCESSFUL`, 31 минута 9 секунд; 181 text reports. Единственные `failed=1` — ожидаемые `negative-control.intentional-failure` и `lifecycle-failure-control.before-all`, которые проверяют сам runner; Ant и все product suites завершились успешно. `semantic-activation` 3/3, Goal036 8/8, Goal037 native 8/8, Goal038 production composition 1/1, Goal039 final static 8/8.
- standalone `ant -q jar`: **PASS**, `BUILD SUCCESSFUL`, 22 секунды.
- `jar tf`: **PASS** для обоих artifacts. `GameServer.jar`: 9 038 762 bytes, 4 331 entries, SHA-256 `1B488598CB315B758E3527443EE88F1CA0F35DCC22D9B72424A2DF608C6CBF52`, содержит `PersonalPartySupportService.class`. `LoginServer.jar`: 313 194 bytes, 200 entries, SHA-256 `683E8CD1A66FEFB68A572431CE8365139F4046F9D0CC69B240A2AD45FA74D2C1`.

Первый candidate focused attempt был diagnostic-only и обнаружил CRLF schema inventory; после byte-materialization он прошёл 6/6. Первый candidate full-verify attempt был diagnostic-only и остановился на том же Windows raw-EOL классе в pinned semantic XML (`AEFBE38A...` вместо canonical `16C749B9...`); после materialization обоих известных semantic inputs новый свежий полный прогон прошёл. Product guards, hashes и tests не ослаблялись.

## Final artifact scope

Exact changed-file allowlist:

1. `build.xml`
2. `dist/game/config/Custom/PersonalCharacterQoL.ini`
3. `dist/game/data/scripts/handlers/bypass/communityboard/PersonalPremiumQoLBoard.java`
4. `java/org/l2jmobius/gameserver/config/custom/PersonalCharacterQoLConfig.java`
5. `java/org/l2jmobius/gameserver/qol/PersonalCharacterQoLService.java`
6. `java/org/l2jmobius/gameserver/qol/PersonalPartySupportService.java`
7. `test/java/org/l2jmobius/gameserver/qol/QoLPartySupportSuite.java`
8. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
9. `docs/personal-qol/OPERATOR_GUIDE_RU.md`
10. `docs/personal-qol/CURRENT_STATUS.md`
11. `docs/personal-qol/ROADMAP.md`
12. `docs/personal-qol/reports/006-party-mobility-support.md`

Task package, `.phantom-local`, candidate materialization/build/JAR outputs и unrelated tracked/untracked user work не входят в staging.

Commit subject: `qol(006): add party mobility support`.

Mojibake-маркеры в 12 изменённых файлах проверены отдельно: **совпадений нет**.

Escaped Cyrillic в 12 изменённых файлах проверен отдельно по всем шести обязательным regex-паттернам: **совпадений нет**.

Control characters в 12 изменённых файлах проверены отдельно (разрешены только tab/CR/LF): **совпадений нет**.
