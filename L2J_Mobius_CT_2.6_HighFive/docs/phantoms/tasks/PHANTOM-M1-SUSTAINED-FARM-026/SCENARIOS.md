# Native SCENARIOS026

У каждого теста exact fixture/seed/код base+candidate, producer invocation, native
writer witness, assertions, cleanup outcome. RED не ошибка компиляции/DB config.

## F — gameplay
F01. Existing 876-like: несколько native kills, локальное смещение, rejected stand-point;
при другой proven безопасной цели игрок продолжает действие без возврата в центр.
F02. Вода на промежуточном сегменте, cliff/другой этаж: запрет сохраняется даже если
есть nearby NPC. Witness нельзя повторно использовать после смены epoch/revision.
F03. Полный terminal одного пути не вечный отказ farm-миссии: bounded другая local
opportunity или explicit unavailable. Нельзя менять goal revision на каждый tick.
F04. Empty spawn/чужой instance/все недоступны: no fake target, no teleport/no rewards.
F05. Mage MP хватает на1каст, затем ниже cost: normal skill legality, bounded rest,
выход по affordability+hysteresis, следующий kill. HP/MP вручную допускаются только
в guard-owned deterministic TEST setup до measurement, не natural scenes.
F06. Attacked while recovering: no permanent sit, native legal reaction/retreat.
F07. Earned cast pending в момент recovery: no cancel/drop/double effect. Старый
callback не меняет новую goal/epoch; AutoPlay paused/revoked semantics preserved.
F08. Progress затем180s idle: acceptance FAIL. Fresh reason/new plan/counter не PASS.
F09. Actual food/potion отсутствует: ничего не создавать; self-heal не renew farm debt.
F10. NativeZ != geoHeight, но native path legal: сохраняемXYZ и проверяем локальное
действие; вариант wrong floor остаётся запрещённым. Origin/target/geo witness записать.

## E — native-observation и callback
E01. N02 two damaging managed + ordinary third: actual original/delayed kill and EXP/SP,
оба managed получают truthful damage/death/reward records exact generation.
E02. Отдельный direct callSkill вариант: сравнить native after-write hook, не подменять
его искусственным evidence.damage(). Original doCast control также обязателен.
E03. Cancel перед launch / stale captured owner / reused NPC spawn / повторное death
уведомление: no ghost counters и no double rewards; invariant меняется не на счётчик.
E04. EVENT reserved→submitted→running→complete; hold publisher/worker отдельными
barriers. Observe QUEUED vs lost publish vs running listener без таймерного complete.
E05. Actual ON_ATTACKABLE_KILL создаёт delayed quest child. Drain ждёт ровно children;
после cleanup никакой мутации, self-drain и lock starvation не игнорируются.
E06. Executor rejection до публикации возвращает reservation корректно, не путается
с начатым writer failure. Если submit мог состояться, outcome сначала уточнить.
E07. Исключение listener после mutation сохраняется как real incident, никаких
бесконечных auto retries и успешного cleanup без проверки effect.
E08. Temporary checkpoint удерживает необходимые registrations, permanent cleanup
закрывает их. Cold/full store proofs025 не ухудшаются.

## R — durability и жизненный цикл
R01. Existing retained452 копия → production finalization → actual restart2 exact.
R02. Whole cohort SEALED→SQL, затем два actual sameDB restart. Expected берётся из
native witness, не из SQL. Полный inventory+skills hash, поля/epoch/receipt identity.
R03. AFTER_NATIVE crash exact profile after earned work: early resolver witness до
admission. XYZ exact; после release lawful move отдельно, не выдавать lateSQL за loss.
R04. AFTER_FINALIZE optional только после R03PASS, отдельная clone; repeat restart.
R05. Natural/coerced stock NPC death on dedicated lane → existing recovery→farm.
Не heal/res вручную и не создавать заданный skill/шмот/EXP для product PASS.
R06. Synthetic away/demat/background/return/remat → useful native progress; полный
маршрут в TTL, fresh session для отдельного теста, никакого ambiguous replay.

Ненужные fixtures не сочинять: existing025 private lane плюс composed native suites.
Полные R05/R06 не заменять случайной смертью/исчезновением в farm cohort.
