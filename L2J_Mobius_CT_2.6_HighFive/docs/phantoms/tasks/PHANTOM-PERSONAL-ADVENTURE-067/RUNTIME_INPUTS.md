# RUNTIME_INPUTS067 — собственная изолированная среда

**Единственный Git checkout для записи:** `D:\Codex\repos\L2J-phantom-world`, ветка `feature/phantom-world`; `C:\Users\ZBook\L2J_Mobius` и его два stash read-only. Исторические releases057–066, в т. ч. `D:\PhantomWorld\Farm-Economy-066-R2-candidate` и schemas `l2jmobiush5_phantom_release_066r2`/`..._test_066r2`, защищены от запуска и изменения. Их JAR/GeoData/manifest только для чтения через точную проверку hash/provenance; не делать wholesale migration личных персонажей владельца.

**Новые имена (только если сейчас отсутствуют):**
- private staging `<GitRoot>\.phantom-local\task067\`;
- TEST schema `l2jmobiush5_personal_test_067`, собственный runtime `.phantom-local/task067/native-test`;
- release schema `l2jmobiush5_personal_release_067`, cumulative candidate `D:\PhantomWorld\Personal-Adventure-067-candidate`.

Перед созданием убедиться, что ни одна schema/path/port не занята/не создана чужой задачей. При совпадении только собственный verified ownership manifest позволяет продолжение; при конфликте выбрать другой уникальный суффикс, не очищая существующее. Ports две различные локальные 3-ки, выбранные после проверки manifests 065/066/066R2 и текущих listeners. Никаких внешних сетевых проб или выключения не своих Java. JDK25/Ant/MariaDB11.4 уже установлены: использовать текущее, ничего не обновлять.

GeoData — копия 203 существующих проверенных файлов в собственную среду с физическим hash check, **никаких symlink/hardlink/junction на protected release**. Game/JAR собирается из точного committed codeSHA, скрипты+XML той же ревизии, private credentials не публиковать. Не менять shared DB accounts/service/datadir/global config.

**Доказательства без пользовательского клиента:** допустимы native integration на own TEST с реальными stock instance/item/fishing/skill stat components и только синтетическими pre-measurement test inputs; использование сервисного test seam для проверки personal policy разрешено, но **fake GameClient/auth network impersonation запрещено**. Не просить пользователя входить в игру или менять реальные AllowedAccounts ради теста. Любую специфику отображения UI клиента оставить `CLIENT_QA=DEFERRED_BY_OWNER` до его будущей игры.

**Вариант standalone:** при рабочем GameServer Main пройти один короткий normal startup/restart и проверить загрузку нового QoL datapack; если для этой конкретной кампании собственная полная база не нужна, предпочесть own TEST с настоящими native entry operations, а не повторять часы Population/Observer кампаний. Однако конечная runnable candidate должна иметь launchable config, own schema и start/status/stop. Не изменять прежние releases.

**Вывод:** `D:\PhantomWorld\Personal-Adventure-067-candidate\START_HERE_067_RU.md`, `START.cmd`, `STATUS.cmd`, `STOP.cmd`, BUILD_INFO, sourceSHA/JAR SHA, Geo manifest, точные ports/schemas, no active task JVM at exit. Если JAR не подтверждён, не выдавать false runnable PASS. Публикация на `feature/phantom-world` обычным non-force push с кодом и компактными отчётами; без бинарных JAR, GeoData, секретов и private logs в Git.
