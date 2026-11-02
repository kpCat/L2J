# Автоматическая аттестация G0

Из корня checkout в обычном Windows PowerShell выполните одну команду:

```powershell
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\Invoke-G0IdentityAttestation.ps1
```

Дальше всё выполняется автоматически: три focused-группы, свежий запрос, передача pinned bootstrap административному helper через штатный Start-Process -Verb RunAs, проверка ответа, однократное принятие и cleanup. Владелец подтверждает только стандартный UAC, если Windows его показывает. При «Никогда не уведомлять» окна может не быть: обязательны настоящий elevated token, High/System integrity и проверенное включение SeDebugPrivilege, а не наличие диалога. Реестр и политики не изменяются.

Поддерживается Windows PowerShell 5.1. Без параметров используется подтверждённая политика этой машины: MariaDB, NT SERVICE\MariaDB, `%ProgramFiles%\MariaDB 11.4\bin\mysqld.exe`, `%ProgramFiles%\MariaDB 11.4\data\my.ini`, порт 3308. Для другого заранее проверенного размещения тот же entrypoint принимает Executable, Config, ServiceName, ServiceAccount, Port. Автопоиска другой службы или fallback на другую MariaDB нет. CheckOnly выполняет лишь три focused-группы и никогда не вызывает RunAs.

Обычный caller остаётся неповышенным. При EnableLUA=0 или уже повышенном caller сценарий не ослабляет это требование и возвращает BLOCKED. Если Codex не поддерживает RunAs/CIM в своём контексте, используйте эту же единственную команду в обычном терминале владельца. Не открывайте административный терминал и не переносите вручную request, nonce, SHA или пути.

Успех: exit 0 и `G0_ATTESTATION_PASS;OS_IDENTITY_ATTESTED;G0=NOT_ACCEPTED;SQL=0;GameServer=0;TASK114=0`. Это PASS механизма OS identity. Полный campaign G0, SQL/schema/UUID guards и запуск задач этим не разрешаются. Их прежние проверки не изменены.

Отмена RunAs, timeout 120 секунд, недостаточные права, недоступный файл, ACL drift, отсутствующий/частичный receipt, PID/creation FILETIME/runtime EXE/CommandHash drift или replay означают exit 2 и `BLOCKED:G0_*`. Повторного автоматического helper launch нет. В отчёт не выводятся secrets, полная service/runtime command line, nonce, request SHA или содержимое конфигурации.

Исходники физически пинятся и хешируются до focused gate. Эти значения автоматически передаются в bootstrap; административный процесс повторно проверяет байты native reader до Add-Type и каждый dependency под удерживаемым handle. Между тестами и запуском запрещены write/delete sharing. Ничего вручную сравнивать или вставлять не требуется.

Запрос находится в fresh private TEMP-каталоге caller+SYSTEM, исключает reparse/alias и удаляется закрытием delete-on-close handle, включая crash caller. В обычном finally освобождаются все locks и удаляется только собственный пустой каталог без рекурсивного обхода. После crash пустой каталог может остаться; он не содержит принимаемого запроса. Другие каталоги не очищаются.

Helper пишет только собственные `%ProgramData%\PhantomG0Attestation\<SID>` и receipt с owner Administrators, protected exact DACL, caller read+append без overwrite/delete/WRITE_DAC/WRITE_OWNER. Неизвестные ACL не исправляются. Receipt публикуется атомарно из `.pending`, живёт максимум 30 секунд, потребляется через exclusive handle необратимым `USED`. Consumed/expired receipts остаются защищёнными replay tombstones; они не являются рабочими временными запросами и не требуют ручной уборки. Чужие receipts не удаляются.

После итогового статуса STOP. MariaDB, службы, системные ACL, конфиги, БД, production Java, WIP109 и TASK114 не меняются и не запускаются этим сценарием.
