# PHANTOM-OFFSCREEN-CANONICAL-TRAVEL-092B

## 26.10.2026 — SUCCESS_092B_STORED_TRANSIT: native binding, canonical recapture, A5 и sameDB restart

**Gate1/A5/restart GREEN.** После сохранённого ниже первого BLOCKED владелец явно разрешил одну адресную correction и один повторный guarded native START. Исправление прошло RED→GREEN и независимый review; повторная кампания на прежней OWN DB и том же actor подтвердила canonical recapture, реальный persisted offscreen arrival и условный второй START только для sameDB idempotence. Все прежние WIP/backups и полные failure logs сохранены. Следующие092C/093/094 не начинались.

### Закрытый blocker и scope correction

Read-only проверка установила source-backed sentinel: Noble `325/1 — Strider Siege Assault`, `00300-00399.xml`, содержит physical `magicLevel=-1`; `nobleSkillTree.xml` включает этот rank в native catalog. Прежний adapter пропускал physical sentinel в строгий `Mechanics`, прерывая catalog exception. После отдельно разрешённой correction отрицательный native magicLevel даёт unsupported-result до constructor; physical0 и MAGIC1245/14 сохраняются. Нормализация native данных, особое правило для class115/skill325 и ослабление strict validator не добавлялись.

Из `mechanics(Skill)` выделена та же private scalar boundary для focused source-backed проверки без инициализации серверной инфраструктуры. RED на прежней семантике этой boundary: compile0/runner1, реальный XML325 вызвал `Unsupported ordinary skill mechanics`; GREEN: unsupported325, physical0, MAGIC1245 и неизменная строгость constructor проверены в существующем runner. Новые `binding-correction-*` имена лишь отделяют явно разрешённый повтор от прежних `.started` receipts; ownership/cleanup/canonical guards сохранены. Независимый review полного WIP22 и correction не оставил открытых Critical/Important замечаний.

### Gate1 — фактический Player и штатный store

OWN `l2jmobiush5_phantom_test_092b`, profile1/object268480990, тот же UUID `cf38a85a-6522-4b07-bfcd-2477d876f0c3`. Fresh preflight/admission подтвердили actor/config/owner/schema/UUID,28815 input entries,203 Geo inputs, отсутствие чужих JVM/connections и неизменный старый payload. Прежние private metadata сохранены до временного lease/input overlay.

`materialization.materialize(... HISTORICAL_BASELINE, exact requestId)` вернул SUCCESS. Реальный World Player: class115, level80, изученный и выбранный `Steal Essence1245/14`; foreground configure получил AutoUse `[1245]`, background capture — MAGIC1245/14. До cleanup на загруженном Player HP3998/4348, MP3785/3985; native initial MP35 + cast MP134. Модель консервативно резервирует177 за cast. Native mechanics: power108, magicLevel74, cast4000/reuse2000ms, drain0.8. Пять исходных equipped object IDs268480991–268480995 с item IDs6579/6383/6384/6385/6386 и enchant0 сохранены; Adena200000.

Обычный `dematerialize`/owned canonical store вернул SUCCESS и READY MAGIC snapshot. После штатного cleanup сохранённые vitals3998/3998 HP,3785/3785 MP,3598/3598 CP; это отдельные фактические post-store scalars, не подмена исходных runtime maxima. Identity, progress, inventory, XYZ и goal до/после recapture совпали. Старый `corrected-baseline.bin` не переписывался, новый snapshot сохранён отдельным `native-binding-baseline.bin`; SQL payload UPDATE вручную не выполнялся.

| Canonical stage | background.state rowVersion | SHA256 payload |
|---|---:|---|
| До recapture |3|`ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176`|
| После owned store |8|`c95cc06ff579ba6844d498bbd78666e4f4a349937fa8f57f1fd4b4cc3bbfa8d9`|
| Committed arrival |62|`ecc3eed956413882ec5901025fa39e55f36e8ca89ff4534afcb5b44a3a900c1b`|
| После sameDB restart/replay |62|тот же arrival SHA|

Старые и новые authority hashes, native-context rowVersions, inventory/Adena, goal и scalars сохранены в `native-proof.json`. После recapture composite guard подтвердил `stored==saved` и `stored.hashes==live.hashes`; progression hash нового native catalog — `EE97B29DBF1EB8EF55E4052A87A041257B9225822DECD277A2BBF6A327548DDF`. Guards не обходились.

### A5 — canonical historical offscreen arrival

Обычный bounded public planner вернул `planner.ready / OFFSCREEN_OUTDOOR_PENDING`, NPC21299. Сохранены32 возвращённые реальные причины отказов: `stock.geometry.unverified`, `stock.geometry.overlap`, `combat_or_current_exclusion`, с точными NPC/anchor IDs в `proof-summary.json` и полном stdout. Target/NPC/координаты в production не зашиты, certified route не синтезирован.

- Source: XYZ24954/50864/-3400, anchor `elven.farming.oren04-2019-02s`.
- Arrival: XYZ180486/-52817/-3264, instance0, anchor `generated.farm.8bcb77c9ec91bdb3ecdf78d6.anchor`.
- Goal: `3065608245847424986`, revision1→2 через canonical `replacePlan`; та же revision2 при arrival/restart.
- Receipt: `02d752c13b6caea5a1be8fe4efa7a26fd8de5e12c831c0b01cae9b1d8bdd36f8`, activityGeneration0, typed tick92009202, expectedAfterHash `f23a9c43ff356cc3cca9375e56ebff90e5d8fccb4876c2166ce47876d1b24a16`.
- Clock: rng15001501, final residualTravel0/residualEncounter0. Зафиксированы27 historical minute intervals, включая uncertain-commit и cancellation старого request lineage; новый путь занял25 положительных минутных budgets, residual1425398→...→45398→0. Полный travel debt1485398ms следует из committed residual; XYZ меняются только в финальном commit. Preview до cleanup имел собственный runSpeed132.84 и не подставлялся вместо post-store attested policy.

Перед исполнением выдержан обычный60s grace. Observer — существующий 092A owner с настоящим World census и topology DI boundary; final verdict ALLOW для исходного и целевого XYZ, inspectedHumans0. Identity contention дал materialization IDENTITY_BUSY/background RETRY без mutation. Отрицательная commit admission на третьем observer call откатила position/state/clock/receipt/cursor; затем reply-loss после реального commit сверился как IDEMPOTENT. Смена request lineage обнулила чужой residual без перемещения; продолжение нового пути дошло до arrival. На всём переходе World Player отсутствовал. Нативный `characters` XYZ, decoded state и опубликованный topology point совпали.

### SameDB restart, postflight и сохранность

Correction START PID48808:02:16:20Z→02:19:52Z, exit0. Только после actual arrival запущен conditional restart PID48448:02:21:11Z→02:23:45Z, exit0. Restart не выполнял повторный recapture/поиск/переход: existing arrival проверен canonical transaction, topology publication и exact historical replay. Payload/rowVersion62, позиция, goal/revision и operation receipt сохранены, replay IDEMPOTENT. `restart-admission.facts.json` и final `after.facts.json` совпали полностью.

EXP3075966164, SP0, level80, identity и полный inventory/Adena200000 неизменны; reward/kill/drop/расходов предметов не было. В before/final dumps9679 строк: заменены только5 строк — `characters` и4 canonical component rows (`background.state`, `background.native-context`, `background.catchup`, `goal.runtime`); added/removed table rows вне этих canonical updates нет. Ручных DB mutations, новой DB/actor и reseed нет. JVM0, foreign connections0, online0, timestamp cleanup tables0. Прежние private owner/config/input manifests и заменённые shadow inputs восстановлены byte-exact; `DatabaseCleanUp=False` сохранён. Проверены7 source protected config/JAR seals, прежние WIP22/compiled5924 backups. Protected078, MAIN/PLAY и OwnerConfig287 не менялись.

### Итоговые gates и публикация

Fresh Java25/Ant JAR compile exit0;409 test sources + current HpDrain compile exit0 (5 прежних warnings),4590 class entries byte-exact в JAR. JAR SHA256 `44581ba46e4d984067572636b452d290092101aaada17df39fbd0e0b7271966c`; это отдельный proof JAR, source `dist/libs` сохранён.

После обоих successful native exits отдельно повторены final combat/offscreen focused, background-model10/10, progression-catalog60/60, combat-viability068 2/2, combat-core55/55, observer092A8/8 — все exit0. Final source OwnerConfig Verify: exit0,68 files/287 overrides/conflicts0/changed0. General `ant verify` с чужими DB routes не запускался. Final exact-scope review: открытых Critical/Important нет.

Proof: `C:/Users/ZBook/.codex/visualizations/2026/10/26/01a175dd-c8c4-7de1-9bad-03604d0d519c/gate092b-correction/`; raw private snapshots/dumps/preimages — `C:/Users/ZBook/AppData/Local/Temp/phantom092b-8f9b8e1b407540a095e9acd0d7c01388/private/native-binding-correction-20261026/`. Соседний `correct092b.py` переиспользует прежний orchestration/snapshot helper. Полные argv/exit codes, source/class/JAR seals, before/after facts, logs обоих successful JVM, final file copies и encoding checks сохранены. Первый failed START и его отчёт ниже остаются историческими свидетельствами; второй START был отдельно разрешён владельцем, не скрытым повтором.

Публикация ограничена22 файлами накопленного WIP (17 tracked изменений +5 новых source/test/report); существующие untracked task packages и чужие файлы не включаются. Пакет TASK092B отдельно проверен:8 payload entries SHA/size совпали. Local HEAD/upstream и live GitHub ref до публикации равны `ac593194...`; проверка live выполнена GitHub compare API, без загрузки/замены WIP. Exact staging/diff/commit/normal push в `feature/phantom-world` выполняются последним шагом; окончательные SHA, exit codes и local/upstream/live equality — в proof `publication.json` и финальном сообщении. Точные использованные Git argv — `git-commands.json`/`publication.json`; разрешение — текущий owner запрос и DELIVERY092B. Force/history rewrite/merge/reset/restore отсутствуют.

- mojibake-маркеры в изменённых файлах проверены отдельно: весь WIP22, результат в `final-verification.json`.
- escaped Cyrillic в изменённых файлах проверены отдельно: весь WIP22, результат в `final-verification.json`.

**Граница результата:** native binding, canonical recapture, persisted offscreen travel и restart/idempotence доказаны. Сам native cast1245, сопротивление/interrupt/paid drain в реальном бою и native kill **NOT_RUN**; model credit не объявляется native-verified. Combat Readiness/Party Preparation, farm rewards и следующие задачи не выполнялись.

Ниже — сохранённая история предыдущих попыток.

## 26.10.2026 — guarded continuation: BLOCKED_NATIVE_CATALOG, recapture/A5 NOT_REACHED

Выполнено продолжение по новому owner-запросу `/goal` на прежних `feature/phantom-world` / `ac5931941f36fbdbb1a64e8d848b5fb57853b74c`. Текущий WIP сохранён; код из GitHub не восстанавливался. Единственный разрешённый native START завершился exit1 **до загрузки actor, skill binding и canonical recapture**. Arrival отсутствует; условный второй START для restart не разрешился и не выполнялся. Commit, push, staging и следующий этап не выполнялись.

### Первая фактическая причина STOP

Полный stdout содержит:

```text
092B_NATIVE_FAIL IllegalArgumentException
java.lang.IllegalArgumentException: Unsupported ordinary skill mechanics.
  PhantomNativeCombatCapability$Mechanics.<init>:30
  PhantomNativeCombatCapability.mechanics:161
  L2jProgressionBackend.copySkills:562
  L2jProgressionBackend.load:144
  PhantomBackgroundSuite$ProductionAuthorityFixture.start:9059
  PhantomOffscreen092BChecks.nativeAction:487
```

Это отказ построения настоящего progression catalog на native skill facts. `copySkills` вызывает `mechanics(skill)` для каждого source-backed referenced rank; адаптер может передать неподдерживаемые scalars в строгий constructor вместо отказа от конкретной неподдерживаемой модели. Исключение прерывает весь catalog. Конкретный skillId/rank и значение отвергнутого поля в exception не записаны: **они не установлены**, предположение не выдаётся за диагноз. До `092B_BINDING_LOAD`, selector, Player capture/store, planner и observer дело не дошло. Это новый Important integration defect, который DB-free scalar/fixture gates не выявили.

В соответствии с owner STOP при новом BLOCKED production после native failure не исправлялся и повторная native-кампания не запускалась. Самый узкий следующий предмет диагностики — граница `mechanics(skill)`/native `copySkills`: получить точный отвергнутый native scalar и проверить безопасный unsupported-result, сохранив строгий validator и обычные Player rules. Это описание оставшегося blocker, а не разрешение следующего запуска.

### Gate 0: сохранность, review и одна адресная correction

Прочитаны root/module AGENTS, root README, релевантные master-plan/workflow/status/release/package contracts, TASK092B/SOURCE_MAP/TEST_PLAN/SAFETY/ARCHITECTURE/ACCEPTANCE и предыдущие отчёты092B/092A, `build.xml`, native lane, materialization/capture/store/catchup и foreground selector. AGENTS выше Git root отсутствуют; ранее отсутствовавшие отдельные code-map/pattern-файлы повторно не искались. Переиспользованы существующие lifecycle/claim/transaction, `HISTORICAL_BASELINE`, foreground configure и private read-only snapshot helpers. Сохранены Java25/Ant, архитектура и scope TASK092B; 22 унаследованных файла — явно разрешённый owner bounded scope.

Все22 текущих файла до правок совпали с `D:/Codex/recovery/PHANTOM-092B-COMBAT-20261026TFINAL/files/` и его manifest. Manifest SHA256: `0b578b163ae364f6dfbb168246dd72ec31379c07a438f2bfd96ddb3beb32be95`. Ещё одна byte-exact копия сделана вне checkout в новом proof `wip22/`. Проверены5924 прежних compiled outputs. До подготовки сохранены прежние private owner/config/input-seals и каждый заменяемый shadow input; старые baseline/backup не подменялись.

Независимый read-only review охватил весь накопленный WIP22, особенно `HpDrain.java` и `AbstractEffect.java`. В этих двух файлах добавлены только read-only metadata getters; тела применения ordinary effects, damage/HP cap/MP/resistance/interruption не изменены. Simulation оплачивает каждый cast и не выдаёт бесплатный drain; это ограниченный model contract. **Фактическое native применение1245, native resistance/interruption и combat parity остаются NOT_RUN.**

Review выявил рассогласование: preparation могла сохранять disabled/неоплачиваемый primary, пока admission/capture выбирали другой ready learned skill; при исчезновении поддержки оставался stale primary. RED на сохранённых старых production classes: focused test compile exit0, runner exit1 с `AutoUse preparation must select the same ready fallback as capture before retaining a recovery intent.` Исправление: preparation сначала использует настоящий ready resolver, recovery intent сохраняется после исчерпания ready alternatives; primary0 удаляет stale damage IDs с сохранением profession utility и native cursor. GREEN на свежих classes — exit0; reviewer повторно проверил correction. До native открытых Critical/Important замечаний review не осталось; обнаруженный runtime catalog blocker выше этим review не покрыт.

### Реальные проверки и bindings

Proof root: `C:/Users/ZBook/.codex/visualizations/2026/10/26/01a175dd-c8c4-7de1-9bad-03604d0d519c/gate092b/`. Полные argv/cwd/exit codes находятся в `*.process.json`; source/class/JAR SHA — в `build-seal.json`.

| Проверка | Фактический результат |
|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -File <module>/tools/OwnerConfig.ps1 -Action Verify` | exit0, files68 / overrides287 / conflicts0 / changed0 |
| `ant -f <module>/build.xml -Dbuild=<proof>/build -Dlibs=<proof>/runtime-libs jar` | exit0; source `dist/libs` не заменён; JAR только в proof |
| JDK25 `javac @<proof>/tests.args` | exit0,409 test sources + current HpDrain;5 прежних JDK warnings |
| Source/class/JAR binding |4590 production class entries побайтово совпали с JAR; новый source/class manifest; без historical class/JAR fallback |
| Focused combat / offscreen | exit0 / exit0; corrected baseline только read-only decode |
| `PhantomTestLauncher background-model 15001501` | exit0,10/10 |
| `PhantomTestLauncher progression-catalog 20260725001` | exit0,60/60; это DB-free fixture catalog, не успешная native catalog load |
| `PhantomTestLauncher combat-viability068 20260725001` | exit0,2/2 |
| `PhantomTestLauncher combat-core 20260725001` | exit0,55/55 |
| `PhantomOffscreenObservation092AChecks production` | exit0,8/8 |
| Последняя компиляция изменённого binding runner | exit0; CodeSource runtime подтвердил exact fresh production/test output directories |
| Native `binding-a5`, PID47252 | START01:53:38Z → exit1 01:55:53Z; один135s процесс; Gate1 BLOCKED до actor load |

Все Ant-запуски последовательны. Production JAR/config seals не менялись. Полная release-регрессия для публикации NOT_RUN: A5 не GREEN. Native kill, buffs, supply, Combat Readiness/Party Preparation и092C/093/094 не выполнялись.

### Native admission и postflight

Прежняя OWN DB: `l2jmobiush5_phantom_test_092b`, port3308; UUID `cf38a85a-6522-4b07-bfcd-2477d876f0c3`, migration/schema manifest exact. Profile1/object268480990 — единственная исходная identity. Полностью перепроверены28815 input entries и203 Geo inputs; обновлены только13 разрешённых shadow source/test inputs. Перед запуском отсутствовали чужие JVM и connections. Private lease временно продлён только для этого START, безопасный `DatabaseCleanUp=False` overlay сохранён.

Preflight/admission/postflight read-only snapshots и полные dumps сохранены в `C:/Users/ZBook/AppData/Local/Temp/phantom092b-8f9b8e1b407540a095e9acd0d7c01388/private/native-binding-arrival-20261026/`. Полные native stdout/stderr также скопированы в proof root как `binding-a5.stdout.log` и `binding-a5.stderr.log`; orchestration — соседний `gate092b.py`.

После exit: JVM0, foreign connections0, online0. **Все9679 строк own DB совпали**, added0/removed0. Identity, progress, inventory/item object IDs/Adena, XYZ24954/50864/-3400, goal/components/hash/rowVersions неизменны. `background.state`: schema2/rowVersion3/834bytes, SHA256 `ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176`. Old/new payload одинаковы: recapture не состоялся. Private owner/Database/IdManager/input manifests и заменённые shadow inputs восстановлены byte-exact из preimages; cleanup остаётся False. Проверены7 source config/JAR protected seals и сохранность прежних WIP22/compiled5924 backups.

Новая DB/actor не создавались; SQL payload UPDATE, reseed, reward/grant отсутствуют. MAIN/PLAY, protected078, OwnerConfig287 и реальные PLAYER rules не менялись. Native binding, реальная HP/MP/equipment/skill-cost аттестация, canonical recapture, bounded farm search, observer admission, travel receipt/arrival/restart — **NOT_REACHED**.

### Точный scope продолжения и доставка

В этом продолжении изменены только:

- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomNativeCombatCapability.java` — ready-first preparation, допустимый пустой damage intent;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java` — очистка stale primary с сохранением utility;
- `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreen092BChecks.java` — focused regression и guarded same-actor binding/recapture/restart route, новые facts/rowVersion receipts;
- этот отчёт.

Остальные18 файлов исходного WIP22 сохранены. Final exact-scope/file/encoding receipts и новая копия22 файлов находятся в proof `final-files/`, `final-verification.json`. Git использовался только для разрешённых read-only branch/baseline/scope/diff/whitespace checks; точные argv, включая reviewer inventory, сохранены в proof `git-commands.json`. Git write-команд нет. **Commit/push NOT_RUN по явному owner STOP; неподтверждённый production не опубликован.**

- mojibake-маркеры в изменённых файлах проверены отдельно: весь WIP22, результат в `final-verification.json`.
- escaped Cyrillic в изменённых файлах проверены отдельно: весь WIP22, результат в `final-verification.json`.

Ниже сохранена история предыдущих этапов без переоценки их доказательств.

## 26.10.2026 — native skill contract: DB_FREE_GREEN, A5_REQUIRED

Продолжена существующая TASK092B на `feature/phantom-world`, HEAD `ac5931941f36fbdbb1a64e8d848b5fb57853b74c`. Владелец отдельно подтвердил bounded scope13 и разрешил DB-free compile/tests. Native/DB JVM, A5, arrival и restart в этом этапе **NOT_RUN**; commit/push не выполнялись. Унаследованный WIP14 и все прежние backups сохранены.

**Доказанная причина:** отсутствие damage rule в progression catalog ошибочно становилось отсутствием способности изучившего native skill Phantom; capture выбирал MELEE0, а foreground имел отдельный AutoUse selector. Исправлены обе стороны одним `PhantomNativeCombatCapability`, без правил по class115, NPC, координатам или region.

### Реализованный контракт и решения

- Resolver просматривает bounded набор actual learned ranks, предпочитает допустимый native AutoUse primary, затем native magicLevel/power со стабильным ID tie-break. Запись curated catalog сама по себе больше не разрешает неподдерживаемую ordinary механику; прежняя summon ветка сохранена. Это уточнение первоначального дизайна устраняет два разных владельца выбора. Catalog остаётся hash-bound authority для механики и rank.
- Поддерживаются plain single-target PvE `MagicalDamage`, `HpDrain`, `PhysicalDamage`; mixed, conditional, continuous, area, channel, movement, hero/GM/PvP-only, transformation и другие неподдерживаемые эффекты не превращаются в MAGIC. Проверяются actual rank, native conditions, weapon-only restrictions, mute/physical mute, reuse, AutoPlay opt-outs и MP. HP/item/charge/soul-cost skills пока отклоняются: повторный ledger для них не добавлен в persisted codec.
- Native AutoUse получает тот же damage primary. Сохраняются существующие profession Spoil utility и cursor; произвольные debuffs не включаются в аттестованную damage sequence. Подготовительный intent для MP recovery отделён от READY_NOW: намерение не разрешает cast без ресурсов. Реальные игроки и global AutoUse executor не менялись.
- Source-backed native enchant routes/ranks поддерживаемых damage families включаются в progression facts с существующим count bound. Неаттестованный rank безопасно остаётся ordinary fallback и не прерывает native store; он не получает MAGIC/model credit.
- `SkillFact` включает native mechanics, поэтому power/cast/reuse/drain/magicLevel/element входят в существующий progression hash. Simulation fingerprint обновлён и связывает AutoPlay, magic-failure, class damage/reuse и NPC penalty settings. Persisted state/codec/schema не менялись. Старый сохранённый snapshot не мигрируется и не переписывается.
- Модель резервирует MP на **каждый** cast, использует uncompressed cast/reuse time и ограниченное число casts. Drain применяется после survived incoming interval и фактически повреждённого HP цели, с overkill/maxHP cap; будущий drain не спасает уже погибшего caster. Shots расходуются только из имеющегося inventory, опциональны и не получают выдуманного damage bonus.
- Native class damage reductions и level-gap skill penalties уменьшают модель; elemental defense учитывается без положительного attribute credit. Низкий skill magicLevel и неаттестованный magic-success resistance приводят к отказу. Actor weapon attribute и неподдерживаемые actor scalar modifiers консервативно исключены из этого cast envelope. Это ограниченная deterministic approximation, не обещание retail RNG/crit/reflect parity.
- FARM target scalars теперь берутся из той же detached native stat projection, что и planning, с сохранением rewards/drops. World admission, Zone/Geo/observer и native reward/level-band guards не ослаблены.

Read-first pass: AGENTS/workflow/status, текущие planner/authority/state/model и WIP diff; foreground AutoUse/engagement, native Skill/Creature/Formulas/HPDrain, progression catalog/backend. Переиспользованы `PhantomCombatSkillSafety`, native projection, существующий immutable mechanics hash, MP recovery, `PhantomNativeShotControl`, support-effect и party owners. Отсутствовавшие ранее code-map/pattern-файлы повторно не искались.

### RED → GREEN и реальные scalars

RED: после DB-free извлечения прежнего «preferred/catalog IDs only» отбора compile exit0, runner exit1: `A learned supported native attack must survive an absent curated damage rule.` Это focused behavioural seam, не утверждение о запуске старого private native capture.

На byte-exact corrected baseline SHA256 `ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176` проверена pure projection нового действия; persisted bytes не изменялись. Native magicLevel74/power108/cast4000/reuse2000/drain0.8 и MP142+35 читаются из штатного XML1245/14. При сохранённых combat/vitals, neutral/no-shot/no-buff binding:

| Projection | Результат |
|---|---|
| Proposed selected mode/skill | MAGIC, actual learned1245/14 |
| Target | NPC21513/level70, реальный stock source Rune/SwampOfScreams.xml |
| Damage / casts / MP | 362 /11 /1947 |
| Duration / incoming | 53669ms /5496.140133HP |
| Paid actual-HP drain / remaining HP | 3089.6 /1591.459867 |
| Та же модель без drain | predicted death, refusal |

Выбор этого NPC не зашит в production. Он является конкретным source-backed численным witness; native target/Zone/Geo/observer admission и фактическая class/weapon/condition binding после recapture ещё не исполнялись. Поэтому это **не A5 GREEN и не live combat receipt**. Старый сохранённый MELEE snapshot продолжает означать MELEE до обычного recapture.

Focused groups покрывают fallback/next action/actual rank, physical и magic mechanics на уровнях1/20/40/60/80/85, MP/HP/item/charge/soul/reuse/weapon refusal, old magicLevel, resistance/reduced damage, no preheal/resurrection, paid batch debit, optional/no shots и bounded limits. Независимый read-only review выявил пять важных интеграционных случаев: magicLevel, Spoil, AutoUse preference, mute/weapon restriction, enchanted rank. По ним внесена одна адресная correction; повторные native proof claims не добавлялись.

Последние завершённые результаты до final seal: production compile exit0,409 test sources + HpDrain compile exit0 (5 прежних JDK warnings), focused combat3 groups exit0; model10/10, progression60/60, viability2/2, combat core55/55, прежний focused092B exit0. Финальный replay/seal фиксируется ниже.

### Combat Readiness: текущее и отдельный этап

Уже есть native learned attack choice/cost/reuse/weapon admission; только owned shots через `PhantomNativeShotControl`; существующее resource pause/native rest; native self-buffs через AutoUse, effect-lifetime authority и party support actions. Prospective buffs/shots, бесплатные HP/MP/items/EXP/SP не добавлены. Активные непассивные эффекты по-прежнему исключают ordinary background reward context: бессрочное использование истёкших buffs не разрешено.

Можно переиспользовать `L2jPhantomSupplyJourney`/`PhantomAutonomousSupplyPolicy`, commerce receipts, native equipment/learning owners, `PhantomSupportEffectAuthority`, `PhantomPartyTactics` и native party backend. Они существуют, но эта правка не доказывает их end-to-end готовность для текущего actor.

Отдельный продуктовый этап: объединить их в Combat Readiness policy перед farm/PvP/PK/raid/siege; определить достаточные запасы shots/consumables, восстановление и основные доступные buffs, lawful supply, support composition и явное решение о высокоуровневом риске. Бой без shots остаётся физически возможным; для высоких уровней он не объявлен нормальной полной подготовкой. Экономика, автоматическая баф-цепочка и формирование групп сейчас не реализовывались.

### Безопасный следующий gate

После отдельной приёмки: fresh OWN identity/actor/connection/config/source/class preflight и snapshots → safe TEST overlay/lease по разрешённому owner scope → штатная native preparation и проверка выбранного exact rank/cost/conditions → canonical store/recapture с текущей authority, без ручного обновления hashes/payload → сравнение identity/XYZ/EXP/SP/Adena/items/goal/revision → обе фазы original guard и одна отдельно разрешённая A5. Active buffs нельзя переносить в background без attested lifetime contract; при unsupported context нужно штатно ждать/оставаться native, а не игнорировать guard. Arrival/restart остаются открытыми.

Raw receipts: `C:/Users/ZBook/.codex/visualizations/2026/10/25/01a17556-f85b-78e2-ae46-69fc2e63dc5e/combat-capability-092b/`. Здесь сохранены stdout/stderr RED, build, tests и regressions. Неудачные подготовительные команды также оставлены: исправленный import, sandbox javac classpath visibility (тот же explicit classpath проходит elevated), wrong-seed background-model refusal (правильный штатный seed15001501 затем PASS). Это не SQL/native failure и не дополнительная native campaign.

До правок: WIP14 snapshot `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261026T003251934768Z`, manifest SHA256 `202d23e6c9e980419806747096cf1fc9fcd2a40f6b370a6319378b350712ab88`;7 дополнительных исходников сохранены byte-exact в его `combat-scope-before/`. Final snapshot/seals, exact scope, encoding и protected-input verification следуют ниже.

### Финальная проверка этого этапа и STOP

Последний source slice собран `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml -Dbuild=<proof>/green-build compile`, exit0. Все409 существующих test sources и изменённый native HpDrain handler скомпилированы JDK25 через exact `green-tests.args`, exit0; classpath содержит только текущие `green-build/bin`, текущие `green-tests`, test resources и четыре dependency JAR, без старого GameServer.jar или historical test fallback.

`sealed-combat.stdout.log`: focused3 groups PASS на unchanged corrected baseline; `sealed-regressions.receipt.json`: background-model10/10 (seed15001501), progression-catalog60/60, combat-viability068 2/2, combat-core55/55 (seed20260725001), offscreen-focused PASS с прежними candidate/zone/observer/travel contracts. Все exit0. Это последние проверки после correction, включая old-magic-level starvation fence и canonical read-only `checkDoCastConditions`; подготовительный intent по-прежнему не выдаёт ресурсы и не разрешает cast.

`file-verification.json`: scope13; original WIP backups14 и дополнительные7 byte-exact,9 унаследованных WIP-файлов вне нового scope не изменены;7 protected config/JAR inputs и4695 прежних compiled outputs совпали;2796 исходных source seal entries проверены на изменения вне утверждённого scope и сохранённого WIP. OwnerConfig287 и production JAR не обновлялись. Private owner/expired lease, Database.ini и IdManager overlay совпали с A5 preimages, DatabaseCleanUp=False. Payload SHA прежний. SQL не выполнялся;9679-row census в этом этапе не повторялся и не переобъявляется новым postflight.

- mojibake-маркеры в изменённых файлах проверены:13 файлов, PASS.
- escaped Cyrillic в изменённых файлах проверены отдельно:13 файлов, PASS.

Git использован только для разрешённых read-only HEAD/branch/scope checks. Точные основные команды с развёрнутыми путями сохранены в `<proof>/git-commands.txt`, exact whitespace invocation и exit0 — в `scope-whitespace.receipt.json`. Это `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, exact-path `git diff --stat`, `git diff --check`; в последней проверке задан command-local `cr-at-eol`, без изменения Git config и без нормализации существующего CRLF. Обычный check до этого считал CR в существующем формате trailing whitespace; реальные whitespace checks сохранены. Предварительный существующий `capture_worktree.py` также выполнял разрешённые scoped binary/cached diff и ls-files для snapshot; исходный helper и receipts сохранены. Commit/push/stage/reset/restore не было.

Финальный byte-exact backup всех22 файлов union(WIP14, scope13), manifest исходников и fresh compiled output seals: `D:/Codex/recovery/PHANTOM-092B-COMBAT-20261026TFINAL/`. Старые backups остаются на месте. Финальный manifest SHA и файловые counts выведены в `<proof>/file-verification.json`. Остановка на owner gate: **A5/arrival/restart REQUIRED/NOT_RUN**, native/DB JVM не запускались, дальнейший продуктовый этап не начат.

## 26.10.2026 — предложение общего native combat capability исправления, DESIGN_REQUIRED

Владелец подтвердил, что отсутствие curated damage rule не означает отсутствие атакующей способности у изучившего native skill Phantom. Новый scope — общий выбор поддерживаемых learned skills и согласование foreground/background, а не специальный rule115. DB-free compile/tests отдельно разрешены; native/DB JVM и A5/arrival/restart остаются запрещены до отдельной приёмки. Production ещё не изменён; RED/GREEN пока NOT_RUN.

### Подтверждённый read-first

`capability()` в background выбирает только progression rules и может перейти в `MELEE(0)` при реально изученном1245/14. Foreground `configure()` независимо наполняет AutoUse всеми отрицательными active skills, а `offensiveSkill()` выбирает через cursor. Переиспользуемые аналоги: `PhantomCombatSkillSafety` (ordinary one-target PvE skill envelope), `L2jCombatBackend.nativePvpSkillReady` (стоимость/reuse/conditions), `PhantomNativeShotControl` (собственные предметы/native recharge), существующие self-buff/party support owners.

Прочитаны оба selection path, `Skill.isDamage/isMagic/getPower`, native `Creature` casting/reuse, `HpDrain`, magic damage formula, model resource/death path, progression SkillFact/builder, native context и nearby tests. Native `Skill.isDamage()` включает `HP_DRAIN`.1245/14: power108, base MP35+142=177, hitTime4000ms, reuse2000ms, effect `HpDrain.power=0.8`. Handler лечит только0.8 фактически повреждённого HP (с CP/overkill/maxHP ограничениями); это оплачиваемый cast, не постоянная регенерация.

Текущий background model использует generic offense/cycles, списывает `skillMpPerEncounter` один раз на encounter и не имеет drain-per-cast mechanics. Простое присвоение MAGIC или credit всего будущего drain до первого входящего удара небезопасно. В native handler ratio private; штатного read-only getter пока нет. Поэтому fallback selection и model support должны быть одним согласованным исправлением.

### Предлагаемый подход

Один общий Phantom-only native capability resolver: curated rule задаёт предпочтение, затем bounded scan реально изученных native ranks. Он возвращает только доказанно поддерживаемый action либо ordinary attack с явной причиной. Учитываются active/passive/toggle, damage/effect type, single-target PvE, rank, transformation/hero/GM/PvP-only, MP/HP/item/charge/soul reserve, reuse, weapon/native conditions и AutoPlay opt-outs. Foreground AutoUse primary и background capture используют один результат. Изменений global AutoUse/реальных игроков нет.

Для модели передаётся immutable, versioned/hash-bound mechanics из native skill/effect facts: type, power, cast/reuse cycle, фактическая стоимость, supported drain fraction. Число casts, MP и items считаются на каждый cast; drain — после фактически нанесённого HP damage с проверкой выживания до очередного cast и maxHP cap. Unsupported mixed/conditional effects не превращаются в MAGIC. Обычная атака остаётся допустимой без shots.

Skill mechanics включаются в существующий progression mechanics hash; `FarmInput`/`BatchRequest` передают временный descriptor. Persisted `background.state` codec/schema и существующий loadout layout не меняются. Старый snapshot не переписывается и не объявляется совместимым с новой механикой: для него нужен штатный canonical recapture после проверки новой runtime binding.

Buffs применяются только существующими self/party native owners с реальным расходом; prospective buffs в estimates не кредитуются. Действующие active-effect lifetime/native-context guards сохраняются, background buff-expiry model не добавляется. Shots — только owned/paid supply через текущий shot control; без выдуманного charge/stack. Drain getter не меняет execution native handler.

Альтернатива с новыми ручными class rules не закрывает общую причину. Отдельный foreground selector сохранил бы рассогласование. Поэтому предлагается именно общий resolver с механикой, общей для admission и simulation.

### Bounded exception: 13 файлов, один combat contract

1. Новый `java/.../phantoms/combat/PhantomNativeCombatCapability.java` — общий resolver/immutable facts.
2. `java/.../phantoms/actions/PhantomEngagementPlanner.java` — выбор того же native action.
3. `java/.../phantoms/background/PhantomVisibleAutoPlay.java` — согласование AutoUse/shot mode.
4. `java/.../phantoms/background/L2jPhantomBackgroundAuthority.java` — fallback/capture/native binding.
5. `java/.../phantoms/background/PhantomBackgroundAuthority.java` — временные mechanics в PlanningCombat/FarmInput.
6. `java/.../phantoms/background/PhantomBackgroundModel.java` — поддерживаемые paid casts/drain.
7. `java/.../phantoms/background/PhantomBackgroundService.java` — передача descriptor, без нового owner.
8. `java/.../phantoms/progression/PhantomProgressionModel.java` — native mechanics facts с совместимыми constructors.
9. `java/.../phantoms/progression/L2jProgressionBackend.java` — native mechanics/hash population.
10. `java/.../model/effects/AbstractEffect.java` — read-only metadata seam для drain, default0.
11. `dist/game/data/scripts/handlers/skill/effects/HpDrain.java` — expose native ratio, cast execution без изменений.
12. Существующий `test/java/.../tests/phantoms/PhantomOffscreen092BChecks.java` — focused RED→GREEN группы, без нового test project.
13. Этот отчёт — commands/evidence/recapture order.

`java/...` здесь означает существующий `java/org/l2jmobius/gameserver/`; это предварительный exact file scope, а не разрешение broad edits. Если нужен выход за эти13 путей, работа останавливается на scope review. Из унаследованного WIP14 ничего не удаляется/не откатывается; новые пути резервируются только под этот contract.

### Исполнение после подтверждения scope

Сначала focused RED на learned damage без curated rule и тот же selected rank в foreground/background; controls: physical/magic, стоимость/MP/HP/reuse/weapon rejection, unsupported effects, optional/no shots и drain без pre-heal/death resurrection. Затем минимальная correction и GREEN в существующем DB-free runner. Проверки разных классов/уровней строятся на механике, без class/NPC/XYZ whitelist. Build и ближайшие regressions — DB-free; native infrastructure не запускается.

Для Dominator ожидаемый кандидат selection — known1245/14 (`MAGIC`, `HP_DRAIN`), если фактические native conditions/reuse разрешают. Это **ожидание для regression, не уже полученный GREEN**. Новые encounter estimates/outdoor suitability публикуются только после проверки новой модели; free shots/buffs в них0. Canonical recapture описывается отдельно: stopped OWN preflight → штатная native preparation/при необходимости paid supply → ordinary store с current skill/cost facts → сравнение identity/progress/items/authority binding → planner → отдельное разрешение A5. Сейчас ни один из этих native шагов не выполняется.

Перед подготовкой сохранён WIP14 snapshot `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261026T003251934768Z`, manifest SHA256 `202d23e6c9e980419806747096cf1fc9fcd2a40f6b370a6319378b350712ab88`. HEAD/branch прежние. На момент предложения изменён только отчёт; production/test/core/effect handler ещё не правились.

## 26.10.2026 — causal audit: BLOCKED_STORED_MELEE_CAPABILITY, не исчерпание candidate budget

**Attribution:** canonical snapshot profile1/object268480990 содержит полноценные HP/MP, но только обычный **MELEE** без skill/shot/summon. Для обычных source-backed целей текущих tiers эта сохранённая атака слишком слаба по действующему `PhantomBackgroundModel.viableEncounter`. Воспроизводимый source replay не нашёл ни одной одновременно жизнеспособной, дающей EXP/SP и имеющей static spawn source цели в диапазоне70–82. Лимит native proofs256 не исчерпан: до native outdoor proof дело не дошло. Исправление программной ошибки фильтрации/лимитов **не обосновано**; viability не ослаблялась.

Это **read-only causal audit**, без JVM, SQL и исполнения native infrastructure. Числа target estimates ниже восстановлены из неизменённых XML, штатных base-stat/level/passive formulas и сохранённого CombatFacts; они явно обозначены как **static source replay, не новый native loader/parity proof**. Это не доказательство невозможности живого боя с другим законным набором skills/buffs/gear.

### Реальный input, а не предположение о классе

`corrected-baseline.bin`834bytes, format2/schema2, SHA256 `ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176`. Decode выполнен по фактическому codec; для CombatFacts прочитаны15 double scalars, а не16. Identity1/268480990/classIndex0/class115/race3; level80; anchor `elven.farming.oren04-2019-02s`, XYZ24954,50864,-3400.

| Сохранённое поле | Значение |
|---|---|
| current/maximum HP | 3998 /3998 |
| current/maximum MP | 3785 /3785 |
| current/maximum CP | 3598 /3598 |
| modelKind | MELEE |
| physical / magic offense | 288.99 /509.86383175 |
| physical / magic defense | 613.47 /103.2421 |
| attack / cast speed | 394 /273 |
| HP / MP regen в секунду | 6.407353333333333 /2.76991 |
| selectedSkill / skillLevel / summon | 0 /0 /0 |
| skillMpPerEncounter | 0 |
| shot/item count / summon resource/count | 0 /0 /0 /0 |

`PlanningCombat.from(state)` берёт именно эти combat/vitals/cost. MELEE использует P.Atk и attackSpeed; более высокий M.Atk сам по себе не включает MAGIC. MP deficit и currentHp<=0 отсутствуют. `estimateEncounter` не кредитует регенерацию, будущие consumables или shots; CP не является запасом HP для обычного NPC урона. Проверяются исходная длительность <=120000ms и incomingDamage<3998, без simulation compression до20s.

В сохранённом SQL dump `character_skills` есть Steal Essence1245/14 и Blaze Quake1092/19. Но это не selected skill данного snapshot. В accepted knowledge для115 указан `combat.debuff`1367/1, а supported Background capture допускает damage capabilities; без такой capability `capability()` явно возвращает `MELEE/Loadout.none()`. Это существующий контракт, не скрытая нехватка MP и не разрешение молча моделировать известный spell/HP drain. Наличие изученного spell не доказывает готовность его Background simulation contract или AutoUse selection.

### Разложение причин и census

Прочитаны текущие `PhantomHistoricalBackgroundPlanner`, `PhantomBackgroundAuthority`, `L2jPhantomBackgroundAuthority`, `PhantomBackgroundModel`, codec/state, query/backend, relevant WIP diff и hunk inventory14-file snapshot. Model в WIP не изменён. Важные локальные аналоги — существующий `PlanningCombat`, `estimateEncounter`, native stat projection и curated capability contracts. Ранее отсутствующие AGENTS/code-map/pattern-файлы повторно не искались.

Для original `replaceFromState` sets `excludedTargets` и `excludedSteps` пусты. Отдельного запрета текущего anchor в этом вызове нет. В сохранённом логе `npc@` перед двоеточием имеет пустой anchor: это ранний `addCandidates` → `viableTarget` **до обхода source areas/anchors**, а не current location/Geo rejection. Native template absence/unsupported monster не объясняют эти32 записи: query использует тот же NpcData, exact types Monster/RiftInvader. Их статические estimates действительно не проходят combat predicate.

| Tier для level80 | Query range | NPC в полном query | Pages (64, затем256) |
|---|---|---:|---:|
| radius2 | 78–82 | 993 | 5 |
| radius5 | 75–82 | 1188 | 6 |
| radius10 | 70–82 | 1429 | 7 |

Всего **1429 уникальных NPC templates**,3610 посещений через перекрывающиеся tiers; viability memoized по npcId. Все queries помещаются в существующие8 pages. Это counts, восстановленные по sealed exact-type templates/query, **не выдуманный ранее не записанный runtime counter**. Первоначальный упрощённый replay ошибочно включал наследников Monster и пропускал custom template без attack-блока; финальный census использует фактический `NpcTemplate.isType` (exact string), CustomNpcData=True и default stats, включая900102. Промежуточные результаты сохранены отдельно и не являются финальными counts.

Из1429:1419 имеют `predicted_death`,1246 — `encounter_timeout`;1245 имеют оба,174 — только смерть,1 — только timeout. Остальные9 численно viable:6 без EXP/SP (18256,18344,18601,18602,18693,18917),3 с EXP/SP (18809,18810,21766), но у последних нет ни одной записи npc в `data/spawns` и source groups в текущем inventory. Шесть без награды не проходят `sourceBacked` reward predicate; наличие Ancient Egg в static spawn не превращает его в полезный farming target.

**Native outdoor proof attempts0** восстанавливается по причинному пути: полезных viable static sources нет; любой фактический native/budget denial вытеснил бы предварительную запись в capped diagnostics, но все32 остались ранними combat rejections. Лимиты256 native proofs и cap32 diagnostics — разные ограничения.1429 — число уникальных queried NPC, не число NPC@anchor pairs и не размер capped лога. Из исторического лога нельзя восстановить точный runtime census NPC@anchor pairs; он отдельно не записывался.

### Конкретные цели: source replay штатной оценки

| NPC / level | Source | Target HP / P.Atk / P.Def | Time ms / incoming HP | Отказ |
|---|---|---|---|---|
| 21513 Needle Stakato /70 | Rune/SwampOfScreams.xml | 3862 /1312.704 /398.6925 | 84574 /8661.062 | predicted death |
| 21514 Worker /71 | тот же source | 3938 /1499.52 /436.8 | 92828 /10859.223 | predicted death |
| 21515 Soldier /72 | тот же source | 4013 /1708.87332 /407.5715 | 89445 /11924.315 | predicted death |
| 21516 Drone /73 | тот же source | 4086 /1461.888 /426.87 | 94535 /10781.377 | predicted death |
| 22196 Velociraptor /80 | Rune/PrimevalIsle.xml | 5198 /2163.876 /1794.3077295 | 432392 /72992.377 | timeout + death |
| 18809/18810 /82 | static source отсутствует | 2878 /474.50448 /596.79 | 88061 /3259.803 | combat viable, source/anchor отсутствует |
| 21766 /75 | static source отсутствует | 1057 /1187.56992 /695.8028 | 36938 /3422.158 | combat viable, source/anchor отсутствует |

Для первых5 anchor **не выбран**: rejection происходит раньше anchor selection. Несколько sources/groups одного NPC и все target scalars сохранены в полном audit JSON; не назначался фиктивный anchor. Известный generated outdoor anchor из старого отчёта не является whitelist и не обходит combat predicate. Все приведённые NPC используют physical incoming в текущей модели. Например, для21513 при прочих прежних фактах потребовалось бы P.Atk строго выше примерно626.053 вместо288.99, чтобы пройти только death predicate; это численный порог, **не выданные характеристики** и не утверждение, что конкретная покупка его обеспечит.

### Минимальный продуктовый путь и границы результата

Для **текущего сохранённого режима** безопасной обычной source-backed farming цели в существующих tiers не подтверждено. HP/MP уже полные, поэтому ожидание регенерации не исправит этот input. Подмена melee на MAGIC, бесплатные shots/stats, compression в viability или произвольное расширение level tiers были бы необоснованным ослаблением guard.

Безопасный existing fallback сейчас — historical idle и законная native preparation: штатная materialization, native AutoUse/skill preparation в пределах поддерживаемого capability contract и/или оплачиваемое снабжение/equipment через `L2jPhantomSupplyJourney` + `PhantomNativeEquipmentPolicy`, затем обычный canonical store/recapture и новая оценка. `PhantomSystem` уже связывает supply, equipment и learning owners. **Одна supply-покупка не объявлена достаточной:** нужен проверенный фактический combat snapshot. Для offscreen магического режима сначала потребуется доказанный contract для выбранного native action; наличие1245/14 в skills table его не заменяет. Этот audit не исполняет подготовку, не обещает автоматически готовый A5 candidate и не добавляет исключений по class/NPC/level/location.

Native level-gap/reward policy не изменена. В конфигурации `MonsterExpMaxLevelDifference=75`; model требует strict gap меньше configured limit, а native reward kernel сохраняется. Это не основание выбирать произвольно слабых mobs или менять tiers2/5/10: численно viable templates без реального source/reward были отвергнуты отдельно.

**Production correction0. RED→GREEN/build/regressions NOT_RUN**, поскольку новый software defect не подтверждён и задача разрешает сборку/ближайшие регрессии после подтверждённой production-правки. Новых тестов ради диагностики не добавлено. Новый native JVM0, SQL0, lease/config/actor mutation0, commit/push0. A5 **NOT_READY**; не запущен автоматически. Другие classes/levels/zone/Geo/observer/limit policies не менялись.

Evidence: `P/private/combat-current-causal-audit-final/audit.json`, helper `V/task092b_combat_audit.py`; P/V определены ниже. Helper только читает binary/XML/source/logs и пишет собственные receipts; это не новый runtime/catalog engine. Все source group/reward/estimate rows сохранены, 32 actual capped NPC IDs сопоставлены с конкретными combat failures. Ранние упрощённые projections в соседних audit directories сохранены как диагностика и помечены superseded. Нового SQL postflight не было: OWN DB не подключалась; последний9679-row postflight относится к A5 запуску ниже.

До работы: snapshot14 `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261026T000026349318Z`, manifest SHA256 `720e2fb0439706f87779f8867c9be694214b0b449ceebd900f6ad68c9b30b231`. В checkout этого продолжения изменён **только текущий отчёт**. Все13 остальных WIP bytes, старые backups, saved payload, OwnerConfig287 и прежние private A5 receipts сохранены; контрольные SHA находятся в итоговом audit verification receipt.

Git — только разрешённые read-only `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git diff -- <exact planner/authority/model paths>` и scope/snapshot команды `capture_worktree.py` из приведённого ниже PATHS. Exact-path whitespace/status проверки14 WIP также read-only. Staging/commit/push/history commands не выполнялись.

- mojibake-маркеры в изменённых файлах проверены: полный набор27,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: все6 Unicode/XML regex-паттернов,0 совпадений.

## 26.10.2026 — FINAL A5 PRODUCT PROOF: A5 BLOCKED, ORIGINAL GUARD PASS

Выполнена **одна** отдельно разрешённая guarded native-кампания на прежней OWN `l2jmobiush5_phantom_test_092b`, profile1/object268480990. PID35856, 26.10.2026 **01:38:13–01:40:47 Europe/Chisinau** (`2026-10-25T23:38:13.091007Z` → `23:40:47.078466Z`), **exit1**. В одной и той же JVM saved/stored/live quartet совпал сразу после fixture **и непосредственно перед original composite guard после создания/старта сервисов**. Обе части original guard истинны в обеих точках, первый hash mismatch — NONE.

Первая terminal причина текущего запуска:

```text
092B_PUBLIC_PLAN ready=false reason=planner.target_or_route.absent
mode=CERTIFIED generation=null
java.lang.IllegalArgumentException: TASK091_092B_VERIFIED_PUBLIC_PLAN
  at PhantomTask091Lane.require(PhantomTask091Lane.java:31)
  at PhantomOffscreen092BChecks.nativeAction(PhantomOffscreen092BChecks.java:524)
  at PhantomOffscreen092BChecks.main(PhantomOffscreen092BChecks.java:29)
```

Planner вернул все32 доступных в bounded result rejections, каждый `combat_or_current_exclusion`; полный список NPC и причин сохранён **до assertion** в stdout и итоговом receipt. Это capped diagnostic list, не полный census всех рассмотренных источников. По этим агрегированным причинам нельзя честно утверждать отдельную native zone/Geo причину или число фактически рассмотренных альтернатив. Значение `mode=CERTIFIED` при ready=false/generation=null — default результата отказа, не выбранный route и не выполненный travel. Подходящий public outdoor plan не получен. Goal replacement, observer evaluation, canonical offscreen transaction и движение не начаты; operation key остался пустым. `arrival.bin` отсутствует.

**A5 NOT_PROVEN / PARTIAL_092B_SALVAGED.** По требованию STOP при новом отказе production дальше не исправлялся, повторного native/диагностического JVM не было. Условие restart не наступило: второй JVM START не выполнен, `a5-restart.started` отсутствует. Финальная регрессия и commit/push разрешались только после A5 GREEN и поэтому **NOT_RUN**. TASK092C/093/094 не начаты. Исторический hash FAIL не воспроизведён и в исходной поздней точке; это не объяснение его прежней причины и не закрытие A5.

### Подготовка, classpath и scope

Прочитаны актуальные инструкции/status/workflow, recovery TASK, предыдущий diagnostic report/receipts, nativeAction до terminal assertions, оба recorders, exact overlay guard и service/read paths. Паттерны прежние: existing verified/restart path, `recordCompositeGuard`, Task091 OWN checks и SQL snapshot orchestration. Дополнительные отсутствующие AGENTS/code-map/pattern-файлы повторно не искались.

HEAD `ac5931941f36fbdbb1a64e8d848b5fb57853b74c`, ветка `feature/phantom-world`. До правок сохранён byte-exact WIP14: `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261025T233341081871Z`, manifest SHA256 `b26843ca929f1c16a0d8ac9be54db403ff953a24f4b0d1e8151825bd213102c5`. Exact inherited14-file scope сохранён; новые repo-правки этого продолжения — только `PhantomOffscreen092BChecks.java`, `PhantomTask091Lane.java` и этот отчёт. Все production-файлы совпали с прежними source seals после starvation fix; новой production-сборки не требовалось и не выполнялось.

Существующий private stage `P`/production build `R`/orchestration `V` определены ниже. Все новые evidence — `A=P/private/final-a5-product-proof`. Перед изменением lease read-only preflight повторно подтвердил exact task92/schema/UUID/actor/baseline/migration/manifest, private endpoint/config/login binding, payload SHA, source/shadow/Geo и4695 class/JAR seals. Конфликтующих JVM0/foreign OWN connections0. Сохранены полные read-only dumps9679 строк и private preimages. Online0, в обеих timestamp-таблицах0 строк. Семь protected source config/JAR seals совпали; MAIN/PLAY/protected078 не затрагивались.

Все **409 текущих test sources** скомпилированы заново одним javac в `A/classes`, exit0. Native classpath использовал только этот test-каталог, sealed `R/closure-build/bin`, existing resources и dependency JAR. Старые `P/tests` и `R/closure-tests` отсутствовали в classpath. Фактический CodeSource проверен assertion и выведен для authority/planner/transaction/outdoor helper/ZoneManager и test entrypoint/lane/initializer/suite. Смешивания старых test classes не обнаружено.

В test entrypoint добавлены одноразовые aliases `a5-proof`→existing verified и `a5-restart`→existing restart, две фазовые записи quartet, CodeSource assertions и вывод interval/state/outcome перед terminal assertions. **Original composite guard не изменён.** Existing exact cleanup overlay вынесен под test-only internal property `safeCleanupOverlay`: разрешена только точная замена одной строки private `IdManager.ini`, пользовательские файлы не меняются. Штатная инициализация подтвердила `DatabaseCleanUp=False`. Private lease обновлён только после ownership preflight до `2026-10-26T00:22:28Z`.

### Два замера quartet в одном JVM

| Компонент | Saved = stored = live, обе точки |
|---|---|
| knowledge | `b47a63d38acddb21b3ded9614fa24acb3abd0aaf2ea4d49d5578acec094f5cc7` |
| topology | `b59bec011f252ebd6c0609a2a636dd11ea664fe3b2cc2e4be44ed8d45b31a6c6` |
| progression | `C3D056D01376A071786CAF654A8671B507EDFE7849C3D3B7490B413B57B35202` |
| commerce | `1b019bb1415efd06d2102cfd7915ae911b3d8a3352958e0ad66d845c2db50703` |

В обоих receipts: `stored.equals.saved=true`, `stored.hashes.equals.saved.hashes=true`, `stored.hashes.equals.live.hashes=true`, все12 разделов равны. Knowledge/topology generation1; progression/commerce generation не предоставляется публичным API, component/combined hashes сохранены. Фазы явно записаны: `AFTER_PRODUCTION_AUTHORITY_FIXTURE` и `BEFORE_ORIGINAL_COMPOSITE_GUARD`.

### Postflight и сохранность

Полные SQL snapshots до/после/final: **9679 строк →9679, removed0/added0**. Profile/object, XYZ `24954,50864,-3400`, anchor `elven.farming.oren04-2019-02s`, level80/EXP3075966164/SP0, Adena200000 и все6 inventory rows без изменений. Goal3065608245847424986/revision1, clock15001501/0/0, пустой operationKey и все component payloads/rowVersions совпали. `background.state`834bytes/schema2/rowVersion3, SHA256 прежний `ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176`.

Штатные SQL side effects: `UPDATE characters SET online=0` выполнялся при online0, фактических изменений значений0; timestamp cleanup удалил0 строк, широкая очистка отключена. Других сохраняемых SQL-изменений нет по full-row comparison; statement trace не заявляется. SQL exceptions/SEVERE/ERROR не обнаружены; имеется прежний warning об optional `data/buylists/custom`. ThreadPool/Hikari штатно закрыты, postflight JVM0/connections0.

После исполнения **побайтно восстановлены** прежние `private/owner.properties`, `inputs.tsv`, `inputs092b.json` и две прежние shadow test copies. Expiry снова `2026-10-25T16:09:24Z`; безопасный private cleanup overlay False сохранён. Source WIP и новые compiled diagnostic classes/logs сохранены. Подтверждены неизменность11 прежних backup manifests,114 прежних private files,4695 production outputs и7 protected seals. Автоматических исправлений actor/state/authority hashes не было.

Команды этого продолжения: прежний `capture_worktree.py <repo> D:/Codex/recovery` exit0; `python -B V/task092b_live_ops.py a5-preflight`, `a5-prepare` — exit0; `a5-proof` — exit1; `a5-postflight` — exit0. Точная java command/classpath/UTC/exit/stack находятся в `A/a5-proof.process.json`, `a5-proof.stdout.log`, `a5-proof.stderr.log`; оба guard receipts — `P/a5-proof.after-fixture.guard.properties` и `P/a5-proof.guard-diagnostic.properties`; canonical before-planning facts — `P/a5-proof.before-planning.properties`. SQL dumps/facts, preimages, compile args/receipts и `final-verification.json` сохранены в A. Новых DB/users/fixtures/независимых кампаний не создавалось.

Git использовался по разрешению текущей задачи для baseline/scope/backup: `git rev-parse HEAD`; `git symbolic-ref --short HEAD`; `git status --short --untracked-files=normal -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/docs/phantoms`; exact commands неизменённого `capture_worktree.py` перечислены ниже и ограничены его PATHS. Финальные `git -c core.whitespace=cr-at-eol diff --check -- <14 exact PATHS capture>` и `git status --short --untracked-files=normal -- <те же14 PATHS>` выполнялись только для scope/whitespace. Git write/staging/commit/push не выполнялись.

- mojibake-маркеры в изменённых файлах проверены: полный набор27,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: все6 Unicode/XML regex-паттернов,0 совпадений.

**STOP: public plan не получен.** Из этого запуска не следует готовое production-исправление: `combat_or_current_exclusion` — агрегированная причина. Следующее решение требует адресного разбора этого отказа на сохранённых фактах, без автоматического повторного запуска.

## 26.10.2026 — FINAL LIVE AUTHORITY DIAGNOSTIC: MISMATCH_NOT_REPRODUCED, STOP

Выполнен **ровно один** отдельно разрешённый диагностический JVM в существующей OWN `l2jmobiush5_phantom_test_092b`. PID45696, 26.10.2026 01:20:34–01:23:15 Europe/Chisinau (UTC `2026-10-25T23:20:34.281078Z` → `23:23:15.114575Z`), **exit0, exception отсутствует**. После штатной загрузки каталогов и создания действительного `ProductionAuthorityFixture` все четыре live authority hash совпали с saved/stored. Первый несовпавший компонент: **NONE**. Ранее наблюдавшийся отказ `TASK091_092B_VERIFIED_SOURCE_HASHES` в этой диагностической точке **не воспроизвёлся**.

Это завершённая диагностика, не native travel campaign и не SUCCESS092B. A5/arrival/restart/idempotence остаются NOT_PROVEN; следующий этап не начат.

### Read-first, сохранность и admission

Прочитаны root/module AGENTS, README, текущий status/workflow contract, bounded master plan/package standard, TASK092B и recovery TASK, этот отчёт, прежние SQL-only/guard receipts, launcher, `PhantomTask091Lane`, native initializer, `ProductionAuthorityFixture`, authority/hash code и native `IdManager`/`DatabaseIdManager`. Переиспользованы exact OWN guards Task091, существующий SQL-only reader и `recordCompositeGuard`; стек Java25/Ant, существующая схема/actor и original composite guard сохранены. AGENTS в трёх предках checkout и `test/AGENTS.md` не найдены; отдельные code-map/pattern-файлы не найдены. До запуска live hashes оставались непроверенными.

До правок сделан byte-exact WIP snapshot: `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261025T231702316353Z`,14 файлов, manifest SHA256 `1fc4189c2bbdcf8949461aa7707fe84d96477ebc456704e338f3942786780f52`. Private preimages, прежние metadata/logs и TEST-конфиги дополнительно сохранены с SHA256 в `D/preimages`, где далее:

```text
P=C:/Users/ZBook/AppData/Local/Temp/phantom092b-8f9b8e1b407540a095e9acd0d7c01388
R=C:/Users/ZBook/AppData/Local/Temp/phantom092b-recovery-20261025T1356
D=P/private/final-live-authority-diagnostic
M=D:/Codex/repos/L2J-phantom-world/L2J_Mobius_CT_2.6_HighFive
V=C:/Users/ZBook/.codex/visualizations/2026/10/25/01a17556-f85b-78e2-ae46-69fc2e63dc5e
```

Read-only admission подтвердил exact OWN task92/schema/UUID `cf38a85a-6522-4b07-bfcd-2477d876f0c3`, baseline `ac5931941f36fbdbb1a64e8d848b5fb57853b74c`, canonical manifest1/123/223 с прежним aggregate SHA, migration SHA, один profile1/object268480990, private endpoint127.0.0.1:3308/092b, driver/pool4/backup=false/login binding, source/shadow и Geo seals. Конфликтующих Java/javaw0, foreign OWN connections0. Проверены4695 original class/JAR outputs,2796 sealed source inputs и28814 manifest entries +203 Geo; прежнее известное diagnostic-only изменение test092B учтено отдельно. Семь protected source config/JAR seals также совпали; production JAR не заменялись.

До JVM `characters.online=0` у единственного actor. В `character_instance_time` и `character_skills_save` **всего0 строк**, соответственно оба expiration predicate затрагивают0 строк: риска потери timestamp-данных нет. Сохранены полные read-only dumps всех9679 строк OWN schema (`before.all-rows.sql`, `admission.all-rows.sql`) и адресные SQL facts. Использованы существующие MariaDB11.4 clients; DDL/DML в preflight не выполнялись, credentials не выводились.

В **private shadow** установлена единственная точная config-разница `DatabaseCleanUp = True` → `False` в `config/IdManager.ini`. Исходные `OwnerConfig.json` и production-конфиги не менялись. Для этого только в диагностическом режиме test guard проверяет исходный SHA и полное равенство shadow-файла точной замене одной строки; прочие input guards сохранены. Старый private lease истёк: после свежей OWN аттестации временно продлён до `2026-10-26T00:05:07Z`; после JVM возвращён прежний expiry `2026-10-25T16:09:24Z`. UUID/owner/schema/baseline/login не менялись. Перепривязаны только два test source inputs; original build seals не переписывались, диагностические classes помещены отдельно в `D/classes`.

### Фактические authority hashes

| Компонент | Saved = stored = live |
|---|---|
| knowledge | `b47a63d38acddb21b3ded9614fa24acb3abd0aaf2ea4d49d5578acec094f5cc7` |
| topology | `b59bec011f252ebd6c0609a2a636dd11ea664fe3b2cc2e4be44ed8d45b31a6c6` |
| progression | `C3D056D01376A071786CAF654A8671B507EDFE7849C3D3B7490B413B57B35202` |
| commerce | `1b019bb1415efd06d2102cfd7915ae911b3d8a3352958e0ad66d845c2db50703` |

`stored.equals(saved)=true`, `stored.hashes.equals.saved.hashes=true`, **`stored.hashes.equals.live.hashes=true`**; все12 state sections равны. Обе отдельные части original guard истинны. `firstStoredLiveHashDifference=NONE`.

Исходный knowledge combinedHash `d83b84982d1199e55e0fa882bd94d4c80e042fc9bd60efce1bc2ebe1c00c37b4`; topology canonicalHash совпал с topology authority. Travel hash —64 нуля, legs0, fixture использует constructor без explicit travel. Progression/commerce combinedHash совпали с authority; все component hashes сохранены в `guard-diagnostic.properties`. Загруженная loot policy: auto=false, herbs=false, slotLimit=true, itemIds=[]; knowledge/topology generation1. Production CodeSource — прежний `R/closure-build/bin/`, test-only diagnostic classes — `D/classes`.

Вывод сделан **сразу после создания fixture**, до создания background/materialization services и planner. После него выполнены только диагностическое чтение travel catalog hash и goal, затем штатные fixture close, ThreadPool shutdown, Hikari close и exit. Travel query не выполняет перемещения. Terminal marker: `planner=NOT_INVOKED travel=NOT_EXECUTED rewards=0 restart=NOT_RUN`. Полные stdout/stderr сохранены отдельно. Единственный warning — отсутствующая optional `data/buylists/custom`; SQL exceptions/SEVERE/ERROR не обнаружены, ошибки не подавлялись.

### Read-only postflight и SQL side effects

| Поле | До → после |
|---|---|
| profile/object | 1/268480990 → без изменений |
| XYZ | 24954,50864,-3400 → без изменений |
| Level / EXP / SP / expBeforeDeath | 80 /3075966164 /0 /0 → без изменений |
| Adena / inventory | 200000 / все6 полных строк → без изменений |
| background.state | schema2, rowVersion3,834bytes, SHA `ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176` → без изменений |
| goalId / revision | 3065608245847424986 /1 → без изменений; полный goal payload SHA и rowVersion также совпали |
| остальные profile components / ownership / schema metadata | без изменений |
| JVM / OWN foreign connections | 0 /0 после штатного завершения |

Полные SQL row snapshots содержат9679 строк до и после: **removed0, added0**. `UPDATE characters SET online=0` штатно выполнялся: matched1, фактических изменений значений0. `cleanTimestamps()` штатно выполнялся: deleted0; широкая `cleanDatabase()` отключена через private config. Других сохраняемых SQL-изменений не найдено по полному сравнению строк всех таблиц (`sql-row-changes.json`). Персонаж автоматически не исправлялся, saved hashes/payload не переписывались. Snapshot фиксирует конечные строки и не является server SQL statement trace.

После JVM повторно проверены original4695 outputs,7 protected seals,28814 source/shadow inputs с единственным точным cleanup overlay и9 прежних backup manifests. Все preimage copies сохранены. Доступ к MAIN/PLAY/protected078 не выполнялся; новые users/schema/fixtures не создавались.

### Причина, минимальное исправление, команды и STOP

**Конкретного mismatched component в разрешённом диагностическом JVM нет. Источник исторического расхождения не установлен.** Адресно прочитаны `acquisitionSnapshot` → transaction load → codec decode, service start и publisher.ready между fixture creation и старым guard; подтверждённого источника изменения hashes в этом участке не найдено. У старого FAIL нет записанных live values, поэтому нельзя восстановить его компонент по сегодняшнему совпадению. Текущий результат не доказывает, что прежний отказ был именно во второй части composite guard.

**Минимальное обоснованное действие — сохранить diagnostic receipts; production/state исправление не обосновано.** Не ослаблять guard, не переписывать saved hashes и не подгонять production. Дополнительный JVM/native campaign не выполнялся; STOP после этой диагностики. A5 остаётся заблокирован прежним недоказанным результатом, приёмка вручную не объявляется.

В этом продолжении изменены только два test-файла (`PhantomOffscreen092BChecks.java`, `PhantomTask091Lane.java`) и текущий отчёт. Вне checkout сохранён короткий orchestration script `V/task092b_live_ops.py`; он запускает существующие compiler/client/test entrypoint, SQL mutations не выполняет. Новые тестовые проекты, схема, движок или fixture-инфраструктура не создавались.

Фактические команды: `python -B M/docs/phantoms/tasks/PHANTOM-OFFSCREEN-092B-RECOVERY/capture_worktree.py <repo> D:/Codex/recovery` exit0; `python -B V/task092b_live_ops.py preflight`, `prepare`, `launch`, `finalize` — все exit0. `prepare` вызвал `javac -encoding UTF-8 -source 25 -target 25 -cp <existing classes/resources/libs> -d D/classes <два test sources>` exit0. `launch` вызвал **один** `java -Xms128m -Xmx4g -Dfile.encoding=UTF-8` с прежними exact root/UUID/config/native properties, classpath `D/classes;R/closure-build/bin;R/closure-tests;P/tests;<existing resources/libs>` и entrypoint `PhantomOffscreen092BChecks final-live-authority-diagnostic M`, exit0. Full Ant и дополнительные test/native JVM не запускались.

Receipts: `D/process.json`, `stdout.log`, `stderr.log`, `guard-diagnostic.properties`, `before.sql.txt`, `admission.sql.txt`, `after.sql.txt`, три `*.all-rows.sql`, `sql-row-changes.json`, `preservation.json`, `prepared.json`, `final-verification.json`, JVM/config seal receipts. Exception в итоговом diagnostic receipt — null.

Git использовался только по bounded разрешению root AGENTS/recovery TASK: `git status --short --untracked-files=normal`; `git symbolic-ref --short HEAD`; `git diff -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTask091Lane.java`; внутри неизменённого `capture_worktree.py` — `git -C <repo> rev-parse --show-toplevel`, `symbolic-ref --short HEAD`, `rev-parse HEAD`, `diff --binary HEAD -- <exact PATHS capture>`, `diff --cached --binary -- <те же PATHS>`, `status --porcelain=v1 --untracked-files=normal -- <те же PATHS>`, `ls-files --others --exclude-standard -- <те же PATHS>`. Финальные scope/whitespace команды используют только три изменённых в этом продолжении пути. Ветка `feature/phantom-world`; commit/push/staging/history changes не выполнялись.

- mojibake-маркеры в изменённых файлах проверены: полный набор27,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: все6 Unicode/XML regex-паттернов,0 совпадений.

## Исторический срез 25.10.2026 — FINAL NATIVE CLOSURE

25.10.2026, **последний итог: PARTIAL_092B_SALVAGED / A5 BLOCKED — `TASK091_092B_VERIFIED_SOURCE_HASHES`**. Одна отдельно разрешённая FINAL NATIVE CLOSURE на свежем starvation fix завершилась exit1 до planner/goal replacement/offscreen-транзакции. Фактического прибытия нет; sameDB restart NOT_RUN. WIP и все recovery backups сохранены. Commit/push не выполнялись; SUCCESS не объявлен.

## FINAL NATIVE CLOSURE — последнее разрешение владельца и STOP

Владелец разрешил одну дополнительную guarded кампанию после starvation fix: native arrival, затем штатный STOP и новый JVM только для sameDB restart/idempotence того же результата. При FAIL/BLOCKED предписано сохранить диагностику/WIP и остановиться. Разрешение применялось только к прежним OWN TEST092B и actor; ABSENT заменён адресной повторной аттестацией. Новых DB/actor/awards/reseed не было.

Точные пути последнего исполнения:

- `M=D:/Codex/repos/L2J-phantom-world/L2J_Mobius_CT_2.6_HighFive`.
- `P=C:/Users/ZBook/AppData/Local/Temp/phantom092b-8f9b8e1b407540a095e9acd0d7c01388` — прежний собственный native stage.
- `R=C:/Users/ZBook/AppData/Local/Temp/phantom092b-recovery-20261025T1356` — собственные build/tests/evidence.

До изменений сохранён snapshot `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261025T153124265482Z`, manifest SHA256 `9fb9e5eb00b0d247aeda087981c97c55162030038b43bc34419dc5fdca3e13e3`; 14 source/copy SHA проверены. Оба первоначальных и все последующие backups сохранены.

Read-first pass повторно проверил прежний `verified2` PUBLIC_PLAN и текущие параметры: прежний default `mode=CERTIFIED` при ready=false не означал выбранный route; отказ был до canonical writes. Проверены действующие owner/config/input guards, source-hash guard и код загрузки canonical snapshot. Корень starvation и его RED→GREEN описаны в историческом разделе ниже. Ближайшие паттерны остаются прежними: historical canonical transaction, NORMAL-GK priority, 092A full census, Task091 ownership guards. Java25/Ant, format2/compact3, schema и общий scope14 сохранены; новый native результат до запуска оставался непроверенным.

Подготовка этого разрешения затронула только существующие planner/test/launcher:

- В planner исправлена ограниченная диагностика: при заполненных32 записях native denial может заменить предварительный topology/combat/current rejection. Выбор кандидатов, native budget256 и admission guards не меняются. Focused regression сначала RED, затем GREEN: реальная причина native отказа не скрывается256 cheap failures.
- Fixture сохраняет полный Throwable stack, отдельные final markers/logs/process receipts, production CodeSource, observer verdict и before/after native facts. `final-*` aliases используют прежние guarded actions; старые execution markers не удалялись.
- Для нового исполнения сохранены4 exact input preimages и прежняя metadata в `P/private/closure-preimages`, затем перепривязаны только planner, два test sources и launcher. UUID/config/expiry не менялись; полная проверка29017 source/shadow/SQL/config/Geo inputs не обнаружила неизвестного drift. Protected078 и чужие среды не затрагивались.

### Preflight и свежий production build

До любых записей повторно подтверждены exact OWN authority/schema/migration/UUID, TEST URL/driver/pool/backup flags, private ACL/config/login binding, source config/input hashes, прежний actor, native character и полный canonical state/inventory. Read-only SQL выполнялся через существующий MariaDB11.4 client с `START TRANSACTION READ ONLY` / SELECT / ROLLBACK; credentials не выводились. Foreign connections0, конфликтующие Java/javaw0.

Одна временная read-only проверка сначала сравнила schema aggregate SHA с lowercase literal. Прочитан `PhantomTestSchemaManifest.sha256`: контракт использует uppercase HexFormat. Exact stored uppercase `8699ACDD436D29F5666CCC501E6B8D9A09265F064FC16AB7A7166BE04C84AEA2`, schema_version1/script_count123 и migration совпали; повторная корректная проверка PASS до записей. Это ошибка временной проверки регистра, не изменение metadata или обход guard.

```text
ant -f P/source/build.xml -Dsrc=M/java -Dbuild=R/closure-build -Dlibs=R/closure-libs jar
python -B M/test/offscreen_canonical092b.py final-preflight M P R
python -B M/test/offscreen_canonical092b.py final-travel M P R
```

Full Ant exit0,2367 sources,22s; source читается прямо из текущего M/java. Это третий full Ant за recovery, один в этом разрешении; прежние builds сохранены. OwnerConfig Apply только в собственном shadow, changed0,68 files/287 overrides. Три текущих WIP test sources адресно скомпилированы в `R/closure-tests`; focused092B на свежих full-Ant classes exit0 (3 groups/14 intervals, candidate fairness/native cap/diagnostics/fence). Старые 092A8/8, contracts092, GK/Travel/Corridor/Geo regression PASS относятся к starvation build ниже; после diagnostic-only изменения отдельно не перезапускались. Финальный read-only review всего накопленного14-file diff не оставил Critical/Important findings.

GameServer.jar SHA256 `7d779b245d0c8e2b4c2e3d420ad4bf2ead760de2483b6ae2d27d7d1d134532ea`; LoginServer.jar `4efe87c92b5ffaa8765f74e94a75afee4462d4e9d35c812d710c24681d73d16f`. Execution seal `R/final-outputs092b.json` SHA `83ae960365587fbc2a1a3fbab2185b47c25e528118ad482b4417eb0208d595f6`. Последующая read-only сверка всех4695 outputs/2796 source inputs,13 owned source/test/launcher и7 protected source config/JAR дала0 mismatches (`R/final-artifact-verification.json`). Source JAR не заменялись.

`final-preflight` exit0: `sameOwnedDB=true sameActor=true baselineUnchanged=true foreignConnections=0 DDL=0 DML=0`. CodeSource и в preflight, и в native log — именно `R/closure-build/bin/`, не предыдущие classes/JAR. Private expiry остался `2026-10-25T16:09:24Z`.

### Единственный запуск и первая точная причина отказа

`final-travel`: PID39560, `2026-10-25T15:40:36.820458Z` → `15:43:19.449034Z`, exit1. Полный stdout/stderr сохранён совместно в `P/final-travel.log`, terminal process receipt — `P/final-travel.process.json`. Native inputs загружены; первый terminal отказ:

```text
java.lang.IllegalArgumentException: TASK091_092B_VERIFIED_SOURCE_HASHES
at PhantomOffscreen092BChecks.nativeAction(PhantomOffscreen092BChecks.java:443)
```

Guard требует одновременно `stored.equals(corrected-baseline)` и `stored.hashes().equals(fixture.authority().hashes())`. Он выполняется до чтения `currentGoal` в verified branch, `recordNativeFacts`, `replaceFromState`, observer admission, goal replacement и travel transaction. Поэтому текущая кампания не проверила пригодность outdoor после starvation fix. `arrival.bin` отсутствует; transaction receipt остался пустым. `final-restart.started` отсутствует: новый JVM не запускался, поскольку результата для restart/idempotence нет.

Read-only postflight подтвердил unchanged persisted payload == corrected baseline; `acquisitionSnapshot` только декодирует этот payload. Это указывает на несовместимость сохранённых authority hashes с текущими runtime authority facts, а не на потерю сохранённого actor. **Конкретный несовпавший компонент не установлен**: actual live hashes не были выведены до guard. Нельзя приписывать отказ JAR fingerprint, определённому каталогу/zone/NPC/XYZ или считать его продолжением PUBLIC_PLAN без данных. Guard не ослаблялся, сохранённые hashes/state не переписывались, дополнительная native диагностика не запускалась.

### Фактические before/postflight, без игрового прибытия

Полный scoped SQL/binary export сохранён в `P/final-failure-facts.json` SHA256 `e81c9d87951230c68d08918654d7158821c499339319348d6ffafec784a87ec8`. Это read-only failure receipt, не synthetic PASS/arrival.

| Поле | До запуска → postflight |
|---|---|
| profileId / objectId / classIndex / activeClass / race | 1 / 268480990 / 0 / 115 / 3 → без изменений |
| Level / EXP / SP | 80 / 3075966164 / 0 → без изменений |
| XYZ / instance / heading | 24954,50864,-3400 / 0 / 0 → без изменений |
| committed anchor | `elven.farming.oren04-2019-02s` → без изменений |
| GoalId / revision | NOT_READ_BEFORE_GUARD_FAILURE; замены goal не было |
| Canonical payload / rowVersion | SHA `ce28ab8fc69df72a81600f896aebae51c89cf05f0af8762f19ffa3f817e39176` / 3 → без изменений |
| Clock epoch/residual/remainder | 15001501 / 0 / 0 → без изменений |
| Receipt operationKey / generation / tick / expectedAfterHash | пусто / 0 / 0 / пусто → без изменений |
| Inventory / Adena | Все6 INVENTORY/PAPERDOLL rows и полный canonical hash совпали; Adena200000 без изменений |
| Observer admission | NOT_INVOKED; ALLOW не получен и не заявлен |
| Arrival / restart / idempotence | NOT_REACHED / NOT_RUN / NOT_PROVEN |

Inventory rows сохранены полностью: object268480991/item6579×1,268480992/6383×1,268480993/6384×1,268480994/6385×1,268480995/6386×1 (PAPERDOLL);268480996/item57×200000 (INVENTORY). Новых предметов/EXP/SP/Adena не выдавалось. Сохранённые expected authority hashes:

| Компонент | Saved hash; actual live value NOT_CAPTURED |
|---|---|
| knowledge | `b47a63d38acddb21b3ded9614fa24acb3abd0aaf2ea4d49d5578acec094f5cc7` |
| topology | `b59bec011f252ebd6c0609a2a636dd11ea664fe3b2cc2e4be44ed8d45b31a6c6` |
| progression | `C3D056D01376A071786CAF654A8671B507EDFE7849C3D3B7490B413B57B35202` |
| commerce | `1b019bb1415efd06d2102cfd7915ae911b3d8a3352958e0ad66d845c2db50703` |

ThreadPool/Hikari штатно закрыты; после завершения Java/javaw0, foreign DB connections0. Read-only postflight DDL/DML0; никаких DROP/TRUNCATE/массовых DELETE/reseed/schema/actor recreation и изменения тестовых координат не было. Все287 overrides и чужие данные сохранены. После native FAIL production/test/launcher больше не менялись; выполнены только read-only разбор и сохранение отчёта/evidence/backup. Полные logs/receipts и прежние seals не удалены; `final-travel.log` SHA256 `412d72ec2c0f71dd8c82da91eef72e37a124de5a57c251ad7006753cb247fae0`.

**A5 BLOCKED, A6 local-only.** Следующий native запуск или правка совместимости state/authority требует нового согласованного scope; текущая кампания закончена по заданному STOP. Commit/push и следующие092C/093/094 не выполнялись. Git использовался только для разрешённого bounded exact diff/scope/backup; точные команды и PATHS приведены ниже, дополнительно reviewer читал `git diff HEAD -- <exact production/test groups из тех же14 путей>`.

Final exact-scope `git -c core.whitespace=cr-at-eol diff --check -- <14 PATHS>` exit0; `git status --short --untracked-files=normal -- <те же14 PATHS>` exit0, прежний scope10 modified/4 untracked, staging не выполнялся. Git сообщил прежние LF→CRLF advisory для двух tests; EOL policy не менялась.

- mojibake-маркеры в изменённых файлах проверены:14 файлов, полный набор27,0 совпадений (rg exit1).
- escaped Cyrillic в изменённых файлах проверены:14 файлов, все6 Unicode/XML regex-паттернов,0 совпадений (rg exit1).

## Предыдущее возобновление — исторический этап до FINAL NATIVE CLOSURE

25.10.2026, исторический итог: PARTIAL_092B_SALVAGED / A5 NOT_PROVEN. Дополнительно разрешённый `verified2` дал native diagnostics `ready=false / planner.target_or_route.absent`. После него найден и исправлен starvation-дефект; на момент этого этапа новый native на исправлении ещё не был разрешён. Его последующее разрешение и результат приведены выше.

## Последнее возобновление: строгий preflight, один verified2, исправление starvation

Владелец отдельно разрешил ещё один guarded resume прежней `l2jmobiush5_phantom_test_092b` и actor, с read-only проверками **до любых записей**. До первого backup/изменения этого возобновления выполнены:

- Owner/schema/baseline/UUID и TEST URL/driver/pool/backup flags, login binding, source Database.ini SHA, private owner/config/root ACL: PASS. UUID `cf38a85a-6522-4b07-bfcd-2477d876f0c3` сохранён.
- WIP14 SHA совпал с snapshot `PHANTOM-092B-RECOVERY-20261025T142217788743Z`. Full source/shadow/SQL/config/Geo inventory:29017 entries; runtime inputs без drift. Единственная известная source/copy разница до подготовки — diagnostic test из сохранённого WIP предыдущего этапа; её exact SHA подтверждён snapshot.
- Установленный MariaDB11.4 client выполнил `START TRANSACTION READ ONLY` + scoped SELECT + ROLLBACK, без файловых записей/DDL/DML. Подтверждены exact own authority row/migration, canonical schema metadata, один profile1/object268480990, payload SHA == `corrected-baseline.bin`, native character XYZ/heading/class/race/level/EXP/SP/expBeforeDeath.
- Полный inventory hash вычислен по прочитанному `PhantomBackgroundInventoryHash`: все INVENTORY/PAPERDOLL rows в порядке objectID, ASCII fields с NUL separators. Hash совпал с сохранённым canonicalHash:6 строк, включая Adena. Tracked/equipped subset в state содержит5 объектов и не является полным inventory.
- Foreign DB connections0, Java/javaw processes0. Ни одной записи до успешного завершения этих проверок не было.

Встроенный runtime получил EPERM на чтение private owner; ACL не ослаблялась, использован разрешённый host read-only процесс. Первые временные SQL-reader проверки не были PASS: исправлено смещение после length-prefixed anchor; затем сравнены полные rows с tracked subset, что неверно для существующего inventory-контракта. Прочитан реальный hash/transaction code; правильная полная canonical hash проверка прошла. Это ошибки временной read-only проверки, не обнаруженные изменения БД; между ними записей не выполнялось. Чужие credentials/схемы не использовались, секреты не выводились.

Перед изменением runner сохранён дополнительный byte-exact snapshot `D:/Codex/recovery/PHANTOM-092B-RECOVERY-20261025T144111012198Z`, manifest SHA `6ccad6ef3eeee88d7b25f9c7e4ccde4cfe77f80355856d6dccddd867ed1b0d1a`. Перед последующей адресной production-правкой — `PHANTOM-092B-RECOVERY-20261025T145706715871Z`, manifest SHA `3fc10a678a4fbab754a4c36320ad07bcd9270975795cb1c13d771913586b233d`. Все прежние backup сохранены.

Подготовка разрешённого прогона изменила только существующие test/launcher: `resume2-preflight`, `verified2`, `restart2`, `connections2` нормализуются в прежние действия, но сохраняют новые CREATE_NEW markers и собственные log/process receipts. Старые `verified.started`, logs и execution seals не удалялись/не перезаписывались. Перепривязаны только2 input copies, предварительно сохранённые с metadata в `P/private/resume2-preimages`; config/UUID/expiry не менялись. `R/resume2-tests` содержит отдельно скомпилированный test, production build прежний. Независимый read-only review этой подготовки — без конкретных safety regressions.

Команды этого разрешения из Git root (M/P/R расшифрованы ниже):

```text
python -B M/test/offscreen_canonical092b.py resume2-preflight M P R
python -B M/test/offscreen_canonical092b.py verified2 M P R
```

Штатный resume2-preflight exit0, exact own actor/state/config/inputs/metadata и foreign0 подтверждены повторно. Непосредственно до `verified2` host census вновь подтвердил JVM0.

**Ровно один новый native-прогон**: PID26188, `2026-10-25T14:45:44.498800Z` → `14:48:22.431207Z`, exit1 (157.932s; Europe/Chisinau16:45:44→16:48:22). Geo203/Zone2859/Spawn42407 загружены. Native diagnostic:

```text
092B_PUBLIC_PLAN ready=false reason=planner.target_or_route.absent mode=CERTIFIED
first 32 rejections: NPC18592/18593 generated anchors : topology.not_public_outdoor
092B_KEY TASK091_092B_VERIFIED_PUBLIC_PLAN
```

`mode=CERTIFIED` здесь — default accessor для неготового Result, **не выбранный certified itinerary**. Отказ произошёл до `replacePlan`/`advanceHistorical`, без offscreen commit. JVM/ThreadPool/Hikari штатно завершены; повторного native запуска и `restart2` не было.

### Подтверждённый дефект бюджета выбора и минимальная правка

Stock templates18592/18593 в `data/stats/npcs/18500-18599.xml` не содержат acquire EXP/SP. `suitableTargets` фильтрует level/kind/attackable/targetable, но не rewards; такие записи законно доходят до planner. Helper правильно отвергает их как offscreen-кандидатов. Ошибка была в другом: `seen.size()` расходовал общий лимит256 **до** static eligibility/native proof и после их отказа продолжал запрещать offscreen всем последующим anchors, включая нижние level tiers. Кроме того, combat/current exclusion проверялись после расхода attempts.

В существующем `PhantomHistoricalBackgroundPlanner` теперь отдельный plan-local счётчик256 native proofs. Cached combat viability/current exclusions и static `sourceBacked` topology/reward conditions применяются до расхода native попытки. Обычный certified/GK route проверяется первым; actual native denials расходуют лимит. Knowledge pages8/tier, level tiers, dedup,32 diagnostics, source/native/observer/transaction guards не ослаблены. Нового worker/cache/provider/schema или whitelist по NPC/region/XYZ нет.

Один новый grouped focused regression использует synthetic multiplicity64 source areas×4anchors и **реальный stock outdoor** как следующий источник. Fixture area aggregation приведена к production `aggregateSpawnAreas` unique NPC/node pattern одной строкой `distinct()`; это только test helper. До production-правки получен точный RED exit1: `256 cheap zero-reward rejections must not starve the next real lower-tier outdoor source.` После правки GREEN:

1. 256 cheap zero-reward failures не скрывают следующий real outdoor.
2. Combat-ineligible candidates не тратят native attempts.
3. 256 actual native proof denials всё ещё дают bounded refusal.

Первый вариант multiplicity fixture дублировал area summaries; assertion про native cap падал из-за недостижения256 уникальных anchors. Это исправление fixture, не production compile error; затем проверен нужный RED на starvation. Эти три boundary assertions — один focused regression, не256 отдельных тестов и не native/Geo sweep. Read-only review финального planner/test diff важных регрессий не нашёл.

Native diagnostics подтверждают ранние false candidates, а RED→GREEN — конкретный starvation-дефект. Полнота причин **того native отказа** не доказана: diagnostics ограничены32 и не содержат всех256 попыток. После исправления нельзя утверждать, что выбранный actor обязательно получил бы native-допустимый outdoor, пока не выполнен новый отдельно разрешённый product proof. Guarded endpoint/level/gear checks не обходились.

### Проверка фактического финального diff и сохранность

После нового production fix выполнен **один** final full Ant/JAR в новый `R/budgetfix-build`/`R/budgetfix-libs`: exit0,2367 sources,21s, compiler correction0. Суммарно с прежним recovery-этапом full Ant было2; прежний build не выдаётся за компиляцию изменённого planner. Production source читается прямо из M/java, Apply только в существующем own shadow,changed0. Старые native execution classes/JAR сохранены отдельно.

```text
ant -f P/source/build.xml -Dsrc=M/java -Dbuild=R/budgetfix-build -Dlibs=R/budgetfix-libs jar
```

Три текущих WIP test sources адресно скомпилированы в `R/budget-final-tests`. На **новых full-Ant classes**: focused092B exit0 (starvation bounds + прежние stock/level/14 intervals/fence); 092A8/8 exit0; contracts0923groups exit0; NormalGK/Travel089/Corridor064 exit0/0/0; GeoValidationRules exit0. Logs — `R/budget-final*.log`; DOCTYPE-denial и Geo fixture0 являются прежними DB-free negatives, не дополнительным native-прогоном.

Final GameServer.jar SHA256 `4d9caa9a280e81148269565dbe78f966028dc21cb7b7f3bb24a911116d53aff3`; LoginServer.jar `8373dd0afe00bb598bb343feab73a897c7474131080201f0056b8e91317e8454`. Финальные13 source/test/launcher SHA — `R/budget-final-source-sha256.json`. Seal именно выполненного `verified2` — `R/resume2-outputs092b.json`, SHA `ce2e02a5e34e257ec6fd6b1842b6b7db69c1dc570a53a1bf8c34ccfdc2e741f1`; он относится к версии **до starvation fix**. Старые execution seals не обновлялись ради нового WIP; новая native привязка/запуск не выполнялись.

Read-only SQL postflight после verified2 подтвердил exact OWN authority/actor, payload SHA, canonical character XYZ/class/level/EXP/SP и полный items/Adena hash **без изменений**, foreignConnections0. После завершения own build/test/verifier JVM0. Source config/JAR7/7 byte-exact; final OwnerConfig Verify exit0,68 files/287 overrides/conflicts0/changed0. No schema recreate/reseed/awards/cleanup, protected releases/чужие worktrees/клиент не менялись.

Итог gates: A0 guarded preservation/preflight/postflight PASS; A1–A4 final DB-free/contracts/review PASS в описанном scope; **A5 на final starvation fix NOT_RUN/NOT_PROVEN**, arrival/restart отсутствуют; A6 local report/WIP/backup, без commit/push. Новый обязательный шаг до GREEN — separately authorised guarded native proof **финального** planner с тем же actor/DB и sameDB restart; текущий лимит нового native прогона уже использован. 092C/093/094 не начинались.

- mojibake-маркеры в изменённых файлах проверены:14 файлов, полный набор27,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены:14 файлов, все6 Unicode/XML regex-паттернов,0 совпадений.

## Предыдущий guarded resume — исторический этап

25.10.2026, **актуальный итог после разрешённого guarded resume: PARTIAL_092B_SALVAGED / A5 BLOCKED — `TASK091_092B_VERIFIED_PUBLIC_PLAN`**. Единственный дополнительный native campaign выполнен и завершился exit1 до offscreen-транзакции. Persisted arrival и sameDB restart не доказаны; `SUCCESS_092B_STORED_TRANSIT` не объявлен. WIP/backup сохраняются, commit/push не выполнялись.

## Guarded resume: новая явная авторизация и фактический результат

После первого безопасного STOP владелец явно ответил: **«Разрешаю guarded resume существующей OWN TEST092B»**. Этим для единственного A5 campaign требование ABSENT заменено на повторную аттестацию прежней owned БД и actor, без очистки/reseed. Старый `092B_EXISTING_SCHEMA_STOP` ниже — история до этого разрешения, не текущая причина блокировки.

Переиспользованы прежние stage/config/DB/schema/installation ID, `profile=1` и прежний character object из `corrected-baseline.bin`. Новых DB/actor, выдач EXP/SP/items/Adena, CREATE/DROP/TRUNCATE/DELETE или reseed не выполнялось. Private Database.ini и UUID/schema не менялись. Bounded двухчасовой owner expiry перепривязан до `2026-10-25T16:09:24Z`; прежние owner/inputs metadata и семь заменяемых input copies сначала сохранены в `P/private/resume-preimages/` с SHA-проверкой. Сохранность исходных recovery snapshots в `D:/Codex/recovery/` не нарушена.

В существующий launcher добавлен optional compiled output с exact owner/module/stage/source/output SHA bindings. Reuse final Ant production classes из R, без повторной полной сборки и без нового большого shadow. Native fixture получил `resume-preflight`, который до native infrastructure выполняет SELECT-only: полный прежний ACL/config/input guard, canonical SQL metadata/owner, quiescence, exact one profile/object и byte-decoded background state == corrected baseline. Отдельный read-only review test/launcher изменений не нашёл конкретных safety regressions.

Native-команды из Git root:

```text
python -B M/test/offscreen_canonical092b.py resume-preflight M P R
python -B M/test/offscreen_canonical092b.py verified M P R
python -B M/test/offscreen_canonical092b.py connections M P R
```

Здесь `M`, `P`, `R` — те же точные абсолютные module/stage/recovery-output paths, которые приведены ниже в разделе исходной recovery-проверки. Optional compiled override допускает только focused/resume-preflight/verified/restart/connections; configure/bootstrap/native/continue с ним запрещены. Исходные Task091Lane owner/config/inputs/metadata guards не ослаблены.

| Факт после разрешения | Свежий результат |
|---|---|
| Guarded resume preflight | exit0; sameOwnedDB/sameActor/baselineUnchanged=true, foreignConnections0, DDL/DML0 |
| Native campaign `verified` | **ровно один**, PID40768, `2026-10-25T14:11:36.851723Z` → `14:14:09.697479Z`, exit1; длительность152.846s |
| Загруженные native inputs | Geo203, 31 zone classes/2859 zones, 42407 spawns; эти числа сами по себе не доказывают outdoor admission |
| Последняя actor state проверка перед planner | `stored.equals(corrected-baseline)` и authority hashes совпали; failure пришёл позже, на PUBLIC_PLAN assertion |
| Точный отказ | `IllegalArgumentException: TASK091_092B_VERIFIED_PUBLIC_PLAN`, native fixture строка379 в выполненной версии |
| Точка остановки | `replaceFromState` не удовлетворил `plan.ready() && mode==OFFSCREEN_OUTDOOR_PENDING`; `replacePlan` и `advanceHistorical` не достигнуты |
| Canonical arrival/receipt/cursor proof | NOT_PROVEN; `arrival.bin` и `last-catchup.bin` отсутствуют |
| Restart | NOT_RUN, `restart.started` отсутствует; повторный native campaign не выполнялся |
| Штатное завершение | ThreadPool shutdown, Hikari pool closed; PID40768 отсутствует |
| Read-only postflight connections | exit0, `TASK091_CONNECTIONS_ZERO`, PID3848 завершён14:18:19Z; native infrastructure не запускалась |
| Source сохранность после campaign | Production sources совпали с native/full-Ant source seal; source config/JAR7/7 unchanged; OwnerConfig Verify exit0,68/287/conflicts0/changed0 |

Выполненный fixture не вывел `plan.reasonKey`, travelMode и bounded `candidateRejections` перед PUBLIC_PLAN assertion. Поэтому этот результат **не доказывает** CANDIDATE_EXHAUSTED, отказ конкретной zone/NSWE/Geo/combat проверки или даже отличие not-ready от неподходящего mode. Общий assertion не заменяет корневую attribution. Связать отказ с конкретным NPC, Swamp/Krateis/XYZ или глобальной готовностью зон по существующему логу нельзя; whitelist/ослабление guards не вводились.

После failure добавлены две строки bounded diagnostic output `092B_PUBLIC_PLAN ready/reason/mode/rejections` перед этим assertion. Они адресно скомпилированы в **отдельный** `R/postflight-tests`; focused092B на том же final production build — exit0,14 scalar intervals. Эта diagnostic **не была выполнена native**, второй JVM campaign ради её вывода не запускался. Exact classes и seal реально выполненного campaign сохранены: `R/resume-outputs092b.json`, SHA256 `7a05b5377c1721702f393531136034433844dbd8d22a8970c131578cfe02e670`. После diagnostic исходный test SHA закономерно отличается от этого исторического execution seal; launcher не перепривязывался для второго native запуска. Production sources/JAR после full Ant и native не менялись.

Актуальные A0–A6: A0 guarded ownership/config/preflight и terminal cleanup PASS; A1 DB-free generic selection PASS, **native public-plan admission BLOCKED**; A2–A4 focused/092A/contracts/old-GK controls PASS, real canonical movement/commit rollback/replay NOT_PROVEN; **A5 BLOCKED**, arrival/restart отсутствуют; A6 только local WIP/report/backup, публикация запрещена без A5. Максимум одного дополнительного native campaign израсходован. Узкий следующий шаг — отдельно разрешённая native диагностика готовых bounded planner rejections с последующим адресным решением по фактам; текущая задача не использует ещё один запуск или Geo sweep без нового разрешения.

Дополнительный source scope этого resume: только существующие `test/offscreen_canonical092b.py`, `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreen092BChecks.java` и этот отчёт. Новый read-only preflight не создаёт второго SQL/bootstrap стека; full Ant по-прежнему один. Последний focused/compile относится к финальному test source, а native proof — к сохранённой execution version. Начальная работа recovery до разрешения и исходный run1 сохранены ниже как исторические этапы; их NOT_RUN/ABSENT statements не описывают выполненный выше campaign.

## Recovery до разрешения guarded resume — исторический этап

25.10.2026, recovery: **PARTIAL_092B_SALVAGED / A5 BLOCKED: `092B_EXISTING_SCHEMA_STOP`**. Локальный production WIP сохранён, адресные исправления и свежая full Ant/JAR сборка проверены. Обязательные persisted arrival и sameDB restart в recovery **NOT_RUN**; `SUCCESS_092B_STORED_TRANSIT` не объявлен. Commit/push не выполнялись.

## Recovery: сохранность и источник истины

Исходный HEAD, tracking ref и live `origin/feature/phantom-world` равны `ac5931941f36fbdbb1a64e8d848b5fb57853b74c`; рабочая ветка `feature/phantom-world`. Проверка live ref в sandbox получила network failure, разрешённый read-only host retry прошёл. Новый checkout/worktree/branch не создавался; reset/restore/stash/clean не использовались.

До правок и компиляции выполнен штатный `capture_worktree.py`. Byte-exact snapshot: `D:\Codex\recovery\PHANTOM-092B-RECOVERY-20261025T134051523365Z`. Manifest SHA256: `d26b09b2cbc9205ec07c17c5b3fd150c46a848d972489cf060f5da18dfe9f9b8`. Скопированы 14 файлов; отдельно повторно проверены все source/copy SHA256. Отсутствует только старый предполагаемый `test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomOffscreen092BChecks.java`; реальный новый тест `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreen092BChecks.java` сохранён. Других WIP092B sources/tests вне списка не обнаружено. Task packages не менялись; SHA исходного пакета 8/8 и recovery-пакета 7/7 подтверждены. Чужие packages/configs не копировались.

Первый sandbox capture получил WinError5 на создании внешнего backup-каталога. Повтор с разрешённым доступом завершился exit0 до любых изменений checkout. Источники не изменялись при неудачном capture. Первоначальный snapshot остаётся неизменным.

Прочитаны root AGENTS/README, PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, релевантные release/owner contracts, оба task package, локальный run1 report, сохранённый полный tracked diff, новый outdoor helper, planner/authority/service/transaction/operation key, ZoneManager, native zone forms, Geo API и существующие focused/native fixtures. Дополнительные AGENTS в предках/серверном модуле, module README и отдельные актуальные code-map/pattern-файлы не найдены; SOURCE_MAP обоих пакетов использован как карта. Переиспользованы historical transaction/identity, NORMAL-GK proof, 092A census и native091 ownership guard. Java25/Ant, codecs format2/compact3, SQL schema и архитектура owners сохранены.

## Run2 WIP: что было сохранено и проверено

| Production owner | Уже существовавшее поведение run2 | Recovery / оставшийся риск |
|---|---|---|
| `PhantomHistoricalBackgroundPlanner` | До 8 knowledge pages на tier, 256 offscreen candidate attempts, до 32 отказов; отказ конкретному NPC/anchor, продолжение поиска; certified/GK приоритет | Сохранено; проверены alternative/exhaustion/level-band controls, без poison профиля или региона |
| `PhantomOffscreenOutdoorTravel` | Stock polygon/source mapping, public ancestry, native zone semantics, scalar elapsed и exact predecessor receipt | Исправлено преждевременное снятие observer-fence; real native public eligibility не объявляется за fixture |
| `L2jPhantomBackgroundAuthority` | Geo surface/stock/zone proof, class/combat/level/resources и canonical source/destination | Добавлена NSWE_ALL проверка обоих endpoints; никакого whitelist по NPC/anchor/XYZ/названию региона |
| `PhantomBackgroundAuthority` | Legacy/test implementations fail closed по offscreen и zone generation | Сохранено без новой правки recovery |
| `PhantomBackgroundOperationKey` | Отдельные appended offscreen/cancel actions и reserved receipt tag; старые GK leg/digest contracts | Сохранено; ordinary GK/old contracts PASS |
| `PhantomBackgroundService` | READY/BACKGROUND lease, отсутствие World/materialization owner, historical replay/cancel, publisher, observer before load/spawn | Сохранено; actual canonical arrival/rollback/restart в recovery NOT_RUN |
| `PhantomBackgroundTransaction` | Locks, full profile/object/goal/revision/plan/interval binding, no reward/fees, fresh observer и pinned zone generation | Добавлен повтор native advance proof прямо перед последним observer verdict и commit |
| `PhantomSystem` | Volatile publication configured runtime для настоящего 092A callback без System monitor под JDBC locks | Сохранено; свежие 092A 8/8 PASS |
| `ZoneManager` | Completion/ready/generation metadata, read-only loaded-zone snapshot поверх существующего `getZones(x,y,z)` | Metadata нужна для различения empty outdoor и незагруженного/reloading index; удалён новый skip при обратных Z bounds |

Старый blanket DENY всего generated producer из run1 уже был заменён в сохранённом run2 фактическими stock/Geo/native-zone проверками. Recovery не возвращает blanket deny и не разрешает конкретный Swamp/Krateis/NPC/XYZ. Тег `outdoor-farming` сам по себе допуска не даёт. NSWE_ALL консервативен: клетка с закрытым направлением отклоняется, planner может выбрать другую подходящую цель.

Новые точечные правки recovery: четыре production-файла (`PhantomOffscreenOutdoorTravel`, `L2jPhantomBackgroundAuthority`, `PhantomBackgroundTransaction`, `ZoneManager`), два уже существующих WIP test-файла (`knowledge/PhantomOffscreen092Checks`, `tests/phantoms/PhantomOffscreen092BChecks`) и этот отчёт. Остальной WIP сохранён. Общий inherited scope — 9 production + 3 test + launcher + report = 14 файлов; bounded exception прямо задан recovery-пакетом, новые подсистемы не добавлены.

## Найденные дефекты и свежие проверки

До правок actual WIP адресно скомпилирован и focused092B прошёл. Затем отдельный focused RED воспроизвёл observer-fence defect: статус MATERIALIZED записывается до World spawn; при pre-spawn DENY cleanup STORE очищал tagged receipt и следующая попытка обходила guard. Теперь оба native capture снимают marker только при MATERIALIZED **и точном Player в обоих World indexes**. Без World marker сохраняется; положительный World capture может его снять. Новый RED assertion exit1 → тот же focused GREEN exit0. Это DB-free проверка решения по native facts, не исполненный native cleanup scenario.

Geo height не доказывает NSWE. В native endpoint admission добавлен существующий `GeoEngine.checkNearestNswe(getGeoX(x),getGeoY(y),z,Cell.NSWE_ALL)`. Общий GeoEngine не менялся. Final transaction повторяет тот же bounded `advanceOffscreenTravel`, сравнивает XYZ/clock/fee0, затем получает свежий observer verdict с zone/topology/hash проверками перед commit. Это закрывает найденное окно между ранним native proof и canonical writes при Geo reload без изменения topology generation. Runtime после последнего чтения и JDBC commit не замораживается; более сильная глобальная синхронизация не заявляется.

`ZoneCuboid` и `ZoneNPoly` штатно нормализуют min/max Z. Новый WIP-only `continue` в ZoneManager при `minZ > maxZ` удалён вручную после backup: обычные зоны больше не теряются из-за этой проверки. Оставшиеся изменения manager добавляют только admission metadata и snapshot; обычные enter/exit handlers и zone forms не менялись. Проверены обратные Z bounds и ближайшие существующие Geo controls; native normal Player/NPC/GM enter/exit и live reload отдельно NOT_RUN.

Пути проверок: `M=<repo>/L2J_Mobius_CT_2.6_HighFive`, `P=%TEMP%/phantom092b-8f9b8e1b407540a095e9acd0d7c01388`, `R=%TEMP%/phantom092b-recovery-20261025T1356`. Повторный shadow из тысяч файлов не создавался. Full build читает **текущие M/java**; прежний собственный P/source предоставляет неизменённый build.xml и config inputs, vendor JAR скопированы в новый R/libs. Apply выполнен только к прежнему shadow, changed0; source Apply/Save не запускались. Старые native manifests/seals/owner/DB не обновлялись.

| Команда / gate recovery | Результат |
|---|---|
| `python -B M/docs/phantoms/tasks/PHANTOM-OFFSCREEN-092B-RECOVERY/capture_worktree.py <repo> D:/Codex/recovery` | exit0, 14 source/copy SHA совпали; первая sandbox попытка без записи — exit1 |
| Адресный `javac -encoding UTF-8 -source 25 -target 25` по сохранённым WIP java/tests | exit0 до правок и после исправлений; не full Ant |
| Receipt regression до исправления | exit1 с точным assertion о преждевременном снятии fence; без БД |
| `ant -f P/source/build.xml -Dsrc=M/java -Dbuild=R/build -Dlibs=R/libs jar` | **один full Ant**, exit0, 2367 sources, 21s; compiler correction не потребовалась |
| Адресный javac трёх текущих WIP tests против R/build/bin | exit0; остальные unchanged test classes переиспользованы из P/tests |
| `PhantomOffscreen092BChecks M` на final full-Ant classes | exit0; graph0/certified, stock polygon, rejected native-zone candidate → следующая цель, combat exhaustion, bounded diagnostics, 14 scalar intervals, residual revision/plan/request, no rewards, aborted-load fence |
| Уровни 5/20/40/60/80/85 | exit0; stock fixture outcomes: suitable 4 / NO_ELIGIBLE_TARGET / NO_ELIGIBLE_TARGET / 60 / 80 / 80. Реальный 72-level источник для85 отклонён. Native combat/Geo для этих fixtures NOT_RUN |
| `PhantomOffscreenObservation092AChecks production` | exit0, 8/8: recent-human, refresh, 257 overflow, source/destination, unknown/grace/errors, classifier, concurrency, façade |
| `PhantomOffscreen092Checks contracts M` | exit0, 3 groups, old certified control, operation identity, format2/compact3/161 objects, 092A retention |
| `PhantomNormalGatekeeperPureTest`, `PhantomTravel089Test`, `PhantomWalkCorridor064Test` | exit0/0/0, 1+3+4 ближайших controls |
| `PhantomGeoValidationRulesTest` | exit0; hermetic Geo/zone/collision negatives. GeoEngine fixture loaded0; ожидаемый DOCTYPE-denial diagnostic является negative control, не native campaign |
| Source `OwnerConfig.ps1 -Action Verify` до/после build | exit0/0, files68, overrides287, conflicts0, changed0 |
| SHA Rates/PhantomPlayers/PhantomMerchants/PremiumSystem/OwnerConfig/source server JAR | 7/7 unchanged относительно recovery preimages |
| `git -c core.whitespace=cr-at-eol diff --check -- <14 exact paths>` | exit0; EOL policy/config не менялись |

Первый invocation focused не загрузил main из-за PowerShell parsing JVM `-Dfile.encoding=UTF-8`; аргумент заключён в кавычки, последующие запуски exit0. Полных Ant повторов, stress/sweep или серверных стартов из-за этого не было. Независимый read-only review нашёл NSWE/fence/final-Geo gaps; повторная проверка точных исправлений — без незакрытых Critical/Important замечаний.

Новые JAR только в `R/build/dist/libs` и `R/libs`: GameServer SHA256 `d335088ced87ffc3a318b8052290e2eb111c032442a00158f05c6f12c5c46f35`, LoginServer `248f2599ec3c9ce744ef69e6c62d3efd74b4b944b657e1048ebf5f83ab80fb66`. SHA всех source/test/launcher inputs и snapshot delta относительно R0 сохранены в `R/source-before-report-sha256.json`; этот файл записан до изменения отчёта, его report entry относится к историческому run1. Production/test inputs после final build не менялись.

## Точная блокировка native A5

Запущен **только существующий read-only** `java -cp P/source/dist/libs/* P/Preflight092B.java P/source/dist/game/config/Database.ini`. Он подключается к уже заданному private localhost endpoint без selected catalog, включает readOnly и первым SELECT проверяет exact namespace. Свежий результат **exit1: `092B_EXISTING_SCHEMA_STOP`**, `Preflight092B.java:15`. Дальнейшие проверки foreign connections/CREATE grants не достигнуты и не объявляются PASS. DDL/DML0; native launcher `verified`/`native`/`continue`/`restart` в recovery не запускался. Бюджет одного нового native campaign не израсходован.

Существующая `l2jmobiush5_phantom_test_092b` содержит прежний WIP actor и не удаляется, не очищается и не reseed. R5 требует ABSENT; подмена имени schema, продление owner expiry, изменение guard или игнорирование существующей БД без отдельного разрешённого контракта не выполнялись. Минимальная разблокировка — согласовать точный guarded resume **существующей** owned092B БД/actor с повторной ownership/quiescence/config/inputs аттестацией вместо ABSENT, либо разрешить отдельный новый namespace. Только после этого один campaign должен доказать persisted arrival и stopped sameDB restart. Это новая проверка условий запуска, не просьба восстановить код из GitHub.

| A0–A6 на текущем recovery diff | Статус |
|---|---|
| A0 safety/build/config | Snapshot/branch/live/config/JAR PASS; DB admission BLOCKED, foreign/CREATE checks NOT_REACHED |
| A1 generic graph0 selection | DB-free PASS, actual native source/Geo/zone/combat admission для выбранного actor NOT_RUN |
| A2 STORED canonical binding | Reviewed + focused contracts PASS; real canonical mutation NOT_PROVEN |
| A3 observers | 092A 8/8 и fence RED→GREEN PASS; actual commit-denial rollback NOT_RUN |
| A4 elapsed/identity/replay | Scalar/exact residual и old GK controls PASS; persisted replay NOT_RUN |
| A5 arrival/sameDB | **BLOCKED**, native arrival/restart NOT_RUN |
| A6 delivery | Локальные WIP/report/backup сохранены; commit/push запрещены без A5 GREEN и не выполнялись |

Recovery начат с capture в 13:40:51 UTC (15:40:51 Europe/Chisinau); full-Ant/final focused закончены до13:55 UTC. CPU/heap peaks не измерялись. Это не acceptance 092C, фарма/боевых наград или общего мира. 092C/093/094 не начинались. Protected releases/клиент/чужие worktrees/MariaDB service не запускались и не менялись; полного внешнего hash-аудита защищённых поставок не выполнялось.

- mojibake-маркеры в изменённых файлах проверены: 14 файлов, полный набор27, 0 совпадений. Использован literal pattern file вне checkout; первый inline-вызов не дал проверяемого результата и не учитывался как PASS.
- escaped Cyrillic в изменённых файлах проверены: 14 файлов, все6 Unicode/XML regex-паттернов, 0 совпадений.

Git применялся по прямому разрешению recovery-пакета для salvage, exact diff и baseline/scope guard. Точные read-only команды: внутри capture — `git -C <repo> rev-parse --show-toplevel`, `git -C <repo> symbolic-ref --short HEAD`, `git -C <repo> rev-parse HEAD`, `git -C <repo> diff --binary HEAD -- <15 PATHS capture>`, `git -C <repo> diff --cached --binary -- <те же PATHS>`, `git -C <repo> status --porcelain=v1 --untracked-files=normal -- <те же PATHS>`, `git -C <repo> ls-files --others --exclude-standard -- <те же PATHS>`. PATHS — exact tuple в неизменённом `capture_worktree.py`, включая отсутствующий старый test path. Также `git status --short --untracked-files=normal -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/docs/phantoms` однократно для bounded WIP inventory; затем только `git status --short --untracked-files=normal -- <14 manifest paths>`, `git -c core.whitespace=cr-at-eol diff --check -- <те же 14 paths>`, `git rev-parse HEAD origin/feature/phantom-world`, `git symbolic-ref --short HEAD`, `git ls-remote origin refs/heads/feature/phantom-world` (sandbox failure / host exit0). Git write/history commands не выполнялись. Остальной унаследованный exact allowlist перечислен в историческом разделе ниже; дополнительно в scope входит `java/org/l2jmobius/gameserver/managers/ZoneManager.java`.

---

## Исторический run1 — сохранённый прежний отчёт

Всё ниже описывает первый запуск и его native+continuation, **не свежий recovery proof**. В частности, старые blanket generated DENY, JAR SHA, время и publication assumptions не описывают текущий run2+recovery diff.

25.10.2026. **PARTIAL / A5 BLOCKED — реальный STORED transit не доказан.** Typed production path и focused gates реализованы, но native-сценарий и единственная continuation не получили offscreen commit. SameDB arrival/restart NOT_RUN; commit/push не выполнены. Это не SUCCESS_092B, TEST_ONLY success, FULL092, farm/combat acceptance, paid-GK resume или 092C.

## Authority и read-first

Работа выполнена в фактически определённом Git root `D:\Codex\repos\L2J-phantom-world`, на `feature/phantom-world`, исходный HEAD и tracking ref — `ac5931941f36fbdbb1a64e8d848b5fb57853b74c`. Tracked dirt на старте отсутствовал. Чужие untracked task packages и `.phantom-local/` сохранены; worktree/branch не создавались. Восемь payload entries пакета 092B проверены по size/SHA256; пакет не менялся.

Прочитаны root AGENTS/README, текущий статус, workflow/release/owner contracts и весь пакет 092B; ближайшие reports091/092/092A; build.xml; planner, authority, owner, transaction, operation key, goal/state/catchup/native-policy codecs, 092A observer façade, topology/publisher и native091 lane. Дополнительные AGENTS в предках/целевых папках, module README и отдельные актуальные code-map/pattern-файлы не найдены. SOURCE_MAP092B использован как карта.

Локальные аналоги: historical canonical transaction с profile/goal/catchup/state/canonical locks; typed NORMAL-GK travel proof; 092A full census и recent-human; native091 ownership/ACL/schema/input guard. Сохранены Java25/Ant, существующие owners, format2/compact3 и SQL schema. Нет нового worker/timer/provider, synthetic edges, Player/GameServer core изменений или новой среды в защищённых поставках.

Bounded scope: восемь production-файлов; один новый focused/native test file; переиспользование stock092 fixture и фиксированного native091 guard; один небольшой launcher и этот отчёт. Расширение на два соседних test fixture файла необходимо для actual native operation, без второго SQL/bootstrap стека. План из ARCHITECTURE/TEST_PLAN выполнен последовательно: RED → typed production path → focused GREEN → own native transaction → sameDB restart → exact-file delivery. Независимый read-only review применён к production пути.

## Production поведение

Planner создаёт `OFFSCREEN_OUTDOOR_PENDING` только при отсутствии certified route, с реальными monster/source-area facts, FARMING anchor, `FARMING_AREA/outdoor-farming`, instance0, public ancestry и native Geo endpoints. Известные закрытые/подземные/Seven Signs/Rift/instance/door/closed contexts не допускаются. Область, уже содержащая source, не является удалённым offscreen-кандидатом. Certified-кандидаты сохраняют приоритет во всех прежних level tiers; visible fallback не получает виртуальный путь. Mode сохраняется в существующем goal reason, без schema/codec изменения.

Новые enum actions добавлены в конец: `HISTORICAL_OFFSCREEN_TRAVEL` и `HISTORICAL_OFFSCREEN_CANCEL`. Старые ordinals/digest strings и строгий `leg.<24 hex>` контракт не менялись. Existing historical identity связывает profile/object, goal/revision, request/generation, interval/cursor, plan identity и hashes. Отдельный reserved historical receipt tick отмечает offscreen provenance; scheduler generations не переиспользуются.

Время считается по 3D расстоянию и текущему native-attested runSpeed: batch ≤60000ms, долг ≤24 игровых часов. Partial step оставляет фактические XYZ/anchor на source и уменьшает residual; конечный due step записывает canonical destination. Continuation требует digest предыдущего интервала ровно той же цели/ревизии/request/generation/plan. Неясный legacy residual не переносится в новый маршрут: no move/no reward. Известный tagged offscreen debt при замене request/goal можно отменить отдельным canonical cancellation minute, без изменения XYZ, inventory, progress или RNG, затем начать путь заново. Это консервативная потеря старого прогресса, не ускорение нового пути. Слишком короткие повторяющиеся requests не гарантируют завершения дальнего пути; нужен достаточный следующий historical window либо штатный native takeover.

Admission требует READY, BACKGROUND identity lease, исключение World object/save task, отсутствующую materialization entry, operation/lifecycle claim, native policy и отсутствие owned-store intent. В canonical transaction перепроверяются exact goal/catchup/state bytes, native context, generations/hashes, proposed XYZ/clock, отсутствие rewards/fees и ownership. Full observer admission source+destination применяется до execute, под row locks и непосредственно перед commit; DENY/UNKNOWN дают rollback/retry. После arrival marker нельзя стереть ordinary Background farm/idle/travel: до native takeover такие операции ждут. Перед native load и непосредственно перед World spawn admission применяется снова. Marker снимается после штатного native materialization/store, не при pre-World refresh.

Production callback использует настоящий `PhantomSystem.offscreenObserverAdmission`; ограниченный `.onlineHumanPoints().limit(256)` не используется. Configured instance опубликован volatile, поэтому callback не берёт System class monitor под JDBC locks. Census ограничен257 и читает World без длительного глобального monitor; publication происходит только после durable verification. Native fixture использует документированную DI boundary с реальным topology publisher, полным World census, реальным QA point и настоящим startup grace60s; fixture ALLOW не подставляется.

Обнаруженная native root cause: `nativeAnchor()` без previous state мог вернуть destination intent как committed source при старых XYZ. Теперь начальный capture требует тот же unique factual native anchor, что `planInitial`; последующий native store может переаттестовать exact factual point. Существующая off-anchor lineage остаётся fallback. Координаты активного Player этим исправлением не назначаются.

## Проверки и реальные неуспешные попытки

`M=<repo>/L2J_Mobius_CT_2.6_HighFive`, `S=%TEMP%/phantom092b-8f9b8e1b407540a095e9acd0d7c01388`. S имеет private owner/SYSTEM ACL. В нём сохранены byte-exact preimages production inputs, owner files и source server JAR; SHA копий проверены. Native inputs: 28814 source/copy entries и203 Geo tiles, существующие canonical SQL, отдельные manifests и class/JAR seals. Private inputs/logs/DB credentials не публикуются.

| Команда/шаг | Результат |
|---|---|
| Source `powershell -NoProfile -ExecutionPolicy Bypass -File M/tools/OwnerConfig.ps1 -Action Verify` | До правок: exit0,68 files/287 overrides/conflicts0/changed0 |
| Targeted javac baseline + `PhantomOffscreen092Checks required-product M` | javac0/main1: настоящий graph0 RED `planner.target_or_route.absent` |
| Первые targeted javac в sandbox | Старый source GameServer.jar не содержит092A; затем sandbox javac не видел свежие TEMP package classes. Host compile прошёл; ACL не ослаблялась |
| Первый focused runtime | RED: substring `door` ошибочно совпал с `outdoor-farming`; исправлен только token guard |
| `ant -f S/source/build.xml -Dbuild=S/build jar` | exit0,22s,2367 production sources, один full Ant invocation; OwnerConfig Apply только shadow,changed0 |
| Отдельный javac всех test sources | exit0,5 существующих warnings; не означает запуск всех tests |
| Адресные javac/JAR updates после review | Pre-spawn fail-closed; receipt/native takeover fence; затем одна native source-anchor correction. Полный Ant повторно не запускался; final JAR/class seal обновлялся явно |
| `python -B M/test/offscreen_canonical092b.py focused M S` | exit0,3 focused группы: graph0/certified priorities/closed/overlap;14 минут/arrival/exact residual lineage; no-reward/bounds |
| `PhantomOffscreenObservation092AChecks production` | exit0,8/8, включая0/256/257 humans, unknown/grace, concurrent recent-human и façade |
| Старый `PhantomOffscreen092Checks contracts` | Первоначально устаревший retention assertion RED: ожидал забывания после STORED, уже неверного в baseline092A. Ожидание исправлено на реальный092A contract; final exit0, codec2/compact3 и161 objects PASS. Это не offscreen/native GREEN старого092 |
| `PhantomNormalGatekeeperPureTest`, `PhantomTravel089Test`, `PhantomWalkCorridor064Test` | Первый запуск из неверного CWD завершился до assertions; из собственного shadow/dist/game: exit0/0/0,1+3+4 controls PASS; paid native GK не запускался |
| Read-only preflight новой OWN DB | exit0: exact namespace отсутствовал, foreign connections0, CREATE privilege есть, selected catalog NONE,DDL/DML0 |
| Launcher `configure` | exit0: private config только own092B, source config не менялся |
| Launcher `bootstrap` | exit0:123 scripts,18 absent DROP noops пропущены; DROP/TRUNCATE/DELETE0, owner/schema metadata verified |
| Первый launcher `native` | exit1: неверный source anchor после initial native capture, observer rollback probe не достигнут. Никаких успешных offscreen/native claims |
| Read-only сравнение после failure | baselineEqual/identity/inventory/progress=true, background rowVersion0; XYZ исходные, receipt/cursor не продвинулись |
| Единственная launcher `continue` | exit1: canonical source исправлен native load/store, Level/EXP/SP/identity/inventory/XYZ сохранены; выбран NPC18581, затем probe завершился до ожидаемого observer commit boundary. Offscreen receipt/cursor не продвинулись |
| Launcher `restart`, sameDB | NOT_RUN: arrival/committed virtual operation отсутствуют; restart не подменялся новым bootstrap |

General ant verify, иные product smoke, оплачиваемый GK, Rift/Seven Signs/Olympiad, 5000 profiles/128 Players/200 stores и следующая092C не запускались. Прямые SQL UPDATE XYZ, teleToLocation и новые item/EXP grants после fixture baseline не выполнялись.

## Новый подтверждённый blocker и остановка

Сохранённая цель continuation: NPC18581 Gardener, revision1, `generated.farm.386e6c4606d43aad870b4d6e.anchor`, XYZ `(-83986,-20411,-8320)`. В `data/phantoms/topology/high-five-generated-01.xml:2079` эта область имеет `FARMING_AREA`, instance0 и `outdoor-farming`. Её source — `data/spawns/Others/KrateisCube.xml`, то есть комната Krateis Cube с Match Manager/registration/battle lifecycle, а не публичное outdoor-поле. Факт подтверждён исходными topology/spawn/NPC XML и существующим `managers/games/KrateisCubeManager`, без запуска события.

Это конкретный gap положительной public-outdoor attestation: Geo, sourceExists, instance0 и один tag не доказывают доступность. `PhantomTopologyValidationBackend` предоставляет map-region/NPC/spawn/door/source-exists, но не native event/public-entry eligibility. Ни случайный список запретных названий, ни Geo-valid Z не заменяют отсутствующий факт. Общая generated topology не менялась и не объявлялась проверенной.

Безопасный final scope теперь fail-closed исключает `generated.farm.*` как source/destination виртуального пути до отдельной положительной аттестации этого producer. Curated core и доказанный population.ingress ROUTE_AREA остаются доступны. Добавлен focused contract на отказ unverified generated target. Это ограничение делает A1 неполным для generated atlas; не выдаётся за выполнение всей092B.

Read-only состояние после continuation: corrected baselineEqual=true; background rowVersion3 — только native load/store reattestation; identity/inventory/progress неизменны; фактическая позиция всё ещё `(24954,50864,-3400)`, `elven.farming.oren04-2019-02s`. Catchup RUNNING, ordinal0, cursor=from=29882020, revision1,rowVersion1 — только замена плана. Native context COMPLETED, policy v2, farm/travel=true, unsupported0, points1, runSpeed125.84. Offscreen canonical operation не состоялась; arrival/receipt replay не доказаны.

Сам `TASK091_092B_COMMIT_DENIAL_ROLLBACK` недостаточен для attribution точного runtime отказа: прежняя версия fixture не записала промежуточные callback count/result. Поэтому ошибочная Krateis classification подтверждена как самостоятельный A1 blocker, но не объявляется доказанной единственной причиной неуспешного transaction probe. В fixture добавлен один diagnostic result для следующего разрешённого запуска; старые результаты не переписаны.

По TEST_PLAN Stage2 бюджет — «ONE native campaign; optional one correction only after a logged root-cause, without reseed». Native + одна continuation выполнены; третьего gameplay запуска, reseed, нового actor или смены inventory/level не было. Узкая разблокировка: положительно аттестовать конкретный публичный outdoor target/source через существующий topology/knowledge owner, перепроверить пригодность по окончательному stored capture, затем отдельно разрешить один дополнительный native proof на той же OWN DB/actor. Новый навигационный движок и расширение harness не требуются. До этого A5 BLOCKED, A2–A4 native transaction/replay остаются NOT_PROVEN; DB-free GREEN не заменяет их.

CPU/heap peaks и отдельное время написания кода не измерялись; это не scale/performance proof. Время Europe/Chisinau (UTC+02:00): build13:22:05→13:22:28, test compile13:22:28→13:22:38, bootstrap13:29:42→13:30:43. Первый native process13:37:39→13:41:19, exit1; continuation13:59:50→14:03:18,exit1. Raw UTC timestamps сохранены в process receipts. Существующие123 SQL/bootstrap source и защищённые releases/DB/JVM не менялись.

## Final safety / artifact verification

Последний focused на финальных classes/JAR — exit0,3 группы,14 scalar intervals; новая проверка generated producer DENY включена. Один full Ant jar, затем только адресные javac/JAR updates — повторной полной сборки не было. Это явно больше одного targeted compile pass: исправления review, native source capture и final fail-closed blocker guard проверялись отдельно. Утверждения «одна компиляция вообще» нет.

Final GameServer.jar SHA256 `de9b3407c8585e99e6df3aa57747f3e1a7cd3737ce9c7062918d7e5e41ba1155`; LoginServer.jar `99b5fd3fcaff236b2c4b61b828b5d20962bd1a95d259325a093c4c8f7ee13133`. Артефакты находятся только в S/build/dist/libs и собственном shadow; production/source dist JAR не заменены. Class/JAR seal и source/copy inputs проверены launcher; JAR не публикуются в Git.

Final source OwnerConfig Verify: exit0,68 files,287 overrides,conflicts0,changed0. Rates.ini, PhantomPlayers.ini, PhantomMerchants.ini, PremiumSystem.ini, OwnerConfig.json и source GameServer/LoginServer.jar —7/7 byte-exact относительно preimages. Source Save/Apply не запускались. Последний read-only census: foreign/own-other connections к092B DB0; после закрытия diagnostic connection её пулов нет; own Java/Javac processes0. Protected078, другие DB/JVM/worktrees, клиент и MariaDB service не изменялись. Полного hash-аудита защищённых поставок не проводилось.

Новый actor создан один раз: profile1/object268480990, class115/level80, пять заранее заданных fixture equipment и200000 Adena до baseline. После failure/continuation новых выдач, EXP/SP или identities не было. Initial и corrected baseline сохранены отдельно; typed offscreen receipt отсутствует, request ordinal0. OWN DB сохраняется без cleanup/reseed для дальнейшего разрешённого разбора.

Default `git diff --check` сообщил CR-at-EOL в изменённых CRLF Java строках. Повторная exact-path проверка с process-local `-c core.whitespace=cr-at-eol` — exit0; repo config/EOL policy не менялись. Кириллица и escaped Cyrillic проверяются отдельно по всему final изменённому scope; результат ниже.

- mojibake-маркеры в изменённых файлах проверены: полный заданный набор,13 файлов,0 совпадений (`rg -f`, exit1 означает отсутствие matches).
- escaped Cyrillic в изменённых файлах проверены: все6 Unicode/XML regex-паттернов,13 файлов,0 совпадений (`rg -f`, exit1).

Первая inline PowerShell проверка patterns завершилась parser error до поиска: smart quote внутри mojibake-маркера. Рабочие проверки выполнены через отдельные literal pattern files, без изменения проверяемого текста. Чужих legacy совпадений в этих13 файлах не обнаружено.

Commit/push не выполнялись: обязательного product результата A5 нет. HEAD остаётся исходным ac593194 на feature/phantom-world; tracked worktree намеренно содержит перечисленные production/test правки. Report-only publication не выполнялась. Следующие092C/093/094 не начинались.

Final local HEAD, `origin/feature/phantom-world` и live remote ref совпадают с исходным ac593194. Первый `git ls-remote` в sandbox получил network failure; host read-only retry exit0 подтвердил live SHA. Это parity baseline, не публикация изменённого кода.

## Exact changed-file allowlist

Относительно M:

- `java/org/l2jmobius/gameserver/phantoms/background/PhantomOffscreenOutdoorTravel.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomOffscreen092Checks.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTask091Lane.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreen092BChecks.java`
- `test/offscreen_canonical092b.py`
- `docs/phantoms/reports/PHANTOM-OFFSCREEN-CANONICAL-TRAVEL-092B.md`

В delivery также входит неизменённый owner package `docs/phantoms/tasks/PHANTOM-OFFSCREEN-CANONICAL-TRAVEL-092B/` — девять exact files, включая manifest. Другие untracked packages не включаются.

## Git commands и основание

Git использовался только для разрешённого root AGENTS/workflow bounded branch/scope guard и запрошенного task live parity. Точные команды: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git worktree list`, `git rev-parse origin/feature/phantom-world`, `git status --short --untracked-files=no`, `git ls-remote origin refs/heads/feature/phantom-world` (sandbox failure, host retry0).

Из root также выполнялся `git diff --numstat -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`.

`git diff --check --` вызывался из M с exact implementation/test paths выше, без report; `git -c core.whitespace=cr-at-eol diff --check -- $paths092b` — из root, где `$paths092b` является точным allowlist13 путей выше с префиксом M. Read-only reviewer использовал только `git diff --` с восемью exact production paths. Никаких git add/commit/push/branch/merge/rebase/reset/restore/clean/stash, broad diff или history inspection не было. Полный staged diff/tracked-clean delivery NOT_RUN: staging и публикация не выполнялись.
