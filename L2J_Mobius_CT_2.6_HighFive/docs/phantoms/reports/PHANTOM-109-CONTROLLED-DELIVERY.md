# PHANTOM-109-CONTROLLED-DELIVERY — PARTIAL / STOP

01.11.2026, Europe/Chisinau. FULL109=PARTIAL. Подготовлено самостоятельно собираемое минимальное Party PvE подмножество от baseline `a1b30f7b84a9c06e1141a0cae7a2e48dd922bf24`. Публикация разрешена текущим запросом только после всех delivery guards; фактические commit/push фиксируются отдельными receipts и финальным ответом. Полная TASK109 не объявляется успешной.

## Точная поставка

Все пути относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` — согласовать champion DPS с native HP/regen; рассчитывать оставшееся время боя по текущему HP. Native Geo/LOS, lease, MP, incoming-risk и предел60s сохранены.
2. `java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java` — только чистый `projectedFarmDuration`; acquisition eligibility и изменение pull-пути исключены.
3. `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java` — только generic all-Phantom GENERAL_PVE cold recovery с exact group/generation/manifest/objective/member/rowVersion и сохранением собственного operation. Support-role exclusion исключён; реальные участники/consent и прежняя farmRecovery ветка сохранены.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java` — один существующий focused three-member cold-restart regression, без остальных WIP hunks.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomDeliveryParty109Checks.java` — извлечённые существующие projection/champion-unit contracts без Native/Lane/C2 зависимостей.
6. `docs/phantoms/reports/PHANTOM-109-CONTROLLED-DELIVERY.md` — этот новый отчёт.

Другие2377 production source files совпадают по содержимому с baseline. Проверены все2380 identities; штатные checkout LF/CRLF различия учтены отдельно, не выданы за product delta. Новых библиотек, SQL/schema/config/build.xml, UI, policy или timeout изменений нет. STORE/pickup/earned-admission код сохранён из baseline; он не является новым delta этой поставки.

## Dependency graph и native совместимость

`Backend.groupViable -> Backend.nativeDamagePerSecond -> AutonomousFarmParty.projectedFarmDuration` использует прежние combat estimates, native champion divisor, HP и regen. `Coordinator.commitObserved` использует только прежние PartyState/claim/CAS/invitation contracts. Compile или runtime зависимости от новых Farming/PvP/Social API отсутствуют.

Изучены отчёты TEST109j–p, узкие source pins j/k/l/m/o и полные production identities n/p; проверены настоящие n/p exit/log/stderr/inputs/JAR/completion SHA bindings. Из2380 production sources n→p изменён только Backend; все10 текущих production WIP совпали с p. Изолированный кандидат не равен полному p или n.

| Evidence boundary | Факт и предел применения |
|---|---|
| j | Исторические Party PvE/rewards, stock pickup13+6, STORE и restart; C1 bounded PASS. Не доказательство произвольного нового среза. |
| k | Реальные warning/kill/PK1/karma720; fatal learned/derived STORE checker, restart NOT_RUN. |
| l | Pre-cut supply отказ, exit1; gameplay/restart NOT_RUN. Численный false operand не восстанавливается. |
| m/n | Native proactive PK, stock STORE/SQL и положительные PK/karma в новой JVM; n Party3 recovery. C2 NOT_RUN_UNSAFE. |
| o | Совместный маршрут не завершён; последующее исправление champion units не подтверждается старым JAR o. |
| p | B/F_PvE, earned rewards, inventory/acquired skills/Party/STORE/SQL и чистые exit0 подтверждены. Native PVE_QUIET FAIL; C1 NOT_RUN, D NOT_RUN_UNSAFE; C2 не запускалась. |

Релевантный Backend кандидата побайтно равен p; projection/recovery участки совпадают точно. В sealed p joint-протоколе hostile Player загружается только после B/quiet. До этого места p Native не дошла; Restart выполняет PvE и возвращается. Поэтому исключённый support/defense код не обеспечивал измеренный B/F. Обычные `farm.background` goals не требуют acquisition additions; same-Party sharing исключает rare escalation, karma0 сохраняет baseline STORE путь. Source/receipt pins и исключённые ветви рассмотрены независимым reviewer.

Это обоснование совместимости ограниченного p PvE пути с минимальным срезом, а не native запуск нового JAR. **Actual candidate native=NOT_RUN.** Candidate GameServer SHA256: `e38bd31b0613716aff9ee1e609b7b8a071b44e3ce0dd9275fdd8d480e591fafc`. Historical p GameServer: `c7c77c3d0fb94eec6990ad3b292f8f96dd9d7a839c04e5e8c4d818d5d9080291`. Эти JAR не объявляются одинаковыми.

## Исключённые блокирующие зависимости

- Proactive PK из настоящего farm/acquisition root требует согласованного Farming rare admission, acquisition Party eligibility и безопасного native STORE с ненулевой karma. TEST109n возвращала временный acquire.item в прежние party.form/join; STORE уходил в `nativeOnlyStore(UNSUPPORTED_LINEAGE)` до изменяемого karma refusal. Поэтому положительная n persistence не подтверждает новый supported-goal karma capture. Эти2 karma hunks, Farming и acquisition additions исключены; новый PK delivery PASS не заявлен.
- Party defense при занятости PvE и переход обратно к цели не доказаны p. Quiet guard не обходился; failure-time counters не записаны и задним числом не восстанавливаются. Независимый support-role exclusion не переносится.
- Отдельный paid Heal C2 не прошёл: incidental historical heal, paid buffs B и static readiness его не заменяют. Новый heal-функционал не заявлен.
- Revenge discovery `Context -> Bridge.revengeCandidates -> Social.recentMemories` — отдельная необязательная ветвь без достаточного native покрытия; исключена целиком. Включение source в старый JAR не равно покрытию.

В итоге публикуется только совместимый минимальный PvE/recovery delta поверх уже принятого pickup/STORE baseline. Более широкая поставка ограничена перечисленными зависимостями; новых QA/native кампаний для их закрытия не создавалось.

## Проверки

Одна независимая Ant invocation `ant -f <candidate>/L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests jar`: exit0,2380 production/450 test sources,46s, прежние warnings. Ant штатно компилирует production повторно внутри этого invocation; это не две отдельные сборки. Старые compiled classes не использовались. Выходы только в candidate build/dist; исходная поставка не менялась.

Один affected DB-free набор, все8 процессов exit0: projection/units; baseline STORE lineage4positive/7negative и persistence-context5; baseline pickup; Party lifecycle23/23, state-recovery14/14, tactics9/9; combat-core68/68; background-model10/10. Новые test frameworks/проекты не создавались. Native/SQL/JVM-server/БД не запускались; `ant verify` не выполнялся.

Независимое exact source review `/root/delivery_review`: P1=0/P2=0 для этих3 production и2 tests; рекомендация scoped публикации после final guards. Это не FULL109 и не проверка оставшегося WIP. Exact patch, source manifest, compiler/runner argv/exits, review, text/scope/preservation и publication receipts находятся в credential-free `controlled109` evidence текущего чата.

Offline verifier сначала неверно считал, что все Git blobs используют LF, затем что все совпадают с raw checkout. Подтверждена смесь исторических LF/CRLF и штатная checkout-конверсия. Исправлена только классификация verifier, product bytes/сборка/tests не менялись. Финальная матрица: baseline→candidate3 содержательных изменения; p→candidate9 содержательных различий и42 EOL-only. Никакого сокрытия source delta нормализацией не было.

- mojibake-маркеры в изменённых файлах проверены: полный27-marker набор, отдельный final receipt;
- escaped Cyrillic в изменённых файлах проверены: все6 regex, отдельный final receipt.

## Сохранность и публикация

47 исходных WIP/source файлов сохранены побайтно вне checkout с SHA256. Исходные TEST109–p, JAR/DB/evidence и исторические task packages не переписывались. Сверены использованные immutable receipts и входы; новый полный150k-file historical audit не запускался. ASU-MKR не открывался. БД не подключались, новый live SQL clearance не заявлен.

Для интеграции переиспользован прежний safe-delivery паттерн: отдельный `GIT_INDEX_FILE`, `read-tree <baseline>`, `--work-tree=<candidate> add -- <exact6>`, staged scope/blob/text/whitespace guards, один commit в `feature/phantom-world`, затем `update-index --add --cacheinfo <exact6>` основного index без замены WIP файлов. Новый test и отчёт материализуются только на ранее отсутствовавших путях. После commit основной checkout намеренно сохраняет полный WIP и не считается проверенным production snapshot; собираемая версия — isolated candidate/committed tree.

Git reads, разрешённые AGENTS и текущим запросом: `rev-parse --show-toplevel --git-dir --git-common-dir HEAD refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`; `symbolic-ref --short HEAD`; `status --short`; `worktree list --porcelain`; `ls-tree -r <baseline> -- <module>/java`; exact-path `diff`, `diff --numstat`, `diff --check`; `ls-remote --exit-code origin refs/heads/feature/phantom-world`. Для owner-created worktree использовался только разовый `-c safe.directory=<candidate>`, global config не менялся. Managed worktree создан native `create_worktree` от exact baseline по прямому запросу изоляции.

Final publication commands: `read-tree <baseline>` только во внешнем index; `--work-tree=<candidate> add -- <exact6>`; `diff --cached --name-only`, `diff --cached --raw --no-abbrev -- <exact6>`, `diff --cached --check -- <exact6>`, `diff --cached --numstat -- <exact6>`; `--work-tree=<candidate> commit --file <external-message>` один раз; `rev-parse HEAD HEAD^`; `ls-tree -r HEAD -- <exact6>`; `update-index --add --cacheinfo <mode,blob,path>` только exact6; `push origin HEAD:refs/heads/feature/phantom-world`; final `ls-remote --exit-code origin refs/heads/feature/phantom-world`. Полные exact argv/paths/exits записываются в commit/push-command receipts, разрешение — текущий /goal.

Protected branches, force/reset/stash/rebase/restore/clean, WIP deletion и native continuation не выполнялись. Публикация других веток запрещена. Managed candidate оставлен для review с pinned baseline; его файлы не заменяют полный исходный WIP. После финального результата — STOP. TEST109q/r и TASK110 не запускать.
