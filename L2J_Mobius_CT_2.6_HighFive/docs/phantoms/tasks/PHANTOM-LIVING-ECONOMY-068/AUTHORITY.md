# AUTHORITY068 — автономная комплексная кампания

Владелец разрешает все A–G из TASK.md, самостоятельные проектные решения внутри этих границ, связанные Java/config/XML/script/test изменения, собственные TEST/release БД и runtime, сборки, анализ логов, отладку своих процессов, штатные START/STOP, адресные commit и **normal non-force push только `feature/phantom-world`**. Не спрашивать утверждение плана, числа файлов, каждого RED/GREEN или входа владельца. Явные подтверждения безопасности самой платформы сохраняются; зависимый шаг при блокировке отложить, независимые продолжить.

## Рабочее место и Git

Единственный checkout записи: `D:\Codex\repos\L2J-phantom-world`. В скриптах вычислять фактический root, не зашивать имя пользователя. Source scope: `L2J_Mobius_CT_2.6_HighFive/`, её docs/tools/tests, при точной необходимости root `.gitignore`/`.gitattributes`. Не менять Studio/Account_Manager/прочие хроники. Более10 связанных файлов допустимо, exact list вести в отчёте. Целенаправленно править общие native seams, когда этого требует описанный lifecycle; не глобальный refactor.

Разрешены bounded rev-parse/symbolic-ref/status/worktree list/for-each-ref/ls-tree/show/log/diff/diff --check/merge-base/ls-remote/fetch origin feature/phantom-world, адресный add/commit/archive и push origin HEAD:refs/heads/feature/phantom-world. Fast-forward локальной отставшей базы допустим только при сохранном dirt и отсутствии пересечений. Новый remote descendant не игнорировать. Divergence/nonfastforward → сохранить свою работу, BLOCKED_GIT; не force/rebase.

Запрещены reset --hard, clean, массовые stash/checkout, stash pop/drop, add ./Stage All, force push, удаление refs/worktrees, изменения master/default branch. Начальный пакет068 — разрешённый входной dirt; чужие файлы не присваивать и не затирать. Изменения исторических task/report ради новых PASS запрещены.

## Среды и ресурсы

Создавать и изменять только новые068 ресурсы RUNTIME_INPUTS. Все057–067, включая066R2, MAIN/PLAY, их TEST/release schemas, клиент, C:-checkout/его stash и пользовательские файлы защищены. Старый C: может уже отсутствовать — не восстанавливать/не удалять/не требовать его для работы. Ресурсы брать из проверенного067 read-only либо другого manifest-verified защищённого источника, физически копировать с SHA, не запускать источник. Перепроверять только фактически затронутые входы, не обходить все старые базы.

Разрешены source build/JAR, свои fixture accounts/Player access0 и pre-measurement TEST inventory/levels/skills, private credentials в own runtime; читать stock metadata. Нельзя fake GameClient, вход за владельца/управление его персонажами, выдача GM/accessLevel. Native сервисный test seam допустим, но не рекламировать его как сетевую клиентскую QA. Для реальных personal checks соблюдать exception только внутри TEST, production guard exact access0/allowlist/Phantom exclusions сохраняется.

Local debugger/jcmd/JFR/thread dump — только после проверки exact своего PID/startTime/manifest, bind loopback. Не hotpatch proof-сцену, итоговые результаты на обычном собранном коде. Не менять Windows/firewall/общую MariaDB users/grants/service/datadir, не устанавливать новые зависимости. Никаких taskkill/force-kill чужих или всех Java. STOP собственных штатный; неудача → STOP_BLOCKED с PID, не ложный JVM0.

## Экономические изменения и исключения

Разрешены заранее документированные и ограниченные phantom-only bootstrap subsidies/warm-start из B, merchant role и необходимые lifecycle handoffs, nonzero configured manufacture fee, native recipes/craft и версионированные совместимые components. Старый population creation hash не менять бесконтрольно; legacy identities сохраняются. Нельзя превращать starter5000 в бесконечный stock.

Новая pricing utility по умолчанию Plan. Автоматическое применение **проверенного собственного плана только в own068 candidate/staging** разрешено: предварительно diff/preimage hashes, backup, validation и bounded anti-arbitrage check. Изменение профиля до сцены и запись его SHA обязательны. На старых/пользовательских путях Apply/Rollback запрещены. Авторские цены отличать от stock фактов. Не подкручивать шансы/HP/дроп/ресурсы посреди сцены ради PASS.

Схему own068 разрешено расширить только минимальными совместимыми migrations существующих компонентов; предпочтение versioned profile components/receipts, никакой новой универсальной economic DB. В migration не удалять/не обнулять legacy rows. Native private trades/warehouse/craft могут менять предметы и Adena только собственных068 test/merchant actors. Shared offline-таблицы ordinary игроков не очищать внедрённым phantom handler. Условные остатки/награды должны иметь единственного владельца и обработку retry. В случае недостоверного баланса — закрыть конкретную опасную операцию, не компенсировать выдачей денег.

## Эффективность и завершение

Одна основная сессия; максимум два помощника только на независимые файлы или узкое ревью. Не делегировать троим одинаковый read-set; не создавать worktrees ради формального workflow. Общие файлы и все Ant/native runs сериализовать. После compaction читать PROGRESS068 и текущий diff, не весь архив. Workflow approval вне platform уже дан этим пакетом; отдельный «утвердите spec» не нужен.

На инфраструктуру повторно не тратить часы: переиспользовать проверенные067/066R2 build/runtime guards узкими parameterized bindings. Никаких глобальных replace всех task IDs или ослабления historical schema guards. Проверять цели launcher/build.xml один раз, не угадывать названия.

Перед завершением целевое ревью final diff: duplicate owners/restore, quantity/Adena overflow/retry, price cycles, native control player, timer races. Если reviewer независимый реально недоступен, self-review назвать честно. UTF-8, отдельные mojibake/escaped Cyrillic checks и whitespace check обязательны. Бинарники/GeoData/private dumps/секреты в Git не публиковать.

Законченные блоки должны быть собраны, проверены и сохранены независимо от успеха остальных. При PARTIAL не бросать готовый код без поставки/отчёта. Итог: codeSHA, reportHEAD, remoteSHA, statuses A–G, exact candidate path, startup guide с конфигами, свои JVM/ports/writers STOPPED, non-force push. Заканчивать раньше, когда весь доступный объём выполнен; не заполнять12ч искусственными тестами.
