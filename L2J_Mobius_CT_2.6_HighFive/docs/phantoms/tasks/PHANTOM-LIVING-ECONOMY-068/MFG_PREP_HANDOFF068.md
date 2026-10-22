# RELEASE068 — конечная подготовка одной manufacture-профессии

Агент champions_f. Это разрешённый PREMEASUREMENT_FINITE_PROFESSION_GIFT, не заработанный live-прогресс. Source/helper готов; isolated javac exit0. Native/DB/Ant этим helper ещё NOT_RUN. Final codeSHA/build и настоящий release preparation выполняет основной исполнитель после интеграции.

## Exact scope и read-first

Новые файлы только:

1. test/java/org/l2jmobius/tests/phantoms/PhantomRelease068ManufacturePreparation.java
2. docs/phantoms/tasks/PHANTOM-LIVING-ECONOMY-068/MFG_PREP_HANDOFF068.md

Production, TEST guards, runtime tools и existing fixtures не менялись. Прочитаны AUTHORITY068/TASK clauses B/C/D, native SkillTree/SkillLearn/Recipes, PhantomClassSkillLearningTransaction и его L2jProgressionBackend caller checks, RecipeManager, OfflineTraderTable, Player, профильный component CAS, Headless infrastructure-only bootstrap, NativeMerchant/D/AltB fixtures, Runtime068/Common/assembly receipt bindings. Bootstrap повторяет подтверждённый порядок native loaders; здесь нет другого craft/pricing engine. Реальные формулы fee берутся из PhantomPrivateMarketPricingAuthority.

## Границы

Перед ConfigLoader/БД helper требует явный `phantom.task068.release.prepare=true`, exact40 codeSHA и64 whole GameServer SHA256, собственный RELEASE_CANDIDATE root на фактическом диске source module, schema l2jmobiush5_phantom_release_068, loopback127.0.0.1:3308 и pool4. Working directory строго candidate/game. Проверяет ownership068.properties, BUILD_INFO068.json и исходный SOURCE_BUILD_INFO068.json hash/source/JAR binding. Player.class должен быть загружен из exact candidate/libs/GameServer.jar, helper — из phantom-test/bin того же committed build. Historical/native/control task flags, aliases, чужой JAR или отдельный prod-shadow отклоняются. Запущенных own068 control/native JVM быть не должно; перед pool bootstrap read-only connection требует0 других schema sessions. TEST initialize и его guards не вызываются и не ослаблены.

Helper держит exclusive local-play/operator.lock на всю подготовку, не запускает Main/listeners/PopulationManager и не восстанавливает массово offline stores. Никаких ordinary rows не очищает. Только один стабильный account phm068profession, character Craft068Maker. Если identity существует без exact receipt, отказ вместо нового имени или перезаписи.

## Конечный receipt и native результат

В существующем merchant server profile хранится merchant.profession.prepare.v1, schema1. Если ledger отсутствует, он атомарно создаётся вместе с PENDING receipt из текущих настроенных лимитов; capital/stock выдачи не резервируются. Отдельного связанного с персонажем Phantom profile нет. Если ledger уже существует, receipt создаётся CAS с expectedVersion=-1. PENDING сохраняется до account/actor/credit effects. Любой оставшийся PENDING при повторном запуске даёт failclosed/no replay/no refund; новый actor не создаётся.

SkillTree DwarvenFighter class53 подтверждает Create Item172/rank1 только с5 уровня и310SP, без required items/prerequisites. Эти факты проверяются до grant. Native profession gift ровно: EXP до5, SP310, один scroll1666. Общие stock auto-get skills применяются по фактическому уровню; allskills/85/equipment/capital не выдаются. Existing PhantomClassSkillLearningTransaction записывает native skill и тратит SP310→0; это ограниченный profession credit, а не доказательство NPC-диалога обучения. Native registered Recipes.onItemUse поглощает один scroll и сохраняет recipe1 в character_recipebook. Recipe проверяется как dwarven/rank1/100%; native MaxMP должен вмещать30.

Maker получает Adena0, gear0 и ingredients0. Native manufacture listing recipe1 имеет положительную fee из общей source-aware authority. Позиция берётся из первого configured merchant site, фактический Geo height и PEACE/NO_STORE проверяются. Штатный OfflineTraderTable.onTransaction записывает только этого actor; SQL postconditions проверяют account/access/class/level/SP/vitals, actual skill/book, zero item rows и exact native offline listing/fee. READY сохраняется только после этих проверок; mirror MFG_PREP068.properties создаётся в candidate с CREATE_NEW. Если mirror не удалось создать после durable READY, DB receipt остаётся authority, helper не повторяет grant.

Повтор READY читает/проверяет native durable rows и возвращает ALREADY_READY без credits, лечения, загрузки/спавна actor или редактирования inventory. После последующих заработанных изменений строгая premeasurement проверка может отказать: это не разрешение обнулить/пополнить владельца.

## Точная команда после committed build/assembly

В PowerShell из `<candidate>/game`, когда own release и TEST JVM остановлены:

```powershell
java -Xmx4g '-Dphantom.task068.release.prepare=true' -cp '<candidate>/libs/GameServer.jar;<candidate>/libs/*;<matching-build>/phantom-test/bin' org.l2jmobius.tests.phantoms.PhantomRelease068ManufacturePreparation '<candidate>' '<exact-committed-codeSHA>' '<exact-candidate-GameServer-SHA256>'
```

`candidate` строго `<actual-drive>/PhantomWorld/Living-Economy-068-candidate`. Не использовать source/game, native-test, runtime-test или067. Helper не добавлять в ordinary START wrappers.

После PASS основной исполнитель читает MFG_PREP068.properties и добавляет единственный characterId в **candidate-only** config/Custom/PhantomMerchants.ini MerchantPreparedOfflineCharacterIds, затем обычный START. Stock OfflineTraderTable восстанавливает actor; existing reservePrepared расходует один lifetime ordinal и0capital/stock, присоединяет тот же World Player. При первой поставке это занимает одно место из target200; при уже созданных200 добавляет одну роль в пределах hardcap/lifetime. Не переносить TEST D068Crafter,10b/85/gear либо TEST rows в RELEASE.

Нужный actual native gate после подготовки: ordinary Main восстановил непустую MANUFACTURE лавку и усыновил exact actor, fee>0; источник денег/ингредиентов customer записан отдельно; native ManufactureService/RecipeManager переводит fee и расходует ingredients/MP ровно один раз. Recipe1 при100% производит500 Wooden Arrow из4Stem+2IronOre и MP30. Этот helper не исполняет customer craft, не выдаёт ему деньги/материалы и не объявляет будущий paid craft PASS.

## Проверки и остаток

Изолированная команда `javac -encoding UTF-8 -cp <dev-GameServer.jar;module-libs/*> -d <own-task>/mfg-prep-draft/bin <helper.java>` завершилась exit0. Это только compile readiness; production shadow/JVM/DB не запускались. Parent integrated compile и native подготовка остаются обязательными.

Непроверено запуском: release metadata guard на фактической assembly, минимальный native loader bootstrap, durable skill/recipe/store postconditions и ordinary restore/adoption этой новой identity. При любом сбое сохранять PENDING и первоначальные stdout/stderr; повторный grant, новое имя, funding repair или ручная смена PENDING наREADY запрещены.

Mojibake-маркеры в двух новых файлах проверены отдельно. Escaped Cyrillic/XML escaped Cyrillic проверены отдельно. Git-команды в этом блоке не выполнялись.
