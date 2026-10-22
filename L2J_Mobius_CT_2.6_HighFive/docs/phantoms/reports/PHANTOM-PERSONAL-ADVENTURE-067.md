# PHANTOM-PERSONAL-ADVENTURE-067 — итог

22.10.2026. **ADVENTURE067_CORE=PASS; FULL067=PARTIAL.** Реализованы личные Kamaloka/Labyrinth, Wondrous Cubic, fishing assist, Henna и положительные augmentation stats. Rift — PARTIAL_DESIGN с установленными lifecycle-причинами; Soul Crystal overlevel — NOT_APPLICABLE. `CLIENT_QA=DEFERRED_BY_OWNER`: владелец не входил в игру, сетевой клиент не имитировался. M1–M7 и прежние PARTIAL этим результатом не пересматриваются. Следующий Goal/R2 не начинается.

Baseline local/live GitHub: `8631faf3d4ce7f0f2256cb312af99659c008560d`, `kpCat/L2J`, branch `feature/phantom-world`. Production source commit: **`2939b7054caeafbbfdc913ad5ebe4785c618c8a1`**. Normal push этого commit выполнен с exit0; GitHub connector повторно подтвердил тот же live SHA. Финальные документы публикуются отдельным потомком; его local/remote binding сохраняется вне Git в `PUBLICATION067.json`, без самоссылочного SHA в исходниках.

## Приёмка по функциям

| Gate | Итог | Проверенное поведение и граница доказательства |
|---|---|---|
| G0 | PASS | Проверены live HEAD, ancestry, исходный dirt и exact allowlist41. Только собственные schemas067/порты/runtime; protected8, protected Geo203 и шесть исходных task docs совпали по SHA256. |
| G1 | PASS | Новым детям нужны master, allowlist, child и exact accessLevel0. Phantom, Background identity lease, headless, GM/nonzero access, foreign, malformed config и OFF получают отказ. Ни права Player, ни packet/auth guards не менялись. |
| G2 | PASS | Native Kamaloka57/level23 и Labyrinth73/level29: solo enter, allowed entrant, родной world/reuse, native escape32496, в том числе после childOFF. Настоящая Party2 вошла в один world. Wrong level, foreign и повторный reuse отклонены. Native completion callback/cleanup проверены; полный бой и boss reward не заявляются. |
| G3 | PARTIAL_DESIGN | Rift production не изменён. Death/restart-point не завершает Rift надёжно; room timer/kill не сериализованы, room reservation не имеет атомарного владельца. Маленький singleton Party не устраняет эти причины. Глобальный min-party остаётся штатным. |
| G4 | PASS | Настоящий item10632 / ItemSkills / skill2510 дал штатную награду, немедленный repeat запрещён. Canonical stamp24h сохраняется; effective60m работает для старого stamp, границ59:59.999/60:00, subclass и DB-backed Player reload. OFF/foreign возвращают24h; нет ручной выдачи fragments. Сетевой пакет UseItem не имитировался. |
| G5 | PASS_NATIVE_BOUNDED | После native manual START расход bait1/MP1; существующий tick вызывает настоящие Pump/Reel с MP2/reuse2s/FishShots/resist. Natural WIN дал7729×1; late tick не дал второй награды. Четыре mode/deceptive сочетания и контекстные stop guards проверены отдельно. Полный сетевой logout не заявляется. |
| G6 | NOT_APPLICABLE | Q00350, LevelUpCrystalData и Attackable.addAbsorber не содержат player-overlevel запрета. Не добавлен пустой switch. NPC/stage/quest/absorb/skill/chance и существующий multiplier сохранены. |
| G7 | PASS | Native dye1 STR+1/CON−3: OFF/IGNORE_NEGATIVE/FLIP_NEGATIVE, cap+5, повторный remove, reload и foreign. Расчёт через recalcHennaStats, без XML/template мутаций. NPC fee/acquisition route не менялся и отдельно не запускался; клиентский tooltip может остаться штатным. |
| G8 | PASS | Native augmentation131073: положительные option1 PDef15.45 и option2 MDef12.33 масштабируются factor2. Duplicate apply, remove/reapply, shared template, ordinary Options, mixed negative/Mul/Sub/condition и bounds проверены. Skills/proc/chance/glow не масштабируются. |
| G9 | PASS | Чистый committed-source JAR, final-JAR native suites, standalone candidate,203 физических Geo copies, два обычных Main с теми же schema/JAR и две штатные остановки. Own JVM0/listeners0. Source опубликован normal push только feature branch. |

Подробный Rift root cause: [RIFT_ANALYSIS067.md](../tasks/PHANTOM-PERSONAL-ADVENTURE-067/RIFT_ANALYSIS067.md). Это граница реализации, а не таймерный тест, оставленный ждать владельца.

## Реализация и настройки

Общий персональный допуск переиспользует PersonalCharacterQoLService и существующий strict config snapshot. Невалидный master/allowlist закрывает всё; ошибка нового дочернего блока закрывает только его. Независимый review обнаружил реальную дыру: access profiles10–60 могут иметь isGM=false. Исправлено требование exact access0, подтверждено RED→GREEN на10 реальных profiles плюс null. Player accessLevel не менялся.

Admission сохраняет один immutable roster и identity настоящей Party; изменение состава отклоняется перед входом. Solo owner хранится только в KamaWorld, поэтому native escape остаётся доступным после выключения льготы. Все native level/reuse/max/range/instance требования сохраняются. Остальные instance scripts не переписывались.

Cubic вычисляет last-use как `stamp − storedReuse`, оставляя stock24h в persist. Одну effective policy читают UseItem, виртуальный Player reuse query для ItemSkillsTemplate и restore/display. При неизвестной provenance отказ остаётся штатным; используется максимум действующих cubic stamps. Чужие shared-group timers не очищаются. У stock10632 shared group0, поэтому отдельный shared-group packet в этой конфигурации не возникает. XML и reward skill неизменны.

Fishing использует существующий1s owner/tick и known skills1313/1314 через useMagic, без своего scheduler, прямого damage и автозаброса. `mode == deceptiveMode` выбирает Pump, иначе Reel. На текущий оплаченный cast допустима уже израсходованная последняя приманка. Смена rod object/instance, наблюдённый OFF, смерть, transform и offline навсегда прекращают помощь до нового ручного cast. Пропускаются занятые cast/queue/bow delay; удаляется лишь совпавший неудачный automatic queue candidate. Узкая гонка ручной постановки того же skill в очередь не доказана сетевой сессией.

Henna каждый раз пересчитывается с нуля и stock cap5. Augmentation создаёт отдельный FuncAdd только для точного типа, положительного конечного value, с теми же stat/order/owner/condition. Default Options.apply(Player) остаётся штатным; remove использует прежний owner. Shared Options не мутируются.

Файл `game/config/Custom/PersonalCharacterQoL.ini` в поставке равен committed source. **Master=False, allowlists пустые, все новые дети OFF.** Существующие настройки сохранены; сокращены лишь повторяющиеся комментарии этого файла. Для личного использования должны быть явно заданы собственный account/character ID и нужные значения; требуется restart GameServer. Никаких аккаунтов владельца не угадывали и не переносили.

| Ключ | Допустимое значение / shipped default |
|---|---|
| EnablePersonalSoloInstanceEntry | True/False; False |
| EnablePersonalWondrousCubicCooldown | True/False; False |
| PersonalWondrousCubicCooldownMinutes | 1..1440;60 |
| EnablePersonalFishingAssist | True/False;False |
| PersonalHennaNegativeMode | OFF / IGNORE_NEGATIVE / FLIP_NEGATIVE;OFF |
| EnablePersonalPositiveAugmentationMultiplier | True/False;False |
| PersonalPositiveAugmentationMultiplier | конечное1.0..5.0;1.0 |

## Поставка, binding и сохранность

Самостоятельный каталог: **`D:\PhantomWorld\Personal-Adventure-067-candidate`**. В нём START.cmd, STATUS.cmd, STOP.cmd, START_HERE_067_RU.md, runtime tools, libs, data/config и manifests. Это физическая поставка с203 регионами GeoData, без runtime-ссылок на прежний candidate. JavaHome указывает установленный JDK25. Source и test tree для воспроизводимости сохранены в `<GitRoot>/.phantom-local/task067/source-final` и `build-final-source-final`; эти private artifacts не публикуются в Git.

| Binding | SHA256 / SHA |
|---|---|
| Source commit | 2939b7054caeafbbfdc913ad5ebe4785c618c8a1 |
| Committed archive | 11EF6CB166D23D20B7B97A1C82ABD524D1F363B5E863378655A6BE6F3B6AE5F4 |
| Committed source tree | DEFAECA60AD8ADA715DD6528FBA4EB8F46ABE93A909431459F2842F28DA0A663 |
| GameServer.jar | 06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58 |
| LoginServer.jar | 75E6CD9F6278C06C59AEE4F4A9A3D50C7609E9B3136366FEBFF0622F42CF25DB |
| Compiled test/resources tree | B870B142769048739F0A44767BF5104883ACF94DCD7BB94CB4F604AA70A44579 |

BUILD_INFO067.json и SOURCE_BUILD_INFO067.json связывают whole JAR/source/test/tool hashes. GEODATA067.tsv фиксирует203 отдельных hash/size. Runtime067 проверяет manifests, exact root/schema/ports, JAR/tools hashes, физические paths и при Start весь GeoData. STOP проверяет PID+start ticks+runtime ID+JAR+owner067; force fallback отсутствует. Foreign/mixed controls и unbound067 control отклоняются до DB/listeners.

Собственные схемы: `l2jmobiush5_personal_test_067` и `l2jmobiush5_personal_release_067`, каждая создана один раз121 stock scripts/124tables, без reset/reimport. Aggregate stock SQL SHA256: `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`. MariaDB127.0.0.1:3308 и старые схемы не останавливались и не перенастраивались. Credentials копировались в private config без вывода. Release database новая, персонажи владельца в неё не мигрировали.

TEST ports2248/7948/9248; release2148/7848/9148. Main generation1: Game22324/startTicks639282427219545794, Login2988/639282427200765523. Generation2: Game24564/639282428034664244, Login14252/639282428018995258. Обе регистрации и обе штатные GameServer shutdown подтверждены логами, same schema/JAR и новые PID — status receipts. В финале обе роли STOPPED, все6 портов свободны, own067 JVM0.

FINAL_DELIVERY067.json подтверждает protected8 SHA/size,203 protected Geo originals,203 TEST copies,203 release copies,6 неизменных task docs, тот же JAR/config и zero own processes/listeners. Это адресная сохранность проверенных входов; полный побайтовый baseline всех historical releases не снимался. Прежние schemas не открывались для тестовых writes, чужие JVM не останавливались, C:-checkout и два stash не затрагивались. JAR/GeoData/secrets/logs в Git отсутствуют.

## Выполненные проверки и пределы

Команды выполнялись из фактического Git root, JDK25 / Ant1.10.17. Ant запускался последовательно. `ant verify` и старые DB suites не запускались.

| Команда / route | Exit | Доказательство |
|---|---|---|
| `Build-Task067.ps1 -Label source-final -Build build-final` | 0 | exact committed archive; ant jar + compile-tests;2344 production files/304 test files; два прежних deprecation warnings System.runFinalization |
| own067 `QoLAdventure067AccessChecks` | 0 | 0.17s,10 access profiles+null; права не менялись, DB writes0 |
| `Task067RuntimeGuardChecks` | 0 | 0.11s; unbound control refused до DB/process actions |
| `QoLAdventure067Native core` через Run-Final067.ps1 | 0 | 17.21s; G1/G2/G4/G7/G8, Player.class реально из final GameServer.jar; SEVERE0 |
| `QoLAdventure067Native fishing` | 0 | 40.35s; native START/WIN, mode table, lifecycle; SEVERE0 |
| `PhantomTestLauncher post002-semantic-v3-content 2002002` | 0 | 2.83s,3/3; committed source и pinned tests без повторной компиляции |
| `Assemble067.ps1 ... -Kind Release` | 0 |203 physical regions, committed receipt binding; DB_WRITES0/PROCESS_ACTIONS0 |
| copied `Runtime067.ps1 -Action Start/Status/Stop -RuntimeRoot <candidate>` | 0 каждый | два ordinary Main,539 quests/306 scripts, registration; sameDB/sameJAR restart, native STOP |
| private `Verify-Delivery067.ps1` | 0 | FINAL_DELIVERY067.json, protected hashes, ownJVM0/listeners0, MainSEVERE0 |
| private `Verify-Scope067.ps1` | 0 | exact allowlist41, strict UTF-8, отдельные encoding scans и exact-path diff whitespace check |

Точные invocation/classpath, logs и durations: `.phantom-local/task067/Run-Final067.ps1`, `FINAL_NATIVE067_VERIFIED.json`, `final-verified-*.log`, `reports/fishing067-native.txt`, `reports/standalone-final`, generation status и FINAL_DELIVERY067.json. Final native routes суммарно60.67s. Оценка по времени жизни30 development/final native log files329.03s плюс два Main менее3мин; лимит60мин не исчерпан. Эта оценка не является метрикой боя или soak.

Native fixture подготовлена **до измерений**, в собственной TEST schema: реальные Player objects, уровни/skills/предметы; отдельная declared fishing fixture rod6529/lure7807/FishShots6535,level10,skills1312/1313/1314/1315:1. Реальная сессия GameClient отсутствует; teleport acknowledgements вызваны на component boundary. Во время reward run не редактировались HP/RNG/rates/quest/skill/item data. Для mode table использовались отдельные premeasurement реальные FishData. Наблюдённый onlineFalse — state guard, не доказанный полный network logout. Cubic persistence доказана store/reload настоящего Player; повторный packet use после клиентского reconnect не заявляется. Main restart отдельно доказывает запуск прежнего JAR/schema, без персонажей release.

Genuine RED сохранены: cubic stale24h; fishing noMP/action; Henna CON−3; solo refusal; augmentation1150.9 expected/1135.45 actual; solo exit32496-no; unbound067 control и nonzero access profile. Общие fixture defects исправлены native revive/spawn/handler registration и cleanup ordering, без ослабления headless/auth. Исправлена одна compile ошибка неподтверждённого InstanceWorld.getInstance на настоящий InstanceManager lookup. Ранний запуск до конца compile дал ClassNotFound; неверная трактовка обычного Java stderr в PowerShell Stop прервала один launcher. Они не считаются product PASS; после завершённой сборки final runs exit0/SEVERE0. Пустой/failed partial log сохранён. В финальном read-only проходе отсутствующий logging.properties дал shell exit1; штатные java/error logs найдены и проверены, runtime-команды от этого не зависели.

Независимый reviewer проверил affected production owners и runtime safety; после exact access0 исправления блокеров не осталось. Клиентская UI/tooltip/packet QA и полный сетевой logout — DEFERRED_BY_OWNER/NOT_RUN, не переложены на владельца как условие завершения067.

## Изменённые файлы и Git

Exact41-path allowlist: [SCOPE067.txt](../tasks/PHANTOM-PERSONAL-ADVENTURE-067/SCOPE067.txt). Bounded exception кампании разрешён AUTHORITY; новые несвязанные подсистемы не добавлены.

Все пути ниже относительно HighFive module, кроме `.gitignore`; полные test paths раскрыты в SCOPE067.txt:

| Файл | Причина |
|---|---|
| `.gitignore` в root | Только private staging067; не допустить публикации секретов/бинарников |
| `dist/game/config/Custom/PersonalCharacterQoL.ini` | Независимые OFF children, ranges, краткие русские комментарии |
| `dist/game/data/scripts/instances/Kamaloka/Kamaloka.java` | Общий admission roster и exact solo escape |
| `java/org/l2jmobius/commons/util/LocalRuntimeShutdown.java` | Только свой owner/control namespace067 |
| `java/org/l2jmobius/gameserver/config/custom/PersonalCharacterQoLConfig.java` | Typed adventure snapshot, strict child validation |
| `java/org/l2jmobius/gameserver/qol/PersonalCharacterQoLService.java` | Общий персональный guard, access0/identity lease deny |
| `java/org/l2jmobius/gameserver/qol/PersonalInstanceAdmissionService.java` | Один проверяемый roster/Party identity |
| `java/org/l2jmobius/gameserver/qol/PersonalItemReuseService.java` | Canonical/effective cubic reuse и persisted provenance |
| `java/org/l2jmobius/gameserver/model/actor/Player.java` | Только reuse/restore и Henna recalc seam |
| `java/org/l2jmobius/gameserver/network/clientpackets/UseItem.java` | Effective query/display при прежнем canonical record |
| `java/org/l2jmobius/gameserver/model/fishing/Fishing.java` | Native Pump/Reel в существующем tick/lifecycle |
| `java/org/l2jmobius/gameserver/model/options/Augmentation.java` | Передать персональный factor в stock Options |
| `java/org/l2jmobius/gameserver/model/options/Options.java` | Clone только точного positive FuncAdd, прежний owner/remove |
| `test/.../commons/util/Task067RuntimeGuardChecks.java` | Unbound control regression |
| `test/.../gameserver/qol/QoLAdventure067AccessChecks.java` | Nonzero non-GM access regression |
| `test/.../gameserver/qol/QoLAdventure067CoreChecks.java` | Native representative scenarios и safety matrix |
| `test/.../gameserver/qol/QoLAdventure067CubicChecks.java` | Reuse boundaries/unknown/group preservation |
| `test/.../gameserver/qol/QoLAdventure067AugmentationChecks.java` | Typed mixed funcs и apply/remove symmetry |
| `test/.../gameserver/qol/QoLAdventure067FishingChecks.java` | Mode/lifecycle checks |
| `test/.../gameserver/qol/QoLAdventure067FishingScenario.java` | Declared native START/action/reward fixture |
| `test/.../gameserver/qol/QoLAdventure067Native.java` | Own067 bounded runner и cleanup |
| `test/.../tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java` | Own067 infrastructure-only lane, старые DB guards сохранены |
| `test/.../tests/phantoms/PhantomTask067Environment.java` | Однократный stock bootstrap только своих schemas |
| `test/.../tests/phantoms/PhantomTask067Lane.java` | Exact067 root/schema/control ownership |
| `tools/phantom-runtime/Build-Task067.ps1` | Committed archive и clean pinned build |
| `tools/phantom-runtime/Assemble067.ps1` | Standalone physical copies/provenance/guide |
| `tools/phantom-runtime/Runtime067.ps1` | Guarded START/STATUS/native STOP |
| `docs/phantoms/tasks/PHANTOM-PERSONAL-ADVENTURE-067/*` из allowlist | Исходные6 task docs+PACKAGE_SHA добавлены без изменения; progress, audit, matrix, handoff и scope067 |
| `docs/phantoms/reports/PHANTOM-PERSONAL-ADVENTURE-067.md` | Этот итог |
| `docs/phantoms/PHANTOM_CURRENT_STATUS.md` | Краткая новая запись067, история сохранена |

Git-команды **использовались** по прямой AUTHORITY067 и пользовательскому запросу. Выполненные формы: `git rev-parse --show-toplevel`, `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git status --porcelain --untracked-files=all` (в builder также с module scope), `git worktree list`, `git remote -v`, `git merge-base --is-ancestor 8631faf3d4ce7f0f2256cb312af99659c008560d HEAD`, `git ls-tree HEAD -- <exact Player.java>`, `git diff -- <exact paths>`, `git diff --numstat [baseline HEAD] -- <exact paths>`, `git diff --cached --stat -- <exact paths>`, `git -c core.whitespace=cr-at-eol diff [--cached] --check -- <exact paths>`, `git add -- <exact allowlist paths>`, `git commit -m "phantom(task-067): add personal adventure entry and native QoL"`, `git archive --format=zip --output=<own067 archive> 2939b7054caeafbbfdc913ad5ebe4785c618c8a1 -- <module build.xml/java/test/dist/tools>`, `git push origin HEAD:refs/heads/feature/phantom-world`. Для итоговых5 документов — адресные add/diff-check и `git commit -m "docs(task-067): record verified delivery and bounded acceptance"`, затем тот же normal push и local/live SHA comparison. Все списки exact paths раскрыты в SCOPE067.txt; неизвестный dirt не включался. Reset/clean/stash/restore/rebase/force/изменение master не выполнялись.

- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: Unicode04/05 и XML x/X04/05 отдельно от mojibake, совпадений нет.

Blue champions, остальные item cooldowns, массовая offline-экономика, raid AI, cross-class refactor и Semantic Pack/Studio остаются future roadmap вне067. Проверенное завершается сейчас; ждать12часов и запускать новые частные сценарии для количества не требуется.
