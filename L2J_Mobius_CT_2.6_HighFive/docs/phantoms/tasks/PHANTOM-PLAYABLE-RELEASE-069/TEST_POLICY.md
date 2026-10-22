# TEST_POLICY069 — не сжечь лимит и 12 часов ради тестов

1. До исправления диагностика конкретной общей причины. Одна test-матрица Native `low,high,member,negative` на P0 level/curses, без случайного выбора сотен эпиков. Для Queen Ant не обязательно убить босса, нужно проверить, что способность не применяет проклятие в native attack/aggro/cast. Drop probability проверяется стабильно как policy, не фармом тысяч босcов.
2. M1 обычный Main: 5–15 минут полезного изменения / собственный owner / checkpoint; не «ждать 6min × 12 групп». Сохранять имя/id/cursor/EXP/loot, потом один restart. 1–2 archetype combat. Если встречен stuck cause дважды — минимальный causality fix и повтор затронутого пути, не тотальный soak.
3. M2/M3 старые проверки SERVER_VERIFIED — только affected light check 2–4 кейса, не дублировать 1000+ тестов.
4. M4/PvP: legal response and refusal; karma apply, anti-repeat; не провоцировать бой настоящего владельца и не выдавать «all PvP done».
5. M5 Raid: 1 короткая encounter внутри native fight: verified damage/heal/MP, tank-aggro, minion reacquire/retreat; максимум 10–15 минут общей сцены. Никогда не повторять без причинного изменения. Boss25199 kill не обязан случиться.
6. M6 instance: native entrance + exit + wrong fee/reuse level/current state controls; Frintezza scoped level input без полного многочасового лабиринта. Network actual logout не эмулировать.
7. M7 profession: deterministic near-threshold scenes at 19→20,39→40,75→76 separately, one true learned native skill + cost, one save/reload, not actual days 1→85.
8. Хозяйство: one earned receipt for buy, charge and valued loot; если завершение stage нельзя доказать за 15мин native — mark PARTIAL; не добавлять setup в claimed earned.
9. Разрешено использовать cheap property tests covering many data rows, but **один** targeted DB/native command per root cause, no repeated full `ant verify`, no multi-day background waits. Ant serial. Test harness may be fixed, but no hours wasted on exact fixture names/permissions.
10. Главный hard gate обычный Player persistence и отсутствие дублирования/потерь. Одно хеширование items/skills/vars/quest before/after sameDB restart, без изменения TestAdmin или kpCat. Не трогать клиент до READY_OWNER_PLAY.

Отчёт каждой строки ACCEPTANCE: `RESULT`, 1-2 команды (exit), evidence path, ограничения и runtime/JAR binding. Не смешивать предполагаемое поведение с полученным. В частности `PLAYER_WORLD_VISIBLE` подтверждённым сервером отличать от увиденного настоящим человеком — последнее отложено до первого релизного входа владельца.
