# HANDOFF017 — структура отчёта Codex

В конце Goal017 (отчёт по-русски) указать:

1. `TECHNICAL_VERDICT`: FULL017/PARTIAL017/BLOCKED017 и G0–G8 отдельно, без завышения.
2. `PARTY_REWARD_ROOT_CAUSE`: таблица 17 эпизодов, 71 excluded Party action и 13 Solo; конкретные validator checks/поля и количество UNKNOWN; ссылки на source lines и immutable trace.
3. `DATA_CENSUS`: распределение по SOLO/PARTY × TRAIN/TUNING/AUDIT × stock/exploratory; planned/settled/cut/paid/terminal/exactSelection/control/gradient; реальный upper bound и недостача.
4. `FIT_READINESS`: почему 016 не может fit на неизменных данных (максимум15 gradient и7 audit даже при полном Party rescue); план будущей выборки с учётом attrition, не меняя пороги.
5. `DIRECTOR`: конкретная проверка `DONE016`, idempotent cold completion после сохранённого NO_FIT с **0 новых optimizer updates** и сохранением исходной ошибки в истории.
6. `GAMEPROOF`: сколько действительно новых нативных Party paid->terminal receipts; если 0, то почему и какие тесты это только DB-free.
7. `BUDGET`: actual starts/минуты (0 или1GS, не более20мин), все read-only расходы, CPU/RAM/disk и завершение процессов.
8. `PROTECTION`: historical SQL183 exact SHA, selected DRAFT14, terminal Adam903/223, pointer12, Geo203, protected207, MAIN/PLAY/Studio сохранены, own TEST OFF.
9. `GIT`: актуальный origin HEAD/parent, обычный push только feature, actual exact allowlist diff, GitHub full-body readback, команды.
10. `QUALITY`: исходные ошибки и новые неприятные наблюдения, что осталось неподтверждённым, нет ли shadow-training/data leakage.
11. `NEXT`: если проблема доказанно в недостаточности split, дать проект следующего *одного* измеримого учебного набора, а не запускать его. Новый Goal не начинать.
12. Счётчик: задача **№11/13**, после выдачи **2**.

Формат поставки внутри task directory: `IMPLEMENTATION_PLAN017.md`, `RESULT.md`, `EVIDENCE.json`, `ACCEPTANCE_RESULT.md`, `PARTY_REWARD_DIAGNOSIS.md`, `DATA_CENSUS.md`, `DIRECTOR_CLOSURE_RESULT.md`, `COMMANDS.md`, `READBACK.json`, `HANDOFF_RESULT.md`; разрешено сокращать, если одинаковое доказательство без потерь доступно из одного файла.

Manual gate остаётся `REQUIRED`; даже если все автоматические проверки PASS, владелец принимает результат отдельно.
