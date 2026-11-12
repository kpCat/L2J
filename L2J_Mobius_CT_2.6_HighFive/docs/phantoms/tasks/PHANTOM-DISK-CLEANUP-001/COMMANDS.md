# PHANTOM-DISK-CLEANUP-001 — команды и проверяемые результаты

Дата: 2026-11-12. Operational postflight завершён. Encoding/diff checks и публикация выполняются последним шагом; их фактические результаты и outgoing SHA фиксируются в отдельном PUBLICATION.json рядом с private preservation.

Все Git-команды разрешены прямым заданием на инвентаризацию, сохранение, консолидацию и cleanup; точный набор шести каталогов D: дополнительно одобрен владельцем в этом чате. Команды выполнялись из фактического checkout, без смены baseline на старые ветки. Shell: PowerShell; локальные диагностические скрипты: Python 3.12 standard library. Полные приватные JSON и скрипты находятся в каталоге текущей visualization-сессии `01a1d082-069e-7951-afa3-dd3fe12af80a`. Приватное сохранение — `D:\Codex\_PRESERVE\PHANTOM-DISK-CLEANUP-001`.

## Read-only Git

В основном checkout:

```text
git rev-parse --show-toplevel --git-common-dir HEAD
git symbolic-ref --short HEAD
git status --short
git remote -v
git fetch origin
git ls-remote origin refs/heads/feature/phantom-world refs/heads/wip/phantom-learning-006
git worktree list --porcelain
```

Результат fetch/ls-remote: exit 0; feature `dfcba154fa1f459be6082a559744d2b4a5c9b47a`, WIP006 `5a38d35194c914c5dda90f8d57763a916201e977`. GitHub connector независимо подтвердил исходный commit и ветку feature. Первичная составная команда завершилась exit 1 только из-за запрещённого sandbox-доступа к CIM; Git-команды перед ней успешно вернули HEAD/ветку/status/worktrees.

Для каждого Git-корня использованы следующие точные формы (`<repo>` и `<HEAD>` перечислены в INVENTORY; локальная JSON-инвентаризация содержит все значения):

```text
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> rev-parse --show-toplevel --absolute-git-dir --git-common-dir HEAD
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> symbolic-ref --short HEAD
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> remote -v
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> for-each-ref --format=%(refname) %(objectname)
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> worktree list --porcelain
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> status --porcelain=v1 -z --untracked-files=all --ignored=matching
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> rev-list --count --all --not --remotes=origin
git -c safe.directory=<repo> -c core.fsmonitor=false -C <repo> rev-list HEAD --not --remotes=origin
git -C <main> merge-base --is-ancestor <HEAD> dfcba154fa1f459be6082a559744d2b4a5c9b47a
git -c safe.directory=<repo> -C <repo> reflog --all --format=%H
git -C <main> rev-list dfcba154fa1f459be6082a559744d2b4a5c9b47a
git -C <main> ls-tree -r -z <published-commit>
```

`GIT_OPTIONAL_LOCKS=0` установлен только для read-only Python Git-инвентаризации. `safe.directory` — разовый точный параметр команды, глобальная конфигурация не менялась. Exit 128 dubious ownership при первых чтениях устранён только этим параметром. Exit 1 symbolic-ref для detached HEAD — ожидаемый. У gear125 `.git` не содержит действующего репозитория: exit 128, исходник оставлен. Счётчики относительно старых origin refs не считаются доказательством отсутствия commit в GitHub: решающая проверка — membership в свежем published ancestry. MAIN stash `d9926911fa4dc05dd94f1e615c2ba81d41d143a4` не входит в feature, но остаётся в нетронутом общем Git root; не переносился и не удалялся.

Для чтения четырёх правок109 применены `git -C <109> diff --stat -- <четыре exact пути>` и `git -C <109> diff --unified=2 -- <L2jPhantomPartyBackend.java> <PhantomPartyCoordinator.java>`. Получены 74 добавления/4 удаления; ключевые native DPS и generic recovery уже присутствуют в актуальном main. Старый draft сохранён без активации.

## Файлы, процессы, сохранение

`Get-PSDrive C,D` — исходные и конечные free bytes. Read-only `Get-CimInstance Win32_Process` и локальная адаптация `migration057/handles_inventory_archive057.py` — процессы/открытые disk handles; чужие handles не закрывались. 349 процессов в исходной session, 16 недоступных системных/защищённых; рабочие Java/MariaDB006 отсутствовали. Единственный handle в кандидатах принадлежал нашему inventory Python. Настройки, SQLite состояния Codex и история не читались для модификации и не менялись.

`disk_inventory.py` использует `os.scandir`, `stat(..., follow_symlinks=False)` и отсекает Windows reparse points. Учтены logical bytes и оценка allocation по подтверждённому cluster size 4096; это не выдаётся за точную NTFS allocation. Реальное свободное место измерено средствами ОС. Первый sandbox-pass имел 25 Access Denied на D:, повтор штатным владельцем дал 0. На C: четыре каталога доступны только sandbox-владельцу; дополнительные bounded чтения выполнены без изменения ACL. Они исключены из удаления.

`preserve_payload.py <exact-plan.json>` — только чтение источников, дедупликация по SHA-256, ZIP object store, проверка каждого записанного объекта обратным чтением. Нет полной резервной копии Git-репозитория. Для visualization overlay опубликованные bytes заменены ссылками `commit/path/blob`; исходная SHA-256 и преобразование LF/CRLF записаны в manifest. Git metadata без object packs сохраняется отдельно. Все приватные SQL/config/credentials остаются только локально.

Подтверждённые receipt:

| Архив | Files | Bytes ZIP | SHA-256 | Результат |
|---|---:|---:|---|---|
| learning006-environment.zip | 27751 | 893984105 | 9178e00e390231291d7bf5d86f43412fe482a0fb477b744e9141d226e2a9ee8d | все объекты SHA VERIFIED |
| worktrees-unique-payload.zip | 591105 | 524514653 | 99669e08d1752e5e928d735492676428fbcd0e9a43efd8fdd34caf718608894b | все объекты SHA VERIFIED |
| migration057-evidence.zip | 426 | 1821364296 | 374e89b10e5b6c5d4615f6bc389e90822924d091607f7f07fdb577185164dbd9 | все объекты SHA VERIFIED |
| gear125-orphan-source.zip |79636|7822757|c1d8ea3d5f8da5122c60ee7cdb666888c9e59ac665e4a1d1738dd508bf88b77b|76886 published streams и97 stored mappings VERIFIED|
| visualizations-unique-overlay.zip |2523876|1913722258|ffd0034ec6ace1f78241c8c62d43c61cececd9fcebfa70e634516f1121ccceb3|77608 published streams и224336 stored mappings VERIFIED|
|nov04-unique-overlay.zip|159755|304827205|6474fa158becbbcbd80040918fc3137df5e61b26305a087ca831af16672657d8|76375 published streams и1689 stored mappings VERIFIED|

`restore_payload.py learning006-environment.zip <new-root> --sample006`: exit 0, 4 файла/231821842 bytes, SHA совпадают. Worktrees `--sample-worktrees`: exit 0, 4 файла/12766010 bytes, включая ibdata1, два исходника109 и causal log116. Это проверка файлового восстановления; новый SQL-сервер не запускался и новый operational DB restore не заявляется. Существующее cold-readback006 с 9 receipts/Adam8/536 samples сохранено вместе со stopped datadir.

Visualization restore smoke: exit0,3 файла/12831522 bytes, SHA совпадают. `verify_overlay.py` использует `git -C <main> cat-file --batch`: восстановлены все77608 уникальных опубликованных byte streams, каждый сопоставлен с исходной SHA-256 из overlay, LF/CRLF учтены. `verify_preserved006.py`:27751/27751 исходных файлов006 без расхождений, protected207/207, HEAD5a38d351… неизменён. Все recovery helpers дополнительно скопированы рядом с private archives. Внешние несжатые sidecar mappings сокращены; полный manifest сохранён внутри неизменённого ZIP, его SHA не менялся.

## Мутации и отказы

1. Созданы только четыре отчёта в task-каталоге; helper/scripts/JSON/archives — вне checkout.
2. Удалён точный `D:\Codex\worktrees\migration057-20261016` после archive SHA guard, Resolve-Path и reparse guard. Первый `Remove-Item -LiteralPath <exact> -Recurse` остановился на read-only/hidden файле (exit 1), повтор с `-Force` завершился exit 0. ACL не изменялись; shell `-Force` не является Git `--force`.
3. Автоматическая проверка отклонила начальное удаление шести worktrees: потребовала отдельного одобрения exact набора и доказательства восстановления. После дополнительного restore smoke владелец явно разрешил эти шесть каталогов; обхода отказа не было.
4. `Cleanup-Worktrees.ps1` проверяет archive SHA, exact пути, reparse, tracked dirt и published ancestry. 31 untracked log116 перед удалением повторно сопоставлен с SHA сохранения. 114 удалён штатно. Первая попытка116 отказала: global `core.untrackedCache=true`/`core.fsmonitor=true` показывали уже отсутствующий каталог логов. Свежий status с обоими cache=false пуст; повтор native removal с временными параметрами прошёл без `--force` и без изменения config.

Точные native удаления:

```text
git -C D:\Codex\repos\L2J-phantom-world worktree remove D:\Codex\worktrees\phantom-living-world-114\L2J-phantom-world
git -c core.fsmonitor=false -c core.untrackedCache=false -C D:\Codex\repos\L2J-phantom-world worktree remove D:\Codex\worktrees\phantom-life-scenarios116\L2J-phantom-world
git -C D:\Codex\repos\L2J-phantom-world worktree prune --dry-run --verbose
```

Четыре independent clones удаляются `Remove-Item -LiteralPath <exact-approved-path> -Recurse -Force` после guards. Эти каталоги не являются linked worktrees; регистрации `.git` чужого репозитория не редактируются. Ни `git clean`, ни `reset`, ни `stash`, ни `restore`, ни force push/checkout, ни изменение истории не применялись. Production build/tests не запускались: production-код не меняется.

`Cleanup-Remaining.ps1` содержит literal allowlist восьми thread roots из INVENTORY и orphan-source125, запрещает текущую дату/006, проверяет recovery receipts, SHA архивов и ancestor/descendant reparse. Exact targets удаляются native `Remove-Item -LiteralPath <exact> -Recurse -Force`; `.codex`, year/month/day roots, sessions и state SQLite не являются target. Восемь старых roots не registered worktrees: их standalone Git identities/HEAD/refs/reflogs проверены заранее, metadata сохранена. Mutation прошла automatic approval review после полного overlay verification, отдельного нового подтверждения пользователя для C: не потребовалось.

После основного удаления таким же overlay-контрактом проверены два доступных Nov04 ресурса: весь thread127 `2026/11/04/01a1a8a1-3a5a-7890-9094-8aad2a8b18ab` и точный stage126 `2026/11/04/01a1a825-c373-7513-a65a-223a80ab4b0b/task126/Phantom-Party-Pvp-Life-126-bc5c30db-030e-450f-81ed-3daeec24dfef`. Они добавлены в literal allowlist только после ZIP/SHA/reconstruction verification. Закрытый sibling126-3a031, остальные unique logs126 и все текущие контексты не удаляются.

`compact_receipts.py gear125-orphan-source worktrees-unique-payload visualizations-unique-overlay` сокращает только избыточные несжатые sidecar JSON; полные mappings остаются `manifest.json` внутри неизменённых ZIP. Освобождены333996786 bytes для двух первых sidecars и2289894854 bytes для visualization sidecar; точные before/after receipts сохранены локально. Не удалялись SQL backups или исходные manifests006.

Nov04 sidecar уменьшен163646930→1010 bytes; полный manifest остаётся в ZIP. На rerun PowerShell5 обернул исходный массив operation receipts в `{value,Count}`. `normalize_receipts.py` привёл только JSON-журнал к плоскому виду, сохранив raw-original;17 записей удаления плюс migration отдельно, все paths проверены отсутствующими. Источники/архивы этим не менялись.

## Final postflight / delivery scope

`disk_inventory.py` с task-specific `PHANTOM_INVENTORY_PHASE=after`: D-worktrees1233105167 bytes/85721 files,0errors/0reparse; C readable348753005 bytes/2693files,4known ACL errors/0reparse. Плюс неизменённые ACL-subtrees3613010781 bytes дают C residual3961763786 bytes/139270files. Три сохраняемых related clones004/005/006 измерены отдельно без ошибок/reparse. Исходные inventories не перезаписывались.

`verify_preserved006.py` после всех удалений: exit0,27751/27751 file SHA и207/207 protected PASS, HEAD5a38d351… неизменён. `postflight.py`: exit0,18 exact targets отсутствуют,6 private archive SHA VERIFIED,6dirty109 files byte-identical, main tracked/staged dirt0; main HEADdfcba154…, origin/master16d61833…, stashd9926911… сохранены. SQL/Java launch0, Git merges0, product commits0.

Pre-commit проверки выполняются двумя отдельными `rg` вызовами по exact четырём Markdown paths: полный набор mojibake-маркеров; шесть regex escaped Cyrillic/XML. `rg` exit1 означает0совпадений и преобразуется проверкой в PASS/exit0. Затем `git diff --cached --check` и exact cached-name allowlist4. Product smoke/build не подменяются этими checks; production-код не меняется.

Фактический encoding check: mojibake — `rg -n -F -f <current-thread>/mojibake-patterns.txt -- <exact4paths>`,27literal patterns,0matches/PASS/exit0; escaped Cyrillic/XML — отдельный `rg -n` со всеми6regex,0matches/PASS/exit0. Первоначальный inline-marker вызов вернулexit1 без stdout и не считался PASS; проверка повторена по явному UTF-8 pattern-файлу. Причина поведения inline-shell не устанавливалась, источник отчётов на её основании не изменялся.

Финальные Git-команды (в указанном порядке, только4exact файла):

```text
git fetch origin
git ls-remote origin refs/heads/feature/phantom-world refs/heads/wip/phantom-learning-006
git -c core.fsmonitor=false -c core.untrackedCache=false add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-DISK-CLEANUP-001/PLAN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-DISK-CLEANUP-001/INVENTORY.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-DISK-CLEANUP-001/COMMANDS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-DISK-CLEANUP-001/RESULT.md
git diff --cached --name-only
git diff --cached --check
git commit -m "docs(phantom): record verified disk cleanup and recovery"
git push origin HEAD:refs/heads/feature/phantom-world
git rev-parse HEAD
git ls-remote origin refs/heads/feature/phantom-world refs/heads/wip/phantom-learning-006 refs/heads/master
```

Независимый GitHub connector `fetch_commit` должен подтвердить exact outgoing SHA и exact4path set; это финальная проверка содержимого, независимая от Git transport. Фактические exit codes/readback записываются в `D:\Codex\_PRESERVE\PHANTOM-DISK-CLEANUP-001\PUBLICATION.json` после отправки. Source base feature:dfcba154fa1f459be6082a559744d2b4a5c9b47a; исходный и конечный006SHA5a38d35194c914c5dda90f8d57763a916201e977. Ни один новый игровой commit не активируется.
