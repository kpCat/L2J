# TASK058 — RESULT (кампания продолжается)

Статус: IN_PROGRESS. Начало 16.10.2026 11:18:49 UTC; потолок 17.10.2026 01:18:49 UTC, резерв доставки с 23:48:49 UTC либо раньше по измерению.
OWN=`D:\Codex\worktrees\phantom-dev\L2J_Mobius`; BASE=`039d1dcb8b702285e4d0eaffbadec2cf9274d24b`; публикация `experiment/m1-candidate007-observe008`.
М1 остаётся OPEN; разрешение продолжать М2–М7 с отложенными G4/G6/личной приёмкой записано в RELEASE_CONTRACT. Старые FAIL не переписаны.
Владелец штатно остановил057 во время кампании; это ожидаемое состояние.057 не запускать, release_057a не изменять.

| Этап | Реализация | Серверная проверка | Владелец |
|---|---|---|---|
| М2 | PARTIAL: request/reply, native invitations, real-led buff/heal/recharge/res, durable mixed party | Native role matrix3/3; follow/assist/full sequence/current JAR/restart ещё открыты | QA_PENDING |
| М3 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М4 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М5 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М6 | NOT_STARTED | NOT_STARTED | QA_PENDING |
| М7 | NOT_STARTED | NOT_STARTED | QA_PENDING |

Подготовка: утверждённый пакет отсутствовал в OWN и найден в MAIN; шесть SHA256 совпали, семь файлов точно скопированы в OWN. MAIN только читался.
Добавлен test-only exact058t lane: фиксированные OWN/runtime/schema/owner/role, pool4, запрет второго lane, schema fingerprint. Кumulative058a/release и старые schemas исключены.
Bootstrap переиспользует StrictSqlScriptRunner и tracked SQL inventory; создаёт только отсутствующую058t, не меняет users/grants, не делает reset/drop. Установлено121 scripts, schema SHA `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`.
Focused ownership RED2/3→GREEN3/3. File-based guard PASS без DB connection. Native baseline сначала RED на пропущенной058 CWD ветке; адресная адаптация helper→GREEN10/10.
Ordinary DB-free harness controls66/66.

- mojibake-маркеры в изменённых исходниках/документах проверены отдельно: PASS;
- escaped Cyrillic в изменённых исходниках/документах проверены отдельно: PASS.

Diff whitespace проверен с разовым `git -c core.whitespace=cr-at-eol diff --check`: в существующих Java сохранены Windows CRLF; repo config не менялся.
Native Party проверил реальные invitation/expiry/terminal identity, save/reload, transfer/expel/leave, managed callbacks и Rift provider; это не полный companion gameplay и не клиентский вход.
Ownership: exact058t identity/manifest/CWD/catalog проверяются до fixture writes; старые lanes не расширены.
Логи: `.phantom-local/logs/TASK058`; приватный checkpoint и матрица: `.phantom-local/contract058a`.
Сборки test runner:17s и18s; targeted javac helper exit0. Два старых javac warnings System.runFinalization вне scope сохранены.
М2 request: штатный ingress/actual delivery «го кач?»→один durable proposal к speaker; неадресный GENERAL получает managed delivery, но не recruiting. Corpus добавляет «го пати», «пойдём вместе» и три отрицания;248 cases,16 intents.
RED: доставленное обращение не имело executable proposal. Далее выявлены закрытый parser intent-set и codec dictionary. Добавлен ровно один intent; compact dictionary только дополнен в конце, прежние индексы/schema1 сохранены. Existing coverage counts обновлены на шесть явно добавленных примеров.
GREEN: companion-request0582/2; semantic corpus3/3, intents3/3, context3/3, pack3/3; conversation catalog/codec2/2 и understanding2/2. XML DOCTYPE error — ожидаемый negative control, suites exit0.
Self-review: точный speaker/typed TARGET_PLAYER slot и прежний election/cooldown; generated social recursion не расширена; no autoaccept за человека. Девять связанных code/data/test paths — bounded scope по TASK058§7; общий framework не добавлен.
Готового058 candidate пока нет; далее нужны обратное приглашение, actual FARM/earned takeover и companion play/support/lifecycle.
Native invitation composition1/1 использует реальные stores/backend, зарегистрированный PartyDecision и DecisionEngine, durable goal sync, native chat SENT, explicit TEST accept, native Party и persisted roster: Phantom1/TEST real-kind1. Не является actual owner client login; не проверяет follow/support.
Неправильный setup без progression owner сохранён INVALID_INPUT; исправлен штатным L2jProgressionBackend. Test cleanup теперь сохраняет первичную ошибку и обрабатывает свои terminal callbacks. Await ждёт все объявленные факты без изменения часов/правил. Native composition и affected request2/2 завершились с exit0/штатным закрытием JVM/pool.
Ordinary FARM goal takeover: causal RED (goal.busy, goalsSubmitted0, native invitation отсутствует)→GREEN1/1. L2j port разрешает только party.invite поверх валидного ACTIVE farm.background; существующие goal/execution SQL CAS и runtime sync переиспользованы. Committed party goal negative сохранён. Native combat/earned при самом takeover ещё не измерен: исходная FARM цель — объявленный fixture contract.
Affected execution/ownership/consent controls7/7; существующий normal doCast/finalizer E01 контроль1/1 (focus=diagnostic, cast(false,false), без fault/crash/abort/drain-pending режимов). Оба процесса завершились штатно, exit0. Никаких GM-выдач в product, новых DB permissions, timers или изменения rates.
Current status получил ссылку на owner-forward amendment без изменения исторической сентябрьской матрицы. Прямое human→Phantom приглашение: causal RED→GREEN1/1 через существующий ManagedInvitationPolicy; настроенный social refusal проверен native, neutral acceptance сохраняет человека-лидера, исходную FARM-цель и durable mixed roster. Строгие XML/ownership contracts5/5, affected native Party10/10.
Self-review: только текущий свободный materialized recipient, валидный ACTIVE FARM либо отсутствие цели; social authority обязательна. Нет нового контроллера, goal writer, autoaccept за человека или ослабления native legality.
Real-led reconcile RED→GREEN: наблюдение через managed member, существующие roster/leader commit и tactics; native buff349:1 на human, MP907.4646→847.4646, штатный leave освобождает pin. Declared TEST class100/level85/XP/base class/learned skill, не natural career. Pure tactics2/2, support policy5/5. Первый full-MP cost assertion FAIL сохранён; последующий input с MP headroom проверен без подпитки/отмены во время measurement. Полная role matrix/follow/assist/partychat/current JAR/restart остаются открытыми.
Role matrix: existing native mechanism verification3/3 на штатной starting point с current learned/ready authority. Heal1335:1 (class97) HP≈145→611; recharge1013:32 (class105) MP≈27→183; res1016:9 (class97) actual native request→explicit TEST answer→HP452/stand-up. MP caster во всех случаях уменьшился; IDs/skill/исходные HP/MP/XYZ записаны до episode. Прежняя попытка на старой combat point: смерть wounded TEST и две readiness failures сохранены как FAIL; новая исходная точка — отдельный episode, не спасение старого. Поддержка под атакой пока не доказана.
Mixed lifecycle RED→GREEN: Phantom-led native party с человеком после обычного leave освобождает claim/pin; только ACTIVE core party goal с совпавшим operation goalId становится ABANDONED с новой ревизией. Final focused1/1, real-led personal FARM negative1/1, native support affected1/1, native Party affected10/10. Cleanup сверяет актуальные group/generation, снимает exact scoped lease и не удаляет runtime новой generation; partial SQL CAS и native effects не объявлены общей транзакцией. Полный handoff/multi-Phantom lifecycle и earned-work composition ещё нужны.
Git разрешён TASK058: exact status/rev-parse/log/diff/check, exact add/commit и normal non-force push. Опубликованы `754e83259631a823fd658b3cbdccd92ba52c42a1`, `488953dd26cd7fa7bf326a3c793a432d85cb14bb`, `dfde6df948eccb9c34765d067b1b6fd564e36b4d`, `7fe0b4279a7fa0e89196565277991bdda958f471`, `9ae1b7e09b694591e0cae8109134868bc6b8e4cc`, `afbac4b6757824baab040bb8fb74408eff771670`, `0461650ecf928ddb02ad6e88183ccf215e663ff1`; remote equality подтверждена. Mixed lifecycle готовится к публикации.
