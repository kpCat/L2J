# PHANTOM-PLAYTEST-PILOT-001 — состояние

Статус: `READY_FOR_CLIENT_BINDING`. Автоматические проверки и clean deployment завершены. Настоящий клиент ещё не привязан; `CONNECTED_SERVER` и `CLIENT_OBSERVED` не подтверждены, GREEN не заявлен.

- Branch: `feature/phantom-world`; initial HEAD `d2ae5704031ca85e417c3cb959de47cd72618821`; последний code commit `2bc9d71c53963ffac243954955c8a8159fface26`.
- Clean JAR из `e918f755167cd2a63c45f4938b7f291d60c13d59`: SHA-256 `CC7EC666A0662B7916B900B8EC1122932798956D2DE29FCD0484EE71DA77FC4B`. Runtime helper из `2bc9d71c53963ffac243954955c8a8159fface26`: SHA-256 `1B4C62A2BD3D22C65EA23DB3A5679B5B3D17680C91D6D6BBD7850E365CC351E7`.
- Owned LocalPlay: LoginServer PID `22512`, GameServer PID `7776`; порты `2106/9014/7777` открыты у owned процессов; pilot enabled; Population=`10000`, Active=`64`, MaterializedCap=`128`, PulseMs=`100` и `FRESH_LOCAL_PROVISIONED` сохранены.
- Expected character: точный ник `TestAdmin`, отдельный реальный персонаж уровня 1. Account, objectId и session будут взяты из настоящего `GameClient` при `.playtest arm`; пока они неизвестны. `PersonalCharacterQoL` allowlist и GM не менялись.
- Mailbox `WAITING_ARM`, runner остановлен. Краткоживущий arm permit обновляется непосредственно перед выдачей `USER_CLIENT_ACTION`; одноразовую команду не записывать в репозиторий.
- Connected run: `RESULTS.tsv` содержит только заголовок; ни один connected scenario не запущен. Следующий шаг — единственный клиентский вход/arm, затем автономный batch и сохранение фактических результатов.
- Отзыв: `.playtest stop`, `.playtest off`, локальный `Stop-LocalPlayPilot.ps1`; disconnect/restart/expiry инвалидируют lease. После сценариев runner будет остановлен, исправный LocalPlay останется пользователю.
- Report `RESULT.md` SHA-256: `5C20FCC91C8908FAD7B7267E48E00B6A0CAF1EC4B8FAE308352D9091FD5AA1FE`.
