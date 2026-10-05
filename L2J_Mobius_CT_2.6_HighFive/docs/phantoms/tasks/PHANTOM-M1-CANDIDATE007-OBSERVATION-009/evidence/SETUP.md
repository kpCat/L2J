# Sanitized setup evidence

| Проверка | Фактический результат |
|---|---|
| Frozen source entries | 103, mismatch0 |
| Original PLAY files | 304, mismatch0 |
| Clone DB | l2jmobiush5_localplay_observe008 |
| Native character | TestAdmin / object268492939 / online1 |
| Client connection | PID15116, localhost:64012 -> localhost:7777 |
| Server connection | PID17884, localhost:7777 -> localhost:64012 |
| Strict pilot session | отсутствует |
| Fresh permit before cleanup | WAITING_ARM, actor=null, runActive=false |
| Fresh permit after withdrawal | OFF, actor=null, runActive=false |
| Config active target / cap | 64 / 8; frozen Java допускает active <= cap |
| Pilot config | enabled=True, synthetic=False |
| Mailbox ACL | root + inbox/processing/results/journal: protected, только ZBook Allow FullControl |
| JVM user | ZBook |
| Owned JVM cleanup | 17884/14532 stopped после live force authorization |
| Client cleanup | l2 process отсутствовал перед JVM stop |
| Final listeners | 2106/9014/7777 отсутствуют; MariaDB8696/3308 сохранена |

Просмотрены кадры ниже. Это setup evidence, не 10–15-минутный Phantom episode.

- [Command-line login pending, 12:56:29 UTC](login-pending.png).
- [Ручной вход TestAdmin, 13:00:55 UTC](manual-in-game.png).
- [Серверные arm rejection messages, 13:05:11 UTC](arm-rejected.png).

Два отказа в последнем кадре — ручной ввод пользователя; инструмент остановился
до submission. Никакого принятого arm receipt или identity owner snapshot нет.
DB online1/HUD/TCP7777 подтверждают native IN_GAME; owner=REAL_LOGIN не подменяется
этими фактами. Причина guard failure, связанная с disabled system, выведена из
frozen config validation и GameClient arbitration path, а не Reflection.
