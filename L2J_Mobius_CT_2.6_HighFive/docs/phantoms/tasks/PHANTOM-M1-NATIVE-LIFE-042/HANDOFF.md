# HANDOFF042 — минимальная публикация и дальнейшая работа

Результаты в `docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/`:

1. `CHECKPOINT042.md`: state/time/OWN/HEAD, exact semantic candidate/JAR/evaluator, 042a/b namespaces, chronological actual bounded attempts, no modifications outside allowlist, eventual stops.
2. `GATES042.tsv`: P0–P6 per-criterion PASS/FAIL/NOT_VERIFIED; отдельно status M1 legacy G0–G8, без преждевременного CLOSED.
3. `RESULT042.md`: в первую очередь человекочитаемый ответ «что теперь реально работает», native group IDs/cycles, однозначный S_LIFE timeline producer/commits/epochs, оставшиеся препятствия и next task recommendation.
4. `REVIEW042.md`: каждый actual semantic diff root cause/RED/fix/GREEN/negative/native; regress commands/exit codes; source ownership and risk. Если semantic source не менялся — явно указать отсутствие product edits.
5. `TASK042-review.zip`: строго ограниченная выборка source diff/typed proof/reports/manifest/hash; full raw, прежние041 ZIP/DB не репаковать. Все member CRC/SHA256 проверены на фактическом ZIP. Для больших raw оставить exact local path/SHA и export instructions.

Обычный Git `status`, `rev-parse`, exact add, ordinary commit, ordinary push; no force/reset/clean/restore/merge/branch changes; verify local HEAD=remote, stable own test stopped. Не коммитить пароли/токены/DB/raw/private profiler/JAR. UTF-8 и точечная проверка mojibake/escaped Cyrillic.

Состояние после TASK042 **всегда** `M1=OPEN`, `READY_FOR_M2=false` до отдельного закрытия G1/G3/G5/G6/G7/G8/R1/O_REAL и принятия человека. Нельзя запустить М2. Если G2/G4 прошли, следующая узкая задача — G5+G3, затем отдельное безопасное решение по G6 и конечная ручная приёмка. Если platform blocks an operation, do not retry under another disguise; report exact BLOCKED and continue safe unrelated work.
