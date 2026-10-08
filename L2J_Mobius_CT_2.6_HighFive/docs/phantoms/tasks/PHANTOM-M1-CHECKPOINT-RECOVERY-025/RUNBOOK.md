# Автономный RUNBOOK

Root MODULE под own worktree. JDK25/Ant/MariaDB127.0.0.1:3308.
Reuse TASK024 Start-Reviewed, Update-OwnedRuntime, Observe, persistence export.
Не копировать87 helper scripts; thin adapters меняют только собственный base/DB/path.
Не запускать Java agents с пропущенным matching class/manifest; private helper build
зафиксирован hash'ом отдельно. Не делать новый source patch через JSON на каждый шаг.

## Clone/time layout
025a — копия последнего contract024c с pending452: только reproducible recovery test,
не natural farm PASS. Источник immutable.
025b — own fixture DB по Contracts024DatabaseLane; common TEST нетронут.
025c — fresh PLAY export для natural cohortA/B; каждое смещение observer до baseline,
все rejected setup locations фиксируются, максимум3 места.
025e — выделенный planned AFTER_NATIVE; 025f резерв AFTER_FINALIZE.

Effective config:1280 READY target /8active /8materialized /10000scheduled,
diagnostics ON, stock rates and no GM overrides; Synthetic=True for own observer.
Не уменьшать target/retire profiles, чтобы избавиться от неудачных actor.
TestAdmin ordinary clone native Player, no GameClient, LOCALPLAY_TEST_HUMAN;
existing RealClient autoattach сохранён на утро; kpCat untouched.

## Ни одного user input gate
Начать Synthetic штатной Start-LocalPlaySynthetic.ps1, получать/передавать RunId,
ActorMode=Synthetic. Fresh heartbeat отдельным cheap control каждые10с, во время долгого
запроса он тоже живёт. Не открывать несколько mailbox writers с одной sequence.
При UNCERTAIN сначала серверный status+requestId, не повторять mutate action наугад.

Session TTL из текущего конфига измерить до действий. Для leave/return выделить отдельную
fresh session по границе сценария, не reuse A+B session и не ожидать, что heartbeat
продлевает абсолютный TTL. Не увеличивать525s/400commands/5starts-per-JVM ради PASS.
При временном healthy expiry сохранить факт и корректно завершить; expired≠native defect.

## Runtime safety
Перед build/deploy: stop own runtime, clean build exact committedSHA, validate JAR/config/data.
Пробный патч в работающий JVM не внедрять. Collector не переписывает AI/ownership.
До closeout verify existing NPC and normal scheduler, synthetic not direct Decision driver.
Native TELEPORT_SELF допускается для setup только observer; leave/return — native MOVE_SELF.
PREPARE_M1_ENVELOPE не использовать как read-only snapshot (он двигает observer).

Если ранний safety failure повторяется — 3 bounded snapshots + first exception/thread dumps,
закончить probe, исправить внутри scope. Не досиживать6мин в заведомом мёртвом мире.
Final scenes наоборот должны пройти весь defined duration, без early success по5kills.

## Короткий endpoint до120мин
На одном exact new codeSHA получить хотя бы:
(а)452 FINALIZED из retainedcopy + повторныйrestart, или
(б)native actor farm→checkpoint→OPEN→следующий farm,
и короткую full-server проверку. Оба нужны для итогового taskGREEN.
Невозможность endpoint требует конкретного result/outside design, не просьбы войти.

Если retained024c отсутствует: использовать его exact сохранённый receipt/SQL snapshot только
в своей fixture lane, пометить RETAINED452_FULL_SERVER=NOT_RUN. Не выдумывать runtime
восстановление и не ждать пользователя; безопасные live-path работы продолжить.
