# REAL_FINAL040

Сервер C4 запущен в OWN040a как READY_FOR_PREVIEW; M1 остаётся OPEN.
Используйте существующий клиент `D:\l2afterwork-client\system\l2.exe` и private login-конфигурацию `D:\Tools\L2ClientTools\L2Client.ps1`, character TestAdmin.
Готовая команда входа: `powershell -NoProfile -ExecutionPolicy Bypass -File D:\Tools\L2ClientTools\L2Client.ps1 -Action Login`.
Пассивный observer должен автоматически привязаться к текущей actual REAL session; arm не нужен, управление персонажем остаётся у вас.
Посмотрите native движение/обход/бой melee и caster, смену целей, нормальный rest и отсутствие устойчивого flicker/pop-in; один короткий уход и возвращение — личная проверка, не замена незакрытого G4/G5.
Сообщите, вошли ли вы, что видели и принимаете ли результат; продолжение будет в этом же TASK040.

Status без изменения runtime:
`pwsh -NoProfile -File C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops040\Control-Acceptance040.ps1 -Action Status`

Actual Login/EnterWorld: CONFIRMED. ACTUAL_REAL_BINDING: PASS — actualNetworkSession=true, REAL, objectId268492939 на том же Game30312. Первый TestAdmin login записан 2026-10-13 01:02:48.800 Europe/Chisinau; read-only observer snapshot 2026-10-12T22:37:43Z подтвердил реальную сессию. USER_ACCEPTANCE: NOT_GIVEN. REAL_FINAL: FAIL_INCIDENT_DEFERRED; M1=OPEN.
Пользователь сообщил: Zariche, убийство одного фантома, затем потеря движения/атак/Scroll of Escape/unstuck при работающем чате и выделении целей. Снимки сохранены до вмешательства. Casting=true без task/future, AI=IDLE с pending MOVE_TO; outOfControl/movementDisabled/effects/overload не объясняют блокировку. Локальный casting guard и вероятный исходный отказ низкоуровневого AURA cast описаны в FIX_REVIEW040.md.
После обычного перезахода пользователь подтвердил нормальное движение; серверный срез 22:59:12–17Z показывает изменённые координаты и AI=MOVE_TO. Пользователь затем сообщил о гибели от охранника с Zariche и подтвердил, что это сейчас не главное. По прямому указанию пользователя дефект Zariche оставлен без исправления. Перезаход — обход, не GREEN regression и не приёмка M1.
Focus fixtures3/3 не подменяют реальные сетевые результаты. Последние ground clicks не зарегистрированы трассировкой MoveToLocation: точный guard конкретного пакета и исторический момент выставления casting не доказаны. REAL logout/reconnect affinity отдельно не измерялась.
Поле REAL_FINAL=NOT_RUN в существующем Status helper — прежнее статическое метаданное; актуальный клиентский результат указан здесь.
G4 productive away и G5 complete cross-boot ещё NOT_VERIFIED; G6 BLOCKED_AUTHORITY из-за сохранённого запрета crash72/73. Даже успешный личный вход сам по себе не переводит M1 в CLOSED при этих остатках.
Пароли/токены не публикуются. Финальный сервер не принадлежит finally/test cleanup и не выключается по окончании goal/Synthetic TTL.
