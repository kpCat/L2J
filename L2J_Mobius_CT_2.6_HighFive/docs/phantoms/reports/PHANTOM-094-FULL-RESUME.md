# PHANTOM-094 — FULL RESUME: PARTIAL delivery

27.10.2026. Ветка `feature/phantom-world`, baseline `9a599e8bed06b6ea874d97e9f1d890a5297511a8`.

По последнему прямому указанию владельца работа переведена в STABILIZE → REVIEW → DELIVER → STOP. Полный TASK094/FULL094_GREEN не заявляется. Новые native-кампании, смена спота, PartyRoute и дополнительные gameplay features остановлены. Результат публикации и фактический commit SHA фиксируются отдельно после обычного push, без самоссылки commit в собственном содержимом.

| Gate | Статус | Факты и границы |
|---|---|---|
| G0 | PASS | AccessDenied установлен и устранён без расширения ACL; собственная TEST094, один create-only bootstrap, guards и postflight. |
| A | PASS | Native114: обычные farm/activity intents, настоящая native Party3, два DD и Prophet, native приглашения и Phantom-only consent. |
| B | PASS | Native114: shared native damage обоих DD, свежий support cast с MP debit и эффектом на союзнике. |
| C | PASS | Native114: два native убийства, earned EXP/SP/Adena, native pickup и exact STORE. |
| D | PARTIAL | Focused ownership/consent/lifecycle/recovery negatives проходят; controlled native negatives были PASS в restart105, но тот запуск не завершил final STORE. Полная повторная приёмка текущего кандидата не выполнена. |
| E | PARTIAL | Native114 и restart114 завершили STORE/drain; новый JVM/sameJAR/sameDB восстановил exact earned SQL и Party/clock. Повторный PvE после restart не состоялся; restart.complete отсутствует. |
| F | LOCAL PASS | Стабильный build121, affected139/139, необходимые guards, content7/7 и независимое review Critical0/Important0. Итог commit/push — в публикационном receipt и сообщении владельцу. |

## Сохранность и G0

Семь первоначальных WIP preimages, затронутые product originals, все старые stages, receipts и логи сохранены вне checkout/в прежних local evidence. Старые отчёты `PHANTOM-AUTONOMOUS-PARTY-FARM-094.md`, `PHANTOM-094-G0-RECOVERY.md` и immutable task packages не переписаны. Предыдущий FULL-RESUME report сохранён с SHA256 `e9bb0293c5dda6be0d8ef20ee432c2c9962b94917e6cf9ebdf40f06f69d0e83f`.

AccessDenied воспроизведён до DDL: CodexSandboxOffline не проходил Java `Path.toRealPath` через прежний session path; разрешённый ZBook достигал stage, но private-файл принадлежал другому NTFS owner. Старые ACL не расширялись. После fresh absence TEST094/sessions0 создана собственная private stage owner+SYSTEM, с CREATE_NEW receipts. Старые файлы не заменялись ради обхода guard.

G0: URL6/stage18 negatives, junction, wrong caller и второе JDBC connection отклонены. Один Java PIN, independent verify, один create-only bootstrap123 canonical scripts. Пропущены только18 доказанно отсутствующих DROP noops; отправлено DROP/DELETE/TRUNCATE0. Read-only verify128 tables, economy071 authority, SQL manifest, registration1+canonical seed2. Foreign metadata unchanged, foreign sessions0. `TABLE_ROWS` исключён из строгого fingerprint; SQLException классифицируется по фактической фазе. SQL aggregate `8699acdd436d29f5666ccc501e6b8d9a09265f064fc16ab7a7166be04c84aea2`.

Authority штатно продлена дважды по прямому разрешению владельца; действующий срок `2026-10-27T10:28:31.4884101Z`. Exact-byte backup/CREATE_NEW receipt, изменён только expiresUtc. UUID, DB, ACL, claims, fixture и earned state при renewal не менялись. Это не разрешение запускать новые кампании после указания STABILIZE.

## Стабильное изменение

Обычный farm/activity root использует существующие PartyCoordinator, RoleMatcher, PartyInvitationService, tactics/combat и общий scheduler. Bounded selection учитывает фактические learned skills, уровень, локальность, instance, готовность и роли. SELF-only DD не заменяет support; guardian требует настоящего native Hate/Aggression. Танк опционален; native114 доказал минимальные2DD+1support. Native aggro опционального танка в этой сцене не заявляется.

Restart claim conflict: фактический DD2 row11 имел INCONSISTENT/generation3/membership2, group `386F578867AB30674C1AF9D875DDF108490EA58DD1CD1519F608716418F9A7E5`, manifest `C9BC95D72FBA572EF2789D40346CAE6A68827C69307523A377BA7353560C0C43`; он совпадал с собственным computed/operation manifest. Историческая пара peer payloads до предыдущего reconciliation не сохранена и не выдумывается. Точный дефект sanitize: пересчёт общего admitted hash по промежуточным локальным JOIN rosters. RED35→GREEN36 сохраняет admitted hash/gen/revision/operation; настоящие stored mismatches остаются INCONSISTENT. Native38 применил только штатный own CAS repair после typed PARTY_ABSENT и World/owner/root проверок. Ручного SQL reset claims нет.

Разрешённый regroup fallback работает и для pending JOIN: строго больше60000ms вдали от подтверждённого большинства. Таймер не сбрасывается retry/route/invitation/generation; schema1/PRG1/112bytes `party.regroup-clock` сохраняет stable intent, farSince, cooldown и observation. Cooldown300000ms резервируется CAS до native side effect. Новый intent сохраняет cooldown; clock rollback и authority mismatch fail closed.

Только managed headless Phantom, current owner/epoch и оба World indexes, native Party majority, exact roots/claims; instance0-only, Geo/NSWE/doors/dry landing, siege/event/death/transaction/store/защищённые действия/incoming aggro guards. Отменяется лишь exact coordinator-owned movement. Используются existing PARTY_REJOIN lease и обычный видимый `teleToLocation`; людей не телепортируют. Native98: firstFar1793082780169 →teleport1793082840235,60066ms, pendingJoin=true, majority2/3, cooldown1793083140235; same owner epoch, затем Party3. Cooldown сохранён, но к114 уже истёк; активное подавление повторного teleport после restart доказывается focused contracts, не выдаётся за событие114.

Другие причинные исправления: actor leases закрываются LIFO после доказанного NATIVE_WORK_CONTEXT_ORDER; lawful orphan recovery использует exclusive absent-owner/canonical durable checks; native support вызывает Player.useMagic и не заменяет чужой pending cast. Source refresh сохраняет1s TTL и budget, не отменяя unchanged owned work при истечении cache посреди slice. Maintenance оставляет35% net MP reserve только для buff/song/dance; urgent actions и реальные castable support capabilities сохранены. Learned protective buffs обслуживаются до optional idle extras, без skill-ID/profile hardcoding.

Owned combat сохраняется до terminal/loot; normal native REJECTED завершает и отменяет exact прежнюю action/lease вместо продолжения старого ATTACK/CAST. Focused corrected RED: old112 core67/68→stable114 core68/68, MELEE и MAGIC. Native reward sensor сохраняет actual scalar settlement при раннем retarget после удаления завершённого ledger; RED1088/9→1099/9, без повторной выдачи rewards/counters. Strict settlement observer сохранён.

Target discovery использует существующий combat acquisition2000, максимум8 кандидатов и current target; region/instance/Z/reward/Geo/risk guards сохраняются. Extended approach дополнительно проверяет dry direct Geo/doors/NSWE и bounded actual PvE threats, включая свежую native boundary. Доказанный far-WALK114 не заявляется: соответствующий passive checkpoint не наблюдался.

Test-only bootstrap загружает34 canonical TargetHandlers вместо отсутствовавшего registry; production handler engine не заменяется. SameDB receipt использует Properties.store/CREATE_NEW, исправляя потерю backslashes Windows path. Повторный STOP уже остановленного test scheduler признаётся по canonical STOPPED; product scheduler не изменён.

## Native114 и restart114

Native114 PASS: два shared native kills, оба DD дают fresh damage и exact reward settlement. Support1043:1 на DD1, remaining1200, observed native MP debit13.7652448, canonical cost28, fresh cast/effect.

| Участник | Earned EXP/SP за Native114 | Earned Adena | Durable после STORE |
|---|---|---|---|
| DD1 | +46614 / +5749 | +1550 | XP71080822 / SP27873 / Adena12826 |
| DD2 | +46614 / +5749 | +1549 | XP71080822 / SP27873 / Adena12825 |
| Prophet | +44933 / +5542 | +1551 | XP70149017 / SP30026 / Adena13483 |
| Optional Pld | не участвовал | 0 | XP70459179 / SP0 / Adena10000 |

Native pickup: leader sequence6/count1590, DD2 sequence2/count1549, Prophet sequence2/count1551. Тестовых раздач после начала сцены, повторной fixture и ручных изменений XP/items не было. Full live-to-SQL STORE всем4, scheduler/party/combat STOPPED, actor leases/workers/queues/actions0.

Restart114: до materialization exact durable XP/SP/items/skills всех4 и exact Party/regroup bytes/schema/row versions; новый JVM, тот же JAR/DB, reseed0. Восстановлены native Party3 и support cast/cost. Shared damage/rewards после restart отсутствовали:20592 effective HP62750 и duration164389.692ms отклонены текущим60s risk guard;20594 не прошёл Geo. После120s confirmed evidence blocker выполнены штатные STORE/STOP; все4 сохранены, drain0. Earned Native114 не менялся. Это runner FAIL, функционально PARTIAL, а не полный E PASS.

Последний durable Party audit: первые3 claims имеют один group `9C86E487E790B614E6DA2D70BA031BB25B3C24233547E7780D8DC9D8AEAE9761`, generation44, membership1014, manifest `80CE489D4B373718CBDFC7426087D043AD7A26F04E19A257560612B31E471021`; все background READY/native COMPLETED. Native114 и restart114 завершили full STORE; Postflight114 PASS. Fresh Postflight121 PASS: owner=true, priorOwnConnections0, foreignConnections0, foreignMetadataUnchanged=true, DDL_DML0. Тот же сохранённый TEST094 проверен read-only; новые Player не создавались.

Старые неуспехи сохранены: Native98 overstrict currency observer исправлен по контракту EXP/SP или loot; restart99 отказал до сцены из-за Properties escaping; restart105 после positive PvE/negative controls не выполнил final DD STORE из-за test drain bug. Последние live начисления DD105 вручную не восстанавливались. Последующий lawful recovery107 и полные STORE подтверждены. Ни один такой запуск не переименован в полный PASS.

## Стабилизация, проверки и scope

Незавершённый camp/PartyRoute эксперимент115–120 исключён из публикации по последнему указанию владельца. Все6 его файлов сохранены exact-byte в sibling `task094-stabilize-experiment-v120`, receipt `preservation.tsv`; предыдущие candidates/RED/failure logs сохранены. Его последний hazard test не прошёл, поэтому эксперимент не объявляется проверенным и не публикуется.

После исключения эксперимента independently verified: все30 product/data overlay и414 test sources совпадают SHA с candidate114, mismatches0. Новый build121 повторно собран из этих исходников; JAR hashes отличаются из-за новой упаковки и не выдаются за native-tested binary114.

Native114 GameServer SHA `dc45294c83ef509aacf7fe037dd6361a901545bbc7d272356e40a3e21de2c995`, Test094 `0df1f4c44f988b627ba4f659ad5fb3d29c9674ae6b7db1d7f7eb04b864293072`.

Stable121 GameServer SHA `a9fe5f202a7f2ec4a019947e441a634d54fd1ffb1eb9ec8d89eca3003433f31e`, Test094 `b7651737663ae52fef707e24e06566dc97154c8417a43f584330a3c6594409bc`. Общие overlay SHA `97058f898e0070d4281beb81d84bdaa15f303eb4dda5e165628ccadc95280e46`, testSources SHA `27fe14e3b57a589acfc0b79018cd7f59f0d6e24eb0a5080e0286a1709061ba39`.

Свежие проверки121, exit0:

- `Task094.ps1 -Action Build -Candidate v121 -Previous v120`: JDK25/Ant, только существующий own shadow, compile-tests/jar.
- `Verify-PartyCandidate.ps1 -Candidate v121`: affected139/139 — state14,roles11,lifecycle22,canonical6,tactics9,route7,combat68,reward2.
- NativeEvidence9/9; Travel0919/9,0893/3,Corridor0644/4. Это DB-free contracts, не native успех.
- Existing combat-action-ownership37/37; после content проверки GameServer/Test094 JAR seals неизменны.
- `phantom-humanized-v3-content-validate`:7/7, isolated shadow с явными build/test.src; generic DB verify не запускался.
- `OwnerConfig.ps1 -Action Verify`:68 файлов,287 overrides,conflicts0,changed0.
- Independent final review stable45: Critical0/Important0, ownership/lifecycle/backward compatibility; restoration proof30+414 отдельно подтверждён reviewer.
- Immutable task packages FULL/G0/original:5/5,7/7,8/8, exact SHA+size.

Exact publication allowlist —45 файлов, `.phantom-local/task094-full-resume/scope-allowlist.core45.txt`:30 product/data,14 test/tool, этот report. Сохраняются38 tracked modifications+7 новых разрешённых файлов. Bounded exception единой TASK094 ранее разрешён FULL-RESUME; дальнейшее расширение остановлено. Старые task packages/reports, ignored/local private evidence, JAR, credentials, чужой dirt в commit не включаются. Защищённые среды/DB/клиент/OwnerConfig не изменены этой стабилизацией.

Git разрешён root bounded inspection и прямым указанием владельца commit/normal push. Выполнены `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`, `git rev-parse '@{upstream}'`, `git ls-remote origin refs/heads/feature/phantom-world`, `git status --porcelain=v1 --untracked-files=no`, exact-path diff и `git -c core.whitespace=cr-at-eol diff --check -- <exact45>`. Точный `git add -- <exact45>` и `git diff --cached --name-only`, `--check`, `--stat`, `--summary` подтвердили только разрешённые45 путей. Разрешены последующие `git commit -m "Stabilize autonomous phantom party farming and guarded regroup"` и `git push origin HEAD:feature/phantom-world`; их фактический SHA/exit/remote equality записываются после выполнения в отдельный publication receipt. Reset/clean/stash/force/merge/rebase/history rewrite и изменение master не применялись.

Mojibake-маркеры в изменённых файлах проверены; escaped Cyrillic в изменённых файлах проверены отдельным regex. Exact scope/staged и publication receipts записываются в local evidence при соответствующих действиях. Денежная стоимость недоступна. Goal tool возвращает исторический blocked snapshot7416546 tokens/44333s; это не текущий billing/Pro counter и не оценка стоимости. Новых token budgets/Goal не создавалось.

## OPEN после доставки

1. Устойчивый поиск следующего безопасного спота/цели и законная смена места всей группы при исчерпании подходящих stock mobs.
2. Длительный Party Farming с чередованием боёв, regeneration/supply/recovery без зависания и повторных бессмысленных маршрутов.
3. Окончательная полная native-приёмка: новый PvE/rewards после sameDB restart, весь D/E и final STORE в одном успешно завершённом сценарии.

Эти пункты не скрыты DB-free PASS и не продолжаются автоматически после публикации. Native114, restart114 и их earned state/evidence сохранены для последующего отдельного решения владельца.
