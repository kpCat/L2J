# План работ014

## A. Read-first и G0

- Проверить актуальный origin/feature, local safe checkout, тестовую SQL, dirty MAIN, external worktrees, old 013/012 owner state.
- Зафиксировать exact source allowlist, SHA/JAR/config/Geo203/protected207 и лимиты. В отдельном report — manual acceptance предыдущего PARTIAL013 без выдумывания FULL.

## B. Профиль до кода

- Воспроизвести узкий bounded headless этап либо используй старые доказательства как upper-bound; instrument под текущим профилем.
- Замерить реально потраченное время, CPU/SQL, стоимость на отказ, split by source/roster/materialize/natural recovery/A-B.
- Назвать проверенный root cause: если не выяснен — не менять код наугад.

## C. RED → GREEN

- Контракты быстрого deny, stale cache, safety invariants, bool status, duplicate reservations, iteration fairness и distinct target.
- Только после FAILED tests — минимально адресовать dominant measured stage. Потолок двух direct fixes, не проектировать новый серверный AI.

## D. SKILL TARGET audit + BUFF read-only audit

- Обязательные тесты действительных изученных/enchant old skills, legal enemy damage vs friendly heal, invalid targets, no re-buff healthy, no false class specific claims.
- Никаких production changes в buff/party classes, если они не разрешены отдельно.

## E. Полный HEADLESS-ready

- Сохранить естественные MP/HP, owner, Гео, справедливые A/B, title/candidate/roster source SHA. Доказать >=3 cells, >=2 classes, Solo+Party, 2 legal actions, свежесть и physical equality. Без этого — GS0 и PARTIAL.

## F. Native (только после E)

- Max3 GS/60min, один cohort 48/51 native episode attempts в собственной TEST. Не повторять reserved slots. При достаточном gradient/held-out — один FIT и холодный readback. Иначе честное insufficient data; не заявлять improved/model prod.

## G. Завершение

- Test-Training.ps1 focused, targeted Ant 013/012/011/009/008/007/JAR, `git diff --check`, encoding scans, native exit and postflight, docs/result/evidence/push/readback; manual gate REQUIRED; следующую задачу НЕ начинать.
