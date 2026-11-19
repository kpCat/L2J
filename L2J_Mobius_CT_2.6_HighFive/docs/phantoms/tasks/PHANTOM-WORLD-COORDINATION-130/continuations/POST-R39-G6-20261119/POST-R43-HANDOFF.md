# TASK130. Продолжение после R40

**G0–G9 подтверждены. `ownerGate=REQUIRED`.** G6 закрыт одним native trace R43. Публикационный G10 отдельно подтверждается обычным fast-forward push и независимым readback GitHub HEAD, parents, scope и байтов каждого полного тела до и после изменения. Путь к receipt `r43-github-readback.json` в каталоге локальных доказательств ниже.

Входной checkpoint `7b5e2da2b3ac4a525628f5d13afab36e90e84410` чистый. В нём сохранён [handoff R40](RECOVERY_HANDOFF.md), родитель `f83b6950134e05127cf1f0ed9691da559f43c4ef`, исходный feature baseline `b2e6942e5ef635302cab7b2f2e27098837137224`. Exact scope входа — прежние 20 путей и handoff R40. `diff --check` прошёл.

G0–G5 и G7–G9 сохраняют PASS на K10 `7AA35657A0B1E619F4C777BE4FE85BCAB32C2F269D3C1F96A7DC9E8F41CEC549`. Production Java, отслеживаемый test runner, verifier и K10 не менялись. Сборка/запуск изолированных тестовых копий использовали тот же JAR и четыре зависимости. Свежий DB-free MODEL на полном checkpoint root, `PhantomTestLauncher world-coordination130 130`, exit 0, 81/81. SQL и model evidence G6 сохраняются. Старые R38/R39/R40 и доказательства других gates остаются в прежних местах.

## R41. Защита только DD-лидера

- Pre-CUT fixture на том же штатном `equipBeforeCut` дала только DD-лидеру комплект 6373–6378. У него P.Def. 642,75 → 1 037,76 и HP 4 987 → 5 432. Показатели guardian и healer совпали с R39. Цель проверки — устранить наблюдавшуюся раннюю смерть DD, которая предшествовала длинному раздельному возвращению. Свежие SQL check/prepare, package, artifact check, fixture и открытие дали exit 0. Приватный Java observer SHA-256 `3445D590E64AC4453F20FDAD9CE757AA88871CEBB15B35DB494B06D9C7169621`, class SHA-256 `EC9B9E36B01A478A7D3162E4197EDE10CC0AFF48ECFA3D33E34CF8E810D2CE05`. Предел наблюдения `RequireRecovery` 599 с.
- `native-20261119-133808`. GS PID 17800, SQL PID 20336. Group `E81B543B8A5B4383F59EF2D7BAF823353D537DBAE47C5FA60F680C82BFC9B24A`, профили 1/3/4, объекты 268480990/268481006/268481023. Начальный источник `21320@.5241`, один образец stock Heal, ни одного подтверждённого группового убийства. К 200,843 с `REPLAN_THREAT`. DD впервые виден мёртвым в 301,100 с, во время DRAIN. World 0 к 351,279 с, `RECOVERY_STORED` и `BACKGROUND` к 361,317 с. Отдельный read-only SQL watcher истёк до `RECOVERY_STORED`, SQL-снимок старых эпох отсутствует.
- Тот же groupId и stock Party3 в generation 2 к 411,460 с, другой источник `21320@.4475`. Наблюдаемая последовательность лидера включает три WALK до GK, платный GK за 9 300, затем ещё два WALK. К 591,972 с начался последний из них, до нового DAMAGE/KILL время не дошло. `NATIVE130_FINITE_EVIDENCE_DEADLINE`, GameServer exit 0, verifier/wrapper exit 1, SQL stop exit 0. Финальный postflight exact.

## R42. Перенос длинного WALK на исходный общий маршрут

- Сравнение R41 `.5241` и `.4475` показало один и тот же законный GK и дополнительные Geo-эпизоды только у `.4475`. Открытие `.4475` могло бы перенести этот WALK на первый совместный маршрут. В чистом TEST130 использован уже существующий pre-CUT режим `PROVED_HOT_SPRINGS_APPROACH`, те же экипировка, test observer и K10. Самостоятельная read-only проверка `.4475` прошла, без CUT и без World Players.
- `native-20261119-184242`. GS PID 23244, SQL PID 8168. Live startup показал восемь предложений, среди них `21320`, `21318` и `21315` на `.5241`, а `.4475` отсутствовал. `WAIT_PHYSICAL_PROOF_BUDGET` и typed `START_FAILED`, CUT и наблюдаемого gameplay не было. GameServer exit 0, verifier/wrapper exit 1, SQL stop exit 0. Этот boot учитывается в лимите.

## R43. Первая цель из подтверждённого списка .5241

- Основание — `21315@.5241` присутствовал в восьми live предложениях R42. R37 после отказа на этом источнике зафиксировал 145 нового урона на `21318@.5241`, но тот прогон не подтвердил финальный STORE. Это обосновало отдельную проверку короткого местного маршрута. В R43 изменился только pre-CUT выбор исходного источника в изолированном наблюдателе. Он принимал реальный production offer `21315@.5241` лишь после обычного допуска. После CUT исходный production planner и все критерии приёмки действовали полностью.
- Свежая read-only проверка нашла два живых кандидата 21315 с запасом группы, проверила WALK/GK и Geo-отход. Live preflight заново принял источник `21315@generated.farm.5241a753fc2261cfc117ac7b.anchor` при 0 World Players. Приватный observer source SHA-256 `C36BDE1C2E4286BEA96B3757E399CE55A2D88EDD22BAEC8124C4756B7AF2550D`, class `DAD1C1462F8855B8E97766D5526C35D0F6504C08C4E0D8BD65CC600375C15A59`.
- Один trace `native-20261119-184931`, GS PID 12280, SQL PID 17484 на собственном loopback 33307. Group `0AC400B8A98BCE426F7259E528818BBE34523E0A37187334FCA79AB239D778E9`. Профили 1/3/4, stock Player объекты 268480990/268481006/268481023. Исходные epochs `118034453185500/118038482830200/118038488239600`, root revisions 0. Party3 к 40,190 с, обычные WALK и оплаченный GK. К 220,702 с native kill цели 268452016, spawn generation 1, по 51 677 EXP и 8 860 SP. Один наблюдённый stock Heal `1015:15` на 345 HP за 66,2014 MP.
- `REPLAN_GROUP_DAMAGE` к 501,425 с. World 0 зафиксирован на этой же выборке, `RECOVERY_STORED` и `BACKGROUND` к 511,454 с. Production replan под ограждением `withStoredNativeAbsence` всех старых участников сохранил groupId и исключил `21315@.5241`. Новый источник `21318@.5241`, generation 2, root revisions 1. Штатная Party3 к 561,631 с. Во время новой материализации отдельная read-only SQL транзакция подтвердила три сохранённых native-context `COMPLETED`, три logical claims и отсутствие `background.owned-store`. Новые epochs `118572581684400/118574581842300/118575093598900` подтверждены последней выборкой до действия.
- После снимка 591,715 с и до монотонного предела 599 с actor 268480990 в epoch `118572581684400` нанёс подтверждённые 214 HP цели 268452013, instance 0, spawn generation 1. HP 8 317 → 8 103, damage sequence 0 → 1, stock damage 0 → 214, новых reward delta 0. Это одно новое полезное действие после replan, отдельно от первоначального убийства.
- В той же трассе `RECOVERY_PROOF_PASS`, штатный STOP и `RECOVERY_STORE_PASS claims=3`. Финальная проверка прочитала canonical SQL STORE для всех трёх, связанные `COMPLETED`, отсутствие owned STORE и ноль Player в World. `recovery130.complete` связывает PID, JAR, попытку, groupId, generation и roster. Отдельно повторён штатный outcome verifier, exit 0. GameServer, wrapper и остановка SQL exit 0. Postflight `OWN_JVM=0`, `OWN_SQL=0`, `OWN_LISTENERS=0`, `GEO203_PROTECTED207_TRAINING10_EXACT`.

## Матрица приёмки K10

| Gate | Результат и основание |
| --- | --- |
| G0 | PASS. 4 941 класс, 4 зависимости, 15 отслеживаемых исходных входов. Свежие R43 G0 identity, JAR pin, schema, stock Geo и exact protected hashes. |
| G1 | PASS. Восемь SQL modes R35, составы 2/3/9, CAS, rollback и cold JVM. |
| G2 | PASS. 81/81 MODEL, включая текущий R41 `world-coordination130`, typed admission и границы уровня. |
| G3 | PASS. F7/STORE/cold SQL и focused MODEL на K10, включая accepted Supply и stale callback. |
| G4 | PASS. R38 native Party3, WALK/GK/Heal, отдельное убийство и native pickup, естественный STORE. |
| G5 | PASS. R38 sameDB/sameJAR restart, точный byte witness и второе отдельное убийство с новой наградой при том же договоре. |
| G6 | PASS. R43 и ранее завершённые MODEL/SQL. Refusal, old STORE, другой источник, новый stock состав, positive DAMAGE и final STORE в одном оригинальном trace. |
| G7 | PASS. C15 на K10 с 128 distinct World IDs и epochs в трёх снимках за 61 133 мс. |
| G8 | PASS. 246 targeted MODEL, merchant 3+3, Supply и native ordinary-human Heal. |
| G9 | PASS. R43 owned CPU 210,125 с, working set 4 688 093 184 байта, один OS-срез и отдельный read-only SQL, штатный STOP и проверенный OFF. Исторические K10 измерения сохранены. |
| G10 | Локальные source/JAR, scope и focused gates проверены. Факт push и независимая сверка GitHub фиксируются отдельным receipt после записи этого коммита. |

После R39 всего четыре новых GameServer boots. R40 дал одно initial kill и три Heal. R41 дал один Heal. R42 остановился до CUT и не выполнил gameplay. R43 дал одно initial kill, один Heal и одно новое действие после replan. Приватные `javac` R41 и R43 заняли около 4 с суммарно, новый MODEL около 13 с, работа SQL G0 и package отражена в отдельных `r41`–`r43` логах. Дополнительные рестарты, обучение и старые TEST не запускались.

## Исходные доказательства

Данные R41/R42/R43 лежат в `D:\Codex\repos\L2J-phantom-world\.phantom-local\t130f17-r41`, `D:\Codex\repos\L2J-phantom-world\.phantom-local\t130f17-r42` и `D:\Codex\repos\L2J-phantom-world\.phantom-local\t130f17-r43`. Логи оболочек и postflight лежат в `D:\Codex\repos\L2J-phantom-world\.phantom-local\logs\PHANTOM-WORLD-COORDINATION-130-FULL-CLOSURE-20261117`.

| Файл | SHA-256 |
| --- | --- |
| R41 `native-20261119-133808.stdout.log` | `C2DC0AA8363DBDF435D1823253E1F0DA2C92BDDE0A7CA526A58D2FC1A0CE3536` |
| R41 `native130.trace.native-20261119-133808.17800.tsv` | `22F417783B1459E0F6008C7C3BA9BE96DE99B444A365AE32CC14A3FE8AC9E664` |
| R41 `r41-native-launch.log` | `C1CC2FADF730DC3C542348DB5D2E1B5A531B3FBCEE99CE66EC1348AB9E4F3C09` |
| R41 `r41-sql-stop.log` | `11AE452844EE6F94EAC52D26BE53D44A60DA65335FC01B95C5A3EDA7FECBC23A` |
| `r41-model.log` | `5DFA88A382BC7C8358BAE06690FBF5FE929462A56533DFE90896E8BC5027EC1A` |
| R42 `native-20261119-184242.stdout.log` | `3331E5C16E4476F523C7BD889549197DE41BBD50246E28CE4C0C4013DCA80C73` |
| R42 `r42-rendezvous-initial-precut-opening.log` | `8EB612CD87C0D5040E91567A548B70232CB8EF45EFD1AFA6AAA11C69D347A04E` |
| R42 `r42-native-launch.log` | `4BE7E542F7D19A083222825F6A11F4734610C7E00EDB34B98ED89CCE787D5122` |
| R42 `r42-sql-stop.log` | `AF374D95DD9DDB715D079A06A38107761179A038D3A1DF66F3330B8E0F351706` |
| R43 `native-20261119-184931.stdout.log` | `1796B481FCBACE68166B2852A78B181CE9C6C9AA37C9E4EC5E2C57CEEC8AE533` |
| R43 `native-20261119-184931.stderr.log` | `877578D49F103B118066C60ED2F1470AA65C35F74F6A7FF3C8032A447544D82A` |
| R43 `native130.trace.native-20261119-184931.12280.tsv` | `630782F07FC370D5E11B4AADE3D5B73FC007B1497B9368D7BD7FF287E3A4D23A` |
| R43 `preflight130.cut.native-20261119-184931.12280` | `CED092E4783E67B66438DB41B4BAA31F47A6233B4EFAE415311EF90E8A9C20CA` |
| R43 `recovery130.complete` | `43AAD7B6BC5F9888CBE54638170289794BDA4D2B1A09809E5996C53F7009D320` |
| R43 `r43-rendezvous-initial-precut-opening.log` | `F64C3C797248F36DFF4249F730168EE56C65382DDC0CE37D90528EB65D945CA9` |
| R43 `r43-new-epoch-sql-readonly.tsv` | `852E0B8FA09DED47F90815F9279B70D11D00A79B6B94464C0E074223ECACE0E9` |
| R43 `r43-native-launch.log` | `5192DFD929A4E754383E61642D92038E045C5D9A9041A40AF7B20879D4E167F6` |
| R43 `r43-sql-stop.log` | `F3801E577FA9148F5D5DC4AC900A4C64969ECED9BF5AAFCAB25E870C6CCFE225` |

R41/R43 CUT зафиксированы. R42 уже использовал свой launch claim. Все три процесса и их SQL завершились, их состояния и raw evidence сохранены. `ownerGate=REQUIRED`.
