# PHANTOM-DISK-CLEANUP-001 — инвентаризация

Дата: 2026-11-12. Единицы: ГБ = 10^9 bytes, GiB = 2^30 bytes. Размеры в таблицах — logical bytes; оценка NTFS allocation и реальные free-space receipts отделены от них. Инвентаризация и cleanup завершены; итоговые действия/дельты — RESULT.

## Защищённый scope

- Основной checkout: `D:\Codex\repos\L2J-phantom-world`, ветка `feature/phantom-world`, исходный HEAD `dfcba154fa1f459be6082a559744d2b4a5c9b47a`. Много исторического untracked dirt; staged/unstaged tracked изменений исходно нет. Сохраняется.
- `D:\Codex\repos\L2J-learning-006`: HEAD `5a38d35194c914c5dda90f8d57763a916201e977`, WIP006; PARTIAL, manual gate REQUIRED. Не сливается, не удаляется. Сохраняются весь `.phantom-local`, основной TEST SQL, оба cold shadow, checkpoints/Adam8/ledger, manifest207/Geo203 и visualization006 `2026/11/12/01a1cffc-6a90-7551-8db7-51d0cf919301`. Создан дополнительный проверенный локальный recovery archive; GitHub не считается backup окружения.
- Существующие `L2J-learning-004`, `L2J-learning-005`, MAIN/PLAY, release057–061, клиент и общая MariaDB не очищаются. У004/005/006 нет `objects/info/alternates`, зависящих от удаляемых клонов.
- Дополнительный read-only audit004/005/006: GitHub origin `https://github.com/kpCat/L2J.git`, по одному independent checkout, tracked status пуст.004 — `wip/phantom-learning-004` на `dff800f4b95232c132b374f8ec97c932eda4a6b1`;005 — `wip/phantom-learning-005` на `dfcba154fa1f459be6082a559744d2b4a5c9b47a`;006 — `wip/phantom-learning-006` на `5a38d35194c914c5dda90f8d57763a916201e977`. Их ignored/runtime данные не очищались.
- Текущий чат `01a1d082-069e-7951-afa3-dd3fe12af80a`, ASU и Rust active contexts, все visualization-каталоги текущего дня — KEEP. История/config/credentials/state Codex не являются cleanup targets.

Сохраняемые связанные source clones дополнительно измерены read-only:004 —3678418613 bytes/114583 files;005 —3391562162 bytes/113673 files;006 —4787276506 bytes/114423 files. У всех errors0/reparse0. Они не входят в объём удалений D-worktrees и не выдаются за очищенное место.

## D:\Codex\worktrees — все 11 каталогов

Полный owner-pass: **54 575 849 837 bytes**, **1 229 141 файл**, reparse points 0, ошибок чтения 0. Суммарная оценка rounded allocation: 58 417 192 960 bytes; размер кластера C:/D: подтверждён как4096. Она не подменяет OS free-space measurements.

| Каталог относительно worktrees | Исходный размер, ГБ | Git / HEAD / изменения | Сохранённая уникальная работа | Решение |
|---|---:|---|---|---|
| phantom-learning-001-source | 28,732171057 | independent clone; wip/phantom-learning; b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8; tracked clean, untracked522696/ignored4064 entries | все `.phantom-local` native/cycle1–19/SQL, logs, harness, ignored outputs в deduplicated worktrees archive | exact deletion одобрена владельцем |
| migration057-20261016 | 14,468138650 | не checkout и не отдельный foreign project; исторические L2J TASK057 migration evidence/metadata | все426 файлов, включая два SQLite по4317118464 bytes, diff/index snapshots и manifests, сохранены полностью в ZIP1,821ГБ | CLEANED |
| phantom-learning-002-source | 3,369087233 | independent clone; wip/phantom-learning-002; b7b5575457c12c1b0438bcbaa646675a67778391; tracked clean, ignored21 entries | TRAIN SQL, incomplete bootstrap, Director state, native/contract/regression logs и outputs в worktrees archive | exact deletion одобрена владельцем |
| phantom-living-career121-source/L2J-phantom-world | 1,774734829 | independent detached clone; 29337ba89d94394b4e94ab1bc92dea54a44b038f; tracked clean, ignored5; origin указывает на local120 | ignored `.phantom-local`, build/dist outputs сохранены | exact deletion одобрена владельцем |
| phantom-restart-recovery120-source/L2J-phantom-world | 1,448218345 | independent detached clone; 3e340e45c4c2aed42b7eca5202440b0a8817901d; tracked clean, ignored2; GitHub origin blob:none | test evidence/build payload сохранён | exact deletion одобрена владельцем |
| phantom-109-controlled-delivery/L2J-phantom-world | 1,233104754 | registered detached; a1b30f7b84a9c06e1141a0cae7a2e48dd922bf24; modified4/untracked2/ignored4 | четыре исходника, новый check/report, ignored evidence/outputs сохранены; пробное восстановление двух исходников прошло | PRESERVED: грязный worktree; не применять --force/restore |
| phantom-life-scenarios116/L2J-phantom-world | 1,211423750 | registered detached; a7cb9a7c2bd4ced33ed693c4d6f3d22f809981a5; tracked clean, untracked31 test logs/ignored2 |31 causal/red/green/final/replay logs и ignored outputs сохранены; SHA31 повторно проверены | CLEANED через native worktree remove |
| phantom-living-world-114/L2J-phantom-world | 1,174945392 | registered detached; d493fffa101c388e9d005ad1a904ffa23f28038f; tracked clean, ignored2 JAR | оба ignored JAR сохранены | CLEANED через native worktree remove |
| phantom-career-gear125-source | 1,164025414 | `.git` — пустая/неполная directory, Git exit128; действующей регистрации нет | полный source snapshot сохраняется как unique overlay к опубликованным Git bytes; оригинальная Git metadata отсутствует | отдельное сохранение/проверка; без активации кода |
| b17f/verification-fixture | 0,000000413 | registered fixture другого repository: D:\Codex\migration-reports\verification-fixture; codex/readme;8544073dbd47ea261275728ac621ffa46c068424; нет remote, clean | уникальная fixture и checkpoint refs остаются в исходном Git root | PRESERVED; не относится к игровым изменениям L2J |
| task062-consolidation | 0 | только пустой `.codex-worktree-name`, не Git repo | ценных данных не обнаружено | KEEP:0 bytes, не требуется отдельная операция |

Все семь валидных игровых HEAD D: входят в свежий GitHub feature ancestry. Дополнительно проверены локальные branches/reflogs independent clones:001—16,002—2,121—21,120—2 SHA; все уже опубликованы. Shared109/114/116 видят один stash d9926911…, который сохраняется в основном `.git`. Ни один ready product commit повторно не сливался. Отсутствие commit относительно stale origin refs старых клонов не выдаётся за отсутствие в GitHub.

## Codex visualizations

Owner-pass обнаружил **74 905 293 007 bytes / 2 687 475 files** и20 независимых Git-клонов. Четыре ACL-закрытых для owner каталога дополнительно прочитаны sandbox-владельцем; они не удаляются. Поэтому общий размер owner-pass — нижняя граница, а не полный размер корня. Reparse points не обнаружены ни в прочитанном owner scope, ни в дополнительных bounded чтениях.

Данные находятся в2026 году, октябре/ноябре. Основной объём — ноябрьские TEST/source outputs L2J. Небольшие собственно визуальные материалы и текущие сессии сохраняются.

| Thread directory (year/month/day/id) | Owner-pass, ГБ | Files | Назначение / Git / решение |
|---|---:|---:|---|
|2026/11/10/01a1c4e6-4a2c-7d73-8cfe-aaebe359c5b5|33,052887202|1298426|TASK130:12 source clones + runtimes/SQL/build/evidence; один source имеет3 modified files. Сохраняется unique overlay; все HEAD/refs/reflogs уже в feature ancestry |
|2026/11/08/01a1bcb8-c716-7f61-b2d8-269b39d0da2e|18,340699003|723038|TASK130:6 source/implementation clones + tests. Один исходный index отражает79676 staged deletions/79119 untracked files; его Git index/status и реальные файлы сохраняются, shell cleanup не активирует эти изменения |
|2026/11/01/01a1964a-b23f-74e0-b2de-e40bf133df8a|6,345303189|175170|старые109 readiness/runtime/evidence; unique overlay перед очисткой |
|2026/11/05/01a1aaac-a34e-7933-9651-3d276674a62b|5,543215233|99301|TASK128 runtime/logs128/run1–23; unique overlay перед очисткой |
|2026/11/06/01a1b21c-bfae-7bf0-a688-c1b682eddc2d|3,105722960|113106|TASK130 source8140167705e6a6e76f370e3aa772b50f909b4d37, runtime и evidence; HEAD опубликован; unique overlay |
|2026/11/11/01a1cb4f-77b7-79b3-a795-bcb5909ba612|2,925803682|113790|LEARNING003 l003;06d42410fbfe1ca9b7f462b0816e0da1784630d6, tracked clean, ignored6; исходный TEST и outputs сохраняются в overlay |
|2026/11/04/01a1a825-c373-7513-a65a-223a80ab4b0b|2,410344151|79759|TASK126; один sibling stage недоступен owner; KEEP полностью |
|2026/11/04/01a1a8a1-3a5a-7890-9094-8aad2a8b18ab|2,334718899|80197|TASK127 source/candidates/logs; KEEP: отдельная уникальность не классифицирована для удаления |
|2026/11/06/01a1b1d8-c7c9-7572-be6d-2a84717165c1|0,402306543|38|старые inventories/status recovery; unique overlay |
|2026/11/05/01a1ad80-fcf2-74b1-8c2d-ba86e822a8e6|0,238055959|2201|TASK129 raw/native evidence; unique overlay |

Все20 Git roots имеют GitHub ancestry; `origin` у многих указывает на соседний local source, что и объясняет цепочки клонов. `objects/info/alternates` отсутствует: удаление не разрывает shared object stores других сохраняемых клонов. Действующих externally linked worktrees внутри выбранных visualization-папок не найдено.

Точные HEAD прочих TASK130-копий: `de3377d385c39627bdbb1fc1844ed4d0346c11f8`, `3232ee6d3179859b47debb047c79fbcb0bf5cfe7`, `eb50ce71ad0a662ef7aceced4056eb2334def9eb`, `1d12b282ec31d4f8d0d7b44e21e8912458ad89ab`, `8e3246c6b708819a2b73e4a7999fd08954b791ad`, `c5b81f89ce02030eff8441395b0411137a110b2f`, `ddd8db8f888d5b7bbaaafb767950ae8748b12c74`, `5d988cb99dda6ea57644214117d43ea75b0ecd60`, `b25cb21f728b56d222c7cf4015f0f94e2ffa4c1e`, `3067468bd4b0670a9c46526506e42f70d91b5cf3`, `c807b3d85652ba8cf44fe31f73885c9354603a5b`, `199dbd5d39c20ea86fd1d8cefaa5e718a8bb7128`, `53f27c94701579cacbb560f37f718373040a8af9`. Повторяющиеся HEAD указаны один раз; полное сопоставление29 Git roots (D+C) сохранено в приватной JSON-инвентаризации.

ACL-остатки без изменения прав:

| Exact subtree | Измерено bytes | Причина KEEP |
|---|---:|---|
|2026/11/04/01a1a825-c373-7513-a65a-223a80ab4b0b/task126/Phantom-Party-Pvp-Life-126-3a031b79-a15d-4aa6-a31f-18347d308f1f|1149310485|доступен sandbox-владельцу, owner Access Denied; нет разрешения менять ACL |
|2026/10/27/01a17dde-eebd-7730-845a-7210897571ae/native096n-a5925167-47c3-4062-a46f-e6a6f8b5c176|1239557350|то же; unique stage/data не доказаны к удалению |
|2026/10/26/01a1780f-b767-7d71-b890-890da2df670a/task094-stage-final|1224142946|то же |
|2026/10/26/01a1780f-b767-7d71-b890-890da2df670a/task094-stage|0 files/0 bytes|пустой каталог; ACL не менялись |

Объединённый итог двух непересекающихся чтений: **78 518 303 788 bytes / 2 824 052 files**. Дополнительные ACL-root чтения дали0 reparse и0 ошибок. Это не означает разрешение изменять их права или удалять их из owner-контекста.

Риск удаления старых визуализаций: ссылки на файлы этих старых диалогов могут перестать открываться. Сохраняемый локальный overlay позволяет восстановить данные, но это не регистрация managed worktree в приложении.

## Итоговые решения и остатки

Все шесть отдельно одобренных D-worktrees/clones, migration057 и orphan-source125 — CLEANED. Сохранение source125: ZIP7822757 bytes,79636 mappings,76886 Git byte streams VERIFIED. Приведённые выше первоначальные решения для этих строк закрыты фактическим удалением. Только dirty109 и fixture b17f — PRESERVED; task062 и внешние оболочки бывших worktrees содержат нулевые markers/empty directories.

На C: CLEANED все восемь крупных thread roots из таблицы, затем весь thread127 и exact stage126-bc5. TASK127 и доступный stage126 прошли дополнительный полный overlay:159755 files,ZIP304827205 bytes,76375 Git streams VERIFIED. Первоначальная KEEP-классификация этих двух доступных ресурсов разрешена этим сохранением. Остальной thread126 с уникальными logs/preimages и ACL sibling3a031 сохранён.

Postflight D-worktrees: **1233105167 bytes /85721 files**, из них dirty109 —1233104754 bytes, fixture413 bytes, остальные markers0 bytes; reparse0/errors0. В общем Git зарегистрированы только main и dirty109; stale metadata отсутствует.

Postflight C owner-pass:348753005 bytes/2693 files, те же4 ACL-отказа. С учётом трёх ранее точно измеренных непересекающихся ACL-subtrees: **3961763786 bytes /139270 files** (≈3,962ГБ). Из них ACL-остатки3613010781 bytes; исходные небольшие результаты, thread006, thread005 и текущие diagnostic artifacts —348753005 bytes. Nov04 thread126 без ACL-соседа:101255264 bytes; текущая сессия на момент замера94222756 bytes; visualization006713906 bytes;00522839950 bytes. Текущий каталог не удалялся, поэтому продолжает содержать необходимые этой уборке scripts/receipts.

На исходных проверенных D/C roots удалено **127940546227 logical bytes**:53342744670 bytes на D: и74597801557 bytes на C:. Удалено18 exact targets (8 на D:,10 на C:);9 полных C thread roots и один stage subtree. Шесть приватных ZIP занимают5466235274 bytes; весь private preservation с restore smoke и helpers —5735135375 bytes. Фактические free-space BEFORE/AFTER — RESULT; logical payload не называется физически освобождённым объёмом.
