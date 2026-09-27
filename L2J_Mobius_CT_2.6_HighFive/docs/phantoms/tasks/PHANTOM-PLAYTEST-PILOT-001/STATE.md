# PHANTOM-PLAYTEST-PILOT-001 — состояние

Статус: `READY_FOR_CLIENT_BINDING`. Автоматические проверки и clean deployment завершены. Настоящий клиент ещё не привязан; `CONNECTED_SERVER` и `CLIENT_OBSERVED` не подтверждены, GREEN не заявлен.

- Branch: `feature/phantom-world`; initial HEAD `d2ae5704031ca85e417c3cb959de47cd72618821`; последний code commit `93cd5b8a609`.
- Clean JAR из `e918f755167cd2a63c45f4938b7f291d60c13d59`: SHA-256 `CC7EC666A0662B7916B900B8EC1122932798956D2DE29FCD0484EE71DA77FC4B`. Runtime `Prepare-LocalPlayPilot.ps1` из `93cd5b8a609`: SHA-256 `B347C7721615AE2B3A419488D65ECD646037DC8197B0CAD47894BCADC1E78444`.
- Owned LocalPlay: LoginServer PID `22512`, GameServer PID `7776`; порты `2106/9014/7777` открыты у owned процессов; pilot enabled; Population=`10000`, Active=`64`, MaterializedCap=`128`, PulseMs=`100` и `FRESH_LOCAL_PROVISIONED` сохранены.
- Expected character: точный ник `TestAdmin`, отдельный реальный персонаж уровня 1. Account, objectId и session будут взяты из настоящего `GameClient` при `.playtest arm`; пока они неизвестны. `PersonalCharacterQoL` allowlist и GM не менялись.
- Mailbox `WAITING_ARM`, runner остановлен. Runtime helper без `ExpectedName` создал permit для `TestAdmin` до `2026-09-27T12:48:26.740Z`; одноразовую команду не записывать в репозиторий.
- Connected run: `RESULTS.tsv` содержит только заголовок; ни один connected scenario не запущен. Следующий шаг — единственный клиентский вход/arm, затем автономный batch и сохранение фактических результатов.
- Отзыв: `.playtest stop`, `.playtest off`, локальный `Stop-LocalPlayPilot.ps1`; disconnect/restart/expiry инвалидируют lease. После сценариев runner будет остановлен, исправный LocalPlay останется пользователю.
- Report `RESULT.md` SHA-256: `162FFA4B1DBF3E5D984E6DB542AA93E9BB143175EF261E771D3DCC7B692BC592`.
