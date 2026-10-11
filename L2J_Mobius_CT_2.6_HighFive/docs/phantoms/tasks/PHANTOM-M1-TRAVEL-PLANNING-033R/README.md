# TASK033R — планировать путь, а не дробить прямую

Исполнение2026-10-11: **PARTIAL**, M1=OPEN, REAL_FINAL=NOT_RUN.
Реальный clean R3 obstacle route → native arrival/farm →5 consecutive cycles PASS;
93/93 и lifecycle11/11 PASS. RETURN_GK runtime chain, whole-cohort lineage и
whole-system drain не закрыты. Полный результат: [RESULT_HANDOFF033R.md](RESULT_HANDOFF033R.md),
границы source review: [SOURCE_REVIEW033R.md](SOURCE_REVIEW033R.md), cleanup: [CLEANUP033R.md](CLEANUP033R.md).

Этот пакет ЗАМЕНЯЕТ ещё не запущенный PHANTOM-M1-NATIVE-ROUTE-033.
Старый TASK033 не продолжать и не объединять его STOP/allowlist с новым.
Исходный planning archive создавал только эту папку; исполненные11 source/test
paths перечислены в FINAL_SCOPE_GUARD.tsv.

Base: `df71140e659670e2e3d225605f64689a6d32d281`; branch: `experiment/m1-candidate007-observe008`.
Новый Codex-чат, GPT-6.1 Sol, Very High, без субагентов.
Пользователь утвердил пересмотр через отправку GOAL из этого пакета.

Порядок чтения: TASK → DESIGN → PATCH_GUIDE → SOURCE_MAP → PLAN → SCENARIOS → RUNBOOK.
SOURCES разделяет реально прочитанное от выводов/предлагаемого решения.
proposals/BoundedTravelSearch033R.java — проверяемый справочный поиск по ГОТОВЫМ
валидированным рёбрам. Это не готовый GeoEngine и не доказательство runtime-пути.

Цель: materialized Phantom выбирает достижимый пеший/транспортный план, обходит
препятствие настоящими native-действиями и после прибытия самостоятельно фармит.
Это не полный closeout M1: старые незакрытые persistence/away/crash gates не закрываются
по наличию хорошего маршрута. Реальная клиентская приёмка остаётся отдельной.
