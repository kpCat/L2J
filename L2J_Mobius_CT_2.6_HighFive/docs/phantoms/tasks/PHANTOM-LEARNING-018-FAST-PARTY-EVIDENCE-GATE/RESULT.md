# PHANTOM-LEARNING-018 — результат короткой диагностики

**Вердикт: STOP018_NO_CAUSAL_PROOF / NO_GO_NATIVE. OWNER_MANUAL_GATE=REQUIRED.**

Задача №12/13; остаётся 1 до пересмотра бюджета. Goal017 остаётся ACCEPTED_PARTIAL. Следующий Goal не запускался.

Старт wall: 2026-11-16 17:28:21 UTC (19:28:21 Europe/Chisinau). Первичная оценка сообщена к T+4min; STOP принят к T+7min. Завершение и полный elapsed указаны в финальном сообщении, включая публикацию и readback; намеренного ожидания до 20/25 минут нет.

## Причина и доказательства

Точный первый сломанный native predicate: **UNKNOWN_MISSING_EVIDENCE**. Исправимая локальная ошибка TEST не доказана; code diff=0. PHASE_DEADLINE, owner loss и смена epoch остаются гипотезами.

В текущем SOURCE `Runtime.outcomeActors` (PhantomLearning001ShadowNative.java:999–1010) присваивает EXP/SP=null при !known. known требует current!=null, равный previous epoch, отсутствие overflow обоих snapshots и current.firstUnprovenReason=NONE. Отсутствие previous вызвало бы исключение, а не сохранённый actor с null.

Напрямую прочитан canonical native settlement slot17, episode `0c661d4a55ad5e02cff2f335871c08fa45532933bc4d74afd2c5641df6ff19c5`, dataset `autonomous009-learning016-g2-v1-c000001`:

| Actor | Native credited EXP/SP | Что доказано |
|---|---|---|
| class94 / profile7791 / object268533502 / epoch1626170109455100 | null / null | Жив, NATIVE_KILL, UNKNOWN |
| class103 / profile7737 / object268533385 / epoch1626170045968800 | 52617 / 8764 | Жив, earnings известны |

Сохранённый epoch actor — исходный previous. Он не доказывает равенство отсутствующему current epoch. `actor-evidence017.json` у slot17 отсутствует. Before/current scalar snapshots, overflow, firstUnprovenReason/Nanos, phase/deadline и exp/sp counters в settlement не сохранены. Нельзя различить current=null, epoch mismatch, overflow и конкретный UnprovenReason.

SHA256 settlement.json: `47bfdac58198fef616d0d8168392c7780e83751322f172a0e809c9e6b527037f`. SHA256 precut.json: `846f06adeac6663897b483e885946674bf9348271aaad9d891ef76bd68b57d66` (причинный payload precut не аудирован).

Принятый аудит017 подтверждает17 Party NATIVE_KILL /71 excluded actions с одним UNKNOWN class94 в каждом; этот census здесь не пересчитывался. `TerminalRewardReceiptV1.java:142` неизбежно отвергает живую Party с неизвестными earnings. Это не доказывает первый historical exception: native write timing также не сохранён.

## Почему native не запускался

Observer017 уже передаёт previous/current через scalarMap без повторного snapshot и пишет `actor-evidence017.json` в collectEpisode. Менять его для исторических данных бессмысленно. Production snapshot может сам зафиксировать PHASE_DEADLINE; его вызовы не менялись.

Проверены Training.ps1 parameters, PhantomAutonomousTrainingNative.main/run и legacy ShadowNative.main. Публичный collector допускает COLLECT/EVAL; COLLECT строит расписание (минимум48, frozen TEST51), вызывает collectEpisode в цикле и пишет campaign/dataset. One-Party-only entrypoint с observer017 и отдельным bounded lifecycle не найден. EVAL запрещён. Legacy001 использует другую lane/SQL и фазовые guards, не подключает observer017; это не готовый путь для TEST006. Новый runner, reflection/private invocation, новый TRAIN dataset и изменение frozen config не выполнялись.

TEST preflight: director STOPPED, autonomous009Phase=OFF, Java processes0, listeners18001/19301/33357=0. Recorded build.jarSha совпал с actual TEST JAR. Полная новая native admission/source fingerprint/protected207-files/Geo203-files проверка NOT_RUN, потому что native NO_GO. Никакого PASS native safety или нового Party reward не заявляется.

## Изменения, проверки, сохранность

- SOURCE/remote HEAD при старте: `048ed764b9e09dfe617f569f4bfa705bed297a44`, feature/phantom-world; GitHub connector подтвердил независимо. MAIN HEAD `7e997636dbac9d5e1754e00c793cd6fac858b0c6` и169 dirt entries сохранены; MAIN не синхронизировался.
- Изменения: только этот RESULT.md и COMMANDS.md; восемь task inputs скопированы из выданного пакета без изменения bytes/MANIFEST. Исходники, build.xml, roadmap и старые отчёты не менялись.
- `ant ... phantom-learning016-test`: exit0,37s, config016 и incremental contract GREEN, SQL=0/GS=0. Компиляция:5 существующих warnings. OwnerConfig dependency сообщила changed=0; SOURCE status после теста чист.
- Existing `party-reward-contract017`: exit0 из папки модуля, NOT_NATIVE, SQL=0/GS=0. Защищает UNKNOWN reject, известные earnings, foreign target/instance, duplicate и поздний native timing; сохраняет исходные observer facts. Это синтетический contract, не Party gameplay proof. Ошибки quoting/CWD первых команд и исправленные вызовы записаны в COMMANDS.
- GS starts0, native minutes0, new TRAIN0, FIT0, EVAL0, promotion0, native Party receipts0. RED/фикс не выполнялись: доказанной ошибки нет. jar/general verify/full suite не запускались.
- TEST/history/SQL/models/Adam/pointer/MAIN/PLAY не записывались. Сохранность SQL183/1822 originals/Geo203/protected207 из017 принята как исторический факт; полный повтор SHA всех файлов NOT_RUN. Четыре safety-файла и выбранный settlement/precut сверены повторно перед выдачей. Owned runtime-процессов не создавалось.
- mojibake-маркеры в изменённых файлах проверены отдельно перед commit.
- escaped Cyrillic в изменённых файлах проверены отдельно перед commit.

## Варианты для решения владельца

1. **Отдельная one-Party native trace.** Точно разрешить TEST-only entrypoint к existing collectEpisode/observer017 с одним fresh owner/scene, без TRAIN campaign и SQL credit, одним GS и общим cap8min. Польза: получить настоящий firstUnprovenReason и before/current обоих actors. Цена: отдельное решение scope и подготовка bounded lifecycle; риск — admission не успеет или законно откажет. Успех: сериализованный исходный cause; Party reward только при полном native receipt.
2. **Отложить Party и пересмотреть training scope.** Зафиксировать недоступность старого evidence; отдельно оценить пригодность SOLO и цену достаточных данных. Цена: пересмотр pipeline и новая постановка; риск — меньше игрового покрытия. Успех: заранее проверяемый yield/budget. Имеющихся7/6/4 всё равно недостаточно; FIT сейчас запрещён.

Локальный фикс018 не предлагается без наблюдаемой причины. Production PlayerNativeEvidence/Party/Combat/reward rules требуют отдельного решения после cause trace. Manual gate остаётся REQUIRED.
