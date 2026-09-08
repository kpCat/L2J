# Roadmap v5 documentation sync after Goal034 closure 6

## Статус

`SUCCESS` — четыре canonical документа синхронизированы с factual closure6
`BLOCKED` и конечным Roadmap v5. Runtime blocker не исправлялся, Goal035 не
начинался.

- Branch: `feature/phantom-world`.
- Required parent/HEAD/origin до изменений:
  `e148d8e4d76a8b3afda1a200aa44aad9260943fc`.

## Canonical documents changed

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md`;
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`.

В commit также включены этот report и пять переданных файлов task package
`docs/phantoms/tasks/034-roadmap-v5-documentation-sync/`. Historical reports и
runtime/build/test/config/data/schema artifacts не менялись.

## Goal034 closure6 truth

- Goal034 остаётся `BLOCKED`;
- predecessor `_skillListTask` bug исправлен, regression и focused suites green;
- два fresh full verify и final jar PASS относятся к closure6 evidence;
- gen1 `managed/desired/expected/online=10/5/5/5`;
- native restart/drain PASS;
- latest real retry gen2 `desired/expected/online=5/5/1`;
- missing profiles: `6028,6029,6032,6033`;
- cleanup PASS, `forced=false`, orphan processes отсутствуют;
- production DB не использовалась;
- current blocker — post-restart loss четырёх scheduler-admitted
  materializations.

Следующий runtime шаг — новый explicit Goal034 resume от preserved gen2
`5/5/1` evidence. Goal035 остаётся `NOT_STARTED` до Goal034 `SUCCESS`.

## Roadmap v5

1. Goal034 — automated black-box local-stack acceptance, сейчас `BLOCKED`.
2. Goal035 — siege gameplay slice.
3. Goal036 — bounded whitelist quests/instances/class transfer/Kamaloka/Pailaka.
4. Goal037 — полный High Five quest-script inventory и rates normalization/parity.
5. Goal038 — Humanized Russian Semantic Pack, social/off-topic conversation и
   user custom overrides.
6. Goal039 — единственный final full-vision release gate + freeze.

После `ACCEPT` Goal039 устанавливается
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`; automatic Goal040+ запрещены.

## Future contracts

Goal037 требует 100% inventory `dist/game/data/scripts/quests/**`, отсутствия
unclassified quests, reward/drop/rate-path и key/control/singleton exception
classification, structural/AST-style audit, compile/load всего corpus,
deterministic 1x/non-1x matrix, representative real-server mechanics и
Player/Phantom ACTIVE/BACKGROUND parity where applicable. Canonical server rates
остаются authority; bypass приводит к diagnostic/gate.

Goal038 расширяет accepted Goal019/020 bounded foundations: natural Russian,
social/off-topic topics, relationship progression, bounded personal memory,
humor/sarcasm/teasing, questions/follow-ups, emotion-sensitive reactions,
personal ↔ game transitions, light flirt и contextual profanity с
personality/relationship/intensity и anti-repeat. Optional mature/18+ register
требует explicit opt-in и shipped `OFF`. Versioned custom semantic/conversation
overrides расширяются без Java recompilation, отделены от core и проходят
strict fail-closed validation с полезными location/reason. Runtime LLM/internet не нужен;
universal open-domain human conversation не обещается.

Goal039 собирает fresh evidence по safe install/config/defaults, Goal034 real
stack, ecology/restart/recovery, siege, quests/instances, rates parity,
Humanized/custom packs, scale/rollback и documentation consistency.

## Validation and safety

- authoritative `Roadmap v4` и closure4 references удалены;
- authoritative forward tail содержит ровно Goal034..Goal039;
- Goal039 является единственным final gate;
- Goal034 `BLOCKED`, Goal035 `NOT_STARTED`;
- referenced closure6 report и workflow/task paths существуют;
- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: совпадений нет;
- trailing whitespace и obvious Markdown structure проверены;
- `git diff --check`: PASS.

Ant, focused tests, jar, LoginServer, GameServer, test DB и production DB не
запускались и не использовались. Runtime-код, build files, configs, data, SQL,
schema и tests не менялись. User-owned staged/unstaged/untracked files не
изменялись и не добавлялись в индекс.

## Git

- Precondition: `HEAD == origin/feature/phantom-world ==
  e148d8e4d76a8b3afda1a200aa44aad9260943fc`; branch
  `feature/phantom-world`.
- Commit subject: `phantom(docs): sync roadmap v5 after goal-034 closure 6`.
- Exact commit SHA и non-force push result фиксируются в final handoff после
  создания commit; commit не может содержать собственный SHA.
- Reset/restore/checkout/clean/rebase/merge/amend/stash/force push не
  выполнялись.

## Next step

Новый отдельный Goal034 runtime resume от post-restart gen2 `5/5/1` evidence.
Goal035 в этой задаче не начинался.
