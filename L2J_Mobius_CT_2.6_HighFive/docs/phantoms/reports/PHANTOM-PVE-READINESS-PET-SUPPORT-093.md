# TASK093 — PARTIAL / DBFREE_VERIFIED

Дата: 26.10.2026. FULL093_GREEN не объявлен. Native gameplay, heal/recharge093 и restart: NOT_RUN.
Baseline local/HEAD/upstream/GitHub: `1561540e52303ebf01e34861520bc0292b2b50e4`, ветка `feature/phantom-world`.
Пакет прочитан; все 8 entries MANIFEST_SHA256.json совпали по byte size/SHA-256, strict UTF-8 VALID.
CLI ls-remote получил сетевой отказ; GitHub compare baseline→feature вернул identical/ahead0/behind0.

## Результат A/B и consumer proof

**A — DB-free verified, native NOT_PROVEN.** Перед новым farm target читаются реальные HP/MP,
equipped weapon/expertise, уже выбранный learned damage primary, совместимые owned shots,
owned healing stock, действующий native buff/time, fed/live BabyPet и party membership.
Shot stock ограничен 256 inventory entries; support Pet не добавляет damage либо будущие эффекты.
Сам факт денег означает запрос существующего supply маршрута, а не доказанную доступность покупки.
Для рискованного solo уровня40+ предпочтительны gear/shots;76+ дополнительно проверяются реальные buffs,
HP/MP и potions. Безопасный native viable target не запрещён одним отсутствием shots.
Фактический threat и native target viability остаются обязательными; вывод не является гарантией победы.

Readiness применяется в `PhantomVisibleAutoPlay.Policy.permitsTarget`, который используют штатные
AutoPlay/AutoUse. Busy continuation допускает только текущую цель; corpse/Sweep и accepted native work
сохраняют владельца. Недостаточная подготовка даёт конкретный reason и конечный45s срок перед existing
recovery/replan. Deadline переносится только при восстановлении регистрации того же goal/revision/epoch.
Новый goal получает свежие факты; долг полезного farm progress штатно не сбрасывается.
HP/MP recuperation и retreat используют существующий resourcePause/CombatPolicy/native backend.

Нового acquisition owner нет: `PhantomSystem` уже регистрирует L2jPhantomSupplyJourney candidates/handlers
и включает `needsOrOwns`/`ownsAutomaticIntent` в visible/accepted intent до обычного farm decision.
Readiness не покупает предметы и не обещает успешный supply: оплаченные buy/return/shortage остаются у
этого child, с прежними budget/fee/stock/weight/store/receipt guards. Native supply093 не наблюдался.
MAGIC→Action2 OFF, physical/no-primary→Action2 ON и skill-based physical сохраняют прежнюю capability
и AutoUse alignment. Learned self-buffs/potions исполняет прежний AutoUse до offensive target gate.
Дополнительные stun/debuff rotations и формирование party не добавлялись: будущий Tactical Combat/094.

**B — DB-free/source verified, native NOT_PROVEN.** Общий AutoPlay summon ATTACK имеет PhantomPolicy
guard; default true и null-policy human ветка сохраняют прежнюю семантику. Phantom policy разрешает
owned Servitor и только stock wolf IDs12077/16025/16030/16037/16041/16042. Остальные Pet исключены.
Вторая ветка new-target/party-assist напрямую командует Player; второго summon ATTACK там нет.
Backend coordinateServitor по-прежнему управляет только Servitor; AutoUse pet buff/cast не менялся.
Stock XML wolf/Buffalo/Kookaburra/Cougar IDs и available skills проверены bounded чтением.

Visible admitted session вызывает существующий `L2jCombatBackend.tryNativeSupportPet` через минимальный
`prepareNativeSupportPet` bridge с live Player/epoch и materialization action lease. Scan ограничен5s
в существующей Session; при busy/movement/pending store/loot/yield/retreat новый summon не выдаётся.
Control item и native handler остаются единственным producer; BabyPet AI владеет follow/food/heal/MP.
Pet060 подтверждает локальный паттерн, но его старый native результат не является новой приёмкой093.
Новая native проверка food/rehydration, всех buff roles и поддержки в опасном бою не выполнена.

## Проверки и review

- Исходный RED: combat-core60 total/59 PASS/1 FAIL, unconditional support Pet ATTACK, exit1.
- Первое GREEN: combat-core64/64, exit0; добавлено5 focused093 contracts.
- Независимый review нашёл2 Important: busy candidate bypass и перенос expired deadline в новый goal.
- Regression RED65/64/1, exit1; исправления одним bounded проходом; GREEN65/65, exit0.
- Повторный независимый review исправленных строк: Critical0/Important0. Повторный full skill resolve убран.
- Финальный standalone shadow `ant -f <proof>/shadow/build.xml -Dsrc=<module>/java jar`: exit0,23s.
- JDK25.0.4.1/Ant1.10.17;2369 production sources;4601 JAR class hashes совпадают с build/bin.
- Финальный affected проход на classpath с новым GameServer.jar первым: все6 команд exit0:
  `PhantomTestLauncher combat-core 93009301`65/65; `supply071 93009301`5/5;
  `PhantomOfflineMarket074Checks`; `PhantomOffscreen092BChecks combat <module>`;
  `PhantomOffscreen092BChecks <module>`; `PhantomOffscreenObservation092AChecks`.
- Эти092B commands — только DB-free modes; historical native campaign/binding-correction не запускались.
- Pet060: исходный helper не менялся, test compilation прошла; live pet060/093 НЕ запускался.
- OwnerConfig.ps1 -Action Verify: exit0, filesChecked68/overridesStored287/conflicts0/changed0.
- Source config SHA-256 совпадают с preimages; source dist/libs/build не были целями записи.
- Один первоначальный full compile-tests exit0,30s, прежние5 compiler warnings; standalone jar ровно1.
- Общий ant verify, DB tests, soak, дополнительные native fixtures и owner QA: NOT_RUN.

Промежуточные ошибки не скрыты: sandbox OwnerProfileVerifier AccessDenied до compile (exit1),
адресный разрешённый elevated verifier exit0/38 rules PASS; последующая shadow сборка штатная.
Один targeted javac exit1: ошибочный package ActionType, исправлен по import существующего supply owner.
Это ошибки подготовки/реализации, не новые production blockers вне scope.
Scope helper:2 parser failures (PS5 UTF-8 и smart quote в marker), затем plain diff check принял CR за
trailing whitespace; применён прежний092C per-command cr-at-eol pattern, без изменения исходных line endings.

## Native safety / STOP native

Native START0; restart START0; JDBC/DB writes0. OWN093 schema и stage не создавались.
Обычный prepare-phantom-test-db вызывает DROP DATABASE и exact default test schema; он не запускался.
Проверенные create-only074/086/087/091 helpers закрепляют чужие namespaces/owner manifests;
091/092B helper не допускает093. Подходящий existing clean OWN093 bootstrap в проверенных seams
не подтверждён. SAFETY.md п.2 требует native STOP при missing clean bootstrap; bypass не делался.
Нет утверждения, что093 отсутствует на сервере: состояние её ownership/DB connections не исследовано.
Read-only process preflight: других java/javaw не найдено. Native shutdown/drain NOT_RUN — server не стартовал.
Полезных durable093 изменений нет; условный restart не применим. Releases057–092,092B/092C DB,
MAIN/PLAY, owner runtime, client, Geo и соседние worktrees не изменялись.
Бюджет дополнительных production исправлений:2/2 review findings, оба закрыты; иных обходов нет.

## Read-first, scope, ограничения

Initial READ_SET12: root AGENTS, module Agents, current status, workflow, efficiency, указанный092C report;
VisibleAutoPlay, AutoPlayTaskManager, L2jCombatBackend, SupplyJourney, AutoUseTaskManager, Pet060NativeSupport.
Пять дополнительных causal files: NativeCombatCapability, CombatPolicy, PhantomSystem composition,
CombatCoreSuite, HistoricalBackgroundService.prepareVisibleDecision. Planner/recovery не переписывались.
Локальные аналоги: resourcePause/retreat native leases; lawful supply child; control-item BabyPet060.
Writer set сохранён: session commandGate, materialization ActionLease, PlayerNativeWorkScope,
AutoPlay/AutoUse, native Pet AI, supply/commerce, Goal/Background persistence, earned loot/Sweep/store.
Новых worker/timer/cache/schema, Player monitor либо второго semantic/combat engine нет.

Отклонение read budget: дополнительно прочитаны короткие API fragments NativeEquipmentPolicy и
SupportEffectAuthority сверх5 causal files. Также читались обязательные build/launcher/OwnerConfig symbols
и адресные bootstrap/affected-runner seams. Полных чтений классов>1000 строк0, whole project dump0.
Это отклонение не представлено как соблюдение лимита. Первый rg inventory был излишне широк;
до первой правки выполнены6 поисковых вызовов. Первый status --untracked-files=all дал огромный
усечённый вывод; для сохранности dirt использована normal summary, исходный tracked/index dirt отсутствовал.
README/master-plan за пределами closed READ_SET заново не открывались; package SOURCE_MAP найден.
Разрешённый final scope9:6 production,2 tests,1 report; package и чужие untracked остаются вне commit.

Production: `phantoms/background/PhantomVisibleAutoPlay.java`;
`phantoms/combat/{PhantomCombatPolicy,PhantomNativeCombatCapability,L2jCombatBackend,PhantomNativePetRolePolicy}.java`;
`taskmanagers/AutoPlayTaskManager.java`. Tests: `PhantomCombatCoreSuite.java`, `PhantomPveReadiness093Suite.java`.
Все Java paths находятся в модуле; новых source/config/data families нет. Девятый файл — этот отчёт.

- mojibake-маркеры в изменённых файлах проверены: полный набор, совпадений0.
- escaped Cyrillic в изменённых файлах проверены: отдельные6 regex, совпадений0.

## Evidence / delivery

Preimages, compact logs, argv/exits/elapsed, JAR binding и final guards:
`C:/Users/ZBook/.codex/visualizations/2026/10/26/01a1774c-e764-77f2-8926-243f19aa9278/task093/`.
`final-commands.json` содержит exact final test argv; `final-binding.json` — JAR SHA/class count/config guard.
До delivery snapshot Goal usage293561 tokens/1357s; счётчики продолжают расти до финального ответа.
Tests:4 промежуточных core runs (2 RED/2 GREEN),1 финальный; targeted compile3, test-only compile2;
standalone jar1, full ant verify0; финальный scope/text verifier1 успешный проход. Native0/conditional restart0.

Git использовался по TASK/SAFETY093 и root bounded exception: status(short/all, затем normal),
rev-parse HEAD и --git-dir/--git-common-dir, symbolic-ref --short HEAD, for-each-ref точной feature/upstream,
ls-remote origin refs/heads/feature/phantom-world (network failure), diff --stat/-- <exact paths>.
Whitespace check: `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <exact paths>`.
После финальных guards разрешены только exact git add9, staged diff review/check, один commit с
`DBFREE_VERIFIED / PARTIAL_NATIVE`, один normal `git push origin HEAD:refs/heads/feature/phantom-world`.
SHA/push result фиксируются после commit в delivery receipt/финальном ответе; этот отчёт не предвосхищает push.
Force/reset/clean/stash/branch/merge/history rewrite не выполнялись. Следующий task093-R2/094 не начинался.
