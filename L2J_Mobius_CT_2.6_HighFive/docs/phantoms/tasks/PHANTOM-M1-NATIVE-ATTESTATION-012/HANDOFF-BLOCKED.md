# HANDOFF012 — BLOCKED_OTHER, M1 OPEN

Остановлено до подтверждённого focused RED. Исходный inventory/autoGet mismatch
не классифицирован; production fix отсутствует; observe012 не запускался.

Native TEST infrastructure остановлена. Guarded TEST full restore отказал на
`seven_signs_festival`; journal сохранён. Imported 10000-profile aggregate остаётся
в guarded TEST. Не запускать новые DB tests, не создавать новый before-image,
не удалять journal и не ослаблять restore/b4 guards.

Для продолжения требуется отдельно ограниченная recovery-задача: атрибутировать
10 cycle4 festival rows относительно retained exact before-image, разрешить
проверяемый exact restore без расширения general guard и подтвердить исходный full
TEST aggregate через штатный PopulationFixture restore. Эти изменения затрагивают
test infrastructure вне task012 SOURCE_MAP; в task012 не выполнены.

После принятой recovery task012 всё ещё требует настоящий native TEST. Profile68
в исходном PLAY snapshot DEAD, поэтому сохранённый диагностический patch нельзя
применять без пересмотра READY-only selector. Использовать допустимые factual
READY/DEAD prerequisites или existing runtime-observed fixture path, не выдумывать
inventory/skill state. Затем выполнить I1/A1/A2/A3/B1 и только при доказанных premises
решать autoGet-only normalization. Существующий preflight STORE не должен скрывать drift.

Production scope остаётся только PhantomBackgroundService.java. Все engineering
и manual runtime gates исходного задания остаются REQUIRED. M1=OPEN.
