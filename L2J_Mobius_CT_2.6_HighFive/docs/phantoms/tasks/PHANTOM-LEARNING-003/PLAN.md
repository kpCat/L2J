# PHANTOM-LEARNING-003 — план реализации

> For agentic workers: использовать executing-plans для интеграции; отдельный ограниченный исполнитель модели допустим по subagent-driven-development. Все проверки — реальные exit codes, без повторной аттестации Windows Job.

**Goal:** законченная TEST-вертикаль native HEAL → immutable V3 → SQL cold reload → lawful Party HEAL с сохранением DAMAGE002.

**Architecture:** stock PartyTactics перечисляет кандидаты; V3 ранжирует только неэкстренные HEAL одного актёра. Combat lease и native Skill остаются единственными исполнителями. Новый schema/codec/SQL registry отделён от V1.

**Tech Stack:** Java25, Ant, существующая MariaDB11.4 и Windows Training Director; без новых зависимостей.

**Spec:** TASK.md, ARCHITECTURE.md, FEATURE_SCHEMA.md, ACTION_CONTRACT.md, EVIDENCE_AND_QUALITY.md, ACCEPTANCE.md.

## Global Constraints

- Пользовательский checkout READ ONLY. Только независимый clone и wip/phantom-learning-003 от b7b5575457c12c1b0438bcbaa646675a67778391.
- V1 LearningFeatures/NeuralPolicy codec и historical accepted002 bytes не менять.
- 4–8 МиБ настоящих float32 weights, состояние кодируется один раз на решение, bounded actions, immutable inference.
- NATIVE_HEAL только после exact native HP writer с actor/target/epoch/skill/party/action authority; no duplicate credit.
- TRAIN/TUNING/AUDIT разделены до обучения по actor/context; AUDIT не используется для подбора.
- Никаких новых движков, потоков на актёра, hot SQL, gameplay formula changes или production promotion.
- TRAIN_*.cmd manual acceptance отложена владельцем; Windows Job и bootstrap не переаттестуются.

## Read-first и bounded exception

Прочитаны AGENTS/Agents, README, workflow, task standard, релевантный master plan,
пакет003, HANDOFF/RESULT/решения/отчёт002, build.xml, LearningFeatures/Policy/Store/
CastEvidence/NeuralPolicy, PartyTactics/Coordinator, PlayerNativeEvidence и native fixtures.
Аналоги: immutable V1 codec; isolated SQL guard+batch writer; stock Party support+Combat lease;
pre-CUT test actor setup и пассивный native Heal observer. Вышестоящие AGENTS и отдельные
pattern-файлы не найдены. Повторный поиск не требуется.

Исключение свыше 10 файлов ограничено одной связанной вертикалью:
новые *V3.java в learning; точечные Party/Combat/native observation seams;
Training entrypoints/config только для выбора V3 и существующего isolated TEST;
focused test/java/*Learning003* и необходимые reuse seams fixtures;
одна SQL migration, build/launcher route при необходимости; входной пакет003,
PLAN/PROGRESS/HANDOFF/RESULT/scope guard, русский отчёт003.
Перед staging будет exact allowlist, outside=0. Studio и прочие подсистемы вне scope.

## Карта actions

| Action | Owner | Candidates | Receipt | Learning |
|---|---|---|---|---|
| DAMAGE | CombatService/L2jCombatBackend | existing exact offensive skills | LearningCastEvidence | V1/002 неизменный fallback |
| HEAL | PartyTactics → Combat external lease | learned/ready stock heal для текущей Party | exact native Heal HP write + MP + authority | V3 |
| RECHARGE/RESURRECT/CONTROL/DISPEL | существующие stock owners | typed contract, execution не расширяется | отдельный будущий receipt | trainable=false |
| MOVE/RETREAT/SUMMON/SPOIL/SWEEP | Navigation/Combat/Progression | typed contract | отдельный будущий receipt | trainable=false |

## Review Focus

- Смена party roster/owner/epoch во время каста: receipt не засчитывается.
- Emergency/критический союзник: stock rescue сохраняет приоритет.
- UNKNOWN hostile inventory/cooldown: не читать скрытые данные и не превращать UNKNOWN в zero.
- Повреждённый/чужой schema checkpoint или overbudget: deterministic stock fallback.
- Неполный каст/duplicate native callback: zero или один durable event, без synthetic credit.

## Task 1: Immutable модель V3

**Files:** learning/TacticalModelV3.java; focused PhantomLearning003ModelChecks.java.
**Interfaces:** STATE_SIZE=128, ACTION_SIZE=64; initialized(long seed), encode(float[128]),
scoreEncoded(float[] encoded,float[64]), score(float[128],float[64]), bytes(), sha256(),
restore(byte[],String sha), train(List<Example>,List<Example> tuning,long seed), loss(List<Example>).
Example(float[] state,float[] action,float reward); Fit(model,epochs,initialLoss,loss).
Fingerprint собственного алгоритма включает dimensions и normalization; observation schema
идентифицируется отдельным checkpoint metadata.

- [ ] Написать и выполнить focused RED для checkpoint corruption/roundtrip и реального обучения.
- [ ] Реализовать dense state encoder около 1–2 млн float32 параметров и дешёвую action head.
- [ ] Проверить math learning, immutable roundtrip, NaN/version/size rejection; synthetic только math.
- [ ] Измерить warmed encode+4/8/16 scores для 128 logical actors; full snapshot измеряется отдельно.

## Task 2: Native observation/action/HEAL integration

**Files:** learning/TacticalObservationV3.java, LegalActionV3.java, TacticalPolicyV3.java,
HealEvidenceV3.java; минимальные seams PartyTactics/L2jCombatBackend и native cast ticket.
**Consumes:** Task1 encode+scoreEncoded. **Produces:** legal selection и exact rich native event.

- [ ] Написать focused RED для masks/entity bounds, emergency fallback и stale/duplicate receipt.
- [ ] Собирать реальные class/level/stats/gear+jewels/effects/geometry/Party/summon/enemy факты,
      deterministic bounds; hidden enemy fields UNKNOWN.
- [ ] Ранжировать только lawful nonemergency HEAL одного actor. OFF/SHADOW/ON, throttle,
      maxEntities/maxActions и budget fallback; owner повторно валидирует перед dispatch.
- [ ] Привязать пассивный HP writer к конкретному cast/action generation; сохранять useful HP,
      MP/time и предложенные альтернативы, без reward за issued/overheal.
- [ ] Выполнить focused GREEN и affected Party/Combat tests.

## Task 3: Durable training и native proof

**Files:** learning/TacticalStoreV3.java, TacticalTrainerV3.java; отдельная SQL migration;
TrainingV3.ini и минимальная backward-compatible маршрутизация существующего Director;
test/PhantomLearning003Native.java и необходимые существующие fixture seams.
**Consumes:** Tasks1–2. **Produces:** DRAFT/ACCEPTED_TEST checkpoint, portable export, cold proof.

- [ ] Подготовить собственный TEST existing Director/G0/SQL, свои порты и datadir.
- [ ] До CUT реальные native healer skills и >=2 законных конкурирующих HEAL alternatives.
- [ ] Собрать native train/tuning/audit в независимых actor/context. Никаких post-CUT HP writes.
- [ ] Offline fit, baseline/constant/audit metrics, immutable SQL save/readback, export/rollback.
- [ ] Холодно загрузить принятый TEST checkpoint: lawful changed HEAL и реальный HP/MP receipt.
- [ ] На том же изменённом JAR проверить DAMAGE002 Solo/Party, обычный Player heal, F7/F8.

## Task 4: Проверки и доставка

- [ ] Полный snapshot/encode/score/dispatch benchmark, p50/p95/p99 CPU/RAM/alloc, limitations.
- [ ] Один итоговый targeted regression маршрут; safe STOP только собственных процессов.
- [ ] Русский отчёт, HANDOFF004, exact SHA модели/JAR, evidence manifest и changed paths.
- [ ] Exact artifact scope guard, git diff --check, две отдельные encoding проверки.
- [ ] Scoped commit и обычный non-force push только wip/phantom-learning-003; independent HEAD readback.

## Решения

Дополнение владельца о реальных КД/времени принято в ту же вертикаль. Точный дополнительный
scope и native acceptance зафиксированы в OWNER_ADDENDUM.md. Завершение требует обоих новых
native proofs; ранее подтверждённые HEAL/DAMAGE и immutable модели сохраняются.

Пакет владельца уже задаёт архитектуру и разрешает самостоятельные технические решения;
дополнительный промежуточный design approval не вводится. Собственный clone размещён в
разрешённой writable task-папке, вне пользовательского checkout. Это независимый GitHub
clone без alternates. Существующая инфраструктура002 будет переиспользована с явным
конфигом источника read-only assets, поскольку clone находится не рядом с прежним001.
