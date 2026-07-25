# CONTEXT — Task 001A review closure

## 1. Причина задачи

Task 001 была выполнена Codex успешно. После независимого GitHub-ревью архитектурная работа признана корректной, однако выявлены документальные follow-up замечания.

Пользователь отдельно уточнил, что `Agents.md` был добавлен им самостоятельно через amend исходного commit Codex.

## 2. Git provenance

### Original Codex result

```text
Commit: e7dcf575dd45a94c83560fd140144635bbf96e37
Parent: 16d61833b3983a3976583d0e4813e0de9457a52f
Message: docs(phantoms): complete task 001 baseline audit
```

Diff исходного commit содержал:

- Task 001 package;
- audits;
- ADR;
- report;
- `verify-task-001.ps1`.

Production `.java`, SQL, runtime config и другие хроники не изменялись.

### User amend

Пользователь затем самостоятельно добавил:

```text
L2J_Mobius_CT_2.6_HighFive/Agents.md
```

и amend-ил commit. Текущий ожидаемый branch tip перед Task 001A:

```text
cdca7a3d96554285eb8c992fa14f65b27f7f36ae
```

Это пользовательское действие. Нельзя приписывать amend Codex.

Поскольку amend переписал commit, `e7dcf575...` и `cdca7a3d...` имеют общего parent `16d61833...`, но не являются ancestor друг друга.

## 3. Независимый review verdict

- Original Task 001 content: `ACCEPT`.
- Amended branch state: `ACCEPT WITH FOLLOW-UP`.
- P0/P1: не найдено.
- Gate verdict `FEASIBLE_WITH_SEAM`: принят.
- ADR 0001: остаётся `Proposed`.

## 4. Замечание к отчёту Task 001

В committed report остались placeholders:

```text
Final-commit verifier run 1: required after commit.
Final-commit verifier run 2: required after commit.
```

и:

```text
Final commit run 1: to be recorded after commit.
Final commit run 2: to be recorded after commit.
```

Финальный handoff Codex сообщил реальные результаты:

- pre-commit `43/43`;
- final run 1 `43/43`;
- final run 2 `43/43`;
- final outputs identical;
- exit code `0`.

Нужно записать эти факты, сохранив provenance к `e7dcf575...`.

## 5. Замечания к Agents.md

### Pathfinding

`Agents.md` содержит краткое:

```text
геодата пока отсутствует, pathfinding отключён
```

Task 001 baseline уточнил:

- geodata directory содержит только `Readme.txt`;
- region files отсутствуют;
- `GeoEngine.ini` содержит `PathFinding = 2`;
- runtime server не запускался;
- fallback behavior не подтверждён.

Новая формулировка должна объединять эти факты и не менять master plan.

### Headless wording

`Agents.md` упоминает:

```text
headless client adapter
```

Task 001 отверг fake/null-network `GameClient`.

Принятый контракт:

- canonical `Player`;
- small outbound/session seam;
- headless output/packet sink;
- zero network writes;
- required `ServerPacket.runImpl(Player)` exactly once;
- action facade вместо client packet API.

## 6. Почему старый verifier не меняется

`verify-task-001.ps1` проверяет исходный exact scope Task 001 и намеренно не разрешает `Agents.md`.

Расширять его allowlist задним числом нельзя: это изменило бы исторический acceptance contract.

Task 001A получает отдельный verifier и отдельный base commit.

## 7. Разрешённый результат

Task 001A не должна самостоятельно объявлять manual review окончательно пройденным.

Правильный статус после Codex:

```text
IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

После отчёта Task 001A независимое ревью проверит реальный commit/diff. Только затем может быть разрешена Task 002.
