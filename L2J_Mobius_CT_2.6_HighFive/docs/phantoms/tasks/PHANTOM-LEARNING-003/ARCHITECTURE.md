# Архитектура V3: единый мозг, разные классы и разные действия

## Приоритет
Минимизировать rework: V1/002 модель 12 500 Б продолжает работать как fallback. Новый immutable V3 action scorer — отдельная версия модели, общий для всех профилей. Различается состояние и набор доступных действий, не экземпляр нейросети на персонажа. Каждый бот получает собственный контекст/короткую память, Party имеет общий ограниченный контекст, правом штатного лидера не владеет нейросеть.

## Поток
`Native World/Player/Party snapshot (read-only) → legal action generation (stock owners) → typed V3 observations + missing masks → shared entity encoder (один раз) → bounded per-action scorer → owner arbitration/lease → native effect → exact receipt → durable episode → offline learner → model registry → cold loading / shadow / controlled ON`.

## Модель 4–8 МиБ
Предпочтение: компактный набор обучаемых embedding/MLP с общими весами: кодировщик self; shared кодировщик Ally/Summon/Enemy с role+relative facts; masked/bounded aggregation (без отдельного full A*); encoder действий; scorer `Q(state, typed action)`; не нужно 5000 копий. Если полномасштабный encoder при каждой оценке действия слишком дорог, state embedding кешируется ТОЛЬКО в пределах неизменного наблюдения/decision, с CAS epoch; при новом tick обновление.

Цель размещения: 4–8 **MiB сериализованных float32 weights**, не число параметров из воздуха. Алгоритм, размеры, версия, hash, normalization и action typing — в checkpoint. Реальная плотная сеть порядка 1–2 млн параметров может оказаться медленной на i7-11850H: до native ON провести реальное end-to-end сравнительное измерение с bounded action counts. Если не укладывается — двухступенчатое ранжирование и меньше ширина/частота, не постоянный GPU/IPC в боевом тике.

## «Знания игры» против «знаний модели»
Штатный L2J остаётся реестром реальных Skill/Items/Effects/Stats/Geo. Веса не обязаны хранить таблицу каждого предмета/скилла. Модель учится выбирать законные действия и обобщать их через тип эффекта, ценность, MP, время, range, классовую роль и ситуацию. Значимые механики: magicLevel-vs-level, resistances, weapon/armor/jewel bonuses, SA/enchant, buff/debuff, face/back/flank, summon owner and summon statuses. Нельзя угадывать скрытые эффекты/непроверенные API.

## Многоуровневое решение
1. Stock rules фильтруют и защищают экстренные ограничения (нет скилла, не хватает MP, target не visible, критический rescue, PvP authority).
2. Стратегический приоритет: survival/support/control/offense — на первом этапе safety-owned арбитраж, но typed context готов к обучению после sufficient evidence.
3. Тактический ranker: среди разрешённых и сравнимых действий выбирает skill/target/position. В V3 нативно обучается HEAL-target; Damage V1 сохраняется, пригодность нового Damage V3 проверяется отдельно. Контроль/перемещение/ресы — typed slots, без false «trained».

## Миграция модели
Старый algorithm 1/2, schema V1 и accepted SQL остаются read-only. В V3 новый алгоритм/registry и отдельный API. Бывшие 117 боевых событий могут применяться только для сохранённых V1-признаков, не воспроизводить выдуманные V3-факты. Вариант teacher-distillation допустим как provenance=TEACHER, только для pretraining, не label=NATIVE; качество проверять на новых actual episodes. При росте V3 ещё раз сохранить исходные `observations` и receipts: возможность переобучить V4 не зависит от совпадения слотов старой сети.

## Унификация с будущими задачами
- Дебафы/стан: native application event, status effect state, продление duration, immunity/resist/cleanse; не reward за cast без нанесённого эффекта.
- Resurrection: cast skill vs scroll separate kind, cast/after revive, team saved, time/risk; прямой credit только при native revive receipt.
- Summon: есть owner+summon identities, HP/status/UD, cooldown, controlled summon actions, offensive anchor; не «второй фантом».
- Movement: move intent/target point, L2 GeoData/stock owner, path/outcome receipt; не обучать обычный A* «угадывать» проходимость.
- Олимпиада, осады, TW: game rules and commanders decide legality; модель использует общий контекст и соответствующий objective reward после появления native receipts.
- Semantic Pack: отдельная поздняя модель/LM service, не влиять на Combat latency, делать диалоги на русском по реальному игровому контексту; не копировать просто веса огромной LLM в 8 МиБ боевого scorer.
