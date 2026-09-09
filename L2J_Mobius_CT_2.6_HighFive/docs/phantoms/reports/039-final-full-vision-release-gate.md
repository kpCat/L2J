# Goal039 — final full-vision release gate

Status: **BLOCKED**

Дата: 2026-09-09

Ветка: `feature/phantom-world`

Исходный required parent: `ba692bd0a5e86fbbfe5f87c9c851f3a629e4d5a1`

Опубликованный blocker baseline: `1ceb045c663a4d6877d2fc8de06822bd78b4769e`

Blocker: `BLOCKED_GOAL039_RESUME2_GOAL036_SOURCE_HASH_ARCHIVE_NORMALIZATION`

## Решение

Первый Goal039 blocker исправлен в Resume-1, но следующий обязательный focused
gate на clean no-geodata candidate выявил независимый Goal015 Background
position defect. Единственный разрешённый confirmation run воспроизвёл те же
два failures. По stop-budget Resume-1 не исправляет вторую family, не запускает
оставшиеся focused и expensive этапы и сохраняет Goal039 в состоянии `BLOCKED`.
Новый Goal не создан.

## Хронология и публикационный gate

Исходный Goal039 остановился на Goal033A1 `1/4`: suite владел устаревшими total
topology counts `110/110`, а creation/ingress contract предполагал наличие
geodata. Blocker commit `1ceb045c...` имел required parent `ba692bd...`.

Перед Resume-1 выполнены bounded `fetch` и проверка remote ref. Origin был на
`ba692bd...`, после чего ровно один разрешённый non-force push опубликовал
существующий commit `1ceb045c...`. Resume-1 edits начались только после
`origin/feature/phantom-world == 1ceb045c...`.

## Clean candidates и сохранение пользовательского дерева

No-geodata candidate экспортирован read-only `git archive` из exact
`1ceb045c...` в `.phantom-local/r1/L2J_Mobius_CT_2.6_HighFive`. В candidate
перенесены только Resume-1 code/data overlay, guarded `Database.test.ini`, schema
manifest и 121 checkout-normalized clean SQL input. Schema aggregate:
`394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.

Второй candidate создан из того же archive и того же code/data overlay в
`.phantom-local/r1g/L2J_Mobius_CT_2.6_HighFive`. Дополнительно в него скопированы
только 203 внешних `.l2j` geodata files общим размером `1063452308` bytes.
Geodata не входит в commit.

Из обоих candidates и task-owned artifacts исключены существовавшие до Resume-1
пользовательские изменения:

- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`;
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomClanDirectiveIntegrationGoal030C2ASuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomMultipartyEconomySuite.java`;
- все pre-existing untracked task packages и launcher files.

## Resume-1 root cause и correction

Baseline diagnostics подтвердили одну заявленную Goal033A1 family:

- whole-corpus owner фактически содержит `112 nodes / 113 anchors / 83 edges`;
- новые Giran siege outer/inner nodes присутствуют и не относятся к Goal033A1;
- без geodata Human Mystic сохраняет raw Z `-3570` вместо historical canonical
  `-3568`;
- без geodata Dwarf сохраняет raw Z `-400` вместо historical canonical `-408`.

Goal033A1 больше не утверждает global topology cardinality. Suite по-прежнему
проверяет собственный полный slice: 11 starting classes, 38 manifest rows,
unique ingress IDs/positions, exact sources, farms, route IDs, route hashes,
contiguity, cost/travel rules, семь normal population saga groups и fail-closed
negative fixtures. Runtime point теперь явно отделён от source raw point и
historical geodata-normalized canonical point.

Из production data изменены только 34 ненулевых `population-ingress` tolerance
в `high-five-core.xml`; четыре zero-delta Human Fighter anchors остались `0`.
Каждый из 38 tolerance равен точному manifest raw↔canonical расстоянию. Текущие
X/Y одинаковы, поэтому распределение `abs(rawZ-canonicalZ)` равно:

- `0 × 4`;
- `2 × 4`;
- `8 × 12`;
- `18 × 6`;
- `21 × 2`;
- `24 × 4`;
- `64 × 6`.

Generic `exactAnchor`, `PlayerCreationInitializer`, `PhantomPopulationStore`,
Player coordinates и `high-five-siege.xml` не менялись. No-geodata navigation
принимается suite только как официальный `DIRECT_UNVERIFIED_NO_GEODATA`, когда
geodata действительно отсутствует у endpoint; IDs, endpoint, distance и весь
route evidence остаются строгими. При наличии geodata требуется и получен
`DIRECT_VALIDATED`.

## Safe defaults и static phase

Shipped defaults подтверждены:

- system `OFF`;
- population / ACTIVE `0/0`;
- diagnostics `OFF`;
- mature conversation `OFF`;
- destructive DB preparation отсутствует в Goal039 aggregate routes.

Исходная fresh clean-candidate static phase: **22/22 PASS**:

- Goal039 structure/static suite: `3/3`;
- Goal031 local-play preflight: `8/8`;
- Goal038 catalog: `5/5`;
- Goal037 static inventory: `2/2`;
- Goal030 release baseline: `3/3`;
- DB guard negative control: `1/1`.

## Focused результаты

Clean no-geodata candidate:

- Goal033A1 topology ingress: **4/4 PASS**;
- unique production exactAnchor: семь из семи групп;
- 80 из 80 factual route segments проверены, все 80 в официальном DEGRADED
  mode из-за отсутствия geodata;
- topology production corpus: **7/7 PASS**, `112/113/83`;
- Background production audit: **1/1 PASS**;
- Background position canonicalization: **0/2 FAIL**;
- единственный confirmation run Background position: **0/2 FAIL** с теми же
  diagnostics.

Geodata-present candidate:

- Goal033A1 topology ingress: **4/4 PASS**;
- representative Human Mystic/Dwarf: `hasGeo=true`, runtime Z равен canonical Z;
- 80 из 80 route segments `DIRECT_VALIDATED`, DEGRADED segments `0`;
- topology production corpus: **7/7 PASS**, `112/113/83`;
- Background position canonicalization: **2/2 PASS**.

## Новый независимый blocker

Clean no-geodata Background position suite дважды воспроизвела:

1. production farming anchor factual Z `-3061` при identity GeoEngine fallback
   остаётся `-3061`, а historical Goal015 assertion требует geodata-normalized
   Z `-3056`;
2. malformed arrival fixture с tolerance `0` при identity fallback считается
   stable canonical position, тогда как suite ожидает rejection.

Это не population-ingress data и не Goal033A1 ownership. Geodata-present
Background position `2/2` подтверждает зависимость именно от отдельного
Goal015 canonical committed-anchor contract. Исправлять эту вторую family в
Resume-1 запрещено.

## Остановленные этапы

После confirmation blocker не запускались:

- Navigation core и Topology core;
- Goal033A и Goal033 focused/production-composed ecology;
- Goal032 ownership/reseed;
- Goal031 local-play readiness;
- Goal030 restart/rollback controls;
- fresh DB negative guard;
- Goal039 safe/static повтор Resume-1;
- Goal035–038 final-domain aggregate;
- Goal029 scale/environment/endurance;
- fresh full `ant verify` — `NOT_RUN_BLOCKED`, timing отсутствует;
- standalone final JAR — `NOT_RUN_BLOCKED`, SHA-256 и bytes отсутствуют;
- fresh Goal034 real stack — `NOT_RUN_BLOCKED`, run ID и
  gen1/restart/gen2/continuity/cleanup evidence отсутствуют;
- final documentation/freeze acceptance verifier — `NOT_RUN_BLOCKED`.

Completion/freeze marker не выставлен; freeze document не создан.

## DB safety и evidence

Все DB-backed запуски использовали только `127.0.0.1:3308`, базу
`l2jmobiush5_phantom_test` и пользователя `l2j_phantom_test`.

Production DB used: **NO**.

`prepare-phantom-test-db`: **NOT RUN**.

Evidence root: `.phantom-local/r1/evidence`.

Ключевые artifacts:

- `no-geodata/goal033a1-topology-ingress.{txt,xml}`;
- `geodata-present/goal033a1-topology-ingress.{txt,xml}`;
- `geodata-present/topology-corpus.{txt,xml}`;
- `geodata-present/background-position-canonicalization.{txt,xml}`;
- `focused-no-geodata/topology-corpus.{txt,xml}`;
- `focused-no-geodata/background-production-audit.{txt,xml}`;
- `blocker-background-position-run1/background-position-canonicalization.{txt,xml}`;
- `blocker-background-position-confirmation/background-position-canonicalization.{txt,xml}`.

## Matrix и handoff

Historical Goal030 matrix остаётся byte-identical: 20/20 `PASS`, SHA-256
`fd891490e7bed44dba7d33f1b72d5c1de46ff67003190b31d22b7dd96206e64e`.
Goal039 declared-scope matrix сохраняет 28 строк: historical 20 `PASS`,
safe-defaults 1 `PASS`, living/Background domain 1 `BLOCKED`, остальные 6
`NOT_RUN_BLOCKED`.

Lightweight blocked-overlay Goal039 structure validator: **3/3 PASS**,
`BUILD SUCCESSFUL`, 18 seconds. Exact source↔clean-candidate overlay fingerprint
comparison прошёл для всех четырёх task-owned paths.

Финальные quality checks: mojibake markers отсутствуют; escaped Cyrillic
отсутствует; strict UTF-8/control-character checks прошли; changed topology XML
загружается strict parser; `git diff --check` прошёл.

Goal034–038 historical reports и SUCCESS решения не переписывались. Goal040 не
создан.

Declared limitations Goal035–038 остаются прежними: bounded Giran, bounded
Q102/Q152 + Q401 Fighter→Warrior, Kamaloka 57, Pailaka Q128/template 43,
отсутствие universal quest solver/open-domain LLM и возможный `DEGRADED` без
geodata. Goal039 не расширяет эти claims.

## Goal039 Resume 2 — dual-mode Background correction

Дата: 2026-09-10

Required parent и origin: `eefa4749f804f4b04dac079356a18462b16fdf58`.
Ветка: `feature/phantom-world`.

Исходный clean no-geodata candidate воспроизвёл обе известные
Goal015 failures: production anchor получил `-3061` вместо жёстко
ожидаемого `-3056`, а zero-tolerance fixture законно canonicalized при
identity fallback. Исходный geodata-present candidate прошёл ту же suite
`2/2`. Это подтвердило классификацию
`GOAL015_POSITION_TEST_GEODATA_PRESENT_ASSUMPTION`, а не production defect.

Изменён только
`test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`:

- production positive получает canonical Z из текущего `GeoEngine` и
  требует `liveDelta <= tolerance`;
- identity fallback записывается как `IDENTITY_DEGRADED`, а
  zero-tolerance production negative — как
  `NOT_APPLICABLE_IDENTITY_FALLBACK`;
- при реальном `liveDelta > 0` zero-tolerance production transition
  по-прежнему обязан завершиться `ANCHOR_MISMATCH`;
- deterministic private `IntUnaryOperator` seam по-прежнему доказывает
  exact tolerance PASS, tolerance+1 reject, unstable/non-fixed/instance reject;
- normal full production transition обязателен в обоих режимах.

Production Java/data/config/build changes: **0**. Generic Background helper,
GeoEngine, raw farm Z `-3061`, tolerance `5`, Resume-1 ingress tolerances и
topology data не менялись.

## Resume 2 clean candidates

Оба candidate созданы read-only `git archive` из exact parent
`eefa4749...`; в них перенесены только changed test overlay,
guarded `Database.test.ini`, schema manifest и 121 checkout-normalized clean
SQL input. No-geodata candidate не содержал внешних `.l2j`.
Geodata-present candidate дополнительно содержал только 203 `.l2j`
общим размером `1063452308` bytes. Geodata в commit не входит.

Pre-existing user-owned tracked/untracked paths в candidate и task-owned
commit не включались. Baseline содержал три tracked user
modifications и 393 untracked paths; sorted porcelain fingerprint:
`87ccaed666dadeb322a58f3f433175dbef4cce9ef1f80f1937f2bc73db7232c5`.

## Resume 2 dual-mode evidence

No-geodata candidate:

- Background position canonicalization: **2/2 PASS**, raw/canonical Z
  `-3061/-3061`, `liveDelta=0`, `IDENTITY_DEGRADED`,
  `NOT_APPLICABLE_IDENTITY_FALLBACK`;
- Goal033A1 topology ingress: **4/4 PASS**, 38 ingress rows, семь saga
  groups, topology `112/113/83`, 80/80 route segments в documented degraded
  no-geodata mode;
- Background production audit: **1/1 PASS**;
- Background model/lifecycle/server integration: **7/7**, **4/4**, **5/5 PASS**;
- topology production corpus/core: **7/7**, **38/38 PASS**;
- navigation core: **50/50 PASS**;
- Goal033A historical Background: **4/4 PASS**;
- Goal033 ecology focused: **9/9 PASS**.

Geodata-present candidate:

- Background position canonicalization: **2/2 PASS**, raw/canonical Z
  `-3061/-3056`, `liveDelta=5`, `GEODATA_NORMALIZED`, malformed
  zero-tolerance transition `ANCHOR_MISMATCH`;
- Goal033A1 topology ingress: **4/4 PASS**, topology `112/113/83`, 80/80
  route segments `DIRECT_VALIDATED`, degraded segments `0`;
- topology production corpus, Background production audit и navigation core:
  **7/7**, **1/1**, **50/50 PASS**.

## Resume 2 new independent blocker

Следующий affected route,
`phantom-population-ecology-production-goal033-test`, завершился
**0/2 FAIL**. Primary failure:

`Supported content source hash is stale: data/phantoms/acquisition/high-five-quest-collection-v1.xml`

Второй failure (`Expected <10> but was <0>`) каскадно следует из
failed cold reseed before setup. Ровно один разрешённый focused confirmation,
`phantom-quest-instance-goal036-test`, воспроизвёл primary failure
в Goal036 `before-all`: **0/2 FAIL**; cleanup failure — каскадный.

Static byte evidence локализует причину:

- catalog SHA-256: `e9b5e5d0038414d892a64971425601807910526aeb073d19d59039072dc4247b`;
- checkout source: тот же SHA-256, 1731 bytes, LF;
- exact-parent `git archive` source:
  `b4d83f03e7dd5020b87058d7e23cb210b375a4b08c320719cb66595a752bf2e9`,
  1755 bytes, 24 CRLF.

Классификация:
`GOAL036_SUPPORTED_CONTENT_SOURCE_HASH_ARCHIVE_NORMALIZATION_MISMATCH`.
Эта family не зависит от Background position correction. По Resume-2
stop-budget она не исправлялась; Goal039 остаётся **BLOCKED**.

После confirmation не запускались Goal032/031/030 affected
continuation, DB negative guard, Goal039 safe/static repeat, Goal035–038 aggregate,
Goal029 scale/endurance, rollback, fresh full `verify`, standalone final JAR,
fresh Goal034 real stack и freeze/docs verifier. Final JAR SHA-256/bytes и
Goal034 run ID отсутствуют как `NOT_RUN_BLOCKED`.

Все DB-backed запуски использовали только
`127.0.0.1:3308/l2jmobiush5_phantom_test`, user `l2j_phantom_test`.
Production DB used: **NO**. `prepare-phantom-test-db`: **NOT RUN**.

Resume-2 evidence сохранён под
`.phantom-local/goal039-resume2/evidence` до публикации отчёта.
Goal039 matrix сохраняет 28 rows: 20 historical `PASS`, safe defaults
`PASS`, living domain `BLOCKED`, остальные шесть
`NOT_RUN_BLOCKED`. Completion/freeze marker не выставлен;
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE` не заявляется. Goal040 не создан.

Lightweight blocked-overlay Goal039 structure validator: **3/3 PASS**,
`BUILD SUCCESSFUL`, 18 seconds. Historical Goal030 matrix осталась
20/20 `PASS`, SHA-256
`fd891490e7bed44dba7d33f1b72d5c1de46ff67003190b31d22b7dd96206e64e`.

Resume-2 task-owned changed files:

- `test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`;
- `test/resources/phantoms/release/goal039-full-vision-coverage.tsv`;
- `docs/phantoms/reports/039-final-full-vision-release-gate.md`.

Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
Escaped Cyrillic в изменённых файлах проверена: совпадений нет.
Strict UTF-8/control-character checks прошли. `git diff --check`
прошёл. Exact source↔clean-candidate SHA-256 fingerprints совпали
для всех трёх task-owned paths.
