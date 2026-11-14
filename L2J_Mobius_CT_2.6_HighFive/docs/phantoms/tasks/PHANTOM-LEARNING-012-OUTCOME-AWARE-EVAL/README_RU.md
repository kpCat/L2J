# PHANTOM-LEARNING-012 — версия оценки, учитывающая смерти, и честный Solo curriculum

**Встроенный путь:** `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-012-OUTCOME-AWARE-EVAL/`.

Архив имеет корень `phantoms/tasks/...` и распаковывается **в каталог `L2J_Mobius_CT_2.6_HighFive/docs/`**, а не в Git root и не в `docs/phantoms/`.

Предварительная база (14.11.2026): `origin/feature/phantom-world` = `9e78e3db1153126c616812bfeb8ff5bdbb8c8278`. Перед началом проверить реальный remote HEAD; если уже обновился — сравнить и перепланировать, не переписывать чужую работу.

## Точка проблемы

011 зафиксировала 8 native CUT / 47 paid events / 8 достоверных терминальных receipts. В Solo: пары STOCK death / LEARNED kill и STOCK death / LEARNED death. В Party обе пары STOCK kill / LEARNED kill. Legacy evaluation `EvaluationArm.completed` сводит исход к Boolean `episode.outcome()==COMPLETED`, а `pairStatus()` считает сравнение завершённым только при двух `true`. Это выкидывает подтверждённые смерти из статистики сравнения. Требуется исправить **оценку**, а не переписать фактические исходы или историю старой модели.

Статус011: `PARTIAL011_NATIVE_SOLO_COMPLETION_UNMET`, G4 FAIL, ручная приёмка REQUIRED. Нельзя задним числом объявлять старые G4 PASS. Новая задача №6/13, остаётся7 после выдачи. 007 принята, 008–011 PARTIAL и manual gates там, где указаны.

## Основные файлы
- `TASK.md` — обязательное исполнимое задание.
- `PROTOCOL_V2.md` — строгий контракт оценивания терминальных боёв.
- `CURRICULUM.md` — как выбирать классы/уровни/сцены без подгонки результата.
- `TEST_MATRIX.md` — RED→GREEN, включая негативные случаи.
- `PLAN.md` — очередь этапов, бюджет.
- `ACCEPTANCE.md` — G0–G8 и честные вердикты.
- `GIT_POLICY.md` — только feature, сохранение SQL/моделей.
- `PRE_AUDIT.md` — проверенные исходные факты, не выдуманные результаты.
- `HANDOFF.md` — обязательное состояние после завершения.
