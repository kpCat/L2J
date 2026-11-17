# Исполнение Goal024

Рабочий baseline: `1b588a3d3f88d369de96bcdfcf9e0997baf62b91`, `feature/phantom-world`.
Этот документ описывает TEST-only mini-batch v1; окончательные факты — в RESULT024.json и отчёте.
Owner gate REQUIRED, production V4 OFF, Goal025 и 480 минут не разрешены.

## Команды

Из фактического Git root, JDK25 / Ant / Windows PowerShell5.1:

```powershell
$module = Join-Path (Get-Location) 'L2J_Mobius_CT_2.6_HighFive'
$training = Join-Path $module 'tools\phantom-runtime\training'
ant -f (Join-Path $training 'Build-TrainingBatch024.xml')
# $history — подтверждённый абсолютный private TEST train, не MAIN/PLAY.
# Boot разрешён только при актуальном offline-green024.json с exact source/class hashes.
powershell -NoProfile -ExecutionPolicy Bypass -File "$training\Training.ps1" -Action ActionValue022 -Root $history -ActionValue022Command Batch024Prepare
powershell -NoProfile -ExecutionPolicy Bypass -File "$training\Training.ps1" -Action ActionValue022 -Root $history -ActionValue022Command Batch024Run
powershell -NoProfile -ExecutionPolicy Bypass -File "$training\Training.ps1" -Action ActionValue022 -Root $history -ActionValue022Command Batch024Report
# Только OFF recovery: никогда не запускает native повторно.
powershell -NoProfile -ExecutionPolicy Bypass -File "$training\Training.ps1" -Action ActionValue022 -Root $history -ActionValue022Command Batch024Recover
```

Build использует копии pinned learning022 classes/runtime-libs, компилирует три TEST-класса
в отдельный learning024-build. Он не вызывает основной jar/OwnerConfig, не меняет dist JAR.
Сборка сама по себе не создаёт offline GREEN и не разрешает GS. Fresh targeted contracts,
020/022, post-V2 и QualityGuard023 должны пройти отдельно. Точные выполненные команды — в отчёте.
Prepare резервирует один quality-slot; повторный Run использованного native root запрещён.
Новый Prepare наследует предыдущий batch-slot; отдельные boot024 claims ограничены двумя.
Активный historical active022 pointer не заменяется автоматически даже после нового commit.

## Порядок и writers

- PS создаёт отдельную staged campaign, копирует immutable ledger, пишет policy/admission,
  create-only reservation и отдельный latest-batch024 selector. Общий batch024 file lock сериализует команды.
- TrainingOwner остаётся byte-identical. Его retained handles, process identity, loopback private SQL,
  resource delta/deadline, exact source hashes и cleanup используются повторно.
- Java preflight создаёт два эпизодных каталога, nonce/ID/cursor/policy и manifest до boot.
  Manifest SHA входит в preflight SHA физической boot reservation. В GS загружается один frozen model.
- На сцену создаются новые actor/profile через существующий reproduceRoster и новый stock monster.
  Canonical settle/drain/store и Runtime.close завершают владельцев; death listeners удаляются.
  Release связывает actor/target/epochs с исходными settled/paid receipts; статические intent queues
  очищаются только перед следующей сценой после закрытия предыдущей.
- Decision/Intent024 публикуются до issued native action; после drain — raw/row/Effect и complete.
  Старый обработчик issued перехватывает ошибки и ставит integrity=false: такой эпизод не получает complete/credit.
- Одна batch seal возможна только после двух original completes. Root BATCH_OWNER_ACK024_V1 —
  совместимое подтверждение для неизменённого supervisor; это не отдельный эпизод и не источник FIT.
- После GS/SQL OFF Java повторно проверяет process absence, retained exit и original provenance.
  Единственный optimizer writer — ActionContinuationV4.prepare в offline JVM: create-only candidate/seal,
  отдельная cold JVM, reauthentication и atomic HEAD под каноническим ledger lock.
- Quality guard читает batch как один slot с двумя физическими episodes. Best verified остаётся null;
  status/report — только проекции. Durable safety/evaluation latch не разблокируется.

## Разбор отказов

До all-complete credit равен нулю. Paid originals сохраняются. Recover сначала доказывает OFF,
затем либо отказывает для partial, либо повторно аутентифицирует полный batch и продолжает
prepare/cold/HEAD идемпотентно. Post-HEAD replay отдельно проверяет оба эпизода и общий batch.
Ни один recovery route не перезапускает неоднозначный native.

Bounded scope exception: один TEST batch family, девять code/test/build файлов плюс новые delivery docs.
Historical task packages, protected resources и production Java API не переписываются.
Read-set расширен точечно для фактического private root, lawful reproduction и lifecycle writer audit;
полные файлы >1000 строк не читались. Фактические отклонения от read/search guideline указаны в отчёте.
