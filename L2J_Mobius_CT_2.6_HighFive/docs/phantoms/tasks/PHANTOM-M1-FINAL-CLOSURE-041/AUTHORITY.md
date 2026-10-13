# AUTHORITY041 — новое разрешение crash и защита действующего сервера

## 1. Прямое разрешение пользователя

Сообщение, на основании которого выдан пакет041 (локальная дата13.10.2026):

> да разрешаю Codex выполнить контролируемые аварийные завершения исключительно собственных изолированных тестовых GameServer и БД, с обязательным восстановлением, без вмешательства в мой текущий игровой сервер, но что бы изменения полезные не затерялись и потом на финале при полном компиле после М7 я получил полностью собранный сервер со всеми плюшками и что бы я мог самостоятельно его запускать и останавливать и настраивать конфиги. Жду следующую задачу. Давай уже добьём весь М1 окончательно.

Операционное толкование: CRASH_AUTHORIZATION=GRANTED_ISOLATED_041 только для owned GameServer процессов041x72/041x73, работающих на выделенных копиях схем. Это НЕ разрешение аварийно завершать shared MariaDB, LoginServer, старый040a или чужую JVM. STOP_AUTHORITY=TASK041_CONTRACT + более позднее прямое указание пользователя. Отправка GOAL запускает этот041, отменяя старое правило «только040».

## 2. Защищённые ресурсы

MAIN; PLAY; действующий contract040a/runtime, его config/JAR/schema/accounts/прогресс/observer; все старые000–040 DB/raw/exports/worktrees; чужие изменения и staged index; исходные D:\Tools\L2ClientTools и игровой клиент. Не менять их, не стопать, не обновлять, не attach debugger/agent. Разрешён краткий read-only OS census/существующий Status без контроля игрока. Не требовать от пользователя выхода из текущего сервера до финальной041 приёмки.

Не сравнивать живую040a БД с замороженным hash: она законно изменяется от игры. Контроль «не трогали» — scope фактически выполненных операций, сохранность config/JAR, отсутствие stop/restart/agent attach, read-only census; не непрерывное аудирование чужого мира.

Нельзя останавливать MariaDB/service/PID, перезагружать Windows, менять global grants/ACL/system firewall, команды process-name kill или kill дерева без индивидуальной identity. Никакого kill всех java ради удобства. Полезные binaries/данные не удалять.

## 3. Точные новые namespaces

Все пути ниже относительно OWN module, кроме конечной поставки:

| Назначение | Root | Единственная schema |
|---|---|---|
| ordinary/lifecycle/sameDB | .phantom-local/contract041a | l2jmobiush5_phantom_test_041a |
| независимая группа | .phantom-local/contract041b | l2jmobiush5_phantom_test_041b |
| native fixtures | .phantom-local/contract041t | l2jmobiush5_phantom_test_041t |
| crash72 + recovery/idempotence | .phantom-local/contract041x72 | l2jmobiush5_phantom_test_041x72 |
| crash73 + recovery/idempotence | .phantom-local/contract041x73 | l2jmobiush5_phantom_test_041x73 |
| финальная отдельная поставка | C:\Users\ZBook\PhantomWorld\M1-041 | l2jmobiush5_phantom_test_041final |

Вспомогательные новые roots: .phantom-local/ops041, build041, release041, client041. Не создавать ненужные clones заранее. Совпадение существующего каталога/schema допускается только при доказанном собственном041 checkpoint; иначе conflict, не overwrite/удаление.

Новые Login/Game/control/debugger/observer ports обязаны быть свободны и не равны занятым040a. Listener ownership проверяется до Start; запуск не закрывает чужой порт. Ports выбираются один раз до эпизода и записываются actual значениями. Применимые config различия перечисляются в manifest, game policy прежняя.

## 4. DB clone и ограничение blast radius

Предпочесть уже сохранённый согласованный export с известной schema/provenance; нельзя копировать live DB files. Import только в новую свою schema до начала эпизода. Для каждого runtime проверить реальные JDBC URL/catalog каждого пула, login database и игровые подключения; не достаточно имени в launcher. Другую schema команды не получают даже при общих локальных credentials.

После начала sameDB/recovery последовательности: никакого reimport/reset/reseed/SQL-repair. Shared Maria остаётся запущенной, восстановление — только штатный product path после собственного GameServer crash. Запрет глобальных административных SQL и изменения системной schema.

## 5. Crash pre-arm — обязательный короткий guard

До каждой точки72/73 в одном manifest и одним guard подтвердить:

- точные root/schema, PID+start-time+executable+commandline, JAR/code, фактические порты/соединения; это один из двух exact041x lanes;
- нет настоящей IN_GAME сессии; никакого воздействия на040a/041final/другие JVM;
- existing fault window имеет подтверждённую семантику и достигается нужным actor/profile/epoch/operation. Номер72/73 или exit code сами по себе не доказательство нужной границы;
- исходное согласованное состояние и нужные closed pre-crash facts сохранены; recovery-контроллер готов, fault-OFF config подготовлен;
- запас времени на recovery boot, повторный sameDB boot, проверку и stock stop есть; protected040a наблюдается здоровым read-only;
- negative guard (передан protected040a или чужой PID/root/schema) отклоняется ДО любого действия, проверяется на данных/fixture, не попыткой kill живого040a.

Использовать существующий controller/hook72/73, адаптировав exact ownership allowlist к041. Разрешён существующий целевой abrupt GameServer halt либо OS termination только в предусмотренной точке и только после guard. Не добавлять fault-команды в обычный release package/START/STOP. Не расширять на другие типы аварий.

## 6. После crash

Сохранить действительную запись reaching boundary и факт выхода процесса. Fault разоружён; первый recovery boot — той же schema, без импорта, с fault OFF, до допуска обычной новой работы. Проверить canonical state/context/FULL inventory/skills/goal, earned/recovery ownership, native XYZ/instance. Затем второй ordinary boot той же schema: уже применённая операция не повторяется, pending не остаётся зависшим без законного объяснения. После проверок Game/Login штатно остановить, schema/raw сохранить.

При actual crash отсутствие normal RUN_END допустимо только у аварийного эпизода и не заменяет доказательство pre-crash границы. Обе recovery/normal остановки требуют полного штатного end/drain. Не фабриковать RUN_END у оборванного процесса.

Если recovery выявляет дефект — сохранить preimage/raw, исправлять существующего владельца на безопасной новой dev-проверке; затем повторить затронутое окно на финальном кандидате. Не crash заново поверх неразобранной corruption; не очищать pending руками. Если восстановить за бюджет не удалось, G6 не PASS, точная schema/причина сохраняются, безопасный отказ от admission предпочтительнее скрытого начисления. Это не разрешение считать обязательное восстановление выполненным.

## 7. Финальная сдача без вмешательства в040a

В конце041 штатно закрыть свои тестовые процессы. Защищённый040a остаётся нетронутым; дополнительно остаётся один здоровый НОВЫЙ041final для приёмки. Поэтому правило «ровно один сервер» означает один новый041 acceptance, а не kill старого040a.

Самостоятельный STOP поставки обязан управлять только её точной парой и никогда не имеет force fallback. Остановить старый040a или перенести готовый сервер поверх MAIN можно будет только по отдельному будущему указанию пользователя — данный пакет такого действия не разрешает.

## 8. Disposable build output

Допускается переинициализация ТОЛЬКО собственных сгенерированных class/bin/temp outputs внутри нового build041/release041 staging после проверки exact path и отсутствия junction/hardlink на protected runtime. Это разрешение для clean compile, не для удаления исходников, чужих файлов, DB, raw, failed evidence или уже выданной поставки. Старые artifacts не очищать ради места.
