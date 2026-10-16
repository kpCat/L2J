# ACCEPTANCE058 — факты, а не переименование OPEN в PASS

## Результаты этапов
| Статус | Значение и продолжение |
|---|---|
| NOT_STARTED | До этапа не дошли; не перечислять как реализованный |
| IMPLEMENTED_UNVERIFIED | Код есть, обязательный сценарий не завершён |
| PARTIAL | Перечисленная часть проверена, остальные requirements остаются открытыми |
| SERVER_VERIFIED | Все обязательные серверные факты этого этапа из DESIGN_PLAN получены на указанном semantic candidate; можно продолжать следующий |
| OWNER_QA_PENDING | Пользовательский клиент/ощущения не проверены человеком; это не запрет М3–М7 |
| BLOCKED_PLATFORM / BLOCKED_INPUT / BLOCKED_PRODUCT | Конкретный недоступный шаг и причина; независимые действия продолжаются |

Не называй all-Phantom party фактической REAL+Phantom проверкой. Тест через transport-neutral тот же native service подтверждает server integration, а не сетевой UI/вход. В конце матрица отдельно хранит implementation/server/owner статусы. Старый M1=OPEN и deferred risks не переписываются; текущий forward waiver не гарантирует сохранность при аварии.

## Минимальные проверяемые результаты
| ID | Требуется, прежде чем заявлять SERVER_VERIFIED |
|---|---|
| S2-INVITE | Два направления canonical invite/response, доставленный ответ чата, реальные Party members; refuse/expiry/replay/full/foreign sender без побочных membership |
| S2-PLAY | TEST partner + разные Phantom роли: follow по геодате, assist двух последовательных целей, настоящие damage/reward, interruption/resume без competing FARM |
| S2-SUPPORT | Допустимые heal/buff/recharge/res с actual эффектом и расходом; negative wrong target/dead/instance/reuse/MP; никакого лечения изменением HP из тестового helper во время измерения |
| S2-LIFE | leave/leader change/logout/rejoin callbacks, отмена старого control, один normal persistence/restart с разрешённым useful continuation |
| S3-SOCIAL | RU corpus с отрицанием/адресатом/вариантами + native deliveries + память конкретного собеседника после restart + bounded chatter без циклов и ложных обещаний |
| S4-PVP | Защита/фокус/отступление, свои-чужие/флаги/карма по native правилам; party/clan authority и safe-zone negatives; только свои тестовые actors |
| S5-RAID | Реальный поддержанный encounter: сбор/роли/агро/поддержка/regroup/смерть, actual результат и stock reward без дублей; coverage и неподдержанное явно |
| S6-INSTANCE | Поддержанные Rift и Kamaloka: штатный допуск и совместный instance/выход/reuse, failed entry и identity guards, последствия death/logout/closing |
| S7-CAREER | Указанное и честно полное для claims покрытие20/40/76 + learning/subclass/noblesse; действительное изменение через native paths и сохранение; один Q401 не означает весь М7 |
| SKILLS | Новый изученный rank используется, недоступный не используется; старый не выбирается всегда; смена класса/gear/restart обновляет факты; контекст/стоимость проверены |
| DELIVERY | Проверенный descendant source + configs/data/tools/SQL; обычная сборка с инструкцией, smoke и сохранность; old057 не изменён |

## Правила сценариев
1. IDs/состав/параметры/ожидаемый результат и длительность зафиксировать ДО измерения. Если setup невалиден, сохранить INVALID_INPUT, устранить конкретное условие и начать новый episode; не вычёркивать погибшего/failed actor задним числом.
2. Для новых сценариев длительность равна времени достижения оговорённых полезных переходов плюс bounded reserve, а не обязательным380s от старого G2. Старые CONTINUITY_V2 thresholds не переписывать; просто не требовать full М1 campaign для каждого М2 патча.
3. Live evidence — actual World/native events/effect + проверенная state/durable boundary. SQL XYZ — сохранённое, не current World. `issued=true`, `DISPATCHED`, «cast начат» и «ответ сформирован» по отдельности не доказанный игровой эффект.
4. Fixture mutation разрешена ДО episode в своей058t или на явно объявленной подготовительной границе058a; тестовые MP/HP/level/item исходные условия маркировать. Никакого reward/heal/skill/Class grant во время measurement для спасения PASS. Natural claims только для natural фактов.
5. Естественная смерть/отказ/занятость не всегда bug. Проверить legality и исходный контракт. Новый независимый кандидат допускается после честно завершённого старого episode, без стирания старого результата.
6. Непроведённый G6 не тормозит058, но повреждение/дублирование от обычной новой операции — настоящий блокер затронутого этапа. Сначала исправить или исключить незавершённую функцию из shipped candidate с явной отметкой.

## Финальная проверка и поставка
- Выделить один final semantic candidate; каждое изменённое общее ядро имеет affected coverage. Reuse только при доказанной неприменимости изменений, не просто по старой зелёной цифре95.
- Выполнить выбранный current full regression set один раз в финале, сохранить фактический count/command/exit. При ошибке сначала failed target; повтор целого набора нужен только при обоснованном изменении общего ядра.
- Все исходники/helpers/данные проверенных функций tracked, committed и опубликованы обычным push. Final runtime собирается из clean committed input; не вытаскивать product classes из старых private JAR.
- `D:\PhantomWorld\M2-M7-058-candidate`: новый config/database, START/STATUS/STOP, `START_HERE_RU.md`, `BUILD_INFO`, `KNOWN_ISSUES`, список реально дошедших М-этапов и 5–10-минутная ручная проверка party/chat. Указать точные имена подготовленных TEST партнёров, уровни/роли и где их найти; не посылать владельца угадывать между гремлинами.
- Start→Status→нормальный Stop с подтверждённым save/absence→sameDB Start/короткое наблюдение→Stop, либо совместить с последним проверенным integration restart без потери фактов. Новый кандидат оставить STOPPED; старую пользовательскую пару не трогать.
- Runtime ZIP без секретов/БД/raw НЕ равен review ZIP. Обязателен после действительно завершённого М7 по RELEASE_CONTRACT; раньше готовый каталог достаточен, дополнительный ZIP не должен съедать время разработки.
- Summary: фактический elapsed/что сделал/что можно проверить/что отложено/одна следующая незавершённая зависимость. Нельзя написать «М2–М7 закончены», когда покрыта только S2-INVITE.

## Stop conditions
Безопасно заканчивать при исчерпании budget/reserve, всех завершённых разрешённых целях, внешнем stop/лимите платформы или отсутствии безопасной полезной работы после конкретного разбора. Один RED, один unavailable candidate, необходимость человеческого видео, прежний G4/G6 или первое опубликованное исправление — не самостоятельные причины завершить всю кампанию.
