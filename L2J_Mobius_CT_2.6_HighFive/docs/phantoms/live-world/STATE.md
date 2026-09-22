# Состояние LIVE WORLD

Baseline: `6c229190d5920e6159c317e78433972457d01912`.
Дата фиксации: 17.09.2026.

| Этап | Статус | Следующая контрольная точка |
|---|---|---|
| LIVE-001 | NOT_STARTED | 001-A: получить свежие логи и отличить завершение Java от дисконнекта |
| LIVE-002 | NOT_STARTED | машинный реестр всех spawn-групп и карта покрытия |
| LIVE-003 | NOT_STARTED | трассировка существующего фонового цикла и разделение доступности/детализации |
| LIVE-004 | NOT_STARTED | единое владение торговцем и отдельный бюджет сидящих Player |
| LIVE-005 | NOT_STARTED | реестр инстансов, затем сквозная приёмка после предыдущих этапов |

## Не потерять

- Рейты пользователя сознательные: XP3/SP5, партия2/2. Не «исправлять».
- Сбой 17.09: маршрут Talking Island; точная привязка к границе города не доказана.
- Последний показанный лог: 20:00:15, 34 516 байт; нет его содержимого.
- Июльский Access denied — не доказательство причины сентябрьского вылета.
- Cross-class отказ у Nerga/Kincaid; до исправления проверить действующий runtime, реальный логин и путь до showNoTeachHtml.
- 10 000 постоянных личностей — цель; отдельные видимые торговцы не расходуют боевой лимит, но расходуют память и входят в общий body cap.
- PM/приглашение далёкому Player не равно PM/приглашению профилю без Player.
- Региональный крик не становится глобальным только потому, что адресат нематериализован.
- Фоновая модель должна обновляться редко; обычный spoil только при доступных Spoil/Sweep.
- Полное географическое покрытие и доступ человека в катакомбы — разные вещи.
- Существующий QOL freeze сохраняется; нет автоматического QOL-010 и бесконечных «финальных» задач.

PREVIOUS_NEXT_ACTION (checkpoint 17.09): собрать TXT по CRASH_CHECK.md без Codex, изменений сервера и БД.

## LIVE-003-0B — 22.09.2026

Исходный HEAD `8676bbf8dcf78b73686b0601d101740b704a43fa`, ветка `feature/phantom-world`. Исправлен ecology-aware ACTIVE admission до региональных квот и последний native materialization failure; focused 10 Ant targets GREEN (79/79), guarded Player-in-World integration GREEN, `ant jar` GREEN. Отчёт: `docs/phantoms/reports/LIVE-003-0B.md`.

GameServer JAR SHA-256 `3B7D421924A5702CB6D8183422494FE94A77430DE03251F2E3E212334D452F15` доставлен в существующий private runtime вместе с точечным admin handler. Пользовательские игровые конфиги, данные, heap и collector сохранены; manifest metadata синхронизированы с действующим конфигом. CHECK/START/STOP в private runtime теперь сопоставляют owned PID по DateTime; rollback-копии лежат в `.phantom-local/backups/LIVE-003-0B-20260922-2255/` и `.phantom-local/backups/LIVE-003-0B-review/`.

Runtime CHECK/START подтверждают два owned процесса и открытые ими порты. Native eligibility/World snapshot в живой JVM ещё не получен через авторизованную GM-команду; DB `online` не заменяет эту проверку. Поэтому **LIVE-003-0B runtime gate=PENDING; общий результат=PARTIAL**. LIVE-003 целиком, recovery/content/crash, LIVE-001/002/004/005 не закрыты. Новые этапы не запускались.

NEXT_ACTION: в текущем runtime авторизованным GM снять `//phantom status` и `//phantom status <profileId>` в естественном ACTIVE окне, подтвердить eligible → admitted → exact World Player либо конкретный native blocker и принять runtime gate LIVE-003-0B.
