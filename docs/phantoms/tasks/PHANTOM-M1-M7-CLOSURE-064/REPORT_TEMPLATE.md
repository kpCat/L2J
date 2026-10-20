# Итог064 — краткий формат, не создавать сотни страниц

Создать `docs/phantoms/reports/PHANTOM-M1-M7-CLOSURE-064.md` (ориентир ≤180 строк), `ACCEPTANCE064.tsv` и при необходимости приватный `HANDOFF064` в `.phantom-local/task064`.

- `STATUS`: `SUCCESS`/`PARTIAL`/`BLOCKED`; не обещать `ALL_M1_M7_PASS` при технических открытых строках.
- `TIME`: UTC start/deadline, elapsed и резерв, включая compactions.
- `GIT`: baseline/HEAD/remote, exact commits и source/JAR hashes; ignored/private inventory.
- `ENV`: обнаруженные Java/Ant, источник GeoData (не публикуя файлы), новая схема/порты и own manifests; old-state unchanged.
- `M1…M7`: одна таблица `source/implementation/actual native scene/ordinary scene/old proof reuse/open blocker/ownerQA`.
- `CAUSES`: сколько архитектурных классов дефектов исправлено; ссылки на focused RED→GREEN. Не считать lines changed критерием.
- `VALIDATION`: affected suites, old111, compile/test exit codes, clean JAR, entire run Start/Status/Stop + sameDB and stock facts; точный terminal state.
- `FAIL`: прежние FAIL сохранены, новый blocker с причиной, без необязательных повторов.
- `NEXT`: одна конкретная незавершённая зависимость; не предлагать автоматически продлить TASK064.
- `RESOURCE`: optional private list найденных устаревших custom semantic/Geo/config ресурсов, не перенесённых по причине несовместимости; ни один secret не commit.
