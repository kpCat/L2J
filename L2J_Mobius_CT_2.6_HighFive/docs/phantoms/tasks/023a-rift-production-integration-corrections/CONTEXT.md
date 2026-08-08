# Контекст corrective Goal 023A

## Проект

- Репозиторий: `https://github.com/kpCat/L2J`
- Git root: `C:\Users\endim\L2J_Mobius\`
- Единственный рабочий модуль: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Ветка: `feature/phantom-world`
- Required parent: `840e159a989f6372da9c471c915413f1e4470daf`
- Parent Goal 023 commit: `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`
- JDK 25, Apache Ant.
- MariaDB test endpoint: `127.0.0.1:3308`, DB `l2jmobiush5_phantom_test`, local credentials `root/root`.
- Production DB `l2jmobiush5` использовать запрещено.
- Seed Goal 023A: `23002311`.

## Почему создан corrective Goal

Independent review commit `840e159a...` не принимает Goal 023. Реализация содержит полезную и в основном корректную factual/readiness основу, но обязательные production-сценарии recruitment/route/restart не доказаны и в нескольких случаях фактически недостижимы.

Решение review:

```text
Goal 023 @ 840e159a989f6372da9c471c915413f1e4470daf
CHANGES_REQUIRED
Corrective Goal 023A required
Goal 024+ NOT_STARTED
```

## Что сохраняется без переписывания

Следующие части Goal 023 считаются полезной базой и не должны быть заменены без конкретного проваленного теста или доказанного дефекта:

- side-effect-free `DimensionalRiftManager.entryReadiness(...)`;
- строгий factual Rift catalog и provenance текущих High Five XML/XSD/config/runtime facts;
- Phantom-only composition policy как отдельный источник policy data;
- canonical live Party roster как источник истины;
- Goal 017 `PhantomPartyRoleMatcher` для assignments/vacancies;
- восемь typed readiness dimensions;
- запрет входа в Rift, списания items, teleport/room jump/combat;
- один `rift.prepare` Goal и отсутствие собственного worker/thread/timer;
- ordinary real-player consent через canonical `PartyInvitationService`.

Goal 023A — не переписывание Rift подсистемы. Это bounded closure production integration, target-side Phantom consent, exact identity/restart и acceptance proof.

## Обязательные project docs

До изменения кода прочитать:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
2. `docs/PHANTOM_BOTS_ROADMAP.md`
3. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`
4. `docs/phantoms/TASK_PACKAGE_STANDARD.md`
5. `docs/phantoms/CODEX_REPORT_TEMPLATE.md`
6. `docs/phantoms/tasks/023-rift-advanced-party-recruitment/TASK.md`
7. `docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md`
8. `docs/phantoms/reports/023-rift-advanced-party-recruitment.md`
9. `docs/phantoms/reviews/023-independent-review.md`
10. весь пакет `docs/phantoms/tasks/023a-rift-production-integration-corrections/`.

## Ограничение чтения

После обязательных документов читать только exact files из `TASK.md`/`REVIEW_FINDINGS.md` и непосредственные type definitions, без рекурсивного аудита всех Goal 005–023. Повторное чтение уже описанного factual Rift XML не требуется, кроме проверки, что corrective code не изменил factual hashes/entry semantics.

## Git и геодата

- Другие хроники не изменять.
- `.l2j` не читать массово, не изменять, не удалять, не добавлять в commit.
- Required parent должен быть exact HEAD до начала изменений.
- При любом итоговом статусе создать ordinary commit и push текущей ветки по workflow contract.
