# PHANTOM-DISK-CLEANUP-001 — результат

Дата: 2026-11-12. **PARTIAL_CLEANED_WITH_PRESERVED: безопасная очистка выполнена, известные ACL/unique остатки сохранены. FULL не заявлен.** Итоговый чистый прирост свободного места: **106606182400 bytes =106,61ГБ**. Это реальная очистка, не только инвентаризация.

## VERIFIED

Свежие origin refs и независимый GitHub readback подтвердили исходную feature `dfcba154fa1f459be6082a559744d2b4a5c9b47a`. TASK130, LEARNING001–005 и все проверенные старые source HEAD уже входят в её ancestry; повторных merge нет. Новой готовой игровой ветки для консолидации не обнаружено. WIP006 `5a38d35194c914c5dda90f8d57763a916201e977` остаётся отдельной PARTIAL-работой с manual gate REQUIRED; в feature она не включалась.

Ссылки: [исходная feature](https://github.com/kpCat/L2J/commit/dfcba154fa1f459be6082a559744d2b4a5c9b47a), [LEARNING001](https://github.com/kpCat/L2J/commit/b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8), [LEARNING002](https://github.com/kpCat/L2J/commit/b7b5575457c12c1b0438bcbaa646675a67778391), [LEARNING003](https://github.com/kpCat/L2J/commit/06d42410fbfe1ca9b7f462b0816e0da1784630d6), [сохранённая006](https://github.com/kpCat/L2J/commit/5a38d35194c914c5dda90f8d57763a916201e977). Полный перечень D-worktrees и20 C-клонов с SHA приведён в [INVENTORY](INVENTORY.md).

Read-first выполнен по AGENTS/README/current status/workflow и двум локальным preservation/consolidation аналогам. UI, production Java/C#, schema, runtime, проекты/зависимости и Git history не изменяются. MAIN/PLAY, защищённые releases, общая MariaDB и чужие активные чаты не очищались. GitHub default/master не менялись. Production build/tests не нужны для operational/docs-only изменений и не запускались.

## CLEANED

На D: удалён historical migration057 с полным сохранением всех426 доказательств. Это L2J migration audit, содержащий SQLite inventories, diff и Git index snapshots; он не был игровым checkout или веткой другого проекта. Его содержимое в L2J не сливалось.

Удалены два registered worktree (114/116) через штатный `git worktree remove` без `--force`, и четыре отдельно одобренных independent clone001/002/120/121. У116 перед native removal сохранены и повторно проверены SHA31 untracked test logs. Stale metadata после native removal не обнаружена; dry-run prune не предложил удаления чужих записей. Грязный109 не удалялся. Точные команды, отказы и исправление cache-причины116 — в [COMMANDS](COMMANDS.md).

На C: удалены9 полных старых thread roots и один exact stage126, включая20 самостоятельных source clones. На D: orphan-source125 также удалён после полного byte reconstruction verification. Всего18 exact targets отсутствуют по final postflight; полный список с сохранением и измерениями — ниже.

## PRESERVED

**006 не удалена и не модифицирована.** Её clone, `.phantom-local`, основной TEST SQL, обе cold shadow SQL, checkpoints, ledger, Adam8, manifests и visualization006 остаются на месте. Дополнительно сохранён архив `D:\Codex\_PRESERVE\PHANTOM-DISK-CLEANUP-001\learning006-environment.zip`:27751 файл,2,819ГБ исходных bytes,0,894ГБ ZIP. Каждый объект проверен SHA-256. Четыре ключевых файла, включая231,686МБ `tactical-lineage.sql`, реально восстановлены в отдельный новый каталог; SHA совпали. Никакой GitHub commit не выдаётся за полную копию TEST окружения.

Файловое восстановление проверено; новый запуск/импорт SQL не выполнялся. Исторический cold-readback006 с9 receipts,536 native samples,769 optimizer updates и неизменным Adam8 сохранён. После восстановления нельзя считать старые PID/owner/lock файлы активными ownership receipts: новое окружение требует обычных guards существующего TrainingOwner, а текущая сохранённая006 может продолжаться по своему task. Порты и SQL других окружений не переназначались.

Для старых D-worktrees сохранено591105 локальных/untracked/ignored файлов:29,184ГБ logical payload превратились в ZIP524514653 bytes благодаря дедупликации. Ни committed source tree, ни `.git/objects` повторно не архивировались. Все записанные объекты проверены SHA; SQL ibdata1, два исходника109 и log116 пробно восстановлены с совпадением SHA.

Для `phantom-career-gear125-source` создан отдельный overlay79636 файлов: только5311520 bytes уникального содержимого, ZIP7822757 bytes. Проверены SHA-256 всех76886 опубликованных реконструируемых потоков и97 stored mappings. Пустой/неполный `.git` не выдумывается заново; исходное состояние зафиксировано.

Старые четыре modified-файла109 сохранены и в исходном dirty worktree, и в archive. Ключевые исправления native DPS/projected duration/generic recovery уже присутствуют в актуальном feature; старый draft автоматически не накладывался. Исходный общий stash и Codex checkpoint refs остаются в нетронутом основном Git root. Fixture b17f — другой repository без remote; сохранена, в L2J не вливалась.

Приватные архивы содержат SQL/config и не публикуются. Вне checkout также сохранены точные inventories, ancestry, hash manifests, restore helpers и operation receipts. Для восстановления006 используется published source SHA5a38d351… и приватный runtime006 payload; для старых visualization overlays published blob/commit/path references позволяют восстановить исходные bytes, включая LF/CRLF. Оригиналы006 остаются основным вариантом продолжения.

## BLOCKED / остатки

Четыре ACL-различающихся каталога старых TASK094/096/126 доступны sandbox-владельцу, но owner-контекст получает Access Denied. Три содержат3613010781 bytes, четвёртый пуст. Они измерены read-only и оставлены; права/владелец/UAC не менялись. Небольшие исторические visualization evidence, unique logs126 и активные контексты сохранены. TASK127 и отдельный доступный stage126-bc5 после дополнительного overlay verification удалены. Точный перечень и размеры — INVENTORY.

Это остатки конкретных каталогов, а не основание выдавать инвентаризацию за FULL. Отдельного завершения gameplay006, fit/A/B или manual acceptance эта уборка не доказывает. Удалённые файлы старых диалогов могут перестать открываться по старым UI-ссылкам; локальный archive/overlay сохраняет содержимое, но не автоматическую app-регистрацию.

## Диски и выполненные действия

Финальный snapshot: **12.11.2026 19:19:11 Europe/Chisinau /17:19:11 UTC**. ГБ в этом отчёте =10^9 bytes.

|Диск|Свободно до, bytes|Свободно после, bytes|Чистый прирост, ГБ|
|---|---:|---:|---:|
|C:|217501401088|293960372224|**76,458971136**|
|D:|405583171584|435730382848|**30,147211264**|
|Всего|||**106,606182400**|

На исследованных roots удалено127940546227 logical bytes. Private preservation занимает5735135375 bytes, включая шесть ZIP5466235274 bytes, manifests, recovery helpers и restore smoke. Эти расходы уже входят в итоговое свободное место D:. Параллельно работал другой Codex goal/Rust и ОС, free bytes изменялись независимо от удаления; суммарный размер файлов и дельты отдельных операций не выдаются за итоговый чистый прирост. Настройки pagefile не менялись.

В таблице `D:` означает `D:\Codex\worktrees`, `C:` — `C:\Users\ZBook\.codex\visualizations\2026`. Архивы: **W** worktrees-unique-payload, **M** migration057-evidence, **G** gear125-orphan-source, **V** visualizations-unique-overlay, **N** nov04-unique-overlay. Все ZIP и восстановление VERIFIED. `Δfree` — наблюдаемая дельта ОС между началом/концом конкретного удаления; при параллельной записи это не строгая атрибуция исключительно этому каталогу.

|Каталог|Исходно, ГБ|Статус Git|Сохранено|Действие|Δfree, ГБ|Остаточный риск|
|---|---:|---|---|---|---:|---|
|D:phantom-learning-001-source|28,732|HEAD опубликован; tracked clean, local TEST|W: все runtime/SQL/logs|CLEANED clone|29,267|восстановление local TEST — из ZIP, не одного commit|
|D:migration057-20261016|14,468|не checkout; L2J audit|M: все426 файлов|CLEANED metadata/evidence|14,359*|нужные исторические bytes в M|
|D:phantom-learning-002-source|3,369|HEAD опубликован; tracked clean|W: TRAIN/SQL/bootstrap/logs|CLEANED clone|3,541|restore из W|
|D:phantom-living-career121-source/L2J-phantom-world|1,775|detached, опубликован|W: ignored payload|CLEANED clone|1,855|нет активного зависимого object store|
|D:phantom-restart-recovery120-source/L2J-phantom-world|1,448|detached, опубликован|W: evidence/build|CLEANED clone|1,601|нет активного зависимого object store|
|D:phantom-life-scenarios116/L2J-phantom-world|1,211|registered; опубликован|W:31 logs и ignored|CLEANED native worktree|1,377|metadata удалена штатно|
|D:phantom-living-world-114/L2J-phantom-world|1,175|registered; опубликован|W: ignored JAR|CLEANED native worktree|1,324|metadata удалена штатно|
|D:phantom-career-gear125-source|1,164|orphan, пустой/неполный .git|G: все79636 файлов|CLEANED orphan source|1,313|не выдумывается отсутствующая Git history|
|C:11/10/01a1c4e6-4a2c-7d73-8cfe-aaebe359c5b5|33,053|12 clones, всё в ancestry;3 dirty source|V: source overlay, SQL/logs/index|CLEANED thread root|35,169|старые UI file links могут исчезнуть|
|C:11/08/01a1bcb8-c716-7f61-b2d8-269b39d0da2e|18,341|6 clones, всё в ancestry; один dirty index|V: реальные files и Git index|CLEANED thread root|19,495|staged draft не активирован|
|C:11/01/01a1964a-b23f-74e0-b2de-e40bf133df8a|6,345|runtime/evidence109, не Git|V: unique bytes|CLEANED thread root|6,499|старые file links|
|C:11/05/01a1aaac-a34e-7933-9651-3d276674a62b|5,543|runtime/logs128, не Git|V: unique bytes|CLEANED thread root|5,650|старые file links|
|C:11/06/01a1b21c-bfae-7bf0-a688-c1b682eddc2d|3,106|опубликованный source130|V: runtime/evidence/metadata|CLEANED thread root|3,263|restore через V+published Git|
|C:11/11/01a1cb4f-77b7-79b3-a795-bcb5909ba612|2,926|опубликованный003, clean|V: старый SQL/evidence;006 отдельно сохранена|CLEANED thread root|3,138|003bytes не потеряны,006 не затронута|
|C:11/06/01a1b1d8-c7c9-7572-be6d-2a84717165c1|0,402|inventories, не Git|V: всё|CLEANED thread root|0,402|нет|
|C:11/05/01a1ad80-fcf2-74b1-8c2d-ba86e822a8e6|0,238|raw129, не Git|V: всё|CLEANED thread root|0,241|private raw остаётся local|
|C:11/04/01a1a8a1-3a5a-7890-9094-8aad2a8b18ab|2,335|physical source/stage127|N: все bytes|CLEANED thread root|2,484|unique draft сохранён|
|C:11/04/01a1a825-c373-7513-a65a-223a80ab4b0b/task126/Phantom-Party-Pvp-Life-126-bc5c30db-030e-450f-81ed-3daeec24dfef|2,309|physical stage126|N: все bytes|CLEANED exact stage|2,435|ACL sibling и остальные logs126 остаются|
|D:phantom-109-controlled-delivery/L2J-phantom-world|1,233|dirty4+untracked2, registered|W и исходный worktree;6 SHA совпали|PRESERVED|0|старый draft/открытый Git handle, без force|
|D:b17f/verification-fixture|0,000000413|другой repository, нет remote|исходник/Git refs на месте|PRESERVED|0|local-only fixture; не L2J|
|C:ACL094/096/126 (точные paths в INVENTORY)|3,613|metadata недоступна owner|оригиналы на месте|BLOCKED ACL|0|без изменения ACL не удалять|
|Прочие C: old evidence/current/005/006|0,349|mixed artifacts, не cleanup code|на месте|PRESERVED|0|активные/unique artifacts сохранены|

* Первый partial Remove-Item migration057 успел удалить часть уже сохранённых файлов до read-only отказа; отдельная дельта этой части не записана.14,359ГБ — receipt успешного продолжения, не оценка полного исходного размера. Все426 файлов в M проверены до первой операции.

После удаления C: осталось3961763786 bytes/139270 files; D-worktrees1233105167 bytes/85721 files. В общих Git worktrees только main и сохранённый dirty109; prune dry-run пуст. Во время cleanup SQL/Java не запускались, чужие процессы не останавливались, ACL/UAC и глобальные настройки Codex/Git не менялись.

- mojibake-маркеры в изменённых файлах проверены —27literal patterns,0совпадений.
- escaped Cyrillic в изменённых файлах проверены —все6regex,0совпадений.

Итоговый scope публикации — только [PLAN](PLAN.md), [INVENTORY](INVENTORY.md), [COMMANDS](COMMANDS.md), [RESULT](RESULT.md). Исходный feature SHA `dfcba154fa1f459be6082a559744d2b4a5c9b47a`; новый игровой код0. Точный docs-only outgoing SHA, ordinary push и независимый GitHub readback записываются после отправки в `D:\Codex\_PRESERVE\PHANTOM-DISK-CLEANUP-001\PUBLICATION.json` и приводятся в финальном ответе. Исходный/конечный006SHA остаётся `5a38d35194c914c5dda90f8d57763a916201e977`; manual gate006 — REQUIRED.

## Почему появились копии и как ограничить рост

Подтверждённая локальная причина: в истории TASK130 есть явный `git clone --no-local --depth 1 --single-branch` из `r130/src` в `r130f/src` внутри visualizations; найдено20 самостоятельных `.git` directory clones и повторные runtime/SQL/build outputs. Отсутствие alternates означает самостоятельные object stores. По именам `r130/recovery130/safe130/final130` видно повторное развёртывание изолированных проверок; связь каждого развёртывания с конкретным мотивом не выдумывается. Уже существующий WORKFLOW130 прямо требовал **один clone + один stage** и запрещал10клонов/30ГБ preimages; фактический набор этому ограничению не соответствует.

Это не доказательство сбоя автоматической уборки приложения. Найденные arbitrary clones внутри visualizations не являются Codex-managed worktrees. [Официальная документация](https://learn.chatgpt.com/docs/environments/git-worktrees) описывает lifecycle managed worktrees, сохранение snapshot и cleanup при archive/лимите; она не обещает уборку произвольных `git clone` и TEST SQL в visualization roots. Локальные настройки cleanup не изменялись и их значения не предполагаются.

Предлагаемый механизм для будущих задач, **без включения глобального cleaner**:

1. В начале задачи фиксировать owner/thread ID, exact source/runtime/SQL/artifact roots, исходный SHA и реестр созданных временных ресурсов. Не использовать visualizations для полного source clone.
2. По умолчанию переиспользовать пригодный checkout либо один managed linked worktree с общими Git objects. Если task требует independent clone, держать ровно один. Не создавать новый clone для каждого повторного focused gate.
3. Держать один task-owned TEST datadir и один runtime stage; сохранять checkpoints и модели отдельно от воспроизводимых binaries/caches. Protected runtime/copy contracts без отдельной задачи не менять.
4. Финализатор задачи работает только по её registry/allowlist: проверяет отсутствие активных процессов/handles/reparse, публикацию source, dirt/ignored, зависимые alternates и уникальные evidence. Не сканирует и не удаляет `.codex`/историю глобально.
5. Перед освобождением unique overlay/SQL evidence должны иметь полный manifest, SHA verification и restore smoke. Непонятные файлы получают PRESERVED с размером и причиной. PARTIAL/manual gate — сохранять SQL, checkpoints, модели и нужную causal evidence.
6. После успешной доставки удалять только воспроизводимые собственные временные resources; managed worktree убирать native archive_worktree с предварительным сохранением нужных ignored данных. Отдельные clones удалять только после доказательства восстановления. Main/source и истории не касаться.
7. Писать BEFORE/AFTER free bytes и список residual resources в RESULT; task не считается законченным по housekeeping, пока registry не содержит CLEANED либо обоснованный PRESERVED/BLOCKED для каждого созданного ресурса.

Этот механизм предложен и продемонстрирован текущей bounded очисткой; глобальная автоматизация, settings, hooks, AGENTS и runtime builders не изменялись.
