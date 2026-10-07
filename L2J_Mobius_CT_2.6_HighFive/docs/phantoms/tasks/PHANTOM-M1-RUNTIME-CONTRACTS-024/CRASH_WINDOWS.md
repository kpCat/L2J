# Crash / точность сохранения

Новые position eligibility должны пройти тот же b4-protocol, а не обходить его.

| Окно | Что разрешено после recovery |
|---|---|
| До PREPARE | последний committed snapshot; не заявлять сохранность ещё только RAM-earned |
| После PREPARE, до native store | прежняя или предусмотренная exact recovery branch; никаких invented deltas |
| После native store, до FINALIZE | resolve exact AFTER, ни rollback подтверждённого earned, ни двойной replay |
| После FINALIZE, до POST_STORE/index | native данные уже committed; index реконструируется, native rewards не повторяются |
| Повтор recovery/start | тот же outcome/hash, нет новых items/EXP от replay |

FaultInject TEST checks для всех окон; минимум реальный отдельный процесс AFTER_NATIVE
и AFTER_FINALIZE на OWNED clone (макс2 planned crashes). Actual GameServer может использовать
существующие fault points/guarded fixtures. Не выдавать обычный terminate процесса в произвольной
точке за проверку PREPARE/NATIVE/FINALIZE. Точное окно фиксируется в receipt перед stop.

Shared TEST schema barrier из023 НЕ решать переписыванием metadata. Если shared guard
отказывает, выполнить transaction/fault checks в созданной с нуля task-owned isolated DB
через existing injectable ConnectionProvider + собственный exact clone guard. Новый
allowlist test-only, привязанный к clone/manifest, не расширяет production guard. Если
actual process fixture недоступен — CRASH_MATRIX=BLOCKED_SCHEMA/NOT_RUN, но другие четыре
контракта продолжать. Не считать отсутствие проверки PASS и не ждать оператора ночью.

Expected snapshot — последний SEALED native snapshot, соответствующий PREPARE, не последний
произвольный live census. Inventory+skills сравниваются полными каноническими hashes.
После штатного stop SQL read до restart, затем same-DB restart, receipts/idempotence, затем
новая игра. Background/native income между двумя наблюдениями учитывается отдельно.
Не использовать монотонность EXP: законная смерть может уменьшить EXP, expected берётся
после native death drain. Не переносить диагностические счётчики в saved state.
