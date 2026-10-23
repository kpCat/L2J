# План исполнения 070

Source of truth: TASK.md, AUTHORITY.md и PRODUCT_DECISIONS.md этого пакета.
Baseline: dd57479f28db8ed2cb6d824b645c57dcb49dfa18, feature/phantom-world; remote совпал перед изменениями.

Пользователь явно утвердил автономное исполнение без промежуточного согласования. Новый worktree запрещён AUTHORITY070. Разрешённый bounded exception более 10 файлов ограничен перечисленными ниже семействами одной поставки; Studio, клиент и старые runtime/БД не изменяются.

- [ ] P0: причинный разбор Queen Ant и M1, затем focused исправление/инструментирование, максимум 70 минут. Никаких refill, reset incident/debt или изменения boss XML.
- [ ] P1: canonical pricing и каталоги: расширение tools/phantom-economy, source-backed XML/TSV, отдельные AA/9912/Codex/quest цены. Plan/Apply/Rollback только OWN070 STOPPED, exact preimages и SHA, стабильный x10→x20→x10.
- [ ] P2: persistent shop в gameserver/qol по существующим native inventory/DB контрактам; stock, savings, receipts, stale quote и fail-closed recovery. Доставка ровно один раз должна быть доказана, иначе SKU блокируется.
- [ ] P3: premium через существующий PremiumManager/loot evaluator; chance2/amount1, Adena net2, account term; native rune audit. Существующие policies suppression/quests сохраняются.
- [ ] P4: HomeBoard/HTML, Giran blacksmith, Mammon, QoL: один источник цены, native controls и проверка normal access0; обычный готовый шмот остаётся запрещён.
- [ ] P5: один committed build JDK25/Ant, focused native TEST070, одна обычная Main и sameDB restart, самостоятельная RELEASE070 с 203 physical Geo и 4g heap; обе среды STOPPED.
- [ ] Финал: exact scope/encoding/checks, честная ACCEPTANCE070, отчёт, exact-path commit и non-force push только feature/phantom-world.

Review focus: crash между native inventory и ledger; повторный/stale bypass; account/phantom eligibility; cross-list дешёвые цены; рестарт, не создающий stock/субсидию повторно.

Рабочий журнал и приватные receipts: .phantom-local/task070 (не публикуется). Решения и незакрытые gates переносятся в финальный отчёт.
