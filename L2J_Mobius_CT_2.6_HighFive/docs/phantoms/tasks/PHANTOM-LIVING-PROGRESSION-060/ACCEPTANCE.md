# ACCEPTANCE060 — наблюдаемые результаты, не тестовые декорации

Для каждого case хранить `implementation`, `serverEvidence`, `ownerQA` отдельно. Итоги: NOT_STARTED, IMPLEMENTED_UNVERIFIED, PARTIAL, SERVER_VERIFIED; OWNER_QA_PENDING разрешает продолжение. Исторические М2/М3 и deferred М1 не являются автоматическим текущим PASS. Все ниже — расширение конкретных пользовательских функций, а не скрытое закрытие полных М8–М10.

## Главный приоритет: production-driven life
| ID | Положительный факт | Что не является успехом |
|---|---|---|
| LIFE-DECIDE | Native earned/low-stock/level/риск событие вызывает самостоятельную goal choice у existing producer | Тест вручную назначил skill.learn/commerce/farm по сценарию |
| LIFE-CHAIN-A | Один actor сам проходит farm→обучение/снабжение→новый полезный farm, с настоящими затратами и прогрессом | Только путь до NPC; статус готовности без результата |
| LIFE-CHAIN-B | Другой класс/регион либо иной source availability: общая policy работает без product ID/coordinate hardcode | Тот же сценарий с переименованным fixture |
| LIFE-RELOCATE | При доказанно неподходящем/пустом/опасном spot выбирает другой допустимый, прибывает и работает; нет метания | Каждый отказ вызывает TP или бот ждёт бесконечно |
| LIFE-GK | Product chooses native GK leg, выполняет interaction/fee/переход/следующий WALK и полезное действие | Прямой test setXYZ/teleToLocation без native запроса |
| LIFE-RESUME | Обычный save/restart сохраняет earned и нужное intent; продолжение без дубликата платы/обучения | Заново созданная БД, повторно выставленный тестом goal |

## Supplies и рыночные операции
| ID | Положительный факт | Обязательный negative/control |
|---|---|---|
| SUP-NPC | Сам замечает depletion, покупает совместимые доступные shots/arrows/feed у NPC, не перегружен, использует | Wrong grade/нет денег/изменённая цена/нет offer/weight ограничение |
| SUP-DISPOSE | Разгружает разрешённый surplus через штатную продажу и продолжает движение | Quest/gear/control/reserved item остаётся; нет double refund/price bypass |
| SUP-OFFLINE-BUY | Реальный detached private seller, customer arrival/buy, обе стороны conserved, bought stock расходуется | Sold out/stale offer/другой instance/двойная покупка/закрытая лавка |
| SUP-OFFLINE-CRAFT | MANUFACTURE offline гном, actual recipe/fee/materials/crafter MP/chance→выдача/использование | Нет recipe/материалов/MP/торговец busy; callback/retry не дублирует |
| SUP-ALTERNATIVE | При недоступном NPC/рынке выбирает другую законную supply/farm цель и не банкротится | Нет бесконечного retry/выдачи ресурсов из воздуха |

## Карьера и связанные групповые функции
| ID | Положительный факт | Граница полноты |
|---|---|---|
| CAREER-LEARN | Producer-created journey, current skills/плата/return/effect; main/subclass не смешаны | Immediate-SP и explicit walk059 reuse, не переписать |
| CAREER-CLASS | Source-backed native quest/transfer20 затем40/76 по фактическому поддержанному route | По каждому class/quest scope; near-level fixture не естественная полная1→80 карьера |
| CAREER-SUB-NOBLE | Native eligibility/quests/cost/switch/reload, новые decisions/skills | AutoNoblesse, готовые marks, прямой classId не проход квеста |
| GROUP-THREAT | Танк удерживает boss/опасных adds, уязвимые отходят к помощи, DD разбирают threats и возобновляют бой | Не force-human-target; no infinite kite; проверенные area recipients/native limits |
| PET-HEAL | Собственный native Buffalo-вариант с реальным learned heal помогает допустимому владельцу | Level/food/MP/range/owner/death; без нового параллельного heal timer |
| PET-MANA | Собственная native Kookaburra-вариант с реальным recharge восполняет MP | Depletion/recipient/reuse/смена owner; никакой всепартийной выдуманной поддержки |
| PET-LIFE | Summon/control item/TP или корректный отказ/unSummon/reload без утечки и второй копии | Ordinary human pets сохранены; fixture level не earned evolution |
| P4-FLOW | Native defense→risk decision→действительный retreat→regroup/end | Исходные ally/peace/clan/war/instance/stale/death/save требования |
| R5-FULL | Законный selected supported boss: readiness→gather→fight→native death/reward→regroup | Старый25199 FAIL сохраняется; общий исход не подменить death одного миньона |
| I6-RIFT | Scoped consent→PRODUCT entry/fees→room fight/reward→native lifecycle/exit | Missing/expired/price/roster changed consent; no repeated fee/reward; death/logout/timer |
| KEEP | M2/M3/learning/Kamaloka058/059 сохраняются на актуальном коде | Новые tests не заменяют старые обязательные IDs молча |
| DELIVERY | Обычный coherent060 candidate и useful save/restart, STOPPED | Live setup отдельно от saved XYZ, проверенные порты и protected057–059 |

## Правила испытаний
1. Перед сценой фиксировать input, участников/pet/template/class/skills/stock/price/вес/границы, цель, expected native outcome, код/JAR и срок. Проверить фактическую native mobility и survival readiness, а не имя класса. После изменения input новая независимая сцена, не переписанный старый FAIL.
2. Тест может один раз подготовить near-level персонажа/конечный кошелёк/торговцев/пета/внешнее событие. Затем autonomous actor принимает нужные решения сам. TEST human может давать записанный consent и ordinary помощь; не играет вместо всех ботов.
3. Любой test healing/подстановка level/resources/teleport во время измерения делает соответствующий natural claim недействительным. Разрешены обычные spells/potions/shops/TP/рецепты самой игры и их стоимость.
4. Readiness/CAST/ACK/DB coordinate — не effect/World arrival/completed payment. Для группы наблюдать recipients/aggro/resources/death/награду. Успех другой сцены не исправляет старый failing scene.
5. Production выбирает affordable supported alternatives. Это не ослабление закона игры. Нельзя повысить цены/деньги/HP/MP или выключить curse/weight/reuse ради успеха. Фактические RNG-исходы не требуют гарантировать предмет.
6. Жизненный цикл нельзя проверить только географически близким NPC: минимум один другой farm point с реально полезной деятельностью и один платный/штатно бесплатный GK переход; всё через те же product owners. Не нужно прогонять все локации мира за060.
7. Новые handlers/data upgrades сохраняют accepted context IDs/receipts/память. Известная version migration с preimage/CAS и старым parser, неизвестная — отказ. Case провала новой версии не чинится wipe всех conversation components.
8. Для более дорогих native flows reuse подтверждённой семантики допустим с аргументом по owner/data/JAR; итоговое число должно разделять свежие/reused/непокрытые. Не изображать110/110 полной М7. Обнаруженная общая race/сохранность — реальный blocker зависимого flow.

## Граница успеха кампании
Основная ожидаемая ценность — LIFE-DECIDE/CHAIN/RELOCATE/GK/RESUME и SUP каналы, далее завершённые career/party/Pet/instance действия по плану. Это не основание остановиться сразу после первого chain, пока есть бюджет. Если всё не помещается — оставить runnable интегрированные шаги, остальное честно PARTIAL, а не выкинуть критерии.

SERVER_VERIFIED М4/М5/М6/М7 требует полного исходного S4–S7 и новых связанных обязательств, а не одного CASE_PASS. Поддержанные классы/квесты/encounters/петы указывать явно. Массовая offline экономика и performance scale остаются будущими этапами. Непроведённый пользовательский вход не заменяется TEST observer и не блокирует серверную разработку.

## Формат итоговой краткой проверки пользователем
В инструкции — где находится новый candidate, START/STATUS/STOP, настоящий Login port, куда смотреть пароль (не публичный пароль), имена/роли/петы, последнее подтверждение World presence и причина/условие появления, команды пати. Одна понятная цепочка наблюдения: фарм→смена цели/магазин/тренер/TP→дальнейшая жизнь. Non-GM TestAdmin не предлагать //admin.
