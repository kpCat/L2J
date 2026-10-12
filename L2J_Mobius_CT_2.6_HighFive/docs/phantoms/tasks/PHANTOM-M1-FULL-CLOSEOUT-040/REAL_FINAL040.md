# REAL_FINAL040

Сервер C4 запущен в OWN040a как READY_FOR_PREVIEW; M1 остаётся OPEN.
Используйте существующий клиент `D:\l2afterwork-client\system\l2.exe` и private login-конфигурацию `D:\Tools\L2ClientTools\L2Client.ps1`, character TestAdmin.
Готовая команда входа: `powershell -NoProfile -ExecutionPolicy Bypass -File D:\Tools\L2ClientTools\L2Client.ps1 -Action Login`.
Пассивный observer должен автоматически привязаться к текущей actual REAL session; arm не нужен, управление персонажем остаётся у вас.
Посмотрите native движение/обход/бой melee и caster, смену целей, нормальный rest и отсутствие устойчивого flicker/pop-in; один короткий уход и возвращение — личная проверка, не замена незакрытого G4/G5.
Сообщите, вошли ли вы, что видели и принимаете ли результат; продолжение будет в этом же TASK040.

Status без изменения runtime:
`pwsh -NoProfile -File C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops040\Control-Acceptance040.ps1 -Action Status`

Actual Login/EnterWorld: NOT_RUN. ACTUAL_REAL_BINDING: NOT_RUN. Видимая оценка пользователя: NOT_RUN. USER_ACCEPTANCE: NOT_RUN. REAL_FINAL: NOT_RUN.
Focus fixtures3/3 проверяют arrival/logout/reconnect/stale disconnect/instance, но не настоящую сеть. Реальные O2/O3 должны быть подтверждены после входа.
G4 productive away и G5 complete cross-boot ещё NOT_VERIFIED; G6 BLOCKED_AUTHORITY из-за сохранённого запрета crash72/73. Даже успешный личный вход сам по себе не переводит M1 в CLOSED при этих остатках.
Пароли/токены не публикуются. Финальный сервер не принадлежит finally/test cleanup и не выключается по окончании goal/Synthetic TTL.
