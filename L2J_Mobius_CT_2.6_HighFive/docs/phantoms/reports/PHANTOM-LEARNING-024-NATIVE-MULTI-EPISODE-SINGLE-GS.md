# Goal024 — BLOCKED: первая native-сцена завершилась смертью actor

**BLOCKED024_FIRST_EPISODE_DEATH_SAFETY_REVIEW**. FULL не достигнут.
Owner gate **REQUIRED**. Второй эпизод не имитировался и не запускался после смерти.

## Фактический результат

- Новых GS boots: **1 из максимум2**, один GameServer.main; native PID98296.
- GS lifetime по retained exit: **74.892с**; GS boot → fully OFF GS/SQL: **78.392с**, меньше8мин.
- Sealed episodes: **1**; sealed batches: **0**; episodes/boot=1.
- Episode `ba8d677d99dc6b4b4adea158d5cd6b265e769fbe630c8fb53c3869f40cb1aa4e`.
- Actor268536343, target268536366; новые физические объекты; epochs и SHA — в RESULT024.json.
- **29.6122135с**, 11 genuine paid receipts, observed HP7296 / MP590, одна смерть.
- Original reason `NATIVE_ACTOR_DIED`; native stderr: `024_FIRST_EPISODE_DEATH_SAFETY_PAUSE`.
- Retained native exit **0**: штатный Shutdown завершил JVM после assertion.
  Exit0 не подменяет batch-complete: второго CUT, второго complete и all-complete seal нет.
- Второй boot **NOT_RUN**. После реальной смерти требуется safety review; unlock не реализован.
- FIT, новая candidate, её cold JVM и atomic model HEAD commit **NOT_RUN**; TRAIN credit **0**.

Frozen216: `90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6`.
Принятая историческая lineage **188→206→216**, без новых updates.
Ledger HEAD `1ff7547d9d4571673e1e5bfa58a1f3ac3799e4d95f587a32db9ae39622df2f69`;
keys302, sequence2, committed cursor4. Исторические Goal022 boots **3/3 сохранены**.

## Реализация и проверенная граница

Добавлен явный ActionValue022 Batch024Prepare/Run/Recover/Report и TEST helper для двух сцен.
Один frozen manifest/quality-slot содержит два nonce/ID/cursor/policy; manifest привязан к boot SHA.
Первая сцена использовала lawful class94, acquired1230:28/1275:14 и stock NPC20966.
Per-episode create-only Decision/Intent публикуются перед native action; Effect/raw/row/complete — после drain.
Canonical reproduceRoster создаёт отдельные actor/profile; release связывает объекты с settled/paid epochs.
Static experiment/intents reset предусмотрен только после close. **Rearm второй сцены физически не проверен**.
TrainingOwner.ps1 восстановлен byte-identical: compatibility batch ACK не является episode или FIT input.
Старые020/021 entrypoints и production Java API не заменены; dist JAR и builds022/023 сохранены.

После OFF отдельный verifier повторно проверил original complete/manifest/row/Intent/Effect и process exit.
Partial originals сохранены; canonical authentication11 GREEN. Head/candidate216/global ledger не изменялись.
Реальная partial regression доказала отказ FIT, отказ Recover с native retry, quota1 и remaining TRAIN5.
Durable Goal023 `PAUSED_SAFETY_REVIEW` создан только после этой проверки; повторная проверка latch byte-identical.
Quality **QUALITY_UNPROVEN**, bestVerified=null, superiority NOT_EVALUATED; reference216 сохранён.
Partial JSON/MD/HTML показывают batches0, episodes1, boots1, native time и overhead.
Historical active022 pointer не переключался. Все **122232** исходных private-файла byte-identical;
дополнительно owner cleanup сверил144182 pins, drift0, TEST OFF/process0/portsFree.
MAIN/PLAY, shared MariaDB, клиент, protected Geo203/protected207+approved022 и чужие worktrees не менялись.

## G0–G9

| Gate | Результат |
|---|---|
| G0 | PASS: фактическая private source, assets, frozen216, keys302/cursor4 и original pins |
| G1 | PASS: offline GREEN, один frozen batch, два заранее разных episode IDs |
| G2 | BLOCKED: первая смерть; release первой сцены доказан, второй rearm запрещён safety pause |
| G3 | PARTIAL: один genuine sealed episode в одном GS, второго нет |
| G4 | PARTIAL: одна original chain канонически аутентифицирована, полного batch нет |
| G5 | NOT_RUN: incomplete batch, credit0, FIT/cold/new model HEAD запрещены |
| G6 | PASS: один TRAIN-slot, durable safety pause, quota/reference/bestVerified сохранены |
| G7 | PARTIAL: offline contracts/canonical fault fixtures и реальный partial refusal GREEN; full batch recovery не доказан |
| G8 | PASS: один boot74.892с, fully OFF78.392с, второй не запускался |
| G9 | Scoped delivery; final commit/push/GitHub receipt — в ответе владельцу и local readback |

## Проверки и стоимость

JDK25/Ant isolated compile трёх TEST-классов GREEN; offline verifier отдельно скомпилирован после OFF.
Java020 contracts GREEN; Java022 imported274, synthetic4095/4096/4097 rollover/cold/killed-writer GREEN.
Java024 TRAIN/AUDIT manifest negatives8+8 GREEN; эти fixtures не считаются native evidence.
PS022 control30 + post-V2 negative15 GREEN; originals1944 byte-identical в regression fixture.
Quality023 **44 checks GREEN**; PS024 три focused группы и AUDIT reservation edge GREEN.
После death: canonical11, partial FIT/Recover отказ, safety admission отказ, HEAD unchanged — GREEN.
`ant verify` **NOT_RUN_UNSAFE_DB_LANE**; jar **NOT_RUN**, production не менялся.
Final static verifier два раза с byte-identical результатом; exact allowlist13, PS5/XML/JSON syntax и diff-check.
Mojibake-маркеры в изменённых файлах проверены: полный набор27,0 matches.
Escaped Cyrillic в изменённых файлах проверены:6 patterns,0 matches.

Фактический residual GS time − episode time: **45.2797865с**; включает startup/preparation/drain/shutdown.
**Экономия времени не измерена**: завершённого batch из двух сцен нет.
Исторические89.808/89.615с и44.865/36.328с относятся к двум иным boots и не доказывают speedup.
Восьмичасовая тренировка, production V4 и Goal025 **NOT_RUN**.

Начальный READ_SET12; дополнительные чтения точечные: root README, build, CLI/test runners,
RESULT023/EXECUTION/report, три исторических scripts для private root, reproduce/materialization,
authority fixture, TacticalEpisode и post-V2 copier. Полные >1000-lines reads0, другие хроники0.
До первого patch:8 rg invocations/6 search batches вместо guideline6; ранний широкий symbol query
дал лишний output. Это отклонение, а не заявленное соблюдение лимита. READ_SET расширен ради G0/G2 writer audit.
Code/test/build9 + delivery docs4 — bounded exception одной TEST artifact family; package-входы владельца не правились.
Guideline400k tokens превышен; обоснование: physical G0/rearm/provenance audit, independent review,
полный offline gate, один настоящий boot и доказанная zero-credit safety closure. Точное usage — local receipt.
Инженерная работа до подготовки отчёта около55мин; hard stop110мин не исчерпан.

Промежуточные ошибки: Ant timestamps оставили старые classes (исправлен isolated exact delete);
Integer/Long и reject-helper в новых fixtures; старый Owner pin перекрывал post-V2 negatives
(Owner восстановлен byte-identical, проверка не ослаблена); новый PS5 файл с кириллицей потребовал UTF-8 BOM.
Начальный inline PS source probe имел interpolation error; заменён script-file и повторён успешно.
Ревью исправило lifetime/original reservation/release bindings, AUDIT slot edge и episode count.
Физический blocker не обойдён дополнительным boot или synthetic вторым боем.

Точные новые команды: `ant -f <training>/Build-TrainingBatch024.xml [verifier]`,
`Training.ps1 -Action ActionValue022 -Root <approved-history> -ActionValue022Command Batch024Prepare/Batch024Run`.
Regression commands: Test-Training022.ps1 -CorrectiveHistoryRoot/-EvidenceRoot,
Test-TrainingQuality023.ps1 -HistoryRoot/-EvidenceRoot/-NativeFixtureHistoryRoot и Test-TrainingBatch024.ps1.
Private runner/logs: `.phantom-local/logs/PHANTOM-LEARNING-024-NATIVE-MULTI-EPISODE-SINGLE-GS/`.
Подробный CLI/writer contract — EXECUTION024.md. Дальнейшие boots требуют owner safety review.

Git использовался по прямому разрешению этой задачи: status, rev-parse, symbolic-ref, worktree list,
remote -v, fetch origin feature/phantom-world; exact-path diff --stat/--numstat/--check и reviewer diff.
Публикация: `git add --pathspec-from-file=<SCOPE_ALLOWLIST024.txt>`, cached name-only/check,
scoped commit и обычный `git push origin HEAD:feature/phantom-world`; никакого force/history rewrite.
Final SHA/parent/exact13/full bodies/SHA — независимый GitHub readback после push, local receipt и ответ владельцу.
