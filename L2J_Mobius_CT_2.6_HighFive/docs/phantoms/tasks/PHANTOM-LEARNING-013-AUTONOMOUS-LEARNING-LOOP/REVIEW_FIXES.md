# Независимое review013 и единый проход исправлений

Reviewer review013, gpt-6-astra/xhigh, fresh context, read-only. Native readiness пока NO. Targeted Ant013/012/011/009/008/007/JAR exit0 не заменяет native acceptance.

Четыре Important findings приняты:

1. Per-campaign budget допускает reset013; child2/35 ограничен на вызов, не суммарно. Нужен общий installation-level ledger4/80 и child subset2/35, durable nonce reservation, idempotent charge.
2. RESUME DECIDE/CHECKPOINT доверяет journal.lastV2 без повторного typed raw readback и exact evidence hashes.
3. Terminal patience/regression gate находится до незавершённого CHECKPOINT, crash после DECIDE не восстанавливает completed artifact.
4. Historical physical fence строится по текущей bounded странице, а должен по проверенным историческим snapshots до source caps/deduplication.

Дополнительно: historical replay отвергнут automatic approval, потому что verify-eval013 вызывает writer publish. Заменить на pure exact-byte comparison существующих V2 encode/pairJson/statistics/campaign APIs. Старые012 evidence не писать.

Ruling: это дефекты ещё не принятой реализации явно утверждённых B/C/D contracts (budget/recovery/evidence/split), а не дополнительные unrelated runtime blockers. Завершаем эти исходно согласованные contracts в одном review fix pass внутри9из10путей. Reviewer не обнаружил необходимости нового слоя, SQL/reward/model schema, production owner или расширения allowlist. Бюджет дополнительных внешних/native causal blockers не расширяется; если исправление потребует неразрешённого контракта, применяется STOP/gate. Цена ошибочного ruling — нарушение bounded-process scope; поэтому каждый finding получает focused regression, native пока0.

Required tests: новый campaign не обнуляет общий spent; duplicate charge не увеличивает spent; третий child start/child cumulative35min отказаны; checkpoint recovery приpatience3 не теряет completed artifact; изменённый pairs/raw evidence fail-closed; другой actorId с той же исторической physical fingerprint не допускается в TRAIN; genuine012 replay остаётся read-only. После единого fix pass — targeted regression без повторного reviewer dispatch.

Declined to judge reviewer: реальные lawful TRAIN/V2 сцены; native SQL crash и historical parity; фактическое качество/ресурсы/80min; GUI/Designer/GUI-close lifecycle; сцены вне bounded page; comparison-to-best остаётся UNKNOWN. Эти пункты остаются explicit acceptance work/NOT_RUN и не объявлены PASS по review.

Git review был bounded read-only: rev-parse --show-toplevel; rev-parse HEAD; status --short --branch; diff -- exact9source. Commit/push reviewer не делал.
