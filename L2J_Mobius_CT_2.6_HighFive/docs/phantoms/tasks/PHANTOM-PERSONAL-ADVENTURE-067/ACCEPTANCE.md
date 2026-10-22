# ACCEPTANCE067 — самостоятельные продуктовые features, честные статусы

## Независимые статусы вместо бесконечных R2

| Код | Scope | PASS когда |
|---|---|---|
| G0 | git и protected resources | live remote/branch/dirt проверены, нет старых DB/release изменений, new schema/manifest принадлежат 067 |
| G1 | персональная защита | policy allowlisted access0 ordinary, non-allowlisted/Phantom/headless/masterOFF строго denied, без прав GM и без глобального снятия party |
| G2 | Kamaloka solo+party | штатный solo instance и отдельный Labyrinth branch имеют native enter/exit/reuse и настоящая Party из2+ вошла в один instanceId |
| G3 | Rift solo | настоящий безопасный Rift enter/fee/room timer/exit на owner без Party, либо `PARTIAL` с доказанной причиной, без глобального min-party bypass |
| G4 | Wondrous Cubic | id10632 native use/reward, allowlisted reuse60m (включая старые timestamps), чужим24h, restart/duplicate rewards guard |
| G5 | Fishing assist | after manual fish start, native Reel/Pump tick chooses correct action and spends real resources; stop/logout/off guards; one fish reward outcome |
| G6 | Soul Crystal | персональная отмена только подтверждённого level-gap запрета, все остальные NPC/crystal/quest restrictions intact; или доказано отсутствие запрета `NOT_APPLICABLE` |
| G7 | Henna | OFF/IGNORE/FLIP дают правильные реальные stats/кап, ordinary/Phantom untouched, repeated add/remove/relog safe |
| G8 | Augmentation | positive numeric stats scale only for personal, exact remove, no skill/negative/chance changes; или безопасный OFF/NOT_RUN с технической причиной |
| G9 | build/delivery | clean source-committed JAR, bounded native tests, свой standalone candidate с `START/STATUS/STOP`, SHAs, штатно STOP own JVM, push only feature branch |

**Основной продуктовый результат:** `G0/G1/G2/G4/G9=PASS` — это уже самостоятельный полезный `ADVENTURE067_CORE=PASS`; `G3/G5–G8` имеют собственные статусы и честно отражают выполненный объем. `FULL067=PASS` только при `G0–G9 PASS` (G6 допускает документированный NOT_APPLICABLE, G8 нужен PASS для FULL). Нет обязательства искусственно добиться FULL за 12 часов; лучше выдать несколько законченных возможностей и двигаться дальше, чем бесконечная специализация тестового Rift/рыбки.

**Финальная публикация:**
`docs/phantoms/reports/PHANTOM-PERSONAL-ADVENTURE-067.md`, task `ACCEPTANCE067.tsv`, `HANDOFF067.md`, при необходимости `PROGRESS067.md`; native/config snippets и exact SHAs в отчёте. Один полноценный independent reviewer (если доступен) или честный self-review при отсутствии. Список changed source files + почему изменён каждый. При остановке по budget сохранить проверенный code, выключить незакрытые switches и честный PARTIAL/NOT_RUN, не создавать бесконечные 067-R2/R3.

Нельзя подменять `CLIENT_QA=DEFERRED_BY_OWNER` надписью «владелец проверил». Нельзя выдавать полный M1–M7 closure или финальный Phantom World на основании 067. Продолжить продуктовый roadmap после этой кампании: массовая `.offline`-торговля, реальная экономика/экипировка, AI-рейды/социальность, масштаб, Semantic Studio.
