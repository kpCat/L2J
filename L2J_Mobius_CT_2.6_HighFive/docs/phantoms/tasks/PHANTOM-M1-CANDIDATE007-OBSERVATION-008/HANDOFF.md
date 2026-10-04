# Observation008 — конечный handoff

TASK_RESULT=BLOCKED; RUNTIME_BEHAVIOR=NOT_OBSERVED;
AUTOPLAY_5_CYCLES=NOT_OBSERVED; M1=OPEN.

Frozen103 source entries — snapshot `dd58a512c4cb9c6a5318d7320633a35ae849dbf0`,
ветка `experiment/m1-candidate007-observe008`, worktree
`C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius`.
Полный результат и hashes: [RESULT](RESULT.md), [CANDIDATE_SOURCE](CANDIDATE_SOURCE.tsv).

Private runtime/dump/stacks/logs сохранены в module `.phantom-local/observe008`.
DB `l2jmobiush5_localplay_observe008` сохранена на127.0.0.1:3308.
Original PLAY STOPPED как до задачи,304 file hashes unchanged; guarded TEST не трогался.
Experimental Login3064/Game328 остановлены по отдельному разрешению пользователя,
gracefulShutdown=FAIL. Consent OFF/no active run подтверждены до stop.
External Native.cs восстановлен точно. Client14380 не вошёл в мир; force client не применялся.

Получены только build/isolation/setup facts и два просмотренных Login кадра.
Ни cohort, ни gameplay, ни пять циклов не наблюдались. WORLD/verify/historical suites,
synthetic, ручные действия за фантомов, seed/DB corrections не выполнялись.

Ближайший вопрос координатору: какой уже поддержанный безопасный native input transport
позволит доставлять ввод в этот l2.exe? Не менять AI/guard/goal.runtime ради setup.
Нового fix/прогона не начинать автоматически. Это завершённый отчёт bounded попытки,
а не ENGINEERING_GREEN, M1 PASS или принятие candidate007.
