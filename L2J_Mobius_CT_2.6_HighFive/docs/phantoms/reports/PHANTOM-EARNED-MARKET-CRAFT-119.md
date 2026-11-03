# TASK119 — earned market/craft: проверенный результат и открытые границы

Статус: **PARTIAL / WEALTH_NOT_PROVEN**. FULL119 **NOT_PASSED**; OWNER_QA_PENDING.
Проверены production selector, native NPC liquidation→paid stock→rewarded farm и durable restart.
Paid offline customer, warehouse/craft, earned-only refill и rewarded gameplay после restart не доказаны.
STOP119. TASK120 не запускалась.

## Baseline, read-first и сохранность

Live GitHub `kpCat/L2J`, `feature/phantom-world` независимо подтверждён до source mutation:
`1edfa2ae4c4f89907845afd33713d79c0088d876`; внешнего drift при G0/run не было.
Parent baseline: `e04acc6804e011ff43fe0ea5122e4cf99111cad2`.
Основной checkout сохранён на `236b965a116bbc79cccbef78b04c628225a629cb` с уникальным dirty WIP109.
Отдельный физический clone создан из чистого exact118 source через `clone --no-hardlinks --single-branch`;
его baseline HEAD также1edfa2a, HEAD detached. Ни исходный checkout, ни старые worktrees не обновлялись.
1228 protected preimages118 совпали по размеру/SHA; семь файлов119 расширили guard до1235.
G0/postflight сохранили1235; исходная физическая Geo203 повторно SHA-проверена после native.
Все task DDL/DML ограничены новой119; MAIN/PLAY/TEST109–118 не использовались как тестовая БД.
MariaDB/release057–061/клиент/публичные конфиги не изменялись.

Initial READ_SET12: семь файлов119, root/module AGENTS, workflow, efficiency standard, final report118.
Дополнительное bounded чтение: README/readme, status/master-plan/task standard, нужные блокеры117,
build/OwnerConfig, mapped commerce/pricing/recipe owners, existing suites и private G0/native118 protocol.
Расширение вызвано isolated build refusal и обязательной native ownership/byte-seal проверкой.
Вышестоящие AGENTS не найдены; отдельный code-map не искался повторно — использован SOURCE_MAP119.
Полные большие Java/master-plan и отчёты074–116 не перечитывались; другие хроники не исследовались.
Аналоги: Supply071, Living111, Life116, Economy118; LootDisposition/AcquisitionRecipePlanner сохранены.

## Ровно шесть public artifacts

Все пути ниже внутри High Five:

- `java/.../commerce/PhantomOfflineSupply.java` — selector считает premium после упущенного farm cash.
- `java/.../economy/PhantomPrivateMarketPricingAuthority.java` — native target/drop/rates и прежняя valuation policy.
- `java/.../commerce/L2jPhantomSupplyJourney.java` — передаёт текущий farm root в pre-dispatch recheck.
- `test/.../commerce/PhantomEarnedMarket119Checks.java` — пять contrasting production-selector cases.
- `test/.../knowledge/PhantomEconomy118Checks.java` — source-backed opportunity/rounding/unsafe-input checks.
- Этот отчёт.

Без нового кошелька, marketplace, изменения игровой схемы, worker, timer, config или провайдера.
Unknown target/yield/cost и unsafe arithmetic закрывают offline sale; NPC/maintenance fallback сохранён.
Native chance×1/amount×5 для57 и остальные rates сохранены. OwnerConfig288 overrides, Apply changed0.
89 остальных config-файлов byte-equal source118; OwnerConfig нормализован checkout на один перевод строки:
91840→91841bytes, нормализованный текст одинаков, Git diff отсутствует. Overrides не пересериализовывались.

Writer review: изменения только read-side selection; эффекты остаются у Commerce/MultipartyEconomy/
PrivateStoreService/Inventory/Recipe/Warehouse. Action/participant leases, native list monitor,
reservation/CAS/strict listing/object hashes, COMMITTING/reconcile и restart fences не заменялись.
Новый расчёт внутри list monitor читает только immutable snapshot/policy; новых locks/JDBC/writers нет.
Existing unknown-price exceptions остаются fail-closed; ambiguous native effects не превращаются в успех.

## Synthetic RED→GREEN и предел модели

Baseline compile-tests и life11632/32, Supply0719/9, quote6/6, recipe-cost4/4 — PASS.
Для воспроизведения extraction сохранил прежнее production ranking без изменения поведения.
Runtime RED: premium100 принимал поездку с потерянным farm cash101. Затем GREEN5 contrasts:
loss/break-even/unknown отклонены; net50 выбран вместо gross100/net10; пустой рынок не создаёт sale.
Selector применяется реальным offerSale; это не только проверка quotation formula и не paid trade.
Source XML NPC18342: certain7..13 Adena, current57 amount5/chance1, author4kills/min/cost0 →200A/min;
old amount1→40A/min,1ms округляется до1,0ms→0; unknown/negative/unbounded отклонены.
Это **cash opportunity proxy**, не измеренная скорость NORMAL7 и не полная стоимость loot/spoil/расходников.
Существующие4kills/min/cost0 не изменялись. Actual route/Geo не доказывается admission estimate.
Финальные affected runs:11632/0719/quote6/recipe4, Living111 и Craft101 contracts3 — PASS.
Общий `ant verify` с DB gates не запускался; новый тест-проект не создавался.

## Единственная TEST119 и две native фазы

Full G0 PASS до CREATE: physical/disjoint/private owner+SYSTEM ACL, exact OS image/PID/SID/port/datadir,
JDK25.0.4.1/Ant1.10.17/MariaDB11.4.13, full SQL grants/metadata visibility, foreignSessions0,
protected1235, Geo203/1063452308bytes,29093 sealed native inputs, final JAR/code hashes.
Schema `l2jmobiush5_phantom_test_119`, UUID `a74b0341-8587-45ae-ad74-c960971217e5`.
Ровно один CREATE:121 stock files/203 statements;18 DROP пропущены; foreignWrites0.
Private scope: cohort24/active8/finite merchant target24, GS17779; LoginServer не запускался.
JAR один: `3d63ae118eabe954f0b424aaef0140bf2deefea045819962433b65217cf5c748`.
Обе фазы использовали ту же DB/JAR; code inputs после сборки не менялись.
Reuse10 прежних private helpers специализировал119 authority names/UUID/schema/pin; числовые game IDs не заменялись.
Observer — явно обозначенный pre-cut LOCALPLAY_TEST_HUMAN; его setup/follow не меняли деньги, предметы или координаты NORMAL.
После cut он поддерживал observation locality; это не travel/commerce evidence NORMAL.

Native1: calendar predicate выбрал NORMAL7/object268485477/class38; не задана новая роль/цель/город.
Historical catch-up завершился без reset. Первый materialized snapshot18:44:59Z: level3/EXP735/SP70/cash5150.
5000 — настроенный StartingAdena,150 — pre-cut historical balance; исходные gear/Thread отдельно от нового native loot.
Самостоятельная NPC liquidation: SELL1121×8=16A,35×15=210A,36×8=112A; всего31 object/338A.
Native BUY3947×124 за4960A, NPC30137/object268467482/list3013700, rev44;
operationKey `69bd131800f55e682c1559ab2230b7172e78584b4346f05bdc688930f48edccd` COMMITTED.
Receipt: cash5488/stock0→528/124, physicalXYZ12153,15541,-4568,instance0.
Получены5 actual native rewards/+525EXP/+50SP/22 loot observations после покупки/возврата;
nativeFirstUnprovenReason=NONE. Level4/EXP1260/SP120/cash738; shots object268513901:124→118.
Thread1868/object268486557×10 сохранён; Gloves35/object268486552 экипирован; новый48/object268524201 также экипирован.
Всего32 уникальных NPC COMMITTED. Новые native cash210 согласуются:5150+338+210−4960=738.

Native2: новый epoch530703692810800 против529835995053200; тот же object268485477,
EXP1260/SP120/cash738/shot object268513901×118/Thread×10/extra gear восстановлены, без reseed.
Старые32 receipts не посчитаны новой оплатой; повторного списания4960A нет.
Наблюдение18:59:29–19:10:34Z: native rewards0, EXP/SP/cash/stock без изменений.
Первый явный отказ: supply native ROUTE_BUDGET_EXCEEDED, origin18554,19226,-3631 →10935,18013,-4552;
затем child maintain.supplies/item426/rev50 FAILED supplies.deadline, root farm.background/rev4 NEEDS_REPLAN.
HP/MP полные, native AutoPlay не зарегистрирован, historical.visibleRecovery=LOCAL_FARM_UNAVAILABLE.
Existing terminal очищает route, сохраняет FAILED и releaseDemand→REPLAN. Полная причина отсутствия
дальнейшего productive recovery не доказана; этот исход не объявляется исправленным или baseline regression.
Deadline/proof debt не сбрасывались; третий такой же native запуск не выполнялся.
Оба штатных STOP/STORE/exit0/postflight PASS; error logs0bytes, World/JVM/listeners/online/foreignSQL0.

## Реальные экономические альтернативы и отрицательный результат

В наблюдавшемся BUY:1835×100 по6A,3947×100 по36A; у владельцев clientNull=true/capital50100000.
Это конечное OWNER_PROVISIONED финансирование, не заработок NORMAL. BUY capital в снимках оставался50100000.
1835 не принадлежал NORMAL7; его3947 — нужный оплаченный stock, NPC acquisition40A, resale36A даёт убыток4/шт ещё до дороги.
У проданных nonstackable35/36/1121 подходящего BUY не наблюдалось. Thread×10 защищён recipe reserve.
Для Stem/Varnish/Bone source-only BUY50/100/75 равен NPC floor; это не доказательство живой ликвидности.
Ни весь будущий рынок, ни все supply generations этим bounded окном не исключаются. B=NATIVE_OPEN.
Реальные MANUFACTURE listings были; listing/recipe не являются paid craft. Например recipe66/fee296A
требует1927×3,1867×110,1868×55 для27; этих inputs у NORMAL7 нет (Thread только10).
Warehouse snapshot пуст; полезного native deposit/withdraw/craft/crystallize не наблюдалось. C=OPEN, retention=NATIVE.

Consumption6×40=240A; remaining paid stock cost basis4720A, XML reference-half2006A — разные оценки.
Новый native loot:48×12 reference168A и1121×6 reference12A; это180A inventory equity, не исполненная продажа.
338A NPC proceeds реализовали pre-cut historical items, не новый доход измеренного native farm.
Native cash210−consumed240=−30A; новая reference equity180A отдельно. Смешанная cash/stock-basis сумма
включает историческую liquidation и не доказывает новое богатство. Полный cash+changed-reference delta **−2564A**;
cash delta−4412, первоначальные защищённые/неизменные assets сокращаются в сравнении.
XML reference-half — оценка, не чек продажи и не market quote. Long-term profitability не доказана.
Первая покупка4960 использовала исходный смешанный wallet; original5000 не исключены. Earned-only refill=NOT_PROVEN.
Native>600=NOT_RUN: фактический полезный affordable order124. Унаследованные4×600 остаются POLICY_VERIFIED118.

## Стоимость, проверки и доставка

Попытки:1 sandbox build отказал до javac (OwnerProfileVerifier AccessDenied, changed0);
штатный privileged read-only verifier38 PASS, compile-tests32s PASS; focused runtime RED1/GREEN1;
affected javac2 compile rounds; source XML audit1; baseline suite runs4/final suite runs4 +Living111/Craft101;
standalone ant jar1/20s; full ant verify0. Private harness compile1, stage1/DB1/native starts2.
Четыре reuse guard refusals до Geo/SQL: прежние authority name/case и PS5 root-array wrapping;
исправления только119 private adaptation, старые helper SHA сохранены. Это лишние orchestration расходы.
Native measured elapsed1014051ms+731162ms≈29.1min; max heap4069183232/4057224728bytes,
max JVM threads136/135. Native2 дал≈11min без reward; отрицательная проба не повторялась.
Raw logs SHA/размеры сохранены private:664 exact Login retry records исключены только из summary;
остальные7 WARNING видимы:2 missing optional custom-buylists,1 NO_USEFUL_NATIVE_FARM_PROGRESS,4 shutdown.
Multiline/continuation не скрываются; final continuations0. Прочие Login events не исключаются по logger целиком.
Pre-final Goal snapshot:444032 tokens/3957s; USD не измерялись. Ориентир400k превышен:
обязательная изоляция/G0/два lifecycle witnesses и слишком дробное чтение/private адаптация увеличили расход.
Дополнительные files/searches точным счётчиком не измерялись; closed READ_SET/search ориентиры расширены явно выше.
Final exact six-path scope guard выполняется дважды byte-identical; whitespace и обе encoding проверки обязательны.
Mojibake-маркеры в изменённых файлах проверены отдельной проверкой.
Escaped Cyrillic в изменённых файлах проверены отдельной проверкой.
Code review текущих5 code/test artifacts: независимый P1=0/P2=0; native им не подменяется.
Final report/evidence review и команды публикации подтверждаются финальным ответом/private receipts.

Git разрешён TASK119 для pin/isolation/scope и scoped ordinary publication. Использованы rev-parse,
symbolic-ref/status/worktree list/for-each-ref, exact-path diff/diff --check, clone --no-hardlinks --single-branch,
ls-remote exact feature ref; scope guard safe.directory только process-local для собственного clone.
Финальные разрешённые формы: add -- <six exact paths>, commit -m <task119 subject>,
push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world, затем live remote HEAD check.
SHA/push result — в финальном ответе/private receipt, без self-reference commit hash внутри собственного отчёта.
FULL119 не принят: B/C и строгий D открыты; состояние после119 безопасно остановлено. STOP119.
