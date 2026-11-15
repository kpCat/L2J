# Necessary-only Party MP preview — обязательный контракт

## Идея
*PRODUCTION* `L2jPhantomPartyBackend` остаётся единственным владельцем логики `groupViable`. Для тренера вводится вызываемый только при headless-planning/TEST **read-only negative witness**, не изменяющий результат обычного фарма.

### Допустимые результаты
- `PROVEN_IMPOSSIBLE`: доказана невозможность достичь необходимого MP **после обычного допустимого восстановления**, при *exact frozen* roster+target+selected stock/skill alternatives+loadout+skills+config+resource ceiling. Основание типизировано; hashed provenance и lower/upper bounds прилагаются. Такой candidate не расходует materialization/recovery.
- `UNKNOWN`: отсутствует любое существенное canonical поле, possible buff/status/gear/target/skill drift, нельзя доказать верхнюю границу MP или нижнюю границу расходов. TRAIN обязан перейти к обычному native preview/admission, а не исключать класс или учебное действие.
- `MAYBE`: необходимые ограничители не доказали невозможность. Это *не* разрешение CUT, только продолжение обычной подготовки.

Любые ошибки доступа/source/owner дают `UNKNOWN` или fail-closed `ERROR` с собственным кодом, но **не false PROVEN_IMPOSSIBLE**. Прямое производство frozen byte proofs по старой схеме остаётся обязательным.

### Математическая необходимая проверка
Пусть `R_min` — доказуемая **нижняя** граница расхода на данный бой по общему каноническому Party forecast с учётом всех допустимых альтернатив данного pinned action set. `M_max` — доказуемая **верхняя** граница доступного после штатного восстановления MP для того же персонажа при всех разрешённых до CUT эффектах, которые ещё могут появиться. Если `R_min > 0.8 * M_max` — кандидат невозможен по действующему canonical MP budget.

Если нельзя получить доказанный `R_min`/`M_max`, результат `UNKNOWN`. Нельзя вычислять `M_max` просто из текущего `curMp` или использовать ровно `getMaxMp`, игнорируя возможные buff/gear/instance effects. Нельзя назначать недоступному умению нулевую цену. `3060 > 2244.8` — historical diagnostic value, **не правило**.

### Интеграция
Предпочтительно выделить короткий pure read-only общий код вычисления bounds из production `groupViable` и небольшую typed диагностическую оболочку в `L2jPhantomPartyBackend` без изменения старых методов. Новый метод TEST вызывает с точными actor/roster/target facts *до* длительного recovery, но не с поддельной имитацией Party. Старый runtime использует прежний путь, а общая арифметика проходит parity tests.

Если существующие типы не позволяют верно выдать предварительные bounds без широкого публичного API/второго движка — **не создавать псевдоканонический интерфейс**. Выдать владельцу варианты с кодом/ценой/рисками и остановить native с `PARTIAL_DESIGN_BLOCKED`. Дополнительные production компоненты требуют отдельного согласования.

### Неизменяемость
Pure preview: никакой SQL-write, no new effect, no buff, no `setCurrentMp()`, no `getParty()` mutation, no NPC relocation, no acquired skill grant, no scheduler, no cross-owner global cache. Сохраняет `groupViable` результат в обычном игровом контексте; ≤ bounded CPU/allocations, test-only call path. Величины и отказы протоколировать без паролей/сырого PII.

### Защита от false exclusion
Обязательные negative cases: increased maxMP by lawful buff; different target HP/champion; cheaper skill in full acquired set; changed learned enchanted level; changed classIndex; different party composition; different weapon; altered config; full natural regeneration; added/removed supported heal; live owner change; near-equality 80% boundary; NaN/overflow; stale forecast. Для каждого не должно быть ложного `PROVEN_IMPOSSIBLE`; допускается `UNKNOWN`.
