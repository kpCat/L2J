# План выполнения TASK014

База: `ccacd6c5bf8fa1234a5559ece708ee3a35efd536`.
Рабочая область: `C:/Users/ZBook/.codex/worktrees/m1-causal-014/L2J_Mobius`.
Локальная ветка `codex/m1-causal-014`; normal push `HEAD:experiment/m1-candidate007-observe008`.
Experiment ref занят другим worktree; его checkout/ref не изменяется.

- [x] A: карта реального critical path и матрица 29 относящихся commits из 73 GOOD→BAD; до production edits. `git diff --name-only -- .../java` пуст; history causality UNPROVEN.
- [ ] B: bounded observation-only recorder, hooks, consented Pilot operations, focused tests и `ant -q jar`; exact-path commit/push.
- [ ] C: fresh observe014, 1280/8/8/10000, diagnostics, REAL TestAdmin AutoAttach, Synthetic=False, GM startup flags=False, bounded JFR.
- [ ] Ручной gate: сервер готов → запрос входа → только после «в игре» проверка IN_GAME/REAL_LOGIN/ARMED_IDLE.
- [ ] BEGIN → 90 секунд → SNAPSHOT/END; один FIRST_LOST_EDGE без gameplay fixes.
- [ ] D: <=12 suspect commits по доказанному edge, текстовая JFR summary, RESULT/HANDOFF.
- [ ] Ручной gate: character select → online=0 и сохранение level/exp/sp/x/y/z → graceful stop, без force.
- [ ] Exact-path evidence commit/push. M1=OPEN, без continuation.

Bounded exception к лимиту 8–10 файлов: одна диагностическая цепочка, не несколько product families. SOURCE_MAP + необходимые Pilot protocol enum, consent revoke и test registration/runner wiring; task014 документы и scripts. Новые библиотеки, schema, gameplay/state decisions и cleanup вне scope запрещены.

Риски проверки: disabled/no session; ring wrap и конкурентный late writer; max8/корреляция; consent revoke/expiry; отсутствие extra gameplay reads или DB/file I/O в hooks. Тесты закрывают эти recorder contracts. Runtime проверяет реальную цепочку, не заменяется unit tests.
