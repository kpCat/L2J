# PHANTOM-LEARNING-003 — тактическая V3 и native HEAL

Статус: FULL003 — реализация, native gates и дополнения о времени PASS. Финальная
публикация разрешена только в `wip/phantom-learning-003`; точный опубликованный HEAD
фиксируется в итоговом delivery receipt после push, вне самоссылочного Git commit.

В игре: модель №2 реально обучена на120 native Heal events, холодно восстановлена из SQL
и изменила2/4 допустимых выбора цели с настоящим восстановлением HP. DAMAGE V1/002 сохранён.
Дополнительно доказаны выбор готового навыка при живом КД другого и fast/slow Heal при разной угрозе.

## Реализация

Независимый clone от `b7b5575457c12c1b0438bcbaa646675a67778391`, новая ветка
`wip/phantom-learning-003`. Пользовательский checkout не использовался для записи.
Пакет003 сохранён в `docs/phantoms/tasks/PHANTOM-LEARNING-003` с исходной структурой.

V3 — отдельный immutable CPU checkpoint:128 state features,64 action features,
dense128→512→2048→32 encoder и дешёвая action head;1 185 377 обучаемых float32,
4 741 508 байт весов (4,522 МиБ),72 байта header. Padding отсутствует. Backprop проходит
через все слои; фиксированный seed183, Adam, tuning early selection, audit не передаётся trainer.
Состояние кодируется один раз, затем scoreEncoded ранжирует предлагаемые действия.

Rich contract хранит SELF/ALLY/SUMMON/ENEMY, class/level/HP/MP/CP/stats, оружие/броню/
бижутерию/augmentation, effects/skills, Party/epochs, расстояние/высоту/направление/Geo/LOS.
Есть paired known masks и deterministic bounds. Неизвестные приватные сведения противника
не подменяются нулями. `RECENT_SKILL` означает последний начатый native Skill;
неподтверждённая историческая цель/время не записываются. Будущие action families типизированы,
но не получают trainability или новый executor без отдельного receipt contract.

V3 меняет только порядок двух или более законных неэкстренных HEAL одного исполнителя.
PartyTactics перечисляет предложения; Combat external lease и штатный Skill исполняют их.
Повторно проверяются actor/target/epoch/party/skill/MP/range/LOS/lease. При HP цели≤25%,
HP источника≤30%, давлении/высоком stock priority сохраняется stock rescue. Общий CPU slot,
cooldown2s, maxEntities20/maxActions16 и budget5ms возвращают stock при перегрузке.
SQL и обучение отсутствуют в hot path. OFF по умолчанию; ON требует exact version/SHA.

HEAL evidence связывает native MagicUseTask с конкретным Skill и instant HEAL effect,
положительным useful HP writer и фактическим MP debit. Вложенный чужой эффект,
regen, stale/cancelled owner, смена Party/epoch, повтор или неполный cast не дают credit.
Сохраняются offered/selected/baseline, observation, source lease identity и event key.
Reward = usefulHP/maxHP − actualMP/1000 − elapsedNanos/1e11; без награды за выдачу команды.
`Heal.java`, формулы лечения, V1 codec и LearningCastEvidence не изменены.

Отдельные V3 SQL tables хранят immutable native rows, frozen dataset, DRAFT и ACCEPTED_TEST.
TRAIN/TUNING/AUDIT разделены по actor и фактическому контексту; audit используется один раз.
Кандидат должен улучшить TRAIN-constant и совместимый prior по audit минимум на1%.
Fit никогда не включает ON: separate `apply-test <id> <sha>` проверяет точный DRAFT на stopped
native boundary; pin ON остаётся отдельным изменением конфигурации своей TEST.

## Переиспользование и изоляция

Использованы Trainer/Director/G0/SQL, native provisioning/Party goals/Combat lease, ownership
и Windows launchers002. Новый supervisor, TEST engine или provider не создавался.
Имя собственной physical TEST папки осталось `.phantom-local/learning002/train` внутри
нового clone: это новая SQL/datadir, не прежняя002. Порты33337/17981/19281 собственные.
Старые Geo203/protected207 только читались; прежние datadir/MAIN/PLAY/release057–061 не запускались.
`assetSourceRoot` — optional read-only источник; абсолютный временный путь удаляется перед commit.
Проверка ручных TRAIN_*.cmd сознательно DEFERRED_BY_OWNER, Windows Job не переаттестовывался.

## Native corpus и честный неуспешный кандидат

Первый настоящий GameServer attempt `cf117ec217984dc2a0547dadd553821c` дал33 receipts
в32 сценах: TRAIN17/TUNING8/AUDIT8. Stock Major Heal1401:11 восстановил около989–991HP,
расход80MP (в части lvl78 —76MP), native cast около3,97s. Один дополнительный законный cast
дренировался перед canonical cleanup; он сохранён, а не скрыт.

Model1 SHA `fdddf4ee2b275555403e8c34747601b6687c39642ee8357a2f9c1994f3265949`:
tuningMSE0,5149646→0,0370159, auditMSE0,001656054 против constant0,0008432205.
Результат DRAFT, модель не применялась. TRAIN имел0/17 тройных Heal crit, TUNING2/8;
это штатная случайность native Heal, все исходы сохранены. Оптимизатор/seed/порог не менялись.

Следующий заранее заданный корпус round2:64 TRAIN/32 TUNING/24 AUDIT, новые актёры,
классы Cardinal97/EvaSaint105, уровни76–78 в каждом split, S weapon enchant4–7,
две цели PhoenixKnight90/Archmage94, HP30–51%, разные реальные дистанции по split.
Повторные одинаковые фактические контексты внутри TRAIN — независимые native actors;
контексты между splits не пересекаются. Старые rows/model/audit остаются неизменными,
prefix `giran.v3r2.` выбирает исправленный rich snapshot corpus.

Round2 attempt `bb93739c7dba46c4aba98b4e32add210` завершён exit0:120 точных native HEAL,
64/32/24,24 независимых audit actors и24 audit contexts.42 epochs,12,1558s обучения;
tuningMSE0,5404271→0,0142365. AuditMSE0,015467629 против constant0,015723296:
улучшение1,626%, порог1% пройден. Это скромное улучшение предсказания native reward;
counterfactual MSE неизвестна. Model2 отдельно допущена explicit apply-test после остановки native.
SHA `6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc`;
frozen dataset SHA `bf2039c39f18e4137593129deccf1781b5f4f73bf95d9b0eb7b252465079be6e`.
Immutable model/DRAFT1 не изменён, productionPromotion=false.

Первый cold ON attempt `ac2fd342fad043639404b248169f852a` восстановил model2 из SQL,
4 741 580B, и выполнил4 native HEAL;2 выбора изменены. Например stock target268492228
→ selected268492251,1401:11, HP2308,854268→3299,769938, useful990,915670HP,
MP80,3,9778s. EvaSaint также изменил цель: useful2318,045732HP, MP80,3,4224s.
Каждый выбор из двух lawful stock предложений, source/Party/lease сохранены.
Native selection p95=2,0701ms; encoder p95=1,224ms, scorer p95=0,0571ms.

В этом же attempt DAMAGE Solo дал exact1440HP с прежней model1 SHA, но Party за15s
не успела пройти обычную повторную сборку и native cast: NO_EXACT_NATIVE_EVIDENCE,
scorer не вызывался, MP не расходовался. Это не засчитано как полный PASS. Исправлен
только fixture timeout: Party использует existing config.episodeSeconds(), ранний выход
после первого exact receipt сохранён. Следующий proofRound2 использует свежих актёров;
предыдущие CUT не сбрасываются. Дополнительная сборка/native обусловлена этим дефектом.

Обычный unmanaged Player control прошёл на GameServer: HP866,4→1881,8913,
MP2606→2539,41032, learning credits0, exp/sp без изменений. Это реальный useMagic,
не вызов эффекта тестом и не ручное присваивание HP после CUT.

## Проверки и измерения

- Ant compile-tests: exit0 (37s), Java25; пять прежних compiler warnings.
- Model checks: реальный backprop, deterministic fit/codec/corruption/immutable arrays — exit0.
- Contracts: masks, rich codec, class0, stale authority, future untrained, schema guard,
  partial batch terminal marker — exit0.
- StoreChecks: rich receipt codec/splits/quality/explicit apply, DB_CONNECTIONS=0 — exit0.
- Финальный JAR: learning0014/4, combat-core72/72, Combat ownership38/38,
  Party tactics14/14, training-contracts4/4, post002-support9/9, F8 14/14 —155/155, exit0.
  F7 strict receipt hash PASS, SQL_REPLAY=NOT_RUN. Focused V3 Contracts/StoreChecks exit0.
- Дополнения: два focused RED→GREEN защищают пропуск cooling skill и urgent-fast/calm-stock Heal.
  Legacy V3 schema и bytes hash проверены без изменения, temporal tail имеет отдельный fingerprint.

Warmed model-only synthetic benchmark,128 logical actors:

| Actions | p50 ms | p95 ms | p99 ms | CPU µs/decision | Alloc B/decision |
|---|---:|---:|---:|---:|---:|
|4|0,969|1,072|1,146|986|10416|
|8|0,990|1,147|1,257|1017|10416|
|16|1,021|1,202|1,359|1047|10416|

Это подготовленные synthetic arrays, не native full world collection. Реальный SHADOW:
snapshot n33 p50/p95/p99=0,307/0,611/1,054ms; total attempts n65=0,237/0,622/1,810ms;
dispatch n33=4,718/6,632/18,869ms. Encoder/scorer n0: SHADOW без checkpoint их не запускал.
Alloc n65 p50/p95/p99=38360/38832/107608B. Thread CPU timer Windows имеет грубую
гранулярность (p50/p95=0,p99=15,625ms), это не доказательство нулевого CPU.
GameServer process142,234CPU s за263,596wall s, peak working set4 612 116 480B;
включены startup и world, показатель не является overhead модели.

### Финальный native proof и дополнение владельца

Attempt `45f0b903eef144cab5972d6396be3d4c`, native PID85852, exit0.
JAR SHA `e3649974122d9408a9ade45cfdc89f6110de09d693c6772620e57a2b73be7115`.
На этом JAR model2 снова дала4 HEAL/2 changed, overBudget0. Например stock268492756
→ selected268492779,1401:11, HP2308,854268→3299,769938, MP80,3,9801s.

DAMAGE model1 SHA `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`
не изменён: Solo1230→1339:1,exact1534HP; Party1230→1339:1,exact2942HP,
оба SQL readback PASS. Новой DAMAGE training campaign не было.

Native cooldown: Fire Vortex1339, shared reuse hash1367120, remaining9099ms;
Prominence1230 готов. При его реальном запуске Vortex оставался наКД8942ms;
castRejectedDelta0, nativeOwnHpWrites1, observedNetHpDelta1270, observedNetMpDebit55.
Эти net delta — диагностические наблюдения, не новый exact training label;
в cast task часть MP уже списана. Альтернатива не получила training credit.
Ни disableSkill с тестовым таймером, ни forced selection не использовались.

Calm: Major Heal1401:11, base5000ms/native3954ms, effect elapsed3956,406ms,
HP2989,954228→3981,115389, useful991,161161, MP80, attackers[].
Urgent: настоящая атака NPC268452197, Battle Heal1015:15,
base2000ms/native1581ms, effect elapsed1583,206ms,
HP3063,600001→3409,761162, useful346,161161, MP78.
Оба Skill законны/готовы; более медленный1401 оставался допустимым.
Это deterministic temporal safety выбора stock owner, не заявление об обучении combos.
Смерть союзника не предсказывалась: lethalHorizon=UNKNOWN.

Creature содержит единственный извлечённый native расчёт cast time; beginCast использует его.
V3 сохраняет base hit/cool, реальный query duration, shared/subsecond remaining CD,
readiness/nextAvailability, MP initial/final/HP, predicted effect/busy offsets,
observed native next-phase remaining, interruptibility и bounded trajectory.
Точная будущая busy/end/death/interrupt probability неизвестна там, где нет доказательства.
Channel/toggle/simultaneous/special execution не получают выдуманного единого прогноза.
Старая normalized head сохраняет base-cast semantics; rich timing extension не переименовывает
старые признаки или labels. Модель2 не переобучалась на audit/новых timing probes.

Финальный native policy timing: snapshot n4 p50/p95/p99=0,766/1,133/1,133ms;
encoder n4=1,249/1,301/1,301ms; scorer n4=0,0346/0,0588/0,0588ms;
total attempts n8=0,098/2,585/2,585ms, включая4 deferred/empty attempts.
Dispatch n4=7,277/17,449/17,449ms измеряет native выдачу команды отдельно от выбора.
Alloc p95=103216B; CPU timer n8 выдал0 из-за гранулярности, это не нулевой CPU.
Малый native n4 означает p95/p99=max; это не долговременная нагрузочная оценка.
GameServer168,641CPU s за161,970wall s; peak working set4 726 390 784B, включая startup/world.

Warmed benchmark на120 frozen native observations (512 measurements на ширину,
128 logical actors, повторение реальных action vectors до4/8/16):

| Actions | p50 ms | p95 ms | p99 ms | CPU µs/decision | Alloc B/decision |
|---|---:|---:|---:|---:|---:|
|4|0,978|1,030|1,101|1007|21848|
|8|0,964|1,026|1,061|977|21848|
|16|0,977|1,055|1,164|977|21848|

Это vectorization+encoder+scorer сохранённых наблюдений, не World collection и не16 native
HEAL в одной Party. Native snapshot collection измерен отдельно выше. OFF не собирает V3 snapshot;
новый OFF GameServer ради нулевого счётчика не запускался. All128 decisions в каждом pulse не заявляются.

Per-class AUDIT: EvaSaint105 n12, candidateMSE0,0239313 против constant0,0252777;
Cardinal97 n12, candidateMSE0,0070040 против constant0,0061689 (хуже baseline).
Общий audit улучшен лишь на1,626%; превосходство каждого класса не доказано.
Полные цифры: PERFORMANCE.json; прямые native строки/SHA/exit/resources: EVIDENCE.json.

## Review, ограничения и доставка

Независимые model/store/integration reviews и финальный review проведены.
Два P2 исправлены и повторно статически закрыты: partial batch не выдаёт terminal PASS;
lastSkill не связывается с current target. Сам по себе review не заменяет native acceptance.

Ограничения: узкая TEST HEAL вертикаль; не доказано превосходство на всех профессиях,
PvP/Олимпиаде/осадах; нет GPU trainer, pathfinding или semantic neural model. Counterfactual
награда за невыбранное действие неизвестна; auditMSE не означает тактическое превосходство.
Production promotion не выполняется. Все созданные owned TEST процессы остановлены штатным
STOP, порты свободны. В source TrainingV3.ini восстановлены SHADOW/0/empty SHA и пустой assetSourceRoot.
Manual TRAIN_*.cmd — DEFERRED_BY_OWNER. Native forced interruption/stress/PvP/GPU/lifetime Windows
Job повторно не проверялись; старые среды не запускались.

Accepted portable artifact: `docs/phantoms/tasks/PHANTOM-LEARNING-003/artifacts/accepted-model-2/model-2.zip`.
ZIP SHA `04393f2cbc7ba8c4760e42c6f32ae93edd4b8bf848e50d25e97ee8b4348716b5`;
weights.bin SHA совпал с model2,4 741 580B. Архив содержит только веса/manifest/events/dataset,
без datadir, SQL credentials или runtime config. Полный список изменённых путей — SCOPE_ALLOWLIST.txt;
окончательные outside=0 и Git/encoding checks — SCOPE_GUARD.md, команды — COMMANDS.md.

- mojibake-маркеры в изменённых файлах проверены: полный набор, совпадений0.
- escaped Cyrillic в изменённых файлах проверены: все6 regex, совпадений0.

Автоматическая approval-проверка отклонила прежний auto-ON как неявное применение модели.
Путь удалён; staged DRAFT и отдельный exact apply-test заменяют его. Неуспешный DRAFT1
не обходил этот отказ и не применялся.
