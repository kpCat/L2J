# AUTHORITY066-R2 — автономное исполнение без новых вопросов

Владелец прямо разрешает **полностью выполнить** PHANTOM-FARM-ECONOMY-066-R2 в границах этого ZIP: read-first, точечные Java/config/test/script правки, безопасные новые R2 DB, сборки, запуск/штатная остановка собственных Login/Game JVM, лог-анализ, отладка собственных процессов, native/ordinary Main наблюдения, итоговый отчёт, commit и **non-force push** в `feature/phantom-world`.

**Не спрашивать владельца, не просить входить в игру, не ожидать подтверждения для каждой правки, теста или финального push.** После компактного read-first сразу выполнять. При недоступных platform capabilities/обязательных UI prompts честно BLOCKED_PLATFORM и продолжить независимые разрешённые шаги. Нельзя утверждать обход платформенных запросов.

## Git и область

- Git-root `D:\Codex\repos\L2J-phantom-world`; единственная ветка записи/push `feature/phantom-world`; baseline при подготовке ZIP: `5f112172f31cbf36cd3e1c0e47da48ffc77ccbde` (21.10.2026 UTC), проверить **live remote HEAD** перед mutation. Если появился descendant, принимать его без сброса, сравнить narrow diff.
- Код менять только в `L2J_Mobius_CT_2.6_HighFive/` и файлах отчёта/пакета R2. Архив распаковывается поверх рабочего корня; сохранять чужие изменения/неизвестные files. Ни одна команда не должна удалять/откатывать пользовательский dirt.
- Git: строго bounded `status`,`rev-parse`,`symbolic-ref`,`ls-remote`,`worktree list`,`diff`,`log`,`show`,`ls-tree`,`merge-base`,`archive`, exact add/commit и `push origin HEAD:refs/heads/feature/phantom-world`. **Запрещено** reset --hard/clean/stash/mass checkout/force push/rebase/cherry-pick/merge/master changes/add . /Stage All. Nonfastforward → локальный commit сохранить, BLOCKED_REMOTE_ADVANCED.
- Можно добавить более10 связанных файлов при доказанной причинной цепочке; вести exact `CHANGED_FILES-R2.txt`. Не рефакторить весь `Player`, не менять другие хроники/Studio/Designer/Account_Manager.

## Защищено и запрещено

- Защищены **все 057–066**, прежде всего `D:\PhantomWorld\Farm-Economy-066-candidate`, её `l2jmobiush5_phantom_release_066a`, `l2jmobiush5_phantom_test_066`, а также 065, GAME/MAIN/PLAY, чужие JVM/БД, worktrees, legacy `C:\Users\ZBook\L2J_Mobius` (read-only). Использовать старые resources/JAR/DB лишь read-only с подтверждённой ownership. Нельзя изменять или запускать защищённый 066 candidate.
- Не менять глобальные MariaDB accounts/users/ports/service/datadir/grants; не ставить JDK/Ant/.NET/новые библиотеки; не менять system network/firewall; не публиковать пароли/GeoData/JAR/логины/raw DB в Git.
- Не создавать fake GameClient, не управлять аккаунтом или Player владельца, не давать GM, не отключать native item ownership/party/skill/weight/commerce guards.
- Собственные процессы останавливать обычными STOP/flush/drain и проверять exact PID+start ticks+ownership. **Никаких force kill/taskkill/Stop-Process -Force, crash72/73**, отключений всех Java, непредвиденных чужих listeners. Если остановить безопасно нельзя — report BLOCKED_SHUTDOWN.
- During measured scenes не подправлять mob HP/chance/AI/position/EXP/skills/shot balances, не телепортировать bots на успешный spot и не выдавать им items/MP/Adena вручную. До измерения TEST setups явно помечены, никогда не заявлять их earned.

## Бюджет и контроль качества

- **Максимум 12 часов**, целевое практическое завершение раньше (предыдущая066 заняла6ч22). Отметка10.5ч — freeze новых функций, сборка/STOP/отчёт/non-force push. Восполнять бюджет soak или десятками повторений запрещено.
- Read-first/рабочий план максимум45мин; тестовая plumbing одной причины максимум20мин; если45мин нет подтверждённого продвижения — пересмотреть архитектуру, а не генерировать третий аналог fixture. По одной точной причине после двух RED — trace+common fix, не новый Gremlin/seed.
- Последовательность работы: этап А «starter + приоритетная покупка», этап B «native magic + background parity», этап C «ranged Geo», этап D «dwarf» и финал. Обозначения R0–R7 зарезервированы за gates из ACCEPTANCE.md. Порядок может меняться для дешёвого reuse одного committed JAR.
- Короткие focused suites; 1–3 native scenes на отличные механизмы, суммарный ordinary Main ориентир до45мин (макс75мин, включая restart), никакого многочасового ожидания. Прерывать успешный сценарий немедленно. Прежде чем строить новую TEST инфраструктуру использовать `Runtime066.ps1`, `Build-Task066.ps1`, `Assemble066.ps1` как шаблоны **только для чтения**, скопировать/адаптировать exact guards для R2.
- Сквозные живые действия (observed real native damage, money, inventory, movement) важнее totals `PASS`. Исторический G9_S1 и restart066 не гонять заново без нужды; только новая связанная жизнь / starter/supply.
- Перед финалом провести focused source self-review на точных изменениях: lifecycle/versioning, native sale/pickup and callbacks, idempotency/restart/dupes, human player regression, wrong-grade/no-stock fallback. Называть это self-review, не independent review. Сохранять UTF‑8/Cyrillic и `git diff --check`.
- `ant verify` не запускать вслепую: старые target могут менять другие БД. Explicit affected tests + clean JAR/compile-tests в собственном R2 lane. Не запускай сотни миграций/старых БД для «красивого числа».
- Автономный наблюдатель `LocalPlayPilot.ini`/существующие restricted read-only Operator snapshots, **без** попытки owner login. Настройки `PersonalCharacterQoL.ini` не используются для автоматического управления.

## Финал

Готовить `docs/phantoms/reports/PHANTOM-FARM-ECONOMY-066-R2.md`, `ACCEPTANCE-R2.tsv`, `HANDOFF-R2.md`, `CHANGED_FILES-R2.txt`, standalone candidate `D:\PhantomWorld\Farm-Economy-066-R2-candidate` с START/STATUS/STOP. Указать code commit и финальный remote HEAD, JAR/source/config/Geo SHA, точную R2-owned DB схему/порты, реальные item/Adena/shot deltas, status/blocked, список незакрытого. Коммитить и делать обычный push даже при честном PARTIAL, только безопасный working production.
