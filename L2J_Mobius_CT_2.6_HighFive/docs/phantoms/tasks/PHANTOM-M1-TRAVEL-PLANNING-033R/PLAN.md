# Исполнение TASK033R

> Исполнитель использует executing-plans, systematic-debugging/TDD;
> субагенты запрещены. Coordinator уже задал архитектурную границу.

**Goal:** материализованный Phantom выбирает и исполняет доказанный путь к полезной работе.
**Architecture:** стратегия/граф native-коридоров → существующий navigation/Player executor.
**Tech:** Java25, Ant, MariaDB, Windows11, stock GeoEngine; без C#/новых dependencies.
**Spec:** DESIGN.md + ESCAPE_AND_RECOVERY.md.

## 0. Read-first и старт (до45–60мин)
- [ ] Проверить exact base/worktree и immutable source032 evidence, актуальные геоданные.
- [ ] Снять реально действующий UnstuckInterval/route budgets/fee rules из owned export.
- [ ] Отличить raw/compressed/native failure, не ограничиваться словом NO_PATH.
- [ ] Запустить короткий native baseline с существующими observer032 и Synthetic.

## 1. Граф пешего обхода
- [ ] T01–T10 RED: реальный класс ошибки + изолированный U-shaped contract.
- [ ] Сначала reuse полного stock A*; затем generic macro corridor из доказанных local routes.
- [ ] Разделить candidate edge и verified executable edge; избежать глобального Geo rebuild.
- [ ] Short runtime: обход → actual arrival →5native cycles; ранний endpoint обязателен.

## 2. Выбор способа и нормальный возврат
- [ ] T11–T14: существующий GK route, полная стоимость/доступ, safe native return lifecycle.
- [ ] Интегрировать только безопасные режимы; reasons для unavailable, никаких фиктивных fees.
- [ ] Один bounded native transport/recovery proof, без сцены с подменённой reward моделью.
- [ ] T15/T16: emergencyOFF и отрицательные требования. Emergency execution optional,
      реализовывать после основных режимов, только если разрешённые facts доступны.

## 3. Последствия
- [ ] T17/T18: ownership, stale replies, elapsed deadlines, task-cancellation, cooperative.
- [ ] Целевые regression пути и raw resource/position conservation.
- [ ] При неудаче одного связанного mode продолжать другую безопасную работу,
      не подставлять bypass и не прекращать всё на первом новом in-scope subreason.

## 4. Freeze и приёмка (после6ч без новых semantic fixes)
- [ ] Exact-path source commit/normal push; clean committed build, source/Jar identity.
- [ ] Один полный93+11; route→farm и natural scene без debugger pauses.
- [ ] Сохранить результаты WALK_DETOUR, TRANSPORT, EMERGENCY separately.
- [ ] Actual own graceful stop, pending/retained/process guards.
- [ ] RESULT_HANDOFF/MORNING с командами повторного запуска, open facts, known failures.

Не создавать 40 новых tooling wrappers. Новый runner максимум один, импорт существующих
helpers с exact033r guard. Не менять старые task evidence. JSON telemetry read sharing и
premain lifetime enrollment переиспользовать, не изобретать снова.
