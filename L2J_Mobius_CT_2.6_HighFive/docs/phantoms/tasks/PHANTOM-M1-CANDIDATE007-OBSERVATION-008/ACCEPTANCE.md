# Acceptance008: честное наблюдение текущего кандидата

## A. Достоверность запуска — обязательна

A1. codeSHA — experimental frozen snapshot candidate007;103 integrated artifact hashes
сверены. JAR создан из чистого source, runtime загружает именно этот JAR, не старый MAIN.
A2. Login/Game направлены только в разрешённую cloned DB; PLAY/TEST не исправлялись.
A3. Исходный runtime сохранён/возвращён по RUNBOOK; никаких чужих PID/файлов не изменено.
A4. Настоящий TestAdmin подтверждён server-side REAL_LOGIN/World/GameClient IN_GAME;
UI screenshot относится к его клиенту на этом observation GameServer.

## B. Что должно быть в отчёте, даже если не работает

Для каждой строки: OBSERVED_PASS / OBSERVED_FAIL / NOT_OBSERVED / NOT_REACHED,
exact profile/object/epoch, timestamp, ссылка на кадр и/или server evidence.

- materialization/видимость и число настоящих Player, не NPC FakePlayer;
- локальное перемещение и target acquisition;
- обычный удар либо offensive cast, подтверждённый native damage;
- kill → положенная EXP/SP/reward → следующий самостоятельный target;
- ground pickup или AUTO_LOOT, когда это применимо;
- кластеризация, вода/стены, idle/stuck и существующее recovery;
- естественная смерть/восстановление, если произошли;
- уход/возврат REAL, retention/demat/remat с правильным различением epoch;
- первый runtime exception/closed admission/owned-store failure, если наблюдались.

Требуется10–15 минут валидного клиентского эпизода, не только startup screenshot.
Если до gameplay не дошли из-за runtime/transport, сохраняется частичный отчёт BLOCKED.
Четыре наблюдаемых участника — цель объёма, не условие скрыть пустой мир: отсутствие
cohort само по себе является результатом. Исходный roster не сокращать ради PASS.

## C. AUTOPLAY_5_CYCLES

Один native headless Phantom Player, непрерывный интервал на одном object/epoch,
сам выбирает допустимого живого моба → подходит/кастует → наносит damage → убивает →
получает положенную штатную награду → выбирает следующую цель. Повторить5 раз,
без вмешательства оператора в его действия/цели/HP/items/skills/EXP. После пятого kill
нужен переход к следующему target, а не только остановка на счётчике убийств.

Сильное подтверждение: fresh counter baseline, overflow=false, same owner/epoch,
рост nativeFarmCycleSequence не менее 5 с согласованными damage/kill/reward/target
и EXP/SP delta плюс клиентские кадры/видео нескольких последовательных действий.
Если счётчик непригоден для данной штатной награды, независимая timeline пяти циклов
допустима только при фактически имеющихся событиях; не реконструировать пропущенное.
Один моб может возродиться с прежним objectId: различать смерти по времени/incarnation,
не объявлять5 циклов только по5 одинаковым снимкам.

Loot: подтвердить хотя бы один реально доступный штатный drop/подбор, если он был.
Для автолута отсутствие похода к предмету нормально; нужен native inventory/reward факт.
Если drop не выпал или не был применим, LOOT=NOT_OBSERVED/NOT_APPLICABLE_WITH_REASON,
не менять RNG/rates и не выдавать предмет ради PASS. Это не закрывает общий loot gate M1.

Если не было materialization/admission — AUTOPLAY=NOT_REACHED, а не «stock не умеет».
Если actor действует, но5 циклов не достигнуто — FAIL или NOT_OBSERVED по evidence.
Пятицикловый PASS доказывает нынешний native path с hooks007, не чистый upstream без них.

## D. Выход

OFF и отсутствие active Pilot run подтверждены сервером либо честно отмечена потеря
подтверждения при crash. Experimental processes остановлены. Исходный PLAY при его
остановке снова запущен с прежними файлами. Secret artifacts не попали в publication.
No production semantic diff после source freeze; tooling diff строго ограничен.

## E. Итоговые статусы

TASK_RESULT=GREEN: выполнено валидное наблюдение, evidence и cleanup/publication,
даже если RUNTIME_BEHAVIOR=FAIL. Никакой надписи «GREEN M1».
TASK_RESULT=BLOCKED: scope/time/environment не дали провести необходимый эпизод.
TASK_RESULT=FAILED: ошибка исполнения/проверки изоляции/невосстановленный исходный runtime.
RUNTIME_BEHAVIOR отдельно PASS/PARTIAL/FAIL/NOT_OBSERVED; AUTOPLAY_5_CYCLES отдельно.
M1=OPEN во всех случаях. Следующий fix назначает координатор после проверки результата.

Один удачный профиль, отсутствие натуральной смерти и короткий soft-return не закрывают
всю persistence/restart/death/background вертикаль M1. Полный WORLD139 и old synthetic№5
не являются ни prerequisites, ни разрешёнными запусками этой наблюдательной задачи.
