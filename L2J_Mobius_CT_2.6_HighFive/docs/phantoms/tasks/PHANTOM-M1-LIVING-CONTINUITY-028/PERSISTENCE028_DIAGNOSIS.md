# Persistence028: captured boundary и полный terminal gate

Финальный production SHA: `07c2c1cc4036b47487c98e96943a39ce295a3920`.
Исходные memberships: scene F1 — 8, scene F2 — 4, away F3 — 5.
Union содержит 10 profiles и 20 наблюдавшихся native lifetimes. Ни один исходный
участник не исключён. `WHOLE_GROUP_SAVE_PASS=false`.

## Exact captured SQL

Существующий024 validator сравнил immutable SEALED snapshots с реальными
consistent read-only SQL views после FINALIZED: F1 latest captured lifetimes8/8,
F3 latest captured lifetimes5/5. Это actual per-receipt views, не один общий снимок
БД. Native scalars, XYZ, EXP/SP, inventory и skills не заменялись expected values.

Часть более ранних temporary checkpoints уже имела state version `prepared+2`
при чтении SQL exporter: native lifetime успел повторно materialize. Все captured
native scalar comparisons совпали; эти version mismatches сохранены в inventory.
Снимок SEALED temporary checkpoint сам по себе не доказывает permanent terminal
seal. Witness не записывает permanentSeal. Отдельный final-owner-state away export
доказывает DETACHED/permanentSeal=true у пяти исходных away scopes. Полный join
terminal scope→latest receipt для всех20 lifetimes этим audit не реализован и
остаётся UNPROVEN; эти отдельные positive snapshots сохранены в raw archive.

## Потерянные receipts

Точный RED — отсутствуют оба PREPARED/FINALIZED для:

| Profile | Исходный epoch второй сцены |
|---|---|
|872|239072127458300|
|1272|239072201692100|

В `Contract028Observer.observe()` фильтр смотрит только на текущую
`selection.profiles()`. Следующий FullObserve меняет selection на новую cohort;
две прежние identity больше не проходят фильтр terminal hooks. Это collector
scope defect. Native receipt нельзя восстановить из поздней SQL. Локальный
store journal не был enrolled; отсутствие raw proof сохранено.

Рекомендуемая ограниченная коррекция collector для последующего разрешённого
запуска: сохранять bounded receipt membership union отдельно от фиксированной
текущей telemetry cohort, используя тот же hook/ring/exporter. В TASK028 она не
внесена: оригинальные пропущенные события уже произошли, ретроактивного PASS нет.

## Два фактических рестарта той же БД

GameServer35968/Login30824, затем GameServer27540/Login24012, каждый с проверенным
PID/start-time, тем же JAR и `l2jmobiush5_localplay_contract028f`. Synthetic не
запускался; canonical exports всех трёх исходных groups сняты до shutdown.

`FINAL_RESTART_CANONICAL_AUDIT.json`: все characters/items/skills/durable tables
совпадают с post-stop exports до первого рестарта, 6/6 group comparisons PASS.
Pending owned stores0, online characters0. Это canonical preservation proof,
а не восстановление отсутствующих SEALED receipts.

Прямой early SEALED→restart validator: F3 —5/5 в обоих; F1 —5/8 в обоих.
У424/876/1176 state/EXP/SP/XYZ/inventory уже отличались до рестарта от их раннего
seal: state versions3644/4585/6701 против prepared3631/4572/6688. Canonical audit
доказывает отсутствие изменений при рестарте; полная промежуточная native/background
lineage этих трёх не доказана. Эти mismatches не переименованы в8/8.

## Shutdown safety

Исходный stop после F1/F2/F3: initial drain `stopped=true` до pools.
Restart1: initial drain через10009ms — FAILED, retained0, final `stopped=false`.
Native stop-progress first reason не был экспортирован; точный первый predicate
UNKNOWN. Сохранены native log, stderr и четыре bounded thread dumps. Показанный
shutdown implementation027 не менялся. Нельзя объявлять этот FAIL pre-existing
или repaired только по следующему удачному запуску.

Restart2: initial drain `stopped=true`43ms до pools. Все own JVM фактически STOPPED,
force не применялся. `HEALTHY_ALL_FINAL_STOPS=false`; этот safety gate остаётся OPEN.

## Telemetry exporter

В away exporter сохранён sticky AccessDenied при замене `full-frame-latest.json`.
Полный JSONL flow содержит357 frames, max gap2.0276286s; конечные counters:
captured56/exported56/pending0/overflow0. Это классифицированная ошибка публикации
latest telemetry file. Конкретная Windows file-sharing причина UNKNOWN; гипотеза
reader lock не считается доказательством. Ошибка не сбрасывалась и не скрывалась.
