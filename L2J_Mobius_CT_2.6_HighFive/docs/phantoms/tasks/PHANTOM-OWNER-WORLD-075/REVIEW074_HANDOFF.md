# REVIEW074_HANDOFF — независимый intake перед TASK075 (24.10.2026)

**Проверенный GitHub ref**: `feature/phantom-world` на момент подготовки: `8ac113257bea0cd3ea241b708889b42b42b5b961`.
**TASK074 baseline**: `6057cc69728e5050d79806e5e6aa3c5a2bf898c8`; compare `6057cc6...8ac1132` = 11 коммитов вперед, 0 позади.
**Production source LAST**: `104f87e2e85935657caf082f4f08277a146a7925`; до `8ac1132` ещё 5 коммитов только tests/docs/.gitattributes; changed production Java после104f **0**.
**Бинарь (по опубликованному BUILD074.json)**: GameServer.jar SHA256 `9b24ba22b14ce28585a2ec61533b87372d82c9be44b3fb439f8a8bedd9694c72`. Это проверка опубликованного provenance, НЕ независимое локальное воспроизведение JAR.

## Статус 074 (цель закрыта Codex как BLOCKED/PARTIAL; НЕ COMPLETE)

- A: PASS **основной** цепи (штатный SELL prior weapon295A -> BUY180D1463/3780A -> WALK back -> 2kills/shots180->159 -> newJVM159->150). Финансовый источник был заранее подготовлен (4000A +295A prior property), после 3 prebuy kills Adena=0. Не объявлять фарм-Adena доказанным.
- B: PARTIAL. `PhantomHistoricalBackgroundPlanner/Service` реализуют local→global fallback и pre-CAS route admission. Последний native тест прошёл7548.26 units **к продавцу**; farm CAS=false, farm segment не совершён, конечный route `ROUTE_BUDGET_EXCEEDED`, durable coordinates сохранены; не подменять farm поездкой за снабжением.
- C: PARTIAL. Native legal pickup/`CONSUMED_NATIVE` и KEEP материалов/сосок есть, но уже принятый pickup может остаться в `missed-wake` (пример item8611); не объявлять устранённым.
- D: PARTIAL. Native skills/shots/CP и отдельная атака/553-unit retreat доказаны в разных сценах; full unbroken chain финального JAR не доказана.
- Экономика: committed B BUY (60@80), component BUY(1 B crystal@9000, 54 Soul Ore@250) + MANUFACTURE22 за100A ->450B сосок, native charge449. Эпизоды используют подготовленные wallets/stock, а не заработанные за 074; универсальные self-craft FARM-child, NPC/warehouse BOM, split supplies, normal/jewelry crystal, interrupted BOM остаются PARTIAL.
- 132/132 focused +4 standalone заявлены и агрегаты в FOCUSED074.tsv арифметически согласуются; JAR и Windows STOP (DB0/JVM0/listeners0) подтверждены опубликованными журналами, но не повторены этим review.

## Граница влияния на 075

1. Не создавать отдельную R2/076 только за PARTIAL074. Новый продуктовый приоритет — HARD A/B/C и D/E задачи 075.
2. Сохранить 074 native `World` private-store publication (только SELL/MANUFACTURE), `PhantomOfflineSupply` свежую проверку stock/price/ownership/quote, native ledger/restart. Любое добавление BUY для 075 broker не должно сломать текущую индексацию и изоляцию.
3. `PhantomAutonomousMarketProducer` (обычные действующие персонажи) и `PhantomOfflineMerchantService` (отдельные prepared identities) — разные owner'ы. Запрещено считать 200 подготовленных лавок реальной сменной торговлей заработавших товары персонажей.
4. 075 D/E не должны полагаться на предположение, что B/C/D уже PASS, и не должны раздавать ресурсы, чтобы получить красивую native-сцену. Если недоступен законно заработанный surplus, D/E остаются PARTIAL с точным объяснением.
5. Защищённый repo/module, local/remote HEAD и user dirt обязаны быть прочитаны ЗАНОВО Codex перед записью, т.к. 8ac — снимок для handoff, а не вечное разрешение на работу со stale branch.

Источник: [TASK074 report](https://github.com/kpCat/L2J/blob/8ac113257bea0cd3ea241b708889b42b42b5b961/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LIVING-SYSTEMS-074.md), [native evidence](https://github.com/kpCat/L2J/tree/8ac113257bea0cd3ea241b708889b42b42b5b961/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LIVING-SYSTEMS-074-data).
