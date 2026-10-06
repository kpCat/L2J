# Минимальная диагностика продолжения

Не строить новый flight recorder. Reuse TASK014 ring + includeCensus TASK020/021. Trace только для<=4 natural watched lifetimes. Native sensor уже существует; добавляем несколько причинных scalar fields, а не ещё одну копию состояния персонажа.

## Обязательные факты

На baseline, после первого reward/kill, при stall:
- captureUtc, serverNanoTime, profile/object/epoch/goal revision, native sensor epoch;
- decisionSequence, decisionAgeMillis, runtimeState, inFlight/last enter/last exit (если доступны);
- native autoPlaying, оба exact registration membership, sessionCurrent/policy generation;
- last tick acquire outcome/reason, last stop reason/caller/time;
- owner state/current/sealed/failure, pendingOwnedStore, first native incident;
- bounded outstanding/pendingTimers snapshot: kind, count, oldest age, ancestor/earned;
- casting/intention/skill/target, HP/MP/maxima, exact target NPC+spawn generation;
- phase/since/deadline, firstUnprovenReason/time, native actual event counters;
- actual Player EXP/SP/level и inventory deltas, отдельно от sensor counters.

Ограничения: no DB/file I/O под native monitor, no unbounded profile retention. Scalar reads доступны даже при stall; не брать lock в цикле ожидания ради snapshot. Неполное/неатомарное чтение маркировать, не достраивать.

## Когда снимать стеки

Если при наличии due work sequence/tick completion не продвигается10 секунд ИЛИ cast на мёртвом/пустом target остаётся без callback completion: один пакет из3thread dumps с интервалом2 секунды. Не1dump каждые2секунды весь run. Преждевременный stop регистрации также триггер пакета. До завершения JVM не терять первую wait-for картину.

Только exact принадлежащий задаче GamePID, после проверки роли/папки/creation time:

```powershell
& "$env:JAVA_HOME\bin\jcmd.exe" $GamePid help Thread.print
& "$env:JAVA_HOME\bin\jcmd.exe" $GamePid Thread.print -l
```

Timeout каждого diagnostic process10s, без force JVM. Не использовать jcmd0 или имя класса, которое заденет чужие процессы. При attach denied записать ошибку; не менять security/privileges молча.

Если thread dumps недостаточно, разрешён один JFR на<=10мин/64MB, settings=profile, на том же owned GamePID. Это дополняет причинный trace, не заменяет его. Проверить доступные команды через jcmd help; не устанавливать сторонние profiler/IDE. .jfr/thread dumps хранить локально; публиковать small sanitized summary и hashes, без внешних credentials.

Официальный справочник JDK25: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html

## Форма причинного вывода

`first event + exact owner → конкретный stop/block/exception → незавершённый callback/нет следующего tick → старый reason/CAST → horizon expired → cleanup consequence`.

Проверить, что каждое ребро подтверждено. Сходство по времени не равно общей причине. Если REGEN horizon истёк после остановки исполнения — это вторичный сигнал, не доказательство ошибки sensor.
