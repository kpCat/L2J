# HANDOFF066 — актуально на21.10.2026,21:50 UTC

Обновление21:56 UTC: f8cb build завершён exit0, JAR33DC0C8B965AC414785DBDFE445C62E6252A2D4B4BE4185CA70C6F7FEB78B276. Self-review уточнил scalar rewardedFarm: real rewardSequence advance + native kill/damage остаётся witness при одновременном inventory pickup; timestamp-based useful-debt не обновляется искусственно. Следующий source snapshot source066-final-witness в build-final. Промежуточный f8cb в runtime не устанавливать; обе среды всё ещё на7dd, все свои JVM STOPPED. После финального snapshot: Main S1, затем sameDB/sameJAR restart, review/STOP/reports/push. Новые функции и fixture-марафоны запрещены текущим приоритетом владельца.

Продолжать /goal автономно по AUTHORITY066 и уточнению владельца: G9, sameDB restart, G10, STOP, отчёт, non-force push. Не спрашивать вход в игру. Не возвращаться к бесконечной отладке fixtures. Сессия начата16:12 UTC; cutoff новых функций02:42 UTC22.10, потолок04:12 UTC. Собственные ordinary Main наблюдения уже около48мин; общий hard cap90мин. Новые функции не нужны.

## Текущий snapshot и процессы
- HEAD f8cb43f21: productive farm после реальной награды использует существующее90с окно полезного простоя вместо30с пустого поля. Нативные timers/rates/RNG не изменены. Commit полный SHA брать из BUILD_INFO.
- Чистая сборка RUNNING: Build-Task066 -Label source066-respawn-release -Build build; exec session61526. Ожидается около6мин из-за Expand-Archive. Ant последовательно. Не запускать другой Ant до завершения.
- Все свои Main/Login/native JVM остановлены штатно. Последние Game38368/Login33764 STOP_CONFIRMED, log priority-main-stop.log. Может работать только текущий build JVM.
- TEST/runtime и внешний candidate пока на7ddfa8a5600a52c56191caa31d455c532ef2bced; whole JAR D31A4210117DE46B233746B4DAD0F503263B5EA2E4823F5D6074B8583C44BD1C. build-final сохраняет эту привязку.
- Свои БД: l2jmobiush5_phantom_test_066 и l2jmobiush5_phantom_release_066a. Candidate D:/PhantomWorld/Farm-Economy-066-candidate; ports2146/7846/9146. TEST ports2246/7946/9246. Защищённые057–065 не запускать/не менять.

## Общие причины и реализации
A optional shots: UNAVAILABLE не блокирует native бой, FAILED по-прежнему fence. Обязательные MP/reagent/ammo/known skill сохранены. Новое7dd supply правило: optional закупка покрывает target deficit60hits; частичные4shots больше не создают дорогие micro-trips. Cash/stock facts снова разрешают достаточную закупку. Mandatory ammo/healing partial сохранены. RED5/6 -> GREEN6/6 commerce-decision.
B bounded native loot + exact source receipts/party/sweep; herbs получают отдельный CONSUMED_NATIVE receipt, не inventory/Adena. Native loot6/6 на4244; на7dd5/6, ranged case INVALID_INPUT: оба заранее выбранных Geo corridor недоступны. Не подбирать новые координаты ради PASS.
C soft16+burst16/physical32 в candidate; fair physical permits, reservations/failed retained, exact epoch/hard holds/lazy cleanup. Latest7dd native4/4; arrival950ms и cleanup60223ms по более раннему corrected timestamp run.
D bounded existing SupplyJourney child, conservative recipe/product roles, stock SELL/refund/warehouse fee30/CrystallizationService, native checkpoint+economy claim+durable receipt. Native economy3/3 и maintenance journey1/1 на7dd. Sale236, fees206/176, bank withdraw3, buy160, surplus69->915D crystals, replay idempotent. Native journey сам прошёл424units, sale->bank->withdraw->field.

## G9 — текущий direct blocker исправлен, нужен Main GREEN
- Main7dd actor19/object268484999 имел HP163/163, Adena13, shots0; новый supply-child не создавался. Вернулся к field, native damage3/kill1/reward1, consumed1 herb. Затем AutoPlay прекратился с background.visible.replan_required.
- Read-first доказал общий конфликт: native Npc.onDecay -> Spawn.decreaseCount -> respawn25, corpse default7 плюс decay tick5; цикл дольше30с no-target. Exclusion120с больше useful-stall90с, поэтому повторное включение не успевало. Это не ошибка одного спота.
- f8cb добавляет scalar rewardedFarm по реальному reward/farmCycle, сохраняет его внутри того же native epoch, использует90с no-target окно только после награды; абсолютный useful-debt90с остаётся неизменным. Pure causal RED53/54 -> GREEN54/54. Не добавлялся новый engine, не было teleport/refill/native timer changes.
- Passive Task066View (-71212,255041,-3240); synthetic OFF, no client/network. LocalPlayObserverProfileIds=5,19,20 теперь сохраняет status тех же identity вне cohort. Это read-only selection, конфиг изменён только при stopped Main, preimage private.
- S2 helper дважды остановился ДО любых DB mutation: сначала UTC/Z authority token (исправлен), затем нет двух подходящих L18–22 spoil farms с mapped FARMING anchors и близким Merchant/Warehouse. До48 исходных FRESH identity сохранены. Scavenger не получает Crystallize: незаконный неисполненный fixture grant удалён в f8cb. Stock native Spoil отдельный PASS: два kill/reward, два Sweep, material1868x2 и negatives.
- S2 Main пока BLOCKED; не объявлять CORE PASS. Не подменять его отдельными native fixtures. Альтернатива по authored stock coverage требует общей доработки данных, не исключения для конкретного NPC.

## Сразу следующие действия
1. Дождаться61526; проверить BUILD_INFO build exit0/fullSHA/JAR/testTree. Update-Test066 -Build build -Label respawn-f8cb -Lane TEST, затем CANDIDATE (idle own JVM guard).
2. Read-Release066 -Build build -TestBuild build -Label respawn-before-main -Snapshot (ITEM/RECEIPT evidence теперь добавлены). Start own candidate Runtime066, сохранить новый PID/incarnation; Observe066.py respawn-main-s1 --seconds720 (elevated private PID read).
3. Параллельно независимые pure whole-JAR checks Run-Dev066 -Build build -Committed -Pure: combat-core seed20260725001 label respawn-core54; commerce-decision140014 label respawn-decision6; commerce-receipt140014 label respawn-receipt9. Не перезапускать все unrelated native gates.
4. При критерии >=3kill/reward, >=2exact pickup и следующем damage в том же epoch/Object — немедленно normal STOP. Collector различает pickedInventory и pickedConsumed. Последнее не деньги/инвентарь.
5. Snapshot respawn-before-restart, normal sameDB/sameJAR Start без reseed/input changes, read-live + bounded useful continuation, normal STOP, snapshot after. Сравнить ITEM/RECEIPT/identity, честно отделять реальные новые gains от неизменных completed receipts.
6. Финальный self-review точного diff, scope, encoding2checks, package7/7, preservation, RuntimeStatus own PID/portsfree/DBwriters0. Два focused native случая остаются незакрыты: A direct dual-class unboosted magic не дал ожидаемый damage (3/4); B ranged geometry INVALID_INPUT (5/6). Не скрывать FAIL и не продолжать fixture-марафон.
7. Сформировать public report<=180lines, ACCEPTANCE066.tsv, актуальный compact HANDOFF; точечные CURRENT_STATUS/revised-roadmap. CORE PARTIAL, MARKET_BRIDGE DEFERRED, OWNER_CLIENT_QA DEFERRED. Гейты только по фактическому виду evidence.
8. Проверить live remote, normal push origin HEAD:refs/heads/feature/phantom-world; подтвердить ls-remote. Можно сначала push source, затем report-only commit/push; FINAL_PUBLICATION066.json в candidate фиксирует финальный live SHA без self-reference в commit.

## Evidence, helpers и сохранность
- Всё private/ignored: GitRoot/.phantom-local/task066. Run-Dev066, Update-Test066, Read-Release066, Observe066, Inspect-Observer066, Verify-Source066.py, Verify-Protected066.ps1. Source archives только private; candidate/private/credentials не публиковать.
- Последний full impacted7dd: priority-impacted.log: A3/4 FAIL, B5/6 FAIL geometry, C4/4, D3/3, maintenance1/1, core53/53, receipt9/9, decision6/6. Не выдавать этот набор за all-green. f8cb affected core54/54 уже GREEN на compile tree; wholeJar/Main ещё нужны.
- Финальный preservation пока снят21:14: files7 + sourceGeo203 + ownPhysicalGeo203, два реально использованных065 schema/10tables exact hash PASS. Это не проверка122 БД. Verify-Protected066 повторить в конце, сохранив предыдущий private receipt при необходимости.
- Candidate START/STATUS/STOP и START_HERE уже есть. CONFIG066.tsv88 hashes пока СТАРЫЙ относительно observer selection; пересчитать после финального STOP с сохранением preimage. BUILD_INFO/SOURCE_BUILD_INFO обновляет Update-Test.
- Cumulative exact allowlist67 files; package SHA7/7, mojibake и escaped Cyrillic PASS отдельно. Verify-Source066 --inventory --stage адресно stages allowlist; git разрешён AUTHORITY. Не использовать add., force, reset, stash, merge/rebase и чужие refs.
- Live remote последним оставался934c19f906aa67349f1fd498eacfbb5a56d9b90f. Push ещё NOT_RUN. Все commits локальные feature/phantom-world.
- Предыдущий длинный HANDOFF сохранён побайтово вне checkout в TEMP/PhantomTask066-Handoff-20261021-2150 с проверкой SHA; текущая запись заменяет его как source of truth.
