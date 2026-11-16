# PHANTOM-LEARNING-016 — PARTIAL016_NATIVE_COLLECTED_DIRECTOR_CLOSE_FAILED

Разрешённая generation2 сохранила исходные 59 файлов, 10 файлов director и session1. После RED→GREEN перехода получены ровно три новые A/B-пары: три сцены, классы 94/103/110, Solo+Party. Seal сформирован после шестой руки, без поиска четвёртой сцены; расписание — 51 слот.

Настоящий TRAIN: **2 GS / 71,1598 native-минуты, 51 CUT, 399 оплаченных эффектов, 24 820 наблюдённого MP**. Исходы: 29 убийств NPC, 16 смертей акторов, 5 безопасных остановок без законной цели, 1 requested drain. Все 51 settled замыкание сохранены; 11 ранее завершённых эпизодов совпадают побайтово после STOP/RESUME. Есть 27 terminal reward receipts, но у Party — 0; исключения и UNKNOWN-метрики раскрыты в RESULT/EVIDENCE.

FIT — 0: `NO_FIT_INSUFFICIENT_VALID_DATA`. Все 183 SQL-строки, TEST2, DRAFT14, Adam 903/223, pointer12, Geo203/protected207 и MAIN/PLAY сохранены. Headless 2/3; консервативный суммарный расход 772 590/1 800 000 мс. TEST OFF, собственных процессов нет, порты свободны. **TRAINED_MODEL=NO; GAME_ADVANTAGE=NOT_EVALUATED; PRODUCTION=OFF.** Manual gate REQUIRED; G8 — в READBACK. Задача №10/13, осталось 3; следующий Goal не начат.

G4 PARTIAL: после cold NO_FIT checkpoint фаза `DONE016` отклонена validator `^[A-Z_]{1,32}$`: `TRAIN013_STATE_VERSION_OR_PHASE`. Director journal остался в FIT. Runtime после freeze не исправлялся; retry, дополнительный FIT и новая generation не запускались. **FULL016 не заявляется.**

[RESULT016](../tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/RESULT.md), [EVIDENCE](../tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/EVIDENCE.json), [READBACK](../tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/READBACK.json).

---

Историческая первая попытка016:
# PHANTOM-LEARNING-016 — PARTIAL016_UNSEALED

Exact7 реализован и проверен offline: config51, incremental atomic pair journal/selection replay, linked marker-last seal, native reservation и отдельные бюджеты, conditional FIT без EVAL/promotion. Первая реальная headless session дала2Solo pre-CUT arms,0paired cells: ошибочная глобальная уникальность NPC spawnGeneration вызвала fail-closed. Source исправлен RED/GREEN, frozen TEST не перепечатан; новый идентификатор подготовки/переход требует отдельного согласования.

Headless1/3,72 430ms issued /172 590ms Prepare со сборкой; GS0/3, native0/75мин, FIT0. OFF/own0/portsfree, historical183rows/TEST2/DRAFT14/Adam903/223/pointer12/Geo203/protected207 сохранены. MAIN/PLAY и Studio не изменялись задачей. Нет нового TRAIN CUT/paid effect/terminal и доказанного улучшения нейросети.

G3 FAIL, G5 NOT_RUN/FAIL. **TRAINED_MODEL=NO; GAME_ADVANTAGE=NOT_EVALUATED; PRODUCTION=OFF; manual REQUIRED.** [RESULT016](../tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/RESULT.md), [EVIDENCE](../tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/EVIDENCE.json), [READBACK](../tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT/READBACK.json). Goal015 остаётся принятым PARTIAL, история неизменна. №10/13,3remaining; следующая задача не начата.
