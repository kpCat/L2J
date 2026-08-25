# Goal 030A — legacy player identity and role ecology hardening

## Статус

- Delivery status: `SUCCESS`.
- Goal 029 overall: `ACCEPT`.
- Goal 030 Checkpoint 1: `ACCEPT`.
- Goal 030A: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 030 overall: `IN_PROGRESS`.
- Goal 030 Checkpoint 2: `NOT_STARTED_AFTER_030A`.
- Required parent: exact `d9a0d01b62f66ec3c9c95edadc24b8167d32b624`.
- Branch: `feature/phantom-world`; upstream: `origin/feature/phantom-world`.
- `occurred_context_compaction`: `no`.
- Goal usage at pre-report snapshot: `264845` tokens, `1782` seconds.
- `apply_patch` invocation count: exact `0`.

## Summary

Machine-looking `prefix + suffix + base36` naming заменён единым data-driven legacy-2007–2011 hybrid policy в authoritative population catalog. Каталог содержит шесть exact-weighted styles, synthetic corpus `101/40/30`, case-insensitive reserved-token policy и structural collision alternatives; universal hash/base36 suffix отсутствует.

В тот же catalog добавлен creation-time `CareerArchetype`. Архетип выводится stateless из уже durable population seed, `creationOrdinal` и catalog authority через детерминированный 100-slot low-discrepancy cycle; затем выбирается compatible canonical level-zero class. `PhantomPopulationStore.createShell` передаёт эти факты, но `PhantomPopulationState`, codec/schema, Manager и runtime Scheduler/Decision/Party/Progression не изменены.

Shipped `PhantomPlayers.ini` получил только русские комментарии UTF-8 без BOM; все 13 keys/values/defaults сохранены exact, система остаётся выключенной, target/ACTIVE target — `0/0`. Добавлен русский guide по именам, ecology и hashed-catalog discipline.

## Nickname policy и 10k proof

Authoritative weights: `CLEAN=35`, `COMPOUND=25`, `TRANSLIT_SLANG=18`, `DECORATED=10`, `DIGITS=8`, `LEET=4`; сумма exact `100`.

Corpus: primary `101`, secondary `40`, translit/slang `30`. Это curated synthetic vocabulary, account dump не использовался; offensive vocabulary не добавлялся. Reserved case-insensitive set включает `admin`, `administrator`, `gm`, `gamemaster`, `npc`, `server`, `l2j`, `phantom`. Финальный candidate проходит ASCII/length и reserved-substring validation.

DB-free simulation seed `30003010`, identities `10000`:

| Метрика | Результат |
|---|---:|
| Unique within attempts | `10000/10000` |
| Exact ordered rerun | PASS |
| `[A-Za-z0-9]{1,16}` | `10000/10000` |
| Length 4..13 | `9916` (`99.16%`) |
| С цифрами | `1295` (`12.95%`) |
| Decorated | `1123` (`11.23%`) |
| Leet | `440` (`4.40%`) |
| Reserved-token matches | `0` |
| Max root/token share | `<=3%`, suite PASS |
| Max deterministic 2–3 suffix share | `<=3%`, suite PASS |

Actual styles:

| Style | Count | Share |
|---|---:|---:|
| CLEAN | 3307 | 33.07% |
| COMPOUND | 2338 | 23.38% |
| TRANSLIT_SLANG | 1937 | 19.37% |
| DECORATED | 1123 | 11.23% |
| DIGITS | 855 | 8.55% |
| LEET | 440 | 4.40% |

Collision-attempt histogram: `attempt0=8716`, `attempt1=1077`, `attempt2=171`, `attempt3=33`, `attempt4=3`, `attempt5..8=0`. Первые alternatives меняют roots/style/structure; поздние attempts `6..8` являются bounded digit/leet fallback. Ни один identity не исчерпал существующий предел.

### Exact40 deterministic samples

1. `COMPOUND:AngelKraken`
2. `TRANSLIT_SLANG:Bratsky`
3. `DECORATED:OoSiriuswaroO`
4. `LEET:R4vencra`
5. `CLEAN:Odinhe`
6. `DIGITS:Mystic03`
7. `COMPOUND:VegaClaw`
8. `COMPOUND:DemonAtlas`
9. `TRANSLIT_SLANG:Tigersp`
10. `COMPOUND:WingOracle`
11. `DECORATED:xXCrazyscXx`
12. `CLEAN:Ghostfang`
13. `CLEAN:Tigermigh`
14. `TRANSLIT_SLANG:Shadowna`
15. `TRANSLIT_SLANG:Nightdedo`
16. `DIGITS:Vega59`
17. `TRANSLIT_SLANG:Atlasme`
18. `CLEAN:Dragondash`
19. `DIGITS:Arsen96`
20. `COMPOUND:RogueRhythm`
21. `CLEAN:Hawksoul`
22. `LEET:J4ckvo1`
23. `TRANSLIT_SLANG:Titanme`
24. `TRANSLIT_SLANG:Flamehil`
25. `TRANSLIT_SLANG:Dedokarr`
26. `DIGITS:Lucky39`
27. `COMPOUND:StepMike`
28. `DECORATED:lIMoondemoIl`
29. `CLEAN:Scorpionhear`
30. `COMPOUND:SharkFury`
31. `CLEAN:Ravenwr`
32. `CLEAN:Ghostqu`
33. `TRANSLIT_SLANG:Kraftro`
34. `TRANSLIT_SLANG:Arsentan`
35. `CLEAN:Thorde`
36. `DIGITS:Flame88`
37. `LEET:N1ckqu3`
38. `CLEAN:Windrh`
39. `COMPOUND:ForgeArcher`
40. `COMPOUND:RayKing`
## Career ecology и canonical feasibility

Exact targets/actual 10k ordinals `1..10000`, seed `30003010`:

| Archetype | Target | Actual | Share |
|---|---:|---:|---:|
| DAMAGE | 55 | 5500 | 55.00% |
| TANK | 8 | 800 | 8.00% |
| HEALER | 8 | 800 | 8.00% |
| ENHANCEMENT | 12 | 1200 | 12.00% |
| CONTROL | 7 | 700 | 7.00% |
| ECONOMY | 10 | 1000 | 10.00% |

Global support (`HEALER+ENHANCEMENT+CONTROL`) — `27.00%`. Каждый contiguous block из 500 creation ordinals содержит все шесть archetypes; worst rolling-500 support — exact `27.00%`, ниже cap `34%`. Ordered archetype/class rerun exact; все выбранные пары catalog-compatible.

Final class-ID distribution:

| Starting class ID | Count | Share |
|---:|---:|---:|
| 0 | 864 | 8.64% |
| 10 | 1284 | 12.84% |
| 18 | 1111 | 11.11% |
| 25 | 875 | 8.75% |
| 31 | 1100 | 11.00% |
| 38 | 1023 | 10.23% |
| 44 | 626 | 6.26% |
| 49 | 1039 | 10.39% |
| 53 | 1000 | 10.00% |
| 123 | 487 | 4.87% |
| 124 | 591 | 5.91% |

Maximum concentration — class `10`, `12.84%`: ниже hard `20%` и preferred `15%`. Все canonical level-zero starting classes `{0,10,18,25,31,38,44,49,53,123,124}` достижимы.

Feasibility audit ограничен `PlayerClass` lineage, `high-five-capabilities-v1.xml` и `high-five-party-roles-v1.xml`:

- DAMAGE: physical, magic и summon descendants из всех non-Dwarven starting lineages;
- TANK: Human/Elven/Dark Fighter lineages к canonical knight descendants;
- HEALER: Human/Elven/Dark Mage lineages к Cardinal/Eva Saint/Shillien Saint;
- ENHANCEMENT: Prophet, Sword Muse, Spectral Dancer, Shillien support, Orc shaman и Judicator paths;
- CONTROL: conservative Human Mage, Orc Mage и Kamael paths; неполностью доказанные Elf/Dark Mage CONTROL пары не включены;
- ECONOMY: exact Dwarven Fighter lineage к Fortune Seeker/Maestro; existing progression authority подтверждает spoil/sweep/craft.

Всего final feasible catalog pairs — `27`. Profession quest/class-transfer implementation не добавлялся; archetype остаётся future intent.

## Production boundary, lifecycle и performance

- Production Java изменён только в `PhantomPopulationCatalog` и двух строках creation selection `PhantomPopulationStore.createShell`.
- `PhantomPopulationState`, `PhantomPopulationStateCodec`, persisted schema/migrations и DB lifecycle не изменены.
- `PhantomPopulationManager`, Scheduler, Decision, Party и Progression runtime behavior не изменены.
- Нет live population scan, cache, background task, per-phantom worker или hot-path work.
- Naming и ecology выполняются только при identity creation/collision candidate creation; external service/runtime I/O отсутствуют.
- `BLOCKED_030A_REQUIRES_DB_LIFECYCLE_SCOPE` и `BLOCKED_030A_HOT_PATH_ECOLOGY_DESIGN` не возникли.

## Russian config и data guide

`dist/game/config/Custom/PhantomPlayers.ini`: переведены только комментарии, encoding strict UTF-8 without BOM. Suite parsed exact 13 settings и подтвердил неизменность keys/values/defaults. `EnablePhantomSystem=False`, `PhantomPopulationTarget=0`, `PhantomPopulationActiveTarget=0`.

`dist/game/data/phantoms/README.ru.md` описывает styles, CareerArchetype ecology, safe weight/corpus edits, SHA-256 authority, запрет casual comment-only churn hashed XML/TSV и exact fast gates. Credentials и local absolute paths отсутствуют.

## New DB-free suite

`PhantomPopulationHumanizationGoal030ASuite`, seed `30003010`, cases:

1. `01-name-policy-corpus-and-safety`;
2. `02-ten-thousand-legacy-name-distribution`;
3. `03-role-ecology-canonical-feasibility`;
4. `04-ten-thousand-role-ecology-distribution`;
5. `05-russian-config-comment-contract`.

Ant target `phantom-population-humanization-goal030a-test` зависит только от `compile-tests`, forked, timeout exact `120000`, без DB config/provisioning и без `-Xmx4G`.

## Final fast gates

После последнего source/test изменения выполнена exact sequence:

1. `phantom-population-humanization-goal030a-test` — PASS `5/5`, Ant total `20 s`;
2. `phantom-population-catalog-test` — PASS `3/3`, Ant total `18 s`;
3. `phantom-population-schedule-test` — PASS `3/3`, Ant total `19 s`;
4. `phantom-progression-catalog-test` — PASS `60/60`, Ant total `18 s`;
5. ровно один финальный `jar` — `BUILD SUCCESSFUL`, Ant total `17 s`; LoginServer/GameServer/DatabaseInstaller jars собраны, GameServer/LoginServer jars скопированы в working `dist/libs`.

На каждой `compile-tests` фазе остались только две historical JDK removal warnings для bounded `System.runFinalization()` в Goal029 CP2/CP3 suites; новых warnings нет.

Не запускались: DB provisioning, любые DB targets/DB-backed population tests, `phantom-population-test`, population performance smoke, materialization/headless, Goal029 soak, Goal030 CP1, CP2, geodata и `verify`.

## Development findings и deviations

- Bare `ant` не найден в PATH и завершился до Ant execution; далее использован существующий `.phantom-local/apache-ant-1.10.17/bin/ant.bat`.
- Focused 030A iterations до final chain выявили и исправили: недостаточное DECORATED/COMPOUND structural space, rare reserved token на стыке roots, частые natural suffixes `er`/duplicate decorator `xx`, а также stale legacy catalog test range `0..31` вместо authoritative `0..8`.
- Один запрос на сужение suffix metric был автоматически отклонён до write как ослабление контракта; тест не ослаблялся. Generator/corpus исправлены так, чтобы полный 2–3 suffix gate прошёл.
- Production DB и test DB не открывались и не изменялись.
- Release matrix TSV не менялся; counts `20` domains и CP2 pending rows сохранены.
## Exact changed files

Bounded exception к обычному ориентиру 8–10 files: task прямо требует одну связанную artifact family из production catalog/store, authoritative data, focused test wiring, shipped operator docs, release statuses и отчёта. Изменены только следующие 12 paths:

1. `build.xml` — Goal030A seed и forked DB-free target.
2. `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationCatalog.java` — strict name corpus/styles/reserved policy, deterministic generator, CareerArchetype ecology и compatible class selection.
3. `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationStore.java` — creation-time archetype/class selection из durable seed + creationOrdinal.
4. `dist/game/data/phantoms/population/high-five-population-v1.xml` — authoritative synthetic name corpus, exact styles и ecology mappings.
5. `dist/game/config/Custom/PhantomPlayers.ini` — comment-only русский перевод; settings exact unchanged.
6. `dist/game/data/phantoms/README.ru.md` — русский operator/data guide.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationHumanizationGoal030ASuite.java` — новый 5-case DB-free suite.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java` — affected catalog name attempt bound `0..8`.
9. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — новый launcher mode.
10. `docs/PHANTOM_BOTS_ROADMAP.md` — CP1 ACCEPT, 030A pending review, CP2 not started, Goal030 in progress; Goal029 ACCEPT сохранён.
11. `docs/phantoms/PHANTOM_RELEASE_GATE.md` — sequence CP1 -> 030A -> CP2 -> CP3; matrix counts unchanged.
12. `docs/phantoms/reports/030a-legacy-player-identity-role-ecology.md` — этот отчёт.

Другие хроники, generated jars в source diff, state/codec/schema и user-owned untracked task packages не изменялись и не staging.

## Static, encoding и scope

- Все writes выполнены small exact-anchor либо bounded new-file chunks через UTF-8-no-BOM temp + same-path `Move-Item`; `apply_patch` не вызывался.
- `git diff --check`: PASS перед report; final check повторяется перед commit.
- Strict UTF-8 decode и UTF-8-without-BOM по exact 12-file changed allowlist: PASS; временные `*.goal030a.tmp` отсутствуют.
- Mojibake-маркеры в изменённых файлах проверены: `0` совпадений.
- Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены: `0` совпадений.
- Forbidden state/codec/Manager/Scheduler/Decision/Party/Progression/release-matrix diff: exact zero.
- Full diff/scope/staged allowlist и отсутствие других chronicles проверяются перед commit.

## Git и delivery

Task/Agents.md разрешают обязательные Git baseline/status/branch/upstream, bounded exact diff/scope inspection, ordinary stage/commit/push. Использованы `git status --short --branch`, `git rev-parse HEAD`, `git branch --show-current`, `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`, bounded `git show`/`git log`, `git diff --check`, `git diff --name-only`, `git diff --stat` и exact-path/full diff inspection.

Amend/rebase/reset/squash/merge/force push не используются. Preferred subject: `feat(phantoms): humanize population identity`.

Commit SHA и push result указываются в финальном сообщении: report-bearing commit не может содержать собственный SHA без запрещённого amend.

## Limitations, risks и next step

- CareerArchetype не сохраняется отдельным полем и определяется для фиксированного catalog authority; содержательная правка hashed population catalog является осознанной authority evolution.
- Archetype не обходит native profession/class-transfer mechanics и не вводит rigid party quota.
- Goal030A не доказывает living-world cross-domain alpha, restart/failure recovery или final release decision.
- Следующий шаг — независимый review Goal030A. Только после ACCEPT пакет Goal030 CP2 формируется заново поверх этого baseline; CP2 сейчас `NOT_STARTED_AFTER_030A`.