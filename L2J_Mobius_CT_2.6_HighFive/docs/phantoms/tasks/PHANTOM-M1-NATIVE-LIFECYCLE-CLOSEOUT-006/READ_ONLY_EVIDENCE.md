# Проверенное и непроверенное

## Независимо проверено через GitHub connector

HEAD `461a4abe32be4aa08532b8417a6147684a8889c6`; название commit: `fix(phantoms): expose bounded cleanup failure diagnostics`.
Parent `51f092538a7d4d85e92305926a1f9c935dd8a18b`, ранее
`ea56bef1381c05b70f6e5da72e07004d53655be7`, затем
`b4f1f03d7407897f50bd3131f40d6e19011116e1`.

Published delta от parent: 8 файлов, +339/-9. В сообщении пользователя показан локальный
список 11 файлов, +482/-85. Это разные объекты проверки, не свидетельство обмана.

Опубликованы:
- java/.../phantoms/PhantomSystem.java
- java/.../phantoms/player/PhantomMaterializationService.java
- java/.../phantoms/player/PhantomMaterializedPlayer.java
- test/.../PhantomProductionMaterializationSuite.java
- test/.../PhantomBackgroundSuite.java
- test/.../LocalPlayPilotSuite.java
- docs/.../PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/Run-M1RuntimeHandoff.ps1
- docs/.../continuation-observer-closeout/Test-M1ObserverOffline.ps1

Путь предыдущего полного отчёта
`docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/continuation-observer-closeout/audit-closeout-20261002/SUMMARY-current-20261003.md`
вернул Not Found при чтении exact SHA; каталог также не получен. Codex обязан прочитать
свою локальную копию и опубликовать sanitised evidence, не переписывая историю отчёта.

## Только сообщено Codex / пользователем

cleanup23/23; Pilot7/7; owned C/D PASS; offline PASS; clean JAR; review NONE.
Runtime110/142/175 FAILED, admissionclosed, pendingintentотсутствует; firstexceptionUNKNOWN.
Независимого запуска этих native тестов или чтения локальных timelines у автора ZIP нет.
Не превращать сообщения в утверждение «независимо воспроизведено».

## Объём source-аудита

Проверены production composition, scheduler retained-failure behavior, materialized
cleanup/leases, owned-store boundary prepare/finalize/resume, native Player store/tasks,
AutoPlay/AutoUse loops/start/stop, visible Session/travel/decision/history, retention,
M1 runner/census, существующие production-materialization и native executor tests.
SOURCE_MAP содержит узлы и inspected ranges/symbols, не обещание ревью всего сервера.
Не выполнены полный SQL transaction audit, запуск geodata на координатах пользователя,
Windows UI, чистая сборка JDK25, native TEST/PLAY/crash acceptance.

## Важные отрицательные выводы

- ACTION_ADMISSION_CLOSED — не самостоятельный root cause.
- Геодата не доказана причиной FAILED110/142/175.
- Усиленный linked executor TEST уже использует production-style scheduler/handler и
  native attributed damage; нельзя объявлять весь старый wiring bypass всё ещё открытым.
- Retention уже учитывает moving/attacking/casting/teleport/combat/admitted actions.
  Нельзя описывать реализацию как вообще не имеющую quiescence/retention защиты.
- Отсутствие pending intent не исключает post-FINALIZE failure.
- Не найдено оснований сносить архитектурные слои или owned-store b4f protocol.

Источники: immutable GitHub URLs и blob SHA в SOURCE_MAP.tsv. Фрагменты PATCH_GUIDE —
выдержки source этого baseline. Proposals — новые проектные решения, не цитаты source.
