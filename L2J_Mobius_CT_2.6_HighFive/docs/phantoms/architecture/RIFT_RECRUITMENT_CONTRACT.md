# Rift readiness и advanced party recruitment

## Граница ответственности

`rift.prepare` является Phantom-only orchestration поверх принятого Goal 017 Party kernel. Новый party kernel не создаётся: canonical live `Party` roster, `RoleMatcher`, приглашения и shared party route используются через существующие контракты.

Factual catalog читается из текущего High Five `DimensionalRift.xml`, XSD, `GeneralConfig.RIFT_*` и read-only snapshot exact runtime entry owner. Entry item — `7079` (Dimensional Fragment). Уровни, стоимость, комнаты, capacity и минимальный размер не выводятся эвристически и не дублируются как выдуманные значения.

## Снимок готовности

Снимок включает mixed Phantom/ordinary real Player roster и проверяет:

- точный full-party и current minimum party size;
- mandatory/optional role vacancies через `RoleMatcher`;
- level, alive, vitals, equipment, capability, supplies и travel readiness каждого участника;
- bounded discovery не более 32 кандидатов;
- deterministic ranking, one pending invite и durable refusal cooldown;
- Phantom accept/refuse и обычное согласие реального игрока без auto-accept;
- restart reconciliation и передачу shared party route существующему Goal 017.

`READY_TO_ENTER` только фиксирует наблюдаемую готовность. Контур не вызывает вход в Rift, не списывает предметы, не телепортирует, не переключает комнаты и не запускает combat.

## Ограничения исполнения

Нет fake `GameClient`, packet invocation, глобального online-player scan, собственного worker/thread/executor/Future/task или отдельного scheduler. Состояние сохраняется bounded-компонентом профиля; production runtime не зависит от тестового seed.

## Corrective route и managed-consent closure

Goal 017 остаётся единственным владельцем shared route. Его bounded `RouteActivity` объединяет planner-pending ownership и persisted `PLANNING`/`MOVING`/`REGROUPING`; пока активность nonterminal, content binding не стирает manifest, не создаёт второй route и не допускает `READY_TO_ENTER`. `ARRIVED`/`FAILED` сначала проходят Goal 017 terminal cleanup, и только последующее наблюдение с `RouteActivity.NONE` может подтвердить stable binding.

Production `PhantomRiftService.evaluateManagedInvitation(...)` непосредственно перед `ACCEPT` повторно читает exact canonical invitation, current goal/binding, всё ещё отсутствующую vacancy, current invitee eligibility, candidate facts и invitee-to-leader relationship. Stale/missing evidence даёт `DEFER`, запрещающая eligibility или relationship policy — `REFUSE`. Explicit `party.join`/conversation precedence сохраняется, ordinary real-player consent этим managed policy не подменяется.
