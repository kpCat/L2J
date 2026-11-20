# PHANTOM-QOL-QUICK-001

**Игровая приёмка подтверждена. Owner gate REQUIRED.** Native и cold проверки пяти исправлений дополнены проверкой Dwarven Craft в игровом клиенте. Baseline ветки `feature/phantom-world` — `8e1b2c8fe2906d758e0cbd050046d892ae146053`, его родитель — `7b5e2da2b3ac4a525628f5d13afab36e90e84410`. Проверки выполнялись в отдельном checkout от этого baseline. В основном checkout сохранены 15 обнаруженных tracked-правок, пользовательские untracked и его HEAD `b2e6942e5ef635302cab7b2f2e27098837137224`.

## Приёмка

| Gate | Доказательство | Осталось |
| --- | --- | --- |
| G0 | GitHub подтвердил baseline, JDK 25.0.4.1, Ant 1.10.17. Player/quest tests выполнялись на отдельной `phantom_qol_quick001_test` с MariaDB 11.4.13 на 13308, LS на 2106/9014 и GS на 7777. Все свои listeners на loopback. | Postflight PASS. |
| G1 | [Актуальная сводка R43](PHANTOM-WORLD-COORDINATION-130-R43-STATUS.md) связывает технический результат G6 с публичным G10 и отдельно указывает Owner gate REQUIRED. | Сохранены оба исторических документа R39 по решению владельца. |
| G2 | Native UseItem списал 728 3→2 и восстановил 1731→2031 MP, reuse 500 мс, повтор в reuse без списания, Olympiad отклонён. Сохранён native cap по recoverable MP, дополнительно проверены +150/+0 и invulnerable. Alt+B multisell 91003 указывает 1000 Adena ×1. | Цена подтверждена источником реальной торговли, покупка в TEST отдельно не проводилась. |
| G3 | Ранги 1–3 на живом Player дали Physical Skill Power 100→110/114/120→100. С сервитором 1557 сохранил коэффициент PAtk 2.0, 1547 менял исходную величину владельца один раз, снятие обоих вернуло штатный PAtk сервитора. Focused-матрица всех шести статов OFF/0/1/2, NaN → stock. | Клиентский tooltip остаётся штатным. |
| G4 | Владелец в игровом клиенте выучил 1321 у Holvas и подтвердил, что навык виден и работает. Сохранение 1321 подтверждено в TEST БД. Отдельная native проверка списала 620 SP за 172, открыла книгу с рецептом 1 и изготовила 500 Wooden Arrows. Холодная проверка подтвердила навыки и рецепт. Допуск отклонён для неразрешённого аккаунта и зарезервированной идентичности Phantom до подключения headless-сессии. | Визуальная активация подтверждена владельцем. Выпуск предмета, расход материалов и MP зафиксированы серверным тестом. |
| G5 | Правило активно для 43 рангов семи семейств. Native UseItem открыл gaps −60, −22, −1, 0, +6 при lvl24 и +5 при lvl79. При +8 и +6 при lvl78 HP и признак смерти сохранились. Каждая попытка 2065 списала один ключ, EXP/SP 0. Реальный `OpenChest` отклонил мёртвую цель и цель в другом instance. | Штатная таблица drop и путь смерти сохранены. |
| G6 | Native сдача 50+50 дала при rate 35× на lvl25 +1 837 500 SP, lvl35 +1 984 500 SP. EXP 0. При 50+49 стеки, SP, EXP и quest state сохранились. Повтор старого turn-in SP не добавил, штатный старт нового цикла прошёл. | Focused проверка 1×, дробей и насыщения сохранена. |
| G7 | RED 0/5 на stock, GREEN 5/5 из финального JAR. Основной Ant unit 66/66, проверка классов JAR и Java 8 script compile, OwnerConfig Save/Apply/Verify. Финальный cold прошёл на той же отдельной БД. Штатная остановка GS, LS и SQL и сверка портов подтверждены. | Подробности в квитанциях ниже. |
| G8 | Точный scope кандидата и HEAD ветки проверены. Исторические файлы TASK130, игровые среды и overlay сохранены. | SHA публикации и независимая сверка GitHub фиксируются в отдельной квитанции после push. |

### Документальная сверка TASK130

В [handoff R43](../tasks/PHANTOM-WORLD-COORDINATION-130/continuations/POST-R39-G6-20261119/POST-R43-HANDOFF.md) указан native trace `native-20261119-184931`, PID 12280. Он связывает отказ, прежний STORE, другую цель, новый штатный состав, положительный DAMAGE и финальный STORE. Вывод относится к выбранной сцене восстановления.

Receipt `r43-github-readback.json` в исходном каталоге логов TASK130 имеет SHA-256 `883D24219DC3A40A5E63099E1A15E9754417B33CE6EBC706E58BDC54CBD3A782`. Он фиксирует обычный push и независимую сверку G10 для HEAD и parent выше. GitHub API подтвердил HEAD и diff опубликованного handoff. Владельческая ручная приёмка остаётся обязательной. TASK130 boots за эту задачу — 0.

## Конфигурация и правила

- `config/Custom/ServitorShare.ini`. Поставляемый `EnableSpiritSharingBonusTuning = True` и шесть независимых множителей 2.0. При отсутствии переключателя действует stock. При OFF либо 1.0 коэффициент сохраняется. 0.0 убирает прибавку. Разрешены конечные значения 0–100. Некорректная настройка оставляет все шесть коэффициентов stock. Настройки читаются при запуске. Реализация 1557, её XML и прежние значения INI сохранены.
- `config/NPC.ini`. По уточнению владельца поставляемое значение `AllowOpeningLowerLevelChests = True`. Без записи в старом конфиге либо при явном `False` действует исходный порог с обеих сторон. Любой подходящий ключ или навык проходит тот же `OpenChest`. Для сундуков ниже персонажа снят порог по уровню, для равных сохраняется допуск, для более высоких действует прежний бинарный порог +6 до уровня 77 включительно и +5 начиная с 78. Условия специальных ключей и квестовых предметов сохранены. Успешное открытие использует штатный путь drop, смерти и подавления EXP/SP.
- Native RED выявил, что у `Box Key` 2065 не было штатного входа расхода. Без `immediate_effect` обработчик предметов не списывал ключ. В `02000-02099.xml` добавлены `itemConsumeCount=1` и штатное соответствие рангов 1–8 предметам 5197–5204. Расход идёт через skill cost до эффекта, с сохранением штатного reuse и целевых ограничений.
- Для 1321 применяется действующий allowlist `PersonalCharacterQoL` с допуском обычного персонажа на основном классе, проверкой прав NPC и штатными требованиями дерева и пакетов. Зарезервированные идентичности Phantom и Background исключены уже на проверке навыка при восстановлении. В клиенте проверен Holvas (30058). SP для 1321 в исходном дереве равен 0. Для 172 действуют требования рецепта и штатная цена обучения. Книга открывается через `OpenDwarfRecipeBook` и `RecipeBookItemList`. Хранилища навыков и рецептов и схема БД сохранены.
- Q298 начисляет 52 500 / 56 700 SP на 1×, 1 837 500 / 1 984 500 на 35× при уровнях 25 / 35, без добавочных множителей персонажа. Бонус вычисляется от целого штатного SP, дробная часть отбрасывается. При переполнении действует предел int на уровне Quest. Штатный выход очищает зарегистрированные предметы. Monitor quest state сериализует две одновременные попытки сдачи через эту ветку, а повторная проверка identity исключает старый state. Остальные награды идут через прежний трёхаргументный API с бонусом 0.

## Проверки и артефакты

- `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests` baseline exit 0, 32 с. Первое обращение к launcher использовало ошибочный путь build/test; с правильным `build/phantom-test/bin` RED exit 1, 0/5, seed 130 по пяти исходным дефектам.
- `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml test jar` exit 0 в трёх запусках: 60, 55 и 88 с. Последний включал проверку границ сундуков. Unit 66/66, две ожидаемые ошибки negative control проверены самим runner. Общий `ant verify` с DB targets не запускался.
- Из `dist/game` запущен `java -Dphantom.module.root=<module> -Dphantom.test.reports=<logs> -cp "../../../build/phantom-test/bin;../../../build/phantom-test/resources;../libs/*" org.l2jmobius.tests.phantoms.PhantomTestLauncher qol-quick-001 130`. Первый расширенный запуск выявил несовместимый `var` в скрипте при stock source/target 8. После явного типа итоговая проверка exit 0, 5/5, зарегистрировано 166 effect handlers.
- Для уточнения по ключам `javac -encoding UTF-8 -cp "<test-bin>;<module>/dist/libs/*" -d <test-bin> <module>/test/java/org/l2jmobius/gameserver/model/script/PhantomQolQuick001Suite.java` exit 0. Тот же focused runner с прежним `False` дал ожидаемый RED 4/5, exit 1, на условии shipped config. С `True` GREEN 5/5, exit 0. Логи `chest-red.log` и `chest-green.log` лежат в `build/phantom-test/reports/quick001-chest` текущего checkout. Повторный OwnerConfig Verify exit 0, 70/288, 0 записей. Код сервера в JAR для этого уточнения прежний.
- После усиления проверок ManaHeal и Spirit Sharing тот же `javac` exit 0, focused runner exit 0, 5/5, лог `detached-effect-green.log` в том же каталоге. Объект вне игрового мира использует штатный расчёт статов и MP без выделения world ID, БД и фоновой регенерации. Эти результаты подтверждают исполнение эффектов отдельно от расхода инвентаря, игровых пакетов и lifecycle сервитора.
- `javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -proc:none -cp "../libs/*" -sourcepath data/scripts` для EffectMasterHandler, SkillList и Q298, exit 0. GameServer.jar SHA-256 `DEE7D82C9803E6FFCB4CF4B2DA657E4122C682717DC8BBC48B4F2FBB07D78BC6`. Копии Ant/dist совпали, классы из проверяемых source путей 9/9 сравнили побайтно с `build/bin`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/OwnerConfig.ps1 -Action Verify` exit 0. Ant OwnerConfig Apply exit 0, 70 файлов, 288 overrides, 0 записей. `tools/tests/OwnerConfigChecks.ps1 -OutputRoot <owned-fixture>` exit 0, 6 сценариев Save/Apply/Verify. Три файла метаданных совпали с Git baseline. При сравнении двух checkout содержимое `OwnerProfilePreserved.properties` совпадает после нормализации CRLF/LF.
- Native GS/LS/SQL boots — 2/3/1. Порт 2106 проверялся перед каждым запуском. Первый LS стартовал в GUI и перенаправил stdout, его штатно остановили и запустили в headless-режиме. Схема с 150 таблицами установлена из штатных SQL в новом datadir, с порядком `phantom_reservations.sql` перед её индексом из checkpoint2. Файлы подготовки, логи и PID receipts находятся только в собственном `build/quick001-native`. MAIN/PLAY, исходный TEST130, клиент, Geo203, protected207, training10 и safety latch024 сохранены.
- `pwsh -NoProfile -File build/quick001-native/Probe.ps1 <revision> <suffix>` с проверкой PID/инкарнации GS запустил пакетный тест в native JVM. `native-One.log` показал ошибку HP у синтетических персонажей. После загрузки живых offline-фикстур в `native-Two.log` прошли G2/G3/G4/G6. `native-Tre.log` показал открытие при −60 со штатно отсутствующим расходом 2065. После RED focused 4/5, исправления XML, GREEN 5/5 и холодного запуска `native-Col.log` подтвердил пакеты, персистенцию, сундуки и отказ Q298. `native-Mat.log` фиксирует reuse, отказ чужого аккаунта, границы HP сундуков и повторный Q298. `native-Sum.log` фиксирует результат с сервитором. Подготовка стартовых условий производится только на свежих синтетических персонажах, измеренные результаты записывают штатные операции.
- `native-Grd.log` подтвердил защиту мёртвой цели и цели в другом instance. После последней правки `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml test` exit 0 за 46 с, unit 66/66, OwnerConfig Apply changed=0. Сравнение native GameServer.jar с этими свежими классами 9/9 и тестовых XML/INI с пятью исходными файлами 5/5 прошли побайтно.

### Native receipts

Полные логи находятся в `build/quick001-native/logs`.

| Лог | SHA-256 |
| --- | --- |
| `native-Two.log` | `6F8AF62E3842AA9A34652FAB4BAA87EA391A2162325A74506604CEEB9CF31FE2` |
| `native-Col.log` | `5A441B058DB6F77E633C734E2ACCB0E025D21695DA093532B02E63861F1B16E6` |
| `native-Mat.log` | `0E656663FF300EE6A14F25521AA4CE8B1D13065D7EECCA8C7FB5BF0C252019FA` |
| `native-Sum.log` | `D88C2D3CE02955D2C10BC2CFBDCD3B8CA86C9B63EA6E57941B838537EDAAB2F8` |
| `native-Grd.log` | `3AD3CD4372E1C0BA17BB56271132E4F28D91050427FE49A73C91C31072044E05` |
| `postflight.log` | `7AA8ADA70DCA244E4F5A9973BABBA67573EAB6E9351E180FA06718E9419BFB3E` |

Первый цикл native завершился штатно с остановкой GS 17448, LS 11516 и MariaDB 20060. База TEST и персонаж `TqVisualTre` сохранены для игрового клиента. Цена Mana Potion проверена через штатный multisell. Отдельная покупка, игровой сценарий с подклассом и отдельная party-сцена Q298 не проводились. Для сундуков использован штатный расчёт добычи. Снят только порог открытия для сундуков ниже уровня персонажа.

### Игровой клиент и финальный postflight

- 20 ноября 2026 в 17:07 по времени Кишинёва владелец вошёл на локальный Bartz через LoginServer 2106. Владелец подтвердил «навык изучил, он виден и работает». После выхода у `TqVisualTre` сохранены 1321 первого уровня и известный рецепт 1. Ингредиенты были в инвентаре. Результат изготовления измерялся отдельно.
- Штатный пакет `RequestRecipeItemMakeSelf` изготовил рецепт 1 на `TqCraftTwo` с изученными у NPC 1321 и 172. Iron Ore 4→0, Coal 2→0, Wooden Arrow 0→500, MP 248→218.
- Ревью выявило допуск сохранённого 1321 для зарезервированной идентичности Phantom до подключения headless-сессии. На отдельной синтетической записи native RED exit 1 показал `ordinary=true, reserved-Phantom=true`. После проверки существующего владельца идентичности native GREEN exit 0 показал `ordinary=true, reserved-Phantom=false`. Обе проверки вызывали штатный `SkillTreeData.isSkillAllowed`.
- Финальный `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml test jar` exit 0 за 67 с, unit 66/66. `PhantomTestLauncher qol-quick-001 130` exit 0, 5/5. Финальные native, Ant и dist копии GameServer.jar совпали, SHA-256 `537E7EEFB00A75D1AAE5D9EEA39101D87DEC9F4E791A0259ED57B9D23D7FC094`. Внутри JAR обновлённый `SkillTreeData.class` побайтно совпал с Ant. OwnerConfig Verify exit 0, проверено 70, переопределений 288, изменений 0.
- Суммарно GS/LS/SQL boots — 4/5/2, из них после первого postflight — 2/2/1. Финальный cold дал 0 ошибок. Свои GS 1152, LS 30480 и SQL 15012 завершили работу штатно. Порты 2106, 9014, 7777 и 13308 свободны. Рабочая MariaDB 3308 сохранила PID 7664.

| Лог | SHA-256 |
| --- | --- |
| `visual-result.log` | `332C6FFD7C55CB8309CF9C4C6402D877178ACA49D3614F3BAC1DBC3AE1560286` |
| `native-own-craft.log` | `F8A1CEBA9EA791CB994025BDA1D91038AFCC87234EB531A19B9BA413FB1BBF3B` |
| `native-craft-policy-Red.log` | `FD83DE54C357D29F4E90FFEAC15E08266E6A2A971D6F583F029C6692AEC8A228` |
| `native-craft-policy-Grn.log` | `261CF5119DDB71E3A4FFBA88EF86425C7484BB103B4B2536335B187E5BBDD960` |
| `native-Fin.log` | `9A51C1332608E269200F190335533EEDCA5C001D417EBD51AB4E04501B3E91BE` |
| `visual-postflight.log` | `6E9CAC8E546849A1C36D39ABBF3B9C540C1BEED55B81863FD4956CBAB3E48E8A` |

- Git inspection по G0/G8. `git symbolic-ref --short HEAD`, `git rev-parse HEAD HEAD^`, `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `git status --short --branch`, `git status --porcelain -uno`, `git status --short --untracked-files=all`, `git worktree list --porcelain`, `git remote get-url origin`, `git ls-remote origin refs/heads/feature/phantom-world`, `git merge-base HEAD refs/remotes/origin/feature/phantom-world`, `git ls-tree`, `git show <ref>:<TASK130 path>`, `git diff` для точных путей, `git diff --cached --name-only`, `git ls-files --others --exclude-standard`, `git -c core.whitespace=cr-at-eol diff --check`. CRLF сохранён там, где отслеживался baseline.
- Mojibake-маркеры в изменённых файлах проверены, совпадений 0.
- Escaped Cyrillic в изменённых файлах проверены, совпадений 0.

Дополнительно прочитал SkillList/Folk/AcquireSkill и `Player.restoreSkills` для легальной выдачи и восстановления навыка, `RecipeManager.requestBookOpen` для пакетов книги, ManaHeal/91003 для предела восстановления и цены, `Chest` и skill2229 для drop и расхода ключа, `QuestState.exitQuest` и `PlayerStat.addExpAndSp` для очистки и native rates, OwnerConfig и Ant для границ сборки. Сырые логи в `.phantom-local/logs/PHANTOM-QOL-QUICK-001`.

## Точный scope кандидата

- `dist/game/config/{NPC.ini,Custom/ServitorShare.ini}`.
- `dist/game/data/stats/skills/{01500-01599.xml,02000-02099.xml,custom/10000-10099.xml}`.
- `dist/game/data/scripts/handlers/{EffectMasterHandler.java,bypass/npc/SkillList.java,skill/effects/OpenChest.java,skill/effects/SpiritSharing.java}`.
- `dist/game/data/scripts/quests/Q00298_LizardmensConspiracy/Q00298_LizardmensConspiracy.java`.
- `java/org/l2jmobius/gameserver/config/{NpcConfig.java,custom/ServitorShareConfig.java}`.
- `java/org/l2jmobius/gameserver/{data/xml/SkillTreeData.java,model/actor/instance/Chest.java,model/actor/instance/Folk.java,model/script/Quest.java,network/clientpackets/RequestAcquireSkill.java}`.
- `test/java/org/l2jmobius/{tests/phantoms/PhantomTestLauncher.java,gameserver/model/script/PhantomQolQuick001Suite.java}`.
- Этот отчёт и новая сводка `docs/phantoms/reports/PHANTOM-WORLD-COORDINATION-130-R43-STATUS.md`.
