# PHANTOM-LEARNING-005 — выполненные команды

Дата: 12.11.2026, Europe/Chisinau. Все команды ниже уже выполнены; команды плана в этот список не включены.

## Разрешение и Git

GOAL/TASK разрешили independent clone от004, новую005 и scoped публикацию. Общий root AGENTS разрешает bounded ownership inspection. Пользовательский checkout читался только тремя следующими Git-командами:

```powershell
git rev-parse --show-toplevel
git symbolic-ref --quiet --short HEAD
git status --short
```

Все exit0. Root ожидаемый; ветка `feature/phantom-world`; присутствует чужой untracked dirt, не изменялся.

Из `D:/Codex/repos`, вне sandbox для создания разрешённого нового каталога:

```powershell
git clone --single-branch --branch wip/phantom-learning-004 https://github.com/kpCat/L2J.git D:/Codex/repos/L2J-learning-005
```

Затем только в новом clone:

```powershell
git rev-parse HEAD
git status --porcelain
git switch -c wip/phantom-learning-005
```

Все exit0; exact pinned base `dff800f4b95232c132b374f8ec97c932eda4a6b1`, до восстановления пакета status пуст. Commit/stage/push пока не выполнялись. Других Git-команд в подготовке не было.

Независимый readback через GitHub connector GET `repos/kpCat/L2J/git/ref/heads/<branch>` подтвердил:

| Ref | SHA |
|---|---|
| wip/phantom-learning-004 | dff800f4b95232c132b374f8ec97c932eda4a6b1 |
| feature/phantom-world | faec72161f9e4f6231764e17d6f2637a4db080af |
| wip/coordination-130-preserved | c5b81f89ce02030eff8441395b0411137a110b2f |
| wip/phantom-learning-003 | 06d42410fbfe1ca9b7f462b0816e0da1784630d6 |
| wip/phantom-learning-002 | b7b5575457c12c1b0438bcbaa646675a67778391 |
| wip/phantom-learning-005 | NOT_FOUND, HTTP404 до создания локальной ветки |

## Входной пакет

Локальный recovery script читает приложенный текст, отделяет17 fenced/plain payloads, нормализует LF, отказывает при существующем target, сохраняет UTF-8 и проверяет все15 размеров/SHA против manifest. Python exit0; все15 совпали, MANIFEST SHA также совпал с SHA256SUMS. Recovery script находится во временной writable области этого чата, исходный checkout не используется для записи.

## Штатный preflight

CWD нового clone:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1 -Action Preflight -ConfigProfile V3
```

Первый запуск sandbox: exit1, UnauthorizedAccessException в `G0SqlIdentity.ps1` physical path guard. Повтор той же команды с разрешённым elevated sandbox scope: **exit0**. Штатный вывод подтвердил config/JDK25/MariaDB11.4/Geo203/protected207/свободные порты; сборок и запусков серверов не было.

## Не выполнялись

Build/tests005, jar, Start/Run/Resume/Stop серверов, SQL install/import, native A/B, model ON, trainer, commits и push. Отсутствующие доказательства перечислены в PLAN.md.

## После подтверждения плана — продолжение

Предыдущий раздел описывает состояние до подтверждения; пользователь подтвердил автономную реализацию.

- Canonical `ant -f ./L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests`: первый exit1 (package-private quote), исправлено локальным escaping; native-route первый exit1 (import Monster), исправлено. Остальные компиляции exit0,5 прежних warnings.
- `java -cp 'build/bin;build/phantom-test/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*' org.l2jmobius.tests.phantoms.PhantomLearning005Checks`: ожидаемые RED matching/sink/foreign exit; GREEN18→20→23→26. Последний pre-native main exit0,26 assertions, CONTRACT_ONLY.
- `PhantomTestLauncher <route> 183` из dist/game с absolute module/reports/classpath: learning0014, combat-core72, combat-action-ownership38, party-tactics14, training-contracts6, post002-support9;143/143, все exit0.
- F7 main PhantomVisibleItinerary068Checks exit0, DB_CONNECTIONS=0/ACTORS=0/SQL_REPLAY=NOT_RUN. F8 main PhantomNativeEvidenceContinuation022Suite с module/reports args:14/14 exit0.
- `Training.ps1 -Action Preflight -ConfigProfile EVAL005` exit0:33347/17991/19291 и pinned assets. PowerShell parser PASS; исходный UTF-8 BOM сохранён.
- `Training.ps1 -Action Start -ConfigProfile EVAL005 -EnableEvaluation005 -Evaluation005PairLimit 1 -Evaluation005Campaign smoke005a` exit0, Director53136; native outcome ещё ожидается.
- Own clone scope: `git status --short`, `git diff --numstat --` четырёх exact изменённых путей. Reviewer: `git diff --` тех же4 paths. Stage/commit/push пока не выполнялись.
- Для нового разрешённого consolidation в original checkout read-only: `git rev-parse HEAD`, `git worktree list --porcelain`, `git for-each-ref --format='%(refname) %(objectname)' refs/heads refs/remotes/origin`,3 `git merge-base --is-ancestor <old-worktree-HEAD> <feature-HEAD>` — все exit0. Connector compare feature→004 ahead162/behind0 и TASK130→004 ahead19/behind0.

## Финальные проверки безопасной части

- Последние canonical compile-tests и jar exit0;28 focused assertions,143 affected assertions,F7 exit0 и F8 14/14 exit0. Final JAR SHA4caa20432d64ffffcd65faf92d815ec4fa4bb2cd615f53f9154269d9fbcf62ca.
- Own `Training.ps1 -Action Stop` exit0, свои33347/17991/19291 свободны. SQL/GameServer не запускались: failed guard был на STAGING, до их создания.206/207 protected hashes совпали, единственный OwnerConfig mismatch не обходился.
- `git status --short -- <exact OwnerConfig.json>`, `git ls-tree HEAD -- <exact OwnerConfig.json>` — readonly диагностика. Файл clean относительно HEAD, но исторический protected SHA отличается; причина не установлена.
- Для new consolidation helper выполнил `git ls-tree -r -z --name-only HEAD` отдельно в source005 и original;10 collision документов TASK130. Это readonly artifact inventory, разрешённый новым поручением. Никаких originals до окончания005 не заменялось.
- Exact39 allowlist/source SHA guard PASS с явным исключением20 generated private runtime файлов. Проверка staged paths запрещает включение private runtime. Whitespace check4 tracked source paths exit0.
- Отдельные full mojibake-marker и six-regex escaped-Cyrillic checks на39 allowlisted текстах: exit0/0matches каждая. Входные MANIFEST/SHA256SUMS подтверждены отдельно.

## Окончательное выполнение после baseline005 разрешения

Записи выше исторические. Авторитетный окончательный JAR: **aba85844f9afa9669f8ad32844fe3c315588acd7a706c32d3b232b1a96cc4e97**, protocol **0f2d23f4621e8934ec2c68d7b4bad6da4c8ab6a913580ba2519a5ca8e55d6bfd**. Scope9→10 разрешён пользователем отдельно; исключение описано в BASELINE005_EXCEPTION.md.

- После разрешённой exact COPY207: admission drain failure исправлен bounded15s ожиданием по локальному аналогу, затем реальный smoke005a4arms. Control exit0 не приравнивается к успешному native gate.
- `Training.ps1 -Action Start -ConfigProfile EVAL005 -EnableEvaluation005 -Evaluation005PairLimit 1 -Evaluation005Campaign smoke005b` сохранил установленный smoke005a config и отказал `Campaign pins changed`. После Stop новый campaign записан только в own TRAIN_CONFIG существующими Read-TrainConfig/Write-TrainConfig, затем `-Action Resume -ConfigProfile EVAL005`: smoke005b4arms/4nativekills,2UNMATCHED пары из-за случайной позиции NPC.
- Пробный eval005c остановлен до nativeCUT после review headless teleport. Основной eval005d: finalsource frozen, own config writer задал campaign и4pairs/режим; `Training.ps1 -Action Resume -ConfigProfile EVAL005` запустил generation7. Native16arms завершены, затем PAUSED; повторных серий ради победителя не было.
- Existing Director последовательно выполнил `ant -f ./L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests` и `ant -f ./L2J_Mobius_CT_2.6_HighFive/build.xml jar`, оба exit0/5прежних warnings. Во время005d source/JAR не пересобирались.
- На final build `java -cp 'build/bin;build/phantom-test/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*' org.l2jmobius.tests.phantoms.PhantomLearning005Checks`: exit0,28assertions, CONTRACT_ONLY. Повторные affected runner routes на этой же сборке:143/143exit0; F7 exit0/DB_CONNECTIONS0/ACTORS0/SQL_REPLAY_NOT_RUN; F8 14/14exit0.
- `resume_snapshot005.py before`; `Training.ps1 -Action Stop -ConfigProfile EVAL005`; `Training.ps1 -Action Resume -ConfigProfile EVAL005`; `resume_snapshot005.py after`: generation8, freshDirector/freshEvaluatorWorker=true, immutableEqual/summaryEqual=true, nativeRerunAvoided=true. Это fresh evaluator process resume; завершённый GameServer budget не повторялся.
- Финальные `Training.ps1 -Action Stop`, `-Action Report`, `-Action Export` с ConfigProfile EVAL005: exit0; own33347/17991/19291 свободны. Own SQL/GameServer реально использовались; послеSTOP не остались работающими.
- `inspect_protected.py <own protected207.005.json>`:207checked,0mismatches; старый manifest immutable. Accepted V1/V3 и optimizer8 ZIP SHA — в EVIDENCE.json.
- `verify_preparation.py <taskroot> hashes`:15payload hashes + manifest/SHA256SUMS,0failures. `verify_scope.py <clone> scope`:156exact paths,10source,0errors,26653private runtime files excluded; staged outside allowlist запрещены. `git diff --check -- <10 sourceFilesSha256 exact paths>` exit0.
- **mojibake-маркеры в изменённых файлах проверены:**156paths, полный27-marker набор,0matches/exit0.
- **escaped Cyrillic в изменённых файлах проверены:**156paths, все6regex,0matches/exit0.

## Отдельно разрешённый разбор старого index

Пользователь подтвердил: «Старая работа — сохрани и разбери автономно». Read-only: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --porcelain=v1 --untracked-files=no`, `git rev-parse --git-dir`; MERGE_HEAD отсутствует. Один broad `git status --porcelain=v1 --untracked-files=all` выдал чрезмерный список private/untracked; после этого применялся bounded tracked/status inventory. Вывод приватных файлов не публикуется.

`preserve_index005.py`: `git ls-files --stage -z -- <each of23 receipt paths>`, `git cat-file blob <each exact stage blob>`, `git ls-tree HEAD -- <each exact path>`;23working files, всеstageblobs иindex сохранены сSHA. Committed HEAD SHA metadata исправлена отдельным cat-file readback; исходный receipt сохранён. Independentreview: bounded `git log --all --format=%H --find-object=<blob> -- <exact path>`, `git log --raw -- <exact path>`, `git cat-file -p <receipt/history blob>`, `git merge-base --is-ancestor`. Старые предложения сохраняются; следующие игровые goals не активируются автоматически.

Scoped stage/commit/push и consolidation фиксируются отдельным publication receipt после фактического выполнения.
