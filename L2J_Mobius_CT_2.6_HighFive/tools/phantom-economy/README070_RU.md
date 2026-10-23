# Ручные цены TASK070

`Price-PhantomEconomy070.ps1` расширяет подход TASK068 отдельным контрактом070. Старый CLI и его владелец не изменены. Runtime читает только `game/data/economy070/pricing070.tsv` с соседним SHA-256; запуск ничего не пересчитывает и не применяет. Поменяв Rates.ini без Apply, оператор продолжает видеть прежние применённые цены.

База x1: Mana1000 Adena, Premium1000000 Adena/сутки; subclass.gateway100000000 Adena как явный авторский floor, а не розничная цена или измеренный доход. Codex6622/9625/9626/9627:20/40/50/100млн Ancient Adena. Исходная цена6622=550 Olympiad Tokens в103.xml не меняется. Epaulette91006 копирует три предложения fortress90001000 с обеими валютами9912+57. Все28 quest-строк и16 scroll-строк сохранены; оба8056 и три clan-предмета имеют единые x1 цены во всех маршрутах.

Политика `data/economy070/price-policy070.properties` содержит доступные без перекомпиляции авторские x1 цены и эпические floor. `STORE_CATALOG070.tsv` фиксирует числовой canonical x1 с источниками. Конкретный Plan снова читает проверяемую политику; явная правка цены в политике отражается в новом Plan, не меняя сохранённую базу и уже применённые цены. Эпики используют max(shared fair×5, полного обычного S80/S84 jewel set×10, авторского rarity-tier floor, авторского бюджета попытки/реального выхода). Для Baium/Freya/Beleth paired Adena fair отсутствует; применяется явно обозначенный AUTHOR_RARITY_OPPORTUNITY, а не выдуманная retail-оценка. Beleth — проверенная скриптовая награда убийце, не обычный drop.

Adena multiplier вычисляется общим `PhantomRateAwareLootFairValue.expectedYield` по фактически spawned обычным NPC и их item57: отношение ожидаемого выхода с текущими rate/per-ID overrides к x1. Так учитываются caps/grouped branches; XP/SP и premium не умножают цену. AA имеет отдельное отношение выхода6360/61/62×3/5/10: рост только Adena не повышает AA Codex. Оценка source-weighted, одинаковый вес источников; это не измеренная Adena/час конкретного класса. Effort в quest-аудите честно UNKNOWN, минимум30/180мин — авторский ориентир; денежный floor явный, без утверждения о фактической скорости фарма.

## Порядок

Задайте абсолютные пути своей070 поставки и нового внешнего каталога. Baseline создаётся один раз на исходном экономическомx1, до первого runtime Apply. Не переснимайте baseline из применённых цен. При изменении состава SKU нужен отдельный review нового контракта.

```powershell
$game070 = '<свой абсолютный runtime070>/game'
$baseline070 = '<свой внешний каталог>/canonical070'
$plan070 = '<свой внешний каталог>/plan-x10'
$apply070 = '<свой внешний каталог>/apply-x10'
$tool070 = '<каталог tools>/phantom-economy/Price-PhantomEconomy070.ps1'
powershell -NoProfile -ExecutionPolicy Bypass -File $tool070 -Mode Baseline -GameRoot $game070 -OutputDirectory $baseline070
# Штатный STOP.cmd; сохранить Rates.ini и явно изменить нужные rates.
powershell -NoProfile -ExecutionPolicy Bypass -File $tool070 -Mode Plan -GameRoot $game070 -OutputDirectory $plan070 -BaselineDirectory $baseline070
$sha070 = (Get-FileHash (Join-Path $plan070 'plan070.json') -Algorithm SHA256).Hash.ToLowerInvariant()
# Просмотреть PRICE_TABLE070.tsv, EPIC_CATALOG070.tsv, CONFIG_RATE_SNAPSHOT.tsv.
powershell -NoProfile -ExecutionPolicy Bypass -File $tool070 -Mode Apply -GameRoot $game070 -OutputDirectory $apply070 -PlanPath (Join-Path $plan070 'plan070.json') -ExpectedPlanHash $sha070 -OwnershipManifest (Join-Path (Split-Path $game070 -Parent) 'OWNERSHIP070.json')
powershell -NoProfile -ExecutionPolicy Bypass -File $tool070 -Mode Rollback -GameRoot $game070 -OutputDirectory $apply070 -PlanPath (Join-Path $plan070 'plan070.json') -ExpectedPlanHash $sha070 -OwnershipManifest (Join-Path (Split-Path $game070 -Parent) 'OWNERSHIP070.json')
```

При явном classpath используйте `-JavaClassPath '<libs>/GameServer.jar;<libs>/*'`. Apply/Rollback требуют owner=`TASK070_ECONOMY_QOL`, точный gameRoot, allowPriceApply=true в OWNERSHIP070.json внутри game или его родителя. Guard проверяет marked JVM/живые PID-incarnation и требует STOPPED. `game/.price070.lock` запрещает одновременно START и mutation; START070 должен отказывать при наличии lock. После аварии stale lock нельзя удалять без проверки STOPPED, всех hash и journal/backup. Чужие поставки и source tree не являются целью Apply.

Exact allowlist записи:600012.xml,600024.xml,91002.xml,91003.xml,91005.xml,91006.xml и два pricing070 TSV/SHA. Изменяются только подготовленные полные файлы с совпавшими preimage; все inputs и canonical hash проверяются. Есть backup, повторный Apply проверяет postimage, Rollback сохраняет поздние изменения и восстанавливает исходные байты. Набор файлов не является общей файловой транзакцией: crash требует разбор фактических hash; blind retry не допускается. Обычная ошибка возвращает уже записанные собственные postimage. Rollback цен не меняет Rates.ini; его возврат отдельный явный шаг. Новому Apply после Rollback нужен новый Plan/output.

NativeMaxAdena берётся из реального Player.ini; текущий source=3000000000000000, а не предполагаемые2.1млрд. Эпические quotes не clamp; ledger и реальные inventory/native tests отвечают за безопасную оплату. Каталог/CLI не доказывают native покупку, transaction recovery или прохождение квеста. `quest-contract070.tsv` отдельно показывает, когда одного предмета недостаточно. В текущем Antharas.java stone3865 проверяется, но не расходуется; Baium cloth4295 расходуется1. Нельзя объявлять оба случая одинаковым consume proof.

Focused gate `Test-PricePhantomEconomy070.ps1` создаёт только собственную файловую fixture: x1→x10→x20→x10, отдельная AA-валюта, двойные fortress-цены, conflict controls, idempotent Apply, byte-exact reverse Rollback и stale lock. Никаких серверов/БД. Проверка требует чтение Windows process inventory для STOPPED guard; отказ ОС должен завершать mutation ошибкой.
