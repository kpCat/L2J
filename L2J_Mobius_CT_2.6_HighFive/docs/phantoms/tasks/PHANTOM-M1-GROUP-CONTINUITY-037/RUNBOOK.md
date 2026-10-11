# RUNBOOK037 — автономно, без ручного входа

## Локальные границы
MAIN=`C:\Users\ZBook\L2J_Mobius` read-only.
OWN=`C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius` reuse only.
MODULE=OWN\L2J_Mobius_CT_2.6_HighFive. Собственные базы:
`l2jmobiush5_localplay_contract037t` TEST;
`l2jmobiush5_localplay_contract037a` diagnosis;
`l2jmobiush5_localplay_contract037b`,`l2jmobiush5_localplay_contract037c` final scenes.
Только 127.0.0.1:3308, manifest/cwd/exportSha/JDBC/SELECT DATABASE проверены до любых writes.
Старые 036/035/032/PLAY и shared phantom_test — только чтение/export. Не сбрасывать старые
таблицы или восстанавливать их из дампа. Никакой новой схемы БД или протокола; обычный import в новые 037.

Проверенный пакет находится только в MAIN — копировать только MANIFEST paths в OWN/task037,
byte hashes проверить до/после. Main не редактировать. Existing untracked evidence035/036 не трогать.
Архив input036 копировать/извлекать выборочно, сверять size/SHA и исключать traversal/symlink.
Если archive helper не публиковался, использовать verified private ops036 из сохранённого архива;
не строить всё заново из текстового отчёта. Reuse START/STOP/premain/observer/runner036.
Новый ops037 — ограниченный patch exact DB names/path/outputs, не копия целого worktree.

## Первый offline анализ
В PowerShell из MODULE (установленный python использовать по фактической среде):
```powershell
$task = Join-Path (Get-Location) 'docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037'
$evaluator = Join-Path (Get-Location) 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py'
python "$task/tools/test_cohort_forensics037.py"
# $episode — единственный проверенный FINAL_SCENE036b внутри извлечённых inputs;
# $output — НОВЫЙ файл в task037 evidence вне immutable episode. Определить пути из архива.
python "$task/tools/cohort_forensics037.py" $episode --evaluator $evaluator --output $output
```
Не исполнять команды с неопределёнными переменными. Приватные результаты выводить кратко:
ID/fault/first timestamp/ключ guard; не тысячи строк log в диалог.

## Runtime
До запуска — полный process census PID+startTime+cmdline+DB/CWD. Чужие процессы не останавливать;
занятые порты без own-identity означают BLOCKED_RUNTIME_BUSY. На новых 037 сохранить рабочие
settings036:1280 READY,target/cap8,scheduled10000; synthetic lane=true, без фиктивного REAL_LOGIN.
Не снижать population/MaxScheduled и не менять rates/geo/GK/world каталог ради сцены.
Synthetic — native Player, не Phantom; initial pre-World point через существующий read-only resolver.
Не стартовать в Giran с последующим teleport, который успевает заполнить cap чужой локалью.
Один ограниченный census, natural ingress: background FARM eligibility/completed due не являются
обязательным условием подхода; production admission и exact native handoff остаются единственными.

Короткий diagnostic probe:до 150 с, фокус на конкретном guard, не ждать час случайного 27.
При остановке callback больше сначала inspect jcmd/JFR/current owner; JDWP только 127.0.0.1,
suspend=n, никаких наружных портов и VM-wide exception floods. No SQL/I/O in native hooks.

## Предпосылки final stable-farm gate (не выбор победителей)
Заранее фиксировать area/ingress и правило enrollment по существующим factual local candidates,
без фильтра по будущим rewards/pass/классу «успешный». Два primaries определить до measurements,
один caster, второй другой eligible участник; не выбирать после подсчёта cycles.
Для 6–8 участников нужна обычная календарная online-доступность на ближайшие 420 с и допустимая
геометрия/цели. Разрешено заранее прочитать расписание; НЕ переписывать его/clock и не подавлять
настоящий departure. Это изоляция stable-farm проверки, не доказательство смерти/away.
В финальных отчётах сохранить отвергнутые pre-enrollment candidates и причины выбора окна.
При невозможности получить минимум 6 в bounded setup не урезать знаменатель и не retry доудачи.
После enrollment ID неизменны; missing/dead/offline всегда строка и исходный FAIL.
Исправление исходного дефекта у пяти 036 проверяется отдельными причинными тестами/trace,
даже когда final натуральная группа другая. Не использовать новый cohort для сокрытия старых faults.

Две final сцены 360–420 с, target380. Observer TTL525 с сохранить; до START рассчитать budget на
setup+scene+завершение; не продлевать автоматически. Вторая независимая сцена на c или другой
проверенной start session того же freeze, без code/config изменений. Breakpoints запрещены.
Приёмка не начинается до реальных native identities; старый retained Player не подходит.

## Завершение и runtime failure
Снять last-frame ДО начала STOP. Вызвать существующий synthetic OFF/STOP и stock shutdown
own Game/Login, с повторным poll одного исходного monotonic deadline. Сохранить first failure,
FULL receipts и POST SQL вне hooks. Healthy stop требует typed COMPLETE/retained=0 ДО остановки
shared pools; OS0 само по себе не PASS. Нельзя занулять counters/map/lease вручную.
При реальном hang сохранить threads/DB/evidence; выполнить безопасный уже разрешённый stock
stop. Force,taskkill,Stop-Process -Force не разрешены. Если stock stop не завершился, честный
CLEANUP_BLOCKED с точными own PID; чужие JVM не трогать, failure не скрывать.
Никаких ожиданий ручного входа/arm/подтверждения этапов. При true out-of-scope — отчёт и stop.

Disk cleanup/delete phase остаётся PAUSED. Сохранить phantom-dev и все 037 DB.
Не архивировать целые runtime/DB: selected raw+Jar+source+manifests вне worktree, SHA.
Не создавать новый worktree для clean build: Ant в OWN, verify clean committed tracked source,
tests Jar-first; compiler-produced build/bin не ставить впереди проверяемого Jar.
