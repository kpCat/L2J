# Реализация TASK026

Goal: непрерывный farm всей группы с сохранением TASK025 invariants.
Architecture: существующий Phantom intent → stock action; local/resource recovery в
Phantom; accounting только после native writes; callbacks/recovery — exact ownership.
Execution: один Codex, inline executing-plans/TDD, без субагентов.

## Review focus
Actual point vs stand-point/неверный этаж; exhaustedMP vs hang; direct skill vs original
cast; RESERVED vs submitted callback; recovered after XYZ vs subsequent movement.
Каждый случай покрыт SCENARIOS, не оставлять эти классы вне тестов.

### 0. Read-first и замер бюджета, 0–30мин
- [ ] Verify HEAD/parent/scope. Основной checkout не менять.
- [ ] Read RESULT025/FROZEN_R4_REGRESSION_SUMMARY и обе raw final cohorts.
- [ ] Запустить proposals/classify_cohort026.py по всем16строкам, не только primaries.
- [ ] Сопоставить missing IDs с retained/dead/retired/online/schedule на той же временной
      оси. Отсутствие в World не сразу IDLE; причина отсутствия обязательна.
- [ ] Взять только нужные raw samples/dumps, ограничить консольный вывод. Зафиксировать
      первый выбранный причинный path F или E и конкретный RED. Не писать общий codemap.

### 1. Первое улучшение непрерывности, до90мин
- [ ] Native/composed RED на selected F failure: доступны реальные местные цели,
      но journey terminal/stand-point или resource policy блокируют useful action.
- [ ] Исправить внутри DESIGN; sibling regressions и before/after failure witness.
- [ ] Source commit; clean Ant build на exact commit; короткий full-server Synthetic
      probe60–120s на копии025d/c без модификации старых clones.
- [ ] Если по-прежнему red внутри F/E — использовать свежий first cause и продолжать
      разрешённый slice. До90мин не обязательно добиться PASS, обязательно достичь
      native server endpoint и знать первую проверенную границу. Не тратить сеанс
      на косметику/переписывание наблюдателя.

### 2. Связанные причины и регрессии, 90–210мин
- [ ] Разобрать N02/S12 deterministic paired. Исходный failing log сохранить.
- [ ] Закрыть exact callback publication/completion или воспроизвести ложный fixture
      отдельно; никаких предположений starvation без captured queue/locks.
- [ ] F1 и F2 проверять вместе: не чинить путь, оставляя resource loop недиагностированным.
- [ ] Периодически коротко наблюдать не только «лучшего» бота; объяснить все members.
- [ ] Повторное discovered in-scope несоответствие не требует нового чата. При трёх
      неподтверждённых теориях об ОДНОМ дефекте остановить эту ветку, сопоставить с
      DESIGN, не применять четвёртый обход. Безопасные уже решённые части сохранить.

### 3. Подтверждение и freeze, 210–270мин
- [ ] Выполнить early-recovery XYZ barrier по CRASH_WINDOWS; повторную работу
      существующего cold452 не исследовать заново. Его PASS — regression.
- [ ] Регрессии F/E/R, exact-path source commit и clean committed build.
- [ ] До freeze провести discovery обеих сцен и проверить пригодность settings/cohort.
      После начала final scene нельзя переставлять ботов/мобов или заменять primaries.
- [ ] Записать frozen source+JAR+config/data hashes. После270мин semantic edits нет.

### 4. Финальный product proof, 270–330мин
- [ ] Две natural scenes360–420s на одном SHA, без producer fixes между ними.
- [ ] Exact whole-group SEALED→SQL8/8 и actual restart→8/8; сохранять paired witness
      для dead/missing actor через retained lifetime, а не только World enumeration.
- [ ] Второй sameDB restart и repeated progress. Без routine history-diff повторения.
- [ ] При FARM/REGRESSION/DRAIN PASS и наличии времени — existing death→recovery→farm
      и synthetic away→return→remat→farm, без нового собственного harness.
      Если времени нет: NOT_RUN, а не ложный M1 closeout.

### 5. Closeout, 330–360мин
- [ ] Exact own graceful stop; archived first incidents не стирать.
- [ ] Один RESULT и MORNING, matrix каждого проверенного/непроверенного gate.
- [ ] Exact-path commit/push при любом исходе; одна publication receipt.
- [ ] Никакого «цель достигнута» как синонима M1_CLOSED. M1=OPEN либо
      WAITING_FINAL_CLIENT только при всех server gates. Не ждать пользователя.

Можно завершить раньше при полном результате или доказанном unsafe/outside-design
barrier. Нет обязанности бессмысленно добивать шесть часов. Runtime starts считают
для отчёта, а не ставят cap, запрещающий идемпотентный second restart.
