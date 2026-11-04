# TASK122 — State Boundaries & Restart Continuity

Дата: 04.11.2026. Итог: **PARTIAL**. Два конкретных STOP-дефекта исправлены с causal RED→GREEN; rewarded sameDB/sameJAR restart и conservation TASK121 не доказаны. Собственная среда после отдельно разрешённого recovery остановлена и прошла postflight. FULL122 не достигнут. STOP122; TASK123 не начинался.

## Проверенное изменение

Исправлен конкретный diagnostic precedence defect: Ecology после `beginStop()` при непринятой preparation demand продолжала сообщать `ecology.preparation_capacity` вместо `ecology.stopping`. Добавлен только `!_stopping` к существующему capacity override. Queues, admission, caps, cursor, claims, rewards, STORE и native ownership не изменены.

Один focused regression в существующей `PhantomPopulationEcologyGoal033Suite`: zero-slot capacity → STOP; проверяются incomplete/scheduling refusal и неизменность durable ecology/catchup. Он включён в ordinary suite и focused `boundaries122` вместе с существующими native-context/live-owner negatives.

RED: expected stopping / actual preparation_capacity, 2/3 PASS, exit1. GREEN: 3/3 PASS, exit0. Ошибочный первый seed33033033 отклонён beforeAll; правильный закреплённый seed33003300 использован для RED/GREEN. Исправление **не выдаётся** за repair EXP или restart starvation.

Второй source-backed defect найден на реальном STOP path: `PhantomSystem.WAIT_ACCEPTED_CONTROL` повторно опрашивает Farming после ожидания соседнего owner, но `PhantomFarmingService.finishStop()` возвращал false для уже STOPPED. Минимальный fix возвращает true в STOPPED; незавершённые operation claims по-прежнему блокируют остановку. Existing Farming lifecycle regression: повторный STOP и неизменность ownership/metrics snapshot; RED1/2→GREEN2/2, seed24002401. Это production fix идемпотентности, а не сброс lifecycle. Связь именно с native deadline:farming не объявляется доказанной: farming snapshot этого окна не захвачен.

## Writer/owner map до правок

| Область | Автор и барьер |
|---|---|
| Native EXP/level/SP | Player/PlayerStat: reward, death, skill learn; native lifetime через PlayerNativeWork. Один Player monitor не является доказательством всех writers. |
| Adena/items | Штатные Player inventory/commerce writers; inventory persistence и full hash; отдельного кошелька нет. |
| Native STORE | Player.store/checkpoint/storeQuiescent → OwnedStoreBoundary PREPARE → base/sub SQL → afterStore/finalize; failed STORE не объявляется success. |
| Background SQL | BackgroundTransaction profile/character/component/item FOR UPDATE, exact payload/goal/epoch/CAS; canonical SQL/inventory checks перед commit. UPDATE_MAIN пишет progress, item delta — Adena. |
| Historical | HistoricalBackgroundService/catchup/goal stores: current claim, exact root/revision; native handoff только при exact owner/epoch. Возврат ownership после STORE. |
| ACTIVE selection | PopulationManager regional quotas/_admittedIds + приоритет current human demand. reason=capacity означает logical selection, не физические8/8. |
| Preparation | Ecology _admittedPreparation, focus/backoff/queues под monitor; live owner не запускает historical advance. |
| Physical slots | MaterializationService semaphore: occupiedSlots=physicalCeiling−availablePermits; независим от двух logical gates. |
| Travel/Commerce | Existing journey/action owners с exact root/claim/action lease; stale callback не получает новый epoch и не повторяет receipt. |

Stale seams проверены source-first: retained owned intent/resume, background canonical drift, ecology scheduling revision, goal/epoch handoff. Cleanup завершает старый receipt и делает свежий capture; доказанного overwrite в этих guards не найдено. Полная initial карта сохранена в private `writer-map.md` до source patch.

## A TRACE: TASK121 остаётся UNKNOWN

Только итоговый report121 и точные private native18/19 receipts прочитаны read-only. profile31/object268481075/class2: последний live18 в06:54:57.017Z, epoch573525934789100: level54/EXP63938217/SP4020/Adena3613217. В06:55:07/17 actor уже отсутствовал. Позднее flight18 содержит ещё одну HISTORICAL_BASELINE→WORLD, epoch573652111967600, за пределами прежних counters.

SQL18/19: level53/EXP63705869/SP10050/Adena3617027. Дельта EXP−232348/SP+6030/Adena+3810 не атрибутирована событию. Source arithmetic точно совместима с +3×15153EXP/+3×2010SP и одной штатной death loss277807: round((70853089−63907911)×4%). Это **не доказательство** трёх kills или смерти. Adena tail также UNKNOWN.

Native capture читает текущий Player progress; historical execute проверяет expected payload, canonical SQL и BACKGROUND lease/World/autosave absence. Документированный STORE journal в старой stage отсутствует. Старые DB и receipts не исправлялись; SQL loss/overwrite не объявляется без события.

## B/C: capacity и границы доказательства

native19 capacity происходит из PopulationManager admissionProfile/_admittedIds. Отдельно существуют preparation selection и физические permits. operatorSetupLocalityTarget требует durable READY; production locality predicate не исключает durable DEAD. Поэтому setup0 после смерти не доказывает production starvation. Существующий corpse recovery относится к уже загруженному native Player.

STOP diagnostic defect доказан и исправлен; причина отсутствия нового PvE19 остаётся OPEN. Wake-failure masking capacity override также OPEN. Forced admission, вытеснение party/store, увеличение caps и изменение root/claim ради теста не применялись.

## TEST122: безопасность и фактические окна

Pinned GitHub baseline `29337ba89d94394b4e94ab1bc92dea54a44b038f`. Основной checkout HEAD236b965 с WIP109 оставлен read-only. Создан независимый detached physical clone без hardlinks; source122 и runtime stage disjoint. Remote HEAD сверялся live; master/default/experiment не менялись.

Полный G0 до единственного CREATE: OS PID/creation/image/SCM/CWD identity, owner+SYSTEM stage ACL, protected1235 SHA без drift, original Geo203 и физическая копия, private17782, JVM0/listeners0/SQL_TCP0, точная JDBC identity3308/physical datadir, full metadata visibility/foreignSessions0/absent schema. Stock dry-run121 SQL files/203 statements;18 DROP omitted. Только новая `l2jmobiush5_phantom_test_122`, UUIDba2cec85-beeb-496f-828c-76d5d2ff101b.

PRE-CUT: новая identity profile1/object268480990, Gladiator2/classIndex0/level54/EXP63907911/SP0, Adena1000000, source B weapon268, B12170/12193 и gear/skills/charges, farm anchor66628,111182,-3688/target20243. Это PREPARED_NOT_EARNED. Existing morning.v3 выбран до CUT после отказа evening calendar до рождения actor. Observer268485182 — отдельный disabled-account test human, не phantom. PRE-CUT SQL ledger сохранён.

Все native попытки использовали один JAR SHA256 `6dcc70b3426b5263d033b241a1b7bf5e4e2f318e4ac61d106090fd90d54de56e`. Stage input seal29084 files. После CUT не выдавались phantom XP/SP/Adena/levels/items/coordinates, не менялись claims/cursors/receipts и не replay rewards. Creation manifest/DB authority сохранены; later runtime seal — отдельный private manifest без SQL mutation. После нового source-only Farming RED→GREEN выполнена необходимая вторая build; её JAR6df8267c9462881b1144e16c232182cf3a0dbb2281ab48769251b16087ccf531 НЕ запускался и не копировался в runtime stage.

| Окно | Факты |
|---|---|
| attempt1, GameServer0 | Existing journal требует owner-only ACL, а child унаследовал owner+SYSTEM. Отказ до конструктора. Helper скрыл первичную ошибку своим неправильным drained=false. Отдельный witness RED→GREEN доказал ACL cause; child ACL сужен, root ACL сохранён. Read-only SQL после отказа точно равен PRE-CUT; исходный failed receipt не переименован в healthy. |
| window2, actual GameServer1 | Ошибка моей fixture: target1/activeTarget8 отклонены штатным parser, Phantom disabled. READY08:42:11Z,3 минуты без reward, штатный STOP/exit0/postflight2: JVM0/ports0/ownSQL0. Actor SQL/все components/inventory равны PRE-CUT. |
| window3, actual GameServer2 | До следующего CUT исправлены только shadow activeTarget1 и opt-in diagnostics; добавлена проверка parsed settings; JAR, initial assets и DB authority прежние. GameServer PID47472 creation08:47:54.760Z действительно запущен. Observer load отказал: dead/HP0; native observation прервана. STOP FAILED, blocker deadline:farming. Rewarded PvE и restart не засчитаны. |

Фактических GameServer starts: **2**, attempt markers:3. Новых запусков после STOP FAILED нет. Source-only диагностика: jcmd thread dump и существующий exact PID/UUID/JAR/CWD/schema read-only Attach snapshot; SQL0/stopCalls0. GUI text не получен; login9014 reconnect pairs не считаются причиной product failure. LoginServer не запускался.

Read snapshot08:57:48Z: WAIT_ACCEPTED_CONTROL/FAILED/deadline:farming; background operations/transactions/leases/transitions0; death drained=true; materialization entries0/permits8/8/blockers0/failedCleanupReady=true. Observer dead/HP0, World=false, native/identity owner=null, lease=null. По отдельному явному разрешению пользователя выполнен ровно один reviewed public shutdownIfStarted. Он вернул false, но реально очистил configured instance; FAILED не переписан. Затем exact observer штатно удалён и вызван stock Shutdown. Точный receipt: `control122-stop-1aa5f911cac84ce2b21f68c66f9c5b09.txt`; original window3 остаётся FAILED. Runner окна3 exit1 из-за отсутствия healthy receipts; recovery Attach/stock JVM/postflight exit0 зафиксированы отдельно.

Recovery postflight PASS: actual exit/JVM0/17782=0/SQL_TCP0/ownSessions0; protected1235 и original Geo203 без drift. Штатный runtime создал2 profiles/7 characters/5 offline stores; это реальные bootstrap effects TEST122, не ручной replay или earned PvE. Ни force kill, ни reset/lease clear/private cleanup/class redefinition не применялись. После recovery новых native starts нет.

## Проверки, review, scope

- Baseline `ant compile-tests`: exit0,25s; final compile-tests exit0,26s,5 существующих JDK warnings.
- Focused `boundaries122`:3/3 PASS. Existing paid-travel121, recovery121, VisibleItinerary068, stop121: exit0; controlled FAILED warning stop121 ожидаем самим тестом, не native success.
- Ordinary Ecology suite:39/40, exit1. `01-strict-catalog-config-and-codec` требует shipped disabled, но owner baseline enabled; этот старый assert не изменён, конфиг ради GREEN не отключался.
- `owner-profile-validate`: exit0,38 rules/288 overrides; Adena57,5 amount/57,1 chance и OwnerConfig сохранены.
- GameServer JAR builds:2, обе exit0; первая23s, последняя `ant compile-tests jar`42s после нового доказанного Farming blocker. Все native были на первом JAR; последний — source-only validation. Это bounded дополнительная build, не серия native candidates. Полный ant verify:0; чужие DB suites:0. Utility Attach JAR не является GameServer candidate.
- Независимый production diff review P1/P2=0. Guarded recovery review: два P2 исправлены (immutable creation pin; exact registry/World observer guard), final P1/P2=0; READ-only проверен. Агент не переопределяет классы и не меняет runtime fields.
- Последний closure run: Ecology3/3, Farming2/2, paid-travel/recovery/itinerary/stop121 — все exit0 на финальном source. `git diff --check` exact source4 PASS; EOL изменён только added hunks с byte preimages/SHA и normalized-text equality.
- Cross-owner regression защищает STOP/selection/cursor/claim. Реальный death/partial STORE/inventory conflict/party/store native matrix и rewarded restart НЕ подтверждены; DB-free checks не подменяют их.

Exact public allowlist (module-relative):
1. `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java`
2. `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java`
3. `docs/phantoms/reports/PHANTOM-STATE-BOUNDARIES-122.md`
4. `java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java`
5. `test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java`

Read set: все7 TASK122 docs, root/module AGENTS, README bounded, current status/master-plan bounded, workflow/efficiency/package standard, только final report121;6 primary Java seams и ближайшие тесты. Дополнительные exact reads обоснованы source dependency: PopulationManager actual capacity, Historical handoff, Authority native capture, PlayerStat/death XML, diagnostics journal, Config parser и GameServer/STOP/Farming при реальном blocker. Полных dumps классов>1000 строк не было. AGENTS выше git root не найдены; local report121 отсутствовал и прочитан с pinned GitHub.

Git использован по явному TASK122/root разрешению: `rev-parse --show-toplevel`, `rev-parse --verify <expected>`, `rev-parse HEAD`, `symbolic-ref --short HEAD`, `status --short`, `worktree list --porcelain`, `for-each-ref --format=... refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`; exact-path `ls-tree --name-only <expected> <report121>` (в main missing tree); `ls-remote origin refs/heads/feature/phantom-world` (sandbox network refused), затем `ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world` PASS.

Own clone: `git -c maintenance.auto=false -c gc.auto=0 clone --no-hardlinks --single-branch --no-checkout <source121> <source122>`; `sparse-checkout set L2J_Mobius_CT_2.6_HighFive`; `checkout --detach <expected>`. Только exact paths: `git diff --`, `git diff --stat --`, `git diff --unified=0 --`, `git diff --check --`, `git diff --cached --check --`. Inventory/scope: `git status --porcelain=v1 -uall`. Main checkout не изменялся.

Code commit `219a8dafcf65fb71f27da06959d78fd63679bf5a`: exact4 code/test files,37 additions/1 deletion. Команды: `git add -- <exact4>`; `git -c maintenance.auto=false -c gc.auto=0 commit --only -m "phantom(task-122): preserve STOP boundaries and idempotent farming drain" -- <exact4>`; `git push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world`. Ordinary non-force push exit0; live remote verified219a8dafcf. Report-only commit публикуется следующим тем же scoped ordinary push с expected219a8dafcf; его окончательный SHA/remote receipt — в финальном ответе/private evidence. Force/history rewrite/branch/reset/clean/stash/StageAll не применялись.

## Evidence и OPEN

Private evidence `%LOCALAPPDATA%/Temp/phantom-state-boundaries-122`; physical stage `Phantom-State-Boundaries-122-ba2cec85-beeb-496f-828c-76d5d2ff101b/private`. Сохранены writer map, RED/GREEN/regression/build/owner logs, G0 receipts, creation/current input manifests, PRE-CUT readback, все failed attempts, exact stop/threads/control snapshots и original logs. Никакие предыдущие TEST/MAIN/PLAY/release/Geo/WIP109 не изменялись.

OPEN: TASK121 tail conservation; production native19 starvation; TASK122 rewarded first/restart; точная native farming timeout attribution; native partial-store/death/party/store matrix; wake-failure diagnostic precedence. Продуктового repair сохранности или autonomous restart этими STOP fixes не объявляется.

- mojibake-маркеры в изменённых файлах проверены:27 markers/5 files/0 matches.
- escaped Cyrillic в изменённых файлах проверены:6 patterns/5 files/0 matches.
- Exact5 scope guard: outsideScope0; protected1235/originalGeo203 без drift; два финальных byte-identical receipts сохраняются в evidence перед report commit.
- Usage snapshot до публикации:605964 tokens/4445 active seconds≈74.1min. Стоимость инструментами не предоставлена. >500k объясняется фактическим FAILED STOP, exact guarded recovery и дополнительным доказанным source blocker; многозапусковую кампанию121 не повторяли. Прямых java.exe calls до code commit:36 (включая2 actual GameServer), Ant/Javac JVM отдельно не учитывались. Repository searches exact counter не вёлся; закрытый primary read-set расширен только указанными causal dependencies.
