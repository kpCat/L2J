# PHANTOM-LEARNING-010 — основная ветка и завершение EVAL

**Уровень Codex: Extra High.** Новая сессия Codex. Язык коммуникации — русский.

## Цель и приоритеты

**A (жёсткий блокирующий этап):** аудит всех локальных checkout/worktree/stash, сохранение уникальной работы и корректное объединение опубликованной цепочки LEARNING006–009 в `feature/phantom-world`. Все следующие коммиты — непосредственно в `feature/phantom-world`. Никаких очередных `wip/phantom-learning-*`.

**B (только после PASS этапа A):** минимально исправить реальную причину нулевого допуска в независимой EVAL009 и проблему окончания GameServer `STOP_UNCONFIRMED`; затем выполнить ограниченную настоящую EVAL на изолированном TEST без повторного обучения и без изменения существующей модели.

**Не входит:** новая нейросеть, PvP self-play, новая логика рейдов, боевой AI всех профессий, включение DRAFT14 в MAIN/PLAY, автовнедрение модели в игровой сервер, изменение экономических rates или механик кастов. Будущая обучаемая командная тактика (танк/ДД/хилер, рейды, PvP) заносится в roadmap как архитектурная перспектива без реализации в этом Goal.

**Критерий честности:** успешный merge не делает обучение FULL009, а успешный native EVAL не доказывает превосходство модели. Итоги A и B фиксируются независимо.

## Источники истины

GitHub: `https://github.com/kpCat/L2J`

Основной checkout (проверять фактически): `D:\Codex\repos\L2J-phantom-world`

Модуль: `L2J_Mobius_CT_2.6_HighFive`

Изолированный TEST-клон: `D:\Codex\repos\L2J-learning-006`; private TRAIN/SQL: `.phantom-local/learning002/train` (путь относительно TEST-клона). Создавать новый огромный clone/worktree запрещено без конкретной необходимости и согласования.

Проверенные удалённые refs на момент выдачи:

- `origin/feature/phantom-world = 7e997636dbac9d5e1754e00c793cd6fac858b0c6`
- `origin/wip/phantom-learning-009 = 01e04537eec0a4f6b19236c4e547df7b2a22efb5`
- `origin/master = 16d61833b3983a3976583d0e4813e0de9457a52f`
- merge-base feature↔009: `dfcba154fa1f459be6082a559744d2b4a5c9b47a`
- `feature` имеет 1 собственный commit с четырьмя документами `PHANTOM-DISK-CLEANUP-001`;
- цепочка009 имеет 13 commits сверх общего предка и относительно feature изменяет 122 файла; 13 не идентично 13 новым задачам.

Эти SHA — **пин исходной диагностики, не лицензия пропускать свежий git fetch/verify**. Если remote HEAD изменился, реальный HEAD имеет приоритет и изменение нужно объяснить; нельзя слепо сливать старый SHA.

Читать до изменений: `AGENTS.md`, case-sensitive `L2J_Mobius_CT_2.6_HighFive/Agents.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/PHANTOM_CURRENT_STATUS.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, итоговые `RESULT`/`HANDOFF`/`ACCEPTANCE` 007–009. Старые абсолютные пути в Agents.md исторические; проверить фактическую структуру.

## A — аудиторский gate до merge

1. Выполнить `git fetch` в известных репозиториях с подтверждёнными remotes, `git status --porcelain=v2 -uall`, `git branch -avv`, `git worktree list --porcelain`, `git stash list`, рефлоги, tags и наличие локальных коммитов, отсутствующих в GitHub. Определить действительные Git roots, не полагаться на старое имя пути.
2. Проверить `D:\Codex\repos\` на дополнительные существующие checkout только по ограниченным известным Git-каталогам. Без глубокого перебора пользовательских данных или удаления.
3. Для каждой локальной ветки, worktree и stash: SHA, upstream, рабочее состояние, уникальные коммиты и изменённые/неотслеживаемые файлы. Сопоставить GitHub branch ancestry. Отличать потерянную **работу** от давно сохранённого эксперимента и приватных SQL/Geo.
4. Сохранить каждый потенциально уникальный код/документ до любых операций в защищённых byte-for-byte backup и manifest SHA-256 **вне checkout**. Не копировать секреты/SQL/Geo в публичный репозиторий. Никаких массовых удалений, stash pop, reset, clean, force checkout, принудительных перезаписей или удаления веток.
5. Отдельный `LOCAL-AUDIT.md` — `SAFE_TO_MERGE` или точная причина BLOCKED. Не объявлять 'ничего не потеряно' на основании только GitHub; локальные данные могут иметь уникальную работу.

### A — сам merge

Только если аудиторский gate пройден и найден чистый безопасный checkout:

- В **актуальной** `feature/phantom-world` выполнить `merge --no-ff` опубликованной `wip/phantom-learning-009`, сохранив оба parent histories. Не делать squash/cherry-pick всех commits/rebase/force-push. Обязательный предикат: HEAD feature до merge и 009 являются предками нового merge commit.
- Не использовать dirty основной checkout как место merge. Если основной checkout грязный, сохранить его, провести merge в уже существующем подходящем чистом checkout без создания нового клона; затем обновить основной checkout только когда это безопасно. Если чистого места нет — STOP с отчётом и запросом конкретного решения, не подменять безопасный gate.
- Убедиться, что четырёхфайловый `PHANTOM-DISK-CLEANUP-001` commit не исчез, цепочка001–009 включена и тестовые/private данные не затронуты.
- Проверить `git diff --check`, JDK25/Ant `jar`, targeted learning 007/008/autonomous009, основные regressions и кодировки. `ant verify` не использовать вслепую: там есть DB gates.
- Подтвердить дефолт `LearningMode=OFF`, exploration/TEST-флаги выключены; никакого автоматического promotion. DRAFT14 остаётся в изолированном owned TEST SQL, **не переносить raw SQL/Adam в Git**.
- Обычный `git push origin feature/phantom-world`, после него независимый readback remote HEAD/merge parents/scope/source hashes. Изменения в `master`, MAIN/PLAY запрещены. **Первая безопасная публикация должна произойти до начала B**, чтобы даже при неудаче B консолидация сохранилась.
- Поправить точечно документированный workflow: новые задачи от `origin/feature/phantom-world`; коммиты и обычный push только туда; временные WIP не заводить без отдельно обсуждённого риска. Не объявлять главную локальную папку чистой, если там остался сохранённый user dirt.

## Базовое состояние обучения (не переписывать)

009 закрыта `PARTIAL009_REAL_FIT_NO_TUNING_IMPROVEMENT_EVAL_AND_NATIVE_LIMIT_FAILED`, manual gate REQUIRED. Новый автономный SQL checkpoint DRAFT14, Adam `887/207 → 903/223`, 16 gradient-эпизодов 3 классов и 48 applications, двойное fault recovery подтверждено; TUNING selected epoch0, улучшения нет. AUDIT MSE14=0.014208027 против collection teacher12=0.005066568. EVAL: **0 lawful cells / 222 refusals / 0 pairs**. `STOP_UNCONFIRMED`: поздний фактический выход JVM; консервативный charged 96.720667 минут против разрешённых90; history 009 не обнулять. TEST был STOPPED/ports free/config OFF. Сохранить immutable кампании и raw отрицательные факты.

`cold-checkpoint.json` всё ещё указывает на последний завершённый цикл model12, а SQL содержит DRAFT14/Adam903/223. Это не ошибка для исправления ручным перемещением указателя; необходимо разделять working SQL checkpoint и completed cycle pointer.

## B — узкое исправление генератора независимой EVAL

**Начинать B только при A=VERIFIED на origin/feature/phantom-world.** Работать в уже существующей изолированной TEST installation, используя код нового feature HEAD. Без MAIN/PLAY.

1. Восстановить точный отказ 009 EVAL из immutable records и active code. Не угадывать причины; фиксировать типы 222 отказов, сравнить admission на TRAIN F и независимой EVAL. Проверить game-bound target, `magicLevel`, actual acquired skills/enchant, ready skill rank, mode Solo/Party, helper requirements, profile/actor load, NPC level/Geo/LOS/owner, class and no-data coverage.
2. Устранить минимальную первопричину в **универсальном** генераторе/adapter, без ID-списков класса/skill, без ослабления combat/party/safety/raid guards, без синтетического damage/кастов и без изменения split или reward. Shared legal selection должен одинаково проверять явные требования TRAIN и EVAL, но независимая EVAL должна использовать другие frozen actors/incarnations/conditions и не подтягивать TRAIN outcomes.
3. Дешёвый headless/deterministic PRE-CUT admission перед GS: доказать хотя бы 2 различных законных EVAL scenes и как минимум 2 поддерживаемых режима/роли на подтверждённых профилях. Если источников реально недостаточно, **honest BLOCKED_EVAL_NO_LAWFUL_SCENE**, не прожигать starts.
4. Настоящая bounded EVAL: frozen DRAFT14 selected checkpoint (либо отдельно обоснованная прежняя accepted TEST модель) против stock с matched pre-CUT facts, fresh actor/target incarnation, balanced A/B orders и хотя бы 2 завершёнными matched pairs в поддерживаемых Solo DAMAGE и Party support/damage направлениях суммарно; не путать counted pairs и arms. Записать exact legal choices, real cast/effects, failures, terminal outcomes, confidence limitations. По возможности stock/stock negative control в том же GS.
5. EVAL не пишет gradient и не меняет optimizer / models / applications, подтверждение cold SQL hash перед/после; никакой автоактивации DRAFT14. Ни два боя, ни MSE не доказывают превосходство.

### B — STOP и контроль deadline

1. Восстановить причину `STOP_UNCONFIRMED`: был поздний exit JVM, поздняя фиксация exit/identity и консервативный charge сверх90. Не считать это просто 'ложным отчётом', пока не разобран actual timeline.
2. Точечно улучшить owned process handoff/stop и сбор proof фактического выхода: PID+start identity, tracked handles, termination time/UTC/monotonic stopwatch, graceful request, deadline, shutdown bound. Не убивать процессы без exact identity, чужие PID никогда.
3. Весь новый native budget учитывать **с момента boot до stop/drain**, не переносить остаток009 и не обнулять его журнал. Для 010 — отдельный накопительный immutable budget: максимум **3 новых GS starts и 45 native минут** суммарно, headless workers учитывать отдельно с явно указанной стоимостью. Никаких 4-го старта или ночного прогона. Запас на shutdown включить до каждого запуска. Если дедлайн заканчивается — честный PARTIAL, остановка, postflight.
4. End-to-end Stop proof: exact exit code/observable process absence, ports free, no owned workers/SQL, defaults OFF. Если late-exit повторится — `FAIL_STOP`, не PASS.

## Технические и организационные правила

- Минимальный дифф: трогать только файлы непосредственно относящиеся к Git workflow, scenario/eval/admission и owned stop. Не переписывать существующий Combat, Party, Player, raid, pvp из удобства.
- Не возвращать исторические дефекты. TRAIN-only experimental exploration OFF вне TRAIN, `safeToRank`, emergency heal, 5ms latency, margin .025, cooldown2000ms не ослаблять.
- Не начинать новый full fit в010: objective — доказать пригодную независимую EVAL и процессный lifecycle при неизменной DRAFT14. 010 — не новый этап бесконечной генерации данных.
- Никаких новых multi-GB clones, worktrees, рекурсивных scratch-delete; 009 SQL/optimizer/backups защищены.
- Если обнаружено неоднозначное архитектурное решение с последствиями (например, что считать законной рейдовой сценой, какой stash реально восстановить), оформить конкретные варианты, риски и один запрос владельцу. Рутинное исправление не требует согласования. Использовать `PLAN` с уже явным разрешением merge/небольших исправлений из данного `/goal`.
- Пользовательские документы — русский UTF-8 без escaped Cyrillic/mojibake; immutable receipts, exact executable commands and exit codes.
- По окончании GitHub сверять **не только текст отчёта Codex**, но и реальный remote HEAD, diff и критичные исходники. Если connector не доступен, не выдумывать независимую проверку.

## Приёмка

Гейты см. `ACCEPTANCE.md`. Ключевой продукт: feature становится единственным development target, история 009 + собственная feature история сохранена; EVAL lawful scenarios и graceful STOP подтверждены нативно в отдельном TEST. Если B не выполнена, **A может быть VERIFIED отдельно, но весь010 не FULL**. Если A BLOCKED, B запрещена.

Доказательства: `LOCAL-AUDIT.md`, `MERGE.md`, `PLAN.md`, `PROGRESS.md`, `RESULT.md`, `HANDOFF.md`, `EVIDENCE.json`, `COMMANDS.md`, `ACCEPTANCE.md`, `EVAL-REPAIR.md` в этом task folder; общий отчёт `docs/phantoms/reports/PHANTOM-LEARNING-010-FEATURE-CONSOLIDATION-AND-EVAL-CLOSURE.md`, обновление `docs/phantoms/COMBAT-AI-ROADMAP.md`.

Бюджет задач: 007 — №1/13, 008 — №2/13 PARTIAL, 009 — №3/13 PARTIAL, **010 — №4/13**, после выдачи 9 осталось. R1/R2 и новый `/goal` тоже считаются задачами; не молча расширять оценку.
