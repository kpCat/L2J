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

## Безопасное редактирование

1. Меняйте только authoritative XML, не создавайте параллельный каталог.
2. Сумма весов name styles и CareerArchetype должна оставаться ровно 100.
3. Сохраняйте минимальный корпус: primary 96, secondary 32, translit/slang 24; не добавляйте оскорбительные слова и служебные токены.
4. Каждая canonical starting class должна оставаться достижимой хотя бы в одном совместимом archetype.
5. Не редактируйте versioned/hashed XML или TSV только ради комментария: даже comment-only правка меняет SHA-256 authority и может намеренно закрыть загрузку durable state с другим hash.
6. После содержательной правки обновляйте только действительно затронутые hash pins и запускайте focused gates.

## Быстрые проверки

Запускайте строго по порядку:

1. `ant phantom-population-humanization-goal030a-test`
2. `ant phantom-population-catalog-test`
3. `ant phantom-population-schedule-test`
4. `ant phantom-progression-catalog-test`
5. один финальный `ant jar`

Эти проверки DB-free, кроме самой сборки они не provision test database и не заменяют будущие Goal030 CP2/CP3 release gates.