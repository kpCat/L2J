# PHANTOM-LEARNING-022 — PARTIAL, native sequence budget exhausted

17.11.2026. **PARTIAL022_NATIVE_SEQUENCE_BUDGET_EXHAUSTED; manualGate REQUIRED.** FULL не заявлен. Production V4 OFF, nightReadiness NOT_READY, SUPERIORITY NOT_EVALUATED. Два GS boots использованы; третий boot, 480 минут и следующий Goal не запускались.

Реализован отдельный TEST профиль ActionValue022: Start/Status/Pause/Resume/Stop/Report, detached WMI controller, owner/lifetime locks, immutable reservation/config/deadline, ресурсные guards, curriculum из acquired source, authenticated native receipts, versioned global ledger, separate-JVM cold и atomic HEAD. Нет нового боевого AI, production scheduler, provider или автоматического model swap. Старые V3/020/021 маршруты сохраняют смысл.

## Фактический native результат и lineage

- Первый boot: CPU_LIMIT во время cold GS startup, до CUT; актёр HP4827/MP2837, paid0, credit0. GS завершён; cleanup обнаружил ошибочный default observation root, own SQL/GS закрыты и OFF подтверждён recovery path. Ошибка не выдаётся за native PASS.
- Последний boot: **native exit0, COMPLETED/NATIVE_KILL**, один genuine native episode,18 TRAIN receipts:1 EXPLORE_COVERAGE и17 BASELINE,1 kill. Measured HP damage10917.5, MP1006, episode44.8650124s; death lower bound0, полное число смертей UNKNOWN. Это наблюдение, не доказательство causal advantage одного cast.
- Physical accepted parent188: `90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6`,274 global keys. Два независимых read-only JVM cold/replay checks PASS, replay0.
- Candidate206: `99ebcd8fe606ab54379f59dc411fec0f493b8d388009b0f564eec548f76ba537`;18 новых authenticated TRAIN updates,292 global keys,segment0. Create-only checkpoint, separate-JVM cold, atomic committed HEAD и повторный фактический local/global replay18 receipts дали additional updates0.
- MODEL_REORDERED=false, EXPERIMENT_REORDERED=true. Контроль и эксперимент различаются; +updates не считаются победой модели. Точные18 event keys/raw/row/Intent/Effect/Decision SHA, source/episode/owner-exit hashes и бюджет — в [RESULT.json](../tasks/PHANTOM-LEARNING-022-V4-AUTONOMOUS-SOLO-DAMAGE-TRAINER/RESULT.json).

Два завершённых native эпизода и цепочка188→candidate1→candidate2 за два rounds **не доказаны**: первый boot исчерпан до CUT. Один успешный round не заменяется18 отдельными updates. Функциональность автономного следующего шага и ночная readiness остаются неполной приёмкой.

## G0–G9

| Gate | Результат |
|---|---|
|G0|PASS с прямым разрешением владельца на отдельный snapshot022. Fresh origin/HEAD d952a73b, physical188/source016, private owner/SQL clone, Geo203 и source-SQL перед GS проверены.|
|G1|Start/Status/Report/Stop и Pause→Stop→Resume PASS. Double Start сохранил PID/create-time/nonce. Реальный Director killed до SQL/GS: RECOVERY_REQUIRED,188 и deadline сохранены,0 credit, detached Resume.|
|G2|PARTIAL: deterministic acquired curriculum, новые immutable attempts/cursors и latest committed model реализованы; два success rounds не наблюдались.|
|G3|PARTIAL: один genuine completed episode/normal exit0,1 paid alternative+17 controls. Второго completed episode нет.|
|G4|PARTIAL: один increment188→206, cold/local/global replay0. Второй последовательный native candidate отсутствует.|
|G5|Bounded PASS: actual pre-native Director death; offline copied-paid-before-complete writer death→no credit; checkpoint-before-HEAD writer death→reuse; reauthentication; duplicate/tamper/cap refusals. Fault fixtures не считаются native samples.|
|G6|Offline PASS:4095→4096→4097, immutable physical rollover base с точными128 weights, cold chain, global key retention, heldout0, tamper reject. Это synthetic capacity evidence.|
|G7|Реальные HP/MP/time/kill/death-lower-bound, per-action/mode/skill, n/variance/UNKNOWN, lineage и historical021 baseline публикуются отдельно. Native TUNING/AUDIT NOT_RUN.|
|G8|Два GS boots; normal successful native89.808s, последний полный lifecycle92.384s; OFF, own0, ports free. Последняя preservation144184 files/drift0. 480min NOT_RUN.|
|G9|Targeted compile/Java/PS GREEN; ant verify NOT_RUN_UNSAFE, DB-free alternative выполнена. Scope/publication/readback — DELIVERY.json. Owner gate REQUIRED.|

## Safety и исправления

Historical protected207 SHA `246ac12cd718e698b18e6ab2b338622b24d505f75ba492eb1cd348bca2e335cd` не изменялся. Полный audit остановил запуск: ровно2 pins были stale — AGENTS.md после разрешённого fast-forward и build.xml с target022. Владелец явно ответил: «Разрешаю отдельный snapshot022 для этих двух изменений». Создан отдельный private approved-protected022.json,205 остальных pins и Geo203 проверяются без ослабления. Start сам approval не создаёт; unknown paths/authority и смена config SHA отвергаются.

Первый Start выявил IntPtr.Dispose: общий Hold-ProcessHandle возвращает borrowed IntPtr, закрывать нужно Process. Нулевой bootstrap сохранён; новая owned кампания унаследовала exact ledger, без cleanup/обнуления истории.

CPU guard исправлен по exact refusal: прежний cumulative startup CPU ошибочно применялся как sustained active load. Теперь2.1-core threshold применяется к10s delta после подтверждённого GS-ready; startup ограничен ActiveProcessorCount2, memory/disk и native hard deadline. Threshold не отключён; focused regression проверяет и startup exclusion, и настоящий sustained refusal. Ресурсный отказ теперь сохраняет измерения.

Exit cleanup022 использует actual private observation layout, включая held exit proof; не обращается к отсутствующему default train основного checkout. После committed HEAD повторный Stop не считает mutable собственный HEAD historical dirt. Original closed proof и fresh process/port checks сохраняются.

Read-only reviewer нашёл orphan cleanup, отсутствие reauthentication prepared source и Stop после Pause. Исправлены в одном проходе: verified drain/SQL shutdown, full raw/row/Intent/Effect/source validation перед commit без повторного FIT и OFF finalization. После native добавлен только read-only replay audit; production JAR/native adapter не пересобирались.

## Проверки и scope

- `ant ... phantom-learning022-test`:4 targeted runs, один initial RED из-за отсутствующего source class_index; main-class index0 теперь разрешён только при classid=base_class. Final physical rollover GREEN. V4/1 codec,128 coefficients,4096 cap,batch256,label и5ms selection budget сохранены.
- Isolated `jar`:3 GREEN generations — initial, source reauthentication review fix, physical rollover base fix. Runnable checkout dist/libs не перезаписывался; protected GameServer SHA `f56dabe46d09bfb65a03b7824abe6aeaa16255d4cd67abcd1fe3ef86fe57a471` сохранён.
- Final DB-free022, legacy021 Java, PS18, legacy021 PS, existing Test-CimExitRace GREEN. Actual successful native replay18/local0/global0 GREEN. Один fault-ready fixture race исправлен atomic publication; readiness signal не является receipt.
- Baseline compile-tests RED:4 errors в чужом untracked PhantomParty109PvpChecks, absent permitsProactiveDispute/revengeCandidates. Этот файл не редактировался. Clean focused compilation GREEN; full ant verify не запускался, поскольку approved Ant DB lane отсутствует. MAIN/PLAY/shared MariaDB не использовались.
- Initial READ_SET12 прочитан bounded ranges;6 repo searches до первого patch, whole reads файлов>1000lines0. Дополнительно читались exact symbols exploration/Effect/broker/build, canonical source reproduction, bootstrap admission и native import для fault diagnosis; это bounded read-set extension, без обзора других хроник. Отдельный code-map/pattern в пакете не поставлен.
- Bounded file exception: одна learning family — helper, native TEST adapter, CLI/owner/PS tests/build target и документы022. Exact allowlist — SCOPE_ALLOWLIST.txt. Runtime SQL/JAR/models/raw receipts/nonces/пароли в Git не добавлены. Studio/Account_Manager/.idea/чужие worktrees не менялись реализацией.
- mojibake-маркеры в изменённых файлах проверены: результат финального static verifier указан в RESULT.json/DELIVERY.json.
- escaped Cyrillic в изменённых файлах проверены: отдельные6 regex, результат указан в RESULT.json/DELIVERY.json.

## Git recovery и публикация

В начале владелец случайно переместил HEAD7e997636→75567cfe и создал auto-stash. По его прямому запросу восстановлены branch/index,487 пересекающихся файлов и индекс предварительно скопированы вне checkout с SHA-256. Два stash сохранены, pop/drop/force/clean/reset--hard не применялись. После exact46 path restoration и освобождения441 проверенной копии выполнен обычный fast-forward к d952a73b. Локальные старые версии остаются в backup; Studio stash автоматически не применялся.

Git использован по разрешению TASK и отдельного recovery запроса: `git status --short`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD origin/feature/phantom-world`, `git worktree list`, `git merge-base`, scoped `git diff -- <exact paths>`, `git ls-tree`, `git fetch origin feature/phantom-world`; для recovery — `git reflog -8 --date=iso`, `git stash list`, `git stash show --stat`, `git reset --mixed 7e997636dbac9d5e1754e00c793cd6fac858b0c6`, `git restore --source=7e997636dbac9d5e1754e00c793cd6fac858b0c6 --worktree --pathspec-from-file=<verified46>`, `git merge --ff-only origin/feature/phantom-world`. Publication использует только allowlisted add/commit, diff-check и ordinary feature push; точные SHA/readback — DELIVERY.json.

Goal usage перед финальной публикацией:490223 tokens/7508s; окончательное значение фиксируется в DELIVERY. Ориентир по tokens превышен из-за незапланированного Git recovery, private source pin audit/owner approval и lifecycle/fault исправлений. Wall hard stop150min и native limit2 не продлеваются. Это не разрешение на следующий Goal.

Следующий шаг — **owner gate по PARTIAL**. Ночной trial не разрешён этой приёмкой. Команды, locations и ограничения — [OPERATOR_RUNBOOK_RU](../tasks/PHANTOM-LEARNING-022-V4-AUTONOMOUS-SOLO-DAMAGE-TRAINER/OPERATOR_RUNBOOK_RU.md).
