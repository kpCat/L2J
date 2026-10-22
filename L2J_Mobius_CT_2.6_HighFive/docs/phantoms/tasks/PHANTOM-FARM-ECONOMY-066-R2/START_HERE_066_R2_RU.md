# Старт R2 candidate

**CORE066R2=PARTIAL.** Подтверждены lifetime5000 и реальный расход; earned-only покупка с продолжением и linked Scavenger остаются незакрыты. Подробности в `HANDOFF-R2.md`, `ACCEPTANCE-R2.tsv` и отчёте.

Самостоятельная поставка: `D:\PhantomWorld\Farm-Economy-066-R2-candidate`. Выполнять из этой папки:

```text
STATUS.cmd
START.cmd
STATUS.cmd
STOP.cmd
```

На передаче Login/Game штатно STOPPED, собственных JVM нет, порты2147/7847/9147 свободны. START/STOP проверяют exact PID/start ticks/ownership/JAR и обслуживают только эту R2-поставку. Не запускать её одновременно с другим сервером на той же schema. Force stop не используется.

Release schema `l2jmobiush5_phantom_release_066r2`, MariaDB127.0.0.1:3308. TEST schema `l2jmobiush5_phantom_test_066r2`, отдельный native runner/порты2247/7947/9247, не пользовательский Main. Config содержит lifetime5000, population60, active48, soft32+burst16, FRESH age0, syntheticOFF. Native персонажи живут по расписанию/Ecology; Task066View — пассивная точка наблюдения access0, без клиента/GM/controller. На последнем замере Elf59 естественно сражался после restart.

Production code `f0fd6a26c2c3380f097678fec563ec201c2d552a`; GameServer SHA256 `8B9C3376B4F6D66DE4DBFB8A7BCCA6D9F1D8A4C2E451344739297A467BF515AD`. `BUILD_INFO066R2.json` и `SOURCE_BUILD_INFO066R2.json` содержат точные hashes/provenance. Финальный source ZIP: `source/SOURCE066R2-f0fd6a26.zip`. Пути исходной сборки в receipt — provenance, runtime от checkout не зависит.

Все старые057–066 поставки и БД сохранены. Не копировать их поверх R2 и не переимпортировать R2 DB. Не делать cursor/goal revision edits, не клонировать identity и не выдавать ресурсы в активной игровой сцене. Пожизненная выдача доступна только новым Phantom profiles, без ретрокредита legacy. Отдельный TEST accelerated refill не означает5000 естественного расхода в Main.

После любого запуска завершать `STOP.cmd` и проверять STATUS. Пароли/Geo/JAR/raw DB не публиковать в Git. Publication SHA и финальные receipts находятся в `PUBLICATION066R2.json`/`evidence` этой поставки.
