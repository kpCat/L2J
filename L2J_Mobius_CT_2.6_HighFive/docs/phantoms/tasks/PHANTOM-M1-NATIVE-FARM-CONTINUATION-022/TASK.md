# Разрешения, границы и критерий задачи

## 1. Что уже существует и не расследуется заново

Materialization, REAL_LOGIN/AutoAttach, актуальная goal revision, typed travel result, bounded local replacement, water guard и atomic replacePlan уже существуют. TASK021 source337bfbc показал local intent30/30. Не начинать новый обзор этих подсистем; проверять только их непосредственное участие в зафиксированном stall.

Нельзя считать STARTED/autoPlay=true/один удар победой. Нельзя считать last reason текущим состоянием. Нельзя считать отсутствие полезного действия у profile110 доказательством отсутствия боя у всей стаи.

## 2. Разрешённый slice

SOURCE_MAP задаёт точные файлы и методы. Разрешены несколько связанных исправлений: native register/stop, owner/context/callback completion, stall freshness, evidence attribution и их минимальные Phantom adapters. CONDITIONAL_CORE — не приказ менять файл: сначала записать точный failing test и causal witness, потом минимальный patch только названного участка. Стремиться к 4–7 production files; абсолютный предел — 11 перечисленных MODIFY/CONDITIONAL/DIAGNOSTIC paths. Новые production classes не нужны.

Task-owned tools/документы и 3 названных test suites не ограничены правилом «10 файлов», но требуют exact allowlist. Не тратить отдельные turns на исключение для каждого лога. Не менять исходную постановку в TASK/GOAL/DESIGN/ACCEPTANCE ради фактического результата; прогресс — в RESULT/HANDOFF.

## 3. Жёсткие запреты

Не переписывать combat engine, ThreadPool, EventDispatcher, Party, AI/actions, movement/effects; не менять schema/геодату/XML зон, EXP/SP/drop rates, эпоху/goal/counters ради PASS. Не включать DISABLE_TUTORIAL, не удалять quest listeners глобально. Не снимать общие ownership/earned-work/owned-store guards. Не увеличивать MAX_TARGETS/MAX_PHASE_NANOS для маскировки проблемы. Не подавлять неизвестный native writer failure, не считать EARNED ticket отменяемым и не очищать его из outstanding вручную.

Нельзя добавить watchdog, который каждые N секунд безусловно abort/restart/teleport бота. Восстановление должно следовать доказанному producer defect, сохранённому owner/epoch и bounded continuation.

## 4. Что относится к TASK023, а что уже сюда

Полный death→revive, уход/возврат REAL, crash/restart-протокол и весь исторический фон — TASK023. Но если тот же registration/callback defect одновременно замораживает фарм и удерживает его work tickets, исправление обоих эффектов входит в TASK022. Не откладывать прямую причину current stall под ярлыком NATIVE_WORK_SELF_DRAIN. Не удалять self-drain guard — переносить checkpoint на правильную существующую control boundary.

Если после исправления фарм PASS, но остаётся отдельный unsafe drain, результат FARM_PASS сохраняется отдельно; TASK_RESULT=BLOCKED, STOP_CLASS=LIFECYCLE, точная оставшаяся причина. Не начинать заново весь farm slice в следующей задаче.

## 5. Остановка и бюджет

Соседняя доказанная проблема внутри разрешённого slice — продолжить в том же TASK, не просить новый TASK023. Однако максимум 3 semantic repair rounds и 2 connected episodes; к 105-й активной минуте закончить новые изменения и перейти к cleanup/publication. Не ждать 120 минут до начала сохранения игрока.

STOP только при исчерпании бюджета, необходимости forbidden/неперечисленного файла, невозможности безопасно работать с БД/owned runtime либо опровержении выбранного контракта. Проблема в guard не означает, что guard надо убрать.

Не делать три одинаковых blocked-turn. Во время ручного gate отправить один обычный вопрос и закончить turn в WAITING_USER_LOGIN/LOGOUT. Не повторять проверки каждые 10 секунд. Никакой автоматической трактовки молчания как согласия.
