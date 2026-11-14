# Статистика012: historic replay отдельно от новой кампании

Новая independent native EVAL012: n=0, GS0, CUT0, terminal0, paid0; win/death/timeout/kill-time/resource/economy estimates UNKNOWN. Pre-CUT eligibility refusal не является игровым поражением и не включается в denominator. Headless admissions не являются native боями.

| Derived011 V2 | Solo | Party |
|---|---:|---:|
|terminal-comparable pairs|2|2|
|both-goal-success pairs|0|2|
|with-death pairs|2|0|
|verified timeout pairs|0|0|
|incomparable pairs|0|0|
|learned-only wins|1|0|
|stock-only wins|0|0|
|both win|0|2|
|neither win|1|0|
|STOCK wins / deaths|0/2|2/0|
|LEARNED wins / deaths|1/1|2/0|
|paid legal divergences LEARNED|6|0|
|discordant pairs|1|0|
|exact two-sided sign p|1.0|UNKNOWN: discordant0|

Solo success Wilson95 при n2: STOCK0/2 [0,0.65762], LEARNED1/2 [0.09453,0.90547]. Party обе стороны2/2 [0.34238,1]. Интервалы описательные, относятся только к этим matched экспериментам. Повторное использование frozen sources и маленькое n не доказывают межклассовую генерализацию. GAME_ADVANTAGE=INCONCLUSIVE; Party paid divergence0 отдельно ограничивает интерпретацию.

Парный success delta Solo [+1,0], Party [0,0]. У death/timeout time-to-kill null; kill-time delta существует только в Party kill/kill. Длительность терминального окна не является мерой «лучше умирал дольше». HP/MP — только реальные selected paid receipts/terminal facts; complete death count, whole-party расход, loot/adena/economy UNKNOWN и не заменены нулём. Полные numeric statistics и source hashes — evidence/LEGACY011-REPLAY-V2.json. Исторический G4 FAIL011 не переоценивается.

Новые pre-CUT strata: Solo lower/higher физически различаются уровнем/HP NPC; Party higher не admitted. Последний bounded проход:12candidate probes,3paired admissions,9refusals (5DPS,4MP). Никакие pre-CUT strata не назначались по боевым результатам. Нет полноценного нового native denominator или достоверного вывода о качестве DRAFT14.

## Фактические парные измерения derived011

Δ=LEARNED−STOCK; terminal duration не является временем победы. MP учитывает только exact observed paid receipts, не весь состав.

|Группа/порядок|Kill-time Δms|Terminal-duration Δms|Observed paid MP Δ|
|---|---:|---:|---:|
|Solo AB|UNKNOWN|-14774.6166|1.0|
|Party AB|1488.2413|1488.2413|-208.0|
|Solo BA|UNKNOWN|-1880.5426|157.8|
|Party BA|-3808.9589|-3808.9589|158.0|
