# RESULT063 — Studio Java parity и чистый checkout

20.10.2026. **TASK063: SUCCESS по двум заявленным блокерам.** Custom и v3 staged Java content gates пройдены; настоящий v3 proof принят Studio. Постоянный независимый checkout подготовлен для GitKraken. GUI/Designer и игровой runtime не запускались. PSS-011 не начата; Phantom AI не менялся.

Baseline: `db375d6b519c017e12b8bd8150aca5d213359633`, `kpCat/L2J`, `feature/phantom-world`. Публикация — обычный non-force push только в эту ветку. Точный delivery HEAD и его равенство live remote фиксируются после commit в локальном `delivery-receipt063.json` и финальном сообщении, без перезаписи отчёта ради собственного SHA.

## Аудит и изменение контракта

Прочитаны корневой и Studio AGENTS, RESULT062, README, DESIGN_RU, Directory.Build.props/Core/tests csproj, оба operator scripts, оба Java probes, loader, content suite/launcher, source fixtures и V3ReleaseHandoff. Переиспользованы физический shadow, stamped copy, консольные source/handoff routes и паттерн внешнего workspace из TASK062. Дополнительные AGENTS выше checkout, отдельные code-map/pattern-файлы в целевом Studio scope не найдены. Стек Java25/Ant, .NET10/C#14/WinForms и зависимости сохранены.

- Старые full pins: build `048a16cd53f694d85803b16af631e14fb20c0c3b49f400a749a328d572c63114`, catalog `b7876a52a487bc1e1bc469c9c71b47de805b31993e589b4be7ca55182c640970`.
- Текущий build: CRLF `f6bebf5452655de3e08d8941134307650b830c2d0c1f31ded1cc1d0f2bd5fdc1`, LF `dafce95145d8d722138dfa16515aac0271110cd80254c204e308a03f78ecddca`.
- Текущий catalog: CRLF `ef0361e344a39e3c6c0e6f2b5f5cb4b4d4c82924df1b9fe956ea983cccc7e840`, LF `a33ec0bbac8407430a695c443722c76140f39abed18d1629e78cc82388d1b335`.
- Все шесть прежних target-body SHA, зависимости, оба classpath SHA и pathconvert SHA совпали без изменения. Closure: `checkRequirements → init → compile`, `checkRequirements → init-test`, `compile,init-test → compile-tests → phantom-humanized-v3-content-validate`. Default cleanup, jar, DB/native M1 targets недостижимы из этой команды.
- Относительно документированного reference `3fd4aa5…` build получил `phantom.m1.native.focus` и два отдельных M1 targets; они не входят в closure. Loader получил предпочтение точной relationship band при выборе template. Реальные probes проверили выбор нового template на текущем loader; Java-код не изменялся.
- Copy: production Java 2296→2336, test Java 188→275, resources 8→12, dependency JAR по-прежнему 4. Новые разрешения — только `test/resources/phantoms/{party-state-v1.base64,M1TimerBootstrap.java,M1Q266Bootstrap.java,M1NativeEvidenceContract.java}`. Это inert resources на content classpath: source Java из resources не компилируется этим target, native/bootstrap modes не выбираются. Wildcard для `.java`/`.base64` в resources не добавлен.
- Обновлены согласованно оба operator scripts и C# importer proof. Exact input SHA/bytes, source/stage disjointness, reparse guards, лимиты 4000 файлов/256 MiB и free budget 512 MiB сохранены. Build paths, reports, Java tmp, copied data и corrupt-копии закреплены внутри собственного oracle. DB/config/credentials/.git, GameServer/LoginServer/sources JAR не копируются. PSS003 также очищает/восстанавливает CLASSPATH по существующему v3 паттерну.
- Java bridges не изменены: настоящий `loadV3(root,true)`, baseline/stage counts/hash, exact IDs/text/act/topic, understand/select, deterministic reload и duplicate/schema native exits сохранены. HMAC и доверенные operator/bridge hashes не отключались.

## Проверки

Все Java запуски последовательные, только отдельные физические сцены. Фразы фиксированные синтетические, без LM, установки и публикации Semantic Pack.

| Проверка | Результат |
|---|---|
| Свежая v3-сцена со старым guard | RED: exit2, `BLOCKED_JAVA: unaudited build contract` |
| Настоящий Java proof со старым C# pin | RED: exit2, `UNAUDITED_ORACLE_BUILD` |
| `Test-PSS003-Java.ps1 -StageRoot <fresh custom stage>` | PASS_JAVA_STAGED; Ant0, bridge0, duplicate child3 |
| `Test-PSS008-V3-Java.ps1 -StageRoot <fresh v3 stage>` | PASS_JAVA_STAGED_V3; Ant0, bridge0, duplicate/schema children3/3 |
| LF build/catalog на отдельной fixture-копии | Реальный v3 Ant/loader и C# native proof/handoff PASS |
| Обе CRLF-сцены | 2628 copied inputs / 33348950 bytes; 5310/20975→5311/20976 patterns/templates; по 2 exact IDs |
| Studio `--pss-008-handoff <genuine proof> --synthetic-operator-review` | PASS; изменённые stage/log/operator/attestation отвергнуты, bytes восстановлены; только offline proposed/backup/manifest/checklist |
| `--synthetic-forgery-negative` | PASS; подделка metadata/log hashes с прежним MAC отклонена |
| 18 preflight negatives на отдельной копии входов | PASS; build/catalog drift, неизвестные Java/base64 resources, превышение byte budget, source/stage drift, protected path и junction; scratch/Ant не создавались |
| `scripts/Build-Verify.ps1` | Release PASS, static Designer PASS, console122/122 |
| Runner `--task062`, `--pss-010` | PASS2/2 и11/11 |
| Полный исходный High Five до/после | 78277 файлов / 1135527771 bytes; пути, bytes, SHA256 и mtime неизменны |

В Java compile-tests остались два прежних предупреждения `System.runFinalization()`; ошибок нет. GUI/VS Designer round-trip, сервер, игровые действия, DB, LM и качество генерируемой речи: **NOT_RUN**. Content PASS не означает runtime parity или разрешение установки.

Первоначальные отрицательные source fixtures дважды завершились необработанным .NET exception и показали Windows crash dialog. Преждевременная параллельная сборка получила MSB3021 из-за удерживаемой DLL. Исправлен только вход console runner: exception → диагностика/exit2, без аварийного окна. После завершения этих процессов сборка и проверки выполнены последовательно. Отказы sandbox-доступа также возвращались exit2 и затем проверялись с разрешённым доступом к отдельному workspace; не считались product failures или PASS.

## Чистая постоянная рабочая копия

**`D:\Codex\repos\L2J-phantom-world`** — независимый shallow single-branch clone `https://github.com/kpCat/L2J`, реальная локальная `feature/phantom-world` с tracking `origin/feature/phantom-world`. Обычный `.git`, без commondir/alternates и зависимости от старой object store. Старый checkout занимает одноимённую ветку в прежней worktree-family, поэтому независимые refs исключают конфликт. Object pack около137 MiB; исходный High Five около1.06 GiB. Это постоянная source copy для открытия в GitKraken и выбора как project folder в Codex.

Перед изменениями clone был чистым на baseline; после delivery commit требуется чистый tracked/untracked status и HEAD=live remote. Игнорируемые собственные build/test outputs не становятся пользовательским dirt. GitKraken GUI не запускался: проверены repository metadata, ветка/tracking и фактический Git status. Старый checkout и все существующие worktrees не переключались, не очищались и не переносились.

## Сохранность и решения владельца

Повторно проверены **1852/1852 оригинала и1852/1852 backup**, по11131955 bytes на набор: точные размеры/SHA256 совпадают с inventory TASK062. SHA inventory `17c126be1c01554d47a692781c9f3f35277893fa35f4e0af92bef35017e3be43`; manifest, VERIFIED1852.csv и tracked-originals.patch целы. Старые файлы не удалялись, не перезаписывались, не staging/promotion в Git.

Во внешнем evidence root TASK063 сохранён точный `owner-decisions063.csv`: **1217 путей**, отличающихся от final baseline или отсутствующих в нём: 1167 ABSENT_IN_FINAL, 47 отличающихся untracked, 3 tracked DIFFERENT_GIT_BLOB. Из трёх tracked две содержательные локальные дельты — Run-M1RuntimeHandoff.ps1 и STATE.md; PhantomMaterializationService.java отмечен inventory как отличающийся от final, при этом RESULT062 не доказал локальную текстовую дельту к старому HEAD. Это не новый код для автоматического переноса. Остальные635 inventory entries совпадают с final blob (633 untracked +2 tracked), но также сохранены. Все5 tracked M и1847 untracked остаются в старой папке; причину status-only M не выдумывали.

Evidence root: `C:\Users\ZBook\.codex\visualizations\2026\10\20\01a159f9-1a4b-7363-8a72-f1188628dfc1`. Там находятся полный перечень решений, повторная верификация, журналы, plan/review, точный реестр Git-команд и delivery receipt. Private workspace, XML, proof/logs, ключи и пользовательские файлы не публикуются. Новый ZIP не создавался.

## Scope и Git

Ровно8 файлов: два Java operator scripts, Core/V3ReleaseHandoff.cs, tests/{Program,Pss003,Pss008}.cs, Studio README_RU.md и этот RESULT063.md. Java/build.xml/data, UI layout, project files/dependencies и исторические отчёты не изменялись. Независимое read-only code review: actionable findings0; runtime и сохранность проверены отдельно исполнителем.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Git использовался по явному заданию проверки baseline/изоляции/публикации и bounded AGENTS-разрешению: rev-parse, symbolic-ref, status, worktree list, config --get, ls-remote, ls-tree, count-objects, exact-path diff/check; отдельный shallow clone; exact-path add, обычные commit и push в feature/phantom-world. Полные фактически выполненные команды — `GIT_COMMANDS063.md` во внешнем evidence root. Master/upstream и refs прежнего repository не передвигались; reset/clean/stash/force/history rewrite/массового staging не было.
