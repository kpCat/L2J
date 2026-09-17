# Вылет 17.09.2026 — сбор фактов без Codex

## Что уже есть

По выводу пользователя: один игровой лог обновлён в 20:00:15 и имеет 34 516 байт; имена обрезаны табличным выводом. В runtime не найдено `hs_err_pid*.log` и `.hprof`. Поиск Windows Application за три часа не вернул строк, но ошибки команды были подавлены. Само содержимое сегодняшних логов пока не получено.

Это не подтверждает ни OOM, ни JVM crash, ни вину Talking Island. Также не доказано, что исчез именно GameServer, а не только соединение/клиент. Запись LoginServer от 24.07.2026 с отказом аутентификации БД не привязана к этому событию.

## Сначала сохранить

До нового запуска скопировать `runtime/game/log` и `runtime/login/log` в отдельную папку с датой; не чистить и не пересобирать runtime. Не присылать `Database.ini`, пароли или heap dump. В логах тоже проверить случайные секреты.

## Команда ниже

Она только читает процессы/порты/логи и создаёт новый TXT в профиле пользователя. Ничего не перезапускает и не редактирует. Прямо на Windows пользователя здесь не исполнялась; это диагностическая инструкция, не отчёт о выполнении.

Сохранить дату и наличие/отсутствие Java сразу после нового дисконнекта. Если сервер уже перезапущен, текущие PID не восстановят факт его прежнего завершения.

```powershell
$R = 'C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\artifacts\local-play\runtime'
$Out = Join-Path $env:USERPROFILE ('L2-crash-check-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.txt')

& {
    '=== TIME ==='
    Get-Date -Format o

    '=== JAVA PROCESSES ==='
    try {
        $p = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop)
        if ($p.Count -eq 0) { 'NO_JAVA_PROCESSES' }
        $p | Select-Object ProcessId, ParentProcessId, CreationDate, ExecutablePath, CommandLine | Format-List
    } catch { 'PROCESS_READ_ERROR: ' + $_.Exception.Message }

    '=== LISTENING PORTS ==='
    try {
        $ports = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in 2106,9014,7777)
        if ($ports.Count -eq 0) { 'NO_L2_LISTENERS' }
        $ports | Select-Object LocalAddress,LocalPort,OwningProcess | Format-Table -AutoSize
    } catch { 'PORT_READ_ERROR: ' + $_.Exception.Message }

    foreach ($role in 'game','login') {
        "=== $role LOG INDEX ==="
        $dir = Join-Path $R ($role + '\log')
        try {
            $logs = @(Get-ChildItem -LiteralPath $dir -File -Recurse -ErrorAction Stop |
                Where-Object { $_.Length -gt 0 -and $_.Extension -notin '.lck','.zip','.gz','.hprof' } |
                Sort-Object LastWriteTime -Descending)
            $logs | Select-Object -First 15 Name,LastWriteTime,Length,FullName | Format-List
            foreach ($f in ($logs | Select-Object -First 3)) {
                "=== TAIL: $($f.FullName) ==="
                Get-Content -LiteralPath $f.FullName -Tail 180 -Encoding UTF8 -ErrorAction Stop
            }
        } catch { 'LOG_READ_ERROR: ' + $_.Exception.Message }
    }
} | Out-String -Width 4096 | Set-Content -LiteralPath $Out -Encoding UTF8

Write-Host ('REPORT: ' + $Out)
```

Прислать созданный TXT целиком. Не только grep по `ERROR`: штатная остановка, потеря LoginServer, сообщения PacketLogger и факт конца лога важны не меньше исключения.

## Как читать результат

- Нет старого Java PID и нет game listener — подтверждается исчезновение процесса/сервиса; ещё нужен exit code/консоль/причина.
- Java PID тот же, порт слушается — гибель JVM не подтверждается; разбирать клиент, соединение и возможную остановку обработки.
- PID сменился — был новый запуск; установить кто и почему перезапустил.
- PID существует, но только слушающий порт — не доказательство здоровья игровой обработки. При повторном зависании снять thread dump подходящим `jcmd` того же JDK и проверить packet/worker очереди.

Текст native crash по умолчанию может оказаться в рабочем каталоге или `%TMP%`/`%TEMP%`. Heap dump без заранее включённого флага обычно вообще не создаётся. Отсутствие Windows Application events не исключает обычное завершение Java, OOM отдельного потока или внешнюю остановку.

## Следующий запуск при недостатке свидетельств

Контрольная точка LIVE-001-B добавляет сохранение stdout/stderr и exit code в существующий launcher, сохраняя его DB/PID guards. Не запускать другой самодельный GameServer параллельно. Сначала отдельная сессия с прежними численностью/настройками, затем один контролируемый повтор маршрута Talking Island.

JVM-флаги для контролируемого диагностического запуска (добавляются к текущим, не вместо остальных):

```text
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=./log
-XX:ErrorFile=./log/hs_err_pid%p.log
-Xlog:gc*:file=./log/gc-%p.log:time,uptime,level,tags:filecount=3,filesize=10M
```

`./log` должен существовать и быть доступен на запись. Флаги нужно проверить на фактическом JDK до игрового запуска. Не менять поколение Java/collector во время расследования. Дампы и command line могут содержать конфиденциальные данные — не публиковать автоматически.
