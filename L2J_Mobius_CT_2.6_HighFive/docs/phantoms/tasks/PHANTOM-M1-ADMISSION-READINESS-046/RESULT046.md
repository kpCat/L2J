# RESULT046 — причина admission=0 установлена

TASK046=SUCCESS_DIAGNOSIS: подтверждён незавершённый bootstrap/catch-up при занятых
native slots. Продуктовый дефект на этом причинном пути не подтверждён; production
code и semantic JAR не изменены. M1=OPEN, READY_FOR_M2=false, приёмка пользователя
остаётся обязательной. BASE/remote перед работой: d76462dd933301bb5e05aebf4c33275ef8896bf2.

Сначала прочитаны RESULT045.md и NATIVE_INPUT045.json, затем PopulationManager,
EcologyService, HistoricalBackgroundService, PhantomScheduler, composition в
PhantomSystem и ближайшие ecology/handoff tests. Паттерн: существующие MemoryStore,
HistoricalMemoryPort, настоящий manager/pulse и permission-edge callback; Java25/Ant.
Root AGENTS.md и отдельный code-map не найдены; применены родительский AGENTS.md,
High Five Agents.md, README и workflow/master-plan. Scope TASK046 задан пользователем.

| Возможная причина | Проверенный результат |
| --- | --- |
| Устаревший снимок | Данные045 актуальны для 13:41:21Z, но сцена ещё не подготовлена. До native run DB integration fixture `population-server-integration` выполнял `cleanupManaged()` до/после теста; готовая популяция исходного036 export не сохранялась. Census содержит новые10006–10972. LIVING assignment штатно начинает cursor с virtual join. |
| Долгий backlog | У всех8 enrolled `initialCatchupComplete=false`, `requestPending=true`; отставание от horizon29866421 составляет2450–23186 минут. Живой Player не доказывает завершения этой истории. |
| Calendar restrictions | Из967 только485 имеют desired ACTIVE;482 календарно не претендуют на ACTIVE admission. В enrolled10008/10009 desired BACKGROUND, остальные6 ACTIVE, но ecology-fenced. `calendarOnline` включает BACKGROUND. |
| Preparation capacity | Все8 physical slots заняты retained/live Players. У672 профилей `catchup.baseline.materialize_capacity_reached`; у10 `ecology.preparation_capacity`. Pending history не применяется параллельно live owner. |
| Отсутствие прогресса очередей | Общая остановка не подтверждена: за27.44сек READY census вырос815→967, у97 общих ID продвинулся положительный calendar cursor. Это не доказательство productive commits. Перед сменой минуты было4 current-ready, все calendar-not-ACTIVE; на enrollment current-ready=0. |
| Продуктовый дефект | Новый deterministic contract проходит на BASE product JAR. После освобождения external live/capacity facts существующие очереди доходят до current readiness и manager сам открывает capped admission. |

Различие владельцев существенно: native-context handoff открывает только
materialization gate при текущем human demand. Ordinary admission требует completed
initial catch-up, отсутствия pending request и cursor не раньше requested horizon.
`process()` удерживает pending request при live owner; после штатного ухода и
полного owned save он возобновляется через обычный worker. Наличие World-present
Player, COMPLETED native context или будущего productive forecast fence не отменяет.
Факты, cardinality и SHA исходных raw: [DIAGNOSIS046.json](evidence/DIAGNOSIS046.json).

Штатная воспроизводимая подготовка актуального состояния:

1. Использовать отдельную принадлежащую запуску TEST среду и корректный stopped
   snapshot. DB fixture с `cleanupManaged()` выполнять в другой TEST schema;
   подготовленное для ordinary проверки состояние этим fixture не очищать.
2. Дать обычным creation/metadata/history queues работать с прежними бюджетами;
   смотреть `operatorStatus().ecology()` и `operatorReadinessProgress(profileId)`.
   Фиксированные80 секунд не являются readiness gate. Если нужен native context,
   получить его обычной locality/materialization, затем штатно убрать human demand
   и дождаться снятия retention и завершения owned save, освобождая physical slots.
3. Снять свежий census после catch-up. Для допуска выбрать существующий профиль с
   desired ACTIVE, `initialCatchupComplete=true`, `requestPending=false`,
   `readiness.complete=true`, committedCursor>=requestedHorizon и `admitted=true`
   через `operatorAdmissionProfile()`. Проверить текущий horizon повторно при
   enrollment; farm permission и достаточное calendar window проверяются отдельно.
   Capacity/native-context-required означает продолжение штатной подготовки;
   terminal failure или отсутствие cursor/revision progress требует диагностики.

Добавлен один focused test `19-live-capacity-release-catchup-reopens-current-admission`
в PhantomPopulationEcologyGoal033Suite:8 live owners +1 capacity retry удерживают
admission0; после release current9/admitted8,432 productive test minutes ровно один
раз, clock неизменён, profile/interval budgets соблюдены. Это owner contract с
существующим historical double; real native preparation046 им не объявляется PASS.
Причинный product RED не получен, поэтому product fix/RED→GREEN не выполнялся.

Проверки: Ant `compile-tests` SUCCESS; ecology31/31 и scheduler30/30, exit0.
Команды: `PhantomTestLauncher population-ecology-goal033 33003300` и
`PhantomTestLauncher activity-scheduler 20260725001`; classpath начинается с
неизменённого045 GameServer.jar SHAfc19dc90786947eff5ad427792ac4f3a491340c0e647feb194ecb088f29adec2,
затем `.phantom-local/ops046/build/phantom-test/bin`, resources и dist/libs/*.
Module/report properties указывают OWN и отдельный ops046 output. Новых ordinary
server boots и DB connections046 —0. G4 не запускался, native подготовка остаётся
непроверенной; G2_CURRENT остаётся NOT_VERIFIED. P07 отдельно OPEN, не повторялся
и не исправлялся: связи с admission/readiness не установлено.

040a Game30312/Login18688 сохранены по runtimeId и UTC start ticks. Старые DB/raw,
MAIN/PLAY, clocks/rates/caps,45/90/120,60/525, earned/CAS/owned protocol и исходный
continuity evaluator не менялись. Review ZIP и новые runner scripts не создавались.

Git разрешён прямым TASK046: `rev-parse HEAD`, `ls-remote origin
refs/heads/experiment/m1-candidate007-observe008`, `status --porcelain=v1
--untracked-files=no`, scoped `diff --numstat`, `diff --check`, `diff --name-only`,
`diff -- <test-path>`, `show HEAD:<test-path>` для сохранения исходных EOL;
`diff --cached --name-only`, `diff --cached --check`; exact-path `add -- @task046Paths`,
обычный `commit -m "test(phantoms): explain admission readiness under live capacity"`,
`push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`.
Точный allowlist `$task046Paths` относительно корня OWN:

- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java` (test-path).
- `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-ADMISSION-READINESS-046/RESULT046.md`.
- `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-ADMISSION-READINESS-046/evidence/DIAGNOSIS046.json`.

Финальные HEAD=remote/clean tracked и SHA фиксируются post-push local receipt
`.phantom-local/ops046/PUBLICATION046.json`; полезные изменения сохраняются в Git.

- mojibake-маркеры в изменённых файлах проверены.
- escaped Cyrillic в изменённых файлах проверены.

М2 и следующая задача автоматически не начинаются.
