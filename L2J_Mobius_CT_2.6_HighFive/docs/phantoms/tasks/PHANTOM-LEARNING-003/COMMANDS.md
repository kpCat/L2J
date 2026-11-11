# PHANTOM-LEARNING-003 — существенные команды и результаты

Все изменения/запуски выполняются в независимом clone. Относительные команды ниже запускаются
из High Five module, если явно не сказано иное. PowerShell5 для existing Windows launcher;
для Java `-D...` аргументы quoted, иначе PowerShell делит dotted option.

## Сборка и focused checks

```powershell
ant -f .\build.xml compile-tests
ant -f .\build.xml jar
```

Ant запускается последовательно. Compile-tests exit0,37s; jar выполняется самим Director,
пишет только в собственный clone/build и own TEST copy. Первый baseline без разрешённого
filesystem access дал OwnerConfig AccessDeniedException; тот же штатный Ant с доступом exit0.
Изменений OwnerConfig/build инфраструктуры для обхода не было.

Из корня clone, classpath:

```powershell
$cp='build\bin;build\phantom-test\bin;L2J_Mobius_CT_2.6_HighFive\dist\libs\*'
java '-Dfile.encoding=UTF-8' -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning003ModelChecks
java '-Dfile.encoding=UTF-8' -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning003Contracts
java '-Dfile.encoding=UTF-8' -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning003StoreChecks
```

Focused checks exit0. Model benchmark описан отдельно в отчёте. StoreChecks synthetic/DB-free
проверяет codec/guards, а не выдаётся за native обучение. Ошибочный unquoted `-Dfile.encoding=UTF-8`
один раз дал launcher ClassNotFoundException; quoted повтор exit0, исходники не менялись.

Existing PhantomTestLauncher routes: learning001, combat-action-ownership, party-tactics,
training-contracts, post002-support (seed183). Они дали67/67, финальный повтор также67/67. Runner должен иметь module root
и cwd `dist/game` для XML data. Первый party-tactics из module cwd не нашёл statBonus.xml;
правильный cwd дал13/13 без исправления production XML. F8: native-evidence-continuation022,
seed22002203,14/14. Его standalone main — `PhantomNativeEvidenceContinuation022Suite <module> <reports>`;
имя suite не зарегистрировано как launcher mode (ошибочный route exit2, правильный main exit0).
F7: `org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks`,
DB-free, SQL_REPLAY=NOT_RUN; финальный повтор exit0.

## Собственная native TEST

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action Preflight -ConfigProfile V3
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action Start -ConfigProfile V3
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action Resume
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action Stop
```

Для Windows5 host process-local PSModulePath установлен на System32 WindowsPowerShell modules;
машинные environment/settings не менялись. Initial prep не прошёл при неподходящем helper
magicLevel76 и Archmage76 без Vortex77; исправлен только fixture selection по native XML.
Следующий entry отказал до CUT из-за schema alias mismatch; добавлен ранний validator regression.
Одна startup File.Replace IOException не воспроизвелась на следующем штатном STOP/Resume;
ownership infrastructure не менялась. Повторов без конкретной причины не было.

Director сам выполняет существующие SQL prepare/actor/native/evaluate/report worker commands.
Native SHADOW evidence и offline fit — разные процессы. Ни один DRAFT не считается applied.
Точные id/SHA отдельного apply-test, cold ON, performance, STOP и Git delivery вносятся
в итоговый report/evidence после их фактического исполнения.

Explicit apply выполнен отдельно, exit0, после PAUSED/native exit0:

```text
TacticalTrainerV3 <own-root> apply-test 2 6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc
```

Первый cold ON дал4 настоящих HEAL,2 изменённых выбора и DAMAGE Solo1440HP.
Party NO_EXACT_NATIVE_EVIDENCE честно retained:15s fixture window не хватило для
канонической повторной сборки Party по5s pulses. Использовано existing config episode window,
не изменены владельцы/формулы/противники. Fresh proofRound2 не сбрасывает прежние CUT;
сборка/native повтор имеет эту конкретную причину. Промежуточный interrupted retry
сохранён как partial, не terminal PASS.

## Git

Пользователь явно разрешил independent clone, новую ветку, scoped commits и non-force push.
Root AGENTS разрешил bounded branch/scope inspection. Пользовательский checkout READ ONLY.
Использованы independent GitHub clone исходной ветки и `git switch -c wip/phantom-learning-003`
на exact base; rev-parse/symbolic-ref/status и exact-path diff — для проверки branch/scope.
Remote refs сравниваются через `git ls-remote --heads` с пользовательским GitHub URL.
Перед stage задаётся exact `SCOPE_ALLOWLIST.txt` (68 путей); `git add .` не используется. Private runtime,
datadir, credentials, logs и model exports исключены локальным `.git/info/exclude`;
его исходная версия побайтно сохранена вне checkout с проверенным SHA.
Финальные scoped команды (из корня independent clone):

```powershell
$allow=Get-Content .\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-LEARNING-003\SCOPE_ALLOWLIST.txt
git status --porcelain --untracked-files=all
git -c core.whitespace=cr-at-eol diff --check -- $allow
git add -- $allow
git -c core.whitespace=cr-at-eol diff --cached --check -- $allow
git diff --cached --name-only -- $allow
git commit -m "feat(phantoms): train tactical V3 HEAL with native timing and readiness"
git push -u origin HEAD:refs/heads/wip/phantom-learning-003
git rev-parse HEAD
git ls-remote --heads origin refs/heads/wip/phantom-learning-003
```

Также `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, exact-path `git diff -- <paths>`
и `git ls-remote --heads origin` с перечисленными protected refs применялись для branch/scope guard.
Ни merge/rebase/reset/clean/stash/forcepush, ни операции в пользовательском checkout не выполнялись.
Конкретные exit codes и опубликованный HEAD отражены в delivery receipt; это отделяет receipt
от commit, чей hash невозможно включить внутрь него самого.
