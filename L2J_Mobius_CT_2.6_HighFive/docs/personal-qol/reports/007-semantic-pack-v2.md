# L2-QOL-007 evidence report

Дата: 2026-09-14

Branch: `feature/phantom-world`

Required parent: `50f857585f652cbe9dd7483d35f11410943e1f8b`

Required subject: `qol(007): add semantic pack v2`

Результат: **SUCCESS**

Final commit и remote SHA должны совпасть после единственного normal non-force push. Точный SHA фиксируется в финальном Codex handoff: этот report входит в сам commit, поэтому commit не может байт-в-байт содержать собственный SHA.

## Scope и read-first census

До production edits полностью прочитаны task package L2-QOL-007 (`TASK.md`, `ACCEPTANCE.md`, `CONTEXT.md`, `FEATURE_CONTRACT.md`, `SOURCE_AUDIT.md`, `TEST_PLAN.md`, manifest и launcher), module `Agents.md`, корневой `README.md`, module `readme.txt`, master plan/workflow/task-package docs, Personal QoL status/roadmap/operator guide и отчёт QOL-006, Goal038/Goal039 evidence, целевые Ant/test owners и следующие authority families:

- `FROZEN_FUNCTIONAL_SEMANTICS`: `PhantomSemanticGrounding`, `PhantomSemanticModel`, `PhantomSemanticNormalizer`, `PhantomSemanticPack`, `PhantomSemanticUnderstandingService`, functional semantic-v1 XML/corpus и functional-first caller ordering. Изменений нет.
- `EXISTING_HUMANIZED_V1`: весь `conversation/humanized` Java package, humanized semantic/conversation/persona v1, corpus v1, custom aliases/slang/topics/phrases/profanity/mature files, config gates и Goal038 suites. Выбран additive v2 load поверх v1 с custom overlays последними.
- `READ_ONLY_DEPENDENCY`: `PhantomProfile(profileId, characterObjectId)`, `PhantomMaterializationService`/`ActionLease`, live `Player`, `PlayerAppearance` и canonical `PlayerClass`. Persistent owners не менялись.
- `CHANGE_REQUIRED`: два отдельных v2 data artifacts, typed strict loader/model extension, immutable runtime identity snapshot, минимальная передача snapshot в humanized planner, focused suite/build wiring и task docs.
- `OUT_OF_SCOPE`: combat, party, economy, progression, lifecycle, Summoner, Noblesse, production DB/schema/provisioning, client patch, autonomous LLM dialogue и любые новые gameplay mutation owners.

Локальные паттерны: strict UTF-8/XXE-safe bounded XML load, content hashes, stable ID plus explicit custom `override="true"`, deterministic selector, recent-response hashes, immutable request/context records, existing action lease и backward-compatible record constructors. Отдельные code-map/pattern-файлы не найдены.

Bounded exception к обычному лимиту 8–10 файлов составляет 14 task-owned файлов: одна additive artifact family требует два data files, минимальные loader/runtime seams, один focused suite/build registration и четыре обязательных docs/evidence files. Независимых подсистем, broad refactor и инфраструктурной замены нет.

## Frozen functional authority

До edits и после завершения проверены одинаковые raw SHA-256:

- `dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml`: `16C749B9E151E7D5FE7D702989A71DFC2AB3EEDDE9FA103C40B7D01A36E66A18`, 15 198 bytes.
- `dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv`: `2B7676BCCFD4395C267BC298E2F2C8DAE265E23CEE76D76853504BF7172F935E`, 23 819 bytes.

Оба файла исключены из changed-file allowlist. Functional `_semantic.understand(...)` по-прежнему выполняется раньше humanized planner; intent/slot/action model, golden corpus и gameplay proposal owners не изменены. Humanized result остаётся text-only `DeliveryPolicy.SEND` с `action=null`.

## v2 artifact/versioning и strict bounds

- Semantic artifact: `high-five-ru-humanized-semantic-v2.xml`, root ID `high-five-ru-humanized-semantic-v2`, version `2`, 22 007 bytes, SHA-256 `5AC1016BA5FFA45D5F8E1C68967FE2440C5CD5630B801B84D6AB056CD04DBBE4`.
- Conversation artifact: `high-five-ru-humanized-conversation-v2.xml`, root ID `high-five-ru-humanized-conversation-v2`, version `2`, 19 228 bytes, SHA-256 `062795A14C6012579D8407B37D6DD70D78A569E1D36FC7D947CA754E576924BC`.
- Runtime family: `high-five-ru-humanized-v2`; focused core hash `e332554391be9f8793a74b3832fc404fd67fef4d3a68c7bbf3580cb549548b49`, custom hash `bdd405108f1e501b6bef87a60b6fe1488df70d4c6d0398a82afd691e94e646cc`, combined hash `cc29cdbb9487e7d0f97de5172aba8bb80a7c73e4f6e2be019eda078603e0bfbf`.

`load(...)` сохраняет явную v1 compatibility, production использует `loadV2(...)`: humanized-v1 semantic/conversation/persona/corpus → два v2 artifacts → прежние custom files. Content hash включает имена и raw hashes всех входов в фиксированном порядке. Existing custom add/explicit override semantics и последний precedence сохранены; shipped custom content не перезаписывался.

Hard bounds: core 262 144 bytes на файл, custom 65 536, classes 128, exact aliases 384, roles 16, role aliases 64, gender aliases 16, identity patterns 32, additional social patterns 128, additional templates 256 и прежние общие limits. Wrong ID/version, unknown attributes/elements, malformed/oversize/non-UTF-8 XML, DOCTYPE/XXE, duplicate normalized alias/ID/response, unknown or mismatched canonical class и invalid gender закрываются исключением до публикации catalog.

## Canonical identity, class coverage и ambiguity policy

`L2jPhantomConversationContextPort` под существующим `PhantomMaterializationService.tryAcquireAction(profileId)` копирует immutable snapshot из live truth:

- profile identity — входной `observerProfileId`, связанный materialization registry с текущим character object;
- character/object identity — `Player.getObjectId()` и `Player.getName()`;
- display name — `Player.getAppearance().getVisibleName()`;
- gender — `Player.getAppearance().isFemale()` → typed `MALE/FEMALE`;
- current class — `Player.getPlayerClass().getId()/name()`.

Snapshot ничего не записывает в `Player`, profile или DB. Loader сверяет data с каждым `PlayerClass.values()` и требует exact coverage: 103 canonical IDs/classes. V2 содержит 219 normalized class/role aliases, 2 genders с 8 aliases, 12 explicit role families и 14 identity patterns.

Unambiguous class alias хранит один exact canonical class ID. Неоднозначные `танк`, `маг`, `дд`, `лучник`, `хилер`, `саппорт`, `суммонер`, `камаэль`, `биш`, `варлок` и `котовод` являются role/family aliases с явным bounded set; никакой representative/exact class не выбирается. `cat_summoner` строго равен class IDs `{14,96}` (`WARLOCK`, `ARCANA_LORD`). Unknown ID, canonical-name mismatch или unavailable identity fail-closed не создаёт identity response. Смена active class в live fixture меняет ответ без semantic/profile DB rewrite.

## Social speech и сохранённые gates

Humanized-v1 содержит 60 patterns, 67 templates и 22 common social acts. V2 добавляет 50 patterns и 96 clean templates: по 3 дополнительных ответа для каждого из 22 existing acts (66) и по 3 для 10 identity acts (30). Итог focused load: 110 patterns, 163 templates; каждый existing common act имеет не меньше 6 clean variants. Duplicate normalized response text запрещён.

Детерминированный selector и recent-response rotation не менялись. Identity understanding вызывается только внутри существующего humanized planner после functional-command stage и применяет прежние alias/blocklist/input bounds. Existing relationship gates для teasing/sarcasm, profanity context/mode, mature opt-in plus private channel plus trusted relationship, feature/custom gates и personal conversation store остаются прежними. Никакого LLM/provider и gameplay action owner не добавлено.

## Verification evidence

Qualified candidate: `C:\Users\ZBook\L2J_Mobius\.qol007-candidate`; normal local clone на exact parent с overlay только QOL-007. Для Windows raw-byte guards ровно шесть неизменённых SQL inputs и два frozen functional semantic-v1 inputs byte-materialized из operator checkout; Git content diff не расширен. Candidate использует process-local `safe.directory` через `GIT_CONFIG_COUNT/GIT_CONFIG_KEY_0/GIT_CONFIG_VALUE_0`; global Git config не менялся.

Использовалась только заранее подготовленная allowlisted test DB `127.0.0.1:3308/l2jmobiush5_phantom_test` с dedicated test user. Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не запускался.

- `ant -q qol-semantic-v2-test`: **PASS**, 5/5, seed `70000701`, 19 секунд; 12 strict negative cases, 103 classes, 219 aliases, 6 greeting variants.
- `ant qol-007-affected-test`: **PASS**, `BUILD SUCCESSFUL`, 1 минута 57 секунд; Goal038 catalog/behavior, functional semantic-v1, social и conversation checkpoint-1. Первый sandboxed diagnostic остановился только из-за ACL чтения `.phantom-local/Database.test.ini`; разрешённый rerun использовал allowlisted test DB.
- `ant -q qol-007-freeze-test`: **PASS**, `BUILD SUCCESSFUL`, 14 минут 39 секунд; QOL-005/QOL-006 affected/freeze, Goal037–039 и historical/static guards. Ожидаемый `Java Result: 2` принадлежит штатному negative control.
- fresh `ant verify`: **PASS**, единственный final запуск, `BUILD SUCCESSFUL`, 31 минута 25 секунд; 191 text report. Единственные `FAIL`-строки — ожидаемые `negative-control.intentional-failure` и `lifecycle-failure-control.before-all`, проверяющие сам runner. Functional `semantic-activation` 3/3, Goal036 8/8, Goal037 native 8/8, Goal039 documentation 2/2 и полный `qol-007-verify` завершились успешно.
- standalone `ant -q jar`: **PASS**, `BUILD SUCCESSFUL`, 17 секунд. `jar tf` прошёл для обоих artifacts. `GameServer.jar`: 9 059 171 bytes, 4 339 entries, SHA-256 `64965A0EEC911E4367FDC5BA9BCC28021B8A365BF585612ABC6772A10F8A3551`, содержит `PhantomHumanizedCatalog.class` и `PhantomHumanizedCatalog$RuntimeIdentity.class`. `LoginServer.jar`: 313 193 bytes, 200 entries, SHA-256 `0ADD5C416B342FD66199187A5C122FF18A64ABD16E5EA2D82C87F22C803CC369`.

Diagnostic-only freeze attempts не ослабляли assertions. Первый запуск в operator checkout дошёл до Goal039 и выявил CRLF-вариант frozen Goal030 TSV. Первый clean-candidate запуск прошёл runtime suites и остановился на ownership guard старого Goal014 verifier, затем на отсутствовавшем candidate JAR. Process-local safe-directory и предварительная штатная JAR build устранили только candidate environment; точечный `phantom-static-verify-014` и следующий полный freeze прошли.

## Final artifact scope

Exact changed-file allowlist:

1. `build.xml`
2. `dist/game/data/phantoms/semantic/humanized/high-five-ru-humanized-semantic-v2.xml`
3. `dist/game/data/phantoms/conversation/humanized/high-five-ru-humanized-conversation-v2.xml`
4. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
5. `java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationContextPort.java`
6. `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java`
7. `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java`
8. `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedConversationService.java`
9. `test/java/org/l2jmobius/tests/phantoms/PhantomHumanizedSemanticV2Qol007Suite.java`
10. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
11. `docs/personal-qol/CURRENT_STATUS.md`
12. `docs/personal-qol/ROADMAP.md`
13. `docs/personal-qol/OPERATOR_GUIDE_RU.md`
14. `docs/personal-qol/reports/007-semantic-pack-v2.md`

Task packages, `.phantom-local`, candidate/materialization/build/JAR outputs и pre-existing tracked/untracked user work не входят в staging.

## Git scope и known limitations

Разрешены только read-only branch/parent/status/diff inspection, local candidate clone, exact path-scoped staging, one commit, normal non-force push и remote SHA verification. Reset/clean/restore/rebase/force/history rewrite не выполнялись. Точные completion-команды и SHA фиксируются в финальном handoff.

Identity v2 отвечает только за materialized live Phantom и намеренно использует конечный curated phrase/alias catalog: это не open-domain NLU и не morphology engine. Неаудированный alias fail-closed остаётся обычным unsupported humanized input. Manual H5 client UI/dialogue session: **NOT_TESTED_CLIENT_UI**. Client patch отсутствует. Production DB: **NOT_USED**. Summoner/Noblesse work: **NOT_INCLUDED**.

Mojibake-маркеры в 14 изменённых файлах проверены отдельно: **совпадений нет**.

Escaped Cyrillic в 14 изменённых файлах проверен отдельно по всем шести обязательным regex-паттернам: **совпадений нет**.
