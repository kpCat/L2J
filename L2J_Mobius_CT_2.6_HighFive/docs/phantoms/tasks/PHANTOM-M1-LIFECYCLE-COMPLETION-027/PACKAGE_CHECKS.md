# Проверка пакета координатором

Проверены ZIP structure, UTF-8 strict decode, отсутствие лишних binary/credentials,
base SHA и ссылки на task-файлы. Windows GameServer не запускался в ChatGPT runtime.
Исходники/reports ревьюились через GitHub на pinned882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf.

Reference Java table: компиляция javac --release17 на JDK21; 139 assertions PASS.
Read-only extractor: unittest8/8 PASS. Эти результаты относятся только к приложенным
helpers; они не называются native RED/GREEN, доказательством runtime fix или M1 PASS.

Production/test файлы репозитория архивом не заменяются: всё находится под docs task027.
M1_OPEN сохраняется; acceptance выполняет Codex локально после реализации.
