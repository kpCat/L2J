# TASK130 после R40

**PARTIAL. G6 и G10 открыты. Owner gate REQUIRED.** Одна новая попытка GameServer после R39, без restart и без публикации. K10 и ранее доказанные результаты сохранены. Этот документ — единственное изменение нового локального checkpoint. Его родитель `f83b6950134e05127cf1f0ed9691da559f43c4ef`, исходный baseline `b2e6942e5ef635302cab7b2f2e27098837137224`. Последний HEAD `feature/phantom-world` на GitHub сверили через Git и GitHub REST, оба вернули baseline.

## Доказанные границы

- 20 путей исходного checkpoint совпали с `FULL-CLOSURE-20261117/SCOPE_ALLOWLIST.txt`. Повторная проверка K10 сверила 4 941 класс, четыре зависимости, 15 исходных входов в checkpoint и физической сборке. SHA-256 GameServer.jar `7AA35657A0B1E619F4C777BE4FE85BCAB32C2F269D3C1F96A7DC9E8F41CEC549`. В R40 тот же JAR и четыре зависимости. Production Java и отслеживаемый test runner сохранены побайтно.
- G0–G5 и G7–G9 сохраняют PASS на той же K10. Основания и прежние SHA в `FULL-CLOSURE-20261117/RESULT.json` и `EVIDENCE_INDEX.json`. MODEL/SQL части G6 сохраняются. Новый native G6 и публикационный G10 остаются открытыми.
- После R40 `OWN_JVM=0`, `OWN_SQL=0`, `OWN_LISTENERS=0`, `GEO203_PROTECTED207_TRAINING10_EXACT`. MAIN/PLAY, старые TEST, checkpoint216 и Goal024 safety latch сохранены. TASK131, Goal025 и обучение оставлены за пределами запуска.

## Что ограничило R39 и R40

**R39.** В 170,680 с смерть дала `REPLAN_NATIVE_MEMBER_DEAD`, в 260,951 с старый состав полностью исчез из World, в 311,054 с новый exact Party3 восстановлен. Исходный логический срок составлял 600 с. На момент повторного сбора в нём оставалось примерно 289 с. Новое поколение сохранило собственный finite travel budget в 480 с. Участники находились раздельно, поэтому владелец проходил WALK → GK → WALK для каждого последовательно. Штатные три платежа по 9 300 adena видны в трассе, последний `gk_member_arrived` в 792,947 с и `gk_roster_arrived` в 803,003 с. Следующий срез к 813,025 с уже показывает `REPLAN_PARTY_JOURNEY_DEADLINE`. Штатный таймер маршрута исчерпался до подтверждённого боя, а исходный логический срок к тому моменту истёк. Наблюдатель ещё работал. Трасса не устанавливает `SEARCH_LIMIT`, `UNREACHABLE` или отказ AI как первую причину.

**R40.** Собственная MariaDB PID 18292, loopback 33307, схема `l2jmobiush5_phantom_test_130_rendezvous`. Свежие SQL check/prepare, packaging, fixture, artifact linkage и Geo/stock opening прошли с exit 0. Read-only probe доказал маршрут к Yeti на `.4475` и обратно. Live planner при нуле Player принял Yeti на `.5241` с точным consent/skill/role roster, Geo и средствами для оплачиваемого GK. До CUT объявлены quiet opening, короткая история и штатная экипировка. Для этого прогона в отдельной копии `observer-src` добавлен только монотонный предел 599 с для `RequireRecovery`. Ветка выхода прежняя, через штатный STOP. Ни одно доказательное условие не менялось. `javac` exit 0, SHA-256 исходника копии `F3859147F8002AD0A1A825CAB4027E4EB1CC565B3C7C64F64BF2E6C5ECFF2B4F`, класса `BC20B7356D314675D5DD357CB687CBF936EB86D454DCE2B67611813A9235D706`.

- GameServer PID 4432, попытка `native-20261119-131202`, группа `E0653FB00715CA1FED5C436A660D9D1A06E5CC6BBB51129282EE98429928C1AC`, generation 1. Профили 1/3/4, stock Player объекты 268480990/268481007/268481025, epochs 97790449183500/97790454627800/97786529294200. Party3 в 50,162 с, три штатных платежа по 9 300 adena.
- До 180,582 с одно подтверждённое убийство первого поколения, target 268451375, instance 0, spawn generation 1, HP 8 458 → 0, каждому 54 547 EXP и 9 592 SP. Три подтверждённых native Heal `1015:15`. Связанных полезных действий после нового допуска — 0.
- В 220,700 с уже шёл обычный DRAIN с `WAIT_NATIVE_RETENTION_ENDED`. Guardian 268481007 впервые виден мёртвым в срезе 280,890 с внутри этого DRAIN. В 331,094 с `BACKGROUND`, World 0. Отдельная read-only SQL проверка дала три `COMPLETED`, три `online=0`, три durable logical claims и ни одного `background.owned-store`. Штатный `WAIT_DEMAND` назначил `nextDue=1795094585816`, приблизительно 623,9 с от первого среза. До выхода ни typed recovery drain, ни replan, ни новый physical epoch не зафиксированы.
- Последний снимок в 592,023 с, затем `NATIVE130_RECOVERY_NOT_OBSERVED`. GameServer exit 0, verifier и wrapper exit 1. Штатная остановка GS в `13:22:47Z`, owner SQL stop exit 0. `recovery130.complete` отсутствует. Один OS-срез R40 зафиксировал 184,375 с CPU и 4 673 757 184 байта working set.
- Повторный DB-free `PhantomTestLauncher world-coordination130 130` дал 81/81, exit 0, с точным checkpoint `phantom.module.root`. Первый запуск с урезанным staging root дал 79/81, exit 1 из-за отсутствия `PhantomSystem.java` и `PhantomBackgroundService.java` для двух source-only проверок. Исправлен путь запуска. Компиляция наблюдателя заняла около 2 с, два MODEL запуска около 26 с по логам, SQL G0 до готовой аттестации около 9 с, упаковка около 15 с, вся подготовка от старта SQL до GameServer около 2 мин 42 с.

## Локальные доказательства и следующий допуск

Корни исходных файлов `D:\Codex\repos\L2J-phantom-world\.phantom-local\t130f17-r40\runtime-rendezvous` и `D:\Codex\repos\L2J-phantom-world\.phantom-local\logs\PHANTOM-WORLD-COORDINATION-130-FULL-CLOSURE-20261117`. Данные R38/R39 и их индексы остались на прежних местах.

| Файл | SHA-256 |
| --- | --- |
| `native-20261119-131202.stdout.log` | `43CF40914607786D698D1E5AD23A32359B57DDD9540D3F9F203B7152CBB9681F` |
| `native-20261119-131202.stderr.log` | `F81968D8CAA276E1F12D6E5B61A1CD9D25CE8BD3F413AE9B3F76782CF890DFCF` |
| `native130.trace.native-20261119-131202.4432.tsv` | `4440C2CC3780B2EAF3CA4CF30B6266E5AB7A3E53DAD8D82D1297C985F305C810` |
| `native-20261119-131202.process.json` | `A11DB5B157F6C80B3A61361687DC6CC2EF0AD02C131C1AFA7C2217922D772B10` |
| `native-20261119-131202.exit.json` | `BAC97EADFBBE682C53CDE3698F2CE22B3FCA79CE10A6E4C9177D11B46CE3266E` |
| `native130.cut` | `A648BFB94CF679D0350013D766E3CF37E55369A968EE0BE2871F1ADBAB0DC669` |
| `preflight130.cut.native-20261119-131202.4432` | `6C2E0C7D7449ABA8159AF13412BB392083E033E9594EB312693195BED7DC9348` |
| `r40-background-sql-readonly.tsv` | `7D49D3174BE6BA153789F3F737C9CCACD46E5EE7ACF1AE03BE7A2AEE56ECF2C8` |
| `r40-native-launch.log` | `0BAE78EE369EDB11AB0B201A05646BDA043E361D1B41BFB2F2E100E00DCD221B` |
| `r40-sql-stop.log` | `CEF6F467DC95468B01826D6E1F7D1852DECB56703B1C420E05B56AE83A7007F8` |
| `post-r39-world-coordination130-verified-root.log` | `E49E126F7870A0BA8A996D712BFC99A4F24DB2808C8077F5ADE121656456EB3C` |
| R39 `native-20261118-090318.stdout.log` | `58C5AECC89CDA3BDD7285A00197F0C7BD0A64AAC2C77E7C98F3C37B3D37AD659` |

R40 имеет CUT и обоих завершённых владельцев. Повтор R40 на той же БД, снятие launch claim, перенос CUT и правка post-CUT данных исключены. Для ещё одной попытки нужен свежий pre-CUT случай с новым причинным основанием. Дешёвый следующий вход — доказать альтернативный штатный источник и маршрут от фактических post-STORE точек всех троих с запасом на новый native action в установленном окне. Старый положительный DAMAGE R28 относится к K8, а R37 на K10 не завершил весь поздний drain. Эти трассы сохраняются отдельно. Настройка времени игры, усиление допуска и повтор неизменного R39/R40 не дают такого основания.

Scope нового checkpoint ограничен этим `RECOVERY_HANDOFF.md`. Базовый Git-аудит выполнен из существующего detached worktree через `git rev-parse`, `git symbolic-ref -q --short HEAD`, `git status --porcelain`, `git log -2 --format='%H %P %s'`, `git worktree list` и `git worktree list --porcelain`, `git merge-base --is-ancestor`, `git diff --name-status 'HEAD^' HEAD`, `git diff --name-only 'HEAD^' HEAD`, `git -c core.whitespace=cr-at-eol diff --check 'HEAD^' HEAD -- @declared` и `git ls-remote origin refs/heads/feature/phantom-world`. Доставка локальная через exact-path stage/check/commit в том же worktree. Без push.
