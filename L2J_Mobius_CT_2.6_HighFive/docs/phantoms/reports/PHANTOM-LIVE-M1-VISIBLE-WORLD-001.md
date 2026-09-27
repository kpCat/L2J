# PHANTOM-LIVE-M1-VISIBLE-WORLD-001

## Status

BLOCKED — `M1_SELECTED_TRACE_DISABLED_ORDER_CONFLICT`.

## Summary

Read-first и два безопасных Pilot snapshot выполнены при исходном HEAD `7872e18fbf37177cf08ab23f342a25e3742bb1d0` на `feature/phantom-world`. Effective LocalPlay diagnostics=False; обязательный selected trace до production fix недоступен. Два snapshot показали admitted profile 6 в 50 world units от TestAdmin, но `materialized=false`. Это unresolved observation, не client pop-in proof и не idle root cause.

## Changed files

Только task-owned `EVIDENCE.md`, `STATE.md`, `IDLE_ROOT_CAUSE.md`, `RESULT.md`, `M1_VISIBLE_SCENE.tsv`, `MATERIALIZATION_MEASUREMENT.tsv` и этот отчёт. Исходные task package документы включаются в exact-path staging только как исходный пакет M1. Production/test/config файлы не изменены.

## Architecture decisions

Сохранены topology → locality → scheduler → native materialization и существующий selected trace. Новый diagnostic/QA слой и speculative visible farm goal не добавлены. Native `World.SHIFT_BY=11` задаёт регионы 2048×2048 и соседнюю область известных объектов; единый радиус видимости не найден. Bounded topology query остаётся правильной основой будущего измерения.

## DB, config, commands, tests, performance

PLAY DML/DDL и migrations: нет. Конфиги и budgets не менялись. `Check-LocalPlay.ps1`: CONFIG PASS, 10000/64/128, pulse 100, LoginServer и GameServer RUNNING. `Get-LocalPlayPilot.ps1`: valid ARMED_IDLE binding; Pilot read-only `SNAPSHOT_PHANTOMS` в 15:44:44Z и 15:45:36Z; `Stop-LocalPlayPilot.ps1` остановил каждый run. Один промежуточный snapshot был REJECTED `SESSION_OR_DEADLINE` из-за нового runId при ещё активном run; штатный stop восстановил ARMED_IDLE, после чего snapshot прошёл. RED/GREEN, Ant, clean detached jar и product smoke не запускались: production behavior не редактировался. Performance/visible UX не измерены.

## Deviations, limitations, risks

Фаза A не может завершиться без selected trace. GM-only `AdminPhantom` неприменим к non-GM TestAdmin, Pilot trace operation отсутствует. Диагностический restart до RED/GREEN конфликтует с порядком TASK; production fix без точной причины запрещён. Пользовательских client actions было 0 из лимита 2. Pilot runner остановлен, здоровый LocalPlay сохранён RUNNING. Ни один GREEN claim не сделан.

## Git and next step

Branch: `feature/phantom-world`. Initial HEAD: `7872e18fbf37177cf08ab23f342a25e3742bb1d0`. Commit SHA и push result: записать после exact-path commit/push. Следующий шаг возможен только после явной поправки M1-порядка для диагностического restart до RED/GREEN и разрешённого non-GM доступа к существующему selected trace. M2 не начат.
