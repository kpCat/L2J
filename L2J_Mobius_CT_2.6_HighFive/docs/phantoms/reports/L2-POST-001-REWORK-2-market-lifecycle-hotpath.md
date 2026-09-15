# L2-POST-001 REWORK-2 — lifecycle fence и hot-path store reconciliation

Статус: **SUCCESS**. Ветка `feature/phantom-world`, точный parent
`fb0f3d2e2001b7d0b949bfbdc0b0b6eed79f5aee`. Работа ограничена High Five.

## Результат и архитектурное решение

`PhantomAutonomousMarketProducer.beginStop()` идемпотентно устанавливает
stopping fence под тем же монитором, что и `pulse/consider`. Поэтому метод
дожидается уже выполняющегося producer pulse; после его возврата ни scheduler
`onPulse`, ни прямой `consider`, ни вложенные publish/restore пути не создают
магазин. `PhantomSystem.shutdown()` ставит fence до обеих веток
`PhantomStoreService.shutdown()`, включая повтор после startup/shutdown failure.
StoreService остаётся владельцем закрытия native listing, удаления durable plan
и снятия Decision owner observer; порядок остальных subsystems не менялся.

`_opened` теперь держит immutable quoted `PhantomStorePlan` и goal identity.
Обычный pulse сверяет его expiry/цену/предмет с живым `Player`, оставшимся
native `TradeList`, in-memory StoreService observer и Decision snapshot.
Частичная native SELL/BUY сделка уменьшает native count, не заменяя исходную
котировку в producer; StoreService transaction observer по-прежнему сохраняет
durable остаток. `currentPlan/findComponent`, goal load и reservation SQL не
вызываются из обычного reconcile уже tracked owner. Goal/reservation сверяются
на ограниченном planning cadence; durable owner recovery тоже остался bounded.
`open/restore` продолжает каноническую независимую перепроверку цен.

## Scope, конфиг и стоимость

Изменены `PhantomAutonomousMarketProducer.java`, `PhantomSystem.java`,
`PhantomMarket.ini`, `PhantomAutonomousMarketSuite.java` и этот отчёт. Ключи,
defaults и pricing math не менялись; исправлены только русские комментарии о
стоимости. Новых DB migrations, provider, worker, future или сетевого запроса
нет. Другие хроники, клиент и production DB не затронуты.

На обычном scheduler pulse сверяются не более configured максимума открытых
owner (default 2, верхний предел 16) по памяти/native state, без durable
component read. Не чаще раза в 15 секунд возможны bounded recovery page,
planning attempt и goal/reservation DB checks; native transaction, close,
restore и shutdown выполняют штатные durable операции при соответствующем
событии, отдельно от cadence. Отдельный микробенчмарк не выполнялся; это
структурное устранение connection-per-operation чтения на каждом 100-мс pulse.

## Проверки

- `ant phantom-post001-autonomous-market-test`: PASS 3/3 после исправления
  тестовой fixture. Сохранены native SELL/BUY conservation, replay/overdraw,
  stale restore и +4 object identity; добавлены partial transaction pulse,
  source hot-path/order contract, повторный fence, drain и post-drain pulse.
- POST-001 enchant/quote/recipe/loot/H5 source matrix, Goal022 native
  private-store BUY/SELL, scheduler, materialization и server shutdown routes:
  PASS. Scheduler 20/20, materialization 21/21, shutdown 7/7.
- Один `ant qol-009-freeze-test`: BUILD SUCCESSFUL, 16 минут 49 секунд;
  Goal039 static 8/8 и documentation 2/2.
- Один свежий полный `ant verify` после green preflight: BUILD SUCCESSFUL,
  36 минут 58 секунд. Сохранённый fresh `post001-autonomous-market.txt`:
  3/3 PASS, `market.shutdownFence=...nativeAndDurableDrained=true...`.
- Первый sandbox targeted compile не прошёл из-за JDK 25
  `AccessDeniedException` на существующем `HikariCP-7.0.2.jar`; повтор вне
  sandbox собрал код. Первый runtime targeted обнаружил только test-fixture
  cap после явного close; один producer pulse снял tracked запись, повтор 3/3.
- `git diff --check` по exact task files: PASS. Mojibake-маркеры в изменённых
  файлах проверены отдельно: совпадений нет. Escaped Cyrillic в изменённых
  файлах проверены отдельно: совпадений нет.

Тесты использовали только существующий guarded
`l2jmobiush5_phantom_test`; `prepare-phantom-test-db` и provisioning не
запускались. Ручной production/client gate не выполнялся и пакетом не требовался.

## Delivery и ограничения

До stage обнаружены три чужих modified файла и множество untracked task
packages; они не входят в exact allowlist этого коммита. Git inspection и
delivery разрешены `AGENTS.md` и данным `/goal`; history rewrite, restore и
force push не применялись. Exact subject:
`post(001): harden autonomous market shutdown`. SHA самого коммита и результат
normal push приводятся в финальном handoff после создания commit, поскольку
SHA нельзя вложить в hashed содержимое этого же отчёта.

Оставшееся ограничение: не выполнялся отдельный многопоточный stress test;
синхронизированный producer gate и composed shutdown/source проверки покрывают
текущую гонку. Следующий шаг — exact staged allowlist, один commit, normal push
и сравнение remote SHA с локальным HEAD. POST-002/QOL-010 не начинались.
