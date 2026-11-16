# PARTY_REWARD_CONTRACT017 — как не потерять Party и не сфабриковать награду

## Реальная точка потери

`PhantomAutonomousTrainingNative.collectEpisode`: для каждого paid receipt берётся `NativeTiming`, создаётся `TacticalStoreV3.Row` и выполняется `TerminalRewardReceiptV1.create(boundary, episode, List.of(action), ...)`. `IllegalArgumentException` перехватывается, а в `excluded-actions.jsonl` пишется только имя класса исключения. В 016 так не получено **ни одного Party terminal receipt** при подтверждённых 17 завершённых Party эпизодах.

**Нельзя заранее объявить конкретную причину**. Перед кодом нужен table-driven **authentic replay** из точных неизменяемых sources. Сформировать для каждого excluded event bounded диагностическую запись с причиной на уровне *нарушенного поля/проверки*, без частных имён, секретов и больших сырых payload.

## Диагностические категории (будущие диагностические имена, не существующий API)

- `OWNER_OR_ACTOR_IDENTITY`: owner nonce/generation, actor profile/objectId/epoch/instance.
- `TARGET_IDENTITY`: selected target versus canonical boundary target; для HEAL — член разрешённой Party и его epoch.
- `EVENT_OR_SPLIT`: eventKey, split, scope, corpus contamination, duplicate.
- `NATIVE_TIMING`: captured/cut/start/end/terminal/settled; оплаченное действие, связь lethal event.
- `TERMINAL_EVIDENCE`: подтверждение kill/death/timeouts, credited exp/sp; неизвестные значения сохранять UNKNOWN.
- `MISSING_SOURCE`: отсутствующий raw timing/receipt/selection, нет точного replay.
- `OTHER_VALIDATION`: именованная проверка и минимальный redact-safe reason; не оставлять `IllegalArgumentException` без указания условия.

Отчёт: 17 Party эпизодов /71 исключённое Party action — сумма по типам и split, плюс 13 Solo exclusions. Отдельно `missing timing` (до вызова create), `validation failed`, `no matching selection`, `empty eligible list`, `source incomplete`.

## Поведение после исправления

- Не принимать чужой mob/actor, skill чужого уровня или необоснованный terminal.
- Не ослаблять native MP, effect timing, world/owner/Party/Geo.
- Не превращать 46 Party actor kill credits в46 независимых уникальных убийств: фактически подтверждены29 уникальных NPC kills по всему cohort.
- Продолжать учитывать смерть и не удалять проигрыши.
- Сохранять исходные `terminal.bin`, `settlement.json`, `*.native.json`, `excluded-actions.jsonl`, `selection.bin`, SQL и frozen evidence **побайтово**.
- Новые diagnostic/replay отчёты помещать только в отдельное версионированное место TEST017 или внешние приватные evidence. Не переписывать 016, не менять history и не приклеивать задним числом training credit.
- После правки проверить **действительный Party paid effect** и формирование одного валидного terminal receipt на независимом isolated TEST, если нативное доказательство требуется и разрешено бюджетом. DB-free fixture не выдавать за native.

## Ограничение разрешения

Если истинная причина находится в shared `TerminalRewardReceiptV1` codec/reward-definition, Codex должен представить владельцу точный проект корректировки и совместимости старых receipt. *Никакой автоматической миграции, модификации истории или смягчения проверок ради PASS.*
