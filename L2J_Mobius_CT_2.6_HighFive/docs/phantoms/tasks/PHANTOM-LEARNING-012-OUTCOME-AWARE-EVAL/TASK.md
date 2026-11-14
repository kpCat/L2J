# PHANTOM-LEARNING-012-OUTCOME-AWARE-EVAL — исполнимое задание Codex

## 0. Зачем

В 011 Encounter Engine получил законные Solo и Party бои: два Party A/B сравнения завершились победами, но два Solo считались `MATCHED_INCOMPLETE`, потому что боты умерли. Из исходников выяснено, что оценщик путает **терминальный исход боя** и **выполнение PvE-цели**. Смерть — полноценный отрицательный игровой результат, при наличии достоверной native settlement; исключать её из сравнения ошибочно.

**Задача 012:** создать версионированную, устойчивую к исходам оценку stock-vs-learned с честной обработкой побед, смертей, окон времени и невалидных прерываний; провести на существующей011 evidence read-only proof и, только после дешёвой приёмки, новую ограниченную нативную проверку. Добавить не подгоняемый по результатам, законный подбор сложности Solo-сцен.

Нельзя превращать этот Goal в новую тренировку V3, full PvP, raid/party TeamCoordinator, league или отдельные новые классы.

## 1. Источники и безопасность

GitHub `https://github.com/kpCat/L2J`.
Только `feature/phantom-world`; предварительный SHA `9e78e3db1153126c616812bfeb8ff5bdbb8c8278`.
Основной checkout `D:\Codex\repos\L2J-phantom-world` — грязный/защищённый, не обновлять насильно.
Существующий безопасный source `D:\Codex\repos\L2J-learning-004`.
Private SQL/TEST `D:\Codex\repos\L2J-learning-006`.
JDK25, Ant, MariaDB11.4, Geo203/protected207.

Первым делом прочитать корневые `AGENTS.md`, модульный `Agents.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `PHANTOM_CURRENT_STATUS.md`, `TASK_PACKAGE_STANDARD.md`, `RESULT011`, `SCENARIO_COVERAGE011`, и документы этого каталога. Убедиться в exact ownership, dirty preservation, состоянии SQL, процессах и JAR.

Разрешены scoped commits и ordinary non-force `git push origin feature/phantom-world`. Без новой WIP-ветки, force, reset --hard, clean, массового stash, удаления чужих артефактов, правок MAIN/PLAY или чужих SQL. Локальные модели/SQL не публиковать в Git.

## 2. Создать независимый от training versioned Evaluation Outcome V2

Не менять `TacticalEpisodeV3.outcome()`, исторические `TerminalRewardReceiptV1`, action advantage007/008/009, optimizer, SQL schema, прошлые V1 codec/receipts, `PhantomLearning005Evaluation` и старые G4 вердикты.

В новом typed V2 контракте явно разделить:
- `nativeAdmitted` и CUT;
- `terminalSettled` с canonical boundary, native reason и evidence;
- `goalSuccess` (доказанное убийство целевого моба/выполнение цели);
- `actorDead/observedDeathsLowerBound`;
- `timeout`/censored outcome;
- `preCutRefused`, `cancelled`, `missingSettlement`, `invalidReceipt`;
- exact `physicalSha`, model authority, split, actor/target incarnations, уникальные native event keys;
- доступные измерения HP/MP, death/kill, duration, EXP/SP — UNKNOWN остаётся null.

Ввести новые имена артефактов / схемы, например `EVAL_ARM_V2`, `EVAL_PAIR_V2`, без безусловного переименования или перезаписи V1. Изменение только EVAL, без training credit. Отдельный local reason code при неверифицируемом исходе.

**Сопоставимая пара**: две стороны со всеми равными подтверждёнными pre-CUT physical facts, разными incarnation/event keys, законно выполненным CUT, аутентичными завершёнными terminal evidence. Победа обоих не обязательна. Смерть одного/обоих не делает пару невалидной, если терминальные сведения полны. Timeout считается отдельным наблюдаемым ограниченным исходом лишь при доказанном native deadline+settlement, но не `kill`, и метрики time-to-kill для него не определены.

**Несопоставимая пара**: отказ до CUT; unmatched physical; reused actor/NPC/event; stale owner/cancelled action; fake/unknown settlement; unsupported terminal reason; непроверенный timeout; несопоставимая сборка/config/model authority; отсутствующий terminal. Нельзя конвертировать эти случаи в смерть/поражение/победу.

Старое поле `completed` может значить только goal success / legacy outcome, а не новое `terminalSettled`.

Покрытие G4 считать по `terminalComparablePairs` отдельно от `bothGoalSucceededPairs`, с обязательным отчётом о смертях, таймаутах, incomplete, unmatched. При малом n — game verdict `INCONCLUSIVE` даже если один learned arm победил, а stock погиб.

## 3. Проверка на фактической 011, без GameServer

Сначала прочитать строго сохранённые private 011 result/settlement/timing/actor evidence. Выполнить read-only replay в отдельный namespace012. Исторические файлы и SQL не менять. Подтвердить как минимум:
- Solo AB: stock died / learned kill — terminal-comparable, success delta +1 в пользу learned;
- Solo BA: stock died / learned died — terminal-comparable, success delta 0;
- Party AB/BA: обе стороны kill — terminal-comparable и both-goal-success (если exact receipts подтверждают);
- четыре исходные пары в новом v2-срезе сохраняют traceability до hash каждого 011 arm;
- G4 старой 011 остаётся FAIL, её `evaluation-pairs.json` и `evaluation.complete` неизменны.

Если соответствующая private evidence отсутствует/неполная — не подменять её текстом отчёта и не объявлять подтверждённый replay. В таком случае `PARTIAL/BLOCKED` с точными missing artifacts; дешёвые synthetic tests могут проверить код, но не заменить authentic replay.

## 4. Независимые метрики

По каждой Solo и Party группе отдельно:
- N terminal-comparable pairs, N both-goal-success, N with-death, N bounded-timeout, N incomparable;
- результаты side STOCK/LEARNED по win/death/timeout;
- парная разница успеха: +1 (LEARNED выиграл, STOCK нет), -1 (STOCK выиграл, LEARNED нет), 0 (оба выиграли/оба не выиграли) при достоверно измеренной цели;
- отдельные значения `learnedWinsOnly`, `stockWinsOnly`, `bothWin`, `neitherWin`, `timeoutsUnknown`;
- время до убийства сравнивать только если обе стороны доказанно убили цель; при смерти/timeout значение time-to-kill UNKNOWN, не ноль;
- MP/HP/навыки/полезное лечение только по фактическим native receipts; economy/whole-party расход UNKNOWN;
- divergence считать paid+legal, не просто score ranking;
- `confidence`/uncertainty для независимых пар: конкретное n, описательные интервалы, exact sign test только по действительно discordant победам; не выдавать n2 за statistically supported advantage.

Статистически верный вывод о преимуществе нельзя получить лишь заменой `MATCHED_INCOMPLETE` на `MATCHED_COMPLETED`.

## 5. Solo curriculum — разнообразие без подтасовки исходов

Для новых независимых запусков сделать **bounded read-only selection difficulty** из доступных законных исходных персонажей и настоящих NPC. Использовать **до CUT** фактическую готовность, класс/уровень, learned abilities/ranks/enchant, MP/HP, native viability/risk, NPC level/HP/defense/атрибуты, уровень награды, Geo/LOS/faction/encounter envelope, имеющиеся native viability facts. Никаких synthetic boosts или определения победителя заранее.

Выбор должен иметь заранее объявленные сегменты, например `LOW_RISK`, `ORDINARY`, `CHALLENGING`, но назначать их только по canonical pre-CUT предикторам. Нельзя подбирать пороги по outcomes 011 или текущего EVAL. Нет источника достоверной оценки риска — помечать `UNKNOWN`, не выдумывать DPS/смертность. Не менять штатные ограничения party/solo viability, награды, шоты и серверный RNG.

Запечатать deterministic evaluation catalog с seed, версиями config/JAR/model, независимым source provenance и заранее заданным порядком AB/BA по каждому направлению. Не исключать умерших после CUT и не заменять их «более удачными» вариантами. Не компенсировать смертность искусственным восстановлением HP/MP, предметами, уровнем или выдачей новых skills. Новые персонажи/мобы только согласно canonical native snapshots и правилам подготовки011.

Весь TRAIN/EVAL leakage контроль011 сохраняется. Для неопробованных OOD-классов нужен отчёт о покрытии, не фиктивный universal PASS.

Если устойчивое Solo выполнение цели невозможно, **G4 может быть технически PASS по доказанным terminal-comparable pairs**, а игровой verdict может остаться BAD/INCONCLUSIVE. Нельзя заставлять Solo выигрывать ради выполнения технического gate.

## 6. Native подтверждение (только после offline proof)

Один новый independent evaluation-only campaign, fresh identities, DRAFT14 FROZEN, exploration OFF/credit=false/fit=false. В новой V2 кампании требуются минимум **2 подтверждённые terminal-comparable Solo** + **2 Party пары**, AB/BA внутри каждой категории, с genuine combat/terminal/paid data. Цель — подтвердить именно V2 evaluator end-to-end, не доказать эффективность за четыре пары.

Потолок Goal012: **2 новых GameServer starts / 35 native minutes**, включая boot/error/drain; первую сессию планировать на все четыре пары, второй запуск только как резерв после конкретного bounded исправления на текущем причинном пути. Headless checks, static regressions и evidence011 не входят в GS starts, но время и ресурсы headless отдельно учитывать.

Не обнулять и не менять исторические бюджеты: 009 10 starts / 5803240 ms, 010 4 starts / 715447 ms, 011 1 start / 328635 ms. Не использовать оставшиеся старые попытки 011 и не повторять old slots. Не запрашивать автоматическое расширение.

Если нет готового Solo+Party preflight — GS0 и честный PARTIAL/BLOCKED. Если source changes потребуют изменение Combat/Player/Party/Spawn/Global farm — сначала отдельное решение владельца, не ослаблять canonical law.

## 7. Итог и публикация

Создать TASK/PLAN/PROTOCOL/RESULT/ACCEPTANCE/HANDOFF/EVIDENCE/COMMANDS/STATISTICS/REGRESSION docs в текущем module task path, общий отчёт `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-012-OUTCOME-AWARE-EVAL.md`. Никакой отдельной постоянной WIP, исходники и docs только в `feature/phantom-world`.

Только `G0–G8 PASS` = `FULL012_OUTCOME_AWARE_EVAL`. Это технический статус корректной оценки, а не доказательство превосходства нейросети. При partial, блокере или отрицательном наблюдении НЕ обманывать и не переписывать прошлые отчёты. Финальная manual acceptance у владельца. Новые задачи автоматически не начинать.

## 8. Счётчик

После006R1 согласован бюджет13 новых Goal. 007=№1, 008=№2, 009=№3, 010=№4, 011=№5, 012=**№6 из13**, после выдачи осталось**7**. PARTIAL/retry/new /goal расходуют счётчик. Scope approval внутри текущего Goal не создаёт новый.
