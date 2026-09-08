# Roadmap v5 documentation sync — context

Required parent: `e148d8e4d76a8b3afda1a200aa44aad9260943fc`.
Branch: `feature/phantom-world`.

Это documentation-only synchronization после Goal034 closure 6 `BLOCKED`.
Runtime baseline closure 6 уже закоммичен и отправлен в origin.

## Что доказала closure 6

- `_skillListTask` lifecycle edge детерминирован и исправлен:
  regression `20/21 FAIL -> 21/21 PASS`;
- focused lifecycle/materialization/background suites green;
- два fresh full `ant verify` PASS;
- final jar PASS;
- fresh real gen1: desired/expected/online `5/5/5`;
- native restart/drain PASS;
- единственный post-fix real retry gen2: desired/expected/online `5/5/1`;
- missing profiles: `6028,6029,6032,6033`;
- cleanup PASS, forced=false, no orphan process;
- production DB не использовалась;
- Goal034 остаётся `BLOCKED`;
- Goal035 не начат.

Authoritative closure6 report:
`docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-player-future-cleanup-resume.md`

Следующий runtime resume после этой documentation-only задачи должен отдельно
детерминировать post-restart loss четырёх scheduler-admitted materializations,
используя preserved gen2 evidence closure 6. Эта задача runtime blocker НЕ чинит.

## Почему нужен Roadmap v5

Текущие canonical документы всё ещё содержат Roadmap v4, где:
- Goal037 — rates/quest normalization;
- Goal038 — final release gate.

Это больше не соответствует согласованному конечному scope.

Roadmap v5 должен иметь конечный хвост:

1. Goal034 — текущий automated black-box local-stack acceptance, сейчас BLOCKED.
2. Goal035 — siege gameplay slice.
3. Goal036 — bounded whitelist quests / instances / class transfer / Kamaloka / Pailaka.
4. Goal037 — полный High Five quest-script inventory + server rates / quest-rate normalization / Player+Phantom parity.
5. Goal038 — Humanized Russian Semantic Pack + social/off-topic conversation + user custom overrides.
6. Goal039 — final full-vision release gate + freeze.

После `ACCEPT Goal039`:
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`.

Автоматически Goal040/041/... не создавать. Только proven bug/regression либо
новая явная feature request пользователя.

## Goal037 — обязательное усиление scope

Goal037 не должен означать выборочный аудит нескольких quests.

Нужно канонически зафиксировать будущий контракт:

- 100% inventory quest scripts под `dist/game/data/scripts/quests/**`;
- каждый quest должен быть классифицирован, а неклассифицированный script — gate fail;
- определить:
  - quest/objective item grants и chance arithmetic;
  - completion EXP/SP;
  - Adena;
  - generic/items completion rewards;
  - normal drop/spoil/manor interactions, где применимо;
  - canonical rate/helper path;
  - direct/bypass arithmetic, которая обходит canonical rates;
- отдельно классифицировать control/key/singleton quest items, которые нельзя
  бездумно умножать rate multiplier-ом;
- проверить 1x и non-1x rate matrix;
- проверить normal Player и Phantom ACTIVE/BACKGROUND parity там, где
  соответствующая механика применима;
- QA должен быть многоуровневым:
  - полный structural/static corpus audit;
  - compile/load всего quest corpus;
  - deterministic synthetic rate matrix;
  - настоящий GameServer только на representative mechanics;
- НЕ запускать отдельный real GameServer на каждый quest;
- для структурного Java анализа предпочтителен AST / Java compiler tree API;
  grep допустим только как discovery aid;
- изменение server rate config не должно молча ломать quest lifecycle/rewards;
  обнаруженный bypass/unclassified case должен иметь явный diagnostic/gate.

Goal037 не обязан заранее обещать, что каждый quest требует production patch:
он обязан сначала дать полный inventory и evidence-driven classification.

## Goal038 — Humanized Russian Semantic Pack

Нужно канонически зафиксировать отдельный feature Goal038 поверх уже принятых
Goal019/020.

Runtime по-прежнему не зависит от LLM/интернета.

Требования:

- естественная русская разговорная речь вместо только игрового command catalog;
- social/off-topic topics:
  - знакомство;
  - настроение;
  - бытовые темы;
  - интересы;
  - музыка/фильмы/игры/еда;
  - личные разговоры в bounded curated scope;
- humor, teasing, sarcasm, дружеский трёп;
- встречные вопросы;
- relationship progression:
  acquaintance / friend / trust / rivalry / enemy либо эквивалентная модель;
- bounded structured memory о важных личных разговорах и предпочтениях,
  без бесконечного raw chat log;
- empathy/congratulation/irritation/apology/reconciliation reactions;
- переходы personal conversation -> game -> personal conversation;
- light flirt;
- optional mature/18+ conversational register:
  только explicit opt-in,
  shipped default OFF;
- contextual profanity:
  anger / amused / surprise / positive emphasis и т.п.;
  частота зависит от personality/relationship/emotion/intensity;
  anti-repeat обязателен;
- пользователь должен иметь возможность расширять Semantic/Conversation Pack
  без Java recompilation;
- нужен отдельный custom override layer, концептуально:
  `data/phantoms/semantic/custom/**`
  и `data/phantoms/conversation/custom/**`
  либо эквивалентная versioned layout;
- custom data должны strict-validate при загрузке и выдавать точное место/причину
  ошибки, без тихого частичного повреждения behavior;
- core pack должен оставаться отдельно от user custom data;
- точные имена будущих config keys/layout являются design detail Goal038,
  а не уже существующей реализацией.

Нельзя переписывать честное ограничение master plan:
без runtime generative model не обещается универсальный человеческий разговор
на абсолютно любую тему. Goal038 — широкий, но bounded/versioned deterministic
social conversation scope.
