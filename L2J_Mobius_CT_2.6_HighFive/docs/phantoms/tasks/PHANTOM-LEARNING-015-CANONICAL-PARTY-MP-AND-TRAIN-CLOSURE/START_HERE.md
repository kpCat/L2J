# PHANTOM-LEARNING-015 — начинать здесь

**Новый диалог Codex; Extra High.** Отдельное задание №9 из 13; после выдачи остаётся **4** до обязательного пересмотра бюджета. Не продолжать 014.

## Единственная основная ветка
- Репозиторий: `https://github.com/kpCat/L2J`.
- Baseline на момент подготовки: `origin/feature/phantom-world` = `9a4495b9dbe24f53cfe7357d43be801fd2067512`. Сверить свежий HEAD перед работой; при сдвиге провести scoped audit, не затирать новую работу.
- Main checkout владельца: `D:\Codex\repos\L2J-phantom-world` (содержит исторический dirt; защищён).
- Безопасный source: `D:\Codex\repos\L2J-learning-004` (проверить чистоту/actual ref).
- TEST: `D:\Codex\repos\L2J-learning-006` с приватным `.phantom-local/learning002/train`.
- Модуль: `L2J_Mobius_CT_2.6_HighFive`.

## Порядок чтения
`TASK.md` → `PRE_AUDIT.md` → `CANONICAL_MP_CONTRACT.md` → `PIPELINE.md` → `PLAN.md` → `TEST_MATRIX.md` → `ACCEPTANCE.md` → `GIT_AND_PRESERVATION.md` → `HANDOFF.md`.

**Суть:** не повторять головную ошибку 013/014: выждать полное естественное восстановление ресурсов, а только потом доказать, что Party изначально не могла пройти штатную проверку MP. Не копировать формулу проверки из production, не подменять текущий MP будущим и не ослаблять родные правила. Проверка непригодности — доказательная и fail-open-to-native при UNKNOWN.

Сначала разработать и проверить безопасный необходимый read-only прогноз в каноническом Party-источнике. Затем измерить отказ на том же exact сценарии без ожидания восстановления. Только после DB-free PASS — ограниченная headless readiness. GameServer — только после полного seal TRAIN. Больше ночных ожиданий ради статистики, не подтверждающей admissibility, не должно быть.
