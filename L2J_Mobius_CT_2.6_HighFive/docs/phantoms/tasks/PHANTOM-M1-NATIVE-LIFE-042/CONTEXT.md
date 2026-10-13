# CONTEXT042 — проверенные исходные факты

Фактический опубликованный срез 041: `docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/CURRENT_STATE041.md` commit `a5674811479d087ec7f67ca62921ffa85d298263` (2026-10-13). Прочитать исходный файл, не подменять этим конспектом.

- Полный M1 OPEN. G0 PASS. G1/G3/G4/G5/G7/G8 NOT_VERIFIED на всей acceptance boundary; G2 FAIL (одна группа PASS); G6 FAIL (72 не вооружён,73 incomplete useful continuation). Новая задача не переписывает эти факты.
- На semantic candidate `92400bebb9126ec4364ac57b05f8c453fe42690d` C24 GROUP_B 8/8 PASS, окно 381.6816272s, 366 samples, gap 1.1124117s; primaries418/426 39/36 cycles, остальные35/23/34/34/16/33. Сверь actual codeJar/evaluator/config во входных receipts.
- C8 GROUP_A2 FAIL из-за actor276, покинувшего locality/retired. Не исключать276 задним числом, не объявлять его ошибкой само по себе: выяснить легальность release vs ошибку удержания/перехода/наблюдателя. Анализировать source of human locality и epoch/instance.
- В TASK041 нет подтверждённого native→soft release→productive BACKGROUND→полезного native return того же profile. Death/useful fixture ранее PASS11/11 + отдельный actual45s, но это не заменяет G4.
- C8 closed FULL_SAVE/global drain PASS:999 births,147585 owning commits,973 stopped profiles; общий G3 ещё NOT_VERIFIED. Не собирать C8 повторно и не переносить его PASS на новые остановки.
- 041x73 два fault-off boot закончились, но `ORIGINAL_PROFILE_NO_USEFUL_RESUME:418`; 041x72 не вооружён. **В TASK042 не запускать никакие crash/recovery operations.**
- В Git включены `PhantomProfileRepository` owning-goal after-commit witness, `PhantomVisibleAutoPlay` alternative after30s real no-target без ослабления debt90, standalone start/stop/status и исходные тесты. Никаких reset/restore/rebase ради старого baseline.
- Защищённый040a в последнем снимке Game30312/Login18688, точную принадлежность проверять только read-only; эти номера **не** разрешение управлять процессами. Все041 JVM отсутствовали на момент предыдущего аудита; текущий runtime-срез проверяется заново.
- М1 ещё не разрешает запускать М2. Будущий RELEASE_CONTRACT М1–М7 сохранён; TASK042 его не переопределяет.
