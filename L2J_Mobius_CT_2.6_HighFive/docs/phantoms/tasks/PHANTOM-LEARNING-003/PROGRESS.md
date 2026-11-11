# PHANTOM-LEARNING-003 — журнал

## Основание и рабочая копия

- Активный goal003; пользовательский D:/Codex/repos/L2J-phantom-world строго read-only.
- Независимый GitHub clone в разрешённой task writable-папке, каталог `l003`.
- База b7b5575457c12c1b0438bcbaa646675a67778391, собственная ветка wip/phantom-learning-003.
- GitHub readback protected refs совпал с пакетом; ветки003 до работы не было.
- Архив D:/l2j_learning_003.zip распакован без изменения структуры в High Five.
- PLAN.md задаёт bounded exception и четыре связанные задачи одной HEAL вертикали.

## Выполнено

- Полный требуемый пакет003, relevant 002 handoff/result/report/decisions, инструкции и seams прочитаны.
- Baseline Ant compile-tests: первый запуск exit1 OwnerConfig AccessDeniedException в sandbox;
  тот же штатный запуск с разрешённым доступом exit0,31s. Код build/OwnerConfig не менялся.
- TacticalModelV3:1,185,377 настоящих float32 параметров,4,741,508B weights,72B header.
  Math-only train32/tuning8/audit8; full backprop, deterministic fit, strict codec; checks exit0.
  Model-only warmed128actors:4/8/16 actions,p95 1.072/1.147/1.202ms,10,416B/decision.
  Независимое Task1 review не нашло дефектов. Это не native обучение или full snapshot benchmark.
- Typed rich observation/action, masks, bounds, binary roundtrip. Focused contracts RED missing003 → GREEN exit0.
- Native snapshot source-confirmed APIs, класс/уровень/stats/gear+jewels/effects/skills/Party/summon/NPC/geometry.
- Separate HEAL cast ticket, scoped HP/MP status-writer observers, native MagicUseTask lifecycle;
  Heal.java и V1 LearningCastEvidence/codec не изменены. Targeted javac этих seams exit0.
- Bounded Party HEAL scorer: OFF/SHADOW/ON, emergency/pressure stock,2s cooldown,one global CPU slot,
 5ms fallback. Party lease и canonical dispatch остаются владельцами.
- Native reuse audit подтвердил class97/Cardinal,1401:11 и stock helper creation/Party goal APIs.
- TrainingV3.ini и optional ConfigProfile V3 переиспользуют existing Director/Job/G0/SQL/STOP;
  новый supervisor не создаётся. Own TEST root сохраняет proven002 physical layout в новом clone.

## Фактический native проход и корректировка корпуса

- Собственная новая SQL/GameServer TEST работает через неизменный Director/Job/G0 layout002.
- Native attempt cf117ec217984dc2a0547dadd553821c:33 точных HEAL receipts,32 actor/scene contexts,
  TRAIN17/TUNING8/AUDIT8. Major Heal1401:11, реальные HP/MP writer и external lease identity.
- Обычный unmanaged Player: HP866,4→1881,8913; MP2606→2539,41032; learning credits0, exp/sp unchanged.
- Первый fit seed183/model1 честно DRAFT: auditMSE0,001656054 > TRAIN-constantMSE0,0008432205.
  SHA fdddf4ee2b275555403e8c34747601b6687c39642ee8357a2f9c1994f3265949.
  TRAIN не содержал тройного Heal crit (0/17), TUNING содержал2/8; все исходы сохранены.
- Критерий1%, optimizer и seed неизменны. Round2 заранее задаёт64 TRAIN/32 TUNING/24 AUDIT,
  классы97/105, уровни76–78 в каждом split, реальные disjoint позиции/дистанции и новые actors.
  Context prefix выбирает исправленный native corpus; старые rows/run/model неизменны,
  использованный audit никогда не переходит обратно в обучение.
- Независимое review: исправлены два P2 — partial batch не публикует terminal marker;
  неподтверждённый PastAction(lastSkill,currentTarget,snapshotTime) удалён, RECENT_SKILL сохранён.
- Ant compile-tests exit0,37s; focused Contracts и StoreChecks exit0. Регрессии67/67,
  F7 и F8 exit0 до последней локальной корректировки; финальный повтор ещё ожидается.
- Автоматическая проверка отклонила auto-ON. Этот путь удалён: fit сохраняет только DRAFT,
  даже qualified; отдельный apply-test требует exact reviewed id/SHA на stopped native boundary.
  SHADOW Resume по безопасному staged пути разрешён. Дополнительный manual gate не вводится.

## Итог технических проверок

- Model2:120 native events64/32/24; fresh audit улучшен1,626%, explicit apply-test exit0.
- Final attempt45f0b903eef144cab5972d6396be3d4c: cold model2 sameSHA,4HEAL/2changed,
  DAMAGE Solo/Party exact receipts и прежние model1weights; native exit0.
- Дополнения владельца: shared/subsecond CD и native duration/next-phase/interruptibility contract;
  native1339 наКД9099ms→готовый1230, rejectedDelta0. Calm1401≈3,956s против urgent1015≈1,583s,
  настоящий NPC attacker и exactHEAL HP/MP. Temporal contract не подменяет старые labels.
- Последние Ant compile-tests/jar exit0;155/155 affected tests,F7,Contracts/StoreChecks exit0.
- Accepted-model warmed128actor bench p95≈1,03–1,06ms; полный native choice p95≈2,59ms.
- Штатный STOP exit0: свои процессы остановлены, порты свободны. Source config снова SHADOW/0.
- Native proofs/metrics PASS. Scoped Git delivery выполняется после exact scope/encoding checks;
  окончательный HEAD фиксируется в delivery receipt. Manual TRAIN_*.cmd — DEFERRED_BY_OWNER.

## Изоляция

Документированные readonly Geo manifests cycle19 найдены при разрешённом read-only доступе.
Предыдущие SQL datadir не копируются и не запускаются. Новый TEST использует existing installer
в собственном datadir/портах33337/17981/19281. TRAIN_*.cmd manual acceptance остаётся DEFERRED_BY_OWNER.
