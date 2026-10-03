# Результат PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006

**BLOCKED; M1_OPEN.** Native TEST доказал потерю ownership до завершения уже заработанного delayed quest reward. READY_FOR_CONTROLLED_DEPLOY не достигнут. Остановка по DESIGN D5 / PLAN, workstream 4: безопасное покрытие этого producer выходит за разрешённый SOURCE_MAP. Deploy, synthetic №5, real arm и M2 не выполнялись.

Baseline и сохранённый production code SHA: `461a4abe32be4aa08532b8417a6147684a8889c6`. Исходный checkout остаётся на `b80cdf78560c06a9d88b8ec761e1582f0e55afba`, с тем же чужим diff. Предки `b4f1f03d7407897f50bd3131f40d6e19011116e1` и `ea56bef1381c05b70f6e5da72e07004d53655be7` сохранены. Проверенный remote до публикации — baseline. Точный SHA опубликованного отчёта и подтверждение push записываются в PUBLICATION.md; report SHA отличается от code SHA.

## Критический native RED Q01/Q05

Команда: `ant '-Dbuild=.phantom-local/m1-native-closeout-006/red-delayed-native-quest' '-Dphantom.m1.native.focus=async' phantom-m1-native-lifecycle-test`. Guarded TEST `l2jmobiush5_phantom_test`, seed 15001501; exit 1, 0/1 PASS. [Полный отчёт](evidence/red-delayed-native-quest.txt), [XML](evidence/red-delayed-native-quest.xml), [execution](evidence/red-delayed-native-quest-execution.txt).

Реальный stock AutoPlay убивает Monster 20534. `Attackable.doDie → EventDispatcher.notifyEventAsyncDelayed → ThreadPool` запускает настоящий ON_ATTACKABLE_KILL callback. TEST ConsumerEventListener держит callback барьером и затем вызывает штатный `Quest.giveItems(player, 1334, 1)`. Это native event/writer, а не модель EXP или hand-coded combat. NPC/item пара существует в Q00266_PleasOfPixies; полный quest-state replay этой quest не запускался.

На profile 73434 / object 268435465 / epoch 635971541604800:

- Во время активного quest callback admitted actions = 0; cleanup проходит owned PREPARE.
- Cleanup возвращает SUCCESS и удаляет service entry, World Player, identity lease и outbound.
- Только затем callback выдаёт предмет: inventory 0 → 1; pendingOwnedStore = false. Background canonical hash уже зафиксирован до выдачи.

Player hit/cast leases закрыли один callback path, но независимо отложенный event остаётся вне счётчика. `EventDispatcher` не возвращает owned Future; `QuestTimer.cancel(false)` не дренирует выполняющийся callback. Удаление earned reward, ослабление mismatch guard или sleep не удовлетворяют контракту. Нужен отдельный producer ownership/drain design с точным покрытием kill events/quest timers/прочих native writers и finite timeout, либо обоснованный fail-closed механизм. В этой задаче такой механизм не внедрён.

## Что проверено и что осталось

| Finding | Фактическое evidence | Состояние |
|---|---|---|
| P04/P05 stale P1 удаляет P2 в обоих managers | RED 4 cases в red-ownership-lf; GREEN в green-session и green-incident-mage | Candidate fix, не принят к deploy |
| P07 stale rollback / P08 partial pair | RED red-session-overlap; GREEN 8/8 green-session | Candidate fix |
| P01 missing policy | Исправленная fixture GREEN в обоих pools; первоначальная AutoPlay fixture не имела валидного observer target | Корректный baseline RED P01 ещё REQUIRED |
| P09 mage after pair repair | green-stock-mage 1/1, штатный 1177:1, native cast/kill, EXP +70 / SP +2 | Отдельный PASS; old-epoch subcase не закрыт |
| E02/E03/E04 primary vs finalizer/abort | Реальные store/resume SQL и native stopAllTasks faults: RED → GREEN | Candidate fix; outer materialization-finally audit остаётся REQUIRED |
| E01/E05 first failure при retry/removal | red-first-incident 2 failures → detached archive assertions PASS | Candidate fix |
| E06 bounded hostile formatter | Native incident bounds/Unicode/hostile Throwable PASS | Без baseline RED; exact-hook contract ещё открыт |
| Q hit/cast reward | Native hit/timeout cases PASS; cast отдельно PASS после исправления TEST bootstrap | Покрывает эти callbacks; Q01–Q05 целиком не PASS |
| Q delayed kill event | red-delayed-native-quest 0/1 | Критический BLOCKER |
| T01–T03 terminal Journey | Native travel RED после копирования существующей geodata | Не исправлен; production PhantomSystem composition в TEST не исполнена |
| A01–A04 false PASS | Четыре negative controls actual extracted grading function дают OFFLINE_ASSERT, exit 1 | Не исправлены; A05–A08 не закрыты |

`green-incident-mage` — имя каталога, а не verdict: полный запуск дал **17/18, exit 1**. Семь E assertions, восемь P assertions, hit и timeout прошли; mage fixture тогда имела неполный native handler bootstrap. Последующий отдельный green-stock-mage с существующим MasterHandler дал 1/1, exit 0. Объединённый финальный GREEN suite не запускался. P02/P03/P06, ordinary REAL/offline/synthetic positive controls, T04/T05 continuation, end-to-end passive attribution остаются REQUIRED. [Матрица RED_GREEN](RED_GREEN.tsv) описывает пределы каждого результата.

VERIFY broad regression (11 targets), owned C/D/restart/idempotence/mismatch controls в новом candidate, performance smoke и bounded concurrency stress не выполнены: после доказанного D5 blocker переход к integration gate остановлен. Ранее сообщённые 23/23, Pilot 7/7 и C/D из предыдущего SUMMARY не выдаются за новые проверки. TEST cleanup выполнялся штатными fixture finally; PLAY SQL mutation не выполнялась.

## Review и публикация

Независимый read-only review подтвердил Q blocker, travel/grading defects и безопасную docs-only публикацию. Дополнительно обнаружены stale cleanup hook (`ACTION_DRAIN` с `AFTER_ACTION_ADMISSION`) и ordinary generation-check/SQL-side-effect race; второй — static finding, нового regression нет. [Review](REVIEW.md).

13 candidate production/test/build/runner файлов остаются **незакоммиченными** в изолированном worktree. [CANDIDATE.patch](evidence/CANDIDATE.patch) сохраняет полный diff, включая новый untracked PhantomCleanupIncident.java. Это audit artifact; применять его для deploy запрещено. SQL LF normalization, private config, DB manifest, copied geodata и binaries исключены из patch и commit. Перечень и SHA256: [CHANGED_FILES.tsv](CHANGED_FILES.tsv).

Remote publication подготовлена только для task package, этого отчёта, handoff и sanitised evidence. Production tree report commit должен остаться baseline; после push это проверяется в PUBLICATION.md. Основание commit/push при BLOCKED — High Five AGENTS.md: «При BLOCKED ... оставить безопасный аудит, тесты и документацию ... закоммитить ... запушить» и прямое требование TASK опубликовать результат независимо от gate. Ограничение TASK на production commit после GREEN не обходится.

Artifact gate **NOT_FULFILLED**. Clean committed production-fix SHA build/JAR SHA256 = **NOT_RUN / NOT_AVAILABLE**: принятого fix commit нет, VERIFY требует сначала TEST/review/publish production fix. Сборка baseline не подменяла бы эту проверку. TEST компиляция candidate не считается clean production build.

## Сохранность и ограничения

Пять исходных изменённых файлов сверены SHA256 до/после; staged diff исходного checkout пуст. Login PID 24248 и Game PID 33368 имеют прежнее start time; исходный dist/libs/GameServer.jar SHA256 и два config hashes совпадают с read-first. Загруженный runtime JAR provenance не переустановлен этой проверкой: прочитанный JAR исходного checkout не объявляется загруженным JAR. [Preservation evidence](evidence/PRESERVATION.json), [предыдущий SUMMARY provenance](evidence/PREVIOUS_SUMMARY.md), [READ_FIRST](READ_FIRST.md).

Исторические first exceptions 110/142/175 остаются UNAVAILABLE; новые TEST не восстанавливают их. Synthetic budget остаётся 4/5. Текущая JVM, runtime files, arm и PLAY не менялись. L2ClientTools не потребовался.

Git использован по разрешению TASK/AGENTS и пользователя для scoped inspection, isolated worktree и exact-path docs staging. Commit/normal push разрешены для безопасной BLOCKED публикации; фактический результат записывается в PUBLICATION.md после исполнения. Команды и фактические exit/results: [COMMANDS.txt](COMMANDS.txt). Reset/clean/stash/rebase/force, add . и broad restore не выполнялись.

- Mojibake-маркеры в изменённых файлах проверены: полный заданный набор, совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: Unicode/XML patterns, совпадений нет.

Проверки охватывали task package/evidence, report и все 13 candidate paths; `rg` exit1 означает отсутствие совпадений. Package/evidence publication90files — bounded exception одной artifact family, прямо требуемой TASK. Ни одна новая production subsystem не публикуется.

Следующий безопасный шаг: согласовать bounded producer ownership/drain расширение SOURCE_MAP, затем native delayed-event RED→GREEN и остальные открытые P/E/T/A/Q controls, broad regression/review/artifact gate. Runtime/deploy gate остаётся отдельным и сейчас недоступен.
