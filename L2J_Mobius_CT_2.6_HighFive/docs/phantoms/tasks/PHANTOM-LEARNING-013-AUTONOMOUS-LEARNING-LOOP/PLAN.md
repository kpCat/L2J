# PLAN013 — bounded, offline-first, максимум двух прямых блокеров

| Этап | Работа | Переход |
|---|---|---|
| A | Fresh branch/AGENTS, TEST safety, identity/SQL/Adam/chkpt, проверить manual012 | G0 или STOP |
| B | Аудит `Training.ps1` vs V2 protocol, freeze/fit/selected/optimizer, реальная достаточность TRAIN/CONTROL, честный n | Точная diagnosis matrix |
| C | RED→GREEN versioned cycle state, split fence, stop decision и V2 integration без GS | Focused PASS |
| D | Typed read-only replay012 и преднамеренные штатные ошибки; cold resume, duplicate/timeout/owner guards | PASS |
| E | Native preflight Solo/Party, train controls/ACTUAL acquired, frozen V2 evaluator, один GS с несколькими эпизодами | только если ready |
| F | One real autonomous cohort→fit→V2 evaluation→checkpoint. Следующая cohort автоматически планируется; smoke STOP/RESUME и статус | ≥1 completed cycle с собственными receipts |
| G | Trusted best/no improvement decision, SQL cold readback, own process STOP, targeted suites, GitHub push/readback | G0–G8 |

## Acceptance execution

**Сначала восстановить и протестировать control plane на immutable historical evidence**, но он не заменяет новый native13. Дорогие запуски только при готовом lawful TRAIN dataset и независимом EVAL plan. Native budget 4GS/80min total: ориентир 2 collection/fit cycles в одном supervised native запуске, 1 EVAL и 1 reserve; реальный порядок/стоимость обосновать заранее, не обещать 2 полных fit при недостаточности samples. Закрывать собственные GameServer/SQL после каждого owned stage; не создавать новый клон.

**Минимум FULL013:** один новый **реальный** полный цикл с законным набором, настоящим применённым fit (optimizer updates и exact SQL), штатным V2 EVAL, durable checkpoint/decision, автоматическим переходом к следующему плану/пауза и доказанным STOP/RESUME. Если второй цикл не завершён — явно написать это; `24hReady=false`. Если оператор хочет доказательство длительного unattended, это отдельное последующее испытание.

**Дорогая EVAL:** минимум один независимый новый V2 balanced Solo+Party набор. Если отсутствуют permitted actors/MP/Geo/Party — не запускать, диагностический PARTIAL. Не заменять провал настоящей EVAL прежними R1 012 receipts.

**Чистые блокеры:** в рамках одного Goal допускается до двух непосредственных исправлений на доказанном causal path. Третий unrelated blocker или необходимость нового objective/owner/schema — остановиться с diagnosis/вариантами, не раздувать сутки Codex. Один ранний короткий PLAN approval по root AGENTS, потом рутинно автономно.

**После каждого долгого шага** сжатый статус: текущая фаза, TRAIN data, selected vs terminal, GS costs, следующий gate, промежуточный PARTIAL отдельно. Не устраивать перманентный `ant verify` после одного комментария в docs.
