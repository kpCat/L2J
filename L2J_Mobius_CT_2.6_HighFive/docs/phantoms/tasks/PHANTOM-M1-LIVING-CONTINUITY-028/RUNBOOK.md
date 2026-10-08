# RUNBOOK028

1. Набор baseline фиксировать по identity/visibility до наград. Первые4..8 natural
   actors после фиксированного60с окна; selection seed/location/reason сохранить.
   Одну pre-baseline setup teleport synthetic разрешено, только factual anchor,
   штатные Teleport guards; не PREPARE_M1_ENVELOPE. Не перебирать споты по результатам.
2. Прямой прочитанный builder/runner028 максимум4 PS entrypoints:
   Prepare028, Control028, Observe028, Verify028; один Contract028Observer.java,
   один small helper для crash-free proof если уже есть не писать заново.
   existing SQL/ownership helpers import/reuse; не цеплять ещё `.Replace`-уровень.
3. До запуска планировать 525с absolute TTL. Фарм: setup<=60 + observation380 +
   stop/restore45=485, reserve40. Начальный clock — успешный native START, не baseline.
   Away/return отдельно, сумма бюджета<=480. Heartbeat5с, максимальная пауза<30.
   Команд<=350 (server cap400), не command на каждый sample.5starts/JVM неизменно.
4. Heartbeat arm сразу после START без ожидания ручного подтверждения; owner
   writer имеет bounded stop/finally. Фиксировать monotonic identity freshness.
5. Enrollment/discovery one-shot mailbox; дальше full-native per1–2с.
   Observer hooks не делают filesystem/SQL под Player/owner/DB locks; existing queue
   exporter cap и overflow контролируются. Полный World scan не требуется на каждый tick.
6. На UNCERTAIN сохранить все request/claim/result/heartbeat/session и first stack.
   `proposals/mailbox_audit.py` только читает. RESULT_PUBLISHED не означает SUCCEEDED;
   ACCEPTED не означает ARRIVED. Не resubmit unknown MOVE, ни прежним ни новым UUID.
   Read-only native sample можно продолжать, если session factual alive, но control
   gate не считать разрешённым пока exact outcome не установлен. Gaps не маскировать.
7. Away endpoints выбрать существующими native locality predicates. Путь actual dry,
   оба направления, intermediate geoZ, шаги<=300 в пределах MOVE400XY/200Z.
   После каждого принятого MOVE ждать factual arrival по telemetry, не spam commands.
   Во время AWAY/RETURN observer движение закономерно; stationary-scene assert не применять.
8. Не обещать2actors в старой зоне после законного background relocation. Фиксировать
   exact canonical transitions и demand;>=1 preselected возвратившийся actor нужен
   для положительного remat proof. Не запрещать фоновое развитие ради теста.
9. В конце stop synthetic по существующему exact control, доказать cleanup; затем
   stock shutdown027, immutable seals→SQL, process stops. Safe wait не растягивать
   сверхdeadline и не stop shared executor до accepted lifecycle completion.
10. Никакого blanket alive-сохранения stale данных. После release фон мог измениться:
    сравнивать seal at transaction boundary, далее lineage, а не позднюю строку SQL
    с ранним snapshot. Подтверждения old raw7/8 не переименовывать в8/8.

Утром MORNING.md должен содержать tested exact SHA/JAR/runtime/clone start command,
продуктовые OPEN gates и один next observable endpoint. По умолчанию JVM STOPPED.
Не задавать пользователю вопросы среди ночи, ответы уже описаны в TASK/RUNBOOK.
