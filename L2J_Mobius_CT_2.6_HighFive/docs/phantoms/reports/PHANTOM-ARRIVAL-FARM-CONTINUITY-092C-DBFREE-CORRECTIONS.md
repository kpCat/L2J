# TASK092C — два боевых исправления, только DB-free

Дата: 26.10.2026. Scope — последнее уточнение владельца после read-only разбора смерти092C.
**DB_FREE_CORRECTIONS_PASS; NATIVE_ACCEPTANCE_REQUIRED. FULL_092C_GREEN не объявлен.**
Это отдельный DB-free этап; historical native результат092C остаётся BLOCKED, приёмка092B не меняется.
Native START0, обращения к TEST092B/092C БД0, full Ant/JAR0, commit/push0.

## Read-first и границы

Прочитаны root AGENTS, workflow contract, начало current status и предыдущий отчёт/evidence092C.
Целевые symbols: VisibleAutoPlay configure/align/resourcePause/acquireTick/stopExpiredCombat,
NativeCombatCapability selection/alignment, CombatPolicy resource hysteresis.
Локальные аналоги: AutoUseSettingsHolder и настоящий cursor AutoUse; existing combat-core resource/ownership/reward tests;
native L2jCombatBackend.tryAcquireActor/retreatFrom и его bounded Geo/pursuer/owned movement checks.
Native farm022 и sustained farm026 прочитаны по relevant ranges; их DB/native bootstrap не запускался.
JDK25, текущие зависимости и architecture сохранены. AutoPlayTaskManager/AutoUseTaskManager и обычные Player не менялись.
Новые engine, TEST DB/actor, fixture package, worker/timer/cache, schema/config не добавлены.
Входящий WIP092C и чужие untracked сохранены. До правок создан byte-exact backup7 файлов вне checkout;
перед правкой ближайшего planning-wrapper туда же добавлен его проверенный preimage, итого8.

## Исправление1 — фактический primary определяет damage mode

PhantomVisibleAutoPlay больше не добавляет Action2 безусловно в configure.
alignOffensiveSkill передаёт actual native primary.isMagic() в тот же NativeCombatCapability.alignAutoSkills.
MAGIC primary убирает Action2: существующий AutoPlay.isMageCaster не запускает собственный melee root.
Physical primary и отсутствие damage skill оставляют Action2 и обычную физическую автоатаку.
Нет classId/skillId exceptions, выдачи навыков или подмены MP/cooldown.
При неизменном списке AutoUse сохраняется его skill cursor: повторные AutoPlay alignment ticks не возвращают его к первой utility.
PhantomEngagementPlanner.offensiveSkill для planning/range/viability использует тот же preparationSkill:
временный reuse не превращает удерживаемый MAGIC primary в null/MELEE прогноз. Native AutoUse по-прежнему проверяет readiness и MP.
Это DB-free контракт согласования pools; фактическое исполнение/interrupt/resist spell требует отдельной native-приёмки.

## Исправление2 — busy-safe PvE и существующий retreat owner

Resource verdict получает актуальные ресурсы, threat и selected-target viability до решения о native busy.
Опасность при attack/cast/queued action даёт WAIT_NATIVE; новые AutoPlay/AutoUse roots получают PAUSED.
Запрет не зависит от Intention.ATTACK. Уже принятые cast/queued actions не прерываются новым safety verdict.
После settlement под угрозой используется L2jCombatBackend.retreatFrom через временный existing actor lease;
он сохраняет настоящий World/pursuer census, Geo, безопасный walk segment и MOVE_TO, без teleport.
Native escape destination принадлежит текущему session/epoch; количество hops и исходное recovery time ограничены.
Если угрозы нет, остаётся native rest/recovery; неподходящая неагрящаяся цель возвращается existing reconsideration.
Непроходимая геометрия/невозможный отрыв не выдаются за успешное спасение.
Сохранён прежний recovery MP reserve: min(maximumMp,cost*2), а не один cast.
При PAUSED без угрозы и native busy может продолжаться только existing pending earned loot/Sweep;
это не разрешает новый offensive root. После публикации Sweep в том же tick не выполняется sit/retreat.
Stop/replan очищает только exact owned retreat destination при совпадении Player/текущего epoch, как existing engagement cleanup.
Проверки DB-free не доказывают реальный уход от моба, damage/cast, earned reward или sameDB restart.

## RED→GREEN и проверенные границы

Использован существующий PhantomCombatCoreSuite: два исходных focused cases и два адресных review-regressions; нового runner нет.
Для DB-free RED выделены scalar policy/alignment boundaries с прежней семантикой;
RED — поведенческий assertion, не ошибка компиляции и не сравнение source text.
- red-compile exit0; red runner exit1:55 passed/2 failed, ровно новые mode и busy-safety cases.
- mode-green-compile exit0; runner exit1:56 passed/1 failed; mode/cursor PASS, safety ещё RED.
- green-compile exit0; runner exit0:57/57, оба новых cases PASS.
- review-red-compile exit0; runner exit1:57 passed/2 failed, earned continuation и recovery reserve RED.
- final-green-compile exit0; runner exit0:59/59, все четыре focused cases PASS.
- affected-offscreen-combat exit0: существующие092B combat groups3, readiness/fallback/MP/drain/bounds, native NOT_RUN.
Среди прежних55 — owned lease drain, cancellation, reward, native recovery hysteresis и bounded retreat contracts.
Это тесты production alignment/policy и existing combat service с его DB-free ports; полного live Player/Geo воспроизведения нет.
Независимый read-only review дал0 Critical/4 Important: primary reuse, earned continuation, MP reserve, retreat teardown.
Все4 адресованы одним локальным fix pass внутри тех же двух causal fixes; второй reviewer не запускался.
После fix pass — self-review точных изменений и свежие59/59. Live Player/Geo teardown и cast starvation остаются native acceptance gate.
Declined to judge у reviewer: native runtime, DB/store/restart, Geo/физическое движение и Pet/Servitor.
Решение: оставить их NOT_RUN/отдельным pet-этапом по явному owner scope; риск — DB-free GREEN не доказывает их runtime-исход.
Тесты не объявляются полной simulation/native-приёмкой; setup не подгонялся под PASS.

## Exact scope и доставка

Изменено6 repository files этого этапа:
1. `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`.
2. `java/org/l2jmobius/gameserver/phantoms/combat/PhantomNativeCombatCapability.java`.
3. `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatPolicy.java`.
4. `java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementPlanner.java` — необходимый ближайший wrapper того же primary.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java`.
6. Данный отчёт. Historical092C report и WIP entrypoint/suite не переписаны.
Git — только root-AGENTS bounded scope inspection: `git status --short --branch`,
`git diff -- <exact allowlist>`, `git diff --check -- <exact allowlist>`; полные argv/exits в private final-verification.json.
Первый default whitespace check exit2 пометил CRLF как trailing whitespace; формат файлов не переписывался.
Финальный check с разовым `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <allowlist>` — exit0.
Дополнительно у всех добавленных/изменённых строк проверено отсутствие пробелов/табуляций перед концом строки; Git config не менялся.
Индекс, HEAD, branch, commit/push не менялись.
- mojibake-маркеры в изменённых файлах проверены отдельно; совпадений0.
- escaped Cyrillic в изменённых файлах проверены отдельно; совпадений0.

## Обязательная pet-policy владельца — отдельный bounded этап

Обычные Pet и боевые Servitor — разные категории.
Wolf/Great Wolf/Fenrir могут сражаться. Baby Buffalo и улучшенные — heal/support физических классов.
Baby Kookaburra и улучшенные — MP/heal/magic buffs. Baby Cougar и улучшенные — heal/mixed buffs.
Прочих Pet нельзя назначать боевыми DD; у combat Servitor сохраняются полноценные боевые функции.
Support Pet нельзя автоматически отправлять ATTACK; помощь только реальными native skills с MP/conditions/cooldown.
Потенциальный shared Summon ATTACK в AutoPlayTaskManager остаётся известным follow-up;
pet-policy сейчас не внедрялась, поведение обычных игроков не менялось.

## Evidence и STOP

Private proof: `C:/Users/ZBook/.codex/visualizations/2026/10/26/01a1762e-8fd8-7522-bd57-f9baa88879c3/dbfree092c/`.
Точные javac/java argv и exit codes сохранены в *.process.json, stdout/stderr — там же.
Исходный092C proof/DB facts/receipts/arrival и backup не переписывались.
Новая native-приёмка и публикация требуют отдельного разрешения владельца.
**STOP после двух адресных production fixes и DB-free проверок.**
