# TASK058 — RESULT (кампания продолжается)

Статус: IN_PROGRESS. Начало 16.10.2026 11:18:49 UTC; потолок 17.10.2026 01:18:49 UTC, резерв доставки с 23:48:49 UTC либо раньше по измерению.
OWN=`D:\Codex\worktrees\phantom-dev\L2J_Mobius`; BASE=`039d1dcb8b702285e4d0eaffbadec2cf9274d24b`; публикация `experiment/m1-candidate007-observe008`.
М1 остаётся OPEN; разрешение продолжать М2–М7 с отложенными G4/G6/личной приёмкой записано в RELEASE_CONTRACT. Старые FAIL не переписаны.
Владелец штатно остановил057 во время кампании; это ожидаемое состояние.057 не запускать, release_057a не изменять.

| Этап | Реализация | Серверная проверка | Владелец |
|---|---|---|---|
| М2 | PARTIAL: доставленные request/reply, native invite/TEST accept, durable mixed party | Request2/2, invitation composition1/1, Party baseline10/10; полного S2 пока нет | QA_PENDING |
| М3 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М4 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М5 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М6 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М7 | NOT_STARTED | NOT_STARTED | QA_PENDING |

Подготовка: утверждённый пакет отсутствовал в OWN и найден в MAIN; шесть SHA256 совпали, семь файлов точно скопированы в OWN. MAIN только читался.
Добавлен test-only exact058t lane: фиксированные OWN/runtime/schema/owner/role, pool4, запрет второго lane, schema fingerprint. Кumulative058a/release и старые schemas исключены.
Bootstrap переиспользует StrictSqlScriptRunner и tracked SQL inventory; создаёт только отсутствующую058t, не меняет users/grants, не делает reset/drop. Установлено121 scripts, schema SHA `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`.
Focused ownership RED2/3→GREEN3/3. File-based guard PASS без DB connection. Native baseline сначала RED на пропущенной058 CWD ветке; адресная адаптация helper→GREEN10/10.
Ordinary DB-free harness controls66/66. Mojibake-маркеры в изменённых файлах проверены отдельно: PASS. Escaped Cyrillic в изменённых файлах проверены отдельно: PASS.
Diff whitespace проверен с разовым `git -c core.whitespace=cr-at-eol diff --check`: в существующих Java сохранены Windows CRLF; repo config не менялся.
Native Party проверил реальные invitation/expiry/terminal identity, save/reload, transfer/expel/leave, managed callbacks и Rift provider; это не полный companion gameplay и не клиентский вход.
Self-review: exact058t identity/manifest/CWD/catalog проверяются до fixture writes; старые lanes не расширены. Product-код пока не менялся.
Логи: `.phantom-local/logs/TASK058`; приватный checkpoint и матрица: `.phantom-local/contract058a`.
Сборки test runner:17s и18s; targeted javac helper exit0. Два старых javac warnings System.runFinalization вне scope сохранены.
М2 request: штатный ingress/actual delivery «го кач?»→один durable proposal к speaker; неадресный GENERAL получает managed delivery, но не recruiting. Corpus добавляет «го пати», «пойдём вместе» и три отрицания;248 cases,16 intents.
RED: доставленное обращение не имело executable proposal. Далее выявлены закрытый parser intent-set и codec dictionary. Добавлен ровно один intent; compact dictionary только дополнен в конце, прежние индексы/schema1 сохранены. Existing coverage counts обновлены на шесть явно добавленных примеров.
GREEN: companion-request0582/2; semantic corpus3/3, intents3/3, context3/3, pack3/3; conversation catalog/codec2/2 и understanding2/2. XML DOCTYPE error — ожидаемый negative control, suites exit0.
Self-review: точный speaker/typed TARGET_PLAYER slot и прежний election/cooldown; generated social recursion не расширена; no autoaccept за человека. Девять связанных code/data/test paths — bounded scope по TASK058§7; общий framework не добавлен.
Готового058 candidate пока нет; далее нужны обычный FARM takeover, обратное приглашение, actual companion play/support/lifecycle.
Native invitation composition1/1 использует реальные stores/backend, зарегистрированный PartyDecision и DecisionEngine, durable goal sync, native chat SENT, explicit TEST accept, native Party и persisted roster: Phantom1/TEST real-kind1. Не является actual owner client login; не проверяет follow/support.
Неправильный setup без progression owner сохранён INVALID_INPUT; исправлен штатным L2jProgressionBackend. Test cleanup теперь сохраняет первичную ошибку и обрабатывает свои terminal callbacks. Await ждёт все объявленные факты без изменения часов/правил. Native composition и affected request2/2 завершились с exit0/штатным закрытием JVM/pool.
Следующие разрывы выявлены по исходникам: supersession обычной ACTIVE FARM цели отклоняется; прямое human→Phantom приглашение без заранее созданной JOIN цели остаётся pending. Проверить причинно и исправить перед полным М2.
Git разрешён TASK058: exact status/rev-parse/log/diff/check, exact add/commit, normal non-force push. Опубликованы `754e83259631a823fd658b3cbdccd92ba52c42a1` и `488953dd26cd7fa7bf326a3c793a432d85cb14bb`; remote equality подтверждена. Invitation composition coverage готовится к публикации.
