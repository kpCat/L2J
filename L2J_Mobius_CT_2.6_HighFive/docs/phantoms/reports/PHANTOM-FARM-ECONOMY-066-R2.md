# PHANTOM-FARM-ECONOMY-066-R2

22.10.2026. **CORE066R2=PARTIAL.** Пожизненный фонд5000, его native расход, исторический fence и ranged ordinary pickup подтверждены. Полная покупка за заработанную с нуля Adena и связанный Scavenger→склад→возврат пока не доказаны. Исторические FAIL066 не пересматриваются. Вход владельца и client QA не требуются.

## Код и поставка

- Live baseline `feature/phantom-world`: `5f112172f31cbf36cd3e1c0e47da48ffc77ccbde`, проверен `git ls-remote` до изменений. Ни master, ни другие ветки не изменялись.
- Финальный production commit: `f0fd6a26c2c3380f097678fec563ec201c2d552a`.
- GameServer SHA256: `8B9C3376B4F6D66DE4DBFB8A7BCCA6D9F1D8A4C2E451344739297A467BF515AD`.
- LoginServer SHA256: `C6BB2A4B87BA914D42A9E6C08C49F95FE2A9795FE7FAAA21BB269C31A93AEAAB`.
- Exact committed source archive: `5CC56B9745CADFD0CC9FBB9A15C3652BE9034D2CD1CB5F4EAFE2C97D775279AB`.
- Compiled test tree: `E09866AD798296080090A23D0E62380E147E025685EFD23C6F89F115F303F74C`.
- Самостоятельный candidate: `D:\PhantomWorld\Farm-Economy-066-R2-candidate`; собственная schema `l2jmobiush5_phantom_release_066r2`, порты2147/7847/9147. TEST: `.phantom-local/task066r2/runtime`, schema `l2jmobiush5_phantom_test_066r2`, порты2247/7947/9247.
- Две новые схемы; release получен единственным consistent read-only clone остановленного066a, TEST создан один раз по121 stock SQL/124 tables. Повторного import/reseed нет. Исходные057–066 и их БД защищены.
- Source archives, build receipts, PID/start ticks, native и observer evidence сохранены локально в `.phantom-local/task066r2/`. Credentials, JAR, GeoData и raw DB в Git не входят. Финальный report-only HEAD и live remote фиксируются во внешнем `PUBLICATION066R2.json`, чтобы не создавать самоссылку commit hash в его содержимом.
- Standalone hashes: config manifest88files `FD682A25975C6947B62781D830295FBAC991D7167E6DC63EED4E4FC74CD55F81`; evidence manifest67files `204BC691BB132CCDB367116EC879808C7B03BF5DAFA76E3F3E0A411B67A75E71`; Geo manifest203regions `E711392591B65133D7993E46BA4259A49FE7A5FE13E244678DD7454C2A25EB8A`. Персональные/DB configs представлены hashes, не содержимым в Git.

## Реализованные общие контракты

1. Atomic population shell добавляет `starter.shots.v1` только новым Phantom profiles, в той же транзакции. Legacy48 и V1 PlayerCreationInitializer/init digest не менялись. Лимит config0..5000; default0 сохраняет прежнее поведение, candidate явно5000.
2. Materialized native Player получает не более1000 при compatible stock<100, суммарно≤5000. Выбор1835/3947 использует реальные weapon counts, known damage spells и native handlers;2509 учитывается как совместимый запас. Без оружия, advanced grade, enchant или first claim после novice stage выдача запрещена. Проверяются вес/слоты и economy claims.
3. CAS сохраняет debit/PENDING **до** physical inventory credit, затем проверяет inventory и SQL exact delta. Неопределённый остаток не компенсируется и не проигрывается повторно. Фонд помечен STARTER_SUBSIDY, не earned. Все три fungible NG pools у entitled Phantom консервативно защищены от SELL/private SELL; обычный Player не ограничен.
4. Выдача перенесена из раннего Player load в admitted materialization. Внутри native checkpoint запрещён credit, пока versioned historical catchup blocksNormalOperation. При historical baseline lifecycle callback происходит до capture: изменять inventory в этот момент нельзя.
5. Отдельный reviewer нашёл TOCTOU: уже допущенное equip действие могло сменить NG→D во время checkpoint drain. Native RED на EFE подтвердил issued1000 вместо0. В final code окончательные session/combat/weapon guards и выбор shot type выполняются после seal; cheap stock prefilter сохранён. Instance читается однократно. Final GREEN: issued0/stock0/READY, exceptions0.
6. Supply допускает полезную доступную частичную покупку, с reserve500, fee, weight и минимумом расхода относительно дороги; shots рассматриваются раньше gear. Невозможное предложение кэшируется, прежний bounded retry сохранён. Если refill недоступен, gear/farm fallback не исчезает. Бесплатной Adena и beastshots нет.
7. Background amplification optional для ORDINARY_DEATH_DROP, ACQUISITION_DEATH_DROP и ACQUISITION_SPOIL_SWEEP. Тратится только фактический запас; MP, upkeep, seed и прочие mandatory costs сохраняются. CombatFacts не получает бесплатный shot bonus. World/BACKGROUND exclusive reward owner не менялся.

## Проверки и границы доказательств

Точная матрица — [ACCEPTANCE-R2.tsv](../tasks/PHANTOM-FARM-ECONOMY-066-R2/ACCEPTANCE-R2.tsv). Каждый episode содержит фактический code/JAR/test source binding; не все старые native сцены исполнялись на final JAR.

| Проверка | Факт |
|---|---|
| Clean final Ant jar + compile-tests | exit0; Java25; два прежних deprecation warnings |
| Final content validate | 3/3 PASS, exit0 |
| Final commerce-decision / lifetime codec | 7/7 PASS |
| Final Background model | 10/10 PASS, ordinary + обе acquisition ветки, stock0/1 и mandatory negatives |
| Native final starter | Два настоящих newborn класса124/10, пять партий ровно5000, sixth denied; demat/remat exact |
| Native final safety | 9 distinct cases PASS: concurrency, pending ambiguity, D-grade, no weapon, advanced first claim, historical fence, novice progress, enchant, admitted equip race; human exclusion |
| Ускорение R1 | По разрешению владельца перед каждой finite TEST-фазой физический остаток уменьшен до102; три штатных charges дали99; ledger не подменялся. Это не естественный расход5000 в Main |
| Native magic / positive charge | На a4f1cde/FD77… 4/4 PASS: unboosted melee446→402; spell402→235, MP271→259, callbacks drained; physical stock30→24; BSS30→26, regular30 unchanged; hybrid/summon controls. Positive controls используют конечный собственный TEST stock, денежную покупку в них не измеряли. Эти production combat files после a4 не менялись |
| Исторический magic FAIL | Старый dual-class1177:1 против L17 даёт штатный resist; current learned tree1177:5/magicLevel14 успешно исполнил actual cast. Не объявлено, что прежний FAIL стал PASS |
| Ranged current ordinary | EFE/E892…: death distances327/322.6; source268481863, ordinary item57×143, pickup seq3, actorCount143, nativeConsumed=false и inventory+143. Herb receipts отдельно; не использованы дляR5 |
| Ranged negatives | Protected/foreign/instance/Z/weight/capacity, exact pickup, consumed herbs и yield continuation сохранены; отрицательные проверки прежнего R2 native episode подтверждены, соответствующие production файлы не менялись |
| Earned-only restock | Final F0: TEST Fighter19/NG gear/16shots/20pots/Adena0, stock mapped20521; exact first earned kill EXP+705/SP+27, stock16→4; далее наблюдались EXP675596→677317/SP67 и native путь обучения, затем смерть через111s. Покупка и next boosted action не доказаны. Старый L4 episode:17kills, stock16→0 и fallback, но cash323<reserve500; paid receipt отсутствует |
| Scavenger linked | Native-only, actual54/L20/254:2/42:1/239:1, без Crystallize; stock20059/spoil table и mapped anchor, отдельный town. Первый input был без брони; исправленный имел stock69/24/31,0Adena,20pots,79%slots. После1–2 kills восстановление/смерть, earned Sweep/material/deposit/return не доказаны. Не доказана общая ошибка range40; chance/AI/HP и guards не менялись |
| Artisan / native commerce | Final F0:3/3 PASS. Законный TEST Artisan56/L20/Crystallize1: surplus69×1→1458×915, exact sale/deposit/withdraw/crystal receipts/idempotency и negatives. Это отдельная профессия, не доказательство linked Scavenger |

Native деньги/уровни/gear/skills/pots, заданные до измерения, всюду обозначены TEST и не считаются заработанной карьерой. Никаких ресурсов, HP/MP, XYZ или drop chances в ходе игровых замеров не исправлялось. Исключение владельца для ускорения stock применяется только к отдельным R1 threshold TEST-фазам.

## Population pipeline и ordinary Main

Проверена каноническая последовательность Manager planNew(clock) → atomic shell → register/attachNew ecology assignment → bootstrap → initial catchup → ordinary activity. Ручные helpers первых49/50/51 обходили assign/attach, создавая некорректную историю для Main. Их native_context.required и goal/revision conflict не лечились production послаблением. Helpers удалены из исходников; byte preimages и SHA сохранены вне checkout, невалидные собственные записи оставлены fail-closed. Никаких cursor/revision resets, clones, Ecology disable или «clock fix» нет.

Затем Manager штатно создал52..60 при target60 в собственной release-схеме. SyntheticOFF, пассивный Task066View/access0/L1; клиента, GM и human controller нет.

- EFE code / E892 JAR: canonical Mage57, class10,48kills/rewards,49damage, BSS1000→950; обычная native жизнь, EXP/SP/Adena и pickup. Actor53, class49 с ещё отсутствующим damage spell, законно использует SS1835: после sameDB/sameJAR restart16kills/rewards,122damage,1000→920. Выбор физического усилителя здесь основан на реальных навыках.
- На STOP→START exact snapshot hashes identities/items/ledger/receipts/creation совпали. Все60 identities и original48 identity fields сохранились. После возобновления у53 появился единственный первый debit1000; прежний57 debit1000 не повторён. Один equipped1121 перешёл в INVENTORY, objectId/item/count/enchant сохранены; latest receipts6/33 заменены новыми законными BUY с точными money deltas, старые receipts сохранены в preimage snapshots.
- Final F0 / 8B9C JAR: первый180s замер у прежней точки не показал выбранного53. По admission53 SLEEPING;52/57 ecology_fenced;59 ACTIVE/admitted в BACKGROUND вдали. Перед restart перемещена **только пассивная observer point** к фактической committed позиции59; phantom/item/skill/goal/cursor writes0, preimage+SHA сохранены. Расписания и Ecology не изменялись.
- Final sameDB/sameJAR restart: Elf59/native class18, object268484836, epoch272553224446300;20kills/rewards,60damage, EXP+580/SP+40, SS1000→939, Adena261. Owner healthy/OPEN, настоящий World Player, AutoPlay/AutoUse зарегистрированы. STOP PID39820/52032 штатный. FINAL_RESTART_CONSERVATION066R2.json: все60 identity keys и legacy48 fields сохранены;194 equipped objects без изменений; receipts без изменений; ledger изменился только59:0→1000. Между STOP/START identities/items/ledger/receipts/creation exact hashes совпали.
- Важная диагностика: coarse admission59 одновременно сохранял ecology_fenced/preparation_capacity, хотя live native owner уже работал. Read-only review установил: preparationCandidatesLocked исключает live owner, dueSnapshotLocked метит отсутствие в admittedPreparation без такого исключения; horizon cursor отстаёт. Actual physical1/preparation0/waiting0 и native receipts сильнее несогласованной по времени метки. Первоначальный вывод «World0» по этой метке отозван. Guard/diagnostics ради PASS не менялись.
- Пять ограниченных Main epochs вместе заняли около45мин, ниже предела75мин; оба successful restart измерены без reimport. Старые epoch/JAR факты не подменяются final-runtime PASS.

## Сохранность, review и публикация

На02:29UTC final preservation:9 protected files и203 protected +203 copied physical Geo совпали;5 protected DB tables/accounts/characters/items/profiles/components побайтно совпали по canonical row hashes с preflight. Шесть исходных task docs совпали с PACKAGE_SHA256. Own JVM0, ports2147/7847/9147/2247/7947/9247 free; final STATUS обе роли STOPPED без stale records; release/test other_connections0. Native launchers штатно закрыли ThreadPool/Hikari. Force stop не использовался.

Точный final source ZIP скопирован в candidate/source/SOURCE066R2-f0fd6a26.zip с проверкой SHA. Previous releases, source archives, JAR и schemas не удалялись. Новых систем ради fixture не добавлено; reused native runner/owners и bounded TEST helpers. Bounded exception >10 файлов ограничен причинной цепочкой birth entitlement → native writer → supply/SELL/BG guards → focused tests → safe standalone delivery; exact allowlist в CHANGED_FILES-R2.txt.

Проведены read-first/self-review и отдельный read-only reviewer по production diff: P2 equip race воспроизведён и исправлен, duplication/P1 не найден. Отсутствие найденного дефекта не означает доказательства полной связанной жизни. CORE остаётся PARTIAL, пока R2 и R6 не подтверждены.

Финальные отдельные проверки:

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Scope/encoding/diff receipts фиксируются перед report commit. Бюджет отсчитывался с21.10.2026 23:19:39UTC; лимит12ч, freeze10.5ч. Кампания завершена раньше лимита; незакрытое не маскируется дополнительным soak. Следующий bounded разбор: реальная боевая готовность и безопасный выход на существующие service journeys при нулевом кошельке; затем связанный Spoil/material/bank/return на подготовленном native Scavenger. Никаких proposed ослаблений ownership, cursor или chance guards.

Git использовался по прямому /goal и AUTHORITY.md: bounded `rev-parse`, `symbolic-ref`, `status --porcelain --untracked-files=all`, `worktree list`, `ls-remote origin refs/heads/feature/phantom-world`, narrow `diff`/`show`/`log`/`ls-tree`/`merge-base`, `archive --format=zip --output=<unique R2 archive> <code commit>`, exact `add -- <CHANGED_FILES-R2 paths>`, `diff --cached --check`, `commit -m <R2 change>`. Единственная разрешённая публикация — `git push origin HEAD:refs/heads/feature/phantom-world`, затем live `ls-remote`; без force/rebase/merge/reset/clean/stash/других refs. Фактический финальный HEAD фиксируется publication receipt и финальным ответом.

M1/G4/G6, full MASS MARKET, epic generation и полная client experience остаются вне этого scope. Результаты066 и прежних milestones не переписываются.
