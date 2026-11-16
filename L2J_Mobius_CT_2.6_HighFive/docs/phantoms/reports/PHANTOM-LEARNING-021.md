# PHANTOM-LEARNING-021 — PARTIAL021_EXPLORE_FEEDBACK_UNPROVEN

17.11.2026, **manual gate REQUIRED**. FULL не достигнут: отсутствуют authenticated paid receipt экспериментальной альтернативы, новый V4 increment и новый cold checkpoint. Второй GS не запускался. Goal022 не начат. Production V4 OFF, NIGHT_TRAINER NOT_READY, SUPERIORITY NOT_EVALUATED.

## Реализация и реальный предел доказательств

Переиспользованы V4, acquired source016 и native маршрут020. Добавлены ровно два production Java-класса: `ActionExplorationV4` и `ActionExperimentReceiptV4`. В `TacticalPolicyV3` один TEST-only арбитр DAMAGE, взаимоисключённый со старой TrainExplorationV1. Нет новой модели, executor, scheduler или зависимостей. Сохраняются native skills/MP/HP/cooldown/owner/Geo/LOS, critical stock и5ms выбор. COVERAGE_TEST_V1 cap1/p=1 и EPSILON_V1 epsilon.15 с распределениями .85/.15 и .85/.075/.075 различаются явно. MODEL не получает credit за эксперимент.

План со skills/seed21001/cap1/parent179/source/episode записан atomic до CUT; каждый Decision immutable и buffered до возврата выбора. Existing `LearningPolicy.Observer.issued` фиксирует exact SourceOwner generation/operationKey в Intent до nativeDispatch. После drain предусмотрены additive Effect→original raw/row/episode/feedback SHA, complete manifest/seal, create-only checkpoint и separate JVM cold/replay0. Старые V3/V4 codecs и objectives не изменены.

**Единственный GS021**: fresh private `TEST006/.phantom-local/learning002/train021`, private SQL-copy, loopback33362/18022/19322. Preflight296ms, read-only source-SQL650ms, original180s preparation budget. Source image SHA6865da95…9364e, class94/level77, NPC20966, learned alternatives1230:28/1275:14, initial HP4827/MP2837, ownerCurrent/Geo/LOS true. Ресурсы/skills не выдавались.

Сохранены18 Decisions и18 pre-native Intents: **17 BASELINE,1 EXPLORE_COVERAGE;0 MODEL,0 EXPLORE_EPSILON**. Cursor1: stock1275:14 → selected1230:28, p=1, capturedBeforeDeadline=true. Это доказанный выбор и dispatch-intent, **не доказанный индивидуальный paid effect**. MODEL_REORDERED не заявляется; observed experiment reorder1 относится к выбору.

Original settlement: FAILED/NATIVE_ACTOR_DIED, kill0, death lower bound1,14 eventKeys; агрегированные exact observed HP7827/MP728 за40.0508691s. Эти14 native receipts были в памяти при settle; их полная индивидуальная публикация не завершилась. Нельзя привязать один из14 eventKeys к экспериментальной альтернативе по агрегату. Сохранён только первый raw+row BASELINE: HP625, MP69,1.9110388s. Остальные эффекты и причины четырёх неоплаченных/несопоставленных решений UNKNOWN. Actor after-state измерен original episode: HP0, MP2151.051552; смерть не приписывается одному cast.

По одному контролю/одному выбранному эксперименту нельзя сравнить средние/variance альтернатив. SAMPLE_SIZE authenticated control=1, authenticated experiment=0; INSUFFICIENT_SAMPLE_SIZE, selection bias deterministic COVERAGE, counterfactual UNKNOWN. Kill/EXP/SP не использовались как DAMAGE label. Ни эффективность, ни ухудшение от эксперимента причинно не доказаны.

## Первый causal blocker и bounded исправление

После drain первый `ActionExperimentReceiptV4.bind` отказал на сравнении observation через Java equals. Live `TacticalSnapshotV3` пропускает unknown facts; исторический restore добавляет явные unknown entries. Maps различаются при **одинаковых canonical bytes**. Offline воспроизведение sparse snapshot дало RED с тем же exception; исправление сравнивает полные canonical observation bytes, сохраняя exact action/owner/split/time проверки. GREEN получен без изменения старого codec.

Один локальный fix после LIVE blocker, без второго GS. В failed run publication остановилась на первом BASELINE row до сохранения остальных raw receipts/effects/complete seal. Ephemeral receipt queue уже недоступна; из episode aggregate или Decisions подделывать receipts нельзя. Новый native proof не достраивался задним числом. **TRAIN021 credits0, fit NOT_RUN, updates179→179, new checkpoint/cold NOT_RUN**. Старый179 SHA42cfff…ef96c сохранён. Зелёная offline-регрессия не выдаётся за повторный native PASS.

## G0–G8

| Gate | Факт |
|---|---|
| G0 | PASS: fresh feature baseline30a7a74, ancestor/AGENTS/source clean кроме нового пакета, physical179 SHA/cold/old020 replay0, sealed016/image, private safety. |
| G1 | Offline PASS + native pre-CUT plan/18 decisions/intents; один арбитр, раздельные modes/propensities. Полная publication не завершена. |
| G2 | Bounded offline PASS: probability/cap/deadline/off/TEST-only/schema/heldout/unknown family/owner/tamper; native legality не отключалась. Whole-path p99 отдельно NOT_RUN. |
| G3 | PARTIAL: alternative selected и pre-native Intent, paid experimental receipt UNPROVEN. Baseline raw/row подтверждён. |
| G4 | PARTIAL: original settlement14 events, полная event→experiment→feedback цепочка не сохранена; credit0. |
| G5 | NOT_RUN: новый increment/checkpoint/cold отсутствуют. Parent179 exact cold и historical replay0 PASS. |
| G6 | Contract/evidence delivered; efficiency INSUFFICIENT_SAMPLE_SIZE, superiority NOT_EVALUATED. |
| G7 | PASS bounded runtime/cleanup/preservation; one GS93190ms, wrapper323935ms; OFF/own0/portsFree,91294 files drift0. |
| G8 | Source65a79c5a669 ordinary feature push PASS; independent GitHub HEAD/parent30a7a74/exact28/full9source+19docs blobs PASS. Docs-only delivery append проверяется отдельно; manual REQUIRED. |

## Проверки и бюджет

Выполнялись последовательно, без full verify/test/campaigns:

- `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning021-test -Dphantom.learning021.cohort=<sealed016> -Dphantom.learning021.parent=<physical179>`: initial RED compile33s, static draw compile error13s, GREEN29s; расширенные binding GREEN32s; review-contract RED compile29s, GREEN32s.
- Тот же Ant021 вместе с `phantom-learning016-test`: exit0,61s; legacy020 contracts включены021, TRAIN016_INCREMENTAL_CONTRACT_GREEN. Без SQL/GS/нового credit.
- Ant021 + `jar`: exit0,59s до GS; final canonical fix Ant021 + jar exit0,66s. Все изменённые production/test classes скомпилированы. Пять прежних warnings ExtendedOpenOption/System.runFinalization сохранены.
- `powershell ...\Test-Training.ps1 -ActionValue021`: exit0 до/после; proof false-COMPLETED, missing experiment/control, Stop-before-record fail closed.
- Единственный `Training.ps1 -Action ActionValue021 -ActionValue020Command RunOnce -Root <history> -ActionValueCandidate <train020-c1>`: exit1, wrapper323935ms. Не объявлен успехом. GS/SQL lifecycle93190ms, исходный native JAR SHA32541099ba3f532c5f2f2c2eb56907c337e62680e5ba93a6a524045e11766a9e.
- Focused `javac`/`java ...PhantomLearning021V4Exploration diagnose <train021>`: exit0, exact predicates и alternative IDs; один ошибочно непроцитированный PowerShell -D запуск дал class-not-found, исправлен. Canonical sparse regression RED exit1 → final GREEN exit0.
- `java ...PhantomLearning021V4Exploration post <train021>`: exit0, incompletePublication REJECT, persisted control metrics authenticated, **trainingCredit=false**. Полная positive021 sealed-native ветка NOT_RUN.
- Public Status: NO_GO_INCOMPLETE_PROOF, updates179, additionalUpdates0, automaticNextStep=false. Independent process/loopback readback: own0, свободны. Historical pin hashes91294 drift0, Geo203/protected207 guards PASS. Fresh SQL183 SELECT digest NOT_RUN; утверждается physical preservation, а не новый logical audit.

Сумма основных automatic build/test/wrapper запусков —677.935s (11m17.935s), включая ошибки и копирование; focused диагностика/PS/preflight/source-SQL добавляет менее10s. Native lifecycle уже включён в wrapper. Проверки укладываются12min; дальнейшие runtime/build проверки остановлены. Общий wall и публикация фиксируются в delivery receipt. Цель55–60min является верхней целевой длительностью, ожидание ради неё не добавлялось.

Native episode SHA `f421181fe2365c80dca7db25e3cbcfcf0ce7b2ebd9850296e8463fbf1146eeb6`. Final SOURCE JAR после offline fix SHA75f37c80cda8d2019242e20bbd2c649fe8f0af310162a1c216c6354045782297; этот JAR в GS повторно не запускался. Private raw SQL/JAR/weights/nonces/episode payload в Git не включены.

## Scope, совместимость и review

Read-first: root AGENTS, README, workflow/status, все12 original package files021, accepted020 continuation/result/evidence, TacticalPolicy/TrainExploration/LegalAction/Receipt/Observation/LearningCastEvidence/LearningPolicy/V4 feedback+learning, native020/001/005/016 и Training scripts/build. Аналоги: old exploration007/008, V4 actionValue020, original016 reconstruction/positioning и existing pre-native Observer. Вложенные HighFive AGENTS и отдельный code-map/pattern в пакете021 не найдены; повторного широкого поиска не было.

Bounded exception:9 code/build/script файлов в одной learning подсистеме плюс исходный task package, PLAN/CONTRACT/RESULT/EVIDENCE, этот report и одна roadmap. Exact allowlist зафиксирован в EVIDENCE. MAIN/PLAY/другие worktrees/Studio не изменялись. Новые production classes не содержат class94/NPC20966.

Независимый read-only review выявил4 Important: complete log, semantic summary, pre-native owner и legacy ports. Исправлены в одном проходе: manifest/seal+cursor/cap validation, summary recomputation, immutable Intent через existing Observer и conditional021 ports. Focused negative contracts GREEN. Reviewer не запускал GS/SQL. Declined: actual5ms/p99, physical pins/native outcomes — проверены только в указанных bounded пределах; полной latency guarantee нет.

Будущий versioned adapter/rollover contract — [CONTRACT_V1](../tasks/PHANTOM-LEARNING-021-V4-SAFE-EXPLORATION-EFFICIENCY/CONTRACT_V1.md): DAMAGE only, known/unknown, frozen Decision→Intent→Effect→Feedback, MAX_EVENTS4096 и batch≤256, append-only segments/idempotent cursors, STOP до явного rollover; scheduler/production loader не реализованы.

Git разрешён текущей задачей: bounded inspection, exact staging/commit и обычный feature push. Точные команды/коммиты/readback — в delivery receipt. Force/reset/clean/stash/branch/worktree/история не использовались.

Scope guard: exact28 paths,9 code/build/script файлов. Обе encoding проверки дали0 совпадений. Cached diff-check исходного user task package выявил один уже поставленный trailing space в ARCHITECTURE.md:20; файл сохраняется по MANIFEST.sha256 без переписывания. Для реализации и новых result artifacts отдельный exact-path diff-check проходит; исходное совпадение не скрывается.

- mojibake-маркеры в изменённых файлах проверены: полный набор,0 совпадений;
- escaped Cyrillic в изменённых файлах проверены: отдельно все6 regex,0 совпадений.

Одна безопасная возможность продолжения: после owner manual review отдельно разрешить один fresh owned TEST на исправленном final JAR; текущий partial root/историю не переигрывать. Такое продолжение в021 сейчас не запускается и не обещается в фоне.
