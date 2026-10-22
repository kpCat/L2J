# Пересчёт цен Alt+B — TASK068

`Price-PhantomEconomy.ps1` работает в Windows PowerShell 5.1 и использует Java 25 из PATH. Новые зависимости не нужны. Java exporter входит в GameServer.jar; формулы общие с `PhantomPrivateMarketPricingAuthority`. CLI не запускает сервер и не подключается к БД.

Итоговый candidate068 сохраняет исходные цены Alt+B. Apply — отдельное решение оператора; native TEST-покупка по изменённой цене не делает этот профиль настройкой поставки. Обычные фантомы не получают от CLI новый путь покупки экипировки: их снабжение по-прежнему проверяет native NPC/private-market предложения, потребность и бюджет.

Plan читает конкретный физический `game` и записывает предложение во внешнюю папку. Обход начинается с Custom/home.html и navigation.html, следует фактическим `_bbstop`, `_bbsmultisell`, `_bbsexcmultisell` и `_bbssell` из HomeBoard. Это ограниченный набор достижимых списков. Динамический `_bbsqol`, неизвестные обработчики, неподдерживаемые условия и обмены без единственного обычного ингредиента Adena остаются UNKNOWN/UNPRICED.

## Plan

Используйте абсолютные пути своей поставки. Classpath по умолчанию берётся из соседней `libs`; при работе с отдельной сборкой укажите его явно:

```powershell
$game068 = (Resolve-Path '.\own068\game').Path
$plan068 = [IO.Path]::GetFullPath('.\pricing068\plan')
$apply068 = [IO.Path]::GetFullPath('.\pricing068\apply')
$tool068 = '.\tools\phantom-economy\Price-PhantomEconomy.ps1'
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Plan -GameRoot $game068 -OutputDirectory $plan068
```

Результаты: `plan.json` с SHA-256 входов и точными pre/postimage, `plan.tsv`, `UNPRICED.tsv`, `facts/quotes.tsv`, `facts/offers.tsv`, `facts/sources.tsv`, `facts/unknown.txt`. Одинаковые входы, пути и классы формул дают одинаковые байты plan.json. Manifest содержит конфиги, предметы, NPC, spawn, рецепты, buylist, достижимые страницы и классы формул. Метки `@formula:` являются provenance готового предложения; Apply применяет уже вычисленные exact prices и не запускает формулы повторно.

## Повторный rebalance после изменения Rates.ini

Перед первым Apply сохраните canonical baseline цен в **новый внешний** каталог. Он содержит только достижимые Alt+B XML/HTML, исходные цены, root binding и hashes; Rates.ini туда не копируется. Baseline фиксирует именно проверенные оператором исходные custom/stock цены. Не создавайте новый baseline из уже Applied цен при каждом пересчёте: повторно используйте первоначальный каталог.

```powershell
# Собственная поставка остановлена штатным STOP.cmd; источники и цены проверены.
$baseline068 = [IO.Path]::GetFullPath('.\pricing068\canonical')
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Baseline -GameRoot $game068 -OutputDirectory $baseline068

# Сохраните прежний Rates.ini отдельно. Измените реальные rates только stopped own поставки.
$nextPlan068 = [IO.Path]::GetFullPath('.\pricing068\rates-x20-plan')
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Plan -GameRoot $game068 -OutputDirectory $nextPlan068 -BaselineDirectory $baseline068
```

Рабочий порядок: **STOP → backup/изменение Rates.ini → Plan с тем же baseline → просмотр diff.tsv, target-prices.tsv, UNPRICED.tsv и facts/unknown.txt → явный Apply exact Plan SHA → START**. Каждый Plan заново читает реальные death/spoil/raid chance и amount, per-item overrides включая57, NPC/BOM/rarity и author target-time/scarcity. При отсутствии amount override57 наследует глобальный death/raid amount своего источника. XP/SP не являются price multiplier. `facts/rates.tsv` показывает effective snapshot; исходный Rates.ini имеет SHA в sources.tsv.

Canonical prices используются как стабильные acquisition anchors. Фактические текущие цены сохраняются отдельно как old_price/preHash для diff/Apply conflict guards. `target-prices.tsv` исключает old_price: реальные x10 → x20 → x10 должны вернуть те же target prices и quotes при одинаковых экономических входах и baseline, даже после промежуточных Apply. Полные Plan SHA могут различаться: текущие preimages после предыдущего Apply законно другие. Смена структуры offers/reachability требует отдельного review нового baseline; его перезапись запрещена.

Rollback по-прежнему восстанавливает **цены**, не Rates.ini: вернуть сохранённый rate config нужно отдельным явным действием на stopped собственной поставке. Затем START. Изменённый baseline или позднее редактирование applied XML вызывает conflict без overwrite. Никакого автоматического Apply при startup нет; defaults stock/CustomCommunityBoard OFF не меняются этим инструментом. Без `-BaselineDirectory` сохраняется совместимый read-only Plan с предупреждением о непригодности для повторного rate rebalance.

Профиль по умолчанию — `config/Custom/PhantomMarket.ini`. `-Profile` позволяет выбрать отдельный файл с теми же ключами. Stock rates читаются из реальных конфигов: шанс и количество drop/spoil, raid rates, индивидуальные overrides, Adena, masterwork. XP/SP не являются множителями стоимости.

Авторские параметры отделены от stock: target minutes, kills/minute, consumable cost/kill, scarcity basis points, epic premium. Значение 10000 bps означает 1x, 50000 bps — 5x. Обычные материалы не умножаются всем списком на время фарма. Эпики имеют отдельную классификацию и требуют реального boss loot source; NPC-цена B-grade Queen Ant не служит эпическим якорем. Отсутствие достоверного источника означает UNPRICED.

Плата производства: `max(PhantomMarketManufactureMinimumFee, ceil(source BOM per attempt * PhantomMarketManufactureFeeBasisPoints / 10000))`. По умолчанию минимум 100 Adena и 200 bps. Штатные ингредиенты, MP, шанс и списание обслуживает native craft path.

## Apply и Rollback

Только собственная TASK068-среда может иметь разрешающий файл `OWNERSHIP068.json` внутри `game` или его непосредственного родителя:

```json
{
  "owner": "TASK068_LIVING_ECONOMY",
  "gameRoot": "<абсолютный путь своего own068/game>",
  "allowPriceApply": true
}
```

Этот файл связывает разрешение с одним точным физическим корнем. Не создавайте его в защищённых старых поставках. Перед Apply остановите свою поставку, просмотрите plan.tsv и UNPRICED.tsv, сохраните напечатанный SHA-256:

```powershell
$hash068 = (Get-FileHash (Join-Path $plan068 'plan.json') -Algorithm SHA256).Hash.ToLowerInvariant()
$owner068 = Join-Path $game068 'OWNERSHIP068.json'
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Apply -GameRoot $game068 -OutputDirectory $apply068 -PlanPath (Join-Path $plan068 'plan.json') -ExpectedPlanHash $hash068 -OwnershipManifest $owner068
powershell -NoProfile -ExecutionPolicy Bypass -File $tool068 -Mode Rollback -GameRoot $game068 -OutputDirectory $apply068 -PlanPath (Join-Path $plan068 'plan.json') -ExpectedPlanHash $hash068 -OwnershipManifest $owner068
```

Apply сверяет root, hash, источники и все preimage до записи, создаёт побайтовые backup и меняет только числовой price/count разрешённых строк. Каждый файл заменяется атомарно; весь набор файлов не является одной файловой транзакцией. При обычной ошибке уже записанные собственные postimage восстанавливаются; после аварии с неполным набором нельзя делать blind retry. Нужен разбор сохранённых backup и фактических hash. Receipt сохраняется после полного успеха. Повторный Apply с неизменными postimage возвращает ALREADY_APPLIED.

Rollback требует exact receipt и backup, проверяет все postimage до восстановления. Более поздние изменения сохраняются: при конфликте команда завершится ошибкой. После успешного Rollback для нового Apply создайте новый Plan/output; старый receipt оставлен для аудита. Для загрузки новых цен нужен перезапуск собственной поставки.

## Примеры x10/x20

`-AdenaScale 10 -LootScale 10` и `20/20` дают иллюстративные snapshots без изменения Rates.ini и без запуска сервера. Apply намеренно отвергает такие планы: рабочий Plan должен использовать настоящие rates и scales 1/1. Одновременное масштабирование Adena и количества предметов сохраняет их opportunity ratio, но меняет отдельный авторский бюджет времени для gear.

На stock snapshot TASK068 оба плана содержат 53 PRICED, 46 UNPRICED и 5 файлов изменений. Fair / ask:

| Предмет | x10 | x20 |
|---|---:|---:|
| Stem, 1864 | 200 / 210 | 200 / 210 |
| Crystal D, 1458 | 1300 / 1365 | 1300 / 1365 |
| Scroll Enchant Weapon A, 729 | 900000 / 945000 | 900000 / 945000 |
| Ring of Queen Ant, 6660 | 1304107385 / 1369312755 | 2556000000 / 2683800000 |
| Earring of Antharas, 6656 | 24000000000 / 25200000000 | 48000000000 / 50400000000 |
| Неизвестный ID 999999 | UNPRICED | UNPRICED |

Это цены модели, а не рекомендация оператора. Часть эпиков без достоверной paired Adena/boss связи остаётся UNPRICED. Ограничение MaxAdena проверяется отдельно для применяемых offer.

## Проверки и границы

`Test-PricePhantomEconomy.ps1 -GameRoot <source/game> -OutputDirectory <новая внешняя папка> -JavaClassPath <classpath>` создаёт собственную физическую fixture, проверяет детерминизм, preimage conflict, Apply, повторный Apply, сохранение поздних изменений при Rollback и побайтовое восстановление. Четвёртый focused case проверяет actual Rates.ini x10→Apply→x20→Apply→x10→Apply, одинаковые target/quote bytes, сохранение baseline и inheritance57; затем выполняет обратные Rollback и возвращает исходный Rates.ini. Исходный game не меняется. Java focused mode существующего launcher: `economy068-pricing`.

Anti-arbitrage ограничен доказанными рёбрами: цена поддерживаемого Alt+B bundle выше NPC/crystal liquidation и private BID; runtime BID ограничен свежей NPC acquisition, известной Alt+B acquisition и ожидаемой себестоимостью самостоятельного craft. Offline NPC-якорь — untaxed catalog lower bound, а runtime повторно проверяет реального видимого Merchant и налог. Произвольные циклы quest, валют, restricted multisell, enchant-конверсий и отсутствие NPC в живой сцене этим CLI не доказываются. Сам exporter не загружает полный серверный knowledge loader: он читает stock XML и использует общие valuation helpers.

Native Alt+B purchase, платный manufacture и поведение живых offline merchants требуют отдельного targeted own068 native evidence. CLI smoke их не заменяет.
