# Roadmap v5 documentation sync after Goal034 closure 6

## 1. Идентификатор

Это отдельная **documentation-only** задача между Goal034 closure 6 и следующим
Goal034 runtime resume. Это НЕ Goal035 и НЕ closure 7.

- Task: `Roadmap v5 documentation sync`
- Branch: `feature/phantom-world`
- Required parent/HEAD/origin: `e148d8e4d76a8b3afda1a200aa44aad9260943fc`
- Git repository root: `C:\\Users\\ZBook\\L2J_Mobius\\`
- Only module: `C:\\Users\\ZBook\\L2J_Mobius\\L2J_Mobius_CT_2.6_HighFive\\`
- Current Goal034 report:
  `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-player-future-cleanup-resume.md`
- Goal035 НЕ начинать.
- Runtime blocker Goal034 в этой задаче НЕ исправлять.

## 2. Цель

Синхронизировать canonical project documentation с фактическим состоянием
Goal034 closure 6 и согласованным конечным Roadmap v5.

После этой задачи новый диалог/Codex не должен видеть старый Roadmap v4,
closure4 blocker или ошибочно считать Goal038 финальным gate.

## 3. Precondition

До изменения файлов разрешены только read-only Git команды:

- `git fetch origin feature/phantom-world`
- `git rev-parse HEAD`
- `git rev-parse origin/feature/phantom-world`
- `git branch --show-current`
- `git status --short`
- bounded `git diff` / `git log -1`

Обязательное состояние:

`HEAD == origin/feature/phantom-world == e148d8e4d76a8b3afda1a200aa44aad9260943fc`

и branch:

`feature/phantom-world`

Если HEAD/origin diverged:
- НЕ reset/restore/checkout/rebase/merge/stash;
- ничего не исправлять автоматически;
- STOP/BLOCKED с factual report.

User-owned staged/unstaged/untracked changes не трогать.

## 4. Read-first

Прочитать только:

1. `Agents.md`;
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
3. `docs/PHANTOM_BOTS_ROADMAP.md`;
4. `docs/phantoms/PHANTOM_CURRENT_STATUS.md`;
5. `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`;
6. `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
7. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md` только documentation/Git rules;
8. closure6 report;
9. этот `TASK.md`, `CONTEXT.md`, `ACCEPTANCE.md`.

Не проводить широкий source audit. Java/runtime code не читать, кроме если
нужно лишь подтвердить путь, уже указанный canonical docs; предпочтительно не читать вообще.

## 5. Source of truth этой синхронизации

### 5.1. Goal034 closure 6

Не искажать:

- status `BLOCKED`;
- `_skillListTask` bug исправлен и regression green;
- focused suites green;
- два fresh full verify PASS;
- final jar PASS;
- gen1 `5/5/5`;
- native restart/drain PASS;
- latest real retry gen2 `5/5/1`;
- missing `6028,6029,6032,6033`;
- cleanup PASS / forced=false / no orphans;
- production DB unused;
- следующий runtime шаг — explicit Goal034 resume для post-restart loss
  scheduler-admitted materializations.

Run-specific IDs можно держать в `PHANTOM_CURRENT_STATUS.md` как latest evidence,
но Roadmap/master plan должны описывать blocker семантически, а не превращать
эти IDs в постоянный contract.

### 5.2. Finite Roadmap v5

Authoritative forward sequence:

1. Goal034 — automated black-box local-stack acceptance, сейчас `BLOCKED`.
2. Goal035 — siege gameplay slice.
3. Goal036 — bounded whitelist quests/instances/class transfer/Kamaloka/Pailaka.
4. Goal037 — full High Five quest-script inventory + rates normalization/parity.
5. Goal038 — Humanized Russian Semantic Pack + social/off-topic/customization.
6. Goal039 — final full-vision release gate + freeze.

После Goal039 ACCEPT:

`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`

и automatic Goal040+ запрещены.

## 6. Обязательные изменения

Разрешены ровно четыре canonical documentation files.

### A. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`

Минимально:

- заменить forward section `Roadmap v4` на `Roadmap v5`;
- обновить Goal034 factual status до closure6;
- сохранить Goal035/036;
- усилить Goal037 по `CONTEXT.md`;
- вставить новый Goal038 Humanized Russian Semantic Pack;
- перенумеровать final gate с Goal038 на Goal039;
- сохранить finite-end rule;
- в relevant Semantic Pack/data sections добавить forward requirement:
  user custom semantic/conversation data без Java recompilation,
  strict validation, core/custom separation;
- добавить humanized social/off-topic scope, contextual profanity,
  optional mature opt-in shipped OFF;
- НЕ удалять честное ограничение, что deterministic no-LLM runtime не является
  universal open-domain human conversation.

Не переписывать historical Goals 001–033.

### B. `docs/PHANTOM_BOTS_ROADMAP.md`

- metadata `Версия дорожной карты` -> `5`;
- forward post-release section -> `Roadmap v5`;
- Goal034 factual closure6 status;
- Goal035/036 без semantic drift;
- Goal037 full 100% quest-script inventory/rates contract;
- Goal038 humanized RU Semantic Pack contract;
- Goal039 final full-vision gate/freeze;
- exact finite end marker.

Historical accepted/corrective statuses не переписывать.

### C. `docs/phantoms/PHANTOM_CURRENT_STATUS.md`

- Source-of-truth current Goal034 report -> closure6 report;
- Goal034 row -> current closure6 BLOCKED truth;
- latest evidence gen1 5/5 and gen2 1/5;
- `_skillListTask` predecessor issue отмечать CLOSED/FIXED within closure6,
  не как current blocker;
- current blocker — post-restart loss scheduler-admitted materializations;
- Roadmap table -> v5 with Goal034..039;
- explicitly show:
  after successful Goal034 remain four content/feature stages 035–038
  plus final exam 039;
- next action -> new explicit Goal034 resume;
- Goal035 remains NOT_STARTED.

Не объявлять runtime SUCCESS.

### D. `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`

Сделать пригодным для следующего диалога:

- runtime baseline before docs sync: `e148d8e4d76a8b3afda1a200aa44aad9260943fc`;
- actual branch verification remains mandatory;
- Goal034 closure6 `BLOCKED`;
- exact current report path;
- concise current blocker gen2 1/5;
- Roadmap v5 Goal034..039;
- next runtime step = explicit Goal034 resume;
- Goal035 не начинать до Goal034 SUCCESS;
- production DB rules сохранить;
- user workflow/task package path сохранить.

Не пытаться записать SHA документационного commit до его создания.
Можно явно назвать `e148d8e4d76a8b3afda1a200aa44aad9260943fc` как **runtime baseline before docs-only sync**,
а actual HEAD всегда проверять через Git в следующей задаче.

## 7. Goal037 canonical future contract

В master plan/roadmap/current status должна быть достаточная формулировка,
чтобы будущая постановка Goal037 не сузила scope обратно до sample quests.

Обязательно зафиксировать:

- inventory = 100% `dist/game/data/scripts/quests/**`;
- no unclassified quest;
- reward/drop/rate path classification;
- key/control/singleton quest-item exception;
- structural/AST-style corpus audit;
- compile/load corpus;
- deterministic 1x/non-1x matrix;
- representative real-server mechanics, не GameServer-per-quest;
- Player vs Phantom ACTIVE/BACKGROUND parity where applicable;
- canonical server rate settings remain authority;
- bypass должен давать diagnostic/gate, не молча жить.

Точные implementation classes/tools оставить будущему Goal037,
кроме архитектурного предпочтения AST/compiler tree API вместо grep-only.

## 8. Goal038 canonical future contract

Зафиксировать как **новый** Goal038, не как rewrite Goal019/020.

Goal019/020 remain accepted bounded foundations.

Goal038 должен расширить их:

- natural conversational Russian;
- social/off-topic bounded topics;
- relationship progression;
- structured bounded personal memory;
- humor/sarcasm/teasing;
- questions/follow-ups;
- emotion-sensitive reactions;
- personal <-> game topic transitions;
- light flirt;
- contextual profanity:
  positive/negative/anger/amused/surprise,
  personality/relationship/intensity aware,
  anti-repeat;
- optional mature/18+ register:
  explicit opt-in only,
  shipped default OFF;
- versioned user custom semantic/conversation overrides;
- no Java recompilation for normal custom vocabulary/pattern/phrase extension;
- strict fail-closed validation with useful location/reason;
- core/custom separation;
- no runtime LLM/internet dependency.

Не придумывать будто конкретные config key names уже существуют.
Их названия/format будут выбраны в Goal038 design task.

## 9. Goal039 final gate

Goal039 — единственный final full-vision acceptance.

Он должен собрать fresh evidence по accepted declared scope:
- safe install/config/defaults;
- Goal034 real-stack acceptance;
- ecology/restart/recovery;
- siege;
- quests/instances;
- quest/server rates parity;
- Humanized Semantic Pack/custom pack validation;
- scale/rollback;
- documentation consistency.

После ACCEPT:

`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`

Никакой автоматической постановки Goal040.

## 10. Out of scope

Строго запрещено:

- любые `.java`;
- `build.xml`;
- tests/test resources;
- XML/TSV runtime data;
- configs;
- SQL/schema;
- migration;
- binaries/jars;
- `.phantom-local`;
- historical report editing;
- Goal034 runtime fix;
- Goal035 implementation;
- создание production implementation skeleton для 035–039.

## 11. Build/test/DB/server policy

Documentation-only task:

- НЕ запускать `ant verify`;
- НЕ запускать focused Ant tests;
- НЕ запускать `ant jar`;
- НЕ запускать LoginServer/GameServer;
- НЕ подключаться к test DB;
- production `l2jmobiush5` тем более запрещена;
- `prepare-phantom-test-db` НЕ выполнять.

QA здесь textual/structural only.

## 12. Documentation validation

После edits проверить:

1. `Roadmap v4` не остаётся как **current authoritative forward roadmap**
   (исторические упоминания допустимы только если явно historical);
2. в authoritative forward tail есть ровно Goal034..Goal039;
3. Goal038 больше не назван final gate;
4. Goal039 назван final gate;
5. `FEATURE_COMPLETE_FOR_DECLARED_SCOPE` связан с Goal039 ACCEPT;
6. Goal037 содержит 100% quest inventory;
7. Goal038 содержит custom override + contextual profanity + optional mature opt-in;
8. current Goal034 = BLOCKED, не SUCCESS;
9. current blocker = gen2 post-restart materialization loss;
10. Goal035 = NOT_STARTED;
11. paths/report references существуют;
12. no accidental edits outside exact scope.

Проверить exact touched files на:
- mojibake markers;
- escaped Cyrillic;
- trailing whitespace;
- broken Markdown obvious structure.

`git diff --check` обязателен.

## 13. Report

Создать:

`docs/phantoms/reports/034-roadmap-v5-documentation-sync.md`

Он должен содержать:

- `SUCCESS` либо `BLOCKED`;
- required parent;
- exact canonical docs changed;
- closure6 status copied accurately;
- Roadmap v5 sequence;
- Goal037 contract summary;
- Goal038 contract summary;
- Goal039/freeze rule;
- confirmation no runtime/build/DB/server actions;
- consistency/mojibake/escaped-Cyrillic/diff-check results;
- user-owned files untouched;
- commit SHA/push result;
- next step:
  **new Goal034 runtime resume from post-restart gen2 5/5/1 evidence**.

## 14. Git scope

Разрешён staging только exact paths:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md`
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`
- `docs/phantoms/reports/034-roadmap-v5-documentation-sync.md`
- пять файлов этого task package.

Не stage unrelated user files.

Перед commit:
- `git status --short`
- exact-path `git diff`
- `git diff --check`
- exact-path staged diff review.

Commit subject:

`phantom(docs): sync roadmap v5 after goal-034 closure 6`

Push only:

`git push origin feature/phantom-world`

non-force.

Запрещены reset/restore/checkout/clean/rebase/merge/amend/stash/force push.

## 15. Success / blocking

### SUCCESS
Только если:
- четыре canonical docs mutually consistent;
- report создан;
- exact scope соблюдён;
- commit/push successful.

### BLOCKED
Если:
- HEAD/origin mismatch;
- canonical docs содержат конфликт, который нельзя честно разрешить из closure6 + context;
- user-owned changes пересекаются с required exact documentation paths;
- невозможно сделать exact-scope commit без затрагивания чужих изменений.

При BLOCKED:
- не чинить runtime;
- не начинать Goal035;
- destructive Git запрещён;
- сохранить factual report только если это можно сделать без нарушения user changes.
