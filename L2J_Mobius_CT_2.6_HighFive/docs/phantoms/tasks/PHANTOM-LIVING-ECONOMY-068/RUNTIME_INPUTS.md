# RUNTIME_INPUTS068 — одна безопасная кампания

## Место работы

Source: `D:\Codex\repos\L2J-phantom-world`; module `L2J_Mobius_CT_2.6_HighFive`.
Private staging: `<GitRoot>\.phantom-local\task068`.
Candidate: `D:\PhantomWorld\Living-Economy-068-candidate`.
TEST schema: `l2jmobiush5_phantom_test_068`; release schema: `l2jmobiush5_phantom_release_068`.
TEST runtime: `<GitRoot>\.phantom-local\task068\native-test`.

Создавать только при отсутствии; продолжать имеющийся только по exact собственному manifest. Чужое совпадение не удалять, выбрать явный unique068 suffix и записать все binding. Не плодить068a/b/c ради ошибок fixture. Две own схемы достаточно для всех блоков; native runs последовательно. Предпочтение fresh stock schemas и свои accounts, без копирования личных персонажей владельца. Совместимость legacy components проверяется адресными собственными fixtures, а не запуском старой базы.

## Защищённые источники

067: `D:\PhantomWorld\Personal-Adventure-067-candidate`, schemas `l2jmobiush5_personal_test_067` / `l2jmobiush5_personal_release_067`, ports TEST2248/7948/9248, release2148/7848/9148. Исторически STOPPED; фактическое состояние проверить, не запускать/не останавливать источник.
066R2: `D:\PhantomWorld\Farm-Economy-066-R2-candidate`, schemas `l2jmobiush5_phantom_release_066r2` / `l2jmobiush5_phantom_test_066r2`.
Все057–066, MAIN/PLAY, C:-копия/stash, shared DB сервис и клиент также защищены. Если C:-папка отсутствует, это не blocker: источник runtime на D:.

Source code067 commit: `2939b7054caeafbbfdc913ad5ebe4785c618c8a1`; source branch/report HEAD: `b7e18670704d9b14661594b13d576df97a7ae770`. Предыдущий GameServer SHA256 из report067: `06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58` — для проверки **источника**, не expected hash новой сборки.

## Подготовка и настройки

JDK25, Ant, MariaDB11.4 на127.0.0.1:3308 уже существуют, использовать установленное. Никаких updates/service/global grants. Credentials только из разрешённого local private config, stdout/Git без секретов.

Скрипты выполнять установленным проверенным PowerShell; для Windows PowerShell5.1 кириллический source должен иметь поддерживаемую явную кодировку/BOM. Не менять encoding runtime случайными replace. Взять203 проверенных Geo-региона физической копией с SHA, также необходимые локальные зависимости; без symlink/hardlink/junction на защищённый каталог. Новые JAR/scripts/XML — из одного exact code commit. Build в staging: root jar target может писать dist/libs, не запускать по защищённой поставке.

Новые порты выбрать двумя свободными loopback-тройками после чтения действующих manifests/listeners; записать в own068 manifest и проверять exact PID/startTime/schema/JAR/ports. Не использовать автоматически067/066R2 endpoints и не освобождать занятые чужие. Runtime START/STATUS/STOP самостоятельно работают из candidate без ссылки на checkout; reuse существующих guards минимальным адаптером.

Candidate содержит полный cumulative code/datapack включая067, а не урезанный TEST. Включить явно документированный профиль Phantom/economy/merchant, не полагаться на stock masterOFF. Конфиг200 offline и отдельный bounded active budget не означает изменение пользовательского LIVE. Rate profile, seed/global quotas и pricing plan записать до измерения. Personal QoL master/allowlist не угадывать — source defaultsOFF, тестовые личные акторы только own TEST. Champions blueprint defaultOFF; краткая тестовая включённая конфигурация отдельная, затем восстановить shipped settings.

Ordinary observer через LocalPlayPilot в passive AUTONOMOUS, syntheticOFF; own observation origin в валидной Geo. Он читает состояние, не делает target/attack/grant за product. Native fixtures допускаются для отдельных механизмов, факт отсутствия сетевого клиента не скрывать.

## Поставка и остановка

`START_HERE_068_RU.md` коротко перечисляет startup, ключи merchant/price/champion/Rift, что реально включено и что осталось partial. START.cmd/STATUS.cmd/STOP.cmd + BUILD_INFO/SOURCE_BUILD_INFO и source/JAR/tool/Geo manifests. Price Plan/Apply/Rollback runnable и пример безопасного профиля; staged changes и исходные hashes сохранены.

Финальная сборка на codeSHA, report-only descendant допустим. Одна обычная sameDB/sameJAR повторная загрузка. После всех native/Main runs штатный STOP, exact own JVM0/listeners0/DB writers0 или честный STOP_BLOCKED. Старые resources не изменены; архивов баз/крупных бинарников/секретов в Git нет. Non-force push в feature branch, verify live SHA. Клиентская QA не требуется.
