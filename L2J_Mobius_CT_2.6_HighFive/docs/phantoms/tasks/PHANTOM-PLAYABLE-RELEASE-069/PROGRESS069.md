# Прогресс PHANTOM-PLAYABLE-RELEASE-069

Начало: 2026-10-22 22:02:24 UTC (23.10.2026 01:02:24 Europe/Chisinau).
Финализация с 2026-10-23 08:32:24 UTC; предел 10:02:24 UTC.
Baseline local/live: 1741547db89f715ac7ace7cb279bfee335e950ee, feature/phantom-world.

## Финальная приёмка069

NOT_READY / best verified candidate. Hard P0B и M1/RELEASE PARTIAL, без самоприсвоения READY_OWNER_PLAY. Ниже сохранена хронология промежуточных шагов; финальная матрица — [RESULT069](../../reports/PHANTOM-PLAYABLE-RELEASE-069.md).

- Final product source76afbbeffec23ec0ec15c98bb16f9a7ab800368c, GameServer1FB2C5B410C75EDC6F0889B831009459BA72BC6EAE1CB5ACAC5B60EBF5ED160D. Clean archive/tree/test/JAR hashes подтверждены, Ant jar+compile-tests exit0. По class parity предыдущие receipts не требуют повторения suites.
- Actual ordinary TEST2boots: full203Geo39/37s, canonical native TestHuman/normal access0 Player, no fake GameClient. Profile14 first epoch3kills/3rewards/+87EXP/+6SP/loot2/NONE. Second epoch сохранил real identity/EXP232/SP18/HP12, native reward0; длительный natural regen превысил120с evidence horizon, sticky PHASE_DEADLINE/после human exit DEFERRED. Guard/reset/refill не ослаблялись. Profile19 native warehouse maintenance/learning route без native kill. M1 PARTIAL.
- M2 affected buff1/1+support3/3 на finalJAR PASS; ранее full composition1/1. M6 Kama/Lab/Rift bounded PASS; M7 career6/6+earned paid skill1/1+ordinary Buyer069 exact5 DB images/native quest reload после обоих Main PASS. M3/M5/earned supply soft PARTIAL сохранены.
- RELEASE2boots: stock offline restore200, native owners/nonempty/leases200, openings0/combat owners0; exact identities/profiles/merchant quota/offline owners/items hashes unchanged. Все4 Main STOP штатные, no-force. Root guides/READINESS069 явно NOT_READY, source stock heap4g/2g сохранён; heap1536MB failed full Geo до population.
- Final safety PASS: protected59,068V7,original0698,physicalGeo203×4, own JVM0/listeners0/DB0, обе роли STOPPED. Scope exact71 paths; source71+guides2 mojibake27 и escaped6 scans0/0. Для Windows PowerShell5 false-positive scanner переведён на локальный ordinal pattern, повреждённый private helper guide prefix восстановлен на verified clean preimage, только own069 docs. Финальный commit/remote SHA фиксируются отдельно в PUBLICATION069.json без self-reference. Старые среды/БД/клиент/OS settings не менялись.

## Полномочия и read-first

Источник: TASK/AUTHORITY/ACCEPTANCE/READ_SET/RELEASE_PROFILE/TEST_POLICY/OWNER_DECISIONS/GOAL пакета069. Автономная единая кампания, commit и normal push разрешены. Новые worktrees запрещены; текущий checkout используется только для source, сборка и runtime изолированы. Исходный dirt: только пакеты068V и069; 068V не включать в scope.

Прочитаны корневые AGENTS и README, PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, RELEASE_CONTRACT, revised roadmap, bounded разделы reports057–061/065–068, build.xml и tools068. Дополнительные AGENTS выше корня и внутри High Five, отдельные code-map/pattern файлы не найдены.

Локальные аналоги: native Creature/QueenAnt combat; NpcTemplate/LevelGapProtectionPolicy; HomeBoard и personal QoL; Build/Assemble/Runtime068, существующий консольный test runner. Стек Java25/Ant, native owners, инвентарные транзакции и текущие границы доступа сохраняются. Studio, клиент, старые DB/runtime не изменяются.

Bounded exception >10 файлов: P0, связанный Alt+B и самостоятельная поставка составляют одну прямо разрешённую кампанию069. Нет отдельного нового движка/планировщика. Проверки только targeted; старые длинные suites не повторять.

## План и состояние

1. READ_FIRST: baseline и пакет прочитаны; protected manifest и доступ к own DB ещё не проверены.
2. P0: убрать generation curse/level-only epic gates, сохранить ordinary loot penalty, Mana PvP. PENDING.
3. Alt+B: native mana1000A, no gear bypass, Wind Walk, stone exchange, OFF statuses. PENDING.
4. Поставка069: exact committed source/JAR, fresh own schemas, 203 physical GeoData, guarded START/STATUS/STOP. PENDING.
5. Short native: P0/UI, M1–M7 impacted composition, persistence, ordinary Main/restart/STOP. PENDING.
6. Финальный scope/encoding/safety guard, честная матрица, report, commit и normal push. PENDING.

## Решения

- Существующий пакет и прямой запрос автономного выполнения заменяют промежуточные подтверждения skill workflow. Дизайн определяется задачей; новые согласования не вводятся.
- Для source используется текущий feature checkout, для build — отдельный committed archive. No new worktrees.
- Проверка GitHub из sandbox была недоступна; разрешённая elevated read-only ls-remote завершилась exit0 и подтвердила baseline.

## Выполнено до первого code commit

- P0 contracts: RED0/3 → GREEN3/3; UI contracts RED3/6 → GREEN6/6. Native mutable Config switch удалён, совместимый RAID_DISABLE_CURSE оставлен compile-time final true для существующих owners/tests. Генерация curse в Creature/QueenAnt и устаревшие give/setUseRaidCurse пути удалены. Иные event skills с похожим именем/общие effect definitions сохранены.
- Эпические level-only guards сняты в Frintezza, Zaken family, Freya entrance/battle, Crystal Caverns; Cruma shortcut30483 имел реальный MAX_LEVEL55 и теперь доступен любому уровню. Core остаётся открытой зоной, instance не создавался. Остальные admission guards сохранены; полный native epic audit ещё впереди.
- UI: native multisell91003/91004, Wind Walk2, прямой запрет600010/600014/600016 в MultisellData, multiplier1/1 для утверждённых новых списков, premium OFF guard/status. Дополнен настоящий HomeBoard quick purchase/exchange через существующий Player.exchangeItemsById, без нового GameClient/packet/auth fixture.
- Dev compile+compile-tests дважды exit0 (42s,33s), только два прежних runFinalization warnings. Общий verify не запускался.
- Runtime069 guard RED (unbound accepted) → GREEN (unbound/mixed/schema reject), DB_WRITES0/PROCESS_ACTIONS0.
- Preflight own schemas absent, MariaDB11.4.13. Однократный bootstrap TEST/RELEASE по121stock SQL/124tables, aggregate2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28. Нет reset/reseed. Prepare-Inputs069: package8/8, Geo203 copied/hashed, ports free; CIM потребовал elevated invocation, без OS changes.
- Native commerce RED missing handler → GREEN exit0:1000A→1Mana, blue3/green5/red10, insufficient/repeat/zero/negative/overflow/malformed refusals. Fixture funding1000A+3eachSeal явно initial input, не earned. Native log commerce-green.log, receipt commerce-1792707544715.tsv.
- Native P0 exit0: PvP/karma/duel ItemSkills, MP100/consume1/reuse500; controlled0% level filter leaves actual RaidBoss+GrandBoss grouped/ungrouped/Adena unchanged and suppresses ordinary Monster. p0-native.log, p0-native-1792707665682.tsv. Это не доказательство всех боевых curse roles — они ещё проверяются отдельно.
- Protected59 JAR/config hashes в private task069/PROTECTED_PRE069.tsv; исходный sandbox hash-attempt имел ACL errors и не является полным safety proof. Новая elevated read-only проверка exit0 дала59 exact hashes.

## Короткая native-приёмка и review

- Первый чистый committed build: aa6d766bf0e384e7e1b8b791f79382606558fa43; build-final-core-v1, GameServer SHA256 AAC3DE3B14A1E00D382054F5668B66F2D0E4E1D14353D024D9C94CD5F797F94E. Ant jar+compile-tests exit0; archive/source/test hashes в BUILD_INFO069.json. Это промежуточная сборка: поздний epic audit требует нового committed build.
- Дополнительный epic audit обнаружил level guards Tiat и Ekimus attack/defence. Contract RED1/7 → GREEN7/7; guards и неиспользуемая MinTiatLevel config удалены. EPIC_ENTRY069.tsv перечисляет actual IDs/paths/оставшиеся admission rules. Native Frintezza level1 Party9+CC PASS с отрицательными noParty/noCC/minPopulation/nonleader/reuse; actual Cruma30483 ENTER level80 PASS. Epic victory/полные цепочки не заявляются. epic-native-v1.log.
- M2: companion-full-composition058 PASS1/1 на v1; native addressed speech→party→follow/assist→party chat, actual damage1448/4786/2116, mage MP3684→3485, arrows200→191, два stock18008 kills. Входные class80/S gear объявлены fixture. m2-composition-v1.log.
- M4: pvp-current-native058 PASS3/3, legal response+refusal+party roster control. m4-pvp-v1.log. M5: raid-tactics-native058 PASS1/1, bounded25199 damage/tank aggro/spacing/support, победа не заявляется. m5-raid-v1.log. Длинный raidFlow059 не запускался.
- M7: первый вызов с одним autoProfessions061 отклонён beforeAll, без product bug; source подтвердил требование careerProducer061. Один исправленный вызов quest-instance-goal036 с обоими switches PASS6/6:20/40/76 state/save, Noble80 threshold/idempotency, lineage/sex/subclass. m7-career-producer-v1.log.
- M6: Kamaloka/Labyrinth native solo57/73, OFF exit, Party2 sameWorld, wrongLevel/foreign/reuse controls PASS; m6-kamaloka-fixed-v1.log. Первый fixture INI был invalid из-за отсутствия обязательных CrossClass/Crystallization switches; второй negative case ошибочно использовал сохранённый level23. Исправлены только fixture preconditions, те же identities без reset/reseed. Rift entry/fees/room/timer/terminal/Party2 PASS; m6-rift-v1.log. Network logout и encounter completion не подменялись.
- P0B Queen Ant native: первый magic deadline8s меньше расчётного12s; второй18s не дал подтверждения magic. Причина второго INCONCLUSIVE, повторный бой без причины запрещён. curse-native-v1.log, curse-inspect-v1.log, curse-resume-v1.log сохранены. P0B PARTIAL до полной native четырёхролейной матрицы; source removal сам по себе не заменяет её.
- Независимый read-only review: Critical0, подтверждённых Important product defects0. Два Important test-evidence замечания: initialMP/finished flag не доказывают heal; damage event не доказывает applied HP. Test исправлен на HP evidence и failure snapshot; старые результаты не переименовываются в PASS. Сетевой client QA и ещё не выполненный Main/durability reviewer не оценивал.
- M1: canonical cohort8 создан один раз, zone=-12:00. Подготовка stopped TEST input остановилась до baseline/cursor: старый near-service filter12к не находил field (ближайший17961). Own069 fixture допускает source-backed field30к в пределах native route100к; resume сохраняет те же births/gear/class/HP, не создаёт новые READY и не правит goals/cursors. Earned economy не заявляется из подготовленных входов.
- Native same-grade NPC shot quote в source-backed field отсутствует: ECON PARTIAL, wallet0/initialShot0, продавец и расходники не подделываются. Один resumed factory episode ограничен8мин; это не ordinary Main proof. Для дальнейшей stopped-only подготовки выделен prepare-resume, исключающий ошибочный автоматический переход от подготовки к измерению. Сохраняются уже созданные native BG state/cursors; reset отсутствует.
- Review heal sampler дополнительно исправлен fail-closed по фактическому nanoTime interval: несколько regen ticks после scheduler delay нельзя принять за heal. Compile exit0; новых боёв не выполнялось. В durability baseline metadata characterName отделено от character hash до первого запуска проверки.
- По чтению canonical onlineHumanPoints найдена точная ошибка factory fixture: обычный clientless Player без LOCALPLAY_TEST_HUMAN lease не является human demand. Первый8мин factory episode поэтому не доказывает native locality, даже если BG cursor продвинулся. Fixture получает штатную LOCALPLAY_TEST_HUMAN lease и проверку supplier, без fake GameClient. Повторять этот8мин factory episode не нужно; обязательный proof будет отдельным обычным Main с существующим LocalPlaySyntheticHumanSession. Уже полученные BG данные не откатываются.

Не завершено: final committed build/JAR, actual ordinary Main+sameDB restart, normal-player durability, full delivery/safety. READY не объявлен.

## Ordinary Main: выявленный startup crash

- Core-v2 source10a382d0, JAR5C2858055490608072B2B0B1DD6F2666A658BFCC421A8252A4AAC832E5A525A3, clean archive/tree/test receipt PASS. По4547 classes v1→v2 изменился только GraciaSeedsConfig, поэтому прежние native receipts имеют byte parity, без повторения suites.
- TEST/RELEASE собраны203 physical GeoData каждая, Validate PASS. TEST остановленная конфигурация8/zone-12:00/starter0 и разрешение finite native TestHuman отдельно от RELEASE16/starter5000/OFF. Побайтные preimages TEST config сохранены вне checkout.
- Ordinary Buyer069 baseline создан один раз: реальные Mana1/AA54, явно подготовленные NG68/24/31 в непустых слотах, level5/skill3:1/quest102/variable. First partial setup не replayed; stock QuestMasterHandler и Quest.playerEnter подтвердили native reload, exact5 DB images PASS до Main. Initial constructor-only quest caller и отсутствие Quest.playerEnter были test-caller ошибками; DB hashes совпадали. Supplemental fixture source16d25866, product JAR10a382d0; отдельный source/class binding хранится в reports.
- Дополнительный M7 earned-learning-native058 seed58005833 PASS1/1 на v2:4 stock kills→2420EXP/100SP, skill3 ranks1/2 по50SP, retry idempotent, actual HP/MP effect, native save/reload. Первый неправильный seed58006907 отклонён beforeAll до DB; исправленный вызов один native case.
- Первый Main TEST069: Login PID9756 стартовал; Game PID14876 остановился в constructor при GeoData до mailbox/hook. Один JDK thread dump: main отсутствует/DestroyJavaVM, GC.heap_info used=capacity=max1536MB. Физические203 файла1014.2MiB, source stock java.cfg Xmx4g/Xms2g. Установленный в069 маленький heap был неподтверждённым предположением и удаляется; low-memory readiness остаётся неподтверждённой.
- Исходный crashed Game завершён обычным CTRL+C через отдельную консоль: exact PID/start ticks/owner и GetConsoleProcessList только JVM+helper; exited=true, forceTermination=false. Ни Kill/TerminateProcess, ни agent injection. Login закрыт stock Runtime069 STOP; own TEST DB connections0. Это startup-abort recovery, не финальный graceful Main STOP proof. Логи/thread dump/heap/interrupt receipt сохранены.
- Минимальный product fix: GameServer.main после validated own069 launch перехватывает Exception/Error и выполняет обычный System.exit(1), вызывающий stock hook при его наличии, чтобы failed constructor не оставлял non-daemon JVM без mailbox. Остальные launch modes сохраняют прежнее rethrow. Assemble сохраняет исходный java.cfg; потребуется новый committed build и guarded продвижение только stopped069 поставок с preimage backup.
- Повторный review нашёл Important в первой версии fix3a6d7540: System.exit после уже зарегистрированного hook обходил бы STOP_BLOCKED veto при незавершённом manufacture. До promotion исправлено: volatile phase выставляется сразу после регистрации stock hook; early pre-hook failure закрывает только startup JVM, late failure вызывает существующий Shutdown.startShutdown с его drain/veto. Core-v3 остаётся промежуточным и в runtime не применяется; final build должен включать phase fence. Никаких late native writers ради теста не прерывали.
