# ROOT_CAUSES030 — уровни уверенности

## Установлено опубликованным кодом / отчётами
1. Final029 обе сцены дали cohort0 ДО начала наблюдения. Это не доказательство «все
   боты сломались одинаково», но и не перенос старого028 PASS на новый SHA.
2. В C6 diagnostic110/447: ecology_fenced, transaction.item_conflict_canonical;
   witness указывает item118, NON_MUTABLE_ID, HISTORICAL_FARM. Candidate652 был
   INCONSISTENT. Это зафиксировано ROOT_CAUSES029, не только устным отчётом.
3. mutateItems() запрещает delta для itemId вне mutableItemIds. Правильная защита.
   execute() получает их из expected.inventory + additionalMutableItemIds.
4. Обычные FARM/HISTORICAL_FARM command строятся без additionalMutableItemIds;
   Command constructor разрешает непустое расширение лишь acquisition. Просто передать
   deltas.keySet() нельзя ни логически, ни в текущем интерфейсе.
5. Capture и baseline хранят ограниченную проекцию предметов. Проверка старой проекции
   перед native attestation необходима; её нельзя заменить односторонней нормализацией.
6. Historical replan уже вызывает refreshCanonicalBaseline при farmProjectionChanged;
   недостаточно добавить второй такой вызов вслепую — надо проверить, какая проекция
   фактически получена перед следующим FARM и все пути смены цели.
7. Receipt collector хранит ВСЕ старые RECEIPT_OWNERS в памяти и считает уникальные
   профили за жизнь JVM. После32profiles или128lifetimes enroll() отказывает. Terminal
   scopes не освобождаются из этого реестра. Это не concurrent materialized limit8.
8. Birth hook и checkpoint observer exceptions изолированы существующими catch guards.
   Следовательно, capacity сама по себе НЕ доказанная причина cohort0. Она портит proof;
   воздействие по времени/блокировкам требует отдельного измерения.
9. Профиль260 действительно сделал background TRAVEL→FARM с XP/SP/vitality и persisted
   restart, но итог DEAD. Native pre-farm и whole-return этого же эпизода не доказаны.

## Не установлено — обязательна проверка, не утверждать как факт
U01 exact admission blocker КАЖДОГО finalC7 profile. C6 item118 не доказывает весь C7.
U02 первый producer несовместимого footprint: goal switch, spoil capability, stale capture,
    authority generation или иной путь. Нужен composed native/background RED и immutable witness.
U03 никакое изменение guard пока не обосновано для подлинного canonical inventory drift.
U04 старые route11/shutdown028 UNKNOWN; текущие проходящие регрессии не объясняют прошлое.
U05 отсутствующие old872/1272/early252 witnesses не восстанавливаются из late SQL.

## Архитектурное решение
Не расширять случайный allowlist после выпадения предмета. До RNG/model определяется
полный законный footprint выбранного плана. При необходимости атомарно меняется только
проекция canonical-инвентаря; сами предметы/EXP/виталити не изменяются. Затем новый
ordinary batch читает эту committed проекцию. Защита mutateItems остаётся.
Collector ограничивает активные/ещё не выгруженные данные, а не суммарное число персонажей
за жизнь JVM. Все закрытые epochs остаются в append-only evidence, без удержания Player.
