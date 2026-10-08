# TASK026 — непрерывная native-жизнь после восстановленного сохранения

Назначение пакета: ограниченная реализация Codex, не новый общий аудит M1.
Распаковать в C:\Users\ZBook\L2J_Mobius\. Production распаковкой не меняется.
Модель исполнителя: GPT-6.1 Sol, Very High, новый диалог, без субагентов.

TASK025 доказал cold recovery452, whole-group SEALED→SQL 8/8 в двух сценах и
два actual restart группы d 8/8. Его полный product result всё равно FAILED:
непрерывный farm 0/8 и 2/8, callback drain и N02/S12 не закрыты;
crash recovery XYZ измерен слишком поздно для вывода о first writer;
death/soft-return NOT_RUN.

**Сохранить рабочее persistence/recovery. Не повторять TASK025 с новым номером.**
Основной endpoint TASK026: естественная группа продолжает фарм до конца сцены,
после чего заработанное сохраняется и та же БД повторно запускается.

Читать: TASK → ROOT_CAUSES → DESIGN → PLAN → PATCH_GUIDE → SCENARIOS.
SOURCE_MAP задаёт точные границы; RUNBOOK — автономную работу; ACCEPTANCE — статусы.
Код в proposals — проверяемый reference design / read-only анализатор, не готовый
server patch. Его тесты никогда не заменяют native и server acceptance.
