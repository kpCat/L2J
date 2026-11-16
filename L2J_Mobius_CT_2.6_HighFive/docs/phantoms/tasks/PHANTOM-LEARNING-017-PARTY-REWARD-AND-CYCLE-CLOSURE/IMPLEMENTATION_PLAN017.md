# PHANTOM-LEARNING-017 — план реализации для согласования

> Исполнитель: текущий агент; после согласования применять `superpowers:executing-plans`, последовательно по задачам ниже. До ответа владельца исходники не изменять.

**OWNER_IMPLEMENTATION_GATE: REQUIRED.** Это план после read-only аудита, не результат реализации и не FULL017. Дата аудита: 16.11.2026. Задача №11/13; после выдачи остаются 2 задачи до пересмотра бюджета. Goal016 остаётся принятым `PARTIAL016_NATIVE_COLLECTED_DIRECTOR_CLOSE_FAILED`.

**Цель:** исправить доказанный отказ завершения NO_FIT и потерю диагностических доказательств Party, не назначая неизвестным фактам обучающий reward. Исправление собственно Party reward допускается только после установления конкретного дефекта; сейчас такого доказательства недостаточно.

**Архитектура:** существующие collector/runtime, V1 codecs, `ActionAdvantageV2`, shared owner и консольные runners. Новая диагностика — в TEST-коде; отдельное восстановление завершения — immutable `repair-closure017` вне исторической кампании. Второй training framework, SQL schema и новые production owners не нужны.

**Стек:** JDK25, Ant, Windows PowerShell 5.1. Обнаружены JDK25.0.4.1 и Ant1.10.17. Спецификация: все 13 исходных файлов текущего пакета, в порядке START_HERE; их MANIFEST.sha256 совпал целиком.

## 1. Прочитанные источники и существующие аналоги

- Корневые AGENTS.md основного checkout, SOURCE004 и TEST006; README.md; High Five `CODEX_WORKFLOW_CONTRACT.md` и релевантный текущий статус. AGENTS.md выше корня и дополнительных серверных AGENTS.md не найдено. Отдельных code-map/pattern-файлов для этого learning scope не найдено; повторный общий поиск не требуется.
- Полный пакет017; RESULT016, NEGATIVE_TESTS016, релевантные EVIDENCE016/COMMANDS016 и приватные G0 receipts016. В основном checkout RESULT016/EVIDENCE016 отсутствуют; прочитаны их настоящие версии из SOURCE004.
- `PhantomAutonomousTrainingNative.collectEpisode`, `diagnoseCohort`, `immutable`, CLI и incremental016 contracts; `PhantomLearning001ShadowNative.Runtime.outcomeActors`, freeze/drain и terminal boundary.
- `TerminalRewardReceiptV1` целиком, `TacticalEpisodeV3`, `TacticalReceiptV3`, `Selection`, `LegalActionV3`; `ActionAdvantageV2` целиком; `TacticalStoreV3.autonomousSources/freezeAutonomous` и публичные encode/decode.
- `Training.ps1`: FIT016, reservation/result/checkpoint, cold checkpoint, dispatcher; `TrainingOwner.ps1`: phase save, physical path/hash/atomic write helpers; существующие branches `Test-Training.ps1`; релевантные Ant targets и compile/output contract.
- Аналог №1: штатный `diagnose-cohort` уже восстанавливает настоящие selection/terminal и считает V2 без SQL. Его расширить, не добавлять этот отчёт в trainer/store.
- Аналог №2: EVAL012 сохраняет timing/observation и отдельный native-authority witness; helpers `PhantomEvaluationReplayV2` и `PhantomLearning005Native.recordOutcomeEffect` подтверждают локальный формат и проверки. Старые EVAL файлы/API не менять.
- Аналог №3: `Write-TrainJson`/physical guards, phase receipts и существующие AST/fixture contracts PowerShell. Переиспользовать атомарность и проверки путей, не запускать private методы через Reflection.

## 2. Свежая граница Git и сохранность

| Объект | Наблюдение аудита |
|---|---|
| MAIN checkout | `feature/phantom-world`, HEAD `7e997636dbac9d5e1754e00c793cd6fac858b0c6`; чужой tracked/untracked dirt сохранён |
| SOURCE004 | `feature/phantom-world`, HEAD `e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177`; tracked dirt отсутствует |
| TEST006 | `feature/phantom-world`, HEAD `42d6b675a9cf57faa4df8a74cc52a8b4adda97fa`; старый runtime/source overlay016 сохранён |
| Fresh origin и независимый GitHub API | `eed183b70dd8eb6e1a6db98b3d5fb825281b5a2f`, parent `e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177` |
| GitHub compare e8 → eed | 1 commit, только 10 файлов Studio; learning исходники не изменены |
| TEST safety | OFF; owned processes 0; порты33357/18001/19301 свободны; pointer12 SHA `ACC21BED707B518FB58E23FEC006E7D658723763C73C237A039D950E14C9B010` |
| Geo/protected manifests | `A8A4B73BC7B53BED28F545CD889F489492B585B369F8E84988F8DB8A91C973D5` / `246AC12CD718E698B18E6AB2B338622B24D505F75BA492EB1CD348BCA2E335CD` |

До разбора raw создана внешняя побайтовая копия: 1721 файл / 27 999 227 байт, SHA каждого исходника до/после и копии совпали. SHA backup-manifest017.json: `20A058297C91D43EC48E5241DB51A73CD55D22EBBBC15B2C741FF00264A02C72`. Дополнительно скопированы 102 файла / 1 128 240 байт: два настоящих native attempts и прежние G0 evidence. Все referenced bytes в 51 `settled016.json` и resultSha проверены на копии.

Исторические SQL before/after обоих G0 архивов: 183 строки, SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`. Это проверка сохранённых receipts; **новый cold SQL readback ещё NOT_RUN**, БД во время аудита не запускалась. Полное повторное физическое хеширование Geo203/protected207 и свежая SQL lineage входят в G0 после согласования.

Приватный аудит находится вне checkout, в evidence текущего чата: backup manifests, `raw-audit017.json` с 84 индивидуальными event rows, `canonical-census017.tsv`, `safety-audit017.json` и использованные read-only утилиты. Owner nonces, SQL строки, inventories и сырые payload в Git не помещать.

## 3. Что действительно установлено по Party

Все 17 Party эпизодов — `NATIVE_KILL`, без наблюдавшейся смерти, с пятью участниками. Во всех 17 ровно у одного участника, класса94, `nativeCreditedExp=null` и `nativeCreditedSp=null`; у остальных четырёх earnings известны. В `TerminalRewardReceiptV1` это обязательно нарушает условие `Canonical credited earnings UNKNOWN` (исходник, строка142).

| Slot, нумерация с0 | Split | Режим | Paid = excluded | Null earnings actors |
|---:|---|---|---:|---:|
|17|TRAIN|STOCK|4|1|
|18|TRAIN|EXPLORE|4|1|
|19|TRAIN|EXPLORE|4|1|
|20|TRAIN|STOCK|4|1|
|21|TRAIN|STOCK|5|1|
|22|TRAIN|EXPLORE|4|1|
|23|TRAIN|EXPLORE|2|1|
|24|TRAIN|STOCK|3|1|
|25|TRAIN|STOCK|5|1|
|26|TRAIN|EXPLORE|5|1|
|27|TRAIN|EXPLORE|3|1|
|28|TUNING|STOCK|5|1|
|29|TUNING|STOCK|5|1|
|30|TUNING|STOCK|4|1|
|31|AUDIT|STOCK|5|1|
|32|AUDIT|STOCK|5|1|
|33|AUDIT|STOCK|4|1|

Итого Party: TRAIN43, TUNING14, AUDIT14 = **71** исключённое действие. По сохранённым полям у всех71 совпадают selected actor profile/objectId/epoch с episode, DAMAGE target objectId/epoch с incarnations и event membership. Это ограниченная проверка доступных полей, не доказательство полной законности времени/owner.

**Граница доказанности:** найден конкретный неизбежно нарушенный predicate, но не доказано, что именно он был первым выброшенным исключением. Для84 excluded actions нет архивированных HP/MP write intervals; для18 эпизодов без terminal.bin нет полного terminal boundary. В native attempts отдельные timing и `firstUnprovenReason` в момент snapshot не найдены. `outcomeActors` превращает earnings в UNKNOWN, если current отсутствует, epoch изменился, previous/current overflow или current.firstUnprovenReason != NONE. Какая ветвь сработала у класса94, в raw016 не сохранено. Нельзя объявлять PHASE_DEADLINE, owner failure или early freeze установленной причиной.

Остальные **13 Solo exclusions** находятся в одном эпизоде slot42 / TRAIN STOCK / NATIVE_KILL. У него нет terminal.bin; доступные actor/target/event/earnings проверки не выявили причину. Точный отказ — `UNPROVEN_MISSING_SOURCE`, перечислить отсутствующие native intervals/boundary. Никаких выдуманных timestamp для вызова constructor.

Полная раскладка399: **247** actions внутри действительных terminal receipts, **84** validation exclusions, **57** paid effects в пяти `NO_LEGAL_TARGET:NO_SAFE_SOLO_OPENING`, **11** в `REQUESTED_DRAIN`. Последние68 не получают terminal reward. В331 actions, дошедших до terminal-validation gate, silent missing timing до create не наблюдается:247 +84 закрывают весь набор. Потеря timing при архивировании84 actions — отдельная проблема. Пригодных Party terminal сейчас0; 46 Party actor kill credits не переименовывать в уникальные kills, по cohort их29.

## 4. Точный census и предел FIT

`Selection.restore`, `TerminalRewardReceiptV1.restore`, `ActionAdvantageV2.freeze/targets/sufficient` выполнены только в памяти. Три соответствующих исходника отдельно перекомпилированы без изменений во внешний audit classpath. Затем штатный `diagnose-cohort` на исходном TEST подтвердил тот же datasetSha `bcdf86051d090a9ac46f533e287c89b87a71dace173e9cc3c074878c005402b6` и `sufficient=false`. SQL store freeze, trainer и FIT не вызывались.

| Scope | Split/режим | Slots = CUT = settled | Paid | Terminal | Exact selection+terminal | Targets с >=3 controls | Gradient/held-out |
|---|---|---:|---:|---:|---:|---:|---:|
|SOLO|TRAIN STOCK|10|98|8|8|baseline, не target|0|
|SOLO|TRAIN EXPLORE|12|124|9|9|7|7 gradient|
|SOLO|TUNING|6|47|6|6|6|6 held-out|
|SOLO|AUDIT|6|59|4|4|4|4 held-out|
|PARTY|TRAIN STOCK|5|21|0|0|нет baseline receipts|0|
|PARTY|TRAIN EXPLORE|6|22|0|0|0|0|
|PARTY|TUNING|3|14|0|0|0|0|
|PARTY|AUDIT|3|14|0|0|0|0|

Все51 selection.bin присутствуют также в samples; fallback0; у всех27 terminal ровно одно совпадение selection. No matching selection среди27 =0. У24 slots отсутствует terminal, это не отсутствие selection.

Класс94/Solo:5 TRAIN controls,5 допустимых exploratory targets, TUNING3/AUDIT3. Класс110/Solo:3 controls,4 exploratory pairs, но2 из4 имеют только2 чужих control blocks; допустимы2 gradient, TUNING3/AUDIT1. Три из7 gradient имеют selected != stock. TRAIN classes2, TRAIN scopes1.

Итого сейчас **7/6/4** против порогов **16/8/8**, недостача9/2/4 и отсутствует Party scope. Критерии `TacticalStoreV3` применения/отсутствия старого credit дополнительно перепроверить read-only SQL после gate. Это не увеличит перечисленные offline counts.

Грубая исходная граница15 gradient из PRE_AUDIT уточняется до **<=13**:7 уже допустимых Solo + максимум6 Party. Party controls не исправляют2 Solo targets: другой cell/context. AUDIT по-прежнему **<=7**. Даже идеальный Party rescue не позволит FIT; минимум недостаёт3 gradient и1 AUDIT. Никакого изменения порогов, propensity, target objective или старых controls.

Проект следующего одного нового dataset, **не запуск017**:3 заранее выбранных по законности exact cells, минимум2classes/2scopes; на cell6 независимых TRAIN STOCK blocks +8 TRAIN exploratory +5 TUNING +5 AUDIT =72slots. Теоретический потолок24 gradient/15 TUNING/15 AUDIT. Это coverage-проект, не обещание достаточности: наблюдаемая Solo attrition7/12 exploratory, Party0/6; положительная attainable bound Party сейчас не доказана. Статус будущего плана `UNPROVEN_PARTY_YIELD`, live до подтверждения корректного Party writer не разрешать. При прежней средней стоимости016 72 slots ориентировочно100,46 native-минут, без гарантии и без учёта новых накладных расходов; в лимит01720мин это не входит. Окончательная будущая оценка обязана учитывать exact control-block attrition и не выбирать сцены по исходам.

## 5. Выбранный подход и ограничения

Рекомендуется **bounded PARTIAL017 с исправлением NO_FIT closure и диагностической полноты**, GS0. Он следует сохранённым фактам. FULL017 заранее недостижим по нынешним evidence: настоящего принимаемого Party receipt и exact replay71 rejected actions нет.

Ослабление V1 all-actor EXP/SP проверки сейчас не обосновано; новая reward version/migration потребовала бы отдельного owner решения. Генерация timestamps или повтор51 боёв недопустимы. Новый единичный Party smoke пока тоже не включён: штатного Party-only native CLI нет, прежняя инфраструктура запускает frozen cohort; расширять её ради неподтверждённого дефекта в этом плане не предлагается.

Два возможных прямых исправления в текущей причинной цепи: (1) сохранение причины/фактов вместо одного имени исключения; (2) DONE016/NO_FIT completion. Если впоследствии обнаружится иной доказанный behavioral bug Party, сначала пересмотр этого плана и его allowlist; текущий документ не даёт автоматического разрешения на третье исправление.

## 6. Exact allowlist после согласования

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`. Разработка в существующем SOURCE004 / `feature/phantom-world`; MAIN и исторический TEST overlay не использовать как writable baseline.

### Исходники: ровно5 файлов

| Путь | Разрешённое изменение |
|---|---|
|`test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`|Bounded diagnostics/replay/census на базе diagnoseCohort; immutable raw timing/Row и classified refusals только для будущих ещё не settled episodes; focused contract017 CLI. Нет новых rewards для016.|
|`test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java`|**Явное расширение максимального allowlist пакета, требует согласования владельца:** passive TEST observer существующих previous/current snapshots и known-предиката в outcomeActors. Не менять результат snapshot, freeze/drain или production mechanics.|
|`tools/phantom-runtime/training/TrainingOwner.ps1`|Exact version/proof-bound DONE016 validation; проверка immutable NO_FIT closure и atomic/idempotent output вне истории.|
|`tools/phantom-runtime/training/Training.ps1`|Cold NO_FIT guard до обычной FIT работы; отдельный repair entry без Java train/SQL credit/GS и без перезаписи checkpoint/journal016.|
|`tools/phantom-runtime/training/Test-Training.ps1`|Focused phase/closure/source-preservation contracts в существующем runner.|

`TerminalRewardReceiptV1.java`, `ActionAdvantageV2.java`, `TacticalStoreV3.java`, `TacticalTrainerV3.java`, config templates, build.xml, production Combat/Party/SkillData и остальные исходники — **вне allowlist записи**. Runtime observer в TEST-файле не даёт разрешения менять production PlayerNativeEvidence.

### Документы: ровно7 создаваемых/изменяемых путей

В `docs/phantoms/tasks/PHANTOM-LEARNING-017-PARTY-REWARD-AND-CYCLE-CLOSURE/`:

1. `IMPLEMENTATION_PLAN017.md` — этот согласуемый план.
2. `RESULT.md` — итог, G0–G8, diagnosis/census/director/acceptance/handoff в отдельных разделах.
3. `EVIDENCE.json` — публичные bounded агрегаты, immutable SHA, provenance и ограничения.
4. `COMMANDS.md` — реальные команды, exit codes, расходы и все ошибки оркестрации.
5. `READBACK.json` — независимая публикация/source/evidence readback.

Ещё два пути: `docs/phantoms/reports/PHANTOM-LEARNING-017.md` и одна новая запись в `docs/phantoms/COMBAT-AI-ROADMAP.md`. Исторические записи не менять. Отдельные ACCEPTANCE_RESULT/DATA_CENSUS/DIRECTOR/HANDOFF разрешённо объединены в RESULT по HANDOFF.md; другие выходные документы автоматически не добавлять.

**Bounded exception к8–10 файлам:**5 code +7 delivery docs =12 изменяемых файлов. Дополнительно допускается публикация **без изменения байтов**13 входных файлов этого нового пакета017: `START_HERE.md`, `TASK.md`, `PRE_AUDIT.md`, `PARTY_REWARD_CONTRACT.md`, `DATA_SUFFICIENCY.md`, `DIRECTOR_CLOSURE.md`, `PLAN.md`, `TEST_MATRIX.md`, `ACCEPTANCE.md`, `GIT_AND_PRESERVATION.md`, `HANDOFF.md`, `SOURCE_POINTERS.md`, `MANIFEST.sha256`. Сейчас они существуют только в MAIN как untracked входы владельца; их SHA сверять с исходным manifest. Итого commit inventory ограничен25 exact paths. Это одна recovery-задача с двумя явно заказанными компонентами, без UI, cleanup и новой подсистемы.

Приватные дополнительные инструменты аудита и fixtures допустимы только во внешнем evidence017; в Git не входят. Реальный runtime write scope ремонта — только отдельный `repair-closure017` во внешнем evidence, без других новых TEST runtime roots. Старые016 raw, campaigns, models, checkpoints, SQL183, EVAL012 и бюджеты read-only.

## 7. Последовательность реализации после owner gate

### A. G0 и фиксация входов

- [ ] Повторить fresh fetch/GitHub HEAD и scoped dirt/ancestry. SOURCE004 fast-forward до свежей feature через `git merge --ff-only origin/feature/phantom-world`; это единственное предлагаемое обновление baseline, без merge commit/rebase/новой ветки. При несовпадающем dirt остановиться, не переносить чужие изменения.
- [ ] Сохранить внешние byte backups/SHA allowlist SOURCE и immutable входов TEST. Повторить safety и физические manifests.
- [ ] Для свежего SQL183/selected/Adam readback использовать существующий owned TEST Export/G0 five-SELECT путь, без train/eval/promotion. Operational logs/export receipts только новые, старые SQL receipts не заменять. Доказать read-only запросы и held exit своей краткой SQL сессии; после неё OFF/portsfree. Это отдельная G0-проверка, не часть repair completion.

### B. Диагностика Party: focused RED → GREEN

- [ ] В существующий native contract runner добавить одну focused группу: rejection UNKNOWN earnings сохраняет точный check; отсутствие timing даёт MISSING_SOURCE, никогда fabricated receipt. Прежний writer должен дать RED по отсутствию structured facts, не по синтаксису/компиляции.
- [ ] Добавить passive observer в Runtime с предлагаемым новым package-local setter `terminalEvidenceObserver017(Consumer<Map<String,Object>>)`; передавать факты из **уже полученных** previous/current snapshot и known Boolean. Не вызывать дополнительный snapshot(): он сам может инвалидировать native phase по времени. Consumer только собирает bounded facts в памяти; запись после drain в collector.
- [ ] В collector для будущего незавершённого episode сохранять immutable full replay ingredients: `TacticalStoreV3.encode(Row)`, native timing, boundary, settlement/selection refs и snapshot diagnostics. Переиспользовать immutable/hash/physical bounds и существующий TEST JSON parser. Отдельно считать missing timing before create, validation failure, empty eligible, no matching selection и source incomplete. Не писать в существующий settled016.
- [ ] Диагностический код исключений — bounded allowlisted условие/категория с проверенными скалярами; не произвольный Exception.Message/JDBC payload. Source016 без недостающих файлов остаётся UNPROVEN, без вызова constructor с догаданными параметрами.
- [ ] Повторить authentic read-only census по51slots и84excluded. Проверить hashes frozen sources до/после. Это GREEN полноты диагностики, **не** GREEN исправленного Party reward/G5.

### C. DONE016 и независимый cold NO_FIT closure: RED → GREEN

- [ ] Существующим PS fixture воспроизвести `TRAIN013_STATE_VERSION_OR_PHASE` для конкретного DONE016. Группа отрицаний: DONE017/SOME999, DONE016 legacy013/014/015, отсутствующий/чужой checkpoint.
- [ ] В Save-TrainCycleState013 разрешить точный DONE016 лишь для frozen016 campaign/dataset с proof NO_FIT, fits0, completedCycles1, pending=false и совпадающей immutable reservation/result/checkpoint/parent lineage. Старый regex не расширять до произвольных цифр. Предлагаемый новый proof-validator поместить в том же TrainingOwner.ps1; без нового абстрактного слоя.
- [ ] Добавить новый явный entry `Training.ps1 -Action RepairClosure017 -Root <existing TEST> -RepairEvidenceRoot <external017> -ColdLineageProof <fresh G0 proof>` до общего startup/config-writing/error-journal пути. Проверки source/config/owner/nonce/physical paths обязательны; ошибка ремонта не должна дописываться в failure history016.
- [ ] Исторический FIT, reservation/result/checkpoint и оба бюджетных журнала только читать. Старые TEST source/JAR pins проверять как frozen016; новый исполнитель017 проверять отдельным approved source manifest. Не объявлять новый source hash равным старому и не обходить drift guard обычного Resume.
- [ ] Создать один immutable `repair-closure017` marker-last: schema/version, source file SHA, checkpoint/result/reservation refs, owner/config/build/selected/terminal/Adam/pointer/protected pins, статус `COMPLETED_NO_FIT`, optimizer delta0, trainingCredit=false, EVAL/promotion=false. При повторе только exact readback; при изменении любого pin — отказ. Старые director.phase=FIT и ошибка016 остаются историческими фактами.
- [ ] Cold ветка с уже существующим checkpoint должна распознавать достигнутый NO_FIT до dispatch train и до повторной записи checkpoint; reuse существующей reservation, без новой FIT reservation. На историческом TEST проверять отдельным repair entry, обычный Start/Resume не выполнять.
- [ ] Focused fixture покрывает missing/tampered checkpoint/result/reservation, changed model/optimizer/source/config/owner, unknown pending, fits>0, external/reparse path и interrupted marker. Во всех отказах ноль Java/SQL/GS callbacks и неизменные source bytes. Повторный cold вызов/STOP–RESUME на копии не создаёт FIT/credit/новый цикл.

### D. Проверки и поставка

- [ ] Последовательные Ant: `phantom-learning016-test`, `phantom-autonomous-training-test`, `phantom-learning008-test`, `phantom-learning007-test`. Затронутые015 contracts добавлять адресно; broad015 dependency tree только при конкретной необходимости. Не `ant verify`.
- [ ] Новый focused Java `party-reward-contract017` запускать существующим classpath после compile-tests; build.xml не менять. PS: новая группа `-Closure017`, затем `-Action Test-Cycle013`, `-Readiness016`, `-Headless013`, `-Action Test-Config`, `-Action Test-Atomic` по фактическим существующим формам runner. Сборка jar только в SOURCE004; в TEST ничего не устанавливать поверх frozen016.
- [ ] Fresh-source rebuild/replay должен подтвердить исходные247 terminal actions,27 samples и7/6/4. Отдельно проверять wrong actor/target/epoch/instance/skill/event/time/lethal и UNKNOWN earnings штатным constructor в contract fixtures; synthetic PASS явно NOT_NATIVE.
- [ ] Сверить фактические SQL lineage restrictions read-only, fresh SQL183/selected14/Adam903/223/TEST2/pointer12/Geo203/protected207 и все frozen manifests. Завершить owned процессы штатно; OFF/portsfree.
- [ ] Final artifact scope guard: exact25 paths, отдельные проверки полного набора mojibake-маркеров и escaped Cyrillic; реальные exit codes, disk/CPU/RAM где измерены, UNKNOWN без подмены нулём.
- [ ] Scoped `git add -- <exact paths>`/commit, fresh fetch, обычный `git push origin HEAD:feature/phantom-world`. При параллельном продвижении и non-FF отказе не force/rebase; остановиться на согласованной безопасной границе. Самостоятельный GitHub HEAD/parents/diff и full-body всех изменённых исходников/публичных evidence с SHA/blob readback.
- [ ] Отдельные вердикты TECHNICAL/PARTY_REWARD/FIT_READINESS/DIRECTOR_CLOSE/GAME_ADVANTAGE/PRODUCTION. При нынешней нехватке сырья ожидается PARTIAL017, TRAINED_MODEL=NO, GAME_ADVANTAGE=NOT_EVALUATED, PRODUCTION=OFF. Manual final gate REQUIRED; следующий Goal не начинать.

## 8. Бюджет и стоп-условия

017 до этого gate: GS0, native minutes0, FIT0, EVAL0, promotion0; только bounded file IO и краткие Java read-only процессы, завершившиеся exit0. Native расходы016 остаются2GS/71,1598мин, не переносятся и не обнуляются.

После утверждения этого плана также **GS0/FIT0/EVAL0/promotion0**. Исходный максимум1GS/20мин не является обязанностью его расходовать. Один новый native smoke потребует сначала конкретного безопасного Party-only проекта и уточнения этого плана; текущий gate его не включает. Непроверенное происхождение UNKNOWN earnings и exact timing84 — ограничения доказательств, не разрешение на изменение V1 или создание новой кампании.

## 9. Команды Git, уже использованные в аудите

Разрешены текущим заданием017 и bounded правилами AGENTS.md. Из MAIN выполнены:

```text
git rev-parse --show-toplevel
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --short
git worktree list
git fetch origin feature/phantom-world
git rev-parse origin/feature/phantom-world
git merge-base HEAD origin/feature/phantom-world
git status --short -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-017-PARTY-REWARD-AND-CYCLE-CLOSURE/IMPLEMENTATION_PLAN017.md
```

Первый fetch: exit1, `.git/FETCH_HEAD: Permission denied`; повтор с sandbox escalation exit0. Глобальный Git config не менялся. Для каждого из двух exact checkout paths `D:\Codex\repos\L2J-learning-004` и `D:\Codex\repos\L2J-learning-006` сначала выполнены `git -C <path> rev-parse HEAD`, `git -C <path> symbolic-ref --short HEAD`, `git -C <path> status --short -uno`: exit128 dubious ownership. Затем те же команды успешно выполнены с **одноразовым** `-c safe.directory=<тот же exact path>`:

```text
git -c safe.directory=D:/Codex/repos/L2J-learning-004 -C D:\Codex\repos\L2J-learning-004 rev-parse HEAD
git -c safe.directory=D:/Codex/repos/L2J-learning-004 -C D:\Codex\repos\L2J-learning-004 symbolic-ref --short HEAD
git -c safe.directory=D:/Codex/repos/L2J-learning-004 -C D:\Codex\repos\L2J-learning-004 status --short -uno
git -c safe.directory=D:/Codex/repos/L2J-learning-006 -C D:\Codex\repos\L2J-learning-006 rev-parse HEAD
git -c safe.directory=D:/Codex/repos/L2J-learning-006 -C D:\Codex\repos\L2J-learning-006 symbolic-ref --short HEAD
git -c safe.directory=D:/Codex/repos/L2J-learning-006 -C D:\Codex\repos\L2J-learning-006 status --short -uno
```

Commit/push/merge/branch/worktree mutations не выполнялись. Независимые GitHub GET: branches/feature/phantom-world и compare e8→eed; первая попытка URL с encoded slash отклонена connector, корректный URL прочитан. Проба штатного diagnose-cohort на неполной внешней копии отказала physical-root guard; успешный read-only запуск выполнен на настоящем TEST после backup. Два первоначальных сбоя внешнего audit reader (не episode directory и base для manifest paths) исправлены в reader; raw016 не менялось. Это ошибки оркестрации, не behavioral RED реализации.

## 10. Решение, запрашиваемое у владельца

Согласовать этот точный scope, включая дополнительный TEST-файл `PhantomLearning001ShadowNative.java`,25-path delivery inventory и bounded fast-forward SOURCE004. Ожидаемый честный исход при нынешнем raw — PARTIAL017 с доказанным NO_FIT repair и улучшенной диагностикой; разрешение на ослабление V1/reward migration или live Party smoke не запрашивается этим планом. До ответа владельца — только проверки этого документа и неизменности аудированных входов.
