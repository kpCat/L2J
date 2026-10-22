# TEST_CASES068 — короткие доказательства общих механизмов

## Пирамида и время

Metadata/policy/retry/timer cases — быстрые параметризованные тесты. Native — по одному представителю отличающейся механики. Ordinary Main — одна составная жизнь и sameDB restart. Большое число item/recipe combinations допустимо в одном дешёвом проходе, но не тысячи отдельных JVM/NPC сцен. Активное live-наблюдение всей кампании ориентир45–75мин, предел90мин; это не обязательное время ожидания. Blue champion smoke2–5мин. Функция готова → заканчивать её сцену.

Первая неудача: точные prerequisites/trace. Вторая с той же причиной: менять гипотезу или применять более дешёвую проверку общей причины. Не запускать тот же episode с новыми именами. Подготовка fixture/ошибка компиляции — не product RED. На baseline RED нужен для фактически найденного дефекта, не искусственно ломать каждый уже работающий owner.

Все TEST inputs задаются **до** измерения и маркируются; production callbacks не заменяются тестовыми. Использовать canonical PopulationManager→ecology→bootstrap→catchup, а не ручной READY профиль. Skill ranks/gear/EXP/weight/MP — реальные stock согласованные. Отдельный native actor допустим для bounded компонента, но не выдаётся за ordinary Autonomous proof.

## T1. Заработать → снабдиться → продолжить

Подобрать source-backed stock mob/merchant/Geo до начала, можно одно резервное место. Чтобы не ждать сотни Adena: fixture стартует **ниже вычисленного порога покупки**, но в пределах нескольких обычных наград от него. Исходные средства отметить TEST, фактический недостающий доход обязательно native заработан. Не требовать всю сумму с0, если это не меняет проверяемое решение; отдельный0-stock/0-money fallback уже покрыт R2 и нужен только при затронутой регрессии.

Наблюдать реальный доход/pickup, изменение решения, native платёж/stock и следующий charged hit. Дешёвый partial purchase не блокируется недостижимым резервом; обязательная плата не тратится дважды. Контроль no money остаётся способным фармить. У Scavenger настоящий Spoil/Sweep, подходящая экипировка и посильная цель; источник/банк могут быть в разных зонах. Один trace смерти/непрогресса должен приводить к general retreat/replan, а не «дать ещё500 HP».

## T2. Фоновая бухгалтерия

Одна supported farm/acquisition/commerce последовательность: source inputs, elapsed budget, costs, outputs, receipt. При повторе с тем же ключом/cursor нет второй награды. Native handoff не учитывает те же секунды и предметы повторно. Disabled/offline-budget-exhausted не создаёт доход. Проверить stack/MaxAdena/negative/overflow, подготовленный unresolved mutation прекращает опасный retry. Restart и новая merchant-role не обновляют bootstrap quota. Подарок и виртуальная выработка различимы в evidence.

## T3. Offline ownership и реальные сделки

Начать с3–6 merchant actors, SELL/BUY и MANUFACTURE после D. Реальный покупатель отдаёт Adena, получает существующий item; seller получает ту же сумму минус явный fee; BID выдаёт свою зарезервированную Adena и получает вещь. Два покупателя последнего stack не получают дубликат. Failed partial transaction не переигрывается вслепую. Обычный offline control-player не изменён.

Переход active→offline снимает combat/decision/background ownership. Native store остаётся в World. Restore stock table и Phantom restore дают один Player/objectId, один listing, те же balances. Late callback старого owner не пишет. Собственный close/end-stock/STOP не удаляет чужие лавки. Normal login conflict обрабатывается как native, без второго actor.

## T4. 200 торговцев без200 боевых AI

Один ramp10→50→200, после10 уже проверить no combat ticks/threads; короткие sample30–90сек, при перегрузке остановить ramp. До/после фиксировать processCPU, heap/RSS, thread count, scheduler backlog/latency и SQL/write activity, количество native rows/World stores и роли. Не считать серверный FPS по частоте logging. Сравнить с собственной baseline обычных active actors, не чужим релизом.

Проверить геоданные/spacing/неперекрытие важных NPC дешёвым data scan; одну область осмотреть через passive observer/серверные координаты, не перемещать200 персонажей по очереди. 200 не пустых магазинов должны восстанавливаться на sameDB. Повторить одну сделку после restart. Детальная длительная память/вся карта остаются отдельной final нагрузкой, не обещать их по минуте.

## T5. Производство и улучшение

Клиент native craft делает заказ у dwarf MANUFACTURE: выученный рецепт, реальные inputs/fee/MP/chance, точные inventory deltas; не создавать гарантированный результат при штатном неуспехе. Для доказательства успешного пути выбрать stock100% рецепт, а60% success/failure — controlled RNG в focused boundary, без изменения XML. Self-craft аналогичен. Отсутствие fee/MP/materials/skill отказывается корректно.

Evaluator на полных каталогах class/grade/weapon/armor/recipes; настоящие fixture представители нескольких archetypes и по одному mid/high-grade upgrade. Подготовленный источник не объявлять farm earnings; сам exchange/craft/equip native. Проверка последнего оружия, рабочего запаса сосок, active reservations и повторного equip. Отсутствующие фактические S80 источники показать как ограничение, не выдумывать их.

## T6. Цены и CLI

На одном actual rate snapshot Plan дважды даёт одинаковые данные/hash. Для x10/x20 примерных входов показать различающиеся целевые бюджеты без изменения server rates. Проверить ordinary item, craft BOM, crystals, enchant, эпик, unknown item и non-Adena multisell. Apply только own root + backup + exact preimage; второй Apply идемпотентен/явноalready applied; conflict не затирает. Rollback возвращает исходные байты только если текущие совпадают с применённой версией.

После применения на own candidate реальный Alt+B quote/transaction использует новую цену. Для bounded поддержанных cycles assert net profit<=0 без external source/time/quota; fees/rounding/tax учтены. Lawful farming/crafting value с расходом ресурсов не запрещать просто потому, что есть прибыль. Unknown graph edges перечислить и не рекламировать абсолютную защиту.

## T7. Чемпионы

Policy выбор на0/100 и границах интервалов, исключения boss/minion/quest/instance, сумма вероятностей>100, только red старый конфиг. Native поNORMAL/RED/BLUE: реальные HP/rewards/spoil policy и packet team/aura; respawn reset. Test frequencies выставляются до сцены и потом восстанавливаются, live values не менять. Не нужен живой клиент или тысячи respawn. Client rendering DEFERRED, не PASS со скриншотом которого нет.

## T8. Rift

Exact allowlisted access0 solo native enter/fee/одна смена комнаты/exit и normal Party2 control. Negative nonallowlisted/Phantom/wrong-level/missing-fee/reuse/max/неleader. Entry failure не создаёт лишнюю Party/двойное списание; nonce owner release.

Серийно воспроизвести death→restart-point, logout, double-kill/exit, pending invitation, два захвата комнаты и late timer после terminal. Fake clock/captured scheduled action допустимы для race, но используют production lifecycle. Не ждать реальные комнаты полчаса. Контроль группы не распускается исключением для solo. Ресурсы и callbacks после native cleanup не остаются.

## T9. Финальная интеграция

Один exact committed-source JAR+datapack, собственные test/report files той же ревизии. Ordinary Main с passive observer/syntheticOFF и включённым agreed economy profile: native полезный farm/supply, populated market, по возможности manufacturer. Один STOP/START того же JAR/DB, затем native полезное действие/сделка, сохранённые stock/денежные/стартерные значения. Нет ручных правок balances во время этого доказательства.

067 functions требуют лишь затронутых smoke/regression при правке общих Player/stats/instances, не повторной полной QA. Отдельный codeSHA и потомок reportSHA допустимы; не пересобирать JAR из-за каждой строки отчёта. Финальный STOP exact own PID/ports/writers, отчёт и публикация.
