# PHANTOM-BARTZ-CRAFT-COMPAT-001

**PATCH_READY_CRAFT_COMPAT_OWNER_MIGRATION_REQUIRED**. База `c87f79f2d10af2b649b8c4f80e24561fcdff8f73`, ветка `feature/phantom-world`. Применение к рабочему Bartz требует отдельного решения владельца.

## Доказательство для 20 ролей

- Архивный `GameServer.jar` из сборки `6bd9c077465e1bf155cb4370301cf34df6735b4f` имеет SHA-256 `3b9a99555fa8b0d14c2df2427f8e28b3d8129f62ab6c89873fd79c99dfd90bb4`. Хеш совпал с записью о сборке и отчётом о recovery. SHA-256 архива SQL до перезапуска `07d00b98ee233acca70b39291d9e9c599ec574741ad9ac8332c4934b7efa08bc`.
- Для каждой старой квитанции заново вычислен v1 хеш по четырём классам архивного JAR, исходному порядку полей и данным рецептов. Получено 20/20 точных совпадений с архивными строками и 20/20 расхождений при подстановке классов действующего JAR. Из четырёх классов изменился только `Player.class`, с `cb32fc16c66bfcc16623aa2ca891de7b938bdcf0f7e10fc64ce05427ad651c03` на `dbb9deefc6701c9ef4b5fc22dc2ec17590172b006eb7359deb5c68f74055ab50`. `RecipeData.class`, `SkillTreeData.class` и `PhantomNativeCraftRecipes.class` побайтно совпали. Идентификаторы, ранг, выход, вероятность и упорядоченные ингредиенты всех рецептов в проверенных v1 отпечатках совпали. Все четыре класса кандидата побайтно совпали с классами работающей версии.
- `error0.log` содержит по одному отказу `PhantomNativeCraftRecipes.verify` для каждого из тех же 20 ordinal. Первые 29 289 байт журнала закреплены SHA-256 `380fe00ea2648b5e1593585468209dbb49f07fba2e60bdea034c6e77f5a61e84`. Копия префикса доступна владельцу в `build/bartz-craft-compat-shadow/private-evidence/bartz-startup-attested.log`. Read-only транзакция SQL33331 сверила идентичность, точные квитанции `READY/OWNER_PROVISIONED_CRAFT` версии строки 1, роли `INCONSISTENT` версии строки 4 и неизменный ledger. Байты каждой роли совпали с архивной `READY` после изменения только stage и увеличения версии строки с 3 до 4. Сверены отключённые аккаунты и персонажи, классы, уровни, ремесленные навыки, сохранённые книги рецептов, заголовки MANUFACTURE-лавок и отсутствие связанных операций, резервов и других владельцев. Остаток адены и все шесть предложений с ценами каждой лавки точно совпали с аттестованным SQL-архивом.
- Результат проверки каждой роли `PASS`. Ordinal `0009`, `0017`, `0025`, `0036`, `0044`, `0063`, `0071`, `0082`, `0090`, `0098`, `0109`, `0117`, `0125`, `0136`, `0144`, `0163`, `0171`, `0182`, `0190`, `0198`. Сведения об аккаунтах, токенах и персонажах остаются в локальных источниках.

## Изменения

- `PhantomMerchantCraftReceipt` задаёт merchant v2. Отпечаток включает родословную класса, уровень и ремесленные ранги, нативные рецепты, входы, выходы и дополнительные эффекты рецепта. При чтении существующей v2 квитанции `PhantomMerchantCraftProvision` проверяет семантический хеш, затем применяет действующие native catalog, Player и SQL проверки. Ключ компонента остаётся прежним, версия схемы квитанции становится 2. `PROGRESSION_RECIPE_BONUS` сохраняет исходный verifier и формат.
- `BartzCraftCompat` закрепляет точные SHA архивов и набор 20 ролей. Preflight проверяет журнал и повторяет строгий расчёт legacy v1. Для доказательства допуска рецептов в каталог утилита сверяет цены сохранённых предложений, классы штатного расчёта цен с архивным JAR и исходные XML/XSD/INI рабочего Bartz с закреплённым снимком входных данных. SHA-256 описи 2143 файлов `0676e27a911658021d2a97825dcd79aa8f652c79b8654f882c3cdea129502c59`. Динамические цены на следующем старте проверит штатный серверный каталог.
- Операторский путь повторяет native readback в транзакции `SERIALIZABLE` и выполняет CAS по версии и точным исходным строкам. Все 40 UPDATE коммитятся одной транзакцией после readback. У каждой роли меняется только stage и версия строки. У квитанции меняются отпечаток, версия схемы и версия строки. Повтор с подтверждёнными образами возвращает `NOOP 20/20`. Смешанное состояние или неизвестная причина дают отказ. Утилита требует InnoDB для 12 читаемых таблиц и подтверждённую видимость метаданных триггеров на таблице компонентов.
- В миграции отсутствуют вызовы квалификации, выдачи и штатных игровых writers. Продакшен-стартап утилиту не вызывает. Отчёт dry-run содержит ordinal, причину, версии и old/new fingerprints. Утилита принимает только локальный именованный канал MariaDB с отключённым TCP и сверяет схему и физический datadir. Для будущего рабочего применения дополнительно проверяется отсутствие PID 18792 и TCP endpoint на порту 7777. Владелец должен отдельно обеспечить остановку GameServer и исключить рестарт и других писателей на время операции.

## Проверки

| Проверка | Результат |
| --- | --- |
| Legacy RED, архив и текущий JAR | На исходном алгоритме 20/20 несовпадений после изменения только `Player.class`. Первая диагностическая сверка лавок сравнивала строковые и числовые ID. После исправления SQL-проверка прошла 20/20. |
| `ant -f <shadow-module>/build.xml jar`, JDK 25 | Exit 0, 38/38 правил конфигурации. В первой попытке shadow не содержал двух XML `EnchantItem*.xml`. Повтор выполнен после копирования этих файлов. SHA-256 кандидата `abb5f651da5ef23b2fd11fc6c2a4e1f2f84c58f2273e42dbd5801da3ad0b321b`. |
| `javac` и `PhantomMerchantCraftCompatChecks` | Exit 0. V2 стабилен при смене v1 отпечатка. Изменённые ингредиенты меняют отпечаток, изменённый ранг, отсутствующий предмет и неизвестная версия отклоняются. |
| `BartzCraftCompat dry-run` на чистом pre-restart архиве | Exit 1, `REFUSE` для 20 ролей в `READY`. Ожидаемый отказ для исходного образа без подтверждённого перехода в `INCONSISTENT`. |
| `BartzCraftCompat dry-run` после фиксации на изолированной копии | Exit 0, `NOOP 20/20`. |
| `BartzCraftCompatNativeChecks` на изолированной MariaDB 11.4.13 через локальный pipe | Exit 0. На копии архива воспроизведены только 20 подтверждённых переходов stage. Preflight 20/20. Чужой account и charId, отсутствующий рецепт, другая цена, изменённый остаток адены, stale версия и изменённые байты той же версии отклонены. Нативная ошибка на последнем UPDATE откатила 40/40 изменений. Из двух параллельных CAS один выиграл. Повтор 20/20 `NOOP`. SQL-сверка до/после дала нулевые изменения в ресурсах, персонажах, лавках и компонентах вне allowlist. |
| Вход `apply-offline-bartz` при работающем Bartz | Exit 1 на проверке порта 7777 до чтения входных файлов или подключения к SQL. |
| `PhantomCraft101Checks`, `PhantomMerchant100Checks catalog`, `PhantomMerchant100Checks circulation`, `PhantomShutdown103Checks` | Exit 0 на кандидате. Shutdown 3/3. Рабочий shutdown и игровой рестарт `NOT_RUN`. |

Mojibake-маркеры в изменённых файлах проверены.

Escaped Cyrillic в изменённых файлах проверен.

## Воспроизведение dry-run

Подготовьте физическую изолированную копию SQL-состояния после перезапуска. Используйте отдельный datadir и уникальный локальный pipe с `skip_networking=1`. Имя схемы копии `bartz_craft_compat_fixture`. Передайте неизменённый архивный JAR и исходный SQL-снимок отдельно. Старый SQL-снимок подтверждает данные до ошибки, а изолированная копия предоставляет текущее состояние для сравнения. Сохраните журнал запуска Bartz с зафиксированным префиксом. Используйте физическую копию исходных файлов Bartz из списка `SOURCE_INPUTS` утилиты.

В `jdbc.ini` задайте `Driver=com.mysql.cj.jdbc.Driver`, реквизиты изолированной схемы и `TestDatabaseConnections=False`. Аккаунту нужна явная привилегия `TRIGGER` на `phantom_profile_components` либо на охватывающей схеме для проверки видимости метаданных. Формат `URL` для примера pipe `bartz-craft-compat-01234567` ниже. Конфигурация и результаты остаются в приватном каталоге оператора.

```ini
URL=jdbc:mysql://localhost/bartz_craft_compat_fixture?socketFactory=com.mysql.cj.protocol.NamedPipeSocketFactory&path=%5C%5C.%5Cpipe%5Cbartz-craft-compat-01234567&sslMode=DISABLED&connectTimeout=5000
```

Из каталога `dist/game` изолированной поставки с нативными XML, с абсолютными путями в переменных PowerShell:

```powershell
javac -encoding UTF-8 -cp "$CandidateLibs/*" -d $ToolClasses "$SourceModule/tools/phantom-runtime/BartzCraftCompat.java"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp "$ToolClasses;$CandidateLibs/*" org.l2jmobius.gameserver.phantoms.economy.BartzCraftCompat `
  dry-run $IssuingJar $ArchivedSql $StartupLog $CloneDatadir $CloneJdbcIni
```

Ожидаемый вывод `DRY_RUN PASS 20/20`. Для уже успешно мигрированной копии ожидается `NOOP 20/20`. Перед отдельным рабочим применением владелец подтверждает актуальную резервную копию, окно исключительного доступа, остановку GameServer и конфигурацию выделенного pipe. Рабочая миграция и рестарт Bartz остаются `NOT_RUN`.

## Scope Git

`$Scope` содержит семь путей с префиксом `L2J_Mobius_CT_2.6_HighFive/`:

- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCraftProvision.java`
- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCraftReceipt.java`
- `java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java`
- `tools/phantom-runtime/BartzCraftCompat.java`
- `tools/phantom-runtime/BartzCraftCompatNativeChecks.java`
- `test/java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCraftCompatChecks.java`
- `docs/phantoms/reports/PHANTOM-BARTZ-CRAFT-COMPAT-001.md`

Проверка P0 использовала `git rev-parse --show-toplevel HEAD`, `git symbolic-ref --short HEAD`, `git status --short --untracked-files=no`, `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` и `git worktree list --porcelain`. `git status --short --untracked-files=normal -- <paths>` ограничен каталогами `java/org/l2jmobius/gameserver/phantoms`, `test`, `tests`, файлом `build.xml`, каталогом задачи и путём этого отчёта внутри High Five. `git diff --check -- <paths>` и `git diff --stat -- <paths>` проверили исходные tracked-файлы `PhantomMerchantCraftProvision.java` и `PhantomProfileRepository.java`.

Для публикации P4 разрешает `git add -- @Scope`, `git diff --cached --name-only`, `git diff --cached --check -- @Scope`, `git diff --cached --stat -- @Scope`, `git diff --quiet -- @Scope`, `git commit --only -m "phantom: preserve Bartz craft qualification across unrelated updates" -- @Scope`, `git rev-parse HEAD HEAD^` и обычный `git push origin HEAD:refs/heads/feature/phantom-world`. Точный SHA и итог независимой сверки GitHub фиксируются в итоговом сообщении.
