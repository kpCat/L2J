# PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006

## Цель и результат

Довести текущую реализацию M1 до проверяемой границы controlled deploy, исправив класс
native lifecycle/session ownership, а не подбирая удачного бота для GREEN.
Сохранить текущую архитектуру, owned-store protocol b4f1f03 и усиления ea56bef.

Baseline `461a4abe32be4aa08532b8417a6147684a8889c6`. Перед работой read-first: локальные AGENTS/инструкции, branch/HEAD,
рабочий и staged diff, локальные RESULT/SUMMARY/HANDOFF предыдущей задачи.
Не реконструировать проект с нуля. Проверить именно перечисленные source hotspots.
Если HEAD изменился — сравнить с baseline; не откатывать. Совместимые новые исправления
учесть. Конфликт требований/чужих изменений в тех же строках — BLOCKED, без затирания.

## Обязательные workstreams внутри одной задачи

1. **Native ownership**: policy-missing fail-closed; expected-policy stop; отсутствие
   stale-P1-stop → removal-P2; согласованность обеих native pools и Session.
2. **Failure evidence**: первое исключение не перезаписывается; вторичные не маскируют
   primary; exact identity/epoch/phase/hook и полный ограниченный stack сохраняются
   при retry, abort и удалении runtime entry.
3. **Terminal travel**: исчерпывающий outcome и bounded feedback, без бесконечного
   пересоздания одинакового Journey после непризнанного terminal reason.
4. **Owned-store/native quiescence**: реальные TEST-сценарии dispatch vs delayed native
   effects; producer fix только после доказанного RED. Нельзя ослаблять mismatch guard.
5. **M1 acceptance**: attributed native progress вместо флагов; FAILED/admission-closed
   cohort никогда не исчезает из знаменателя и не получает PASS.

## Среда и запреты

Только High Five. Windows 11 / JDK25 / Ant / MariaDB 127.0.0.1:3308.
PLAY `l2jmobiush5_localplay3` — SELECT/read/decode. Никаких ручных UPDATE/DELETE/reset,
телепортаций фантомов, переключений state или выпуска ownership ради проверки.
Guarded TEST `l2jmobiush5_phantom_test` — существующий gate/fixtures/cleanup.
Текущий PLAY JVM, JAR и конфигурацию не менять и не перезапускать.
1280 READY / 8720 RETIRED сохраняются; 10000 не gate, MaxScheduled не уменьшать.
Никаких M2/PvP/social features. Existing actual-attacker path только regression/diagnostics.
Не писать второй combat engine. Не менять geodata наугад. Не включать invulnerability.
Не менять balance, damage, EXP, spawn, cooldown ради прохождения acceptance.

## Git и объём разрешения

Разрешены read-only status/diff/log/show/ls-tree/rev-parse/ls-remote/fetch нужной ветки.
Запрещены add ., reset, clean, stash, rebase, force; чужой worktree/diff не трогать.
Для файлов этой задачи разрешены exact-path add, commit и обычный push в
feature/phantom-world после GREEN/review. Сначала проверить staged diff на чужие hunks.
Если нужный файл содержит чужие изменения — не commit всего файла вслепую; сохранить
их и отделить только свои hunks безопасным способом или BLOCKED при неоднозначности.
Список допустимых production/test путей — SOURCE_MAP.tsv, колонки role/permission.
Новые вспомогательные классы только явно названные в DESIGN/PLAN. Не менять DB schema.

## Последовательность

Read-first отчёт → реальные RED на текущем коде → минимальная интеграция → focused GREEN
→ broad regression → review/исправления → exact-path commit/push → clean build из
точного committed SHA → публикация читаемого RESULT/HANDOFF → STOP.
Если нужен ещё один producer fix того же класса, сделать его в этой задаче, не выдавать
промежуточную «готовность» после одного зелёного теста.

## Итог

RESULT должен содержать SHA, фактический diff, команды/exit codes, RED→GREEN по каждому
finding, сверку реальных native TEST с моделями и открытые риски. REPORT/RESULT/HANDOFF
должны действительно быть доступны в remote, а не только в .phantom-local.
Статус задачи не равен M1 GREEN. `READY_FOR_CONTROLLED_DEPLOY; M1_OPEN` допустим только
после ACCEPTANCE.md. При провале обязательного критерия — BLOCKED с точной причиной.
