# TEST_PLAN — ограниченная стратегия RED→GREEN + genuine native

## 1. Read-only и targeted RED

- Зафиксируй источник текущих объектов, native loading и root cause для 30006/30146/30576 и Giran30080. Сравни `PhantomHistoricalBackgroundPlanner.candidate()` graph0, `_travel.route`, 087 native `SEARCH_LIMIT`. Убедись, что разница входного условия доказуема, без раздувания ручной test fixture.
- Targeted tests в existing runner: **три** starter cultures, **один** Giran direct control, **две** multi-hop GK alternatives, no-GK/insufficient funds, blocked native edge, cancellation/stale epoch, wrong instance/surface/Geo, no free item/fee. Показать хотя бы один реально падавший на baseline RED по каждой production root cause, затем regression GREEN. Не «переписать ожидание теста под новый код».
- Проверить старые focused: `PhantomWalkCorridor064Test`, `PhantomNormalGatekeeperPureTest`, `PhantomTravelPlanning033RSuite`/`PhantomGeneratedHistoricalRouteSuite` в **тех ветках, которые DB-free и запускаются безопасно**; 085 source diversity и 074 farm. Полные mutable suites — только если отдельный native lane и реальные owner boundaries позволяют.

## 2. Одно isolated compile/native lane

- Stage физически **вне** checkout и вне защищённых release, host owner ACL; не Git worktree. Поддержать native `203` Geo, source-checksums, canonical source `OwnerConfig Verify`, `Apply→Verify` только на shadow copy. Любой source/copy drift между compile и measurement => STOP.
- New 089 schema: например `l2jmobiush5_phantom_test_089`, `127.0.0.1:3308`; READ-ONLY prove absent and non-foreign, затем one allowed canonical CREATE. Не читать/write 087/078 и не переписывать shared service. Собственный process и PID/start/UUID. Не print secrets.
- Одна последовательная production-shadow `ant -f <copy>/build.xml -Dbuild=<stage>/build compile-tests`, Java25; targeted javac only when justified. Не использовать `ant verify`, не вызывать GameServer против сохранённого runtime. Повторить compile только если production изменён после него.
- Initial fixtures только в new089 before-measurement: H40 Gladiator или квалифицированный аналог, E76 Moonlight Sentinel или квалифицированный, O76 Dominator или квалифицированный. Реальное class expertise/learning (item-required skills не выдавать), equipped items counts IDs/slot, inventory/wealth premeasurement. Не использовать мнимый postrestart `AutoSkills1090`; если нужен caster, восстановить законную policy/state, иначе отметить ограничение магической части.

## 3. Native позитивные сцены (одна связанная кампания)

Для каждого из H/E/O: зафиксировать фактическое исходное `Player` XYZ и `currentAnchor/goal`, затем goal выбрать у настоящего native NPC в другом регионе. Обязательные per-character timeline:

`goal id/revision → first-mile native WALK issued → waypoint arrivals → NPC canInteract → native GK bypass → real Adena charge when fee > 0 → teleport landing → post-landing WALK → target/farm anchor → native attack HP delta → earned reward receipt`.

- Требовать два **разных** направления/региона (не три подставленных фейка на одной дорожке). Сегменты — Native Geo validated; ни одного «трансфера» пешком без физического `MOVE_TO` или permitted native GK action.
- Не выбирать все три test destinations с fee0. Минимум два P3 nonzero actual GK charges. У Human baseline free0 допустим штатно, но это НЕ второй paid case.
- Сравни actual inventory/EXP/SP до и после фермы с real NPC kill IDs / reward source. Exp или Adena от test fixture не засчитывать. Персонаж должен прожить эпизод без GM/command hacks.

## 4. Restart и race-сценарии

Один из созданных actors идёт по действительному платному маршруту; после сохранённой native checkpoint/goal/identity сделать **обычное** controlled STOP собственной JVM089. На новой JVM **тот же** DB89/source snapshot: resume same profile/goal/revision, verify native XYZ/current leg, item IDs/count/wallet, без repair/seeding, bounded route continuation до farm. Зафиксируй нет двойной оплаты и нет потерянного лота/дополнительной копии.

Точечные negatives: (a) cancel/stale goal между quote и debit; (b) no funds/karma/siege/NPC disappears; (c) interrupted after paid intent/before arrival — recovery detect, **не** double charge; (d) door/water/Z/instance invalidation; (e) start timer expiry/queue pressure; (f) player enters party/store/combat/dies/lease evicted during travel; (g) source file/schema/owner mismatch reject before action. Негативы DB-free там, где достаточно; real native там, где обещана native гарантия. Нельзя похвастаться всем набором native negatives при запуске только pure predicates.

## 5. Бюджеты и STOP

- Точное число Geo native requests, visited nodes/edges/labels/waypoints, elapsed per route leg; p50/p95 native leg/actor and peak heap/GC/CPU if measured, отдельные wall for loading/attestation (не смешивать).
- Нагрузочный контракт: базовые 128nodes/512edges/32queries/4096labels/1s на единичную локальную попытку остаются safety frame; допускается последовательность bounded episodes с proven checkpoints, но без unbounded retries/pathfinding per tick. Показать cumulative budget на полный journey.
- Следить за source cursor/store/DB0 after graceful STOP. Если runtime hung — terminate только own PID+startUtc после bounded normal stop/receipt; никаких global process kills.
- Если native scope заблокирован ACL/DB/real data — честно `BLOCKED_SAFE_NATIVE`/`PARTIAL`, без ослабления защит и без выдачи тестов за backend E2E. Не пытаться «добить» GREEN на фиктивном сценарием.
