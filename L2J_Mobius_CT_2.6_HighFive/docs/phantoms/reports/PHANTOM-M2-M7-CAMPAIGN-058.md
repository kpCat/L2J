# TASK058 — RESULT (IN_PROGRESS)

Начало **16.10.2026 11:18:49 UTC**; deadline **17.10.2026 01:18:49 UTC**; резерв с **23:48:49 UTC** либо раньше по большему измеренному сроку. Compaction бюджет не обнуляет. Итоговый elapsed ещё не зафиксирован.
OWN=D:\Codex\worktrees\phantom-dev\L2J_Mobius; только High Five, Java25/Ant/stock datapack.
BASE=039d1dcb8b702285e4d0eaffbadec2cf9274d24b; normal push HEAD:refs/heads/experiment/m1-candidate007-observe008.

**М1 OPEN**, development base ACCEPTED_WITH_DEFERRED_GATES; G4 DEFERRED_BY_OWNER_NOT_PROVEN; G6 BLOCKED_PLATFORM_DEFERRED; полная приёмка DEFERRED_BY_OWNER. Поправка к прежнему запрету М2–М7 записана в RELEASE_CONTRACT. Исторические OPEN/FAIL сохранены. Владелец штатно остановил057; её runtime/release_057a/персонажи/конфиги/порты2106/7792/9032 не изменялись и не запускались.

| Этап | Сервер | Открытая граница | Владелец |
|---|---|---|---|
| М2 | **SERVER_VERIFIED**, объявленные native stock-роли | Финальный candidate regression/smoke ещё требуется | QA_PENDING |
| М3 | **PARTIAL**: delivered speech, durable joint/personal memory, ordinary sameDB restart/recall | Полные команды/естественные отказы; «стой здесь» unsupported | QA_PENDING |
| М4 | **PARTIAL**: actual damage facts, enhanced retaliation, native ranged walking, mixed-party defense/leave | Полный risk/retreat/clan/war/servitor/death/save flow | QA_PENDING |
| М5 | **PARTIAL; encounter FAIL**: current skills/NPC агро/ranged tactics/actual raid damage | Tank death/незавершённый heal; victory/reward/regroup/recovery | QA_PENDING |
| М6 | **PARTIAL; encounters FAIL**: native Kamaloka/Rift entry/guards/combat | Kamaloka victory/reuse/closing; Rift human death/no EXP, полный цикл | QA_PENDING |
| М7 | **PARTIAL**: earned20/EXP/SP, native gear/save/paid skills/current rank/effect/reload | Профессии20/40/76, subclass/noblesse quests и самостоятельный town-to-field travel | QA_PENDING |

TEST loopback/packet-neutral partners не называются настоящим клиентским входом. SQL XYZ не доказывают World presence. Объявленные level/class/gear/skills — FIXTURE_PRECONDITION; заработанный progress М7 выделен отдельно. М8–М10 не начинались.

Дополнение поддержки: Cardinal использует изученный Greater Heal как отдельную stock capability с source Bishop tree, выше Balance Life в выборе одиночного heal. Native RED missing rule→GREEN1217:101/actual HP effect/MP cost1/1 при наличии обоих навыков; affected heal/recharge/res3/3. Навык не выдаётся продуктом, текущий ранг/ресурсы/target guards сохраняются. Улучшение не означает закрытого рейда М5.

М2: адресная русская речь → native SENT → canonical invitation/consent в обе стороны, включая refuse/expiry/replay/foreign/full-party guards. Три роли идут вокруг препятствия native walking без TP; assist даёт две полные Karik18008, damage всех3/EXP/MP/arrows. Tank90 использует learned28:49 против NPC и держит агро26s; Player не становится целью агро. Heal/349 buff/recharge1013/res1016 имеют effect/cost/consent и negatives. Leave возвращает прежний personal FARM с четырьмя stock kills/earned EXP. Partychat отвечает одним elected member, повтор молчит. Handoffs во время cast сохраняют его. Actual1339:101/1013:101, bow remove/re-equip и native subclass switch/reload обновляют способности.

М3: ordinary real-led JOIN сохраняет подтверждённое событие с character ID; receipts различают JVM invitations и exact callback retry. Scout11↔TestAdmin: delivered «старый рок», personal205 bytes; ordinary Game37s boot/Status/normal save+Stop/restart → новый native JVM доставляет recall с тем же personality/event, изоляцией другого counterpart и query/idempotency guards. RU248 corpus3/3, v3 content3/3, humanized6/6. Полный М3 не объявлен.

М4: PlayerStatus хранит только фактический входящий HP/CP damage, максимум32 IDs/30s, без Player references/SQL/таймера. Combat/Party читают текущую законность/instance/membership/NO_PVP. Idle loadout перечитывает actual rank; cast/attack/movement сохраняются. PvP spacing использует прежние4 попытки по<=5s/16 geo-кандидатов/step<=500/Z64/LoS, исключая Peace/NoPvP/Water. Tank NPC tactics на Player не перенесены.
Native11: MOVE_TO, max sampled step18.44, max distance241.12; actual1339:101, HP+CP8344→7775, MP3684→3598.41; mixed PARTY_DEFENSE8344→7804, leave снимает evidence, все3 живы; NO_PVP/same-party/invulnerable/no-fact/TTL controls. Native3/3, focused RED4/5→GREEN5/5. Knight class-tree/S-jewelry — новый объявленный input. Предыдущий input без бижутерии погиб; его FAIL сохранён.

М5: current-rank refresh, tank-only native melee, общий NPC aggro/spacing. Focused8/8/runtime5/5/safety4/4. Stock RaidBoss25199/72/HP912634: Mage1339:101 damage326, HP→912386.94, MP3884→3835.50, spacing; tank28:49 агро10.1s. Tank death/actualHeal=false — FAIL. Прежний helper PASS с cast-start без attributed damage не используется как доказательство завершённой магии.

М6: stock Kamaloka57, смешанная партия4, canonical entry/stale/nonleader/identity guards,600s боя, все4 живы, boss HP163178→107430.17; victory/reuse/reward/natural closing не достигнуты. Rift1: Recruit31488, missing-fee/nonleader negatives, native7079 fee/room/identity. Подтверждённый RiftInvader extends Monster копируется как MONSTER: native room4/21668/common combat/HP2863→0, но human погиб,43vs30 не дал EXP — scenario FAIL. Native knowledge parity22/22. Автовход/full timed/death/logout cycle не заявлены.

Штатный teardown: все8 FestivalManager wait теперь завершают worker при interrupt; actual signup→обычный shutdown1/1 без инфраструктурного потока. Pending teleport сбрасывается внутри existing TEARDOWN ticket до final store: после admission-close setter раньше молча не работал. Native RED CLEANUP_FAILED_RETAINED→GREEN SUCCESS/teleporting=false/обе World-записи отсутствуют, ordinary human pending-teleport logout тоже PASS. Player/World guards не менялись; все исторические intermittent FAIL исправленными не объявляются. Fault injection/crash/kill не выполнялись.

М7: input Fighter0/19/EXPfor20−500/SP0/NoGrade68+24+31 и заранее принадлежащие D129+352+2378. Четыре full stock20035 (nativeHP446 со штатными skill multipliers) дают EXP+2420/SP100/natural20/expertise1. Native progression owner надевает D после combat-end, normal save/return сохраняет ID/EXP/SP/gear. Trainer30010 списывает100→50→0 за Power Strike3:1→3:2, retry идемпотентен. Curated Fighter0 capability с source HumanFighter tree ничего не выдаёт: до обучения selectedSkills пуст, после — actual3:2.
Immediate unsaved-SP learning остаётся DURABLE_SP_STATE_CONFLICT; guard сохранён. Native return поместил актёра в город, Peace верно отказал бою. В отдельном объявленном field input менялась только позиция, не HP/MP/EXP/SP/skills/items: actual3:2 HP446→294/MP184→175; native reload сохраняет object268435465/EXP837782/SP0/skill3:2. Native8 —1/1. Самостоятельный путь из города в поле и вся карьера М7 не заявлены.

История сохраняет invalid direct-Monster HP input, test double-consume receipt, три kills/SP75→native death/EXP loss, unsaved-SP conflict, отсутствие Fighter capability и Peace отказ. Награды/HP/MP/таймеры/boss параметры ради PASS не менялись. Failed residues очищались только в058t по exact receipt/account/name/IDs/offline/access0/optimistic profile guards, native GameClient и one-use markers. После auto-review отказа от account deletion более узкая очистка с сохранением account разрешена по read-only proof/AUTHORITY§3. Sweep/reset/чужих строк не было.

Свои058t(pool4)/058a(pool8): stock121 SQL/124tables установлены один раз, fingerprint2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28. Общие MariaDB service/datadir/users/grants, MAIN/PLAY/040a/другие worktrees/старые SQL/raw/backups не изменялись.
058a STOPPED. Перед export подготовлен TestAdmin268435465/localplayer/class94/80/S-gear/1339:101 с прежними account/password/ID: manual fixture, не М7. Партнёры: T058Tank90/80, T058Mage94/80, T058Heal97/80, T058Buff100/80, T058Mana105/80, T058Scout0/1. Saved точка x=-71350,y=258050..258170,z=-3104. Новый SELECT-only baseline:33chars/32profiles/prepared7/skills437/items73; прежний378/68 исторический.

Release_058a создана одним consistent stopped native export/import после quiescence:124/124 extended checksums, engines/counters/collations равны, FK только release; source writes/reset/users/grants отсутствуют. Receipt .phantom-local/contract058a/release-export.complete; повторный import запрещён. SQL приватный, review ZIP/raw не создаётся.

**Доставка IN_PROGRESS:** D:\PhantomWorld\M2-M7-058-candidate runtime отсутствует. Нужны clean committed build, final selected regression, START/STATUS/normal STOP + sameDB preserve-progress restart/STOP, финальные инструкции. Login2138/Game7838/internal9138; свой H5 клиент владелец настраивает отдельно. START_HERE_RU содержит имена/место и5–10-минутную party/chat проверку; OWNER_LOGIN058.txt будет приватным файлом candidate.

Последний published a3324e1c29ba59f5a3554896d358f70275e9cb58; связанный10-path slice пока не опубликован. Native Game316F9DC842F8AD60725CC4F2B996CD4CDE6D07453FA4FBAEE7CE6D70C5EF603A; core XML CE11D37497D9B7FD4410A4A405F21BBF0A61A9D9C51D81E2D50A356ACE499915. Ordinary058a Game3B2B3C0A/LoginC7A06A58 — историческая М3 проверка. Native Player/Combat/Party/Conversation загружаются из одного ordinary JAR без private product overlay.

Evidence: .phantom-local/logs/TASK058, .phantom-local/contract058t/reports, private CHECKPOINT058.md/ACCEPTANCE058.tsv. Последние: native PvP3/3, pure PvP5/5, common7/7, native knowledge22/22, normal teleport1/1, earned/paid/field-effect1/1, affected current M2 full composition1/1. Final candidate set ещё требуется. Reads/команды точно не измерялись; build/test counts будут подсчитаны по логам с сохранением RED/invalid/FAIL.

Git разрешён TASK058: git rev-parse HEAD; git status --short (также exact scope/untracked options); git diff с exact paths/--stat/--numstat/--name-only/--cached/--check; git add -- exact paths; git commit -m message; git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008; git archive --format=zip --output=own-input commit -- L2J_Mobius_CT_2.6_HighFive. Staged sets сравнивались с exact allowlists. Force/history/branch/reset/merge не применялись.

mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
