# Архитектура автономного тренажёра

## Сущности (названия интерфейсов примерные)
- `TrainingScenario`: version + seed + mode + requiredCapabilities + class/level/loadout/boost constraints + env/geography + NPC requirements + intended action categories; не конкретные profileId/instanceId. Каталог сценариев из actual game knowledge и versioned rules.
- `ScenarioGenerator`: распределяет покрытия по классам/ролям/уровням/экипировке/NPC, выбирает legal кандидатов; seeded reproducibility; difficulty progression. Не строить тупой полный декартов перебор.
- `ScenarioPreflight`: stock Player/Party/Combat/Geo/loot/progression check до CUT; reasoned rejection and next candidate. `NONE_FEASIBLE` фиксируется как категория покрытия и не останавливает общую кампанию, если другие legal сценарии возможны.
- `Episode`: exact run/scenario/actor/party/combat generation, observed candidate actions and live outcomes; `PREPARED->ACTIVE->SETTLING->COMMITTED`/`ABORTED`. После CUT только native gameplay; никакого редактора SQL для подделки EXP/SP.
- `LearningCollector`: existing 001 native exact evidence + optional immutable context envelopes. Independent queue and SQL batch. Numeric feature encoder V1 не менять молча; richer immutable facts optional and versioned. Do not convert absence into zero known information.
- `TrainingDirector`: bounded scheduler + lifecycle, single authority for owned runtime and job state. No individual thread per actor, no busy loop, budgeted tasks.
- `TrainingEvaluator`: offline candidate creation; sample-level held-out, value per scope, comparison to constant baseline and last accepted, no automatic MAIN promotion. Cold ON on TEST only when permitted. Report scarcity/overfit.
- `RunSupervisor`: own process identity, loopback/G0, restart/resume, cancellation, heartbeat and result. Distinct process from Codex.

## Эталонный конвейер
`Owner START -> verify own files/ports/db/geo -> select feasible scenario -> pre-CUT natural native actor setup -> start GameServer episode -> exact native Combat/Party action -> authoritative outcome/STORE -> persist native evidence & rich context -> checkpoint -> next scenario -> threshold -> offline trainer -> independent evaluation -> accepted TEST candidate -> readback and optional cold ON -> continue`.

Использовать один GameServer на несколько эпизодов там, где штатный owner допускает. При конфигурационной границе изменения аттестованного JAR/schema или необходимости cold reload — штатная остановка и контролируемый restart. Нет GameServer restart на каждый каст.

## Выбор поведения и эффект обучения
Существующая `LearningPolicy` умеет только выбирать 2–4 реально доступных PvE атакующих навыка. Директор V1 обязан доказать, что эти действия получают NATIVE evidence и позже применяют model v2 через CombatService. Не расширять action space фиктивными HEAL/KITE/PK параметрами без законного диспетчера. Для дальнейших LEARN-003/004 использовать general schema с типизированным намерением и legal action capability descriptor, а не 64 фиксированных флагов.

## Обучение и оценка
- Директор может обучать самостоятельно после batch boundary (или во внешнем offline процессе), но не внутри realtime Combat tick. Использовать frozen train/tune/audit, независимые actor+scenario seeds. Данные разных real actors и epoch; duplicates hashed.
- Candidate remains DRAFT when not superior by documented gates; keep existing last ACCEPTED. No cheating: don't tune on AUDIT and then claim independent generalization. New independent eval seeds needed after each tuning/retry.
- Quality must include at minimum: audit MSE vs constant and prior model, action reordering rate, native skill action/HP verified, per-scope coverage and unsafe action rate; upgrade to real earned PvE objective score in subsequent LEARN003.
- 001 v1/v2 weights stored in MariaDB as immutable versions; independent export of `weights+algo+feature schema+catalog SHA+quality` with no user DB credentials. Distinguish TEST accepted and MAIN promotion.

## Сохранение наблюдений для более крупного ИИ
Сохранять минимальный **структурный** snapshot, а не только 128 чисел:
- actor skill/capability family + live normalized vitals, level, class, equipped capability facts, buff/debuff categories and visible effect masks;
- allied roster list (2..9), roles, observable HP/MP/positions, pets where actually observed;
- enemy set (bounded) with npc/player observed kind, level, visible threat, distance/bearing/height, target, known resistances, LOS/Geo facts; no hidden player equipment;
- offered action descriptors + legal/masked status and chosen action; native outcome, source/training provenance, run/scenario seed, timestamps;
- EXP/SP/loot/spoil/sweeper and deaths tracked as event facts only if genuine authoritative receipts exist; otherwise UNKNOWN.

Serialization can be versioned compact binary or JSON for low-volume episodes; avoid huge world dumps and secret leaks. Distinguish schema version and content hash. Future new model can re-encode from rich snapshots without losing old native evidence. Tests for backward read of absent fields.

## Performance
Background non-materialized profiles do not execute full neural inference. Keep runtime caps and configurable campaign concurrency. The existing tiny MLP remains Java CPU inference in 002. GPU training architecture is optional plug-in boundary for 003, never forced dependency for `TRAIN_START.cmd`.

## User commands
Defaults configured through `TRAIN_CONFIG.ini`. `TRAIN_START` detaches a supervised user-owned process; separate stdout/stderr log + durable status and heartbeat; `TRAIN_STATUS` reads durable own state without needing agent. `TRAIN_PAUSE` stops issuing new episodes and drains current ones safely. `TRAIN_RESUME` resumes. `TRAIN_STOP` drains and shuts down only own processes. `TRAIN_REPORT` writes a Russian Markdown/JSON report with coverage and outcome. `TRAIN_EXPORT` creates portable model artifact if suitable accepted version exists; otherwise explains why not and returns non-success status.
