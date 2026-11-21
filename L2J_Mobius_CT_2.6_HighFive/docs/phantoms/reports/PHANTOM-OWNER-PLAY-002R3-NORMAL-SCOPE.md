# PHANTOM-OWNER-PLAY-002R3-NORMAL-SCOPE

21 ноября 2026 г. Базовый HEAD локально и на GitHub — `62e1d9f7de01f557a81e96d89d54686e10d6496b`.

## Исправление

- `PhantomSystem` передаёт источнику первичного планирования актуального владельца из execution control. `PhantomHistoricalBackgroundService` открывает привязанный к профилю NORMAL scope для solo owner. Штатный `tryAcquireAction` решает, доступен ли Player с учётом фазы, эпохи и блокировок. Scope и action lease закрываются до STORE.
- `prepareCurrentAuthority` возвращает RETRY для отсутствующего первоначального Goal. Ecology повторяет попытку в своей очереди после выхода из refresh. Симуляция выполняется за пределами нового scope.
- `PublicationPending` возвращает RETRY. Ecology ждёт публикации без повторной планирующей материализации. Существующий TRANSFER через CAS публикует Goal и `background.catchup` вместе. Следующий проход читает сохранённую пару. Штатный STORE в отсутствие Goal сохранён.

## Проверки

- JDK 25.0.4.1, изолированный output. `javac -Xmaxerrs 8 @full-red.args` (exit 0), где файл аргументов задаёт `--release 25 -encoding UTF-8 -g:lines,vars,source`, все исходники модуля Java, четыре библиотечных JAR, `-sourcepath test/java`, один focused источник и отдельный каталог классов.
- `java @run.args` из временной папки, с `-Xmx3g`, теми же библиотеками и главным классом `org.l2jmobius.tests.phantoms.PhantomOwnerPlay002R3Checks`. RED exit 1, точный результат `catchup.baseline.action_lease` при ACTIVE solo owner. GREEN exit 0, `OWNER_PLAY_002R3_NORMAL_SCOPE_PASS`.
- Регрессия выполняет штатный планировщик на штатном шаблоне Human Fighter и реальной GeoData, с узким каталогом для ветки исторического idle. Проверены отказы без scope, при refresh, блокировке, чужом owner, REQUEST и закрытой границе. После открытия хранилищ заново на стадии REQUEST проверены один атомарный TRANSFER и точное чтение Goal и companion без повторного плана или продвижения календаря. Временная реализация JDBC хранит только версии и байты компонентов. Игровой SQL и запуск сервера в проверку не входят.
- Слушатели `127.0.0.1:2106/7777/33331` сохранили PID `12008/12500/13724`. SHA-256 рабочих GameServer.jar, LoginServer.jar и файла Studio WIP совпали до и после. Основная поставка, игровой клиент и базы сохранены.
- Mojibake-маркеры в изменённых файлах проверены.
- Escaped Cyrillic в изменённых файлах проверены.

После отдельного разрешения владельца требуется один цикл STOP → свежий JAR → START для основного `dist`. Проверка движения, kill, EXP и SP остаётся отдельной приёмкой.
