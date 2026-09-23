# LIVE-002-A — source-derived world coverage registry

Дата: 23.09.2026. Ветка: `feature/phantom-world`. Обязательный исходный tracked HEAD: `c325baa8aac21410e7153bfc1dd43604dbc29816`. Implementation commit: `94a1461539a86d8fcc52ef36d2f168aa6fc9a851`. Итоговый документационный commit указан в handoff после push: само включение его SHA в этот commit невозможно без изменения SHA.

**CHECKPOINT=GREEN для фактического реестра.** Это только static/source validation. Geodata, pathing и topology integration остаются LIVE-002-B/C.

## Архитектура и read set

`tools/phantom-world-data/Generate-WorldCoverage.ps1` читает native `<spawn>` из `data/spawns` и `<instance><spawnlist><group>` из `data/instances`, извлекает NPC level/type/status из `data/stats/npcs`, native map grid из `data/mapregion`, а остальные указанные корни валидирует как XML и хэширует. Входы читаются из source `dist/game/data`; private runtime не используется. Каждый group получает одну строку. Ключ — SHA-256 от lower-case slash-normalized relative path, native group name, instance ID, сортированных geometry и NPC facts, condition facts. Списки в TSV сортируются ordinal и соединяются `|`; TSV имеет UTF-8 без BOM и LF. Упорядоченный manifest — compact JSON без времени генерации.

LLM read set: `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md`, секции `ROADMAP.md` и `STATE.md`; `SpawnData.java`, `NpcData.java`, `MapRegionData.java`, `Instance.java` (точный блок spawnlist), `NpcTemplate.java` (type/flags), `PhantomTopologyLoader.java`, `PhantomTopologyValidationBackend.java`, `L2jTopologyValidationBackend.java`, три локальные topology/knowledge suites и представительские XML `TalkingIslandMonsters.xml`, один NPC stats XML, `talking_island_town.xml`, `UrbanArea.xml`, `30256.xml`, `high-five-core.xml`. Перед patch были только точечные поиски native loaders, XML синтаксиса, названия Ruins и локальных test/PowerShell аналогов. Весь корпус XML не открывался в контексте модели; его обошёл генератор. Отдельные `AGENTS.md`, `CURRENT_GENERATOR_STATE.*`, `CONTEXT_INDEX.md`, `DEVELOPMENT_CHAT_HANDOFF.md` и code-map в модуле не найдены.

## Corpus и результаты

| Input root | XML файлов |
|---|---:|
| `data/spawns` | 187 |
| `data/stats/npcs` | 137 |
| `data/mapregion` | 28 |
| `data/teleporters` | 198 |
| `data/zones` | 48 |
| `data/instances` | 142 |
| **Всего** | **740** |

Все 740 XML распарсены, ошибок парсинга 0. Независимый validator пересчитал native groups: **3 752**, выдано **3 752** строк, duplicate coverage keys **0**. Уникальных referenced NPC IDs 3 908, отсутствующих NPC IDs **0**.

| Class / status | Строк |
|---|---:|
| `ORDINARY_WORLD / READY_STATIC` | 2 652 |
| `CONDITIONAL_WORLD / CONDITIONAL` | 19 |
| `INSTANCE / INSTANCE` | 260 |
| `RAID / RAID` | 2 |
| `EVENT_OR_SCRIPTED / EXCLUDED` | 0 |
| `NON_FARMING / EXCLUDED` | 819 |
| `UNRESOLVED / BLOCKED_SOURCE` | 0 |

`NEEDS_GEODATA` здесь 0: геоданные не проверялись. `READY_STATIC` требует instance 0, положительный configured amount, source geometry и каждый NPC native type `Monster` с attackable/targetable flags. 819 групп с иными native types/flags, включая смешанные группы, отмечены `NON_FARMING / EXCLUDED`; они не превращены в обычные farming-кандидаты. Raid и instance имеют отдельные классы. В данном source corpus событийному NPC type не соответствовала ни одна группа; fixture проверяет этот класс. У unsupported syntax, missing NPC и unresolved geometry есть явные blocked причины; production таких строк нет. Для polygon sample X/Y берутся из исходной вершины, Z остаётся пустым; centroid не вычисляется.

`Ruins of Despair` фактически назван в `data/teleporters/town/30256.xml` как location `(-19120, 136816, -3752)`; точное имя отсутствует в native spawn group names и в generated registry. В LIVE-002-B надо выполнить source-backed spatial association teleporter/zone/mapregion со spawn groups и проверить её геоданными; приписывать текущим группам это имя по памяти или приблизительной близости здесь нельзя.

## Детерминизм и проверки

- Generator SHA-256: `2bb686b8f0d6494f69ee92c6098390388c1670c9617407bb9cf75def798d1c2a`.
- Input aggregate SHA-256: `468f2411920df9d5a40833a97ecdff70fba7a1586080e20fa8fe33037f8b7695`.
- `WORLD_COVERAGE.tsv` SHA-256: `84af90619ff959af64119ad079cf0f2b85692dec2aa985d2314185b059c76c8e`.
- Canonical `WORLD_DATA_MANIFEST.json` SHA-256: `94ac62cbe005e6df43529506fbb7af6511525a8d3bad355e66413583788a6bf4`.

PowerShell 5.1 и `pwsh 7` на неизменных входах дали побайтно одинаковые TSV и manifest. Fixture GREEN в обеих версиях: ordinary, conditional, instance, raid, event, non-farming, missing NPC, unsupported child, mixed geometry/multiple NPCs, reordering, path case, duplicate key rejection и два одинаковых запуска. Focused production validator GREEN: input/output/generator hashes, 3 752 native groups, все row/class/status counts и READY_STATIC invariants. Raw logs: `.phantom-local/logs/LIVE-002-A/`. `git diff --check` GREEN. Full `ant verify` runs: **0**. `ant jar` runs: **0**.

`high-five-core.xml` не менялся. Runtime/DB/config/rates/heap/schedules/population не читались как source и не менялись; runtime/DB команды не запускались. Все pre-existing dirty/untracked пути оставлены вне staging и commit. После принятия этого checkpoint следующий отдельный task — LIVE-002-B source-derived topology/route generation; автоматически он не начинался.

Goal использован один раз для LIVE-002-A; elapsed на момент подготовки отчёта около 17 минут.
