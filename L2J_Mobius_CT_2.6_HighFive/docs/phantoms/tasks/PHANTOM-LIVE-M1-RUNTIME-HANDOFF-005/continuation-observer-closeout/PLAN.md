# План одной реализации

> Исполнитель: Codex; inline execution по `superpowers:executing-plans`.
> Не создавать команды subagents/reviewers на каждый пункт. Один итоговый обзор diff.

**Цель:** исправить полный observer-путь и получить достоверную приёмку M1 на 1280.
**Архитектура:** адресные immutable снимки из native Player; bounded Pilot observer;
никакого нового gameplay scheduler или синхронизации координат с БД.
**Стек:** Java 25, Ant, существующие PowerShell tools и guarded TEST.
**Спецификация:** [DESIGN.md](DESIGN.md). Общие ограничения: [TASK.md](TASK.md).

## Риски, которые обязательно проверяются
Движущийся Player и stale anchor; одинаковый objectId у нового экземпляра;
запрет произвольного transport; нет настоящей visibility при couldKnow;
ошибка cleanup/logging и переполнение mailbox. Тесты перечислены в ACCEPTANCE.

## 1. Короткий baseline и регрессии
- [ ] Проверить remote/SHA и чистую изолированную копию; сохранить чужие hunks.
- [ ] Прочитать ровно перечисленные методы. Сохранить INPUT_SHA и текущие файлы отчёта.
- [ ] Добавить параметризованные cases из fixtures в существующий
      `PhantomOperatorObservabilitySuite` (не текстовый grep исходника).
      Helpers должны вызываться production-кодом Actions, а не существовать только в тестах.
- [ ] Дополнить короткий native fixture в `PhantomBackgroundSuite` для
      POSITION_CANONICALIZATION: один реальный TEST Player меняет live-позицию без
      durable commit; observer видит новую позицию, durable record не меняется.
- [ ] RED запуск только этих cases; ожидается failure stale-target/неверной фазы,
      а не отсутствие geodata/JDBC/config.

Перед native TEST проверить DATAPACK_ROOT, доступность уже используемых geodata assets,
TEST Database.ini, manifest и отсутствие PLAY URL. Не ждать 50 минут при явном
navigation_direct_unverified_no_geodata: остановить только owned TEST child,
исправить путь/подключение существующих assets, повторить этот target. Не трогать PLAY геодату.

## 2. Источник позиции и подготовка observer
- [ ] Создать маленький `LocalPlayM1Observation.java`: value objects/чистые решения
      разделов 1, 2, 4, 5 DESIGN; без I/O, фонового состояния и собственных потоков.
- [ ] В `PhantomSystem` добавить `operatorM1TargetSnapshot(long)` и ограниченный M1
      candidate-list method, использующий effective positions и metadata в памяти.
      Старые operatorLocalityTarget/committedPosition и игровые publishers не менять.
      Список собрать один раз на INITIAL; адресное чтение одного target — для последующих фаз.
- [ ] В `LocalPlayPilotActions` все M1 geometry/route/visibility решения потребляют
      этот snapshot. Старые committed fields оставить отдельно в output.
- [ ] В prepareM1Envelope реализовать stage INITIAL/APPROACH/LEAVE/RETURN и
      selectionKind. До approach не подменять STORED на EXISTING без явного статуса.
- [ ] В snapshotM1Envelope заменить преждевременное COMMITTED_ANCHOR_CHANGED на
      типизированную смену версии/необходимость перепланировки до контакта;
      hard identity change после lock остаётся RED.

## 3. Перенос, лёгкие снимки и фазы runner
- [ ] В execute/prepare/teleport передать реальный request.runId; добавить один
      одноразовый M1 ticket только для LEAVE/RETURN. Проверить TTL/epoch/XYZ/purpose,
      не расширяя остальные команды Pilot и не меняя Protocol.Operation.
- [ ] includeCensus=false в шаговых снимках; выбранные simple native flags в памяти.
      Исторический статус читать из существующего cached progress API.
- [ ] В том же `Run-M1RuntimeHandoff.ps1` реализовать динамический APPROACH,
      отдельные timing evidence и фазовый continuity lock; убрать Teleport-To
      из первоначального follow. Сохранить basename и формат папки результатов.
- [ ] LEAVE/RETURN используют свои tickets и актуальный outside/meeting point.
      Никакого старого start как безусловной точки ухода и XYZ=null→0.
- [ ] Census три раза, отдельная проверка действий и negative idle; закрыть finally,
      неизменяемый origin, request counters, DateTime и 45 s cleanup.
- [ ] Добавить `Test-M1ObserverOffline.ps1` в папку этой continuation: запускает
      ФАКТИЧЕСКИЕ функции runner с подставным transport/clock и контролируемыми снапшотами.
      Только синтаксический parse/grep не достаточен. Dot-source/test seam не должен
      автоматически запускать PLAY: отдельный main, вызываемый только при обычном запуске.

## 4. Focused GREEN и интеграция перед deploy
- [ ] Повторить cases существующего observability suite + offline PS runner.
- [ ] Выполнить один короткий existing native target:
      `ant -Dbuild=.phantom-local/m1-observer-build phantom-background-position-canonicalization-test`.
      Дополненный native case обязан обращаться к тому же observer API, который использует Pilot.
- [ ] Для запуска observability использовать проверенный mode
      `operator-observability-selected-trace` у `PhantomTestLauncher <mode> <seed>`;
      при необходимости добавить только alias `phantom-m1-observer-closeout-test`,
      зависящий от compile-tests и вызывающий тот же suite. Никаких новых test frameworks.
      Mode уже проверен в PhantomTestLauncher.suite; не запускать все режимы.
      Seed для новых регрессий: 20260929007. Отдельного нового suite/launcher не требуется.
- [ ] Повторять после исправления только затронутую проверку. Не запускать весь
      phantom-m1-runtime-handoff-test/205 tests, broad1..85 и 10k scale для observer-only diff.
      Если всё-таки изменился gameplay-код, прежнее разрешение на сокращённый набор
      больше не действует: нужны соответствующие точечные регрессии и явное объяснение scope.
- [ ] Итоговый diff review: отсутствие новых SQL writes/timers и обхода consent;
      существующие противоречия из HANDOFF каждое закреплено тестом.

## 5. Публикация и единственный игровой сеанс
- [ ] Exact-path code commit/push; clean exact-SHA JAR. В production deployment не
      включать исходный грязный checkout или чужой PhantomMaterializationService.
- [ ] Backup owned runtime/JAR/manifest и штатный backup PLAY перед restart; deployment
      через уже используемые ownership-guarded инструменты. Сохранить LoginJAR/config.
- [ ] Проверить owned PID/hash/config, headroom, короткий preflight без TestAdmin movement.
      Не требовать нулевого глобального failed-history count и не пытаться чинить оставшиеся
      canonical conflicts в этой задаче.
- [ ] Когда готово всё, попросить один свежий arm. Невведённый код не считать запуском.
- [ ] Запустить один runner. До фиксации выбранной цели допустим только ограниченный
      выбор сцены; после игрового RED никакого перебора «хороших» профилей.
- [ ] Завершить restore/stop на всех исходах. Если наблюдатель вскрыл свой остаточный
      harness defect, исправить в этой же задаче; лимит повторного запуска — TASK.
- [ ] Краткий RESULT + STATE. Записать NEW_MATERIALIZATION, CONTACT, NATIVE_LIFE,
      COHORT, SOFT_RETURN, RESTORE, STOP отдельно, затем итог. Не переносить M2 сюда.
