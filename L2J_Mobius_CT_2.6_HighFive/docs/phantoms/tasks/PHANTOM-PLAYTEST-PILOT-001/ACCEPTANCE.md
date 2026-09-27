# Приёмка — пилот, а не все игровые возможности

## A. Безопасность и отключённый режим — обязательно

1. Default OFF: нет принимающего action mailbox и gameplay-эффектов; обычный сервер/клиент ведёт себя прежним образом.
2. Неверные root/manifest/ownership incarnation/nonce/expected name/account/id/GameClient/session/expiry отклоняются до side effect.
3. Нематериализованный phantom/headless Player, соседний обычный игрок, основной персонаж без grant не получают control/idle/flood exemption.
4. `.playtest arm` потребляет одноразовое разрешение. Replay/out-of-order/stale/oversized/unknown-operation/XXE/path escape → отказ; duplicate completed request → прежний result без повторного эффекта.
5. `off`, expiry, disconnect, смена персонажа и restart инвалидируют все queued requests. Старый PID с новой process incarnation не подходит.
6. Stop/preemption/watchdog прекращают run за ограниченное число polling ticks. Poller больше не меняет Player; cleanup не трогает чужие объекты.
7. Нет GM/credentials/extra skills/items/level grant, произвольного кода/консоли/SQL или общего network endpoint.

## B. Автоматический functional gate — обязательно

- Узкий Ant target `phantom-localplay-pilot-test` (новый) и launcher mode; deterministic seed `40004001`.
- Native action adapters проверены positive/negative subcases: состояние/движение/поза, normal chat validation, invitation identity/leave, learned-skill checks, prohibited target. Тесты с native persistence — только guarded TEST DB.
- Одна queued action за раз; running execution и cancellation не гоняются за Player; session revalidation непосредственно перед действием.
- Snapshot/status не расходуют player chat/flood counters. Punitive exemptions только точные разрешённые diagnostics и только при доказанной необходимости; regular chat/transaction/mail protection не ослаблена.
- Подтверждённая server AFK policy для пилота проверяется виртуальным временем: expiry/off возвращают правило, ordinary player по-прежнему подчиняется ему. При отсутствии причины честный substatus, не фиктивный PASS «AFK исправлен».
- Каждый исполняемый native запрос даёт outcome; partial/accepted не превращается в завершённый эффект.
- Профильные existing suites по реально изменённым seams, один clean `ant jar`, diff check и text integrity. Полный verify/scale retest не требуется.

## C. Connected batch — одна пользовательская привязка

После deployment и одного arm выполнить `pilot-smoke` из SCENARIOS. Проверить на настоящем связанном клиенте:

- чтение состояния, native sit/stand, короткий move/return, разрешённый teleport/return;
- не менее двух сценарных сегментов одной сессии, без нового логина/arm между ними;
- snapshot burst, без мута/кика и без изменения общего flood configuration;
- хотя бы native отправка игрового chat/invitation при подходящем target; ответ/принятие фантома оценивается отдельно, `GAMEPLAY_GAP` не подделывается;
- stop/off safety проверена автоматикой и ручной stop-командой НЕ требовать пользователя повторно управлять каждым шагом;
- фактический GameClient/REAL_LOGIN identity сохраняется, нет дубля Player; потери соединения/смерти не маскируются;
- никаких 10k reset/reseed, ручного SQL или budget tuning.

Если подходящего phantom/skill нет — этот gameplay subcase `NOT_APPLICABLE/NOT_OBSERVED` с причиной. Не требовать докачивать TestAdmin или чинить AI в этой задаче. Не пропускать при этом обязательные deterministic adapter tests.

## D. Статус и outputs

`GREEN — CONNECTED LOCALPLAY PILOT PROVEN` только при A+B+C (не означает, что все боты умеют чат/party/farm).
`READY_FOR_CLIENT_BINDING` — код/автоматические gates готовы, реального пользователя/arm ещё нет.
`BLOCKED — <точная причина>` — настоящий scope/security/environment blocker; свой исправимый bug сначала исправить.

Outputs: EVIDENCE, STATE, RESULT, RESULTS.tsv, USAGE.ru, VISION_DELTA. В STATE — code/report SHA, JAR hash, runtime RUNNING/STOPPED, expiry/ARMED_IDLE/OFF, no-running-scenario, способы отзыва. Не публиковать секрет nonce/session capability.

После успешного smoke штатный сервер **не обязательно останавливать**: оставить его пользователю, runner завершить и освободить управление. При exit остаётся лишь конечная по времени выданная пользователем lease, не незавершённая автоматическая работа.

## Критичные negative controls

Персонаж другого аккаунта; другой персонаж того же аккаунта; чужой GameClient того же objectId; fake/headless; отключённый private flag; старый nonce; stale PID/start time; replay команды; concurrent commands; manual override; network disconnect; отсутствие файла разрешения; файл вне root; DTD/entity; оборванный XML; полный mailbox; timer expiry при ожидающем действии; native reject; effect-completed-but-result-missing (`UNCERTAIN`); публичный сервер без LocalPlay grant.
