# Acceptance TASK022

ENGINEERING_PASS:
- для исправленных root есть genuine RED и отрицательные owner/session/epoch tests;
- все applicable SCENARIOS проверены; исключение описано с причиной, не скрыто под общимGREEN;
- stock gameplay/earned store guards сохраняются, native manager изменения только описанной интеграции;
- golden native smoke5cycles; mandatory regressions; clean committed build;
- guarded TEST восстановлен/verified; PLAY_WRITES=0; scope/encoding verified.

FARM_PASS:
- один natural actor в одном epoch≥5полных native farm cycles с реальнымиEXP/SP;
- выбор следующей цели продолжается, callbacks/ticks реально завершаются;
- freshness доказана; нет ситуации «старый reason обозначает live running»;
- sensor не invalidated/overflowed в accepted episode; чужие действия/counters не присвоены;
- нет ручного Phantom control/спавна rewards/переброса к удобной цели;
- применимый drop обработан либо честно отмечено отсутствие drop; native loot TEST доказан;
- cohort до4 раскрыт полностью. Пять циклов одного актёра не закрывают весь M1.

CLEANUP_PASS:
- TestAdmin saved/OFF;
- owned TEST clean;
- stop exact registrations/work не теряетearned наград;
- Phantom retained entries0 и процессы/порты освобождены безforce.

TASK_RESULT=GREEN только ENGINEERING_PASS && FARM_PASS && CLEANUP_PASS.
При farm success + отдельной незакрытой drain boundary: TASK_RESULT=BLOCKED, FARM_PASS=true,
STOP_CLASS=LIFECYCLE. Сохраняем достигнутую farm-вертикаль для TASK023, а не обнуляем результат.

BLOCKED также при бюджете/needed forbidden change/неполном причинном доказательстве.
FAILED — нарушенный контракт/регрессия. Не выдавать потерянные данные за BLOCKED без описания.
M1=OPEN при любом исходе: death/soft return/background/remat/restart/cohort final ещё впереди.

Прежние оценки числа задач — ориентир, не acceptance. Ни TASK022, ни «триоставшиесязадачи»
не разрешают скрыть failing native cleanup или объявить M1CLOSED авансом.
