# L2-QOL-002 evidence report

Дата: 2026-09-13

Branch: `feature/phantom-world`

Required parent: `78f41e441cef6b70e65a087c4e463df4779e7844`

Результат: **SUCCESS**

## Scope и source audit

До правок прочитаны `Agents.md`, `README.md`, документы Personal QoL и Phantom World, весь task package L2-QOL-002, владельцы native trainer/skill acquisition/crystallization, Community Board, inventory, config loader и существующий test/build harness. Локально переиспользованы strict fail-closed config pattern QOL-001, штатные `SkillTreeData`/`Folk`/`RequestAcquireSkill` контракты, native inventory mutation и сообщения `RequestCrystallizeItem`, token/flood patterns Community Board и guarded DB fixture.

UI-технология этого scope — server-rendered Community Board HTML; native client UI и WinForms не менялись. Бизнес-логика оставлена в Java service/packet owners, HTML содержит только presentation и bypass placeholders.

Не менялись public schema, SQL, XP/SP и quest-rate owners, global `ALT_GAME_SKILL_LEARN` semantics, subclass/non-CLASS acquisition, Phantom lifecycle/algorithm, Goal029/Goal034 и старый Phantom freeze.

## Реализация

- добавлен отдельный strict UTF-8 `PersonalCharacterQoL.ini`: shipped master/skills/crystallization OFF, character/account allowlists empty;
- character IDs и normalized lowercase accounts разбираются один раз в immutable sets; malformed QOL-002 config отключает только QOL-002 и не портит валидный QOL-001 state;
- доступ разрешён только exact allowlisted real Player; headless Phantom и active subclass исключены;
- personal cross-class path использует только production teach set выбранного NPC и не допускает class hierarchy выше активного main class;
- list и acquire используют одинаковый явный SP multiplier: same archetype ×2, fighter/mage cross-archetype ×3; native level, previous skill, prerequisite, required-item и SP checks сохранены;
- crafted/stale acquire повторно проверяет authorization и membership в текущем trainer tree до mutation;
- `SkillTreeData` восстанавливает сохранённые foreign CLASS skills только для разрешённого personal main-class игрока; остальные stock restore rules не расширены;
- native packet и Alt+B используют один `CrystallizationService` с единственным per-player in-flight owner, revalidation перед mutation и восстановлением `player.setInCrystallize` в `finally`;
- сохранены native grade thresholds, enchant crystal count, unequip, ownership/manipulation, hero/augmented/shadow/time-limited/non-crystallizable restrictions и сообщения;
- Alt+B fallback показывает только eligible inventory, ограничен scan 500/page 10, выдаёт один 24-byte SecureRandom token на игрока не дольше 120 секунд, потребляет его один раз и повторно проверяет игрока/item/count/enchant/authorization;
- QOL-001 board/shop остаются доступны по прежнему master gate и не зависят от валидности QOL-002 config.

## Test evidence

Qualified candidate: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\qol002-rc-20260913-02`; sparse checkout содержит только High Five module, branch `feature/phantom-world`, HEAD до overlay `78f41e441cef6b70e65a087c4e463df4779e7844`. Три исходно изменённых пользовательских файла и прочие untracked artifacts в candidate не переносились.

Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не выполнялся. DB-тесты использовали только allowlisted fixture `127.0.0.1:3308/l2jmobiush5_phantom_test` под `l2j_phantom_test`; credentials в отчёт не выводились. SQL schema/data в commit не менялись.

Candidate-only materialization сохранил принятую historical EOL-рецептуру: Goal030 matrix SHA-256 `FD891490E7BED44DBA7D33F1B72D5C1DE46FF67003190B31D22B7DD96206E64E`; operator baseline bytes и 121 tracked SQL были перенесены только для воспроизводимой qualification. Эти файлы в commit не входят.

- `ant qol-personal-skills-test`: **4/4 PASS**; проверены character/account access, shipped OFF/empty, malformed fail-closed isolation, headless/subclass deny, production trainer ownership/hierarchy, ×2/×3 display/debit parity, level/prerequisite/item gates, stale acquire, persistence и global ALT regression.
- `ant qol-crystallization-test`: **5/5 PASS**; проверены native characterization, non-dwarf learned CRYSTALLIZE, exact debit/credit и enchant count, grade restrictions, board preview/confirm, expiry/replay/stale/wrong-player и native-vs-board concurrency.
- `ant qol-002-affected-test`: **PASS**; QOL-001 focused/affected, progression/acquisition, commerce/inventory, Goal037 и DB guard owners зелёные.
- Historical static chain 014/014a/015/016/017/018/019/020c1/020c2/022c1/022c2: **PASS**.
- Goal039 structure/static/documentation canary: **PASS**; ожидаемый `Java Result: 2` принадлежит отрицательному DB guard control.
- `ant qol-002-verify`: **PASS**, `BUILD SUCCESSFUL`, 13 минут 03 секунды.
- Один fresh qualified `ant verify`: **PASS**, `BUILD SUCCESSFUL`, 27 минут 00 секунд; скомпилированы 2257 production и 148 test sources. Два предупреждения `System.runFinalization` существовали до QOL-002. Intentional lifecycle failure и XXE fatal log — ожидаемые negative controls.
- standalone `ant -q jar`: **PASS**, 15 секунд; оба финальных архива успешно прошли `jar tf`.

Goal029 soak и Goal034 real-stack не запускались: QOL-002 не меняет Phantom lifecycle owner и focused regression этого не потребовал.

## Final artifacts

Финальные JAR из `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\qol002-rc-20260913-02\build\dist\libs`:

- `GameServer.jar`: SHA-256 `500F7F84404C1B3C88701DD02313E4DABED48DA65A79C01B1AE46DB3ECF60A1C`, 9004993 bytes, 4310 entries;
- `LoginServer.jar`: SHA-256 `4A6C33414AFB1DDA330CFF380A477C4D8B772A59436085EE0AC53A0ECF13C392`, 313194 bytes, 200 entries.

Commit subject: `qol(002): add personal cross-class skills and crystallization`.

Client UI: **NOT_TESTED_CLIENT_UI**. Native server packet path и Alt+B server-side fallback доказаны автоматически; визуальный прогон stock H5 client не заявляется и client patch не требуется.

QOL-002 закрыт как SUCCESS. QOL-003 остаётся PLANNED; vitality/rate items сохранены в backlog и не реализованы.

Final scope audit: 25 task-owned files; production/test/config/docs overlay, существовавший во время qualification, имеет operator/candidate parity 24/24, а этот evidence report добавлен после неё. Task package, исходно изменённые пользовательские файлы, candidate SQL/EOL materialization, local DB configs и JAR не staged. `git diff --cached --check`, strict UTF-8/control, XML и HTML checks — PASS.

Mojibake-маркеры в изменённых файлах проверены: совпадений нет.

Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Control characters в изменённых файлах проверены: совпадений нет.
