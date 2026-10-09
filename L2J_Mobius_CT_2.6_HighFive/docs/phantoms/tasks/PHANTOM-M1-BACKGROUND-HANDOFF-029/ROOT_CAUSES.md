# ROOT_CAUSES029 — установленное отдельно от предположений

## Подтверждено remote source/evidence028
R1. `PhantomNativeContext.Capture` отвергает SUPPORTED при points!=1;
`simulationEligible()` требует COMPLETED+SUPPORTED+points1. Authority nativeContext()
добавляет обычную consume policy. Это намеренная ограниченная V1-модель, а не доказанный
баг native Vitality. Просто удалить predicate небезопасно.
R2. Реальный away633: READY/v11368, points16361, VITALITY_REQUIRES_NATIVE;
759: READY/v3850, points421, POSITION_REQUIRES_NATIVE. В observed absent interval
state version не сдвинулась. Whole post-return3/5:994/1159 route_absent/cycles0.
R3. `combatFacts()` сохраняет УЖЕ СУММАРНЫЕ getExpBonusMultiplier/getSpBonusMultiplier.
При добавлении vitality нельзя умножить эти значения повторно или вычитать vitality
из capped total: независимые policy facts нужны до caps.
R4. `PhantomBackgroundModel.evaluate()` вычисляет rewards внутри encounter-loop;
текущая state/Command не переносит changing vitality. `UPDATE_MAIN` не пишет vitality_points.
Поэтому правка одного eligibility if оставила бы неверные выплаты/расход ресурса.
R5. Фильтр hooks Contract028Observer зависит от текущей selection. Новая cohort
перезаписала selection; terminal PREPARED/FINALIZED872/1272 не захвачены. Это факт
обсервации, НЕ доказательство потери этих native rewards.
R6. Stationary F1 прошла8/8 и V2, и старую строгую проверку; F2 V2=4/4.
Результаты earned cycles в этих сценах настоящие, но не закрывают whole-return3/5.

## Открыто, не выдавать за доказанный root
U1. First restart028 stop:10s/FAILED, retained0, причина первой фазы не экспортирована.
Следующий43ms PASS не объясняет её. Base027 shutdown contract не ослаблять.
U2. Cooperative route11: real late-damage fixture precondition failure; first cause UNKNOWN.
U3. Windows latest telemetry AccessDenied: shared-reader race правдоподобна, не доказана.
U4. Route994/1159: reason известен, не доказана неправильная геодата либо конкретный producer.
U5. Legacy027 EVENT24354 first delayed executor-entry cause остаётся UNKNOWN; не приписывать
ему нынешние отказы и не replay.

## Решение координатора
Не заменять весь behavior controller. Вертикаль029 — operation-specific background
capability + точная передача состояния и полный lifetime proof. Existing farming,
native actions и bounded lifecycle — переиспользовать. Бонусы/position должны быть
входными данными модели, а не специальным разрешением для633/759 либо Келтиров.

## Доказательства candidate C3
- EXACT_REPLY_RED: rollback с потерянным ответом принимался как SUCCESS по старому receipt.
  Исправление commit() сверяет identity и точный operationKey; committed reply-loss даёт
  IDEMPOTENT, unchanged prestate даёт RETRY. EXACT_REPLY_GREEN:4/4, реальная SQL/lease race.
- P05_REGISTER_RED: отрицательный profile создавал обязательство. Теперь positive identity
  и conflicting duplicate проверяются до регистрации; bounded32/128 без eviction.
- P02_TERMINAL_RED: отсутствующий published work sample ошибочно считался ненулевым.
  Повтор с именами штатных counters опроверг эту модель. Terminal sampler вне hooks
  теперь читает actual outstanding()/pendingTimers() после DETACHED+permanent.
  P02_NATIVE_COUNT_GREEN подтверждает temporary SEALED не terminal и отдельную new epoch.
- FIRST_ACTUAL_AWAY_R2 остановлен на NATURAL_COHORT_COUNT0 до отхода; F03 НЕ выполнен.
  R2_EMPTY_CENSUS фиксирует ecology_fenced и незавершённые catchup; root пока не установлен.
- B_R2_STOP: Game/Login graceful exit и отсутствие listeners подтверждены. Login attach
  получил Premature EOF при выходе и оставил native exit1. Это диагностика helper,
  не доказательство first shutdown028; старый U1 остаётся UNKNOWN.

## Доказательства candidate C4
- PREMIUM_PERSISTENCE_RED: native capture отклонял premium вместо сохранения с unsupported
  policy facts. Native persistence теперь сохраняет его; ordinary background reward запрещён.
  PREMIUM_PERSISTENCE_GREEN:3/3. B05 party также сохраняется без ordinary permission.
- P04_EDGE_RED: actual productive typed farm не публиковал независимый operation witness.
  Commit observer добавлен после SQL success/exact resolution, до освобождения BACKGROUND lease.
  P04_EDGE_GREEN:4/4; P04_OBSERVER_UNION_GREEN:1/1. Hook только bounded enqueue, без FS/SQL.
- NATIVE_CONTROLS_RED_OR_GREEN: fixture применял passive rune через applyEffects и получил0.
  Creature.addSkill подтверждает штатную установку stat funcs; исправлен только fixture.
  NATIVE_CONTROLS_NATIVE_SKILL:4/4, реальные EXP30/SP50, caps, disabled/zero/use=false,
  native consume0/negative, повторный float carry и clamps. Пороговые проверки не снижены.
- ACTUAL_AWAY_C3: NATURAL_COHORT_COUNT0 до отхода. C3_PRE_SCENE и readonly PLAY export
  доказывают, что startup background уже переместил группу из Elf area в current farm areas
  и изменил XP/vitality. Это не доказанный F03: commit observer тогда ещё отсутствовал.
  DRY_C3_APPROACH отклонён water bounds; MOVE не запускался. PROBE_C3_CURRENT_ZONE отклонён
  INVALID_ARGUMENT (setup не передал instanceId); teleport к далёкой группе не выполнялся.
  Старые U1/U2 и пропуски872/1272 остаются UNKNOWN/UNPROVEN.

## Проверки C5 без предположительного product fix
- C4_EXISTING_18:18/18 routes,92/92 cases. C4_NATIVE027_ACCEPTANCE:11/11.
  Route11 текущего кода прошёл; исторический U2 этим не объявлен исправленным.
- B04_BOUNDARY_RED был setup-only: неверное имя fixture field transactions.
  После чтения NativeProductionFixture используется transaction. B04_BOUNDARY_SEMANTIC_RED
  фактически GREEN:5/5. Actual native release, один encounter до level8, policy mask128;
  следующий FARM = REPLAN/native_context.required:accepted, canonical state unchanged.
  Гипотеза обхода level guard через legacy predicate НЕ подтверждена; production не изменён.
- TASK single checked setup реализован existing SNAPSHOT_PHANTOMS→exact TELEPORT_SELF.
  Геометрия checked через штатный dry helper до MOVE и до выбора rewards/outcomes.
  Setup теперь bounded45s без reset clock; Probe может проследить cold-dead группу,
  это диагностическое наблюдение, не снижение alive admission Scene/Away.
- PROBE_D_CURRENT_C4: пять actual cold-dead участников, поэтому baseline primary admission
  failed. Последующий SQL export подтверждает native return/HP и homeXYZ46045,41251.
  PROBE_D_HOME_C4: native cohort0. Конкретный stale-route producer этим ещё не доказан.
  Запад/восток/север current long dry routes rejected, MOVE не выполнялся.
- D_C4_STOP: stock Game/Login exit подтверждены; force не использовался.

## Native scalar RED/GREEN C5
- B04_NATIVE_LUCKY_RED / B04_LUCKY_ATTESTED_RED: actual native XP reached level10,
  but native vitality15997.111 != background16127.667. Native target20534 has
  baseHP48.09168 and computed maxHP75; old model used baseHP as full damage too.
- NATIVE_TARGET_HP_GREEN:6/6, actual release/SQL transaction and exact float bits agree.
  Separate target maxHP scalar fixes full-kill loss; legacy Target constructor retained.
  Unspawned native scalar projection always deletes its temporary NPC; no World admission.
  Model/rate fingerprint discriminator changed as part of the same numeric correction,
  so pre-correction V2 FARM facts require fresh native attestation; no codec migration.
- G actual stock start was RUNNING despite stale helper LASTEXITCODE1; exact PID/incarnation
  exported independently. No repeated START. PROBE_G_NATIVE_C4 admitted1 cold-dead actor,
  so4..8 admission failed. PROBE_G_ORIGIN_ROOT retained native single actor252 with
  actual new epoch and6 farm/reward cycles in the existing locality census. This is
  individual native recovery evidence, NOT whole-group F03/F04 or a final scene.
- At180min, no admitted4..8 actual away/background episode is proven. Deadline remains
  MISSED; command admission attempts are not renamed into actual background success.

## Prospectively observed lifetime birth C6
- C6_P06_BIRTH_RED used an unsupported TEST focus acceptance: production fixture was
  absent. This is setup-only and not counted as a semantic RED.
- C6_P06_BIRTH_SEMANTIC_RED uses the existing review focus:6/7 PASS; actual materialized
  owner was not enrolled before its first owned checkpoint. No manual enrollment in P06.
- C6_P06_BIRTH_GREEN:7/7. Passive service afterPlayerLoad callback registers actual owner
  before capture; generic CAPTURE accepts only a uniquely registered current exact owner.
  Native owned checkpoint/key/receipt bytes remain unchanged. Callback failures stay in
  observer exporterFailure and cannot alter native admission. Union stays bounded32/128.
- TEST exporter shutdown initially raced pool close; explicit bounded drain was added
  before closing the existing fixture database. Product acceptance requires zero errors.
- First G cold252 epoch281699997540400 lacks prospective receipt enrollment; the healthy
  later epoch281745537122100 cannot repair it. Both and old872/1272 remain UNPROVEN.
- G_PRE_C6_STOCK_STOP: Game35600 and Login14824 stock exits confirmed; initial typed
  shutdown phase/reason still must be inspected independently. Attach EOF is not ignored.
