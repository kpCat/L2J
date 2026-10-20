# PSS-009 — формат выдачи результата Codex

Нужно предоставить короткое финальное сообщение, ссылки на опубликованные файлы отчёта/commit, с конкретикой:

1. Исходный main SHA = `cddf9a65ffc5b052c110b264c4fe3973f8e6a37c`; новый SHA/parent, обычный non-force push и совпадение HEAD/main/public, дерево clean.
2. Измеренная root cause: реальные Before/After bounds Page/детей с одним-двумя показательными примерами, а не догадка. Почему табы вправо/вниз улетали.
3. Точный список изменённых файлов, особенно Designer.cs/resx/VS nesting. Нет изменений Core, L2J или private данных.
4. New `--pss-009-layout` RED/GREEN с реальными exit code; шесть вкладок, first/second switching, min/normal/max, baseline fixed; legacy `scripts/Build-Verify.ps1` и STA 004–008 outcomes.
5. Каждый статус отдельно: `MAIN_TAB_LAYOUT`, `MODAL_LAYOUT`, `PHYSICAL_UI`, `DPI_100`, `DPI_150`, `VS_DESIGNER_ROUNDTRIP`, `LIVE_LM`, `JAVA`, `L2J`. Не выдавать static за physical.
6. Manual check для владельца: что нажать и что увидеть, без необходимости загрузки Gemma, семантического редактирования или установки L2J.
7. Reproduction screenshots/desktop в ignored artifacts, не в public repo; никакого review ZIP.

STOP после PSS-009. Не выполнять PSS-010 и не заявлять о полном продакшен-готовности Semantic Pack.
