# PHANTOM-LIVING-COMBAT-071 Implementation Plan

**Goal:** выполнить TASK071 в существующих native owners, выпустить самостоятельную проверенную поставку071 с честной матрицей незакрытых требований.

**Architecture:** Economy070Shop остаётся владельцем durable transactions; CombatService/PvpService — единственными владельцами боя; SupplyJourney/NativeEquipmentPolicy — владельцами снабжения и выбора экипировки. Новые runtime071 tools используют проверенные070 root/schema/PID/hash guards с отдельной authority071, не меняя защищённые поставки.

**Tech Stack:** Java25, Ant, штатный MariaDB JDBC, PowerShell, существующий JDK-only runner.

**Spec:** TASK.md, AUTHORITY.md, PRODUCT_DECISIONS.md, TEST_PLAN.md, ACCEPTANCE.tsv в этом каталоге; исходный пакет неизменяем.

## Ограничения

- Baseline local/remote752a91a27ef1421951246a618a9bdfa79f4769da, feature/phantom-world; normal non-force push разрешён.
- Без новых worktrees, движка, зависимостей, rate rebalancer, owner GUI, soak и изменения защищённых057–070/БД/клиента.
- Bounded exception к правилу8–10 файлов: задача прямо требует несколько связанных native подсистем и собственную поставку; отдельные владельцы файлов, минимальные изменения без общего рефакторинга.
- Все builds/DB/native runs последовательно через основного агента. Независимые source lanes допускаются параллельно по dispatching-parallel-agents.
- Цель2–4ч, freeze product edits после5ч, hard6ч от первого действия; сохранение доказательств в .phantom-local/task071.

## Review Focus

- Native questitem13722, Fame-300, hero/ownership/invalid context и replay не обходят native transfer.
- Economy schema binding отказывает посторонней БД; рестарт не восстанавливает проданный stock.
- Реальные shot/CP handler debit/reuse, skill prerequisite/Souls и defensive shortage не подменены помощником.
- Equipment expertise и purchase quote остаются authoritative; недостающий грейд не генерируется бесплатно.
- Pushkin radius/NPC binding и input enchant/materials сохраняются; меню не выдаётся за доказательство purchase.

## Задачи

- [ ] 1. Economy: прочитать Economy070Shop/Board/Pricing/native multisell/Premium config и070 tests; focused regression сначала, затем token exchange1:20000, Codex11m, exact epic/100 и SHA, native Olympiad aliases38/69, cap default3 в INI, explicit versioned DB authority. Файлы: qol/Economy070*, PremiumLootPolicy, PremiumSystemConfig/ini, economy070 data, собственная migration071 и focused tests. Без build.xml и общих runtime tools в lane.
- [ ] 2. Combat: прочитать PvpService/Policy/Context/Model, CapabilityResolver/Safety/ShotControl/CombatService, CP handlers/stock tests. Focused regression, минимальный role-aware skill/shot/CP/reason слой, без нового planner. Файлы: phantoms/pvp, combat и узкие test counterparts; CP paid merchant при подтверждённом отсутствии. Без SupplyJourney/EquipmentPolicy и build.xml.
- [ ] 3. Supply: прочитать SupplyJourney/CommerceCatalog/OfflineSupply/EquipmentPolicy и аналоги066–070. Focused regression, exact level-grade objective и owned-first procurement, bounded shortage continuation/root cause. Файлы: commerce/progression и focused test counterparts. Без combat/pvp/build.xml.
- [ ] 4. Pushkin: прочитать native Mammon HTML/XML, MultiSellChoose и070 test; адресное NPC30300 eligibility и native menu, 1–3 representative native operations. Optional50/60 только при доказанном безопасном carrier и<=20мин, иначе SKIP_OPTIONAL_WITH_REASON.
- [ ] 5. Delivery: собственные071 tools по070 аналогу, отсутствие/OWN071 schema/root guards,203 physical Geo, committed source build, TEST native focused scenarios, RELEASE Main и sameDB/sameJAR restart, graceful STOP/DB0/ports0. Source tests и fixture setup отдельно от earned outcome.
- [ ] 6. Review/publication: exact changed-path diff, focused verification, отдельные mojibake и escaped-Cyrillic checks, acceptance matrix/report/manifest/archive, commit exact paths, normal push feature/phantom-world. OWNER_VISUAL_PENDING; PLAYABLE_OWNER_READY только при доказанных hard gates.

## Ledger

- Intake: git symbolic-ref/status/worktree/rev-parse/for-each-ref выполнены; исходный dirt только пакеты068V и071,068V не включается в commit. Read-only remote ls-remote подтверждён после разрешённого sandbox network escalation.
- Прочитаны root/module AGENTS, root README, master plan relevant sections, workflow/task-package/release contract, current status, roadmap relevant sections,070 report и весь пакет071. Модульный README отсутствует; дополнительных AGENTS выше repo и внутри целевых source dirs не найдено.
- Ruling: готовый пакет и явное «автономно/без участия владельца» заменяют дополнительный design approval; рабочее решение ограничено указанными native owners. Цена ошибки — честный PARTIAL/BLOCKED вместо недоказанной готовности.
- Continuation: все локальные изменения071 повторно инвентаризированы exact paths; чужие7файлов068V исключены. Независимый source review завершён без открытых замечаний после исправления памяти kill в полном MAX_MEMORIES24. Product owners не заменены.
- Проверки к committed build: Ant green06 SUCCESS; PvP combat9/9, admission2/2,CP2/2,paircooldown4/4; social6/6; supply5/5; standalone combat3/runtimeguard PASS. Native economy/Mammon PASS; native смешанный shot reserve PASS. Старшие грейды, классовые роли и полный earned supply пока не получают PASS за существование source.
- C3 bounded native SELL→BUY→return подтверждён durable receipts в новой JVM. Затем VICTORY без EXP/SP из-за штатной level-gap политики19против4; тестовый вход выбран неудачно. Повторного refill/reseed/снижения HP или rates для зелёного результата нет;3награждённых kills остаются PARTIAL.
