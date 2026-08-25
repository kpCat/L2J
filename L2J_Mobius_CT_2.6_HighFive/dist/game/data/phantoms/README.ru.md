# Данные Phantom World

Каталог `population/high-five-population-v1.xml` — единственный authoritative источник политики имён, мягкой карьерной экологии, стартовых классов и недельных расписаний населения. Он читается строго и входит в durable authority через SHA-256 всего файла.

## Стили имён

Никнеймы создаются детерминированно из synthetic-корпуса без внешних сервисов и runtime I/O. Допустимы только ASCII-символы `[A-Za-z0-9]`, длина не превышает 16 символов. Зарезервированные служебные токены отбрасываются без учёта регистра.

Текущие семейства и веса:

- `CLEAN` — 35: короткое обычное имя или мягкое соединение корней;
- `COMPOUND` — 25: читаемая комбинация двух корней;
- `TRANSLIT_SLANG` — 18: транслит и игровой сленг эпохи 2007–2011;
- `DECORATED` — 10: ограниченные рамки наподобие `xX...Xx`;
- `DIGITS` — 8: компактные цифры без универсального hash/base36-хвоста;
- `LEET` — 4: ограниченная замена букв цифрами.

При коллизии первые попытки меняют стиль, структуру и корни. Цифры и leet используются только на поздних попытках. Всего разрешено не более восьми collision retries после исходного кандидата.

## Карьерная экология

`CareerArchetype` — намерение будущей карьеры при создании, а не готовая профессия и не party quota. Архетип детерминированно выводится из population seed, creation ordinal и текущего каталога; отдельное поле в persisted state не требуется.

Целевые веса: `DAMAGE=55`, `TANK=8`, `HEALER=8`, `ENHANCEMENT=12`, `CONTROL=7`, `ECONOMY=10`. Затем каталог выбирает совместимый canonical level-zero class. `ECONOMY` сохраняет Dwarven spoil/craft lineage. Профессии, class transfer и квесты продолжают выполняться штатными механизмами High Five.

## Социальная человечность

Социальная модель разделяет четыре разных понятия:

- личность — устойчивые черты самого фантома, например лояльность или осторожность;
- отношения — субъективные чувства к конкретному собеседнику: доверие, уважение, страх, злость, дружба, соперничество и долг;
- воспринимаемая репутация — личная оценка надёжности, полезности, компетентности и враждебности конкретного субъекта;
- память — ограниченный набор значимых событий с важностью и сроком жизни.

Текущая репутация не является публичной славой или глобальной notoriety сервера: каждый фантом хранит собственное восприятие другого субъекта.

Transient-контекст события различает `NONE`, `SAME_CLAN`, `SAME_ALLIANCE` и `CLAN_WAR`. Поддержка внутри клана усиливает связь сильнее, чем внутри альянса; мелкий отказ или уход получает больше доверия и вредит меньше. Серьёзное предательство соклановца, наоборот, весит сильнее, потому что нарушает более близкое ожидание лояльности. Счётчики соглашений при этом не масштабируются.

Сложившаяся воспринимаемая репутация обладает инерцией: слабое свидетельство противоположного знака частично сопротивляется развороту. `reputationShockBp` задаёт силу события пробить эту инерцию; предательство имеет высокий shock, обычная поддержка и мелкое трение — низкий. Однонаправленные изменения репутации и все измерения отношений не подавляются.

`CLAN_WAR` имеет отдельную семантику: ожидаемый военный бой создаёт меньше личной злости и враждебности, но не стирает страх и соперничество. Goal030C1 определяет transient-контекст канонически по точным live `Player`/`Clan` identity: приоритет `SAME_CLAN`, затем активная война, затем `SAME_ALLIANCE`, иначе `NONE`. Resolver не выполняет DB-запросы, глобальные scans, cache refresh или фоновые задачи.

## Безопасное редактирование

1. Меняйте только authoritative XML, не создавайте параллельный каталог.
2. Сумма весов name styles и CareerArchetype должна оставаться ровно 100.
3. Сохраняйте минимальный корпус: primary 96, secondary 32, translit/slang 24; не добавляйте оскорбительные слова и служебные токены.
4. Каждая canonical starting class должна оставаться достижимой хотя бы в одном совместимом archetype.
5. Не редактируйте versioned/hashed XML или TSV только ради комментария: даже comment-only правка меняет SHA-256 authority и может намеренно закрыть загрузку durable state с другим hash.
6. После содержательной правки обновляйте только действительно затронутые hash pins и запускайте focused gates.

## Быстрые проверки Goal030A

Запускайте строго по порядку:

1. `ant phantom-population-humanization-goal030a-test`
2. `ant phantom-population-catalog-test`
3. `ant phantom-population-schedule-test`
4. `ant phantom-progression-catalog-test`
5. один финальный `ant jar`

Эти проверки DB-free, кроме самой сборки они не provision test database и не заменяют будущие Goal030 CP2/CP3 release gates.

## Быстрые проверки Goal030B

Запускайте строго по порядку:

1. `ant phantom-social-humanization-goal030b-test`
2. `ant phantom-social-events-test`
3. `ant phantom-social-modifiers-test`
4. `ant phantom-conversation-social-style-test`
5. ровно один финальный `ant jar`

Цепочка Goal030B полностью DB-free до сборки: она не запускает provisioning, aggregate social tests, Clan/PvP integration, CP1/CP2, soak, `verify` или geodata.

## Быстрые проверки Goal030C1

Используйте уже подготовленную allowlisted базу `l2jmobiush5_phantom_test` и запускайте строго по порядку:

1. `ant phantom-clan-affiliation-humanization-goal030c1-test`
2. `ant phantom-clan-social-domain-goal027c-test`
3. `ant phantom-clan-checkpoint2-goal027-test`
4. `ant phantom-pvp-warning-social-test`
5. `ant phantom-social-humanization-goal030b-test`
6. ровно один финальный `ant jar`

Первый target выполняется в forked JVM из `dist/game`, использует seed `30003031`, проверяет точные clan/alliance/war context, изгнание, native leader truth, idempotency и cleanup. Эта цепочка не provision-ит и не меняет schema, не запускает CP1/CP2, soak, `verify` или geodata. Directive social events и PK/karma recovery остаются scope Goal030C2.

## Директивы лидера клана Goal030C2A

Директивы поступают только через фактически доставленные сообщения штатного канала `CLAN`. Единственный глобальный observer остаётся у `PhantomConversationService`; он передаёт подходящую `CLIENT_CHAT`-доставку в worker-free side-channel. Сообщения `PHANTOM_GENERATED` не рассматриваются как команды.

Источник authority — только текущий `clan.getLeaderId()` той же канонической nonzero `Clan`, что у materialized recipient. Обычный участник, бывший лидер после transfer, союзник, военный противник, outsider и unresolved Player не получают полномочий. Кэш лидера, DB-запросы, сканирование World/ClanTable, отдельные потоки и таймеры не используются.

Authoritative каталог `clan/high-five-clan-directives-v1.xml` задаёт три bounded-команды:

- `ASSEMBLE`: `сбор`, `го сбор`, `сбор клана`, `все на сбор`, `онлайн на сбор`, `sbor`, `go sbor`; base score `600`, Scheduler state `ACTIVE`, TTL `120000` мс;
- `STANDBY`: `готовность`, `будьте готовы`, `держим онлайн`, `standby`; base score `250`, Scheduler state `WARM`, TTL `300000` мс;
- `DISMISS`: `отбой`, `расходимся`, `сбор окончен`, `otboy`; base score `1000`, снимает только source `clan.directive.<clanId>`, которым владеет этот directive service.

Нормализация ограничена NFKC, lower-case, `ё` → `е`, схлопыванием пробелов и краевой пунктуации. Неизвестные, составные и неоднозначные фразы игнорируются. Итоговый obedience score ограничен диапазоном `[-3000, 3000]`: `ACCEPT` при score не ниже `300`, `REFUSE` при score не выше `-300`, иначе `DEFER`.

`clan.directive.obedience` использует authoritative social weights: loyalty `+900`, trust `+700`, respect `+700`, competence `+500`, reliability `+400`, anger `-700`, rivalry `-500`, hostility `-600`. Принятие и отказ записывают `clan.directive.accepted`/`clan.directive.refused` с exact leader subject и `SAME_CLAN`; defer не создаёт social event.

### Быстрые проверки Goal030C2A

Используйте уже подготовленную allowlisted базу `l2jmobiush5_phantom_test` и запускайте строго по порядку:

1. `ant phantom-clan-directive-policy-goal030c2a-test`
2. `ant phantom-clan-directive-integration-goal030c2a-test`
3. `ant phantom-clan-affiliation-humanization-goal030c1-test`
4. `ant phantom-conversation-understanding-test`
5. `ant phantom-social-humanization-goal030b-test`
6. ровно один финальный `ant jar`

Policy target DB-free, forked, seed `30003032`, timeout 120 секунд. Integration target запускается из `dist/game`, seed `30003033`, timeout 180 секунд, использует существующие config/manifest и не выполняет provisioning. Цепочка не запускает aggregate tests, CP1/CP2, soak, `verify`, geodata или schema migration.
