# PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006

Дата аудита: 03.10.2026. Репозиторий kpCat/L2J, feature/phantom-world.
Проверенный baseline: `461a4abe32be4aa08532b8417a6147684a8889c6`.

**Распаковать ZIP в `C:\Users\ZBook\L2J_Mobius\`.**
Архив уже содержит каталог `L2J_Mobius_CT_2.6_HighFive`. Не распаковывать внутрь модуля повторно.
Архив добавляет только новую папку задачи: он не перезаписывает production source.
Исполнение — новый Codex-чат, GPT-6.1 Sol, **Very High**.

## Что решается

Одна инженерная задача: закрыть обнаруженные нарушения владения native AutoPlay/AutoUse,
потерю первого cleanup exception и неполную передачу terminal travel outcomes;
проверить native store/quiescence и исключить ложный GREEN в M1 acceptance.
Это не ещё одна задача «добавить лог и попросить пользователя зайти».

**Boundary этого чата: READY_FOR_CONTROLLED_DEPLOY либо BLOCKED.**
Ни PLAY synthetic №5, ни real arm, ни deploy этой задачей не разрешены.
Причина исторического FAILED у 110/142/175 остаётся UNKNOWN, пока нет связывающего evidence.

Порядок чтения: TASK → ROOT_CAUSES → DESIGN → STATE_MACHINE → PATCH_GUIDE → PLAN →
SCENARIOS → ACCEPTANCE. SOURCE_MAP — точные источники и символы.
READ_ONLY_EVIDENCE отделяет независимо проверенное от отчёта Codex.
RUNTIME_GATES содержит следующие этапы до полного M1, но не разрешает запустить их сейчас.
ROADMAP сохраняет M1–M10 и критерий перехода в M2.

`proposals/` — конкретные формы изменений для интеграции после RED-тестов, не готовый
проверенный server patch. `proofs/` — исполняемые модели interleaving, не тесты L2J.
Ни чистый JAR, ни native TEST, ни runtime acceptance автором этого ZIP не выполнялись.
