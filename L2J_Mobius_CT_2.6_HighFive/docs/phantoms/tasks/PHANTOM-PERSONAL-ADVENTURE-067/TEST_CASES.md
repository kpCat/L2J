# TEST_CASES067 — мало сценариев, но каждый что-то доказывает

## Проверки доступа: один общий табличный набор, не отдельные тесты для каждого NPC

Input matrix: real allowlisted ordinary access0, обычный nonallowlisted, phantom materialized/headless, GM (не получать личный допуск автоматически), пустой/некорректный allowlist, masterOFF, childOFF; при логине другим аккаунтом тот же персонаж не получает привилегию. Disabled/default must produce stock behavior. Допуск не привязан к одному заранее выбранному `objectId` fixture. Если невозможно создать real network session без участия человека, service-level permission тесты делаются на специально проверенной native TEST среде; **не подделывать сеть/GM**, client-rendering QA отложена.

### S1 — Kamaloka и Labyrinth

Один соответствующий level/template Kamaloka solo, одна Labyrinth branch solo. Native `instanceWorld.allowed=[owner]`, один instanceId, настоящий teleport/exit/time/reuse. Отрицательные: чужой solo, Phantom, wrong level, reuse, bad template, no quest/prereq когда существует; no new instance и no reward on refusal. Существующая Party: leader allowlisted инициирует один world, два члена входят в **тот же** id, исходные level/reuse/max/near/member restrictions сохранены. Полное убийство каждого лабиринтного босса/комнаты необязательно: вход, работа instance, exit/cleanup — достаточно для этой QoL.

### S2 — Rift: только если native party ownership безопасно расширен

Solo enter без пользовательской Party, с настоящими fragments7079, законной комнатой, native room timer/exit; фрагменты списываются один раз, instance/room освобождается после leave/restart. Проверить `RiftMinPartySize=2` для обычного пользователя и sameGroup party. Если полная life-cycle проверка требует переписывания Party/DimensionalRift, прекратить до таких правок и зафиксировать `RIFT=PARTIAL_DESIGN`, не убирать protections. Отрицательные: insufficient fee, logout, death, race on room, ordinary/nonallowlisted.

### S3 — Wondrous Cubic

Предмет10632, allowlisted with switchON: use skill2510 → настоящая reward и reuse ровно60min; на59:59 второе использование отклонено, на60:00+ разрешено; после logout/restart остаётся лимит. Old24h persisted timestamp: миграция или read-only effective override **не допускает ранней выдачи менее чем через60min с фактического последнего использования**. ChildOFF/nonallowlisted обычный 24h, включая общую reusable item group; никто не получает копию награды.

### S4 — Рыбалка

Два вида реальной рыбы (обычная и deceptive), один bounded fishing engagement: реальная удочка/lure/native skill Reel/Pump → HP fish, расход MP/FishShots при наличии, законная win/loss и item output. Не вызывать `usePumping`/`useReeling` напрямую из теста как игровое доказательство, если это обход skill effect; действия должен выбрать production assist. При OFF/обычном/Phantom автоматического действия нет. Logout/rod removal/смерть прекращают assist.

### S5 — SA, Henna, augmentation: дешевле и эффективнее параметризованные проверки

SA: проверить действительное место старого level gap (если его нет — NOT_APPLICABLE, без нового кода), сравнить разрешённого и обычного при одинаковой паре mob/crystal и deterministic rules. Не разыгрывать сотни RNG-попыток; chance formula data-table + одна native outcome.

Henna: record STR/CON/DEX/INT/MEN/WIT по native с OFF, IGNORE_NEGATIVE и FLIP_NEGATIVE при одной-двух действительных dyes. Реальные потолки, снятие, main/subclass switch, reload; ordinary и Phantom неизменны. Клиентский tooltip можно не переделывать.

Augmentation: положительные base stat функции умножаются только allowlisted, не навыки/chance/шанс получить glow; unequip removes exactly what applied; double-equip/relog no double bonus; отрицательные статы и обычные игроки неизменны. При отсутствии безопасного native seam — OFF/NOT_RUN, не уводить тесты в бесконечное исследование любого Augment ID.

### S6 — согласованная поставка, проверка после одного рестарта

Чистый JAR из конкретного codeSHA, stock datapack/config и собственная DB. Один Main штатный START/STOP/restart с проверкой, что скрипты и QoL configs действительно загружены, обычная серверная логика не ломается; native entry/duration/item-reuse remain bound to same source revision. Для теста cooldown использовать FakeClock; не ждать час реального времени. Факт старых Phantom R2 remains accessible/untouched, запуск их protected release не нужен.

По каждому кейсу: `PASS/FAIL/NOT_RUN/BLOCKED/NOT_APPLICABLE`, owner/source, command, duration, one evidence path. Не считать повышение тестового mock access level доказательством policy. Если negative required-gate FAILED — этот feature запрещено включать в поставке.
