# PLAN017 — последовательность без лишних игровых запусков

**До изменения исходников — короткий IMPLEMENTATION_PLAN017.md с exact allowlist и ожидаемыми последствиями; владелец утверждает один раз.** Предусмотренные текущим заданием scoped non-force commits/push разрешены в feature после gate. Применять настоящие SQL/GS только в принадлежащем TEST после G0.

### A. G0 read-first / сохранность

- `git fetch origin feature/phantom-world`, зафиксировать актуальные HEAD/ancestry/diff, test/source/MAIN dirt, исходные 016 commits.
- Cold protected TEST006: owner, SQL five-SELECT183 SHA, selected DRAFT14, terminal Adam903/223, acceptedTEST2, pointer12, Geo203/protected207, порт/процесс, ручные frozen campaign. Вся приватная сырая история016 immutable; перед программным чтением — внешние SHA-backed backups.
- Список реально доступных archived receipts с physical paths и hashes; не интерпретировать отсутствие файлов как положительный пример.

### B. G1 доказать цепь утраты

- Read-only scan 51 rows/399 event keys/84 exclusions/27 terminal: картировать `admission → CUT → nativePaid → timing → terminal → selection → SQL candidate`.
- Для 17 Party и 13 Solo исключённых actions воспроизвести конкретную причину через существующий constructor и точные raw даные. Не писать сообщение `FOREIGN ...` по догадке.
- Показать split/class/actor/time границы и у каких action нет данных. Указывать lower/upper counts и поля UNKNOWN.

### C. G2/3 минимальные исправления

- После OWNER plan gate: RED→GREEN diagnostic and Party validation correction **только** для доказанной причины; shared codec/reward semantics требуют отдельного явного approval.
- Локальный `DONE016` finalization, versioned cold recovery после `NO_FIT` без rerun.
- Report конкретной sufficiency с настоящими порогами, upper/lower bounds по каждому split.

### D. G4 bounded proof

- Сначала DB-free/SQL-read-only validation. Настоящий `TerminalRewardReceiptV1` с exact facts, не стаб, должен принять действительный Party action и отвергать незаконные.
- Если подлинных raw 016 достаточно для **read-only** replay, предпочтительно использовать их, чтобы не запускать GS. Но replay **не делает историческое действие кредитуемым задним числом**.
- Только если offline/frozen tests PASS, необходимо одно native свидетельство и заранее есть реальная законная сцена — разрешён **один** TEST GameServer start, **не более 20 native минут**. Это короткая проверка Party receipt, **не** 51-й эпизод или новый тренировочный цикл. Если существующая инфраструктура не поддерживает честный bounded Party-only TEST без изменения разрешённой архитектуры, не имитировать его, сообщить PARTIAL.

### E. G5–G8 завершение

- Контрольная проверка `ActionAdvantageV2.sufficient()`: old016 dataset недостаточен, никакого FIT/promotion; future dataset plan основан на фактических потерях.
- Targeted Ant/PowerShell и regression 007–016 (разумный scope), последовательные Ant, no broad verify.
- Финальный cold G0, OFF/0owned/portsfree, true timing/resource/cost, точный diff/coverage, кодировки.
- Scoped commit/non-force push feature, независимый GitHub HEAD/parent/source/docs/evidence readback.
- Отдельные `TECHNICAL` / `FIT_READINESS` / `GAME_ADVANTAGE` / `PRODUCTION` verdicts. Manual gate REQUIRED. Следующий Goal не начинать.

## Лимиты

- **0 новой полной кампании.**
- Read-only/offline replay сколько нужно в ограниченном файловом scope, с CPU/IO лимитом и без фоновой активности после завершения.
- **Максимум 1 GS / 20 native-минут**, только после G0–G3 и preflight real Party; по умолчанию 0 GS.
- FIT новых моделей **0**, независимая EVAL **0**, promotion **0**.
- Максимум два подтверждённых самостоятельных causal fixes; новая фундаментальная проблема/необходимость semantic migration — owner review, затем PARTIAL или BLOCKED, не новый цикл микропатчей.

Данный бюджет новый только для017. Исторический016 2GS/71,1598мин не сбрасывать, не повторно расходовать и не смешивать отчёты.
