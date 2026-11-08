# Known failure modes — проверять до дорогого GameServer

| ID | Source-level risk | Нужно сделать до native |
|---|---|---|
| R01 | Исправят только `rendezvousSources()`; downstream `rendezvousGraphPlan` и `rendezvousIngressPlan` сохранят Solo viableTarget | Grep/call-graph PARTY path + RED/GREEN B02,C06 |
| R02 | Поменяют глобальные LEVEL_RADIUS/LOWER_LEVEL_TIERS и случайно сломают Solo и rates | Separate pure Party band; A05 Solo regression |
| R03 | В logical discovery останется лидер==damage, а в native первый слот DD | Unified role/admin leader in discover/requirements; B08 |
| R04 | Уберут `team.size()<3` только в одном method; `selectTeam` и native form продолжат требовать 3 либо mandatory heal | Exhaustive 2..9 across all selectors B08,D02 |
| R05 | 70 автоматически помечен `damageDealer` или `tank.readyNow` из класса без native skill | Durable potential != native ready; B07,D01 |
| R06 | GroupViable даст всем полный incoming (ложный FAIL) или всем zero incoming при танке (ложный PASS) | Use observed NPC target and stock Aggro; D04/D05, typed unknown |
| R07 | Healer сможет бесплатно «лечить» прогнозом без MP/skill/LOS | No predicted effect, actual HP/MP before-after; D05,N04,F8 |
| R08 | SPOILER получит дроп, не выполнив native Spoil→kill→Sweep | Use existing acquisition owner, corpse ID, native skill; tests; no FULL SPOIL claim без proof |
| R09 | Group source ранжируется в top64 до пересечения, 65-я доступная общая цель теряется | bounded paging/cursor B04, no 5k scan |
| R10 | `UNKNOWN` route будет принят за Geo success/телепорт | Preserve structural vs native route distinction B05 |
| R11 | Intent/Goal обновят во время in-flight Commerce, native store, healing/cast | Preserve CAS/request/yield/current epochs, neg SQL C05 |
| R12 | Изменён catalog role XML → old group role/hash silently invalid, restart FAIL | Explicit legacy handling and readback test B06 |
| R13 | Уровень игрока изменится (level-up) после выбора objective | recompute group band at commit, activity revision, native target D06 |
| R14 | 70+72 могут оказаться без достаточного damage и не доказать group kill | Evidence-based third lawful DD or different allowed mob; typed WAIT, NEVER force pass |
| R15 | Native preflight helper не использует production policy и снова начинает запрещать корректное STORE | One shared group policy and conservation witness, tests C04 |
| R16 | Конец наблюдателя приведёт к дематериализации в combat или в Party с человеком | Existing retention-policy + no eviction, real drain N05 |
| R17 | Старые GameServer/CD test artifacts/old JAR подменят финальную проверку | Clean ant build, source/JAR parity, origin+hash, 1 build/1 boot gate |
| R18 | Скорость работы: новая роль потребует SELECT для всех 5000 на каждом scheduler tick | bounded cohort 16, cache/reuse and cursor, 5000 test C07 |
| R19 | Запустят THIRD/FULL GameServer без причинного cheap proof | Bounded authorization; STOP on blocker and ask review |
| R20 | В процессе поправят 132 Clan, 133 Raid, 136 Olympiad для удобства теста | Out-of-scope, protect native rights and existing gameplay hooks |

Если два cheap итерационных прохода не дают измеримого прироста, не расширять список патчей; сделать одну компактную причинную сверку method-to-test-to-product, предложить пользователю корректировку. Не раздувать artefact/test harness.
