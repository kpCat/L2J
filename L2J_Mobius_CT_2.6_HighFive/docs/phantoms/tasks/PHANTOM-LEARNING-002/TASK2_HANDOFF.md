# Task 2 — SQL-журнал, обучение и экспорт

Реализация передана для последовательной проверки главным агентом. Сборки,
GameServer, SQL и Git-команды этим исполнителем не запускались. Это не утверждение
о прохождении SQL/native gates.

## Изменённые файлы

- `java/org/l2jmobius/gameserver/phantoms/learning/TrainingStore.java`.
- `java/org/l2jmobius/gameserver/phantoms/learning/TrainingEvaluator.java`.
- `java/org/l2jmobius/gameserver/phantoms/learning/LearningStore.java`: package-level
  catalogFingerprint/contextKey и promote(Connection,long); обычный public promote
  использует тот же контракт и явно завершает транзакцию.
- `dist/db_installer/sql/game/phantom_training.sql`.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingStoreChecks.java`.
- Этот handoff. Другие файлы исполнитель не изменял.

Прочитаны PLAN и DESIGN_DECISIONS, исходный пакет/инструкции, learning production,
001 Offline/SQL/Suite/launcher, installer manifest/runner. Переиспользованы exact
LearningCastEvidence, LearningFeatures V1, residual NeuralPolicy, каталог и
qualification/promotion LearningStore, installer inventory и stock JDBC.

## Интерфейсы и владельцы

`TrainingStore(Path installation)` принимает физический root, точный owner.txt
`LEARNING002:<32hex>`, root/TRAIN_CONFIG.ini и game/config/Database.ini. Только
account learning001, schema phantom_learning001_train002, host 127.0.0.1 и
конфигурируемый sqlPort. Каждое соединение сверяет DATABASE/port/MariaDB/user,
фактический datadir root/sql/data и неизменные owner/Database.ini.

`initialize()` предполагает, что полный stock installer уже установил
phantom_training.sql; проверяет version1 и закрепляет installation hash.
Actor/roster manifests для probe не нужны.

Native API:

```java
void recover(String runId, String epoch)
long nextOrdinal()
String begin(String runId, String epoch, long ordinal, String scenario,
    long seed, String scope, String split, String beforeJson)
boolean finish(String episodeId, String afterJson,
    List<TrainingCollector.Observation> actions, String outcome, long elapsedMillis)
void abort(String episodeId, String reason)
Counts counts()
void requireStopped()
```

finish возвращает true только для SQL-подтверждённого COMMITTED. Native runner
должен увеличивать свой committed только при true. Empty/missing/non-native,
старое событие, wrong scope/schema/catalog/actor split дают INCOMPLETE без credit.
Перезапись COMMITTED другим содержимым запрещена; точный повтор идемпотентен.
Его event keys уникальны во всём собственном ledger. Ordinal потребляется begin
под row lock и никогда не используется повторно. recover перед новым bootstrap
помечает старые незавершённые эпизоды INCOMPLETE, прерванный PREPARING — FAILED;
сохранённый DRAFT остаётся DRAFT с причиной прерывания.

Numeric evidence копируется из source SQL только после begin timestamp, с
NATIVE/EXACT_CAST_HP, finite positive damage, exact feature schema/catalog,
валидными features/reward. Actor split обязан совпасть с исходным алгоритмом
floorMod(actorGroup.hashCode(),5): 0 AUDIT, 1 TUNING, остальные TRAIN. Один actor
не может перемещаться между разделами. Есть bounded ожидание async SQL flush вне
Combat. Observer не делает SQL; подтверждение вызывается на settled boundary.

## Frozen обучение

CLI:

```text
TrainingEvaluator <train|status|report|export|probe> <installation>
```

train проверяет отсутствие других SQL connections данной схемы. COMMITTED
membership/все raw numeric rows копируются в immutable dataset до обучения.
Overlap audit contexts исключается по существующему LearningStore.contextKey;
совпадающий actual scenario с fitted тоже исключается независимо от seed. Требуются TRAIN>=16,
TUNING>=8, AUDIT>=8, оба scope, два навыка в каждом train/audit scope, >=2 audit
actors и minAuditPerScope. Scarcity — exit0 COLLECTING, новая история не выдумывается.

Audit cohort key = SHA256(actorGroup + ':' + actualScenario).
Он потребляется до обучения и не используется повторно даже после crash/reject.
Ordinal-only episodeSeed остаётся metadata и не создаёт независимый cohort.
Дополнительно SQL NOT EXISTS проверяет immutable frozen AUDIT history по
actor_group+scenario, поэтому старый формат claim key не позволяет повторный audit.
Fitted/held-out scene separation также сверяется со всей frozen history, а не только
последними4096 rows: прежняя TRAIN/TUNING scene не становится AUDIT, прежняя AUDIT
scene не попадает в TRAIN/TUNING последующих моделей.
Native runner создаёт действительно новых pre-CUT actors после persisted attempt
и хранит immutable snapshots поколений каталога; новая метка старого актёра/сцены
не считается новым опытом. Trainer seed берётся из frozen config и не подбирается
по результату audit.

Полученные веса всегда сохраняются собственным DRAFT, даже step0/отказ по качеству.
Per-scope audit MSE сравнивается с TRAIN-mean constant своего scope и последним
accepted model на тех же независимых rows. Нужен относительный выигрыш над обоими.
Только после этих gates выполняется штатный LearningStore.save qualification.
Promotion и active checkpoint epoch/model/SHA CAS выполняются одной транзакцией.
Старые accepted модели не перезаписываются. Unsafe/native reordering rates честно
UNKNOWN, а не ноль: их доказывает отдельный холодный native-сеанс.

## Файлы результатов

- evaluation.json: state COLLECTING/DRAFT/ACCEPTED, reason, candidateAttempt,
  candidateSha, metrics. **modelVersion/modelSha всегда обозначают проверенный
  ACTIVE ACCEPTED checkpoint для следующего холодного батча**, даже если последний
  кандидат DRAFT. Если accepted ещё нет — 0/пустая строка.
- status.json: счётчики, activeModelVersion/activeModelSha, schema/catalog,
  неподдерживаемые режимы.
- report.md: русский итог, история отказов/принятий и per-scope quality JSON.
- exports/model-*.zip: accepted weights.bin, manifest.json, frozen dataset.jsonl,
  retained rich episodes.jsonl. Перед публикацией ZIP веса прочитаны обратно и
  проверены restore/SHA. Нет SQL credentials. Нет accepted — объяснение и exit2.

## Ограничения и budgets

Новые необязательные параметры (default [bounds]):

- maxExamples=4096 [32..4096].
- minAuditPerScope=4 [1..2048].
- minimumImprovementBasisPoints=100 [0..5000].
- evidenceFlushMillis=5000 [100..30000].
- maxLedgerEpisodes=1000000 [100..10000000].
- maxLedgerEvents=262144 [4096..1000000].
- maxTrainingAttempts=256 [1..4096].

retentionEpisodes очищает старые rich JSON с явным richPruned; immutable raw
evidence, frozen datasets и summary/weights истории не удаляются. Их рост имеет
явные пределы: после ёмкости не создаётся новый credit/attempt, требуется экспорт
и новая инсталляция. Пагинация episode export — 8 rows; MLP/dataset максимум4096.
Observation JSON: максимум256KiB, depth32, tokens16384; episode actions максимум4MiB.
Неподдерживаемые режимы всегда NOT_IMPLEMENTED. Рабочая игра не меняется.

## Проверки

Test-first source добавлен до production; главный агент подтвердил RED
`ant -f build.xml compile-tests`, exit1 по отсутствующим новым классам. Дальнейшие
compile/GREEN, SQL/native/STOP выполняет главный агент последовательно.

Планируемые команды после compile-tests (из корня модуля):

```powershell
java -cp '..\build\phantom-test\bin;dist\libs\*' org.l2jmobius.tests.phantoms.PhantomTrainingStoreChecks
java -cp '..\build\phantom-test\bin;dist\libs\*' org.l2jmobius.tests.phantoms.PhantomTrainingStoreChecks <absolute-owned-installation>
```

Первый маршрут DB-free: точный endpoint/account guard, constant/prior quality,
UNKNOWN JSON и malformed JSON. Отдельный focused RED root подтвердил exit1 с
assertion о seed-only новизне; исправление игнорирует metadata seed и сохраняет
consumed identity реального actor/scenario. Его GREEN выполняет root.
Второй — только при attested own SQL и остановленном
GameServer: empty INCOMPLETE, durable ordinal replay refusal, synthetic SQL row
не становится NATIVE credit (удаляется exact key в finally). Он оставляет честные
INCOMPLETE test episodes, не создаёт fake NATIVE. Полный crash/CAS/native proof
нужно выполнить в fresh owned campaign, ещё не доказан этим файлом.
