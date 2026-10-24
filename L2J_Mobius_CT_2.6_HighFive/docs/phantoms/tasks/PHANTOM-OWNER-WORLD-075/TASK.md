# PHANTOM-OWNER-WORLD-075 — Owner World: настройки, экономика отдыха и непрерывность

**ВАЖНО:** запускать только после опубликованной, остановленной TASK074; не параллельно с ней в одном checkout.

**Цель владельца:** одна комплексная реализация A–F, но не шестикратная тестовая кампания. A/B — немедленная фиксация настроек в tracked SOURCE с проверкой каждого будущего build/candidate; C — точная проверка прежних quest fixes; D — естественные offline-лавки обычных фантомов после смен; E — историческое течение жизни во время выключенного GameServer; F — удобный native брокер товаров и указатель на продавца, только если поддерживается клиентом.

## 0. Репозиторий, authority и baseline

- Git root: `D:\Codex\repos\L2J-phantom-world`, module `L2J_Mobius_CT_2.6_HighFive`, branch `feature/phantom-world`, remote `https://github.com/kpCat/L2J`.
- **Не использовать `6057cc69728` как точный baseline этой задачи:** это исходная версия TASK074. 24.10.2026 remote HEAD прочитан как `8ac113257bea0cd3ea241b708889b42b42b5b961`; 074 закончена как BLOCKED/PARTIAL, а не PASS. Перед записями повторно проверить local/remote HEAD и отсутствие активной 074. Не требовать 074 COMPLETE, но и не скрывать PARTIAL. Если TASK074 не опубликована — не начинать записи; можно только read-only brief.
- JDK25, Ant, GeoData203, штатные MariaDB/Player/Inventory/World/Quest scripts; root `AGENTS.md`, `CODEX_WORKFLOW_CONTRACT.md`, `PHANTOM_CURRENT_STATUS.md` и финальный report074 обязательны.
- Пакет разрешает адресные production/source/config/test исправления A–F и exact-path normal non-force push; подробности в `AUTHORITY.md`.
- Прочитать `HANDOFF.md`, `OWNER_DECISIONS.md`, `OWNER_PROFILE_38.tsv`, `PROFILE_38_IMPORT.md`, `REVIEW074_HANDOFF.md`, `SOURCE_MAP075.md`, `EVIDENCE.md`, `ACCEPTANCE.tsv`; минимальная цепочка read-first — ниже.

## 1. Read first (конкретные файлы и символы, без массового чтения)

1. Root `AGENTS.md`; `docs/phantoms/{CODEX_WORKFLOW_CONTRACT.md,PHANTOM_CURRENT_STATUS.md}` плюс `REVIEW074_HANDOFF.md` и `SOURCE_MAP075.md`: контракт и актуальный срез.
2. `docs/phantoms/reports/PHANTOM-LIVING-SYSTEMS-074.md` + финальная matrix, если опубликованы; не превращать старые PARTIAL в PASS.
3. `dist/game/config/Player.ini` секции Weight/PrivateStores/Inventory/Warehouse/Recipes и `java/.../config/PlayerConfig.java` соответствующие поля; 38 bundled rules из `OWNER_PROFILE_38.tsv`.
4. `dist/game/data/{EnchantItemGroups.xml,EnchantItemData.xml}`; `java/.../model/item/enchant/{EnchantScroll.java,AbstractEnchantItem.java}` и `data/xml/EnchantItemGroupsData.java`, `services/EnchantItemService.java`.
5. `dist/game/data/stats/skills/01500-01599.xml` skill1557, `dist/game/data/scripts/handlers/skill/effects/ServitorShare.java`, `java/.../model/actor/Player.java#getServitorShareBonus`.
6. `java/.../phantoms/{economy/PhantomAutonomousMarketProducer.java,economy/PhantomOfflineMerchantService.java,population/...,player/...,background/...}` — только lifecycle/owner/shift/market interfaces found by exact search.
7. `java/.../data/sql/OfflineTraderTable.java`, `phantoms/economy/PhantomMultipartyEconomyService.java`, `phantoms/commerce/PhantomOfflineSupply.java`: native offline + reserve/receipt.
8. Quest Goal037 original result / quest-rate config and actual Q00298/Q00186 scripts. Locate by exact quest IDs/names, not a full codebase re-audit.
9. Existing PersonalCharacterQoL.ini WondrousCubic: confirmed TASK067, no changes requested.
10. Item Broker search: native relevant NPC/HTML/Radar interfaces; protect stock ItemAuction NPC32320–32322.

## 2. Обязательный порядок исполнения

**HARD A. Постоянный owner settings profile.**
- Из действующего SOURCE создать/поддерживать ТРЕКАЕМЫЙ единый owner profile + manifest фактически принятых config/XML путей и значений. **Ровно 38 правил сейчас включены в пакет как `OWNER_PROFILE_38.tsv` (SHA256 `c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c`)**; сопоставить каждое с SOURCE/effective load, ничего не придумывать и не пропускать (см. `PROFILE_38_IMPORT.md`).
- Установить known owner's Player.ini targets из `OWNER_DECISIONS.md`, не затрагивая user secrets, endpoint ports, account allowlists, БД, network/login. Загрязнённые пользовательские значения сначала сохранить/сверить; для non-owned managed paths — отказ, не перезапись вслепую.
- Обеспечить, что **все будущие обычные Ant сборки и сборка candidate** берут tracked source значений, валидируют манифест и НЕ откатываются на stock при переносе клона. Отдельные user/private runtime overrides фиксировать как local-only и не коммитить. Не делать огромный generator: существующий copy/build pipeline предпочтительнее.
- Сразу после самостоятельной успешной A/B части сделать разрешённый точный commit (+ normal push, если remote безопасен), чтобы долгие D/E/F не оставили настройки без фиксации.

**HARD B. Заточка и Servitor Share.**
- Обычные Scrolls с прежним maxEnchant=16 поднять на +25; SPECIAL с собственными 6/10/15/23/50 не менять без доказанной необходимости; согласовать `EnchantItemGroups.xml` и `EnchantItemData.xml`, `OverEnchantProtection`, zero-chance above +25, blessed/ancient/support/bonusRate100. Указанные ставки — 100/85/70/66, границы в OWNER_DECISIONS.md.
- Servitor Share skill1557: независимые владельческие множители **для КАЖДОЙ положительной базовой доли передаваемого stat**, дефолт2.0, величины исходных XML сохраняются. Формула `newCoeff=1+(stockCoeff-1)*multiplier`; ни другие навыки, ни pets, ни глобальные NPC stats не изменять.
- Проверки: XML validation + 1–3 точечных теста маршрута обычного/bonusRate100/no-overcap, Servitor Share ON/default/OFF per-stat parity. Никакого долгого клиентского soak.

**HARD C. Возврат ценных старых Quest исправлений, только при регрессии.**
- Найти и проверить фактический Goal037 и quest-rate источники; определить, не исчезли ли при новом clone tracked изменения ставок дропа и наград (XP/SP/Adena/items) квестов. Доказанное сохранение = PASS без фиктивного переписывания всех квестов.
- Для повторяемого SP квеста `Q00298` проверить stock/владельческий контракт: взятие/сдача/награждение при высоком уровне, repeat и отсутствие скрытого cap. НЕ путать с `Q00186`, у которого другие правила. При потере исправления править адресно + focused test/скриптовая проверка.
- Нельзя вносить глобальное бесконтрольное умножение всего drop/reward или double rate. Для каждой категории отдельно документировать canonical multiplier и fixed-quest exemptions; где нет канонического коэффициента — указать пробел, не выдумывать.

**PRODUCT D. Нормальные Phantom становятся настоящими временными offline торговцами во время отдыха.**
- Не превращать всех phantom в специализированные merchant identities. На подходящих карьерных профилях: достаточный законный surplus/нужда в закупке/изученные рецепты + конец смены или разумная planned торговая активность → тихий штатный переход с единым identity lease → native `SELL`/`BUY`/`MANUFACTURE`.
- Подбирать ассортимент из ФАКТИЧЕСКИХ earned inventory и known native recipes; tradeable/nonquest/surplus only. Не продавать снаряжение, ключевые материалы, собственные стартовые субсидии, reservе. Не выдавать новые Adena/предметы/skill/recipe. Некрафтящие классы не получают crafting навыки.
- Посадка преимущественно Гиран, только проверенный Geo surface/peace/town; **физический зазор** между точками (не садить «в одного игрока»), respect existing humans and stalls. Перевод может использовать существующее законное non-visible/background travel к anchor, НО не телепортировать в видимости игрока и не ломать владение Player/Store.
- Offline trader постоянно существует как реальный native Player и stock store. Возврат в жизненный цикл после расписания/после продажи с сохранением денег, item object ids, issued/cancelled transactions и restart recovery, bounded hysteresis. Персонаж не бежит торговать после каждого единственного дропа.
- Выключенный Phantom/offline subsystem без дополнительных Player и DB jobs. Никогда не снимать чужой offline магазин.

**PRODUCT E. Downtime historical catch-up.**
- При STOP сохранять settlement/cursor и timestamps; при следующем START считать прошедшее время по существующему background/historical механизмам (защита от часов назад/огромного downtime), НО не исполнять подряд миллионы AI/DB ticks. Соблюдать уже существующие causal credits, item/adena conservation, quest/raid/death/reuse/party conditions и дневные смены/интересы.
- Симуляция условной жизни между включениями — bounded deterministic progression там, где штатный background planner реально обосновывает её из data. Ни фиктивные покупатели offline-лавок, ни награды/спойл с отсутствующим источником, ни бесплатная проверка finish. Проданное за выключенный период без корректного контрагента не считать фактической сделкой.
- Restart sameDB idempotent: второй START не создаёт второй earned interval, вторые покупки/награды, дубли личности, другого owner; нет materialization pop-in в поле зрения.

**OPTIONAL F. Нативный item broker / поиск рынка.**
- Read-first найди текущий штатный broker/search и класс `Radar.addMarker`/аналог; не утверждать отсутствия без аудита. Если нет готового — ограниченный index/view существующих SELL/BUY/MANUFACTURE реальных лавок с категориями (weapon/armor/jewelry/grade/material/recipe/shot), названием, quantity, total price и seller location.
- У выбранной записи — optional native Radar.addMarker к текущему продавцу/лавке, **без телепорта** и без клиента-мода; выбранный listing revalidate immediately. Скупщики (BUY) и продавцы (SELL) разделены; native craft recipes отдельно.
- Никаких изменений штатному ItemAuction NPC32320–32322, не создавать полную Web Auction UI. Если серверный пакет или UI клиент не поддерживает — `CLIENT_EXTENSION_REQUIRED / DEFERRED`, без огромного нового инструмента.

## 3. Рабочие границы и критерии

- Не запускать TASK075 одновременно с TASK074, не использовать legacy C: worktree, не пересоздавать TEST071–074, не менять старые candidates/DB, user accounts/passwords, MariaDB service, master.
- НЕ тратить больше времени на доказательство одного маленького сюжета, чем на четыре работающие фичи. Один bounded native Main smoke + sameDB restart только если менялись D/E, не на каждый конфиг. Защита ownership/anti-dup — обязательная; для pure XML/config достаточно narrow checks.
- При неисправимом блокере отдельного D/E/F — сохранить безопасный результат A/B/C, per-lane честный PARTIAL. Следующей задачи автоматически не создавать. `TOTAL=PASS` только если все HARDS A/B/C и заявленные продуктивные D/E проверены; F optional учитывается отдельно.
- Один `docs/phantoms/reports/PHANTOM-OWNER-WORLD-075.md`: A..F gates, code/config changes, 38 отдельных строк OWNER_PROFILE_38.tsv со статусом и effective/source сверкой; какие реально implemented, exact builds/checks, Git SHA, no false owner-client QA.
- Обычный non-force push только `feature/phantom-world`, exact staging и clean status; никакого push с чужими untracked. При неуспехе не оставлять некомпилируемый production patch.

## 4. 074 integration guard / critical decisions

- Task074 закрыта результатом PARTIAL, а не игнорируется: существующие SELL/MFG index, paid quote, item ownership, native receipt сохранять. `World` сейчас индексирует SELL и MANUFACTURE, не BUY.
- PRODUCT D должен создавать сменную торговлю обычного earn-based Phantom; не переименовывать выделенные prepared merchant identities и не выдавать им fake earned inventory.
- Для 20 линий в лавках действительно проверить текущий hard cap в `PhantomAutonomousMarketProducer`/`PhantomOfflineMerchantService`/native packets. 20 в Player.ini не доказывает 20 реально используемых lines; stock только owned.
- HARD 38/38: обязательны `PLAYER/ENCHANT/SERVITOR/STORE/CUBIC/QUEST` строки. Для одной строки `QUEST` задано PRESERVE_EXISTING_VALUES, не установка rates=1 и не догадка об утерянной Goal037 конфигурации.
