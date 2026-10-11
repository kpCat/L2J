# TASK037 — непрерывная игра естественной группы

Новый Codex-диалог: **GPT-6.1 Sol / Very High, без субагентов**.
Required base: `0b47eea99c0d7d6190abc1eaa6de70996ca6dba9`.
Repository: `kpCat/L2J`, только `L2J_Mobius_CT_2.6_HighFive`.
Push: `experiment/m1-candidate007-observe008`.

Распаковать в `C:\Users\ZBook\L2J_Mobius`, отправить отдельный /goal.
Исполнять в уже зарегистрированном `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.
Основной checkout только read. Новый worktree не создавать; существующий не удалять.

Цель: исправить подтверждённые причины прекращения полезной native-игры группы,
а не повторно расследовать успешный cast27. Связанные in-scope исправления разрешены
до принятой границы; один обнаруженный guard не означает автоматический STOP.

Чтение: GOAL → TASK → HANDOFF036 → SOURCE_FACTS → DESIGN → PATCH_GUIDE → PLAN →
SCENARIOS/ACCEPTANCE → RUNBOOK/GIT/SOURCE_MAP. Проверить MANIFEST до импорта.
Код в tools — read-only индексатор исходных данных; он НЕ является новым evaluator.

Важное различие: FULL_SAVE и COMPLETE_LINEAGE_REQUIRED — разные утверждения.
Последний флаг в оригинальном continuity028.py выставляется при отсутствии/смерти
или нескольких сегментах. Он сам по себе не проверяет SQL и не доказывает потерю данных.

Ориентир 3–5 часов, hard max 6 часов, semantic freeze 4 ч 30 м.
Финал этого задания: GROUP_CONTINUITY, не весь M1. M1=OPEN, REAL_FINAL=NOT_RUN.
