# Прогресс PHANTOM-PLAYABLE-RELEASE-069

Начало: 2026-10-22 22:02:24 UTC (23.10.2026 01:02:24 Europe/Chisinau).
Финализация с 2026-10-23 08:32:24 UTC; предел 10:02:24 UTC.
Baseline local/live: 1741547db89f715ac7ace7cb279bfee335e950ee, feature/phantom-world.

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
