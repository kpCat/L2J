# ACCEPTANCE068 — результаты по возможностям

`CORE068=PASS` только при A–E, SAFE и DELIVERY PASS. `FULL068=PASS` требует дополнительно F/G PASS. Иначе PARTIAL/BLOCKED/FAILED с отдельным итогом по каждому блоку. NOT_RUN/NOT_OBSERVED не превращать в PASS. Клиентская визуальная QA — DEFERRED_BY_OWNER, не просьба владельцу войти. Обязательность блока не означает бесконечные попытки.

| Gate | Что должно существовать и чем подтверждается |
|---|---|
| SAFE | Live HEAD/ancestry, чужой dirt сохранён, source/build/own schemas/PID привязаны, protected inputs не изменились, no duplicates/secrets |
| A | Один автономный causal заработок→native покупка подходящих сосок→заряд/следующий бой; законный Spoil/Sweep и дальнейший хозяйственный маршрут на посильной цели; отсутствие0-funds supply loop |
| B | Реальная сохраняемая Background-партия с inputs/time/costs/result, retry/World handoff без двойного начисления; bounded bootstrap не повторяется при restart/role change |
| C | 200 **реальных непустых** offline stores из настроенного мира, не имитация счётчика; native BUY/SELL с изменением реальных inventory/Adena, отдельный battle budget, нулевые combat/decision jobs у parked merchants; sameDB восстановление без дублей |
| D | Native self-craft и платный MANUFACTURE с рецептом/расходами; реальные экипировочные решения разных archetypes, min/mid/high tier coverage по данным, как минимум по одной native замене mid/high-grade; имущество получено из разрешённого источника |
| E | Working CLI Plan/Apply/Rollback, preimage conflict/backup проверены, Alt+B актуально использует применённую цену, shared valuation для рынка, x10/x20 analysis без blanket multiplier; supported arbitrage loops и unknown cases явно разделены |
| F | NORMAL/RED/BLUE mutual exclusivity, independent final frequencies, old red-only совместим; отдельные HP/rewards/spoil/config, spawn/respawn и native packet aura evidence за короткую сцену |
| G | Personal access0 solo Rift enter/fee/jump/exit, ordinary Party2 unchanged; terminal/death/restart/logout/room/timer owner races безопасны; nonallowed/Phantom отказ; без global min-party bypass |
| DELIVERY | Финальный committed-source JAR + targeted tests + обычный Main и sameDB/sameJAR restart; самостоятельный кандидат, все собственные процессы/порты/writers остановлены; normal push verified либо честный отдельный PUBLISH_BLOCKED |

## Что не засчитывается

- Изменение `maxStores`/генерация XML без200 существующих, доступных native торговцев.
- Присутствие типа MANUFACTURE в enum без оплаченной сделки и native результата.
- Фиксированная экипировка одного fixture вместо подключённого production evaluator.
- Первичная подаренная Adena, объявленная заработанной; сгенерированный S80 объявленный настоящим craft.
- Unit-проверка policy как доказательство native входа/подбора/торговли; bare startup двух JVM как доказательство всех игровых функций.
- Старые PASS на другом code path, замолчанный invalid fixture или «прошлоN тестов» без соответствующей функции.
- Объявление арбитража устранённым во всём H5 по двум примерам, идеального баланса без игры, клиентской ауры увиденной без клиента.

## Общая сцена и масштабы

Подходящий самостоятельный farmer/spoiler добывает, разгружает inventory, снабжается; merchant покупает/продаёт существующие вещи, crafter предоставляет native услугу; source/transfer/consumption ledger согласован. Не обязательно одна профессия умеет всё или все NPC стоят у поля. Один normal restart сохраняет идентичности, остатки, открытые предложения и issued budgets, после него проходит следующая сделка/полезное действие.

200 лавок — целевой продуктовый размер этой кампании, не утверждение о возможности бесконечных Player. Нельзя объявить его PASS по10 торговцам. Если реальное измерение обнаружило потолок, C=PARTIAL, привести достигнутое число, ограничение и минимальную причину; не уронить сервер ради цифры.

## Артефакты и конечный checklist

- `docs/phantoms/reports/PHANTOM-LIVING-ECONOMY-068.md`: таблица A–G, что пользователь получает, исходная причина, доказательства/ограничения, sourceSHA/JAR, реальные пути, preservation, STOP.
- В task-папке: `PROGRESS068.md`, `ACCEPTANCE068.tsv` (`gate,status,code_sha,evidence,scope,remaining`), краткий `HANDOFF068.md`; список changed files можно разделом отчёта. Один final report, не RESULT_FINAL_2_3.
- В candidate: START/STATUS/STOP, `START_HERE_068_RU.md`, manifests; понятная таблица ключей, что включено, какой нужен restart. Price CLI + пример плана/профиля и backup/rollback инструкции.
- Обновить `PHANTOM_CURRENT_STATUS.md` и актуальный продуктовый roadmap. Исторический v5 сохраняется; новые данные не меняют прошлые PASS/PARTIAL. В актуальном roadmap добавить одну таблицу «готово / осталось / сознательно не делали», с реальными критериями завершения без предсказания числа будущих задач.

Обязательный перенос остатка: незакрытые A/G (если останутся); групповые роли/raid participation с владельцем, индивидуальные PvP/PK и последствия, прочие поддержанные instances и M7 routes; большие имена/кириллица; Semantic Pack+Studio+runtime context; длительная нагрузка и final интеграция. Не вписывать туда уже выполненные067 Cubic/fishing/Henna/augmentation заново. Новую задачу/автоматизацию самовольно не запускать.
