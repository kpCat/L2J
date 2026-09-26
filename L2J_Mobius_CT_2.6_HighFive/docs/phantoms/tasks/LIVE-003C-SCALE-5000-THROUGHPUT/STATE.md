# LIVE-003C — state checkpoint, 2026-09-26

- **GREEN:** creation-rate RED воспроизведён через WARM population handler (`0/1`, ожидаемый RETRY вместо SUCCESS); bounded same-handler continuation дал GREEN `3/3` и сохранил durable стадии, cancellation, terminal outcomes и предел четыре вызова.
- Code commit/push: `a4bb55270d33bfdd0ce96e0367dd509b5c3358b2` на `feature/phantom-world`. Обязательные creation/reconciliation/lifecycle/Goal033/Goal033A targets — exit 0; `git diff --check` чистый.
- Clean detached `ant jar` на exact code SHA — exit 0. Доставлен только private GameServer.jar, SHA-256 `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`; protected hashes и budgets сохранились.
- PLAY baseline 2668 managed / 2666 linked. Существующая population без reset достигла 5000/5000 в `20:17:23.5151358+03:00` за 11.4977 минуты после запуска: 2332 новых managed, 202.82 профиля/мин. Unique names/accounts 5000/5000, duplicates 0.
- 15-минутный soak завершён `20:32:26.4548589+03:00`. Все samples 5000/5000; общий ряд 30 samples: heap 80.71–93.85%, максимум два подряд >90%, threads 160–166, DB connections 13–15/151, fatal 0. Background committed positions 4978; во время soak обновлялись background и ecology rows.
- Canonical owned STOP выполнен: Login/Game STOPPED, staleRecord=False, ports 2106/9014/7777 closed. Post-stop PLAY 5000 managed / 5000 linked, unique names/accounts 5000/5000, duplicates 0, target 5000, все профили сохранены.
- Подробные доказательства: `EVIDENCE.md`, `LIVE-003C-SCALE-5000-THROUGHPUT.md`, `LIVE003C_RUNTIME_5000.tsv`. Runtime 10000 и LIVE-004/005 не начаты.
