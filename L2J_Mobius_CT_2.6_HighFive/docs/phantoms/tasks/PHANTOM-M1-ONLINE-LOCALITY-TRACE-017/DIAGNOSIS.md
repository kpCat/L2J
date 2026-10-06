# TASK017 diagnostic cohort

TASK016 product fix принят. Все 392 watched LOCAL_CANDIDATE были OFFLINE,
тогда как HUMAN_REFRESH_SUMMARY показал 9 final candidates после online/prewarm
фильтрации. READY_PRESENCE_OFFLINE profile110 не доказывает blocker этих девяти.

Root cause: recorder.watch в topology predicate выполняется без online guard.
Исправление ограничено diagnostic admission; return online остаётся прежним.
Финальный physical-demand watch уже выполняется для accepted candidates.

Scope: один production файл и один DB-free test suite. Bounded artifact exception:
supplied TASK017 package, runtime helpers и evidence находятся в одной artifact
family TASK017; число документов может превышать 10. Другие подсистемы не меняются.

Remote required base подтверждён: 0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7.
Local experiment branch занята foreign dirty worktree. Повторён TASK016 workflow:
isolated detached worktree, exact-path commit, normal HEAD:experiment push.
Основной checkout и чужие worktrees не изменяются.

M1=OPEN. Runtime trace и manual gates пока pending. Automatic continuation=false.
