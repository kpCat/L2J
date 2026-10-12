# CONTEXT040 — восстановленный рабочий контекст

## Происхождение и предел достоверности

Основа: текущая постановка пользователя; извлечённые договорённости диалога «Ветка · lineage»; опубликованные GOAL039, DESIGN039, ACCEPTANCE039, SOURCE_MAP039, CHECKPOINT039 и RESULT_HANDOFF039 на BASE040; переданный Markdown исследования. Remote при подготовке пакета сравнен с BASE040: identical, ahead0/behind0.

Восстановлены рабочие требования, но это не утверждение о побайтовом чтении всей старой переписки. Частные raw/БД/архив039 находятся на компьютере пользователя и при подготовке пакета не проверялись. Их точечную верификацию выполняет Codex локально. Публичный отчёт039 является основанием для плана, но не новым runtime PASS040.

## Последний известный итог

`TASK039=PARTIAL_VERIFIED; M1=OPEN; REAL_FINAL=NOT_RUN`.

Final publication commit: `96bf5d2cba14bf6ad561f334ec49f9010716681b`.
Final production source039: `09e5f38a6fe3f2295f0c0040ab55c1c914c3234e`.
Jar4 SHA256: `3828AD719F6600EE07DE011D8C28FC1AFE2AAA232C1F517D97B6D0BFB4F02005`.
Рабочий remote — `experiment/m1-candidate007-observe008`, не старый `feature/phantom-world`.

| Gate039 | Статус | Что действительно осталось |
|---|---|---|
| G0 INPUT | PASS | Новый preflight040 всё равно необходим |
| G1 ACTION | NOT_VERIFIED | Native melee119/caster153 по5 циклов были; вся natural цепочка обход→damage→cycles→применимый loot не замкнута |
| G2 GROUP | NOT_VERIFIED | Нет двух all-enrolled natural6–8 групп на Jar4; Jar2 B1/6, Jar3 C6/7 — не PASS |
| G3 SAVE | PASS | Три отдельных all-birth stopped Jar4 runs; FULL_SAVE/global COMPLETE retained0. Это не cross-boot goal proof |
| G4 LIFE | FAIL | Native death45s→recovery→useful damage PASS; полноценный productive background→полезный return не доказан |
| G5 RESTART | NOT_VERIFIED | Два actual sameDB restarts есть, но недостаточно полной state/context/items/goal/reward producer-chain |
| G6 CRASH | NOT_VERIFIED | Старый actual OS72 есть; recovery records отсутствуют, x72 pending1. x73 не выполнен; новые crash после resume запрещены |
| G7 REGRESS | PASS | Jar-first93/93, lifecycle11/11 и перечисленные защищённые suites; переоценить затронутые гарантии на040 |
| G8 DELIVERY | PASS по receipts | commit/push/archive/Java0 как завершение039; в040 финальная политика runtime иная |

## Важные конкретные наблюдения039

Natural Probe J4 140.7505711s:7 enrolled,126 fresh samples,maxgap1.2966692s. Оригинальные epochs сохранены через127 samples.119 class0/lvl12:5cycles,14damage,7rewards/7kills,EXP+461/SP+19.153 class10/lvl8:5cycles,6damage,8rewards/6kills,EXP+1769/SP+80. Остальные:113=3cycles,123=2,152=1,155=0,173=3. EXP/SP у155 без собственного damage не доказывает собственную полезную работу.

Profile57: away90.200s, ранний/поздний STATE14459/PNC3244 и EXP23874/SP2129 одинаковы. Productive background FAIL; return измерение имело exporter GAP. Нельзя объявлять, что исправление exporter автоматически починило жизнь57.

Profile119: четыре реальные HISTORICAL_FARM commit с цепочкой EXP106921→108190→109459→110728→108589, SP13946→14006→14066→14126→14186. Фоновая работа реальна, затем законная смерть. При следующем старте DEAD до Synthetic START; полезный native return не доказан. Это не основание лечить HP/SQL или требовать монотонного EXP вопреки death loss.

Jar3 profile28 начал полезное damage только через98.534s, затем выполнил8cycles; FAIL полезного прогресса не равен автоматически сломанному бою. Исторические437,515,576/long pauses требуют причинного разделения. Старые результаты нельзя улучшить задним числом.

## Что039 уже изменил

1. PhantomVisibleAutoPlay: наблюдение реального progress перенесено до resource early return; причинный A02 RED→GREEN.
2. Там же: существующий Session monitor защищает snapshot восьми resource scalars вместо смешанного recovering=true/since=0.
3. PhantomBackgroundTransaction: optional immutable witness на действительной existing owning commit boundary, success только после commit; projection refresh передаёт locked canonical facts. Это не новый persistence engine.
4. Private Contract032Observer: fsync перенесён из каждого record в конец существующего drain64 batch и RUN_END. Ring512, caps и радиусы не повышались. Старые dropped runs остаются GAP.

G3 текущего039 доказан в трёх отдельных runs:634/634 births-terminal,102858commits,606stopped;957/957,80010,949;284/284,14816,284. Нельзя объединять три run в один вымышленный lifetime или переносить PASS через разные JAR без проверки применимости.

## Безопасность и прежние ограничения

Из CHECKPOINT039, запись2026-10-12T12:51Z: latest human authority запретил дальнейшие crash72/73, intentional termination и corresponding injectors. Снятия этого запрета в доступной текущей постановке нет. После resume старый x72 actual crash подтверждён, обе fault-off recovery boot уже закончились без recovery-records; pending1 сохранился. Не запускать старый Crash039.ps1.

К концу039 собственные JVM остановлены; DB/worktree/raw сохранены; очистка/удаление остановлены. Это исторический census, не доказательство отсутствия процессов сейчас. Проверять PID вместе с start-time, command line, root, DB и портами.

PLAY/MAIN и чужие данные не служат расходным материалом. Старая039 БД тоже теперь historical input: только чтение/согласованный export, не recovery поверх оригинала. Новый040 runtime — только новый точный набор owned paths/schemas.

## Наблюдатель и пользователь

Пользователь прямо требует сохранить уже существующее поведение: observer существует без REAL; при входе настоящего игрока автоматически «прилипает» к нему. Не подменять это выдачей Synthetic за REAL и не переименовывать локальный action-test в клиентский тест.

Публично прочитаны LocalPlaySyntheticHumanService.java и LocalPlayM1Observation.java. Первый — отдельный bounded transport с525s native Synthetic-session, heartbeat и лимитом5 started runs; второй — чистые решения approach/identity/envelope. Они не являются доказательством, что весь требуемый долгоживущий observer-handoff уже готов. Codex обязан найти фактический owner привязки в localplay/phantoms и частных032/039 harness; сохранить рабочую реализацию, устранить пробелы, не строить дубликат.

Сохраняются разные уровни: пассивное наблюдение, native SYNTHETIC presence для автономного испытания и настоящее REAL_LOGIN/EnterWorld. Прилипание фокуса не разрешает брать управление пользователем; Pilot actions имеют отдельное согласие/lease. Ранее использовались `D:\Tools\L2ClientTools` и команды Login/Arm/Off/Status; пути и безопасность читать по фактическим local инструментам. GUI сам по себе не доказывает server-side REAL.

## Долгосрочная цель, не новый scope040

Универсальная система goal→path→action→result→replan, сложная геометрия, capability-based классы, осмысленные роли, живые persistent игроки и фоновая симуляция. Дальше: PvP/PK/ганги, профессии, экипировка, пати/инстансы/кланы/эпики/чат и Semantic Pack. Не создавать сейчас второй C#-мозг или LLM runtime внутри M1. Не возвращать проект к декоративным ботам или нескольким специальным фарм-точкам.

## Где проверять первоисточник локально

В OWN module: `docs/phantoms/tasks/PHANTOM-M1-OVERNIGHT-CAMPAIGN-039/`.
Минимум: RESULT_HANDOFF039.md, CHECKPOINT039.md (запись12:51Z), ACCEPTANCE.md, GOAL.md, DESIGN.md, SOURCE_MAP.tsv, EVIDENCE_INDEX039.md. Затем только нужные test/production исходники и raw по индексу, не все старые документы подряд.

Публичный ref всех названных файлов:
`https://github.com/kpCat/L2J/tree/96bf5d2cba14bf6ad561f334ec49f9010716681b/L2J_Mobius_CT_2.6_HighFive`
