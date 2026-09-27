# PHANTOM-LIVE-M1-VISIBLE-WORLD-001

## Status

**BLOCKED — `M1_CONNECTED_MATERIALIZATION_ENVELOPE_UNPROVEN`.** Root-cause, native visible-life and focused automated materialization proofs есть; connected first-prewarm/exit/re-entry через native visibility boundary нет. `GREEN — M1 VISIBLE WORLD PROVEN` не заявлен.

## Root cause and change

При исходном HEAD `7872e18fbf37177cf08ab23f342a25e3742bb1d0` на `feature/phantom-world` естественно видимый profile 123 дал selected trace `farm.background FAILED/TERMINAL`. Код возвращал `FAIL_GOAL` после успешного death recovery, а alive visible farm не имел кандидата. Исторический recovery event самого profile 123 не сохранился (`recorded=0`); последняя `activity.generation_changed` не объявлена первопричиной. Focused RED: materialization 30 PASS/1 FAIL, recovery 1 PASS/3 FAIL, visible candidate 3 PASS/1 FAIL. Fix возобновляет только доказанный failed farm goal, добавляет обычный visible candidate и использует существующий AutoPlay/AutoUse native micro-loop с отдельным phantom admission; macro goal/policy остаются Phantom. Глобальный `.play` не включён, background simulation не запускает AutoPlay/GeoEngine. Native World region prewarm и 10-секундный exit grace закрыли automated materialization contract.

Владелец сообщил о множественных self-heal. Instant SELF skills оказались допущены в повторяемый AutoUse buff path; RED 5 PASS/1 FAIL и GREEN 6/6 после admission только continuous SELF buffs. Конкретный skillId/HP виденного владельцем phantom не снят. Молчание на general chat сохранено для будущего M2/M3. Причина melee у отдельных магов не установлена.

## Verification and connected evidence

Focused GREEN: background decision 5/5 до self-heal уточнения и 6/6 после, recovery 4/4, topology 32/32, scheduler signal 5/5, selected trace 8/8. `git diff --check` был чистым. Code commits/push: `53047c4f4e57270b8cc4fb65f11f020ebbe263a3`, `561c84a2dc2`. Последний clean detached `ant jar` SHA-256 `C868989EB12C656861D4D85BABEC43BB136C1646041CFC753C5CA968A18C9467` был развёрнут после exact backup; исходный runtime jar backup сохранён в `artifacts/local-play/m1-autoheal-backup-20260927`.

Первый connected Pilot выбрал естественный profile 2941: `farm.background ACTIVE → candidate.background.farm → background.visible.autoplay_started SUCCESS`, native monster target 268478028 и `visibleAttacking=true`. Третий разрешённый arm на последнем JAR выбрал естественный profile 1348 с тем же chain; `visibleAutoPlaying=true`, AI CAST с native monster targets 268478025/268478029, live position сменилась с (-90875,248162) на (-90814,248019). Profile 692 на отдельном мгновенном снимке был между планами (`plan.total_timeout`, `NEEDS_REPLAN`) при `visibleAutoPlaying=true`; это не новый terminal-idle defect. Три USER_CLIENT_ACTION использованы с явным разрешением владельца на третье; четвёртое не запрашивалось.

Pilot маршруты с TestAdmin ограничены штатным GeoEngine guard. Во второй сессии достижима (-90531,248658); в третьей — (-90975,247862). Переходы к внешней native visibility boundary отклонены `TELEPORT_PRECONDITION`; first prewarm time/distance, exit/re-entry и client flicker не измерены. Автоматический topology 32/32 не подменяет connected acceptance. TestAdmin возвращён к исходным XY, online/worldPresent, REAL_LOGIN/non-GM; Pilot runner STOPPED. Последний `Check-LocalPlay.ps1`: CONFIG PASS, LoginServer PID 2812/GameServer PID 23188 RUNNING, Population=10000, ActiveTarget=64, MaxMaterialized=128, PulseMs=100. Ни scale gate, ни 45-action smoke не повторялись; PLAY DML/DDL и бюджеты не менялись. M2 не начат.

## Evidence files

Task `RESULT.md`, `STATE.md`, `EVIDENCE.md`, `IDLE_ROOT_CAUSE.md`, `M1_VISIBLE_SCENE.tsv`, `MATERIALIZATION_MEASUREMENT.tsv` содержат точные временные метки, RED/GREEN и границы выводов.
