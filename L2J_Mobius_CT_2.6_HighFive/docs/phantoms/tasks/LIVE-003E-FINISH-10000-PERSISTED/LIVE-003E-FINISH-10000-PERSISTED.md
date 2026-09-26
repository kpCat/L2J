# LIVE-003E-FINISH-10000-PERSISTED — отчёт

## Статус

**GREEN — LIVE-003 FUNCTIONAL SCALE 10000 COMPLETE**.

## Результат и измерения

Сохранённая PLAY population продолжила рост с 9352 managed / 9350 linked и достигла 10000 managed / 10000 linked / 10000 READY при 10000 уникальных именах и аккаунтах. GameServer был готов в 23:35:00 +03:00; target подтверждён в 23:43:37, в пределах 20-минутного окна. Post-target soak завершён в 23:58:39, спустя более 15 минут. Финальная штатная остановка сохранила все 10000 профилей и закрыла owned ports.

В ряду из 27 samples: heap 81,49–92,53% от 4096 MiB, максимум одна подряд точка выше 90%, threads 160–167, DB connections 13–15/151, fatal/OOM markers 0, дубликаты identities 0. Background.state/catchup вырос с 8961 до 9986, ecology — с 9352 до 10000. Последние 14 background positions остаются незавершёнными; во время soak background продвигался.

## Scope и архитектура

Изменены только task artifacts: `EVIDENCE.md`, `STATE.md`, этот отчёт и `LIVE003E_RUNTIME_10000.tsv`. Подробные runtime logs находятся в `.phantom-local/logs/LIVE-003E-FINISH-10000-PERSISTED/`. Production source, build, tests, схема БД, private budgets и public contracts не менялись. Новых архитектурных решений и migrations нет. Прямых PLAY DML/DDL не было.

## Команды и проверки

- Исходные read-only проверки branch/HEAD/status/upstream подтвердили `feature/phantom-world`, обязательный HEAD и посторонние ранее существовавшие изменения рабочего дерева.
- Использованы штатные `Check-LocalPlay.ps1`, `Start-LocalPlay.ps1 -Background`, `Stop-LocalPlay.ps1`. До запуска и после остановки обе роли STOPPED, `staleRecord=False`, порты закрыты. Во время работы Login PID 24308 владел 2106/9014, Game PID 23084 — 7777.
- Read-only PLAY SELECT/SHOW для baseline, минутных samples и post-stop snapshot подтвердили counts, identities, background/ecology и DB capacity. Runtime telemetry получена через JDK `jcmd GC.heap_info`, thread counts, ownership ports и поиск fatal markers.
- Новые тесты не добавлялись и не запускались: runtime-only задача проверена требуемым targeted live gate. `git diff --check`, exact-path review, commit и push отражены в финальном сообщении по задаче.

## Ограничения и история

Данные подтверждают функциональную сходимость и стабильность этой сохранённой population. Старый throughput threshold ими не подтверждён. **LIVE-003D 45-minute ramp performance gate remains NOT MET; no throughput optimization was attempted in LIVE-003E.** Причина исторического промаха не заявляется. Работа над LIVE-004/005 и vision audit не начиналась.

## Git

Ветка: `feature/phantom-world`. SHA task commit и результат push указаны в финальном сообщении после exact-path commit.
