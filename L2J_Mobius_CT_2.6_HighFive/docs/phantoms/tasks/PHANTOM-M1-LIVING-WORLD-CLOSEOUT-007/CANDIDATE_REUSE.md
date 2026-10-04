# Сохранённый candidate006: не терять, не принять вслепую

Из опубликованного RESULT:
`C:\Users\ZBook\.codex\worktrees\m1-native-closeout-006\L2J_Mobius` — ранее созданный
isolated worktree. Проверить его наличие/status/HEAD; не рассчитывать, что он clean.
Remote artifact:
`docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006/evidence/CANDIDATE.patch`.
Его Git blob в base007: `3ff1c981e1f63e7f1e04cab99889bbdcf77d7cb0`.

1. Read-only сверить CANDIDATE.patch, CHANGED_FILES.tsv, RED_GREEN.tsv, RESULT, REVIEW,
   HANDOFF, COMMANDS и наличие нового PhantomCleanupIncident.java.
2. Создать task007 worktree от d924a7d2…; скопировать только task007 docs в него.
3. Сохранить inventory местных candidate006 правок и их хеши. Не брать private config,
   SQL LF normalization, copied geodata, binaries, node/tool caches.
4. Patch имеет U0 hunks. Применение в новом worktree разрешено только после
   `git apply --check --unidiff-zero <exact-candidate-path>` и сверки path ledger;
   затем `git apply --unidiff-zero <exact-candidate-path>`. Это перенос candidate для
   доработки, НЕ разрешение deploy. При несовпадении — перенос нужных hunks после
   сравнения, без overwrite старого worktree и без reset/clean.
5. Read candidate source целиком в затронутых methods, проверить baseline/current diff.
   Нельзя считать P/E исправленными только потому, что они есть в patch.

## Сохранить и завершить

P: sticky managed origin, exact-owner manager stop, pair health/repair, session rollback.
E: primary/suppressed preservation, first/latest incident, bounded detached archive.
Tests: native fixtures, правильный mage bootstrap через existing MasterHandler,
focused Ant target и negatives grading. Структуру тестов лучше вынести из разрастающегося
PhantomBackgroundSuite в разрешённую focused suite без переписывания исторических тестов.

## Обязательно переработать/доказать

Q: candidate runPhantomNativeAction захватывает lease только при входе в completion.
Это не учитывает уже поставленный callback, который ещё не начал работу; blanket return
после закрытия admission может уничтожить legitimate earned continuation. Его нельзя
расширять такими же return в Quest.giveItems. Нужны reserve-before-submit + inheritance.

P ordinary: finishAutoPlayRegistrationStop делает generation check, затем SQL/follow.
Смена generation между check и effect не устранена. Предпочтительно не менять ordinary
ветку сверх необходимого: Phantom-specific registration path без offline SQL; shared
изменение оставлять только с serializable effect design и реальным race regression.

E: сбрасывать exact cleanup hook при переходе phase; AFTER_ACTION_ADMISSION не должен
оставаться именем текущего hook во время ACTION_DRAIN. Outer materialization-finally
Error audit остаётся обязательным.

T/A: 006 создал RED, но НЕ завершил travel/grading fixes. Не потерять эти workstreams.
Baseline P01 fixture была некорректной: нужен валидный observer/target и настоящий RED.

## Не пересобирать всю историю доказательств

Старое evidence оставить ссылками. Повторить только нужные RED controls и новые native
coverage; финальный интеграционный GREEN получить одним корректным запуском из итогового
candidate. 17/18 с именем каталога green — RED. Build tests != clean committed fix build.
