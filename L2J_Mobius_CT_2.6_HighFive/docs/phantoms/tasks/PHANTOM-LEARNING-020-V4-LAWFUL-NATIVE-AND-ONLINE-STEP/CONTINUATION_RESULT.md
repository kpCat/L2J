# Goal020 — результат ограниченного продолжения

**ACCEPTED_FULL020_REAL_V4_NATIVE_AND_INCREMENT для заявленного Solo single-step scope. Manual gate ACCEPTED_FULL.** Владелец16.11.2026 явно принял G0–G8 и независимо проверенную GitHub реализацию HEAD `8ae662a4296076b879c8e95c43f7df4fc220b2bf`: новый native CUT,18 оплаченных V4-связанных действий, original settlement,18 новых TRAIN samples, обновление161→179, checkpoint SHA `42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c`, cold reload и replay0.

Ручная приёмка закрыта по прямому подтверждению владельца. `MODEL_REORDERED=false` и `COMBAT_SUPERIORITY=NOT_EVALUATED` сохраняются без переоценки. Acceptance-правка затрагивает только этот документ и CONTINUATION_EVIDENCE.json; исходники, данные, checkpoints, бюджеты и журналы не меняются. Новых GS запусков нет, Goal021 не начат.

Первый результат f6ebaa20ee5b86e2e65506f21e32605692f4d7d5 **не переписан**: его40с preparation, два отказа до CUT, один GS/127510мс и прежний BLOCKED остаются историей. Продолжение разрешено владельцем отдельно в CONTINUATION_AUTHORITY.md. Новый запуск — **второй в общем Goal020**, первый и единственный в продолжении; третьего нет. Goal021 не создан.

## Исправленный путь016

Источник class94: sealed016 image SHA `6865da954d8fe2f62e236aaade0f99ba4bb6433e19ba1c3a9d9bac958957364e`. До boot проверены original SavedActor codec/SHA, requireReproducible, learned skill levels, одна Solo source/cell и inventory binding. В отдельной SQL-копии read-only `readSavedActor` подтвердил тот же source SHA и physical fingerprint; source-SQL receipt SHA `2a9ebcdbcc1c0018ab6eb17b4b3fb964b7d7677eb29bccf87d336b70fc613e98` привязан к boot reservation. Отсутствие/подмена source, другой class/skill или mismatch — NO_GO до GS. Preflight не выдаётся за живое World.

Тонкий helper в существующем `PhantomAutonomousTrainingNative` вызывает исходные016 `historicalSnapshot013`, `reproduce`, `pools`, `reproduceRoster`, `rosterSource`. Это SQL incarnation из зафиксированного acquired state через штатный `createFromSaved`, с original source/readback guards; persisted roster для нового актёра больше не используется. Сохраняются реальные навыки, экипировка, resources, capacity checks и goal/source insertion/readback. Schema не менялась. Старые исходные SQL файлы не запускались и не записывались; новые actors/profiles создавались только в private SQL-копии.

`prepareAcquired` получил исходный config budget180с. Фактически новый актёр сразу имел полные natural resources: **HP4827, MP2837**, initial deficits0, recovery READY за1,36мс; HP/MP вручную не пополнялись. При отказе теперь сохраняются actual/max HP/MP, moving/sitting/attacking/casting и причина, плюс timings этапов. Новый successful run refusal snapshot не создавал.

Из016 без изменения алгоритма выделен общий `positionAcquired016`: TEST actor на targetX+180, проверка Geo path до переноса, native `onTeleported`, requireSafePartySpawn. И016, и020 используют этот helper. Далее020 повторно проверяет Geo/LOS, exact source/target, faction isolation, native owner, оба learned skill alternatives и их current eligibility/MP. Production Combat/Party/SkillData/Geo/game data и ограничения не менялись.

## Настоящий игровой результат

Новая private установка `train020-c1`, отдельные loopback33361/18021/19321. Один candidate, class94, NPC20966, источник `20966@encounter.local.c8b8c6dc89150f4be781897bae81293fd6a34c7036cea7d1d348a7a38bd3f8d5`. Только канонический TEST fixture Spawn существующего016 source; новый NPC не является сам по себе admission. Затем штатные `acquiredEncounterAdmission`, durable CUT, `runLearning006Encounter` и original `settleLearning004` после native drain.

GS загрузился за29с. **1 новый CUT,1 actor,1 original episode,18 paid exact native actions,1 canonical native kill, outcome COMPLETED/NATIVE_KILL.** Исторический бой не копировался и новый episode не конструировался вручную. ActionFeedbackV4 принимает original owner/actor epochs/target/time/row bytes с SHA и source bindings; EXP/SP всей Party для sample не нужны. Labels — individual TacticalReceipt reward.

|Факт|Результат продолжения020|
|---|---|
|MODEL_CONSULTED|true;18 model-bound paid receipts|
|MODEL_REORDERED|false|
|NATIVE_EXECUTED / PAID_EFFECT|true / true|
|Frozen lawful alternatives|1230:28,1275:14|
|Stock/chosen|Совпали в каждом receipt; встречаются1230 и1275 по штатной доступности|
|Fallback reasons|17 MARGIN,1 BUDGET_5MS; safety margin и budget не ослаблялись|
|First offered scores|[-0.0016417800306151521,-0.0015866459845373462]|
|First stock/chosen|1230/1230; BUDGET_5MS, paidHP888, paidMP69, elapsed1909439900нс|
|Сумма выбранных exact paid receipts|HP12705,5; MP902; это не whole-party метрика и не benchmark эффективности|
|Original episode SHA|`902967ab4fb49e2abf7108fdaa3f84f94491e48170f805bf0bf0d7ad881bfea1`|
|First encoded event bytes SHA|`3abde58bc2188bbaeeb65c07feca6d9d80208707e5ac1b06233ed4072005247e`|
|First native raw SHA|`1041370596a579c8bacb786169a5ba5df786f430a668a873f77e386907bc8a20`|

Положительный exit0 не является proof: owner-wrapper отдельно требует положительный model-bound native receipt + original settlement. Все model-linked записи прошли независимое raw action/owner/effect/observation соответствие, mandatory files binding и V4 validator. Один score-budget fallback не маскировался как reordered action.

## Новое обучение и G6

До boot назначен новый split TRAIN. После полной остановки native и SQL —18 новых authenticated samples, **ровно18 обновлений161→179**. Parent019 SHA сохранён: `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`.

Новый versioned checkpoint: `candidate020-42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c.bin`; SHA **`42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c`**. Стандартный `ActionLearningV4.save`: create-only temp/force/atomic move. Формат128 коэффициентов, optimizer и ledger прежние. Production promotion нет.

Отдельная JVM cold exact:179updates, тот же SHA; replay той же партии —0 additional updates, tamper reject. Публичный repeat RunOnce подтвердил сохранённые bytes/SHA и replay0. Recompute из parent при проверке не становится новым credit: текущий сохранённый179-update model повторно fit-ится в тот же SHA, файлы модели/fit/cold не перезаписываются. Retry logs имеют GUID suffix.

Операторский этап в существующем Training.ps1:

```powershell
$history = 'D:\Codex\repos\L2J-learning-006\.phantom-local\learning002\train'
$script = '.\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1'
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Action ActionValue020 -ActionValue020Command Status -Root $history
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Action ActionValue020 -ActionValue020Command RunOnce -Root $history
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Action ActionValue020 -ActionValue020Command Stop -Root $history
```

Status — COMPLETED/179/42cfff.../automaticNextStep=false только при полном связанном proof + parent/new candidate + hashes. RunOnce на уже settled root завершает fit/cold либо проверяет повтор; **нового GS не создаёт**. Used root без settled proof — NO_GO, unsafe restart запрещён. Fresh owned root может выполнить один bounded end-to-end шаг через существующий wrapper с явным parent candidate; именно этот маршрут уже выполнил ordinal2. Никакого перехода к следующему root/эпизоду или вечного цикла нет.

Stop на незавершённом owned root всегда сохраняет durable request до чтения process records; отсутствие/повреждение records или unknown identity не мешает кооперативному запросу и не даёт права на kill чужого процесса. Native claimBoot проверяет STOP перед GS и одноразовый CREATE_NEW marker. Stop после completed proof сохраняет его и оба checkpoints; это отдельно проверено SHA до/после. Ложный COMPLETED по одному cold-файлу отвергается. Реальный interrupt посреди второго боя отдельно не выполнялся — бюджет не расходовался третьим boot; before-record race и incomplete proof проверены offline contracts.

## G0–G8

|Gate|Результат|
|---|---|
|G0|PASS: исходный HEAD f6ebaa20, fresh origin тот же, scoped source;64851 physical historical pins exact, Geo203/protected207 до/после. Fresh logical SQL183 NOT_RUN.|
|G1|PASS: один source image016, read-only SQL match до boot, свежая reconstructed incarnation/goal, native owner/target/Geo/LOS/skills/MP/positioning/admission.|
|G2|PASS: parent019/161 exact, existing TEST-only V4 hook; production unchanged/default OFF, stock margin/budget preserved.|
|G3|PASS: новый native CUT, consult/selected native skills/18 paid receipts; MODEL_REORDERED=false отдельно.|
|G4|PASS: original after-drain episode, new encoded/raw events и SHA, owner/epoch/target/time/TRAIN authority.|
|G5|PASS:18 fresh updates161→179, new SHA, immutable save, separate JVM cold/replay0/tamper reject.|
|G6|PASS для bounded single-step CLI: actual Status/RunOnce/Stop, repeat0, retained proof/checkpoints, no automatic next generation.|
|G7|PASS по явному продолжению:1 additional GS/88177мс,2total/215687мс, no third; OFF/own0/portsFree.|
|G8|PASS: scoped non-force publication, независимый GitHub HEAD/parent/exact diff/full source readback и подтверждение владельца для HEAD8ae662a4296076b879c8e95c43f7df4fc220b2bf. Manual ACCEPTED_FULL.|

**Полнота этого результата не доказывает смену выбора либо улучшение боевой эффективности.** Новые веса/SHA доказаны; новый before/after score-control отдельно не измерялся. MODEL_REORDERED=false. Ночь/8h scheduler, PvP, Party/heal/tank/control/debuff и общий интеллект не проверены и не объявляются.

## Проверки, сохранность и затраты

- Offline RED: old020 давал2 кандидатов/persisted roster без source. GREEN: source SHA/codec/reproducibility/learned refs, candidate1, exact required016 stages; удалённый positioning-stage отвергается. Один ошибочный selector самого source-wiring check исправлен до GS.
- Ant `phantom-learning020-test`: exit0,36с, fork≤120с, GS0/SQL0; SQL-source gate609мс, read-only, GS0. Private root preflight283мс. К15мин offline verdict уже был GREEN; корректный route и native proof получены до25мин.
- Native bootstrap pin JAR `5d0a20b8e3f4ecb1e7a8e4db25742b22c9aa0c19bc3722fac6fdbf4b9d99fa7c`; actual production classes loaded из этого pinned JAR, TEST classes из SOURCE build. Production source не менялся, прежний собранный JAR не заменялся новой сборкой; только копия в новом private root.
- Первичный fit377мс, cold351мс. G6 repeat RED на существующий create-only path → GREEN deterministic replay/no overwrite. Proof/cleanup/raw/one-use guards PASS. Stop before-record и cold-only false COMPLETED: RED→GREEN.
- Read-only независимый review preparation до boot и G6 после: исправлены source-SQL ingress pin, refused-root cleanup и G6 Stop/status cases. Оставшихся Critical/Important нет. Reviewer серверы/SQL/тесты не запускал.
- Старые train020/016–019, source SQL physical files, budgets, журналы, parent019, V3/TEST2/DRAFT14/Adam903/223/pointer12 сохранены: **64851 files, drift0**; shared MariaDB, MAIN/PLAY, releases и клиент не трогались. SQL183 historical SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`; fresh five-SELECT183 не выполнялся, это physical preservation.
- Полный wrapper от parent process creation до safety receipt:224096мс, включает pins/copies/source SQL/native/cleanup. GS startup/run/shutdown и SQL cleanup88177мс. Общие native020215687мс. Суммарные автоматические проверки продолжения консервативно≤7мин из12; каждый offline gate<120с. Финальные focused Ant020+016: exit0,79с; TRAIN016_INCREMENTAL_CONTRACT_GREEN, SQL0/GS0/trainingCredit=false.
- Пять прежних compiler warnings ExtendedOpenOption/System.runFinalization в unrelated tests сохранены. Полные campaigns/suites/stress не запускались.

Scope:6 code/build/script файлов и4 разрешённых doc artifacts — bounded exception10 files в одной learning-подсистеме. Нового стека/абстракции подготовки/модели нет; общий extracted helper уменьшает расхождение016/020. Прочитаны актуальные source/task/AGENTS и ближайшие016 аналоги; отдельные code-map/pattern и вложенные AGENTS ранее не найдены, повторного широкого поиска не было. Старые RESULT/EVIDENCE/BUDGET020 не переписаны.

Git прямо разрешён продолжением владельца. Использованы `git rev-parse HEAD`, `git rev-parse HEAD origin/feature/phantom-world`, `git symbolic-ref --short HEAD`, `git status --short`, `git fetch origin feature/phantom-world`; exact-path `git diff --stat`, `git diff --check`, reviewer `git diff f6ebaa20... -- <exact paths>`; далее exact `git add -- <allowlist>`, `git diff --cached --name-only`, scoped cached check/stat, `git commit`, обычный `git push origin HEAD:feature/phantom-world`, `git ls-tree -r HEAD -- <allowlist>` для независимого readback. Final SHA/push/readback сообщаются после фактического выполнения. Force/reset/clean/stash/rebase/branch/worktree не использованы.

Поставка содержит два обычных scoped commits: после первого staged summary обнаружена лишняя смена EOL одинаковых jvmarg-строк build.xml. Второй commit восстанавливает точные исходные mixed EOL по независимо прочитанному f6ebaa20 GitHub blob. История не переписывалась; итоговый diff build.xml относительно baseline — ровно1 added/0 removed, новая module-root jvmarg только в020 target. Для EOL-restoration diff-check применяется к итоговому diff от baseline, поскольку возвращаемые исходные CR в промежуточном commit иначе трактуются Git как trailing whitespace. Логика и результаты тестов не менялись.

- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0 во всех10файлах;
- escaped Cyrillic в изменённых файлах проверены: отдельно все6 Unicode/XML regex, совпадений0 во всех10файлах.
