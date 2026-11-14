# MERGE010 — опубликованная консолидация

**CONSOLIDATION: VERIFIED.** Этап A опубликован и независимо прочитан с GitHub до начала исправлений B.

Чистое место: существующий независимый `L2J-learning-004`. Основной `L2J-phantom-world` оставлен на исходном commit с сохранёнными untracked файлами; он также входит в protected207. Новый clone/worktree/WIP не создавался.

| Предмет | Проверенное значение |
|---|---|
| Feature до merge / первый parent | `7e997636dbac9d5e1754e00c793cd6fac858b0c6` |
| Опубликованная009 / второй parent | `01e04537eec0a4f6b19236c4e547df7b2a22efb5` |
| Общий предок | `dfcba154fa1f459be6082a559744d2b4a5c9b47a` |
| Merge commit | `8752d88a8a963b5e283e0ecd0f69c6c319ac5e62` |
| Merge tree | `217bb3861177a9f8ded450469b3c5902dcd1c1f2` |
| Incoming scope | 122 пути из опубликованной цепочки, без собственной правки исторических blobs |
| Собственная feature история | Четыре документа DISK-CLEANUP-001 сохранены через первый parent |
| master, default branch,009 | Не изменены |

Свежие refs перепроверены fetch всех published heads в четырёх независимых clone, без prune/force. Все15 remote heads рассмотрены по ancestry. Нет дополнительной готовой уникальной ветки для merge; старые локальные drafts сохранены без применения.

Исполнен `merge --no-ff --no-commit origin/wip/phantom-learning-009`, затем проверки и обычный merge commit/push в feature. Ни squash, ни cherry-pick цепочки, ни rebase/reset/clean/force не применялись. Оба исходных HEAD являются предками merge.

Первый tracking switch отказал из-за single-branch fetch refspec после обновления index. Это распознано как собственный промежуточный эффект: index равен опубликованному feature tree, working diff/untracked=0. Index/config сохранены с SHA; добавлен только local fetch mapping feature и завершён обычный switch без force. Пользовательский dirt не использовался и не перезаписывался.

JDK25.0.4.1 / Ant1.10.17: `phantom-autonomous-training-test`, `phantom-learning008-test`, `phantom-learning007-test`, `phantom-learning001-test`, `phantom-combat-action-ownership-test`, `phantom-humanized-v3-content-validate`, `jar`: exit0,197с. Десять существующих DB-free routes004/005/006: exit0. Полный `ant verify` не запускался. Build направлен во внешний каталог, прежние два JAR004 сохранены с SHA до штатной замены.

LearningMode=OFF, model=0, evidence=false, exploration/TEST opt-in OFF в поставляемых настройках. Общий EnablePhantomSystem=true — прежняя настройка сервера, она не означает включённое обучение. Автопродвижения DRAFT14 нет.

CRLF-aware diff check: exit0; обычный initial diff check считал CRLF строк build.xml trailing whitespace. Исторический файл ради этого не перекодирован.

- mojibake-маркеры в изменённых файлах проверены: все27, совпадений0.
- escaped Cyrillic в изменённых файлах проверены: все6 regex, совпадений0.

Независимый GitHub connector подтвердил feature HEAD8752, оба parent, tree,122paths и полные тела четырёх критичных исходников с Git blob SHA-1 и SHA-256. Приватный receipt: `stage-A-github-readback.json`. Последующие scoped commits B являются потомками этого merge и не заменяют его историю.

Сохранение490596файлов, stash и snapshot trees подробно описано в LOCAL-AUDIT. Приватные архивы/SQL/модели/Geo в Git не добавлялись. Успех консолидации не изменяет исторический PARTIAL009 и её manual gate REQUIRED.
