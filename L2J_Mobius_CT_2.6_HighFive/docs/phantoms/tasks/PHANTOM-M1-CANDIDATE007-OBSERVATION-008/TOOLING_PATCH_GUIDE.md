# Только транспорт/сбор данных — не production patch

## CURRENT: Build-LocalPlay.ps1

```powershell
$presetValues = @{
    Balanced = @{ Population = 120; Active = 24; Materialized = 32 }
    Lively = @{ Population = 160; Active = 32; Materialized = 48 }
    Stress = @{ Population = 240; Active = 48; Materialized = 64 }
}
# Далее PhantomPopulationTarget/ActiveTarget и несколько gameplay toggles меняются.
```

PROBLEM: это другой эксперимент, а не исходный1280 READY мир.
REQUIRED SHAPE: task-local Prepare-Observation копирует frozen datapack/JAR и существующую
private configuration; меняет только утверждённые isolation/Pilot/cap keys. После проверки
JDBC/loopback/hashes пишет truthful flat local-play.json. Не редактировать Build-LocalPlay
и не убирать его safeguards. Свежая сборка из snapshot commit — отдельно.

## CURRENT: Stop-LocalPlay.ps1

```powershell
if ($process.CloseMainWindow()) { $null = $process.WaitForExit($GraceSeconds * 1000) }
if (-not $process.HasExited) { Stop-Process -Id $state.pid -Force -ErrorAction Stop }
```

PROBLEM: «контролируемый PID» не означает graceful shutdown.
REQUIRED SHAPE: task-local stop для исходного PLAY не имеет force fallback. Он использует
действительно существующий graceful канал и затем проверяет exit/ports. Ctrl-C допустим
только после проверки выделенной консоли; глобальный Ctrl-C/shared console запрещён.
На неподтверждённом graceful path — BLOCKED без изменения исходного runtime.
Force для зависшего одноразового observation JVM отдельно маркируется как failed shutdown.

## UI correction scope

Разрешено читать L2.cmd, фактически вызываемые им scripts и текущий лог/скриншот.
Поправить до2 непосредственно участвующих текстовых файлов: HWND/PID selection,
окно login/Confirm/Start/disconnected, фокус/ввод, распознавание уже вошедшего клиента,
скриншот выбранного окна и transport ввода fresh arm/off. Backup originals обязателен.
Никаких бинарных patches клиента, Java hooks, network interception и подделки REAL_LOGIN.

Сначала Status/screenshot → конкретная ошибка → маленькая правка → повтор той же
операции → проверка server state. Не расширять UI-framework и не запускать его аудит.
До1 повтора observation только после доказанной транспортной ошибки и в общем бюджете.
Если нужна третья исходная UI-зависимость/новый тяжёлый стек — BLOCKED_TOOLING_SCOPE.

## Уже доступные данные, не новый сенсор

Candidate PlayerNativeEvidence.scalarMap отдаёт nativeDamageSequence, nativeKillSequence,
nativeRewardSequence, nativeTargetSequence, nativeFarmCycleSequence, nativeExpGained,
nativeSpGained, nativeLootSequence, nativeEvidenceObjectId/Epoch/Overflow/SampleNanos.
Сохранять raw snapshots и считать только разности на том же exact lifetime.

Текущий farmCycleSequence требует damage + kill + положительные EXP и SP + следующую
цель. На NPC с законным0 SP он может не отражать полный игровой цикл. Не чинить сенсор
ради008: проверить фактическую NPC reward definition, сохранить независимые кадры/факты
и указать ограничение доказательства. Нет фактов5 отдельных циклов — нет PASS пяти циклов.

Не вызывать sensor.damage/reward/loot руками и не считать helper-model runtime evidence.
