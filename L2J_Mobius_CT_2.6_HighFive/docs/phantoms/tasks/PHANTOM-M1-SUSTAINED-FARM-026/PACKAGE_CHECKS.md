# PACKAGE_CHECKS026

Проверено координатором в рабочем контейнере:
- reference Java-код компилируется OpenJDK21.0.12.1; это Java21-compatible subset,
  сервер должен собираться Codex именно JDK25, здесь он НЕ собирался;
- semantic RED reference table на stub HOLD_IDENTITY, затем GREEN1039assertions;
- read-only Python summary:13 unit tests GREEN, включая false-string, no mutation,
  duplicate rejection, no inferred root/no computed product PASS;
- воспроизводимая структураZIP и manifestSHA256, UTF-8 decode, no path traversal,
  нет compiled artifacts/secrets/production overwrite paths;
- docs self-review: exact requiredbase, conditional scope,6h budget, no user gate,
  legacy25persistence preserved, неразобранные причины не объявлены доказанными.

Это проверки task-пакета и reference, НЕ native server tests, НЕ M1 progress.
Codex обязан получить genuine native RED→GREEN и natural full-server outcome.
