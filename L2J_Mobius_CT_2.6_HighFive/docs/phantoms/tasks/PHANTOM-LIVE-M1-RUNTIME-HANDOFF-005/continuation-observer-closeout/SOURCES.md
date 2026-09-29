# Основание пакета

Проверка GitHub: ветка feature/phantom-world, SHA `4e461f3fbf15828de9ddf62ff5f0d53574a31768`.
Пути и методы указаны в SOURCE_MAP; SHA относится к исходному коду, а не к будущей реализации.

- LocalPlayPilotActions.java: подготовка в районе строк 185–345; snapshot/teleport/census в 155–465.
- Run-M1RuntimeHandoff.ps1: Capture, Follow-Native, статический подход, SHORT_LEAVE/RETURN и finally.
- LocalPlayPilotProtocol.java: неизменяемый Request, набор Operation, аргументы и mailbox-предел.
- PhantomTopologyPositionPublisher.java: durable-only публикация координат.
- Stop-LocalPlayPilot.ps1: остановка run без обещания автоматического OFF.
- PhantomOperatorObservabilitySuite.java и PhantomTestLauncher.java: существующий test framework.
- continuation-history-self-heal/RESULT.md: последний отчёт, не полный TSV.

Открыть исходник: `https://github.com/kpCat/L2J/blob/4e461f3fbf15828de9ddf62ff5f0d53574a31768/L2J_Mobius_CT_2.6_HighFive/<путь из SOURCE_MAP>`.
Алгоритм DESIGN и численные лимиты observer, не унаследованные из кода, — выбранные требования
этого пакета. Они не представлены как уже измеренные свойства сервера.
