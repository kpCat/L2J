# PHANTOM-LEARNING-005 — ход работы

## 12.11.2026 — подготовка и обязательный review плана

Статус: **IN_PROGRESS — план подтверждён пользователем, выполнение автономно**. Own Director005 запущен для smoke; G0–G8 ещё не приняты.

- Вход: backup ZIP в приложенном UTF-8 тексте, SHA256 `e65db8bb29c2b4fd68006a7aa61a4ec475aea9c3d36613ce8bc2d75168e96f62`.
- Восстановлены все17 файлов с исходными относительными путями внутри High Five. Все15 MANIFEST payload sizes/hashes совпали; MANIFEST SHA `d175b0ec8d1925ba682f7dcceaf7b0071ca73966240dbaadb383e9080e019ac5` совпал с SHA256SUMS. Входные тексты не изменялись.
- Independent network clone004 создан в `D:/Codex/repos/L2J-learning-005`. HEAD точный `dff800f4b95232c132b374f8ec97c932eda4a6b1`, исходный clone до восстановления пакета clean. Создана только новая ветка `wip/phantom-learning-005`.
- GitHub readback подтвердил004,002,003, feature и TASK130 на ожидаемых SHA. Ref005 до начала отсутствовал (404).
- Пользовательский checkout `feature/phantom-world` содержит посторонний untracked dirt; никаких записей в него не выполнено. Clone004 читался только для документов/source.
- Доступны Git/JDK25/Ant/Python. MariaDB отсутствует в PATH, но штатный G0 знает подтверждённый путь; ручного поиска и смены PATH не было.
- Первый Preflight в sandbox: UnauthorizedAccessException в штатном physical path guard, exit1. Повтор вне sandbox с тем же штатным read-only route прошёл exit0: JDK25/MariaDB11.4/Geo203/protected207/порты. Это не SQL boot и не native evidence.
- Read-first source seams и exact9 source/config paths перечислены в PLAN.md. Входной пакет определяет подход; минимальный EVAL route должен обходить TRAIN preparation/trainer и сохранять прежний G0 lifecycle.
- Дополнительный точечный аудит выявил обязательный writer-free observation seam в `LearningPolicy`: `collecting/record/actorKey` сейчас зависят от training Writer. Путь включён в exact allowlist; без этой правки нельзя честно обещать отсутствие EVAL contamination. Production Combat/Party менять не планируется.
- Accepted ZIP002/003 в новом clone проверены Get-FileHash: оба SHA совпали с pins пакета. Model SQL readback ещё NOT_RUN.
- Независимый read-only builder check: `nothing important left`. Это проверка полноты плана, не приёмка реализации.

Ruling: подготовка clone и восстановление входного пакета разрешены явно GOAL/TASK. Перед product implementation требуется подтверждение плана по пользовательскому AGENTS.md: «если задача крупная, сначала предложи план и дождись подтверждения». После подтверждения рутинные own TEST действия не требуют повторного разрешения по TASK.md:3.

## Подтверждение и Task1

Пользователь ответил на review: **«Подтверждаю план, продолжай автономно»**. Повторного согласования рутинной own TEST работы не требуется.

- Созданы три файла005 в test source tree: Evaluation (versioned arm/pair/pins/matching/statistics), Ledger (process lock, immutable binary frames + canonical JSON, SHA/integrity/cold readback), Checks (focused console main).
- Matching test: ожидаемый RED `T01 equal facts and independent incarnations`, после реализации GREEN18 assertions.
- Первый compile выявил ошибочный доступ к package-private `TrainingStore.quote`. Причина подтверждена исходником; в005 применён собственный ограниченный JSON escaping, без изменения TrainingStore. После исправления compile-tests exit0.
- V1 observation seam в разрешённом `LearningPolicy`: отдельная factory `evaluation`, exact accepted1 SHA + opt-in + identity; collecting/record/actorKey используют EVAL identity без Training Writer. Выбор действий и обычный runtime путь не менялись.
- Sink test: ожидаемый RED `T10 exact evaluation receipts without a training writer`; GREEN20 assertions, native=NOT_RUN, fixtures=CONTRACT_ONLY. Последний compile-tests exit0.
- Прежние4 исходных файла заранее сохранены во внешнем writable scratch с SHA256: Runtime/TrainingNative/LearningPolicy/Training.ps1. Из них пока изменён только LearningPolicy.
- Tracked исходники пишутся через staged apply_patch во внешнем scratch и exact Copy-Item в own clone с command approval; sandbox не включает новый clone по умолчанию. Это не автоматический approval rejection: штатный Preflight вне sandbox был разрешён.

Текущий source scratch: `C:/Users/ZBook/.codex/visualizations/2026/11/12/01a1cf3e-0f2e-7861-bdb7-da71c46c1ff2/task005-work`. Логи compile/test в соседнем `task005-tools`. Внешний scratch не публиковать.

Следующее действие: Task2 — минимальный EVAL005 route в existing Director + TrainingNative, новая disabled-by-default Evaluation005.ini, native evaluator. Не запускать004 collection/trainer. Runtime должен раздельно устанавливать V3 только Party HEAL (его нынешний constructor также подключает V3 к Combat DAMAGE, поэтому нужен TEST-only constructor option). Для fresh native target есть локальный обычный Spawn.doSpawn аналог в PhantomPet060NativeSupport; не использовать HoldingMonster/mock или изменять существующий spawn config. Нужен actual pre-CUT snapshot со всеми контролируемыми полями; несовпадение => UNMATCHED.

Известные TODO до native: Effect.mp должен поддерживать UNKNOWN для V1 (его receipt не содержит exact MP debit); текущий тестовый double пока не покрывает это. Ledger campaign manifest необходимо дополнить config/model/schema fingerprint; START journal и подтверждённый owner-exit resume ещё не реализованы. Ничего этого не объявлять готовым.

## Task2 — review и первый smoke

Вышеописанные TODO теперь исправлены: nullable MP, встроенный source JSON+SHA, family/instance/recipient attribution, config+evaluator bytecode pin, единый atomic START+nonce, immutable pair JSON, JSON/TSV/Markdown export.

TEST Runtime раздельно подключает V3 только к Party HEAL; V1 одинаковый SHADOW в Party обеих сторон. Time-to-terminal наблюдается отдельно от drain/settlement, с точностью polling. Обычный Spawn.doSpawn использует template/координаты реального канонического NPC и свежие incarnations; существующие spawn config/Geo/баланс не меняются.

Director EVAL005 обходится без TRAIN preparation/trainer/auto model selection, включая malformed recovery; G0/Job/Win32 не переписаны. Первый reviewer нашёл5 Important (exit identity, foreign effect, drain timing, START crash window, recovery trainer); все исправлены, повторный read-only review подтвердил отсутствие оставшихся Critical/Important перед native.

Последние проверки: canonical compile-tests exit0,5 прежних warnings;26 focused assertions PASS, включая RED→GREEN foreign exit nonce. Ранее143/143 existing assertions и F7/F8 14/14 PASS; повторить affected regressions после окончательного source. Evaluation005 Preflight exit0, own loopback33347/17991/19291 свободны.

Первый запуск: `Training.ps1 -Action Start -ConfigProfile EVAL005 -EnableEvaluation005 -Evaluation005PairLimit 1 -Evaluation005Campaign smoke005a`, exit0. Director PID53136, generation1, run da91cd930d404b8d9e2a36edecd87e67, nonce2af8f4553a9448bc932af33643e85f9b. Последнее наблюдение BUILDING, появился jar log. Не запускать второй Director и не менять frozen source до STOP.

Private root: D:/Codex/repos/L2J-learning-005/.phantom-local/learning002/train. Private ACL требует exec require_escalated даже для чтения; читать только status/нужные logs, не печатать credentials/SQL client configs/bootstrap records.

Known small wording issue: старый Start выводит «подготовка и обучение», хотя EVAL не вызывает fit. При необходимости исправить условно для EVAL только после STOP, не менять script hash живого Director.

Следующее действие: наблюдать smoke005a; проверить admission и настоящие Solo/Party A/B receipts. Затем bounded4+4 только после диагностики smoke, STOP/resume и delivery. Native evidence/CPU/perf ещё не получены.

## Новое поручение владельца — consolidation после005

Пользователь явно попросил по окончании слить все накопленные изменения обратно в feature/phantom-world. Это разрешает финальную consolidation и обычный push feature после005; не разрешает потерю dirt/MAIN/PLAY mutations.

GitHub compare feature faec72161f9e4f6231764e17d6f2637a4db080af →004: ahead162, behind0. TASK130 c5b81f89ce02030eff8441395b0411137a110b2f →004: ahead19, behind0. Цепь004 уже содержит TASK130/001–003; после005 возможен обычный fast-forward feature.

В original Git common tree зарегистрированы3 старых detached worktrees (controlled109, scenarios116, living114). Все3 HEAD доказанно ancestors текущей feature. Их возможный uncommitted dirt не проверялся; ничего не удалять/не архивировать слепо. Original checkout до конца005 остаётся read-only. Перед обновлением проверить exact untracked collisions, сохранить уникальное вне checkout с SHA256, без force/clean/reset/stash. Clone004/005 с SQL/evidence не удалять.

## Закрытие005 как PARTIAL — реальный G0 blocker

Smoke005a завершился FAILED/STAGING до SQL/GameServer: protected207 ожидал original OwnerConfig SHA5776e83810689da1acf94d0a3d8ee54d204e87ca0bf7488e685034b57c61f4e8, actuald8dbe7f2e87de37c3f04c9a856bb925c7e4ffbeddd7c33b5a08ba55e695f2976. Все207 строк проверены, остальные206 совпали. Причина изменения не установлена; LF/CRLF/BOM вариантов недостаточно, exact expected snapshot в известных источниках не найден. Guard/manifest/protected файл не менялись.

Повторных native boot не выполнялось. Штатный own STOP exit0;33347/17991/19291 свободны. Исходные V1/V3 checkpoints и optimizer8 unchanged. Native arms/pairs/outcomes/divergences/perf — NOT_RUN/UNKNOWN; модели ON не включались.

Последний source: compile-tests/jar exit0;28 focused assertions,143 existing assertions,F7 и F8 14/14 PASS. Final JAR SHA4caa20432d64ffffcd65faf92d815ec4fa4bb2cd615f53f9154269d9fbcf62ca. Native scene/SQL admission/real resume по-прежнему не доказаны. EVAL Start wording исправлен после STOP.

Source allowlist39 paths:9 source/config/test,17 immutable input и13 report/task/evidence. Private generated runtime20 файлов явно исключены и не допускаются к staging. Scope guard no outside source; отдельные mojibake и escaped-Cyrillic проверки39 файлов дали0matches. Входные hashes остаются exact.

Consolidation read-only inventory выявил10 untracked collision файлов, все документы исторического TASK130, без code collisions. До локального fast-forward сохранить их exact bytes вне checkout и проверить SHA. Новое поручение разрешает интеграцию после безопасной PARTIAL delivery; не считать это принятием G0–G8.

## Окончательное продолжение12.11.2026

Ранние записи выше исторические. Пользователь явно разрешил baseline005 COPY и native испытания. Новый exact pinned207manifest прошёл Preflight/staging;206строк и сами207files unchanged. TrainingOwner.ps1 — утверждённый10-й source файл.

Реально запущены own SQL и3GameServer JVM: smoke005a, smoke005b и frozen eval005d. Измеренные sampling timeout и random initial NPC position исправлены до основной серии; ранние arms сохранены отдельно. Headless teleport completion проверен review доCUT.

Основная серия:16arms,8plannedpairs. Fair completed Solo2/Party1; Solo3causal divergence в fairpairs; PartyHEAL0divergence. Смерть, отсутствие эффекта, отклонённая attribution и1Party UNMATCHED сохранены. СтатусPARTIAL, G2/G3 required, FULL gate NOT_PASSED. EVIDENCE_REJECTED не является игровым поражением: native terminal kill/HEAL сохранены.

РеальныйSTOP/freshDirector/evaluator-workerRESUME: immutable records/summary byte hashes unchanged; завершённый native budget не повторялся. ФинальныеSTOP/Report/Export exit0, свои3порта свободны. Final tests28+143/F7/F8 и separate156path encoding/scope checks PASS.17input file hashes unchanged.

Последующая consolidation: старые23tracked dirty/11UU безMERGE_HEAD. Пользователь подтвердил старую работу и автономный разбор. Всеworking/stages/index bytes сохранены сSHA; independentreview выделил16historical/regressive файлов и7незавершённых предложений. Проверенная chainTASK130+001–005 переносится в feature/phantom-world; незавершённый patch сохраняется как восстанавливаемый архив предложений.
