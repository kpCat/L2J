# TEST_PLAN074 — интеграция без марафона и повторного клонирования harness

## Время и ресурсы

- Цель4–5h; HARD7h от начала. Intake/source-specific read-first≤20min; работа на A/B/C/D до6h; последний час только essential tests/build/Main/restart/report/push/STOP. На проваленной lane ≤2 одинаковых native attempts; **другая новая причина** допускает один короткий контроль, не 30 recovery fixtures.
- **Не повторять 35 kills073,** уровень1→20, часовые soak, всех классов/локаций, тысячный PathFinding. Не менять useable business policy ради подготовки fixture.
- 1 раз сборка targeted classes для focused, 1 final Ant JAR из **committed source**. Допускается максимум одна дополнительная итоговая сборка при подтверждённой source проблеме. Не пересобирать JAR только из-за report/TSV/UTF8 README. Existing JDK25/Ant, client не нужен.
- Проверки anti-dupe: после native продаж/покупки source SQL receipt/item/wallet, delayed/replayed result, newJVM exact profile/owner. Инструменты и схема OWN074 не должны обращаться к073 DB без явного read-only ограниченного доступа.

## Единая игровая acceptance-сцена A+B+C

1. Read-only PREMEASUREMENT выбрать настоящее место mob+merchant/станцию с настоящими D1463, реальной ценой/tax, безопасными точками возле NPC, двусторонней leg геометрией; level20+ actor должен уметь получать XP/SP и collectible legal loot. **Отдельно показать existing usefulPurchase, fees/deadline, true speed/gear/shot-per-hit** до создания измеряемого аккаунта.
2. Predeclared actor с теми же физическими native inventory и обычным кошельком, например ранее накопленная разумная сумма (FIXTURE_PRIOR_EARNINGS), а не фальшивый заработок за текущую сессию. Перед покупкой как минимум1–3 реальных rewarded kill с фактической прибавкой Adena или sale surplus; не требовать, чтобы полный бюджет был заработан за один тестовый эпизод. Добытый item физически зафиксировать и при необходимости показать KEEP/SELL_NPC native policy + receipt.
3. Старт из реальной игровой точки, native submission/Geo пути и реальная физическая покупка (item1463 matching-grade); `PhantomCommerceReceipt.State.COMMITTED`, баланс уменьшается, предметов становится больше; повтор с тем же operationKey не дублирует. Если запрос отказал — один доказательный trace identity/status/corridor/path/cooldown и адресное исправление production.
4. Один реальный native обратный маршрут к farm (или корректная перераздача farm-цели, если точка стала бесполезной); ≥2 rewarded battles, inventory shots decremented. NewJVM same actor stored wallet/remaining shots/EXP/SP/HP/goal/receipt; минимум один полезный новый native акт.
5. После упадка/diminishing reward на текущем споте — подтвердить выбор **другого anchor/NPC** и хотя бы первые физически доказанные WALK/GK segment или bounded background owner-approved committed leg. Для далёкого спота не требуется пройти 15км в одной тестовой JVM; пройти безопасную первую ступень, показать сохранённую допустимую цель и её дальнейшее продвижение. No teleport cheat.
6. Две negative-control ситуации: route SEARCH_LIMIT vs true NO_PATH, и чужой/protected loot или несовместимый grade/no money; без неожиданных inventory writes.

## Отдельный короткий PvP фрагмент D

- Для PVP найти первую законную сцену с **близким Native safe waypoint**, вместо требования непременно 3-километрового беспрепятственного коридора до CITY_CENTER; не создавать противника, если местность запрещает бой/движение.
- Бой одного normal Player-owned тестового NPC/opponent с текущими learned skills/shot and physical source; native reason ACTUAL_ATTACK permitted; CP item5591/5592 из подтверждённой экономической stock-системы или finite declared stock (отличать). Shortage при физическом исчерпании, реальный retreat lease move, CP reuse. Даже при `SEARCH_LIMIT` фиксировать bounded safe fallback и запрет наступления; не объявлять ARRIVED без движения.
- Проверить PvP/PK legal negatives и отсутствие human/GM fake whitelist. Full proactive-vs-clan-war matrix не требовать.

## Source/JAR/runtime

- Task-owned `l2jmobiush5_phantom_test_074`, `l2jmobiush5_phantom_release_074`; создать только если отсутствуют и origin/root/authority подтверждены. `Economy070Shop.verifySchema` с существующей migration071 и authority.task_id74; чужие schemas never accepted. Не брать старые credentials из private073.
- Изолированные test ports proposed2254/7954/control9254, release2106/7777/control9154 — использовать **только если свободны и принадлежат OWN074**; при занятии `BLOCKED_PORT`, чужой listener не отключать. Учитывать что release2106/7777 исторически используются отдельными остановленными кандидатами; не стартовать их параллельно.
- 203 физические Geo source hashes (read-only), JAR из committed SHA и manifest; один normal Game/Main3–6min + один sameDB/sameJAR restart2–4min (если есть полезная активность), native graceful STOP, DB connections0, JVM0, listeners0. No account/client human join.
- `JVM0` означает только OWN074 Java, не глобальный процесс пользователя. Не делать full scans старой FS/DB; ровно selected protected hashes.

## Verification budget и provenance

- Focused existing suites: supply native/product contracts, combat/loot, planner/navigation/VisibleFarmTravel, PvP/retreat; JUnit/full global DB marathon не требуется. Source/local static checks first, native evidence next, one full release upon meaningful product changes.
- Test fixture setup только BEFORE measurement; state snapshot (actor/profile/root/schema) и items/wallet bound. Distinguish `SOURCE_PRESENT`, `CONTRACT_PASS`, `NATIVE_EFFECT`, `NATIVE_PAID`, `NATIVE_EARNED`, `SAME_ACTOR_RESTART` и `NOT_OBSERVED`.
- Never claim PASS for a subsystem from contract-only test, or native arrival from `plan FOUND`. For each refusal include exact native status, current/dest xyz, offer/seller identity/ownership, planned future transition/retry reason and visible limitations.
- `REPORT`: `docs/phantoms/reports/PHANTOM-LIVING-SYSTEMS-074.md`, `...-data/ACCEPTANCE074.tsv`, compact `GATE_EVIDENCE074.tsv`, `SOURCE_CHANGE074.tsv` (actual product improvements), `GIT074.md`, `RUNTIME074.md`. **At least four clear feature-diff rows or honest cause NOT_CHANGED**; one paragraph per feature describing what changed and what actor now does.
- Separate full scans for mojibake markers and escaped Cyrillic (all patterns required by AGENTS); Windows PowerShell5 `.ps1` readable Cyrillic UTF-8 BOM where needed; no escaped text to hide encoding bug.
