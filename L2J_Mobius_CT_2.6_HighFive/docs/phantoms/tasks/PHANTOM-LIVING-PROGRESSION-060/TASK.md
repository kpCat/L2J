# TASK060 — связанная жизнь вместо изолированных демонстраций

## 1. Идентификатор
PHANTOM-LIVING-PROGRESSION-060. Новый диалог, прежний OWN/ветка. Исполнение inline, без субагентов.

## 2. Цель
Закончить production-driven life loop и продвигаться к М7 с необходимыми supply/transport/offline/Pet функциями. Пользователь хочет самостоятельные решения, а не фарм одной стартовой точки навсегда. Тестовая явная цель059 — переиспользуемый исполнитель, но ещё не вся автономность.

## 3. Зависимости
BASE/AUTHORITY, исходные MILESTONES038 и ACCEPTANCE058/059. Java25, Ant, Windows PowerShell5.1, существующие058t/058a, tracked libs/datapack и локальная лицензированная геодата. Не вводить runtime LLM/API, внешние paid сервисы или новые движки навигации.

## 4. Контекст059
М2/М3 server-verified; immediate-SP и explicit learning journey проверены, но автономный producer отсутствует. Kamaloka57 имеет полный positive; Rift auto-entry/consent и timer coverage открыты. М4 retreat маршрута нет; М5 25199 не побеждён, AoE-агро и поддержка требуют фактической проверки. Восемь старых orphan сохранены. Эти ограничения не требуют переигрывать всё058/059.

## 5. Read-first
Прочитать пакет, текущие локальные AGENTS/обязательный workflow, RESULT059 и SOURCE_MAP. Начальный обзор продукта <=12 связанных файлов по нужным symbols; затем расширять адресно по call chain, не читать все тысячи строк. Не начинать новую тотальную ревизию репозитория. Зафиксировать один call-path: кто создаёт цель, кто исполняет, кто пишет состояние, кто подтверждает результат.

## 6. Архитектура
Существующий producer/policy в shared Decision/population pipeline определяет потребность; existing owners исполняют допустимые действия. Общая проверяемая последовательность: observed facts → bounded choice → versioned goal/plan → native effect → durable result → новое решение. Не держать long-lived ссылки Player в новом глобальном registry. Имена/IDs тестов не в production.

## 7. Scope
Связанные High Five source/tests/data/config/SQL/tools для жизненного выбора, farm relocation, skills/class progression, shop/teleport/offline manufacture/Pet support, Party/PvP/Raid/Rift зависимостей. Родительские Player/Creature/Party правки — только по доказанному пробелу и с обычными human controls. Торговые catalog расширять проверенными native facts, не по догадке «B соски только так».

## 8. Out of scope
Новая гонка М1/G4/G6, видео/ручные игровые клики владельца, миграция/очистка старых миллионов raw, новый unbounded AI/LLM, полная сеть200–1000 торговцев, масштабирование10000, все профессии через поддельный setClassId, новые клиентские патчи, Zariche. Последние пожелания об AA/кристаллах/красках/экипировке/кусках сохраняются в общем backlog, но не превращаются в сотни обязательных кейсов060.

## 9. Требуемые изменения
DESIGN_PLAN: общий autonomous life loop прежде отдельного raid success. Поддержать безопасное переключение при выросшем уровне, невыгодном/пустом/опасном farm, новых skills, исчерпании расходников и перегрузе. Характер влияет на выбор, но не на native legality. Групповая тактика/Pets не тестовая «спасалка», а часть существующих native owners.

## 10. Конфиги
Bounded policy defaults в ordinary datapack: reserve/budget/weight, re-evaluate cooldown, текущий диапазон уровней/опасности, совместимые расходники, предпочтения party/pets. Не hardcode «с этой точки на эту через10мин». Текущий accepted cap/ретеншн не менять ради G4. Новые типы data/goal должны читать поддержанные прошлые версии без потери памяти/receipts; неизвестная версия отвергается. Не заменять все hashes вручную.

## 11. Производительность
Никакого thread/SQL poll на бота или глобального поиска всех магазинов/мобов каждый tick. Использовать имеющиеся индексы/каталоги, bounded queries и события инвалидации. Кэш возможностей пересчитывается на level/class/skill/gear/pet changes. Stock Pet scheduling допустим как существующий native механизм, второй таймер лечения в Phantom поверх него не вводить.

## 12. Ownership/lifecycle
Один исполнитель текущего действия. PARTY/HOLD/combat прерывают новые FARM/учебные/магазинные roots, но не теряют earned callbacks. Human лидер не управляется ботом. Shop/Recipe/Pet/teleport операции имеют actor identity, current generation, instance и terminal result. На leave/death/restart/remove pet/control item старый план не оживает у другого владельца. Питомец не дублируется при materialization/restart.

## 13. Транзакции
Сохранять059 witnessed-SP/CAS и native item/recipe/private-store authority. Проверять обе стороны сделки, списание fees/ингредиентов/MP, chance, выдачу и идемпотентность. Terminal UNCERTAIN сначала сверяется, не повторяет покупку. SQL XYZ не current World. Native price/tax/stocks могут измениться — requote и bounded replan, не копия старой цены.

## 14. Тесты
На конкретный дефект RED→минимальное исправление→GREEN; работающую функцию сначала проверить, не выдумывать RED. На коротком test fixture разрешено подготовить input, но внутри autonomous acceptance нельзя вручную назначать последовательные цели, выдавать деньги/предметы или телепортировать. В каждой сцене есть фактический native effect; старые FALSE/FAIL хранятся отдельно. Высокоуровневый fixture не доказывает прокачку1→80.

## 15. Сборки и команды
Реальные targets/seeds уточнить в текущем Launcher/build.xml. Не запускать тест после failed compile, не принимать старые test class за свежий RED. Кодировка stdout/stderr UTF-8; WinPS5 stderr сохранять, исход определять по exit code. Output наD, в чат кратко. Во время исправлений focused набор, current affected native по изменившемуся owner; в финале coherent clean committed input/ordinary JAR и достаточная накопительная регрессия.

## 16. Приёмка
ACCEPTANCE разделяет lifecycle chains и полные milestones. М4–М7 не объявлять SERVER_VERIFIED лишь по нескольким положительным actions. Обязательные criteria058/059 сохраняются, новые060 зависимости добавляются явно. M1_OPEN/OWNER_QA_PENDING не препятствуют следующему шагу. Законченная Камалока одного template не закрывает Rift; один class path не все профессии.

## 17. Checkpoint и отчёт
Один `.phantom-local/contract058a/task060/CHECKPOINT060.md`: start/deadline, current code/JAR/lane/PID, done/fail, следующая гипотеза и команда, remaining/reserve, own dirty paths. Обновлять каждые30–45мин, до long operation/compaction и после commit. Одна ACCEPTANCE060.tsv: case/класс/локация/источник цели/фактический результат/input/code/JAR/status. Tracked RESULT<=180 строк с direct useful facts и оставшимися product gaps. Raw не превращать в тысячи маленьких файлов или review ZIP.

## 18. Публикация/поставка
Exact commits+normal push после содержательных проверенных шагов. Runtime-функции в tracked product, не только .phantom-local. Новый060 candidate — cumulative из accepted descendant с configs/data/SQL/libs/tools/START_HERE_RU/KNOWN_ISSUES/ACCEPTANCE_STATUS. Перед финальным claim — clean build и current regressions; источник кода и hash файла честно. Секреты только локально. Нужные параметры настройки и миграции описать, пользователь должен запускать без Codex.

## 19. Как не повторить гремлинов/рейд25199
- Прошлый known FAIL сначала читать, не воспроизводить часами то же самое. Проверка preconditions до сцены включает native class/learned skills/curse от boss И minions, доступные ресурсы, вес/скорость, воду/этаж/маршрут, планируемый расход, pets и текущие role targets.
- Два однотипных полных FAIL на одном encounter без локализованной новой причины — прекратить полные прогоны. Следующая правка сперва короткий причинный тест; возвращение к этому encounter только с обоснованной изменившейся гипотезой/входом и наличием времени. После очередного одинакового исхода выбрать другой предусмотренный supported scenario, а не наращивать бафы случайно.
- Для положительного supported encounter разрешено выбрать ДРУГОГО босса/template/город до enrollment по явным data/level/route/cost правилам. Старый25199/Giran FAIL остаётся и не становится PASS от успеха другой сцены. Нельзя после победы объявлять суженный охват универсальным.
- Первые60–90с боя: net DPS/реген, incoming damage по ролям, реально завершённые heals/MP/aggro. Заведомо неосуществимую сцену штатно закончить, не ждать45мин по привычке. Находить причину: input, planning, native execution или survival, а не один «FAIL».
- При ресурсном дефиците жизненный producer выбирает другое законное занятие/снабжение/помощь, а не вечный retry. При no-path пробует другой разрешённый NPC/коридор/GK route по ограниченному набору, не меняет геодату. При transient cooldown ждёт законный due.
- Конкретный blocker одного компонента не блокирует независимые части. Не спрашивать владельца о прямо разрешённых fixtures/builders/переходах. Границу непредоставленных прав не обходить; сохранять и продолжать другое.
