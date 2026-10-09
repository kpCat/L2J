# TASK029 — разрешения

## Смысл и предел
Пользователь хочет настоящий living world, а не доказательство выбранного удачного бота.
M2/PvP/party/social/professions/economy сейчас не развивать. Расширение обычного background
численного контекста входит в M1; новые тактические политики не входят.

## Worktree/Git
Required remote base `2b9496c935748803f8505c472bf4085977fb04d2`.
Own path `C:\Users\ZBook\.codex\worktrees\m1-background-029\L2J_Mobius`;
при занятом пути первый свободный suffix; detached exact base разрешён.
Main `C:\Users\ZBook\L2J_Mobius` и foreign worktree/diff не переключать и не изменять.
Task files разрешено скопировать из main в own task-dir, не брать foreign source changes.
Прочитать local AGENTS. Это явное пользовательское разрешение listed own operations,
но не обход platform/safety restrictions. При remote mismatch — factual BLOCKED,
не rebase/force/слепое применение patch.
Allowed: initial fetch/status/history, isolated worktree, exact-file add/commit,
normal `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`.
No `git add .`, reset/clean/stash/rebase/force; no global Git config mutations.
Exact-path RESULT commit/push обязательны и при FAILED/BLOCKED. Non-fast-forward
сохранить как blocker с локальным SHA, историю не переписывать.

## Время/работа без пользователя
До360мин wall-clock от первой команды; platform pauses не обнуляют срок.
До90мин — full-server probe на committed candidate (он может проверять collector/prestate).
К180мин должны существовать native parity и хотя бы один реальный background attempt.
С270мин production semantic freeze;90мин на окончательную приёмку/persistence/cleanup.
Нет лимита «4repair rounds». Несколько связанных доказанных fixes в scope разрешены.
Повтор идентичного RED без новой гипотезы/изменения входа запрещён.
После двух опровергнутых fixes одной гипотезы зафиксировать неверную модель; не расширять её
бесконтрольно. Не задавать вопросы о фазах/старте/логине/commit.

## Собственные runtime
PLAY `l2jmobiush5_localplay3` только SELECT/export. kpCat не трогать.
Old contract024..028 immutable read/export, никакого повторного import поверх old clone.
Own DB `l2jmobiush5_localplay_contract029a`..`h`, own `.phantom-local/contract029*/runtime`.
Прямые DML допускаются только существующим fixture/setup в own TEST; product сцены
без SQL-лечения состояния. Shared TEST guard metadata не подделывать: собственная lane.
JDK25/Ant/MariaDB127.0.0.1:3308. Target1280/active8/maxMaterialized8/maxScheduled10000.
TestAdmin100 только own clone; overrides hide/invisible/invulnerable/silence=False.
Synthetic ownership LOCALPLAY_TEST_HUMAN, не fake REAL_LOGIN. TTL525s/watchdog30s/
sequence400/5starts perJVM сохранены. Одна independent heartbeat writer на сессию.
До baseline один проверенный setup teleport synthetic разрешён. Away/return — native
MOVE по подтверждённым dry маршрутам. Phantom не teleport/spawn/heal/target вручную.

## Runtime cleanup
Start/graceful stop своих exact PID+startTime+runtime разрешены.
Не закрывать чужие Java/DB. В конце own JVM STOPPED и sidecar states зафиксированы.
До2 planned own crashes только после healthy controlled save: существующие
AFTER_NATIVE и AFTER_FINALIZE, REALcount0, backup + captured exact owner/epoch.
Emergency force только своих exact PID после двух bounded graceful attempts и dumps,
REALcount0; FORCE не healthy PASS. Никаких timeout-complete для заработанного callback.

## Разрешённые изменения
SOURCE_MAP дополняет ранее protected scope: PNC/model/authority/transaction доступны
ТОЛЬКО для ordinary background scalar/position handoff, с compatibility/crash tests.
Owned-store протокол и существующие receipt bytes не удалять и не переписывать в новую
систему. SQL schema не менять. ThreadPool/EventDispatcher/Player/AI core не переписывать.
Conditional paths используются только при собственном RED с доказанным causality.
Количество docs не ограничивает доказательства, но новых PS entrypoints максимум4,
observer1, Python audit package1. Не писать ещё 20 wrapper scripts.

## Publication/STOP
Raw evidence immutable. No credentials/JAR/fonts/geodata/fullDB/JFR в commit.
UTF-8, исходные EOL; mojibake и escaped-Cyrillic два отдельных guards.
При неизвестном safety или фундаментальном outside-design — BLOCKED с evidence и cleanup,
а не запрос с бессрочным ожиданием. STOP_AUTHORITY=TASK029_CONTRACT.
В конце один RESULT/HANDOFF/MORNING, без автоматического начала TASK030.
