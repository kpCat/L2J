# RESULT010 — PARTIAL, observation RED, M1 OPEN

```text
TASK_RESULT=PARTIAL
CODE_SHA=ffc0b97e2eeccc42b362418524f5ba4829e65f46
AUTOATTACH_TESTS=CONTRACT_PASS_15_OF_15
BUILD=PASS
PHANTOM_CONFIG_ENABLED=true
USER_MANUAL_LOGIN=PASS
REAL_LOGIN_OWNER=PASS
PILOT_AUTOATTACH=PASS_ARMED_IDLE
ARM_CODE_USED=false
RUNTIME_BEHAVIOR=OBSERVED
AUTOPLAY_5_CYCLES=NOT_OBSERVED
OBSERVATION_SECONDS=727.994
COHORT_COUNT=0
DISCONNECT_REVOKE=REQUIRED
RUNTIME_STOP=AWAITING_APPROVAL
M1=OPEN
```

## Изменённые файлы и scope

Только пять MODIFY-путей SOURCE_MAP: LocalPlayPilotConfig.java,
LocalPlayPilotService.java, EnterWorld.java, LocalPlayPilot.ini, LocalPlayPilotSuite.java.
Дополнительно документы и task-local evidence tooling внутри task010.
Player, GameClient, identity registry, ThreadPool, AutoPlay/AutoUse, background/history,
ecology, movement и другие хроники не изменялись. Новых зависимостей и миграций нет.
Это bounded task010; engineering task007 не продолжался.

Allowlist trim / empty-fragment skip / максимум32 имени / длина1..32 / Locale.ROOT.
Malformed list выключает только auto-attach, shipped defaults OFF. REAL_LOGIN acquisition
не зависит от allowlist. Service проверяет enabled flags, exact allowlist, active poller,
private mailbox ACL, manifest/JAR hash, owned JVM и прежний realClient predicate.
Lease другого live клиента не отбирается; exact client/player повторный hook idempotent.
Истёкшая lease очищается существующим revoke. На успехе существующая lease.arm
с внутренним nonce и session publication; arm.properties не используется.
EnterWorld получает ровно один post-enter вызов; ошибки hook отказывают в attachment.
Штатный manual arm и disconnect revoke не изменены.

## Targeted RED → GREEN

ant -q phantom-localplay-pilot-contract-test: RED7 pass / A01–A08 fail на отсутствующем
loadAutoAttach API; GREEN15/15, seed40004001. ant -q jar: exit0.
Финальный повтор после cleanup истёкшей lease: tests19s / jar16s, exit0 обоих.
RED.txt / GREEN.txt содержат фактические результаты.

Ограничение: это parser/lease behavior и static integration/security contract tests,
не восемь native network integration tests. Native positive attach подтверждён
отдельно данным runtime; negative clients/no-stealing/idempotence и manual permit
не проигрывались настоящими вторыми клиентами. Не выдаём static checks за это.

## Runtime config и БД

Fresh read-only single-transaction export PLAY l2jmobiush5_localplay3 на127.0.0.1:3308.
CREATE/import только новой l2jmobiush5_localplay_observe010. До CREATE имени не было;
dump не содержит USE/CREATE/DROP DATABASE, triggers/routines/events не экспортировались.
Characters10002 / profiles10000 / components50000; TestAdmin268492939 online0 до startup.
PLAY не получает UPDATE, seed, migration или обратный import. Original304 file hashes сохранены.

Private runtime .phantom-local/observe010/runtime в worktree m1-observe-008.
EffectiveConfig.java вызывает настоящие config loaders из runtime JAR:
ENABLED=true / Population1280 / Active8 / Materialized8 / Scheduled10000;
Pilot=True / AutoAttach=True / Allowlist=TestAdmin / Synthetic=False.
CONFIG_OVERRIDES.tsv содержит runtime overrides. Rates/goals/NPC/geodata/budgets не правились.
GameServer прошёл условную Phantom World startup section и startConfigured gate,
pilot mailbox enabled, loaded58s, Registered Server1 Bartz; error0.log пуст при проверках.
Owned Login19584 / Game5956; точные start ticks/runtimeId приведены в PRELOGIN.md.
Listeners2106/9014 Login19584,7777 Game5956.

## Ручной вход и auto-attach

После обязательного приглашения пользователь написал «в игре».
Get-LocalPlayPilot до первого request: ARMED_IDLE, runActive=false,
actorObjectId268492939, Game5956. arm.properties отсутствует; Prepare/arm не вызывались.
Первый STATUS, 2026-10-05T16:56:11.0988283Z: online=true, worldPresent=true,
identityOwner=REAL_LOGIN, account=testadmin, exact clientIdentity444143705.
IN_GAME подтверждается успешным sessionValid/realClient guard фактического JAR:
он требует non-headless, non-detached client, client.player==player и IN_GAME.
Это server-side evidence, не только DB online или UI transport.
Обезличенные LOGIN-ENVELOPE.tsv и OBSERVATION.tsv исключают private session/control tokens.

## Observation и самый ранний gameplay blocker

INITIAL natural selector выбрал profile110, STORED_START, object0/epoch0,
committed(44126,42751,-3488), calendar ACTIVE; prepared roster19:
87,110,175,187,252,275,352,406,644,652,752,775,879,888,975,1009,1158,1179,1241.
Это roster потенциальных кандидатов, не19 live actors и не доказанный farm cohort.
Штатный INITIAL временно переместил только TestAdmin во outside point;
после validated APPROACH query наблюдатель возвращён в origin44131,42673,-3488.
Snapshot profile110: distance78, humanPrewarm=true, localityCurrent=true,
signalDelivery=ACCEPTED, requestedState=NEARBY_PERCEPTIBLE, effectiveState=SLEEPING,
admitted=false, materializationState=STORED, worldPresent=false, objectId0,
materializedAtNanos0, lastMaterializationFailure=null,
readinessReason/lastTransitionReason=native_context.required:coalesced.
Это наблюдаемая граница; причинное исправление background/history не выполнялось.

Основное passive window: 17:01:08.8972137Z–17:13:16.8909555Z,727.994s (12m8s).
106 samples:53 census и53 visible-selector. Census44 NO_CANDIDATE и9 CANDIDATE_SNAPSHOT;
visible-selector53 NO_VISIBLE_MATERIALIZED_PHANTOM. Успешных visible selections0.
Удалённые candidates1062,71,57,192 появлялись с materialized=false;
наблюдателя к ним не перемещали и cohort не подбирали до успеха.
Пользователь подтвердил: «Phantom не видны».

Ограничение длительности: window включает перерывы из-за server-side run cancellations.
Первый сегмент около308s, второй246s, третий одна пара snapshots;
непрерывного десятиминутного run не было. Сохранены все106 фактических samples,
а не только успешные. Terminal CANCELLED подтверждался ARMED_IDLE/runActive=false;
запускались новые read-only runId без перезапуска JVM/сессии и без changes gameplay.
Точная причина отмен не установлена; samples также показывают самостоятельные
перемещения TestAdmin. Один начальный request с другим runId был штатно отвергнут;
повтор с текущим runId прошёл. Ничего из этого не объявляется Phantom farm behavior.

Earliest user-visible blocker: NO_VISIBLE_MATERIALIZATION в наблюдаемой стартовой зоне.
COHORT_COUNT=0 materialized lifetimes. Target/damage/kill/positive EXP/SP/next target,
loot/pickup, water/wall/stuck, death/recovery и demat/remat — NOT_OBSERVED.
Пять последовательных автономных cycles не доказаны; AUTOPLAY_5_CYCLES=NOT_OBSERVED.
Оператор не применял ATTACK_NPC, CAST_LEARNED_SKILL, SELECT_TARGET, ручной materialize
или запуск AutoPlay. Никаких gameplay/AI fixes во время observation.
Отсутствие live actor не позволяет делать выводы о качестве attack/cast/rewards.

## Cleanup gate

Observation остановлен на RED; Stop-LocalPlayPilot подтвердил STOPPED/no active run.
Consent пока ARMED_IDLE: для native disconnect revoke нужен выход пользователя.
Обе owned background JVM имеют MainWindowHandle0; CloseMainWindow для exact PID5956
и19584 вернул False. Force не выполнялся. PLAN.md прямо требует предварительное
разрешение exact-PID force, если graceful path недоступен. Runtime сохранён до ответа.

## Проверки и Git

- mojibake-маркеры в изменённых файлах проверены:0;
- escaped Cyrillic в изменённых файлах проверены:0.

Технические regex literals Verify-Scope.ps1 исключены из поиска их собственных маркеров.
git diff --check и exact SOURCE_MAP scope guard PASS перед engineering commit.
Git-команды использовались по прямому разрешению пользователя/GIT.md;
полный engineering перечень в PRELOGIN.md. Для текущего evidence дополнительно:
git diff --name-only, git diff --check, git status --short --untracked-files=no,
git add -- $paths (только конкретные файлы task010), git diff --cached --check,
git commit -m 'phantom(task-010): record real-client observation boundary',
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008.
Origin проверен как https://github.com/kpCat/L2J из Agents.md.
Engineering ffc0b97e2ee normal push PASS. Receipt текущего evidence commit сообщается
после выполнения команды; force push, чужой diff и feature branch не используются.

Следующий шаг только cleanup/disconnect gate. M1 OPEN; task010 GREEN не означает M1 CLOSED.
