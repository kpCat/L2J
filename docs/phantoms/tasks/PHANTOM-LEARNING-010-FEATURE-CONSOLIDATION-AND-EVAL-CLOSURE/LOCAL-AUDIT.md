# LOCAL-AUDIT010

Аудиторский gate: **SAFE_TO_MERGE в существующем чистом L2J-learning-004**. Основной checkout остаётся грязным и не переключается. Дата проверки: 14 ноября 2026.

## Проверенные checkout

| Каталог относительно D:/Codex | HEAD | Tracked dirt | Untracked | Локальная уникальная история |
|---|---|---:|---:|---|
| repos/L2J-phantom-world | 7e997636dbac9d5e1754e00c793cd6fac858b0c6 | 0 | 427482 | stash и два snapshot tree |
| repos/L2J-learning-004 | dff800f4b95232c132b374f8ec97c932eda4a6b1 | 0 | 0 | нет |
| repos/L2J-learning-005 | dfcba154fa1f459be6082a559744d2b4a5c9b47a | 0 | 26653 | нет |
| repos/L2J-learning-006 | 01e04537eec0a4f6b19236c4e547df7b2a22efb5 | 0 | 36455 | нет |
| worktrees/phantom-109-controlled-delivery/L2J-phantom-world | a1b30f7b84a9c06e1141a0cae7a2e48dd922bf24 | 4 | 2 | shared stash, старый HEAD опубликован |

Status повторён с правами владельца и временными `core.fsmonitor=false`, `core.untrackedCache=false`: те же количества, exit 0. Sandbox первоначально видел неполный private scope; его нулевые счётчики005 не использованы как доказательство чистоты. Global Git config и ACL не менялись. Все refs, upstream, branches, tags, reflogs, log и списки путей сохранены в приватном inventory. Tags отсутствуют. Все локальные обычные ветки и detached HEAD опубликованы; уникальны только два stash-коммита. Рабочие черновики109 и stash сохраняются отдельно и не считаются готовыми продуктовыми исправлениями.

## Свежий remote

В четырёх независимых L2J clone выполнен fetch всех `refs/heads/*:refs/remotes/origin/*` без prune/force. Обычный fetch оказался ограничен старым single-branch refspec; это учтено, config не переписан. GitHub connector независимо подтвердил исходный feature HEAD.

- feature: `7e997636dbac9d5e1754e00c793cd6fac858b0c6`;
- 009: `01e04537eec0a4f6b19236c4e547df7b2a22efb5`;
- master: `16d61833b3983a3976583d0e4813e0de9457a52f`;
- merge-base: `dfcba154fa1f459be6082a559744d2b4a5c9b47a`;
- divergence: feature 1 / incoming 13 commits. Incoming изменяет122пути относительно общего предка; собственные4cleanup-документа feature не пересекаются с incoming.

Все15опубликованных heads проверены по ancestry: 001–009,129/130, experiment и master входят в009; отдельный feature cleanup commit должен стать первым родителем merge. Никакой другой готовой уникальной ветки для дополнительного merge не обнаружено.

## Сохранение вне checkout

Приватный каталог этой сессии: `goal010` во внешнем visualization root. Не публиковать ZIP, JSONL или pack: внутри находятся private runtime/config/SQL.

Все490596dirty/untracked файлов сохранены побайтово, включая private005/006 и старые runtime main. Logical payload8569140367bytes;88379уникальных потоков/4519119809bytes. Каждый архивный объект прочитан обратно и проверен SHA-256.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| preserved-local-files.zip | 2541426396 | 7f3759c14318eba0daaedb599b1525ca2cf16fe45142a4eccece80edf830d78f |
| preserved-local-tail.zip | 374599 | 4580b3bd1d72702c3483531d5fcf1c69c1d781488add7acc25bbc3c68fe67b63 |
| local-stash.bundle | см. локальный файл | eb4f4e5a0c975a3259e22a4b9464864da2c7f8b5d6b1ab3b95dce9e362f01072 |
| local-checkpoint-trees.pack | 452542356 | 4ee4ca91493c0555d77f50684c11d756c3bca92e821874e1cb382306703a7bd8 |

JSONL manifests SHA: основной `0f9a15985275194d70f8e45e44264a2ee1ffa9437c4242ad5b80d655fbac6b8f`, tail `dd42482277fac79897d43f59b16919ed0fdc4e4f6dd5a1c1b15e139d694ea73c`. В них точный original root/path, размер, SHA и mtime. Первая запись ZIP остановилась на дате файла до1980; она оставлена неизменённой, хвост записан отдельно с нормализацией только ZIP metadata. Bytes источников не менялись.

Stash `d9926911fa4dc05dd94f1e615c2ba81d41d143a4` и index-parent `cb3d98c2979f63ce019e4fd649c28e830cc878db` сохранены в bundle; `git bundle verify` exit0, published prerequisite `236b965a116bbc79cccbef78b04c628225a629cb`. Это23старых source draft, ранее описанных Goal005; восстановления поверх canonical code не выполнялось. Snapshot trees `0bb21d27e2fc1af438b25cbe4e8b86d259f0cf71` и `e792d8a12eb3f84ddb81da0a97b38359d5f2ade2` дополнительно сохранены object pack с исключением опубликованных объектов, `index-pack --strict` exit0.

## Остальной известный scope

Ограниченный осмотр D:/Codex/repos и D:/Codex/worktrees не обнаружил другого L2J Git root. Development и RustOxideBots имеют другие remotes, fixture b17f — другой repository без remote; они не изменяются. Исторические001/002/120/121 и7известныхC-root клонов после DISK-CLEANUP-001 отсутствуют; восстановление удалённых каталогов не обещается. Старый C:/Users/ZBook/L2J_Mobius отсутствует. Оставшиеся оболочки worktree не содержат Git checkout. Глубокого поиска личных данных не было.

Read-only process census: Java/Javaw отсутствуют; существующий mysqld PID8692 от28октября — чужой, не трогать. Никакие GameServer/SQL не запускались и не останавливались в A. MAIN/PLAY, Geo и protected releases не менялись.

## Ограничения решения

Дополнение после публикацииA: отдельно выполнен read-only ignored inventory всех5известных roots —2716584пути. Найденные288682source/docs (Java/C#/PHP/Markdown/PS/Python/JS/TS/C++/project extensions) сохранены во внешний `preserved-ignored-source.zip`:4956unique streams,86347309bytes, ZIP23664455bytes. Каждый объект прочитан обратно с SHA. ArchiveSHA `970392e33e3f0e50c967d8c0ca4083f07af953880fe03815d46cad864e9b43a4`; manifestSHA `29a567b5aaa49258c4afdabd01ab33fbd6f44f7d460434ab7f093a0c2c2ccb47`; inventorySHA `97e8f24af4e238e9d8f7d8be9a6782110273da97f6684c9097c0fac10439b461`. Это дополнение, не переписывание initial preservation. Большинство — повторные historical source snapshots. Остальные ignored binaries/data/runtime инвентаризированы и оставлены на месте; полная резервная копия каждого ignored byte не заявляется. Основной checkout и109 не изменены, прежние заменяемые JAR004 отдельно сохранены, старая009история/coldpointer/Geo/protected подтверждены exact hashes.

Используется существующий независимый004, не новый clone/worktree/WIP. До build его два ignored JAR сохраняются отдельно; build outputs будут направлены во внешний каталог. Полный merge заранее не выдаётся за проверенный: после merge необходимы targeted regressions, JDK25/Ant, default-OFF, публикация и независимый readback. ЭтапB до этого запрещён.
