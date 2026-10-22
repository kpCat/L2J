# PHANTOM-PERSONAL-ADVENTURE-067 — план и журнал

## Authority и бюджет

Spec: TASK.md, AUTHORITY.md, ACCEPTANCE.md, READ_SET.md, RUNTIME_INPUTS.md, TEST_CASES.md этого пакета прочитаны. Выполнение автономное; дополнительного manual gate нет. Начало 22.10.2026 04:16 UTC; после 14:46 UTC новый функционал не начинать, крайний срок 16:16 UTC. Native/server observation суммарно не более 60 минут.

Git root определён фактически. Local и live GitHub HEAD feature/phantom-world: `8631faf3d4ce7f0f2256cb312af99659c008560d`. GitHub connector fetch_commit по ветке подтвердил SHA. Dirt до работы: только исходный task package067, сохраняется. Другие ветки, C:-checkout/stash, releases057–066R2, Studio и Account_Manager не изменять.

## Прочитано и локальные аналоги

Root AGENTS/README, module Agents, workflow/release/current status, связанные части master plan/task package standard/roadmaps, protected ownership/status066/R2. Общий PersonalCharacterQoLService и strict PersonalCharacterQoLConfig; PersonalEffectDurationService/Policy и QoLPersonalSkillsSuite как образцы snapshot/policy/tests. Kamaloka checkPartyConditions/enter/finish; RiftManager/start, DimensionalRift и Party lifecycle; Q00350, LevelUpCrystalData и Attackable.addAbsorber; Player.recalcHennaStats. Build/Assemble/Runtime066R2 только read-only reference. Code-map и отдельные pattern-файлы для этого scope не найдены; повторный широкий поиск не нужен.

JDK25 / Ant1.10.17, существующие libs и console runner. Только собственные staging/build/DB/runtime, native mechanics и explicit switches. Нет fake GameClient, GM grants, отмены headless guards, нового engine/timer или глобального снятия ограничений.

## Первоначальный план реализации (завершён итогом ниже)

- [ ] 1. Общий strict config snapshot067 и персональный допуск: новые дети OFF, deny Phantom/Background/headless. Один компактный admission adapter поверх существующего allowlist.
- [ ] 2. Kamaloka/Labyrinth: один проверенный snapshot участников, solo только eligible actor без Party, native levels/reuse/range/identity/exit/cleanup. Проверить два representative templates и настоящую Party2 в одном world.
- [ ] 3. Cubic10632: сохранить stock timestamp как last-use provenance, читать персональное effective remaining60m в обоих native checks и display; old24h/relog/restart/childOFF сохраняют правильную границу. Независимый исполнитель владеет только reuse участками Player/UseItem/ItemSkillsTemplate и новым policy при необходимости.
- [ ] 4. Fishing: action в существующем1s tick через known skill1313/1314 и native useMagic; правильный deceptive mode, costs/reuse/родная reward и lifecycle. Независимый исполнитель владеет только Fishing.java и focused checks.
- [ ] 5. D1: доказать отсутствие level-gap, если подтверждено NOT_APPLICABLE без пустого switch. D2: адресный Henna mode в recalc, stock cap+5. D3: только типизированные положительные FuncAdd значения, симметричный owner/remove; при небезопасности OFF/NOT_RUN.
- [ ] 6. Own067 ownership/preflight/stock schemas, чистая сборка exact committed SHA, физические203 Geo hashes, standalone START/STATUS/STOP. Runtime guards и build helpers переиспользуют образцы066R2 без старых writes.
- [ ] 7. Targeted native/contract tests, safety negatives и restart; один independent review; два отдельных Cyrillic scans; final scope guard/exact-path commits, normal push только feature/phantom-world, remote SHA comparison. Штатно STOP все свои JVM, final report/matrix/handoff.

## Bounded exception и source owners

AUTHORITY067 разрешает связанную кампанию с >10 файлами. Production allowlist: config/custom/PersonalCharacterQoLConfig.java; qol/PersonalCharacterQoLService.java; при необходимости новые узкие qol/PersonalInstanceAdmissionService.java и PersonalItemReuseService.java; scripts/instances/Kamaloka/Kamaloka.java; Player.java только henna/reuse; UseItem.java; ItemSkillsTemplate.java; model/fishing/Fishing.java; model/options/Augmentation.java при подтверждённом безопасном seam; commons/util/LocalRuntimeShutdown.java только новый own067 namespace. RiftManager/DimensionalRift/Party допускаются только при bounded безопасном singleton lifecycle, иначе без правок PARTIAL_DESIGN. Config только PersonalCharacterQoL.ini. Test/build/tool allowlist уточняется по фактически использованным новым067 файлам и точным launcher/build registration. Docs: новые067 документы/отчёт и краткий current status067. Старые отчёты/task packages неизменны.

## Review focus

1. Allowlist совпал с Phantom/Background либо master/childOFF: deny до side effects.
2. Party изменилась между admission и teleport: один валидированный roster, никаких split instances/чужого допуска.
3. Old24h reuse и childOFF после personal use: точное last-use, не ранняя повторная награда.
4. Fishing cast завершил бой синхронно или изменился rod/instance: null-safe tick, нет следующего cast/reward.
5. Henna/augmentation equip/relog/remove: без накопления и мутации shared templates.

## Текущие факты

04:19 UTC: read-first завершён. Cubic имеет второй reuse check в ItemSkillsTemplate и raw restore packet; один UseItem patch недостаточен. Fishing native SELF skills1313/1314 тратят MP2 и reuse2s; cast может синхронно очистить _fisher. Soul Crystal Q00350/LevelUpCrystalData/Attackable.addAbsorber не содержат player overlevel запрета; финальное доказательство будет в отчёте. Runtime ещё NOT_RUN; client QA DEFERRED_BY_OWNER.

04:47 UTC: реализованы strict adventure snapshot и отдельные дети, identity lease deny Phantom/Background, singleton admission roster, Kamaloka/Labyrinth entry/solo exit, cubic canonical24h/effective60m, native Fishing tick, Henna mode/cap5, typed augmentation-only positive FuncAdd. Runtime tools067 созданы на основе066R2 без правок прежних tools. LocalRuntimeShutdown расширен только exact067 namespace. `.gitignore` дополнен только `/.phantom-local/task067/` для защиты private inputs; own Prepare-Inputs перенесён в staging, в source не включается.

Native RED сохранены: cubic stale24h, fishing no action, Henna CON-3, solo entry refusal, augmentation1150.9 expected/1135.45 actual, solo exit32496-no, runtime flag silently ignored. Genuine GREEN пока cubic/Henna/augmentation; исправлены общие TEST input defects: dead persisted actor → native revive; unspawned actor → spawnMe; отсутствующий MasterHandler/TargetHandler → штатная регистрация как в QoLPersonalSkillsSuite. Guard weakening0. Один compile failure getInstance у InstanceWorld исправлен на подтверждённый InstanceManager.getInstance(worldId). Повторные FAIL не скрываются.

Own TEST schema создана один раз121 stock scripts/124tables; release schema пока не создана. Новые порты2148/7848/9148 и2248/7948/9248 свободны на preflight. Private root ownership создан до schema/file actions.203 protected066R2 GeoData physically copied into native-test,203+203 SHA проверены;8 protected config/JAR inputs зафиксированы hashes. Source staging/runtime ещё не финальная поставка. `CLIENT_QA=DEFERRED_BY_OWNER`, no fake GameClient/GM. Rift решение PARTIAL_DESIGN: singleton admission не закрывает native death restart и non-idempotent concurrent room/timer cleanup; см. RIFT_ANALYSIS067.md (готовит независимый исследователь). Исходные M1–M7/066 не пересматриваются.

В работе: integrated core checks (policy matrix, solo/Party2/reuse/exit, cubic reward/persistence, Henna modes/relog, augmentation mixed funcs); final fishing representative START/action/lifecycle checks; clean committed build, own release schema/candidate, ordinary Main restart/STOP, independent review, scans и normal push. Ant выполнять последовательно; own Java runs имеют property phantom.task067.native=true и закрывают ThreadPool/Hikari в finally. Общий observation до сих пор менее10мин (точные durations в логах); лимит60мин сохраняется.

## Финальный срез 05:19 UTC

Семь шагов исходного плана завершены. **ADVENTURE067_CORE=PASS; FULL067=PARTIAL** по ACCEPTANCE067.tsv. G0/G1/G2/G4/G9 PASS; G5 PASS_NATIVE_BOUNDED, G7/G8 PASS; G3 PARTIAL_DESIGN и G6 NOT_APPLICABLE. Rift audit закончен без production правок. Новые этапы/R2 не начинаются. Подробный итог — reports/PHANTOM-PERSONAL-ADVENTURE-067.md.

Independent review выявил nonzero access profiles с isGM=false: исправлено на exact access0,10 profiles+null RED→GREEN, права Player не менялись. Clean committed source `2939b7054caeafbbfdc913ad5ebe4785c618c8a1` собран JDK25/Ant1.10.17 в source-final/build-final-source-final. Final GameServer SHA256 `06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58`; whole source/archive/JAR/test binding в BUILD_INFO067.json.

Final access/runtime-guard/core/fishing/content: exit0 каждый, core17.21s/fishing40.35s/content3/3; SEVERE0. Fishing natural final WIN7729+1 (первый development WIN7728 не является финальным evidence). Native component inputs раскрыты отдельно; fake GameClient/GM нет, client packet/reconnect/full network logout NOT_RUN. Финальные тесты не повторять без изменения или найденной проблемы.

Release schema создана один раз121 stock SQL/124tables; TEST также не reimport/reset. Самостоятельная Personal-Adventure-067-candidate собрана с203 физическими Geo copies и START/STATUS/STOP. Два ordinary Main: Game22324/Login2988 и Game24564/Login14252, одна release schema и один JAR,539 quests/306 scripts, обе регистрации и штатные shutdown. FINAL_DELIVERY067.json: protected8/Geo203+203+203/original docs6 PASS; ownJVM0/listeners0; MainSEVERE0. Финальная конфигурация равна source, все новые children OFF/master False/allowlists empty.

Source normal push exit0 только feature/phantom-world; GitHub live SHA2939b705 подтверждён. Заключительные5 docs публикуются отдельным normal push; final HEAD отражается в private PUBLICATION067.json и ответе пользователю. Exact allowlist41, два отдельных Cyrillic scans, no unknown dirt. Начало04:16UTC, проверенная работа завершена намного раньше лимита12ч; время native log lifetimes329.03s плюс два Main менее3мин. Защищённые предыдущие runtime/DB, C:-checkout/stash и чужие JVM не менялись.
