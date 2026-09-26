# EVIDENCE — accepted handoff

Baseline commit:
`cd6e535ea2dba24b981421ff539d614d4757863d`

Persisted handoff:
- target 10000
- LocalPlay STOPPED
- managed 9352
- linked 9350
- READY 9350
- unfinished 2
- identities 9350/9350 unique
- duplicates 0
- background.state/catchup 8961
- ecology 9352

LIVE-003D runtime was healthy enough to continue:
- 49 samples
- heap 80.32–93.75%
- maximum >90% streak = 2
- threads 160–164
- DB connections 13–15/151
- fatal/OOM 0
- duplicates 0
- background and ecology continued forward progress

Late creation rate declined versus early ramp, but creation did not stop.
No root cause is claimed here.
No throughput repair is part of LIVE-003E.

## Результат LIVE-003E — 2026-09-26, UTC+03:00

- Исходная ветка `feature/phantom-world`, HEAD `cd6e535ea2dba24b981421ff539d614d4757863d`; посторонние ранее существовавшие изменения рабочего дерева не затронуты.
- Штатный `Check-LocalPlay.ps1`: LoginServer/GameServer STOPPED, `staleRecord=False`, порты 2106/9014/7777 закрыты. Private config сохранил target=10000, active=64, materialized=128, scheduled=10000, creation in-flight=2, pulse=100 мс, boundaries=64. SHA-256 GameServer JAR остался `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`.
- Исходный read-only PLAY snapshot: 9352 managed, 9350 linked, 9350 READY, две незавершённые записи, 9350 уникальных имён и аккаунтов, дубликатов 0/0, background.state/catchup=8961, ecology=9352.
- Штатный `Start-LocalPlay.ps1 -Background`: Login PID 24308 владел портами 2106/9014; Game PID 23084 — портом 7777. GameServer записал `Started` в 23:35:00.143 и зарегистрировался на LoginServer в 23:35:00.208.
- Sample в 23:43:36 и подтверждение monitor в 23:43:37.808: 10000 managed, 10000 linked, 10000 READY, 10000 уникальных имён и аккаунтов, дубликатов и незавершённых записей 0. Это менее 8 минут 38 секунд от готовности GameServer и раньше deadline 23:55:00.
- Soak длился от подтверждения target в 23:43:37.808 до финального sample в 23:58:38 и завершения в 23:58:39.856 (не менее 15 минут). Все 17 soak samples сохранили 10000 managed/linked/READY и уникальные identities, дубликатов и fatal/OOM markers 0. Owned PID и порты оставались стабильными.
- В 27 runtime samples: heap 81,49–92,53% от 4096 MiB; максимальная серия минутных samples выше 90% — одна точка. Threads 160–167, DB connections 13–15 при максимуме 151. Background.state/catchup продвинулся 8961 → 9986, включая 9635 → 9986 после target; ecology 9352 → 10000. После паузы у 9985 background вырос до 9986; оставшиеся 14 позиций не объявляются завершёнными.
- Штатный `Stop-LocalPlay.ps1` остановил Game PID 23084 и Login PID 24308. Финальный `Check-LocalPlay.ps1`: обе роли STOPPED, `staleRecord=False`, все три порта закрыты. Финальный read-only PLAY snapshot: 10000 managed/linked/READY, 10000 уникальных имён и аккаунтов, дубликатов 0, background.state/catchup=9986, ecology=10000. Target и защищённые бюджеты не менялись.
- Полный минутный ряд: `LIVE003E_RUNTIME_10000.tsv`. Подробные SELECT snapshots и monitor logs: `.phantom-local/logs/LIVE-003E-FINISH-10000-PERSISTED/`. Выполнялись только SELECT/SHOW; прямых PLAY DML/DDL, reset/reseed/delete не было.

`GREEN — LIVE-003 FUNCTIONAL SCALE 10000 COMPLETE`

`LIVE-003D 45-minute ramp performance gate remains NOT MET; no throughput optimization was attempted in LIVE-003E.`
