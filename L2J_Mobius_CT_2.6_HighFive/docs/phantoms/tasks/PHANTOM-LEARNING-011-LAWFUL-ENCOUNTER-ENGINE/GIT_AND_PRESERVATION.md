# GIT_AND_PRESERVATION011

1. ЕДИНСТВЕННАЯ постоянная ветка: `feature/phantom-world`. Точный стартовый SHA из GitHub 14.11.2026: `f153d137a7d666ea0c28586a45a429a94a9d1c44`; перед работой обязательно fetch/readback и повторная проверка.
2. Основной `D:\Codex\repos\L2J-phantom-world` имеет защищённый dirty. Без явного разрешения владельца не выполнять там переключения/fast-forward, которые затирают файлы. Проверять source `D:\Codex\repos\L2J-learning-004` и TEST `D:\Codex\repos\L2J-learning-006` на существование/владение/актуальность. Не создавать новый clone.
3. Проверить список worktrees, dirty source files, Git status и stash read-only; задачи010 уже архивировала уникальные локальные объекты. Не удалять/перезаписывать их и не повторять терабайтную архивацию без причины.
4. В рамках Goal011 разрешены bounded commits и обычный non-force push в `feature/phantom-world`, только по TASK011 source/test/docs. Публикуем не DRAFT model weights/private SQL, а исходники и безопасный evidence summary.
5. Предыдущие 010 refs сохранены: `f153d137...` feature; 009 wip `01e04537...` — историческая ветка, не base новых изменений. `master` не менять.
6. После source push — самостоятельно получить GitHub HEAD, сравнить parent/changed paths, прочитать ключевые source/test/evidence и удостовериться, что feature содержит изменения. Само сообщение `git push exit0` недостаточно.
7. Новые WIP/experiment branches, force push, clean, reset --hard, массовые stash, удаление worktree или private SQL/Geo запрещены. Любой неожиданно конфликтующий checkout => safe STOP и точный отчёт.
8. `docs/phantoms/COMBAT-AI-ROADMAP.md`: задача011 №5/13, остаётся8. PARTIAL/retry — расходует бюджет.
