# Goal 030C2A — Clan leader directives and obedience

## Status

**SUCCESS**

Goal 030C2A реализован на exact parent `b50f2de35d54f34a682fa1166acfcebaf3b2a71b` в ветке `feature/phantom-world` с upstream `origin/feature/phantom-world`. Финальные обязательные gate-команды выполнены в заданном порядке, затем ровно один раз выполнена цель `jar`.

Текущее состояние roadmap после реализации:

- Goal 030C1 — `ACCEPT`;
- Goal 030C2A — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 030C2B — `NOT_STARTED`;
- Goal 030 — `IN_PROGRESS`;
- CP2 — не начат;
- следующий bounded slice — Goal 030C2B.

## Summary

Добавлен ограниченный контур клановых директив лидера через штатный канал `CLAN`. Единственным глобальным потребителем `ChatObservation` остаётся `PhantomConversationService`; он передаёт только подходящий `CLIENT_CHAT` в новый side-channel. Сервис директив проверяет фактическое членство и актуального лидера через живые `Player`/`Clan`, распознаёт строгий XML-каталог команд, применяет социально обусловленное решение и публикует уже существующие сигналы scheduler без прямой материализации.

Поддержаны три директивы:

- `ASSEMBLE` → `ACTIVE`, TTL 120 секунд;
- `STANDBY` → `WARM`, TTL 300 секунд;
- `DISMISS` → снятие только собственного сигнала директивы.

## Read-first pass и локальные аналоги

Прочитаны обязательные `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, текущий `TASK.md`, отчёт Goal 030C1, roadmap/release gate, build/test launcher и затрагиваемые production/test классы. Родительский `AGENTS.md` выше рабочего модуля, корневой `README.md` и актуальный отдельный code-map не найдены; повторный поиск не выполнялся.

Переиспользованы локальные паттерны:

- `PhantomConversationService` как единственный глобальный chat observer;
- строгая XML-загрузка и fail-closed validation из социальных каталогов;
- `PhantomSocialService` и catalog-driven modifier/event effects;
- scheduler signal ownership и lifecycle cleanup;
- headless outbound session seam вместо fake/null-network `GameClient`;
- guarded integration fixture и штатный `PhantomTestLauncher`.

Учтены ограничения High Five/JDK 25/Ant, запрет изменения других хроник, отсутствие новых библиотек, отсутствие отдельного worker/timer/queue, запрет прямой материализации и неизменность DB schema. Непроверенными остаются независимый review Goal 030C2A и будущий Goal 030C2B; они не входят в текущий scope.

## Changed files

- `build.xml`
- `dist/game/data/phantoms/README.ru.md`
- `dist/game/data/phantoms/social/high-five-social-v1.xml`
- `dist/game/data/phantoms/clan/high-five-clan-directives-v1.xml`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/PHANTOM_RELEASE_GATE.md`
- `docs/phantoms/reports/030c2a-clan-leader-directives-obedience.md`
- `java/org/l2jmobius/gameserver/model/actor/Player.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java`
- `java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialCatalog.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDirectiveModel.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDirectiveIngressPort.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDirectiveCatalog.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDirectiveService.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomSocialHumanizationGoal030BSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `test/java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDirectivePolicyGoal030C2ASuite.java`
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomClanDirectiveIntegrationGoal030C2ASuite.java`

## Architecture decisions

1. Native `ChatClan` не изменён. Реальная доставка сообщения клану остаётся штатной, а директива наблюдается через уже существующий глобальный observer.
2. `PhantomConversationService` получил source-compatible no-op side-channel для старых конструкторов и передаёт `CLIENT_CHAT` до собственного channel filter. `GENERATED` origin игнорируется сервисом директив.
3. Получатель определяется по exact materialized profile ID за O(1); принадлежность отправителя/получателя и leader object ID проверяются по живому `Clan` при каждом событии. Перенос лидерства вступает в силу без отдельного кэша.
4. В `Player.isInOfflineMode()` внесён минимальный seam: активная headless outbound session с `null` client не считается offline shop/disconnected session. Это согласует уже существующий `isOnlineInt()` с неизменённым native `ClanMember.isOnline()` и не создаёт fake `GameClient`.
5. Решение `ACCEPT`/`DEFER`/`REFUSE` детерминированно учитывает loyalty, trust, respect, competence, reliability, anger, rivalry и hostility через catalog-driven modifier с clamp `[-3000, 3000]`.
6. Scheduler получает сигналы с источником `clan.directive.<clanId>`. Ledger хранит только ownership этого сервиса; `DISMISS` и `close()` не снимают чужие источники.
7. Accepted/refused эффекты записываются штатными social events с `SAME_CLAN`, exact leader subject ID и bounded TTL. Обязательный прежний `clan.member.expelled` также переведён в fail-closed required catalog contract.
8. Lifecycle сервиса встроен в startup/failure/shutdown `PhantomSystem`; собственных потоков, таймеров, очередей и фоновых scan нет.

## DB и migrations

Новых таблиц, migrations и production-записей в БД нет. Policy suite DB-free. Integration suite использовал только существующую allowlisted базу `l2jmobiush5_phantom_test` через guarded test config/manifest; provisioning не выполнялся. Рабочая база `l2jmobiush5` тестами не изменялась.

## Configs и data contracts

Новый strict XML-каталог: `dist/game/data/phantoms/clan/high-five-clan-directives-v1.xml`. Ограничения: не более 8 directive kinds, не более 64 aliases, normalized alias не длиннее 48 символов; XML parser защищён от XXE. Normalization выполняет NFKC, lower-case, замену `ё` на `е` и bounded collapse punctuation/whitespace без regex hot path.

Social XML дополнен событиями `clan.directive.accepted` и `clan.directive.refused`, а также modifier `clan.directive.obedience`. Контракт, aliases, tuning, authority и effects документированы в `dist/game/data/phantoms/README.ru.md`.

## Tests and results

Финальная immutable последовательность после последнего изменения production/test/status docs:

1. `phantom-clan-directive-policy-goal030c2a-test` — **PASS**, 8/8, seed `30003032`, Ant total 15 s.
2. `phantom-clan-directive-integration-goal030c2a-test` — **PASS**, 5/5, seed `30003033`, Ant total 27 s.
3. `phantom-clan-affiliation-humanization-goal030c1-test` — **PASS**, 7/7, seed `30003031`, Ant total 28 s.
4. `phantom-conversation-understanding-test` — **PASS**, 2/2, seed `20002001`, Ant total 15 s.
5. `phantom-social-humanization-goal030b-test` — **PASS**, 8/8, seed `30003020`, Ant total 16 s.
6. `jar` — **PASS**, единственный вызов, Ant total 16 s; `GameServer.jar` и `LoginServer.jar` скопированы в `dist/libs`.

Во всех компиляциях присутствовали только два прежних warning о deprecated `System.runFinalization()` в Goal 029 test suites. Новых warnings не добавлено.

До финальной последовательности выполнены bounded diagnostic `compile`, `compile-tests`, policy 8/8 и integration 5/5. Диагностические integration-попытки выявили и локально устранили три проблемы fixture/seam: загрузку всего `MasterHandler` с несовместимым source-8 legacy `var`, исключение headless player из native clan online delivery и ошибочно указанное имя уже существующего predicate. После исправлений финальные gate не перезапускались вне требуемой последовательности.

## Static checks

- `git diff --check` — PASS;
- хвостовые пробелы во всех новых файлах — не найдены;
- mojibake-маркеры в изменённых файлах проверены — не найдены;
- escaped Cyrillic в изменённых файлах проверены — не найдены;
- временные `*.goal030c2a.*` артефакты перед gate — не найдены;
- другие хроники не затронуты.

## Performance measurements

Формальный benchmark не требовался и не запускался. Контур event-driven: одно наблюдаемое chat-событие, exact O(1) lookup materialized recipient и bounded catalog/social/scheduler work. Integration подтвердил отсутствие direct materialization (`materializeCalls=0`), отсутствие оставшегося directive ownership после shutdown и отсутствие отдельного worker/timer. Глобальных обходов phantom population и DB-запросов в hot path нет.

## Process deviations and recovery

`apply_patch` вызван ровно один раз и не применил ни одного изменения: операция была отклонена ACL среды (`apply deny-read ACLs`). Повторный вызов не выполнялся. Разрешённый fallback использовал небольшие exact-anchor PowerShell patches через временные файлы и атомарный `Move-Item`. До первой мутации две fallback-команды завершились parser error; позднее одна команда вставки в `build.xml` и одна formatting-команда также завершились ошибкой. Все затронутые файлы после этого перечитаны и исправлены.

При bounded восстановлении line endings `Player.java` одна попытка overwrite была отклонена reviewer и не изменила файл. Затем read-only HEAD baseline использован только для восстановления исходных delimiters с проверкой, что остаётся ровно одна смысловая строка diff (`1 insertion, 1 deletion`). История Git не переписывалась.

Во время выполнения цель пережила не менее двух автоматических compaction. Snapshot перед созданием отчёта: `774256` goal tokens, `7376` секунд; это промежуточные, не финальные значения.

## Git commands

Git inspection был прямо разрешён проектным `AGENTS.md` и task contract для scope guard, exact parent/upstream и полного diff review. Использовались read-only команды:

- `git status --short --branch`
- `git status --short`
- `git rev-parse --show-toplevel`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{u}'`
- `git cat-file -e b50f2de35d54f34a682fa1166acfcebaf3b2a71b^{commit}`
- `git show HEAD:L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/Player.java`
- bounded варианты `git diff`, `git diff --stat`, `git diff --numstat`, `git diff --name-only` и `git diff --check` для exact scope/diff verification.

После отчёта будут выполнены exact-path `git add`, `git diff --cached --check`, cached allowlist/full diff review, один `git commit -m "feat(phantoms): add bounded clan leader directives"` и `git push origin feature/phantom-world`. Commit SHA и push result фиксируются после freeze отчёта и сообщаются в финальном ответе; SHA нельзя достоверно вписать в тот же атомарный commit, содержимое которого он идентифицирует.

## Limitations and risks

- Goal 030C2A остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; статус `ACCEPT` агент самостоятельно не выставлял.
- Текстовые aliases намеренно ограничены каталогом; свободный NLP и расширение команд относятся к будущему scope.
- Директива влияет только на materialized exact recipient, увидевшего штатное clan-сообщение; background population scan намеренно отсутствует.
- Реальная нагрузка production population отдельно не измерялась; архитектурные ограничения и deterministic integration покрыты.

## Branch, commit, push

- Branch: `feature/phantom-world`
- Upstream: `origin/feature/phantom-world`
- Exact parent: `b50f2de35d54f34a682fa1166acfcebaf3b2a71b`
- Commit subject: `feat(phantoms): add bounded clan leader directives`
- Commit SHA: определяется после фиксации этого отчёта
- Push result: определяется после commit

## Next step

Провести независимый review Goal 030C2A. После принятия slice следующим bounded этапом является Goal 030C2B; CP2 до него не начинать.
