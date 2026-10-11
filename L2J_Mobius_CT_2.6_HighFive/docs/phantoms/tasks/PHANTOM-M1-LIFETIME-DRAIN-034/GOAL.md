# GOAL034 — causal lifetime/drain closure

**Repository:** `kpCat/L2J`; **module:** `L2J_Mobius_CT_2.6_HighFive`; **required base:** `44139ca556ad3d5ac35309d5fe222269d3ada256`; **remote branch:** `experiment/m1-candidate007-observe008`.

**Model:** GPT-6.1 Sol, Very High; новый чат; один агент без субагентов. TASK033R уже закончен, текущий worktree удалён; его raw evidence находится вне worktree. Сопутствующий обычный основной checkout пользователя неприкосновенен.

**Цель этой задачи:** выяснить для каждого global retained incident предыдущей natural сцены фактический producer, объект/epoch, первый failure и ownership, доказать baseline-vs-new; если источник исправим в разрешённом lifecycle scope — минимально исправить и получить честный clean drain на *новом* подтверждённом Java-кандидате. Никакого SQL-heal, подмены мировых объектов, принудительного снятия чужих leases или освобождения native owner с незавершённой работой.

**Не цель:** весь M1; не делать новый world navigation, GK/RETURN, 2 sameDB restart, crash72/73, полный away/return либо искусственное улучшение continuity. `M1=OPEN`, `REAL_FINAL=NOT_RUN` независимо от TASK034 GREEN; TASK034 GREEN означает только честное закрытие данного slice.

**Источник:** `RESULT_HANDOFF033R.md`, `PROGRESS033R.md`, изолированно сохранённые raw evidence в `C:\Users\ZBook\.codex\visualizations\2026\10\10\01a127b2-1e6e-7f12-9af8-3f3748fe3047\TASK033R-review`. Проверить manifest SHA до доверия архиву; Git report и native raw — разные источники. Если сырой архив недоступен — честно `INPUT_MISSING`, без выдумывания данных.

**Начинай с доказательств, затем RED→GREEN, короткого real runtime и обязательных 93/93+11/11 только после source freeze.** Несколько непосредственно связанных правок разрешены; масштабные переписывания запрещены. Фактическая причина может быть старой/наследованной и не относиться к сцене, не предполагай обратного.

**Срок:** ориентир 2–4 часа, hard max 5 часов wall-clock. К 45-й минуте — read-first и конкретные id/первый guard; первый собственный GameServer (если необходим) до 60-й минуты. После 4 часов semantic freeze, последние 30 минут — cleanup+commit+normal push. Не более 4 стартов GameServer и 3 production Jar-builds на всю задачу; не более двух полных 380s сцен. После двух одинаковых отказов без новых данных — изменить probe или закончить BLOCKED_CAUSE, не повторять запуск.

**Операции Git:** только разрешённые `GIT.md`, exact-path commit/push при GREEN/PARTIAL/BLOCKED/FAILED. No force/merge/rebase/reset/clean/prune. При удалённом HEAD, отличном от required base: BLOCKED_BASE, не продолжать на другом состоянии без согласования.

**Финал:** `RESULT_HANDOFF034.md`, `CLEANUP034.md`, evidence index/hash/private-boundary, проверка local=remote SHA, own Game/Login/Synthetic JVM STOPPED, подписанный результат `GREEN_CAUSAL`, `PARTIAL_VERIFIED`, `BLOCKED` либо `FAILED`. Следующую задачу автоматически не начинать.

**Bootstrapping:** after read-only packet verification under `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-LIFETIME-DRAIN-034`, create/reuse own detached worktree. The remote base predates TASK034 docs, so copy ONLY the exact contents of this packet (as authorized by manifest) from user main into the matching task directory under own worktree. Verify SHA-256 of every copied file against MANIFEST; if collision or mismatch, BLOCKED_PACKET. Main remains read-only. Commit imported task-doc files only in OWN worktree.
