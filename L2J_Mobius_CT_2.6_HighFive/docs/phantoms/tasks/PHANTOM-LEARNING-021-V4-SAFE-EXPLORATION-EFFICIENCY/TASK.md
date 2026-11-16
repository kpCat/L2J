# TASK — PHANTOM-LEARNING-021-V4-SAFE-EXPLORATION-EFFICIENCY

## Цель и обязательные результаты

Не просто научить V4 выставлять числа, а дать **безопасное исследование вариантов** и первичную оценку их реальных эффектов. Не менять конечную цель на «убить любой ценой»: политика оценивает полезное исполнение с учётом HP/MP/времени/риска; положительный kill сам по себе не доказывает superiority. В 021 проверяется **DAMAGE/Solo PvE**; HEAL/CONTROL/танк/Party/PvP — будущие отдельные family adapters и метрики, но общая схема не должна зависеть от класса94 и конкретного типа действия.

## Рабочие репозитории

- `D:\Codex\repos\L2J-learning-004`, `feature/phantom-world` — SOURCE + scoped commit/push.
- `D:\Codex\repos\L2J-learning-006` — защищённый TEST; не затирать старый overlay/SQL/историю, отдельный fresh TEST-run root + private SQL.
- `D:\Codex\repos\L2J-phantom-world` — MAIN с чужой грязью, только read-only.
- `kpCat/L2J` — remote, no force/reset/clean/stash, никаких новых worktrees/веток.

## Обязательный read-first G0

1. Подтвердить HEAD, ancestor, `AGENTS.md`, source status, RESULT020 + CONTINUATION_EVIDENCE020, private safety.
2. Найти существующую physical V4 179 checkpoint; доказать SHA, cold restore, update count, не пересоздавая его. Проверить наличие sealed016/020 исходной lawful scene и исходного source image; нет — STOP, не запускать GS.
3. Сверить `TacticalPolicyV3.actionValueChoice`, `actionValueTest`, `_exploration` и conflict с V4; текущий V4 не экспериментирует и stock fallback при MARGIN. Не снижать MARGIN для обычных решений.
4. Проверить канонический acquired recovery/Geo/positioning из Goal020 и не повторять ошибку40с/persisted roster; source image→reproduceRoster→natural180с→fresh admission.

## Реализация (одна цель, а не дробление)

1. Новая **версионированная TEST-only ветка выбора V4** поверх уже законного списка `LegalActionV3`: 3 режима BASELINE, MODEL, EXPLORE. Приоритет штатной safety → единственный арбитр альтернатив → native исполнение. Старый `TrainExplorationV1` и V3 semantics сохраняются; нельзя запустить одновременно два конфликтующих исследователя для одного decision.
2. Для EXPLORE добавить **два ограниченных режима выбора**: `COVERAGE_TEST_V1` (заранее зафиксированный порядок безопасных альтернатив; если есть законная возможность, один реальный TEST-переход к alternative с честной `propensity=1`, без подбора случайных draw после результата) и epsilon-политику с корректной заранее определённой вероятностью `p(selected|observation, offered, policy)` и раздельной фиксацией: **до CUT** заморозить episode policy/seed/limits, а **для каждого решения внутри боя, до native вызова навыка** сформировать immutable decision record: actor/owner, scene/episode, offered alternatives, stock, выбранный skill+level, режим, cursor/draw, policy/schema, epsilon, фактическая propensity, deadline, safety reasons. В hot path не делать fsync на каждый каст (5ms budget): bounded in-memory capture и atomic publication после settle с SHA связкой. Потерянный/неполный decision log — нет experiment credit. Не сочинять выбор задним числом.
3. После native cast сопоставить **exact** decision/selected/action/owner/target/generation/eventKey/settlement. Отдельная запись `ExperimentReceiptV1`/эквивалент, связанная SHA с исходным `TacticalReceiptV3` и `ActionFeedbackV4`, не переписывая codecs old019/020. Отсутствие receipt = отказ, не нулевая награда.
4. Действительные локальные метрики DAMAGE: HP damage, resource MP, elapsed, полезная доля HP, interruption/failed action при наличии реального receipt. Kill/смерть из episode записывать отдельно, без фальшивой причинной атрибуции к одному касту. EXP/SP не являются входом DAMAGE label. Оценки не превращать в константные цифры, одинаковые всем классам.
5. На отдельных новых genuine TRAIN actions выполнить дополнительный incremental V4 fit **от parent179**, сохранить create-only checkpoint, cold separate JVM, exact replay0, tamper reject. Existing179 exact immutable. Heldout/CONTROL не давать градиент.
6. Показать сравнение stock vs experimental, честно разделяя детерминированный COVERAGE от рандомизированного EPSILON по наблюдаемым действиям в сопоставимом контексте, явно `SAMPLE_SIZE`, различать selection bias, variability и причинно неизвестные альтернативы. *Не требовать* доказанного улучшения от одного боя; обязательно реальное исполнение хотя бы одного отклонения от stock именно **из-за эксперимента**, и хотя бы одного штатного контроля, если безопасный вариант существует.
7. Будущая интеграция: закрепить **узкий стабильный интерфейс/формат** на границе `Decision → NativeReceipt → Feedback`, поддержать multiple action families через versioned adapters без правок core trainer; без нового scheduler в021. Описать rolling ledger/capacity strategy для ночного тренера, учитывая V4 MAX_EVENTS=4096 и sample batch≤256; не ослаблять caps и не молча удалять ключи.
8. Единственная optional GS проверка в изолированной owned TEST, после read-only/offline GREEN, ≤8мин startup/stop включая natural180с. Один fresh source/candidate. Никаких вторых boot без owner approval.
9. Scoped commit/push в feature и независимая readback GitHub. История, model weights V3, 020, sample ledger и production OFF должны остаться нетронуты.

## Scope и запреты

См. `ARCHITECTURE.md` и `COMPATIBILITY.md`. Если нужен file вне approved bounded allowlist — остановиться и согласовать, не расширять молча. Один focused Java TEST entrypoint, одна команда PowerShell, один Ant target, не добавлять второй боевой сервис, фоновые потоки/новую базу/новую модель.

## Приёмка и отрицательные исходы

`ACCEPTANCE.md` G0–G8. При законной недоступности альтернатив/голоде MP/плохой геометрии: `PARTIAL021_NO_LAWFUL_EXPLORATION`, а не ложный FULL. При реальном эксперименте без последующего paid receipt: `PARTIAL021_EXPLORE_NO_NATIVE_FEEDBACK`; при реальном paid без model increment: `PARTIAL021_FEEDBACK_NO_INCREMENT`. Manual gate REQUIRED. Обязательный REAL result нельзя заменять синтетикой.
