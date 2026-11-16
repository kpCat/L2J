# TASK — цель и точная постановка

## 1. Цель
Закончить реальную сквозную интеграцию компактного обучаемого оценщика `ActionValueV4` и существующего GameServer без изменения штатных правил игры. **FULL020** возможен только если новый подлинный V4-опосредованный native action дошёл до paid effect, стал новым V4 sample, получил *одно* законное обновление параметров в отдельной модели и прошёл холодную загрузку/проверку повторного применения. Сохранение старого 161-update checkpoint не подменяет нового evidence.

## 2. Что уже есть — переиспользовать
- `ActionValueV4` (128 коэффициентов, формат, SHA, score), `ActionFeedbackV4.nativeAction`, `ActionLearningV4.fit/save/load` (event ledger), `TacticalReceiptV3`, `TacticalStoreV3.Row`.
- `TacticalPolicyV3.actionValueTest/actionValueChoice`, существующие stock fallback, `PhantomCombatService`/`PhantomPartyTactics` — не менять игровую семантику.
- `PhantomLearning019ActionValue.nativeOnce` и `PhantomLearning001ShadowNative.Runtime.actionValue019Solo` как исходные точки TEST. Причину их отказа описывает `NATIVE_SCENE.md`.
- `PhantomAutonomousTrainingNative.collectEpisode` и `PhantomLearning001ShadowNative.Runtime.prepareAcquired / prepareAcquiredOutcome / runLearning006Encounter / settleLearning004`: **существующий** рабочий путь 016, давший 51 native CUT. Перенять необходимый короткий путь вместо копирования всего director/cohort.
- `PhantomTrainingScenarios`, `PhantomEncounterLocalSource` — законные исходные scene/anchor/admission/Geo; не заменять на произвольные телепорты или cheat effects.

## 3. Реализовать в **одной** задаче
1. Сначала проверить preflight и известные входные данные 016: отобрать **не больше 2 Solo candidate scenes** (например классы 94/110) с действительными learned skills/owner/inventory/source/Geo, canonical npc/placement, explicit point; при невозможности — причинный STOP до любого GS. Исторический pre-CUT не разрешает бой автоматически: после boot повторить native checks. Нельзя выдавать offline preflight за факт текущего World.
2. Подключить в TEST020 **один** короткий канонический native encounter вместо `coldSoloOpeningTargets` около случайного anchor: учесть исходную сцену, текущие NPC/owner/skills/MP, Geo/LOS, target/admission. В пределах **одного** GameServer допускается проверить второй заранее проверенный candidate, только после корректного завершения/cleanup первого, без нового boot и без скрытого восполнения ресурсов. Никаких 51 slots, producer credits или полного director.
3. Явный TEST-only V4 attach: `ActionValueV4` OFF по умолчанию; V4 может ранжировать только уже законные `DAMAGE` предложения штатного Combat. Не изменять XP/SP, MP, HP, шансы, криты, boss/raid/skill rules. Не заставлять модель выбирать другой навык ради красивого отчёта. Сохранять `MODEL_CONSULTED`, `MODEL_REORDERED`, stock/chosen + оба score и reason как **разные** факты.
4. После настоящего CUT и оплаченного action — получить `TacticalReceiptV3`, оригинального owner, оригинальный `TacticalEpisodeV3` после native settlement (не придумывать episode); привязать event, actor/epoch, target, timing, native row, источник, SHA. Сформировать один или несколько **новых** `ActionFeedbackV4`, без зависимости от EXP/SP всех членов Party. Если episode/receipt не доказан, пометить UNKNOWN и **никакого credit**.
5. После остановки native: короткий offline `ActionLearningV4.fit` на старом зафиксированном candidate 019 + **только свежих** допустимых TRAIN событиях; сохранить новый versioned checkpoint create-only, отдельная JVM cold restore, replay of same event = 0 additional updates, tamper reject. Если новые события не имеют TRAIN authority — не менять split задним числом; отчёт PARTIAL по G6.
6. Открытый API/CLI этапного запуска `Status` / `RunOnce` / `Stop` (без вечного фонового цикла) на защищённом owned TEST root, с bounded time, checkpoint и safe stop, **только если** пункты 1–5 уже успешны. Не превращать задачу в ещё один scheduler. Полноценный самостоятельный ночной цикл — следующая задача. В 020 нужен воспроизводимый end-to-end single step.

## 4. Обязательные ограничения
- Старые `016/017/018/019` immutable; accepted TEST2, DRAFT14, terminal Adam 903updates/223epochs, pointer12, 183 historical five-SELECT rows, protected/Geo и MAIN/PLAY сохраняются.
- Никаких rewrite `PlayerNativeEvidence`, `TerminalRewardReceiptV1`, `ActionAdvantageV2`, `TacticalModelV3`, reward objectives V1/V2, не обновлять модель V3 и production pins.
- No migrations. No backfill/repin old events. No free skill/MP/HP/weapon, no invented monsters or forged receipts. Допустим только законный native `Spawn` в **изолированном TEST** при повторении уже attested scene и с полным каноническим admission, если штатный 016 так делает; в обычной эксплуатации запрещено создать моба ради тренировочного успеха.
- Правила поведения PvP/Party/HEAL/дебафф/танкование **не расширять** в этой задаче. Не внедрять BSoE/Return/бегство в обучаемую стратегию.

## 5. Статус результата
`FULL020_REAL_V4_NATIVE_AND_INCREMENT` — только G0–G8. Возможные честные статусы: `PARTIAL020_NATIVE_SCORED_NO_NEW_CREDIT`, `PARTIAL020_NATIVE_PRECUT_ONLY`, `BLOCKED020_NO_LAWFUL_SCENE`, `PARTIAL020_NO_NATIVE_PROOF`. После одной неудачи не умножать GS/budget. Goal021 автоматически не начинать.
