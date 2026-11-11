# Границы безопасности

- Задача пишется только в независимый clone `D:\Codex\worktrees\phantom-learning-003-source` либо собственный альтернативный exact путь. Пользовательский `D:\Codex\repos\L2J-phantom-world` READ ONLY. Никаких force/stash/clean/reset/rebase чужих веток.
- Новая ветка `wip/phantom-learning-003` от exact `b7b5575457c12c1b0438bcbaa646675a67778391`. Push только сюда. `feature/phantom-world`, TASK130, LEARN001, LEARN002 refs и их runtime сохранять.
- Только High Five. Не менять штатные таблицы Skills/Items/NPC/EXP/SP/PK/Karma/Rewards ради обучения. Не зачислять fake Native reward. Не выдавать модель 4–8 МиБ за мастера игры по объёму.
- Изолированная собственная TEST MariaDB, свои порты и native actors. Использовать уже проверенную изоляцию 002 без повторной реализации. Живые аккаунты/профили пользователя не изменять. Исходные Geo203 и protected207 только читать/проверять SHA.
- Нейросеть может предлагать действие только внутри уже разрешённой зоны owner. Critical heal/rescue и status immunities сохраняются, при ошибке — fallback, а не risk skip.
- Train/DRAFT/ACCEPTED_TEST/PROD separate. Пока 003 не закончена, боевые решения на основном сервере не меняются. No secret/config credentials in Git/export. Unmanaged Player heal unchanged.
- Если представление неизвестного эффекта, скрытого PvP-инвентаря или «победы» требует угадывания — показывать UNKNOWN и не учить. Нет live pathfinding или LLM API в game hot loop.
- Каждый новый процесс/SQL принадлежит собственному TRAIN003 и завершён штатным STOP; всегда записать точные pid/FILETIME, SHA, порты, фактическое состояние и незакрытые blockers.
- Технические решения по snapshot, training и perf можно принимать самостоятельно, но фундаментальные изменения правил игры или опасный compromise владельцев требуют согласования; в остальных случаях не просить пользователя вручную чинить окружение.
