# HANDOFF075 — вход в кампанию после TASK074

1. TASK074 финализирована Codex как TOTAL PARTIAL/Goal BLOCKED и опубликована; это достаточный закрытый запуск для входа в 075, но не выдуманный TASK074 PASS. Сверить OWN074 STOP и отсутствие live writers, не стартовать одновременно.
2. Проверить root AGENTS, local/remote feature HEAD; GitHub ref на 24.10.2026 `8ac113257bea0cd3ea241b708889b42b42b5b961` — снимок, НЕ безусловная константа. `6057cc69728` это **НЕ** parent TASK075 (это начало TASK074). Не использовать git reset/rebase/clean/stash для подгонки parent.
3. Сначала прочитать `REVIEW074_HANDOFF.md`, `SOURCE_MAP075.md` и 38 конкретных строк `OWNER_PROFILE_38.tsv`, проверить SHA256 `c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c`; больше не искать файл в Downloads. Сверить защищённые user-config файлы и старые deploy roots. `dist/game` в Git содержит публичные stock/config значения; реальные старые пользовательские локальные private изменения могли пропасть при клонировании на D. Не заявлять, что обнаружены/восстановлены их точные байты без источника.
4. Уровень мышления Codex: **Very High / Очень высокий** (cross-owner lifecycle, конфиги, деньги, restart/idempotency). При отсутствии отдельного child агента всё делать в том же диалоге.
5. A+B делаются первыми и публикуются безопасным промежуточным commit. C — read-only diff first; D/E требуют реальной кодовой реализации; F минимально/optional.
6. Технологии: Java25 / Ant; один main ordinary build после product code, допустимые узкие tests на конфиги и inventory/offline; нет отдельной сотни schemas/soak. Прежний `PersonalCharacterQoL.ini` WondrousCubic уже готов в TASK067, не переписывать.
7. Read-first места:
   - `config/PlayerConfig.java`, `dist/game/config/Player.ini`
   - `data/EnchantItemGroups.xml`, `data/EnchantItemData.xml`, `EnchantScroll`, `EnchantItemGroupsData`
   - `data/stats/skills/01500-01599.xml`, `scripts/handlers/skill/effects/ServitorShare.java`
   - `PhantomSystem.java`, `PhantomAutonomousMarketProducer.java`, `PhantomOfflineMerchantService.java`, `OfflineTraderTable.java` (см. точные реальные пути в SOURCE_MAP075.md)
   - `PhantomHistoricalBackgroundService/Store` and existing cursor/advance ownership
   - original Goal037 report and native Q00298/Q00186 scripts, locate by exact IDs
   - native broker and Radar implementation before any UI code.
8. Если внутренние task075 ownership/contracts конфликтуют с более новым 074, сохранить действующую 074 архитектуру, описать difference и не откатывать работающие features.
