# PRODUCT_DECISIONS — договорённости владельца для 089

1. Мир: 5000 **durable unique profiles**, материализация cap128+ по фактической нагрузке, минимум 200 **native offline stores** в финальной поставке. TASK089 не заявляет эти цели достигнутыми.
2. Главное сейчас: настоящий автономный уход персонажа из стартовой зоны в **далёкий** farm region с обычными player rules. Не подменять NPC/player, farm и монетизацию демонстрацией.
3. Самостоятельный выбор места охоты по актуальным mobs/level/class/gear/risk/экономике. Локальные предпочтения важны, но дальний кандидат не выбрасывается из-за одиночного неподходящего радиуса.
4. Штатные GeoEngine/PathFinding/TeleportHolder/Npc. Допустима иерархия коридоров с конечными checkpoint; фактические WALK обязательны для native proof. `SEARCH_LIMIT` — не физический запрет. Недопустим arbitrary teleport, invented topology/GK, huge search-time budgets и hardcode Human/Elf/Orc/Giran.
5. Adena/предметы не бесплатные. Штатная скидка/нулевая стоимость на официальном NPC допустима для этого класса/уровня/времени, но не доказательство платного travel. Повторная оплата при restart/неопределённом результате строго запрещена.
6. Реальная награда только после native kill/loot/EXP/SP; недопустимы synthetic reward/GM/test-seed после measurement. Законный дроп по native ownership, не «killer==picker».
7. В `PhantomVisibleFarmTravel` уже есть жизненный цикл, progress, arrival и retries. Нужна интеграция с ним; второй graph, worker/thread per-phantom, отдельный payment engine запрещены.
8. Protect player ownership, death, party, store, materialization, stale goals, cancellation/geo changes/restart. При неопределённости state — безопасный explicit отказ, не дюп.
9. TASK088 settings неизменны: все owner-approved public source конфиги, OwnerConfig, Rates, Player, Olympiad, Community Board, personal QoL. Нет доступа к реальным `AllowedAccounts` / private identifiers.
10. Не создавать промежуточную пользовательскую релизную папку; пользователь проверит единый зрелый world позднее. Не менять protected078/её DB и прежние 087 private data.
11. Результат задачи оценивается **по реальным игровым последствиям**, а не количеству PASS/unit tests. PARTIAL честен, но требуется довести причинную связку настолько полно, насколько позволяет реальная native среда, без ухода в бесконечное инфраструктурное тестирование.
