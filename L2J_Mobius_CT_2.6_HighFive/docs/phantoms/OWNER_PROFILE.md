# Текущий профиль владельца

`dist/game/config/OwnerProfile38.tsv` — редактируемый текущий профиль. Проверка сохраняет ровно 38 известных сочетаний `section/file/key_or_range`, отвергает дубликаты, посторонние пути и недопустимые значения, затем сверяет профиль с фактическими INI/XML. Выводимые SHA-256 описывают текущие файлы; менять Java-константу после обычной настройки не требуется. Исходный пакет TASK075, его `OWNER_PROFILE_38.tsv` и исторические SHA остаются неизменяемой историей.

Для изменения настройки исправьте одновременно значение в соответствующем INI/XML и колонку `value` текущего TSV. Например, `ServitorShareMAtkBonusMultiplier=3.0` требует `3.0` в обеих точках. Изменение только INI будет отклонено; одинаковый `NaN`, отрицательный множитель или значение выше 100 также будет отклонено. Servitor Share поддерживает независимые множители 0..100, Cubic — 1..1440 минут, `MerchantLinesPerStore` — 1..20. Базовые warehouse slots должны быть меньше 300; бонус `WH_LIM` и клиентскую совместимость проверяют отдельно. Остальные счётчики должны помещаться в неотрицательный Java `int`, вес — в конечный неотрицательный `double`; это не доказывает безопасность экстремальных значений в игре.

Обычная заточка сейчас ограничена кодом `EnchantScroll.withinOrdinaryLimit` значением +25. Текущий профиль допускает меньший максимум; согласованно измените предел обычных и bonus100 scrolls, обе строки диапазонов в TSV и XML. Диапазоны должны покрывать 0..65535 без пробелов/перекрытий, шанс — 0..100%, на максимуме и выше — 0%. Одного редактирования профиля для +30 недостаточно. Специальные scrolls и support items сверяются с явными ID/пределами в `OwnerProfilePreserved.properties`; случайное изменение их XML не принимается.

Для осознанной настройки сохранённых пределов измените XML и соответствующий baseline `scroll.preserved.N=ID,...` либо `support.preserved.N=ID,...`. Суффикс `N` — maxEnchant в диапазоне 1..65535 согласно XSD; 0 запрещён. Например, перенос ID из группы с пределом 6 в группу 7 требует такого же `maxEnchant="7"` в XML. Один ID не должен повторяться в нескольких группах своего типа; наборы ID и все пределы сверяются с фактическим XML. Обязательные ключи baseline — `quest.keys`, `quest.sha256`, `scroll.ordinary`, `scroll.bonus100`; другие имена, кроме двух указанных typed-групп, отвергаются. Поддержка числового формата не отменяет ограничений конкретного игрового маршрута.

Для намеренного изменения quest rates сначала проверьте реальные значения и каноническое применение множителей. `OwnerProfilePreserved.properties` содержит явный baseline `quest.keys` и `quest.sha256`; автоматического принятия новых Rates нет. Команда только читает эффективные Quest-ключи и печатает строки для ручной сверки:

```powershell
java .\java\org\l2jmobius\gameserver\config\OwnerProfileVerifier.java --quest-hash .\dist\game
```

После проверки намеренного изменения вручную обновите baseline в `OwnerProfilePreserved.properties`. Хеш вычисляется по UTF-8 строкам `key=value` с LF, ключи отсортированы ordinal, значения обрезаны по краям; комментарии и оформление INI не учитываются. Строка QUEST в TSV остаётся `PRESERVE_EXISTING_VALUES`.

Из корня High Five выполните `ant owner-profile-validate`, затем нужные focused checks затронутой механики. Обычные `compile`, `compile-tests` и `jar` включают эту проверку; copied candidate отдельно проверяется тем же CLI с путём к его `game`. После сборки JAR с актуальным verifier доступен `java -cp <GameServer.jar> org.l2jmobius.gameserver.config.OwnerProfileVerifier <gameRoot>`. Проверка ничего не записывает и не заменяет настройки.

После проверки изучите exact diff, добавьте в index только согласованные профиль/INI/XML/baseline и нужный код, создайте обычный commit и normal non-force push в `feature/phantom-world` в рамках разрешённой задачи. Database/Network, пароли, account allowlists и другие private overrides в этот профиль и commit не включаются. Исторический пакет TASK075 для текущего тюнинга не редактируется.

Пример поручения Codex: «Измени только `ServitorShareMAtkBonusMultiplier` с 2.0 на 3.0 в `dist/game/config/Custom/ServitorShare.ini` и соответствующей строке `dist/game/config/OwnerProfile38.tsv`. Остальные настройки, исторический пакет, секреты и чужие изменения сохрани. Выполни `ant owner-profile-validate` и focused проверку Servitor Share, изучи diff этой пары, сделай только её обычный commit и normal non-force push в `feature/phantom-world`».

Для ручной работы из Git root после успешных проверок; `--only` ограничивает commit указанной парой, сохраняя посторонний index:

```powershell
git diff -- L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/ServitorShare.ini L2J_Mobius_CT_2.6_HighFive/dist/game/config/OwnerProfile38.tsv
git add -- L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/ServitorShare.ini L2J_Mobius_CT_2.6_HighFive/dist/game/config/OwnerProfile38.tsv
git commit --only -m "phantom: tune owner Servitor Share magic attack" -- L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/ServitorShare.ini L2J_Mobius_CT_2.6_HighFive/dist/game/config/OwnerProfile38.tsv
git push origin feature/phantom-world
```
