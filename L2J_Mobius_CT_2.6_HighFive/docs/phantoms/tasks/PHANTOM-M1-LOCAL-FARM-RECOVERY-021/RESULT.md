# TASK021 — BLOCKED_NATIVE_CONTINUATION

TASK_RESULT=BLOCKED. M1=OPEN.
Required base: 2bf2936083bf6b081104c3e03c254ab8f07afc87.
Code/build SHA: 337bfbc42afdeaf5944bec690d9ff1840d3677cc.
Branch: experiment/m1-candidate007-observe008. Final remote SHA приведён в финальном
сообщении после normal push; отдельного receipt commit ради собственного SHA нет.

Исправлены pre-work current-goal reload, stale navigation fence, typed terminal result
с исходным reason/object/epoch/revision, собственный bounded local replacement через
existing atomic replacePlan и exact permit rebind. Default historical planner сохранён.
Water/ownership guards не отключались; native managers/Player/GeoEngine/schema не менялись.
Пять SOURCE_MAP production files, два точных standalone test paths, task artifacts.
Основной checkout и foreign diff не изменялись. Субагентов не было.

Targeted semantic RED на untouched compiled required base: runtime28/current29,
terminal retry/local replacement. GREEN: intent10/10 и local7/7, включая delayed reply
после revision change и actual composed terminal→CAS→native travel→AutoPlay/AutoUse.
Регрессии: admission18/18, native handoff14/14, ecology30/30, handoff6/6,
lifecycle4/4, decision36/36, persistence23/23, recorder3/3.
Existing GK6/7 и native travel0/1 не являются GREEN: те же failures воспроизведены
на required base. Assertions не ослаблялись. NATIVE_WORK_SELF_DRAIN22/23 остаётся OPEN.
R13 отсутствие местного альтернативного target проверено на20 ticks; полный перебор
трёх разных плохих локальных маршрутов не доказан отдельным runtime episode.

Clean committed build: ant -q jar (existing init удаляет build/bin), PASS.
GameServer SHA256: E66D693C25EA1425F688AD73D9C0A3C2C15EAC9885F24E55C86B02E02BEB614A.
LoginServer SHA256: 693E3A654A8268991E287029F4BF811AA9F7368D7760890BE2250651B7EF94B1.
Private JAR hashes совпали перед запуском. Fresh database: l2jmobiush5_localplay_observe021.
PLAY только SELECT/export, PLAY_WRITES=0; исходные304 runtime hashes сохранены.
Guarded TEST: существующий dedicated test guard/fixture cleanup; population journal
не применялся и retained restore journal в isolated worktree отсутствует.

Один run после ручного «в игре», без arm/PREPARE/movement TestAdmin и без runtime fixes.
Baseline: 2026-10-06T16:17:57.961876500Z.
Final sample: 2026-10-06T16:20:44.357529800Z.
Same-epoch166.395653s,19 samples; profile110/object268485779/epoch31633062495600.
Persisted/runtime30/30, COMMON_GUARDS_CLEAR во всех сохраняемых исполнениях;
npc20534, population.farming.elf.20534. Во время samples revision не менялась.
Delta damage/kill/reward/cycle/next-target/EXP/SP/loot: все0.
NativeTargetSequence1 уже в baseline; оно не является следующим farm cycle.
NO_LOOT_OBSERVED; applicable drop/pickup не подтверждены и не создавались искусственно.
Overflow стал true; adapter reason autoplay_running расходился с native autoPlay=false.
Run остановлен штатным Pilot stop после повторяющегося divergence/overflow; нового run нет.
Человек видел одно убийство келтира магией стаей, затем idle. Это не доказывает5 cycles
выбранного profile110. Cohort110/175/752/775 сохранена честно с нулевыми farm counters.

Runtime error: Q00255_Tutorial.onKill, qs=null, OnAttackableKill listener.
Причинная связь этой ошибки со stall не доказана. Полная причина native continuation
не установлена; за пределы SOURCE_MAP для её исправления не выходили.
Root producer revision29 NOT_PROVEN. Fresh export28/COMPLETE, post-stop30/PENDING;
между ними был новый historical request. Это не immutable before/after local-CAS pair.
Pre-CAS29 live components не сняты; immutable own replacement доказан focused R06/R11.
Retained020 runtime28 полный NPC/anchor spec отсутствует: OLD_RUNTIME_SPEC_UNAVAILABLE.
R12 native scene witness:44126,42751,-3488→48712,55466,-3464; dry endpoint,
промежуточная water cell отвергнута. В connected run новый water terminal не зафиксирован.
Last connected terminal binding отсутствует; не подставляется fixture result.

Некоторые reportedElapsedSeconds в raw PowerShell samples искажены automatic DateTime
coercion/culture. EVIDENCE021 пересчитывает длительность из preserved ISO UTC, сохраняет
исходные reported values; scheduling и предел300s не менялись, observation не повторялась.

После явного «вышел»: TestAdmin online0 и totalOnline0. До/после stop одинаковые
level12, exp138026, sp13880, XYZ44131,42673,-3488. Clone progress не переносился в PLAY.
Pilot run отсутствует. Stock graceful Game11400/Login27516 завершились, processes0,
ports0, force=false. Но stock log: final Phantom subsystem drain incomplete,
retainedMaterializationEntries8; profile110 background.state остаётся MATERIALIZED.
Это НЕ full Phantom cleanup PASS; сохранён clone/recovery evidence, долг M1 не скрыт.

Git использовался по прямому TASK.md разрешению. Exact commands/paths: GIT_USAGE.md
и ARTIFACT_ALLOWLIST.json; implementation commit337bfbc42af, затем report commit и
normal push origin HEAD:refs/heads/experiment/m1-candidate007-observe008.
Scope guard и git diff --check обязательны перед обоими commits.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Подтверждён current local intent и безопасный recovery contract, но реальный native farm
continuation после первого боя не достиг критериев. TASK021=BLOCKED, M1=OPEN.
После final report остановка по прямому указанию пользователя; нового semantic slice нет.
