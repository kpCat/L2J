# GIT_AND_PRESERVATION017 — рабочая граница

## Разрешённые изменения — *предлагаемый максимальный allowlist для плана Codex*

Только существующие файлы High Five; Codex сначала проверяет необходимость каждого и предоставляет владельцу `IMPLEMENTATION_PLAN017.md` с точным **фактическим** подмножеством:

1. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — точная диагностика/воспроизведение terminal rewards, без подмены native.
2. `java/org/l2jmobius/gameserver/phantoms/learning/TerminalRewardReceiptV1.java` — ТОЛЬКО при доказанном баге и отдельном owner approval на любые изменения семантики; сохранение исторических codecs обязательно.
3. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java` — bounded read-only sufficiency/trace, если нужен; никаких `INSERT/UPDATE` на исторические записи.
4. `java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java` — отчёт о достаточности, если нельзя получить существующим API; не менять objective/thresholds.
5. `tools/phantom-runtime/training/TrainingOwner.ps1` — version-aware phase validation `DONE016`, safety/closure.
6. `tools/phantom-runtime/training/Training.ps1` — idempotent NO_FIT closure, guard no rerun.
7. `tools/phantom-runtime/training/Test-Training.ps1` — focused no-fit/recovery/phase regression.
8. `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java` — Party receipt/selection offline contracts.
9. `build.xml` — отдельный targeted017 test target, если необходим.

Другие исходники — не менять без отдельного явного согласования. Допускаются новые docs в `docs/phantoms/tasks/PHANTOM-LEARNING-017-PARTY-REWARD-AND-CYCLE-CLOSURE/`, отчёт в `docs/phantoms/reports/` и одна новая запись `COMBAT-AI-ROADMAP.md`. **Не создавать вторую систему генерации, training framework или parallel SQL.**

## Git правила

- `git fetch` и сверка с текущим origin до изменений и до push. Сохранять все параллельные Studio commits. MAIN repo с чужим dirt не приводить насильно к чистому.
- `SOURCE004` и `TEST006` проверяются фактически: если там uncommitted изменения или чужой overlay, внешняя byte backup SHA256, стоп при неоднозначности.
- Никаких `reset --hard`, `git clean`, force push, rebases, переписывания чужой истории, broad stage/stash.
- Scoped commit + обычный non-force push **только** `feature/phantom-world`. Самостоятельно readback GitHub HEAD/parents/diff/полные тела всех изменённых исходников и evidence. Совпадение с локальным отчётом не заменяет readback.
- Приватные SQL credentials, сырые Player inventories, owner nonces, новые модельные веса, личные пути/secret files не коммитить.

## Сохранность после запуска

- Own TEST: отдельный процессный owner, SQL/GS start limits, held exit, OFF и свободные порты.
- Old016: 51 `settled`, 399 original paid events, 27 existing terminal, old `fit-result.json`, `checkpoint016`, director FIT failure, оба frozen поколения; без изменения raw/manifest/hash/history.
- Old015/014 и все предыдущие results, acceptedTEST2, DRAFT14, optimizer903/223, pointer12, 183 rows, Geo203/protected207, MAIN/PLAY/Studio не менять.
- Новые actual Party diagnostic evidence — отдельные immutable файлы с SHA; не переписывать `excluded-actions.jsonl` 016, даже если теперь известны причины.
