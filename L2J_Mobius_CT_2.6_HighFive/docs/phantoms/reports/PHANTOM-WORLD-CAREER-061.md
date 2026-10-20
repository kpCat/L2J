# RESULT061 — A/B выполнены; финальные KEEP/доставка проверяются

Тот же TASK061. Дополнительный бюджет: 19.10.2026 14:47UTC → 20.10.2026 06:47UTC; финальный резерв начат05:13UTC. Решение владельца заменило обязательный Q401 общими Phantom-only профессиями20/40/76 и configurable main80 Noblesse. Полный М7 остаётся PARTIAL.

## Реальные результаты A и B

**A, обычный Main без test-controller.** Thor66,6/268436825, Elf Fighter5: самостоятельно купил11Soulshot за88A, затем у штатного учителя изучил Power Strike3:1 за50SP, вернулся и получил2 native kills/rewards (+418EXP/+10SP). Сам выбрал3:2; actual STOP/sameDB START сохранил тот же goal8694734301784961104/rev23, field/class/index/owner и434085мс оставшегося бюджета. После рестарта дошёл до учителя, заплатил ещё50SP, вернулся; ещё2 native kills/rewards дали +364EXP/+9SP. Между ними сам купил3+1Soulshot и вернулся к фарму. Это chain2боя→решение→дорога/плата→2новых боя, с настоящей JVM restart-границей.

Штатный STOP сохранил EXP3678/SP11, skill3:2/index0, weapon2369, HP180/180, MP67/67, Adena6/SS0. Баланс: EXP2896+782; SP92+19−100=11; Adena126−88−24−8=6; SS11+3+1−15=0. Бесплатных ресурсов/переезда/лечения/skill/class во время ordinary сцены не было. Дальнейший дефицит денег/сосок остаётся ограничением, не скрыт за PASS цепочки.

**Другой ordinary класс:** Steelpo22/268436954, Human Mystic6, появился рядом с passive18. В том же Main/PID14984 World EXP9514→10501/SP426→454, затем native покупка9Blessed3947 у30004 за360A. Actual STOP сохранил результат:10начальных−8расход+9покупка=11Blessed, Adena520−360=160. Следующий обычный sameDB START/PID28312 сохранил identity/EXP/SP/stock/оплату и продолжил прежнюю обратную дорогу с278908мс. Повторной оплаты нет. Это earned/action/resource/save факты; число native kills22 не выдумывается по EXP.

Raw A: `.phantom-local/contract058a/task061/final24-life21-03..04`, `final24-earned-stop6-facts.tsv`, `final25-life21-01..06`, `final25-life6-stopped-facts.tsv`; второй класс — `final25-life18-01..03`, `final25-baseline22-live-facts.tsv`, `final25-secondary22-stop-facts.tsv`, `final25-observer22-01..02`, `final25-final22-stopped-facts.tsv`. Все времена/PID/JAR и actual usercmd receipts сохранены рядом.

**B, representative native сценарии на финальной whole25:** `final25-auto-career`6/6. Production Decision выбрал сохранённую законную Human ветку0→7→8→93. Native Trainer/SkillTree выдал4:1 за3400SP; после первой профессии2 настоящих20139 kills без invul: EXP835862→836804, SP1600→1638 после оплаты, SS300→294; actual owned save/reload. Dwarf53→118 и femaleKamael124→133 прошли по3 законных edges20/40/76 и сохранение. Main80 Noble/native skills/save/repeat,79negative, busy/foreign branch/idempotency/index и race/role/sex/tree guards прошли. Подготовленные20/80, SP5000, gear68/24/31 иSS300 — TEST input, не заработанная прокачка. Tree11roots×6roles не равны66 игровым прохождениям. Raw: `.phantom-local/contract058t/reports/task061/final25-auto-career/quest-instance-goal036.txt`.

Q401 OPTIONAL: исходные states/items35/36 сохранены, marks/cond/class/reward не выданы. Полный Q401 не объявляется завершённым.

## Общие архитектурные причины и исправления

- Долгие действия теперь живут как прежние durable child intents/root witnesses. Active budget и конечная calendar boundary сохраняются через pause/dematerialization/JVM; повторное admission восстанавливает прежнюю цель. Один scheduler/Decision/ProfileRepository, без второго AI, хранилища или новых таймеров.
- Между Historical admission и native travel устранён допуск только к прежнему committed anchor. Подходящая назначенная дорога передаётся существующему travel owner; departure не считается arrival, capacity/Geo/ownership/class/time guards сохранены.
- Learning/Supply сначала запрашивают полный native PathFinding в действующем12k policy limit, затем прежние максимум17 checkpoints. Greedy шаги больше не исключают поддержанную дорогу с временным удалением от цели. Бюджет WALK использует actual MoveSpeed, finite distance/legs/active time. Learning route failure context сохраняется в прежнем intent; root revision/SP/restart не создают бесконечный одинаковый повтор. Transient capacity сохраняет retry.
- Исправлен cross-thread native ActionLease owner/store lifecycle. Native paid AutoGet/SP и class receipts не превращаются в бесплатные навыки/повторные награды. Старые потери не реконструировались.
- CareerChoice выводится из native PlayerClass/CategoryData, сохраняет совместимую конечную ветку, роли55/8/8/12/7/10 и старые identities. Progression вызывает native Player/class/skill/Noble/save, прежний owned-store подтверждает соседние20/40/76 edges. Люди/ClassMaster/global AutoNoblesse не менялись.
- Шоты определяются действием и точным грейдом оружия: физический гибрид сохраняет независимые SS+BSS, магия предпочитает Blessed. Native handlers действительно расходуют их на атаках. Beast6645/6647 по native H5 расходуются из inventory владельца pet/servitor; разрешённое finite пополнение на60 атак проверяет current Phantom owner, вес/вместимость. Detached owner отвергается; новых pet таймеров нет.
- Необходимые расходники используют reserve min(500, четверть кошелька), gear сохраняет500. Exact bound expired Supply может пройти через Decision к существующему terminal/cleanup вне поля; cold admission/pin/deadline не добавлены. Старый child21/rev48 при нынешнем root50 не перепривязан; ordinary26 exact child действительно стал FAILED/supplies.deadline.

## Проверки и сохранённые отрицательные исходы

На текущем коде native learning/lifecycle `native-walk24-reviewed-learning`10/10; Supply `supply-expiry25-red2`0/1 → `supply-expiry25-green`1/1. Первый expiry RED имел неверный after-start TEST binding, исправлен до причинного RED2; raw обоих сохранён. Whole25 отличается от whole24 только reviewed Supply expiry owner; все4482 production class bytes целого clean build совпадают с native-tested Task JAR.

Исходные frozen23 suites/111 case IDs: CURRENT111_PENDING. Предыдущийfc22/A0B0E6D7 прошёл111/111 с retained IDs111; текущая проверка — `fc25.tsv`/`fc25-frozen111-console.log`. Удалённых baseline IDs нет.

Затронутые механизмы: native shots13/13 (`hybrid061-action-green03`, RED12/13), lowFunds1/1, longSupply12/12 (RED11/12), pureCombat53/53 (RED52/53), native paidAutoGet/SP5, learning/lifecycle10, C60/C61 ownership2. M3 recall4 и known-context CAS11→12 сохраняются с явным reuse: message/recall/context owners текущими route/Supply дельтами не менялись. Единственная coverage-матрица — `docs/phantoms/tasks/PHANTOM-WORLD-CAREER-061/ACCEPTANCE061.tsv`; версии/ограничения указаны там.

Оставшиеся границы:6 окончил проверенную цепь сA6/SS0; экономическая самодостаточность/неэффективные мелкие закупки и backoff через смену root revision требуют продолжения. Thorbat18 получил travel.no_route_within_budget/LOCAL_FARM_UNAVAILABLE; это FAIL маршрута, не успех22. Старый supply21 root48 не относится к root50. Старые дополнительные8 Goal036 gameplay controls дали8/8, но after-all оставил4 native earned Pailaka BLOOM timers/DRAIN_TIMEOUT; этот scope вне frozen111 остаётся OPEN. Исходные quarantine/orphan/raw не удалялись. М7 PARTIAL; М1/G4/G6 по-прежнему отложены. Это не OWNER_QA_PENDING.

## Поставка, сохранность и публикация

`D:\PhantomWorld\World-Career-061-candidate`, release DB `l2jmobiush5_phantom_release_061a`, Login2106/Game7841/control9141. Functional commit `f7b8883c8456f92f25b6fcda9c0037a1f3cae492`, GameSHA `68AA76DE98352A1D1D42897C77AA8F5490AE18613B4F90168EC20AE337F73C4D`. Whole build из committed archive, без overlays; старые manifests/preimages сохранены, обновления build дали DB_WRITES=0. Поставка использует один consistent stopped export058a/124 таблицы; reset/bootstrap/reimport не повторялись.

Настоящие START/STATUS/STOP.cmd запускались из C:\Windows. Последние START: `final25-secondary18-start01`45.738s/exit0/force0 и `final25-observer22-samedb01`39.047s/exit0/force0. Последний STOP — `final25-final-stopped01`; конкретные receipts и финальные STATUS лежат в task061. Источник058a и кандидат061 оставляются STOPPED. FINAL_DELIVERY_PENDING.

Защищённые057–060/их БД/конфиги/персонажи/процессы, MAIN/PLAY/прочие worktrees, backups, общая MariaDB, клиент и OS settings не менялись. Attach/jcmd/jstack/jmap, force kill, crash/fault и обход платформенных запретов не применялись. Обычные игровые earned данные сохранялись native путём.

Изменённые functional файлы80 перечислены exact-path от BASE18a5c708 в `CHANGED_CODE_FILES061.txt`. Основные owners перечислены выше; docs и private raw отделены от functional inventory. Полная прежняя хроника остаётся в Git da5efb8 и `pre-final-report061-20261020T0326/RESULT.da5efb8.full-history.md`. Один актуальный RESULT, одна матрица, `START_HERE_061_RU.md` с наблюдавшимися партнёрами; review ZIP не создавался.

Git использовался по прямому разрешению пользователя и AUTHORITY§6: `git status --short -uno -- .`; `git diff --check -- .`; `git diff --stat/--name-only/--quiet -- <exact scope>`; `git rev-parse HEAD`; `git ls-remote origin refs/heads/experiment/m1-candidate007-observe008`; `git add -- <exact changed paths>`; `git commit -m 'Prefer bounded native routes and retain failed learning context'`; `git commit -m 'Settle expired supply intents through the existing Decision owner'`; doc commits; `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`; `git archive --format=zip --output=<private build input> HEAD -- <exact build paths>`. Force/reset/restore/clean/branch/rebase/amend не выполнялись. Итоговый report HEAD/remote equality фиксируются в BUILD_INFO.finalReportCommit и финальной проверке.

mojibake-маркеры в изменённых файлах проверены:0.
escaped Cyrillic в изменённых файлах проверены:0.
