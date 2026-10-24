# OWN074 runtime

TEST и RELEASE созданы по одному разу после absence/owner/free-port guards:123stock SQL scripts и неизменённая Economy071 migration SHA `e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3`, authority.task_id74. Private credentials/hexid не публикуются. Отдельные schemas `l2jmobiush5_phantom_test_074` / `l2jmobiush5_phantom_release_074`; installation UUID зафиксирован OWN074 marker.

Один candidate `D:\PhantomWorld\Living-Systems-074-candidate`, TEST `.phantom-local/task074/runtime-test`. Оба содержат source104f87 / GameServer9b24 / LoginServer3bb59. Первая сборка1f3a/19677 и все её receipts сохранены. Единственная source correction меняла только остановленные OWN074 artifacts: общий operator.lock, DB0/STOP guards, byte backups вне checkout в candidate/local-play/provenance, новые manifest hashes. UUID/schema/config/data не переинициализировались.

| Запуск | PID Game/Login | Подтверждённый RUNNING UTC | STOP requested / completed UTC |
|---|---|---|---|
| Main |2456/11628|01:41:30|01:45:34 /01:45:45|
| sameDB restart |25628/16256|01:47:54 (mtime полного RUNNING status log)|01:55:19 /01:55:30|

Первый useful Main >4min после консервативного RUNNING observation; restart >7min. Целевые2–4min restart превышены из-за задержки внешнего PowerShell stdout pipeline до закрытия дочерних JVM; это не новый bootstrap/soak. Поздний `RESTART_READY_OBSERVED01:55:29` с отсутствующим Game PID — артефакт этой обвязки после STOP, не время старта и не lifecycle proof. Прямой вызов того же operator script штатно завершил обе роли за11s; исходный журнал оставлен без переписывания.

Сравнение quiescent snapshots:216identities и17profiles SHA совпадают; merchant quota `f0a785684859af4734cdfd2fcf4c120901722ab3185eb8eafc277c21c3e0db01`,200offline owners и600store lines неизменны. Bootstrap capital/stock не повторялся. Vortexbra/profile2: level2→3, EXP87→367, SP6→14; ordinary item rows765→767. Некоторые normal profiles умерли; stock HP/respawn не ремонтировались. Это bounded native useful activity, не causal B itinerary/M1 proof.

Фактический SELL stock получен join по owner+native object ID, не по reference catalog:49SKU/450SELL lines из600общих lines.800каждогоD1463/C1464/B1465/A1466, по8stores. S1467 и Blessed3947–3952 отсутствуют именно в SELL; это не аудит всего native MANUFACTURE. Бесконечного bootstrap refill, ценовых/rate/reserve правок и фиктивного производства нет; полный SELL SKU список в MAIN snapshots.

Stock heap Game Xms2g/Xmx4g,16active phantoms,200offline merchants, loopback ports release2106/7777/9154 и test2254/7954/9254. CPU sample включает startup+весь первый run: Game195.42CPU seconds за305wall seconds, working set4.60GB,161stock JVM threads; это не steady-state/scale benchmark. Новых1thread-per-actor producers нет, navigation shared max2.

Native TEST controls шли в отдельном owned JVM/own schema. Первый actor1 paid control не дошёл до materialization store после ошибки test cleanup и исключён из durable PASS; в diagnostic TEST сохранён его COMMITTED receipt при старом physical state, без replay/компенсации/reset. Это не доказательство crash recovery. RELEASE не содержит этого актора. Actor2 — единственный исправленный causal control с успешным graceful sameDB restart. Последующие checks переиспользовали сохранённые identities/resources, а helper-only updates получали committed archive/class hashes без JAR rebuild.

Эквивалентные operator команды (CMD wrappers поставлены, клиент не открывался):

```powershell
& 'D:\PhantomWorld\Living-Systems-074-candidate\tools\Runtime074.ps1' -Action Start -RuntimeRoot 'D:\PhantomWorld\Living-Systems-074-candidate'
& 'D:\PhantomWorld\Living-Systems-074-candidate\tools\Runtime074.ps1' -Action Status -RuntimeRoot 'D:\PhantomWorld\Living-Systems-074-candidate'
& 'D:\PhantomWorld\Living-Systems-074-candidate\tools\Runtime074.ps1' -Action Stop -RuntimeRoot 'D:\PhantomWorld\Living-Systems-074-candidate'
```

Final: TEST/RELEASE STOPPED; each own DB0; all task074-marked JVM0; six own listeners0.203original Geo SHA и два начальных protected file SHA совпали; собственные203copies проверены Validate/Start. Старые071–073 DB/actors/candidates не запускались/не менялись, human client/accounts не использовались, MariaDB service оставлен работать. Визуальная owner QA отложена.
