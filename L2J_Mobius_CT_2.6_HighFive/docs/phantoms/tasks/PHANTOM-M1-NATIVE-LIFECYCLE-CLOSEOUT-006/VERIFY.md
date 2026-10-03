# Проверки и сборка

## Read-first test gate

Сначала проверить `.phantom-local/Database.test.ini` и schema-manifest существующим
validator. Не печатать пароли в RESULT. База обязана быть `l2jmobiush5_phantom_test`,
не PLAY. Отсутствие manifest → BLOCKED/существующий approved test preparation, не fallback.
JDK: `java -version`, `javac -version`; Ant `ant -version`.

## Actual regression targets (названия проверены в baseline build.xml)

В одном запланированном Ant invocation с approved отдельным output выполнить:

```text
phantom-production-materialization-test
phantom-server-shutdown-handoff-test
phantom-activity-scheduler-test
phantom-background-transaction-test
phantom-background-lifecycle-test
phantom-background-materialization-abort-test
phantom-background-quiescence-test
phantom-background-recovery-teleport-test
phantom-background-server-integration-test
phantom-m1-runtime-handoff-test
phantom-localplay-pilot-test
```

Добавить реальные новые suite selectors/target из этой задачи и точные owned C/D selectors
после прочтения previous local RESULT. build.xml/PhantomTestLauncher changes разрешены
только для этих focused tests. Существующие aggregate targets могут разделять dependencies;
Ant выполнит dependency один раз в одном invocation. Не запускать каждый target отдельно
с compile-tests, который заново очищает build.test/reports. До нового запуска copy reports.

Дополнительно: существующий Test-M1ObserverOffline.ps1 + новые pure grading fixtures;
production materialization performance-smoke; bounded native-pool concurrency stress
после deterministic tests; memory/registration retention proof без million-profile test.
Не объявлять timing/performance PASS без измерений и сравнения baseline.

`ant verify` охватывает много исторических milestones — не default для этой задачи.
Q tests не должны превращаться в длинный PLAY soak. Все native guarded fixtures удалить
штатным TEST cleanup, TEST failures не «лечить» production SQL.

## Clean committed build

Production fix commit сначала прошёл TEST/review и опубликован. Build из точного commit
в отдельной чистой export directory approved workflow (`git archive` read-only допустим),
без git clean/reset и без затрагивания shared ../build чужой задачи. Повторно сверить
included library/data/runtime dependencies, JDK25, кодировку UTF-8 и compiled source SHA.
Не переносить PLAY database config в guarded test environment.

Зафиксировать codeSHA, build command, exit code, JAR SHA256, build tree manifest, external
runtime deps manifest. Clean JAR остаётся артефактом, не deploy. Не перезапускать PLAY.
Если после build изменился production source — старый JAR invalid, повторить exactbuild.

## Наши модели в proofs/

`javac M1BoundaryReproducer.java`; legacy run ожидаемо exit1 (8 negative failures),
`java M1BoundaryReproducer --fixed` exit0 (11/11). Это только control-flow model.
Эти команды не доказывают исправность L2J, не требуют JDK25/DB и не заменяют ни один
перечисленный actual production TEST или acceptance gate.
