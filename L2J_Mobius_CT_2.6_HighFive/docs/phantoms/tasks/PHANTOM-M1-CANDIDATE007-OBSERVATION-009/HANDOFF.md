# HANDOFF009 — BLOCKED, M1 OPEN

Сохранённый candidate007/runtime/JAR проверен без пересборки. Полный отчёт:
[RESULT.md](RESULT.md).

Пользователь вручную вошёл под TestAdmin в experimental clone runtime;
native IN_GAME подтверждён HUD, DB online=1 и TCP7777. Строгий pilot REAL_LOGIN
не подтверждён, fresh arm отвергнут до session creation.

Конкретный setup mismatch: ActiveTarget64 > MaterializedCap8.
Frozen PhantomPlayersConfig возвращает Settings.disabled при таком диапазоне;
CONFIG PASS startup helper этого не проверяет. AI/population/config не исправлялись.
RUNTIME_BEHAVIOR=NOT_OBSERVED; AUTOPLAY_5_CYCLES=NOT_OBSERVED;
CLIENT_LOGIN_TRANSPORT=PASS после разрешённого ручного входа;
SERVER_REAL_LOGIN=FAIL для строгого pilot identityOwner contract.

Consent OFF/no active run подтверждён. Owned Login14532/Game17884 остановлены
по отдельному live-разрешению пользователя; gracefulShutdown=FAIL.
Client15116 закрыт пользователем, original PLAY не запускался, MariaDB сохранена.
Worktree/private runtime/clone DB сохранены, candidate source не изменён.
Отчёт публикуется только в experiment/m1-candidate007-observe008;
report SHA и результат push сообщаются после commit без self-receipt commit.

В дальнейшем просить пользователя войти вручную; synthetic ввод credentials
не повторять. Не продолжать engineering task007, не закрывать M1.
Следующее действие определяет координатор. После publication — STOP.
