# PHANTOM-NATIVE-SPEECH-FACETS-082

24.10.2026. **SUCCESS для исходного scope082: A1–A5, B1, C1 PASS. C2 — публикация и post-push сверка выполняются после фиксации этого отчёта.** Финальный manual gate владельца: **REQUIRED**. Live gameplay/клиент, VS Designer и полный OWNER FINAL RELEASE TARGETS не объявляются пройденными. STOP после082; 083/R2 не запускаются.

## Authority и read-first

Фактический Git root `D:/Codex/repos/L2J-phantom-world`, ветка `feature/phantom-world`, origin `https://github.com/kpCat/L2J`. Перед записью local HEAD, upstream `origin/feature/phantom-world` и live remote равны `206beef6ecdcae3bd6882ded6d6636ac7079be38`; tracked dirt отсутствовал. Первый sandbox `ls-remote` не смог подключиться, разрешённый read-only network вызов подтвердил SHA. Пользовательские untracked `.phantom-local/` и пакеты068V/073/076/077/078/079/080/081/082 сохраняются вне commit.

Прочитаны root, HighFive и Studio AGENTS; все девять файлов распакованного пакета082 (TASK, ACCEPTANCE, DELIVERY, FACETS_NATIVE, GOAL, HANDOFF, MANIFEST_SHA256, SOURCE_MAP, TEST_PLAN); восемь SHA manifest совпали. Прочитаны README/README_RU, PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, релевантные части master plan, TASK_PACKAGE_STANDARD, отчёты079/081 и OWNER_FINAL_RELEASE_TARGETS. Родительские AGENTS выше Git root отсутствуют; отдельных актуальных code-map/pattern-файлов не найдено. SOURCE_MAP082 использован как bounded карта владельцев.

Точечный code pass: два humanized Java owners, Social model/service и шесть trait definitions, оба lineage XML, V3 manifest, два зарегистрированных Java suite, Studio PackReader/Models/V3ProposalContract, operator/probe/handoff081 и их focused tests, build.xml, Core/tests csproj и Directory.Build.props. Аналоги: voice079 pool и fallback; MemoryStore/SocialState BEHAVIOR fixture; строгие V3 attributes и append-only stage; isolated three-source Java proof081. Java25/Ant и .NET10/C#14/WinForms/SQLite10.0.12 сохранены; dependencies, lock-файлы и UI не менялись.

План по утверждённому TASK082: pure projector → V3-only metadata/production plan → десять малых RU строк → Studio compatibility/exact pins → focused native/C# gates и physical proof → report/exact scope guard → один commit/normal push. Bounded exception: **15 файлов**, перечислены ниже. Это единая цепочка native metadata → production selection → совместимый source reader → проверяемый handoff; housekeeping/refactor не добавлялись. Независимые owners выполнялись параллельно без одновременных сборок; один отдельный read-only reviewer pass, затем bounded диагностика compiler environment.

## A1/A5 — derived native profile

`PhantomSpeechFacetProfile` — один immutable exact10 projector рядом с canonical humanized service. Вход — существующий `PersonalitySnapshot`: ownerProfileId, persisted seed и persistence/caution/sociability/loyalty/empathy/assertiveness. Никаких новых Social traits, схем, таблиц, persistence, cache, timer, event, Player или RNG.

Каждый signed trait сначала ограничивается в `long` до −10000..10000 и нормализуется в −50..50. Явные веса tendencies дают максимум30 пунктов; отдельный 64-bit mixer от seed, profileId и facet salt даёт offset −20..20; итог `clamp(50+tendency+offset,0,100)`. Намеренный overflow mixer не участвует в арифметике нормализации. Все десять характеристик могут сосуществовать; они являются derived speech preferences из сохранённой native личности, не новыми persisted Social traits.

CONTENT проверил5000 synthetic numeric IDs:5000 различных векторов,4035 векторов с ≥2 интенсивностями≥60; каждый facet имеет ≥30 разных значений. Проверены exact keys/bounds, повторное создание snapshot, seed/profile variation, направления реальных traits, extreme integer clamping и immutable map. Весь этот case занял87224100ns на одном запуске, включая assertions/коллекции; это не benchmark hot path и не доказательство5000 production profiles.

## A2/A3 — selection и сохранённые gates

Только V3 TEMPLATE получает optional `facet` и `minIntensity`. Ключи: aggression, sarcasm, humor, reserved, swagger, timidity, romance, roughness, cunning, gossip. Threshold — ASCII decimal1–3 digits,0..100, default60; leading zeros допустимы. Threshold без facet, пустой/неизвестный/неверно регистровый facet и атрибуты в v1/v2/custom/semantic/manifest отклоняются. Старые Template constructors и select overloads сохранены.

Legacy select полностью исключает tagged templates. Новый overload принимает defensive immutable copy полного exact10 map; null, incomplete, unknown, null-valued и out-of-range input завершаются `IllegalArgumentException`. Каталог не зависит от projector/Social: pure isolated closure не расширилась.

`plan` вычисляет вектор из фактического `social.personality()` после прежних social/relationship gates. Старый base voice остаётся independent: universal facet либо тот же voice; чужой voice недоступен. Сначала действуют прежние act/band/register/profanity index/mature/private gates. Уже допустимый exact TRUSTED/RIVAL base ответ защищён от UNKNOWN facet. Exact-band facet конкурирует в своём допустимом band. При отсутствии подходящей facet остаётся прежний base-voice/universal выбор. Для legacy coverage facet-only строки не засчитываются как чистый fallback.

Допустимые варианты сортируются детерминированно; per-facet LOW/MEDIUM ограничивает число строк, сохраняя выбор между facets. SHA-mixed selector и положительные веса score+1 дают всем вариантам шанс и предпочитают более сильные; recent response hashes вращают выбор среди допустимых строк. Работа ограничена прежними index buckets4096 и десятью score keys. Это выбор текста существующего ответа; functional-first, actions/receipts, materialization/observer authority и Phantom-social budget остаются у прежних владельцев и не изменены.

Сохранён freeze079: explicit legacy выбор каждой из четырёх voice-групп по-прежнему даёт `v3.persona079...`; actual production plan теперь законно выбирает и facets. Повторный service с теми же источниками/traits/request и пустым personal state даёт ту же Decision; TRUSTED empathetic/RIVAL direct специализированные ответы сохраняются.

Новый BEHAVIOR case использует64 persisted MemoryStore SocialState с каноническим общим seed18001801 и разными малыми native traits, одинаковым base voice calm; обычный public GENERAL chat,12 observation selectors, две исходные фразы. Actual `plan` достиг10/10 styles; одинаковая входная PvP-фраза на первой observation дала5 разных facet IDs у unknown-band calm personalities. Profanity/mature/memory false, новые выдуманные personal facts0, SocialStore writes0. Это native service proof без материализации Player и без сервера.

## A4 — десять реальных RU строк

Новые IDs `v3.facet082.<key>.pvp|farming`, только два существующих XML; topic/act/manifest не добавлялись. Все строки NEUTRAL, NONE, non-mature, universal base voice, без утверждения о совершённых действиях/победах/дропе/Party/прошлых встречах.

| Facet | Act / band | Текст |
|---|---|---|
| aggression | lineage.pvp.risk.reply / UNKNOWN | Не люблю уступать. Но лезть в пвп без подготовки — только злиться зря. Какой риск тебя беспокоит? |
| sarcasm | lineage.pvp.risk.reply / UNKNOWN | Смелость прекрасна, особенно когда рядом ещё и здравый смысл. Какие риски в пвп стоит оценить сначала? |
| swagger | lineage.pvp.risk.reply / UNKNOWN | Уверенности мне хватает, но одного гонора для пвп мало. С чего начнёшь подготовку? |
| timidity | lineage.pvp.risk.reply / UNKNOWN | Я бы с пвп не торопился: тревожно идти наугад. Может, сначала обсудим безопасный вариант? |
| roughness | lineage.pvp.risk.reply / UNKNOWN | Пвп на авось — дурная затея. Проверь силы и не лезь в риск вслепую. |
| humor | lineage.farming.spot.reply / UNKNOWN | Выбирай спот по силам, а не по размеру мечты. Мобы наши амбиции читать не умеют. |
| reserved | lineage.farming.spot.reply / UNKNOWN | Для фарма: цель, запас расходников, посильный спот. С этого и начни. |
| romance | lineage.farming.spot.reply / TRUSTED | С тобой даже планы на фарм звучат теплее. Может, присмотришь спокойное красивое место? |
| cunning | lineage.farming.spot.reply / UNKNOWN | Сначала прикинь дорогу, расходники и время на круг. Выгодный спот лучше выбирать расчётом, а не шумной славой. |
| gossip | lineage.farming.spot.reply / UNKNOWN | О спотах можно болтать долго. Что ищешь: плотных мобов, тихое место или короткую дорогу? Слухи лучше потом проверить самому. |

Все десять достижимы actual plan; romance требует сохранённого TRUSTED band. Это небольшая демонстрация в PvP/farming, не покрытие всех тем/голосов и не масштабный корпус.

## B1 — Studio082 и physical Java proof

PackEntry сохраняет imported `Facet` и effective nullable `MinIntensity`; Candidate/BatchJob schemas не меняются. PackReader проверяет case/location/version и не теряет metadata; V3ProposalContract допускает только две новые optional attributes и требует чистый legacy fallback. Существующие source bytes копируются неизменно. Новые Candidate additions обязаны оставаться untagged; даже вручную rehashed receipt с facet отвергается. EDITORIAL_ONLY strengths остаются DRAFT provenance; автоматического native facet mapping/export нет.

Active pins в operator/handoff и inventory test закреплены на точный audited082 catalog; старые audited063/079 history pins сохранены, текущий isolated guard не принимает старый079 SHA. Catalog imports остаются JDK + PlayerClass; PlayerClass зависит от Race. Projector/service не входят в standalone catalog closure, и это подтверждено компиляцией с empty sourcepath/classpath и `-implicit:none`.

| Source/artifact | SHA256 |
|---|---|
| baseline catalog079 | `493d1010e653f66b2e1f3407914735a4ce5a370f8dd7d0d5349fbccb0fb76ef0` |
| final catalog082 | `01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05` |
| final projector082 | `ac2bcedf8c20dd823ebef04e74fb8f90934214a0c04422bc45f8def1dbdb5683` |
| PlayerClass, unchanged | `9678b970528f17192114669ad96b8538add396ec251f4fbc4476d57c1e64a2cf` |
| Race, unchanged | `7cd9a7ba39b483b93c6fd47b1ef4572dc526fb0a06419bf918fafc823c5cbc0b` |
| operator082 | `87c6041cac9bf1303798d4ddd9aa427cc67191c1207e3fd09d374509fe37b1ae` |
| unchanged Java probe | `0704fe01f98de18f2220d46285428eceba1e32aeed0b9d6890bdc807b9369b40` |
| receipt082 | `4ab59d0550c9e368873eb27db0afa47dc86083881f1c42d84a905c26ce3fc6aa` |
| java-validation082 | `7c07916b558dcf78623a8fe0fcc6f92e1e1c5509d4a255cce5b28398d97b99e5` |
| attestation082 | `636bd66a2a4a87e934c4c9661f99d62a7ca1a7bc747c114e6f18dc56e4a823ce` |

Новый external workspace: `%LOCALAPPDATA%/PhantomSemanticStudio/workspace/native-facets082-1da340dfbae344faa29f0bfb97939f3c/workspace`. Stage `91dbaf74a1ca413785cbb42880556f5a`, oracle `oracle-6f2efb7feacf4291b863c0b78603c493`. Старые081 stage/proof/key не использовались. Три явно synthetic candidates: PATTERN/direct/universal; fixture approvals не являются одобрением владельцем рабочего текста.

Один physical operator trial: Version2, ISOLATED_CATALOG_V1, Java25.0.4.1, copied3 sources/93833bytes, compile/probe0, duplicate negative3 и schema negative3; SourceEqual=true, stage/source SHA/bytes unchanged. Ant=NOT_RUN, AntExitCode=null; OwnerConfig не запускался. Source66 files:5313 patterns/21005 templates, fingerprint `c16813c6db02b80d92ce50f68d644f981f880b64426a12fc5bf88653607bdb42`. Native baseline hash `cb167ab0d8ba0d941248fb020d395d1785cced5b82fa38e5609244e3256ed54d`; staged5314/21007, hash `9db60723c713f7d4de43fc8a22cd42393bc04b00ef6ffbc497632781f5da5d23`.

Genuine proof import/HMAC passed; rehashed public-tool/log forgery, copied PlayerClass drift, authenticated fake-Ant relabel, changed stage/log/operator/attestation rejected, original bytes restored. Explicit SYNTHETIC offline handoff: proposed2, backup2, manifest1, checklist1; consent NO/wrong reviewed proof hash/cancel rejected, no extra release. NOT_INSTALLED, JAVA_CONTENT_ONLY_NOT_RUNTIME_READY.

## C1 — команды, ошибки и bounded corrections

Все команды из Git root. `$scratch` — новый `%TEMP%/phantom082-1da340dfbae344faa29f0bfb97939f3c`; native-bin/test-bin/args/reports находятся там, вне Git/server releases. Source/library inventories получены `rg --files <java|test/java> -g '*.java'`; production classpath использует существующие четыре dependency JAR, исключая GameServer/LoginServer и sources JAR. Только production compilation целиком выполнена один раз; полного повторного build/jar нет. Существующий неизменённый PhantomTestLauncher/helpers используются из `build/phantom-test/bin`, новые два suites — из fresh test-bin, все production classes — из fresh native-bin, старый GameServer.jar в GREEN classpath исключён.

| Команда / gate | Результат |
|---|---|
| RED: `javac -encoding UTF-8 -source 25 -target 25 -cp <existing baseline classes/tests/libs> -d <scratch/red-bin> <current catalog,service,BEHAVIOR suite>`; launcher `humanized-conversation-goal038-behavior 38003801` | compile0, RED exit1:6/7; missing native facet assertion |
| `javac -J-Xmx2g --release 25 -proc:none -encoding UTF-8 -cp <four dependency JAR> -d <scratch/native-bin> @<production-sources.args>` | exit0;2365 current production sources,4669 class files |
| `javac -J-Xmx1g --release 25 -proc:none -encoding UTF-8 -cp <fresh native-bin;four JAR> -d <scratch/test-bin> @<test-sources.args>` | exit1; sandbox silently hides class-directory packages; full test compilation NOT_PASSED, not repeated |
| Focused diagnostic `javac -verbose -Xmaxerrs 1 ... <BEHAVIOR suite>` | exit1, same hidden fresh directory; log retained externally |
| First generated `focused-options.args` | exit2, malformed multiline CP argument; this orchestration error is not a feature test |
| Corrected `javac @<focused-final.args>` inside sandbox | exit1, same directory visibility failure |
| Same exact focused args with normal owned temp access outside sandbox | fresh classes resolved; exit1 exposed incorrect test-only `ChatType.ALL` |
| Addressed fix pass1: use source-confirmed `ChatType.GENERAL`; same focused compile | exit0 |
| Launcher `post002-semantic-v3-content 2002002` with explicit fresh test/native classpath | exit0,7/7; expected malformed XML/DOCTYPE negative stderr |
| First GREEN launcher `humanized-conversation-goal038-behavior 38003801` | exit1,7/8; fixture's per-owner seed violated existing SocialService authority seed |
| Addressed fix pass2: fixture seed18001801 matches existing service, trait differences remain; same focused compile + affected BEHAVIOR route | compile0, exit0,8/8; production code untouched by both fixes |
| `powershell -NoProfile -ExecutionPolicy Bypass -File PhantomSemanticStudio/scripts/Build-Verify.ps1` | exit0; single Release solution build,0 warnings/errors; static Designer PASS; ordinary122/122 |
| Release test DLL `--voice080` / `--java081` | exit0:4/4 including082 parser/staging/rehash negatives; exit0:1/1 exact pins/old SHA refusal |
| First `--pss-008-source` with temp workspace | exit1 at existing location guard, no stage; use required LocalAppData root instead |
| Release DLL `--pss-008-source <actual HighFive> <new LocalAppData workspace>` | exit0; source66 unchanged; synthetic3 staged |
| `powershell -NoProfile -ExecutionPolicy Bypass -File PhantomSemanticStudio/scripts/Test-PSS008-V3-Java.ps1 -StageRoot <stage082> -ProofMode IsolatedCatalog` | exit0; one physical trial; native proof facts above |
| Release DLL `--java081-proof <oracle082/java-validation.json>` | exit0;2/2 and genuine import/forgery/HMAC/handoff controls |

Compiler environment cause was read-only investigated, without changing ACL or guards: sandbox denies enumeration of `C:/Users/ZBook`; installed JDK Windows `toRealPath` walks parents, and javac DirectoryContainer/caseMapCheck silently skips on IOException. Direct `javap` resolves fresh classes, whereas compiler directory enumeration fails. The same final args outside that filesystem sandbox resolve current classes. This was environment/orchestration diagnosis; no additional production recompilation, new test project, Ant, server or DB. Two direct code corrections were confined to the native test fixture. Review's safety-matrix gap was filled in the existing CONTENT case before final execution; no second independent code reviewer or test marathon.

CONTENT includes V3 malformed/unknown/case/threshold negatives, v1/v2/custom exclusions, XXE/malformed XML/duplicate ID/normalized duplicate/band/voice negatives, exact legacy fallback, invalid input maps, all ten real content lines, same selector determinism and recent rotation. Synthetic copied-V3 matrix explicitly proves matching/wrong facet voice, NEUTRAL/CASUAL, profanity-coded exclusion under NONE, mature opt-in×private×TRUSTED gates, LOW/MEDIUM facet reachability and rotation. Existing registered functional-first, support, personal-memory, channel/loop/anti-fabrication and4096-message bounded regression remains in BEHAVIOR.

## Protected scope, exact changed files и Git

До записи сохранены baseline hashes. Не менялись build.xml `13b0144d55d5d1d195b0d859f785e8c9c66dfb22d6190d9f2982a2bef36c7756`, OwnerConfig `605ab76de229b3912d3ed5185688e4b847647f2bbd2ee577a1f0cab294854d5f`, OWNER_FINAL_RELEASE_TARGETS `305ff418d07be303d6a586f17cafd3792294ec89b218eb84b766d7216baaed85`, Social model/service/catalog и V3 semantic manifest. Нет runtime/config/SQL/social.state/market/QoL/rates/Playable078 изменений. Main/Login/GameServer/client/DB/soak, release057–061 и owner QA NOT_RUN;5000 durable/128+ materialized/200+ offline остаются будущим полным договором.

Exact allowlist final artifact scope guard:

1. `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomSpeechFacetProfile.java` — новый pure projector.
2. `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java` — strict V3 metadata, facet-aware selection/legacy isolation.
3. `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedConversationService.java` — actual snapshot projection/selection.
4. `L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/conversation/humanized/v3/segments/lineage/pvp.xml` — пять RU facets.
5. `L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/conversation/humanized/v3/segments/lineage/farming.xml` — пять RU facets.
6. `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPost002SemanticV3Suite.java` — два focused CONTENT cases.
7. `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHumanizedConversationGoal038Suite.java` — preserved079 freeze и native plan082 case.
8. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/Models.cs` — imported PackEntry metadata.
9. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/PackReader.cs` — strict retention.
10. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/V3ProposalContract.cs` — schema/fallback/append-only validation.
11. `PhantomSemanticStudio/scripts/Test-PSS008-V3-Java.ps1` — exact audited pin.
12. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/V3ReleaseHandoff.cs` — exact audited pin.
13. `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/Voice080.cs` — strict metadata/stage regressions.
14. `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/Java081.cs` — current closure pin regression.
15. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-NATIVE-SPEECH-FACETS-082.md` — этот отчёт.

Финальный guard `Final-Guard082.ps1` хранится только во внешнем scratch, не является новым repo artifact/test harness. Exact tracked scope/whitespace и девять protected/dependency hashes PASS, exit0. Первый parser attempt guard получил ParserError: PowerShell интерпретировал типографский символ одного literal marker как кавычку; этот запуск не считается проверкой. Маркеры восстановлены из их точных27 Unicode code-point sequences без изменения набора поиска и проверены двумя отдельными вызовами:

- mojibake-маркеры в изменённых файлах проверены:15 файлов,27 точных case-sensitive маркеров,0 совпадений, exit0;
- escaped Cyrillic в изменённых файлах проверены:15 файлов, все6 Unicode/XML regex,0 совпадений, exit0.

| Acceptance | Фактический gate |
|---|---|
| A1_PROJECTION | PASS: exact10, signed traits, immutable deterministic scores |
| A2_SELECTION | PASS: actual native plan10 styles, equal base voice/different persisted traits |
| A3_HARD_GATES | PASS: legacy exclusion, strict parser и facet-aware safety matrix |
| A4_RU_CONTENT | PASS: десять проверенных reachable RU строк |
| A5_DURABILITY | PASS: reconstructed snapshots и bounded synthetic5000; новая persistence0 |
| B1_STUDIO_COMPAT | PASS: strict retained metadata, current exact pins, physical proof/HMAC/handoff |
| C1_BUILD | PASS targeted native/Studio; full test compile sandbox attempt failed, факт выше сохранён |
| C2_DELIVERY | Exact15 scope/protected/encoding/whitespace PASS; commit/push receipt и post-push equality — в финальном ответе |

Git разрешён прямым TASK082 и root/module/Studio contracts. Выполненные read-only формы: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`, `git rev-parse @{upstream}`, `git remote -v`, `git ls-remote origin refs/heads/feature/phantom-world`, `git worktree list`, `git status --short`/`--untracked-files=no`, exact-path `git diff --`, `git diff --numstat --`, `git diff --check --`. Они сверяют baseline, защищённую ветку и точный scope. Публикация: только `git add -- <ровно15 путей выше>`, cached exact name/stat/check guard, один `git commit -m "phantom(task-082): derive native speech facets and preserve staged proof"`, обычный `git push origin HEAD:refs/heads/feature/phantom-world`; затем HEAD/upstream/live remote и tracked status. Без add-dot/Stage All/reset/clean/stash/restore/branch/rebase/amend/force или новых worktrees.

Self-SHA коммита и фактическое post-push local=remote/tracked-clean подтверждение сообщаются в финальном ответе после публикации, чтобы не создавать второй commit ради собственного SHA. Точный статус всех15 файлов берётся из cached guard; пользовательские untracked сохраняются. Приёмка владельцем остаётся REQUIRED; автоматического install/promotion, explicit Studio facet authoring/export и083/R2 нет.
