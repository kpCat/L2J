# Git, SQL, безопасность

- Единственная постоянная ветка разработки `feature/phantom-world`. Baseline HEAD `9a4495b9dbe24f53cfe7357d43be801fd2067512`, fresh fetch/check required. Main `D:\Codex\repos\L2J-phantom-world` хранит user dirt; safe source `D:\Codex\repos\L2J-learning-004`, TEST `D:\Codex\repos\L2J-learning-006` проверяются отдельно. Никаких новых постоянных WIP, clone/worktree, merge в master.
- Явно разрешены scoped commit и обычный non-force push только в `origin/feature/phantom-world`. Без `reset --hard`, `clean`, mass stash, `force push`, `git add .`, rename current branch. Ff-only TEST sync после проверки owner/untracked и сверк исходников.
- Точные source allowlist девять **существующих** путей из TASK §5; менять как можно меньше. Task/report/readback/ROADMAP допускаются отдельно. Никаких других Java/Powershell/SQL/config без owner permission.
- Предварительно выполнить outside-checkout binary backup changed source with hashes; не публиковать модель/пароли/private SQL/actors/Geo. Исходные 013/014 outputs immutable.
- Private TEST base: acceptedTEST2, DRAFT14, Adam903/223, apps48, gradient16, pointer12, 183SQL rows SHA `9a9c2d0ee9a2b8f37da5dd6bcf625334b76be96fe9b76e22490ed20b4da70e7d`. Если разрешён live TRAIN, точно отделить новые законные SQL rows от исторических, не требовать ложного равенства всего SQL после нового записанного credit. Old rows must stay exact.
- No production ON, no MAIN/PLAY, no release057–061, no migration, no broad SQL, no reward/adapters/schema changes, no unstaged secrets. No model auto-promotion. 3 GS/60min only when G3 PASS. 
- Preserve native ownership, Geo203/protected207, no new scheduler/parallel actor threads. Stop worker and MariaDB with existing cooperative lifecycle and held receipt; verify ports. If owner identity ambiguous, fail closed.
- Preserve full Russian UTF-8; run actual mojibake full marker check and escaped Cyrillic/XML check, report results.
- Final independent remote readback of HEAD/parent/compare paths/full relevant bodies and evidence. Not only `git push` stdout.
