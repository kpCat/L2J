# Runtime072 — Main, sameDB restart, STOP

Final committed source `5b61a8a541a1319a9c20075c0904929ee890d8de`, Game JAR `011D5891A52AE5AA351824463608844FD4EC0C5E1C0818DD999DD8CD99681D1B`. В обоих Main использовались одни и те же JAR, schema, root, installation и hexid. Никаких reseed/copy старой DB между ними.

| Запуск | Native ready UTC | Штатный shutdown request UTC | Native shutdown complete UTC | PID game/login |
|---|---|---|---|---|
| Main | 2026-10-23 18:50:27 | 18:55:29 | 18:55:35 | 23140 / 19888 |
| SameDB restart | 2026-10-23 18:56:54 | 18:59:13 | 18:59:19 | 23056 / 32132 |

Main:5мин02с послеready, boot35с. Restart:2мин19с послеready, boot41с. Каждый native shutdown5с, оба LoginServer также штатно остановлены. `Runtime072.ps1` START/STOP exit0, без force fallback. Четыре stderr-лога проверены на Exception/SEVERE/OutOfMemoryError:0. Raw logs остаются в own candidate; [SHA](LOG_SHA072.tsv).

В первом Main созданы200 ordinary offline merchants: `openings=200`, bootstrap issuedCapital20000000, issuedStockValue82093352. Это стандартный predeclared release bootstrap, **не earned income измеряемого Warrior20**. При restart: durableRoles/nativeOwners/nonemptyStores/identityLeases=200, **openings=0**, issuedCapital и stockValue не увеличились. Не делались fake transactions.

[Before restart snapshot](main-before-restart.tsv) и [after restart snapshot](main-after-restart.tsv) сняты штатным read-only `Task072DatabaseProbe snapshot` после STOP; probe отвергает любые другие connections и чужую authority.

| Inventory | Before | After | Проверка |
|---|---:|---:|---|
| identities |216|216| SHA `1b2b1fadd6d67430d7bc95afc37f906001468e59e64f6b3cdc3214a859b42828` одинаков |
| profiles |17|17| SHA `a04cb6d68466116798858c54d3e51afe2f4d6a0ea600673410d332f4493f3c1d` одинаков |
| merchantQuota |1|1| SHA `f0a785684859af4734cdfd2fcf4c120901722ab3185eb8eafc277c21c3e0db01` одинаков |
| offlineOwners |200|200| SHA `5b4b00623bf24ed014e4aabf2aaec0a5901b96932382dbbdca1cc24c0cc22609` одинаков |
| offlineItems |600|600| SHA `b5b4ae66f90f17628e452804e13ee6b21f878c6c3a02f54510d5d5a65b31e3da` одинаков |
| items |765|768| Новые native activity items; не утверждается byte-equality |
| components |329|329| Payload SHA изменился от продолжающейся деятельности |

Пример продуктивного sameDB продолжения Main: **Vortexbra**, profile2/actor268484877, level2→3, EXP87→367, SP6→14; точная identity сохранена. Также Spoilerso/Neost/Turbove:87→367EXP; Volkmin87→261EXP. Есть реальные HP/death состояния, они не сбрасывались для отчёта. Это подтверждает обычную серверную деятельность после restart; **не закрывает P0_RESTART для TEST Warrior20 с отсутствующим paid1463 chain**.

Финальный [RELEASE status](release-final-status.json) и [TEST status](test-final-status.json): GameServer=STOPPED, LoginServer=STOPPED, databaseConnections=0. CIM exact-own task072.control/native JVM count=0; listeners2252/7952/9252/2106/7777/9152 count=0. Native focused runners также завершились и закрыли Hikari; final materialization reports STOPPED.

[Protected inventory](protected-final-v2.tsv):4 selected protected inputs,7 untracked068V files,11 original072 package files,203 protected source Geo files совпадают с начальным SHA. Первый диагностический package hash check ошибочно ожидал колонкуpath вместоrelativePath; его вывод не использован. Final-v2 выполнялся с ErrorActionPreference=Stop и явной проверкой обязательных полей, все225 inventory hashes проверены без ошибок. Полная инвентаризация чужих БД/клиента не выполнялась.

Candidate: `D:\PhantomWorld\Sustainable-Life-072-candidate`. TEST: repo `.phantom-local/task072/runtime-test`. Own TEST/RELEASE schema authority.task_id72, исходная economy071 migrationSHA `e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3`; оба roots по203 физических Geo files. Credentials/hexid и DB backups в Git отсутствуют.

Штатные команды из candidate: `START.cmd`, `STATUS.cmd`, `STOP.cmd`. Они вызывают contained `tools/Runtime072.ps1`, который проверяет exact own binding и whole artifact hashes. Настроенный runtime поставляется в остановленном состоянии с честным gameplay статусом PARTIAL.
