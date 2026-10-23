# TASK071 — read-only audit после публикации

23.10.2026 13:58UTC. Предыдущий goal turn дал progress: native evidence, отдельную поставку, final acceptance commitba025a684e4c051fb061184dd21d170467a2a993 и normal non-force push. Полная цель не достигнута; FULL071 остаётся PARTIAL. Product freeze13:11:28 и hard deadline14:11:28 не сдвигаются автоматическим продолжением.

## CP paid acquisition

Повторно прочитаны committed `PhantomSupply071JourneyNative.cpPurchase`, `L2jCommerceBackend.validateActor`, `Player.load`, native `Npc.INTERACTION_DISTANCE`, own TEST071 spawn XML и неизменённый failed TSV.

- Fixture создала только2000Adena до измерения, сохранила NPC-relative XYZ, затем обычная materialization загрузила Player. В записанном native состоянии actor80534,147922,−3080,instance0.
- `Player.load` получает Z через `GeoEngine.getHeight(x,y,savedZ)`, затем `setXYZInvisible`; materialization вызывает обычный `spawnMe`. Записанный Z потому нельзя считать равным исходному setup Z.
- Stock spawn30081:80518,147922,−3506; source interaction distance250. Разница между recorded actor и stock spawn поZ426, поXY16. Это согласуется с вертикальным range отказом.
- Backend проверяет `isInsideRadius3D` до quote/prepare, а actual result — `REPLAN NPC_OUT_OF_RANGE`. Paid receipt не создан, бесплатная CP не выдана. Commerce/materialization/ThreadPool/Hikari закрыты.
- Actual NPC XYZ в том failed запуске не записан. Поэтому вертикальная Geo-сцена — обоснованная гипотеза, а не окончательно доказанная причина runtime defect. Source не подтверждает, что native range guard ошибочен.

Новый запуск, выдача ресурсов, teleport/setZ после измерения или обход radius не выполнялись. B_CP PARTIAL сохраняется. Это диагностика входной сцены, не successful acquisition proof и не изменение production-кода.

## Retained supply blocker

Повторно проверен `native-earned-supply.tsv`: exact original profile1,actor268481016,paid BUY1835x120=960,receiptCOMMITTED,Adena5290,HP494,MP175,SS116. Никакого Journey replay или сброса сохранённого состояния нет. Native eligible stock начинается с20132level9 на11569ед., остальные12658/12980/13531/13804. В пределах разрешённых5км rewarded-level target с проверенным route отсутствует.

Первый Wolf4 при actor19 корректно дал0EXP/SP. Изменение уровня/награды/HP, новый surplus, повторная покупка или искусственное перемещение заменили бы требуемую связанную сцену. C_RECOVERY остаётся BLOCKED_INPUT, C_SHOTS/C_GEAR — PARTIAL. Main rewards и другие fixture controls этот пробел не закрывают.

## Инварианты

Ни production source/JAR/datapack, ни БД, ни runtime-процессы в этом audit не менялись. Исходный публичный ZIP и его26033 проверенных файла остаются неизменными; этот дополнительный audit поставляется рядом с ним в локальном candidate/evidence071 и отдельным documentation commit. SOURCE4b84313 и acceptance matrix не подменены.
