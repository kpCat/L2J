# Premium и runes: причинный аудит 070

Исходные решения: PRODUCT_DECISIONS.md и PREMIUM_RUNES.md. JDK25, High Five; клиент и защищённые runtime не изменяются.

## Что найдено

- HomeBoard до070 умножал int-цену на срок, проверял баланс отдельно от списания и игнорировал результат `destroyItemByItemId`. Premium выдавался отдельной SQL-операцией. Нативное списание и entitlement могли разойтись.
- PremiumManager до070 считал продление из cache, не блокировал account row, подавлял SQL-ошибку и всё равно обновлял cache/flag. Одновременные продления могли потерять оплаченный срок. Обновлялся только первый online character.
- `account_premium.enddate` — абсолютное время Unix epoch в миллисекундах, а не время работы JAR. Native login читает account row; новый срок равен `max(now, persistedExpiry) + paidDays * 86400000` с проверкой переполнения.
- NpcTemplate имел отдельные premium ветки grouped/ungrouped loot; raid generic bonus пропускался. Старые premium per-ID значения1 для эпиков блокировали generic chance2. Старые chance57=2 и amount57=2 давали составной x4 до обычного ограничения вероятности.
- Attackable выбирает native reward actor: лидер наиболее результативной party либо основной damage dealer. Именно этот actor передаётся NpcTemplate. Premium участника другой роли не повышает rate непремиального выбранного владельца. Party distribution и ground-loot ownership сохраняются.

## Изменённый контракт

`PremiumManager.extendPremium(Connection, accountName, days, now)` принимает только явную транзакцию (`autoCommit=false`), дни1/3/7/15/20/30 и блокирует account row через `SELECT ... FOR UPDATE`. Метод не публикует benefit. Вызывающий код обязан поместить native Adena debit, receipt и extension в эту же транзакцию и вызвать `publishPremiumExpiration(account, expiry)` только после commit. Последний обновляет все присутствующие normal characters аккаунта; headless/native-managed actors не получают premium автоматически. На рестарте источник — account row.

`PremiumLootPolicy` объединяет premium и native bonus chance с пределом3 до server/champion rates. Per-ID0 сохраняется; исходные server per-ID0 также остаются нулём. Herb generic bonus по-прежнему отсутствует; явное per-ID правило действует. Grouped/ungrouped raid rare теперь используют тот же premium selector, что ordinary loot. Quest rewards и Olympiad tokens этим путём не изменяются. PlayerStat не изменён.

Профиль OWN070 должен задавать XP/SP2, drop/spoil chance2/amount1, item57 chance1/amount2; rare per-ID chance2/amount1 либо наследование generic. `EnablePremiumSystem`, `CommunityPremiumSystem`, `CustomCommunityBoard` включаются только assembler-профилем OWN070. Исходные глобальные флаги не включались. Purchase quote должен поступать из явно применённого pinned `premium.day`, без fallback на фиксированную цену.

## Rune result

`RUNE_AUDIT070.tsv` содержит все41 item-template типов RUNE/RUNE_SELECT: exact ID/name/source/icon/action/time/skill/effect/lifecycle. Ни у одной нет `bonusDrop*` или `bonusSpoil*`: **NO_NATIVE_DROP_SPOIL_RUNE_ITEM**. В текущем merchant нет Rune control. Skill8415 `Ring Ability - Drop Rate Up` действительно добавляет bonusDropRate10–50%; это skill способности кольца, а не обнаруженная timed rune. Skill8416 имеет название, но не содержит эффекта.

Реальные XP/SP/Crystal/Feather/Eva runes не переименованы в drop/spoil. Passive path `Item.hasPassiveSkills` принимает RUNE в inventory, но не RUNE_SELECT; default action отсутствующих значений — NONE. Время item хранится как epoch-ms и восстанавливается из items, `ItemLifeTimeTaskManager` удаляет истёкший item. Для720h templates старое выражение `time * 60 * 1000` использует int и требует отдельного исправления до продажи таких длительных предметов. Stock skill identity используется native при add/remove; переносы одинаковых runes могут требовать отдельного stack-аудита. Ни одна rune не добавлена в продажу070, поэтому эти старые риски не активированы новой витриной. Клиентская локализация и визуальный render не проверялись.

## Проверка и границы доказательства

Добавлен `PhantomPremium070Checks`: policy contracts и actual NpcTemplate grouped/ungrouped/spoil execution на Monster/RaidBoss/GrandBoss с normal actor из exact OWN070 lane. Проверяются amount1, Adena net2, source suppression21747=0 и effective profile; изолированная таблица100% проверяет границу количества без выдачи реального boss loot. Source Queen Ant — group30% × item100%, premium factor2 даёт60%; реальная смерть Queen Ant ради RNG-доказательства не подменяется fixture.

На момент этого аудита compile/native purchase/same-account/restart ещё **NOT_RUN**; итоговые результаты обязан зафиксировать общий отчёт070 после единой сборки и собственного TEST070. Требуется подтверждение transactional engine для account_premium/items/receipt tables. Отдельные Ant/DB/server запуски в premium lane не выполнялись.
