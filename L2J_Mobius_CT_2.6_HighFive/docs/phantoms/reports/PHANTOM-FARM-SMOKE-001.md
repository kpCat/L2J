# PHANTOM-FARM-SMOKE-001 — BLOCKED_SMOKE

21.11.2026. Решение NO-GO принято на P0 менее чем за 2 минуты от старта. Причина `P0_R4_ATTESTATION_UNVERIFIABLE`.

- В `TASK.md` пин `n01-scenes.tsv` состоит из 63 символов. В `TASK131-RESULT.json` указан другой, 64-символьный пин. Хеш оригинала сверить не удалось.
- `Get-FileHash` вернул `UnauthorizedAccessException` на оригиналах `n01-fixture.tsv` и `n01-scenes.tsv` из `n01-setup-r4/sql-task131`. Для `n01-scene-scope.properties` получен `FileNotFoundException`. Текущий `PhantomCombat131Fixture.prepare` создаёт scope только при полном наборе сцен, а отчёт TASK131 фиксирует в r4 три из четырёх. `PhantomLearning131Native.fixture` требует оригинальный scope и lane вида `n01-setup-rN`.
- Обязательная исходная аттестация профиля 11 / PHYSICAL / класс 2 / уровень 52 / NPC 20667 не состоялась. Идентичность, ранги, anchor и поколения актёра и цели не подтверждены.

## Приёмка

| Gate | Результат | Доказательство |
| --- | --- | --- |
| S0 | FAIL | Невозможно сверить r4 и получить отдельный допуск. SHA источников диагностики приведены в [JSON](PHANTOM-FARM-SMOKE-001-RESULT.json). |
| S1 | UNKNOWN | Актёр и цель в TEST не наблюдались. |
| S2 | UNKNOWN | Native cast/attack, урон и расход не наблюдались. |
| S3 | UNKNOWN | Death, kill credit, EXP/SP, STORE и durable delta не наблюдались. |
| S4 | UNKNOWN | Следующая цель не наблюдалась. |
| S5 | UNKNOWN | `smoke131-one` отсутствует, снимок ОС показал 0 TCP listeners на 33331/33332/33333. Protected pins и identity 3308 повторно не аттестовались. |

SQL Start/Attest/Stop, SETUP, combat, GS и LS — 0. Сборка, regression и negative controls — `NOT_RUN`. Новая TEST lane не создавалась. Общая MariaDB, MAIN/PLAY, protected207, Geo203, training10, checkpoint216 и latch Goal024 в этой задаче не открывались и не менялись. Studio WIP сохранён. N01 и статус PARTIAL131 без изменений.

## Источники и область изменений

Проверен весь пакет задачи, включая все 6 хешей из `MANIFEST.json`. Прочитаны корневой и модульный `AGENTS.md`, релевантные части README, master plan, TASK_PACKAGE_STANDARD, workflow contract, current status, TASK131 handoff/JSON и точечные участки SQL lifecycle, fixture, native admission и AutoPlay owner.

Изменения ограничены этим файлом и `docs/phantoms/reports/PHANTOM-FARM-SMOKE-001-RESULT.json` внутри High Five. Исходный GitHub HEAD ветки `feature/phantom-world` подтверждён запросом коммита по имени ветки. Локальный HEAD совпал. Сетевой `git ls-remote` вернул ошибку соединения. [Исходный коммит на GitHub](https://github.com/kpCat/L2J/commit/0d2ac555dfde3b4b1e5ab2fd6cfeca834e769d7e).
