# PHANTOM-LEARNING-002 — проверяемая приёмка

| Gate | Требование для SUCCESS |
|---|---|
| D0 — Git & ownership | Base exact b85dd2a3, новая learning002 WIP и независимый clone; MAIN/PLAY/TEST129, пользовательская база, `feature/phantom-world`, TASK130 WIP, learning001 branch unchanged; scoped fast-forward push |
| D1 — Автономность | `TRAIN_START.cmd` выходит, отдельный директор остаётся; после закрытия вызвавшего терминала/codex обработал хотя бы ещё 2 новых эпизода без участия человека/агента; heartbeat и status подтверждают независимый PID |
| D2 — Настоящий native | Собственный штатный GameServer + MariaDB + Geo203; >=1 подтверждённый Solo и >=1 подтверждённый stock autonomous Party PvE бой с exact NATIVE evidence после запуска директора; без фиктивного урона/награды |
| D3 — Генерация, не скрипт | В рамках одной кампании >=3 различных реально проверенных combinations из NPC/positions/levels/equip/class/party (не один exact scripted replay), минимум 2 разных NPC templates или класса где законно; причина несовместимых сцен логируется и директор переходит к следующей |
| D4 — История | Структурные versioned episode records с actual/unknown facts, list of offered actions, provenance; SQL de-dup/replay guard, bounded retention и экспорт. Обычные missed/failed сценарии маркируются без выдачи NATIVE learning credit |
| D5 — Учится сам | После достижения порога директор **без Codex** инициирует offline train, independent audit и создает candidate DRAFT, публикует метрики и решает accept/reject по явным TEST правилам; при достаточном качестве автоматически принимает модель только в own TEST и может cold-ON проверить. Если данных недостаточно — director продолжает кампанию, честный статус `COLLECTING`, не фальшивый SUCCESS |
| D6 — Управление | START/STATUS/PAUSE/RESUME/STOP/REPORT/EXPORT реально работают; idempotent; STOP останавливает только собственные процессы, закрывает SQL/JVM/порты и оставляет DATA/веса/логи; отчёт русский |
| D7 — Recovery | После обычного аварийного завершения собственного training процесса/штатного restart директор не повторяет committed NATIVE event, восстанавливает pending queue/checkpoint; чужие PID/ports не трогает |
| D8 — Безопасность | G0/PID/FILETIME/SID, loopback listener, защищённые хеши, привилегии и права, cross-schema guard, Geo203/protected207, F7/F8 и owner/Party/PvP regressions; no SQL in Combat tick |
| D9 — Бюджет и реальная эффективность | Отдельно измерены episodes/hour, NATIVE examples/hour, peak native selection latency incl features, CPU/RAM/DB; никакого потока на фантома; при разрастании работы adaptive throttling |
| D10 — доставка | Source + scripts + config + docs/report + runbook + тесты + output models (не приватные SQL credentials); `docs/phantoms/reports/PHANTOM-LEARNING-002.md` с SHA и Git, Green/Partial честно, единственный итоговый ZIP экспортируется по запросу, оригиналы и trained weights в локальном TEST сохраняются |

Доказательства обучения не заменяются синтетической математикой. Важнее реально автономная работа и корректный `NATIVE→train→evaluate` loop, чем достижение несуществующего заранее гарантированного процента побед. Full глубокого PvP, Raid, Olympiad, Siege, Team AI, GPU, 128 native visible сюда НЕ входят и **никогда не должны быть заявлены** как реализованные.

`D5` допускает отсутствие улучшившейся модели, если аудит отверг кандидата на реальных данных, но *сама автоматическая попытка обучения и независимой оценки* обязательна. История попыток не стирается. Smoke unattended включает не менее 10 минут после parent exit либо несколько реальных эпизодов с проверкой heartbeat и checkpoint в независимом процессе; время подбирается по фактическому темпу мира, но нельзя заменить одним синтетическим loop.
