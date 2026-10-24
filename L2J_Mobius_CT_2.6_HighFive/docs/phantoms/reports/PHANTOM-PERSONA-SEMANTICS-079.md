# PHANTOM-PERSONA-SEMANTICS-079 — 24.10.2026

Статус реализации: **SUCCESS для bounded P0/P1/P2, DB-free**. Финальный игровой релиз, масштаб мира и клиентская приёмка этим результатом не объявляются.

## Authority и read-first

- Root фактически `D:\Codex\repos\L2J-phantom-world`, ветка `feature/phantom-world`; local HEAD, origin tracking ref и live remote до записи: `8b9c807df838bde720b264d85509f72d2ee203c4`.
- Прочитаны root AGENTS.md, README.md, PHANTOM_CURRENT_STATUS.md, CODEX_WORKFLOW_CONTRACT.md и все 10 файлов распакованного пакета 079. Девять payload SHA-256 совпали с MANIFEST_SHA256.txt; отдельный ZIP079 в checkout не найден.
- Module/nested AGENTS.md и отдельные code-map/pattern Markdown в bounded inventory не найдены; использован пакетный SOURCE_MAP.md. До патча прочитаны ровно 10 Java/build/test source owners ограниченными срезами, без широкого обхода Java.
- Аналоги: существующие TemplateBucket/exact-band selection, strict requireAttributes/enum parsing, custom-last override, Social MemoryStore и in-memory personal CAS fixtures. Сохранены Java25/Ant, единственный серверный Semantic Engine и прежние caps.
- Исходно были удалены 10 tracked-файлов root `docs/phantoms` задачи 064. Пользователь отдельно разрешил восстановить только их из HEAD. Восстановлены с отключённой для команды EOL-конверсией; их diff пустой, в commit079 они не входят. Прочие untracked данные сохранены.

## План и gap matrix

1. Воспроизвести отсутствие контекстной фразы и отсутствие voice eligibility в существующей behavior suite.
2. Расширить только V3 template metadata, передать существующий native persona voice в selection, добавить 3 точных patterns и малые ответные пулы в двух существующих категориях.
3. Проверить strict/fallback/legacy/privacy/relationship contracts, выполнить одну final JAR-цель, скопировать owner targets, проверить exact scope и опубликовать один обычный commit.

| Область | До079 | Результат079 |
|---|---|---|
| Native traits/persona | calm/open/direct/empathetic вычислялись, меняли seed | То же personaKey теперь ограничивает реальный пул шаблонов |
| Relationships | Social authority и band gates уже были | Без изменения authority; UNKNOWN, TRUSTED/RIVAL из сохранённого Social проверены |
| Memory/identity/support | TTL/CAS/recent hashes/turn budget/native identity/functional-first уже были | Production owners этих контрактов не изменены; focused regression прошла |
| PvP/farm | Категории уже существовали, 3 точные фразы отсутствовали | Новые patterns в существующих acts, без новых событий/действий/сырой истории |
| Финальный масштаб | Требования владельца в пакете | Canonical tracked документ; реализация масштаба отложена |

## P0 — selection и совместимость

- Template получил optional `voice`: пусто либо `calm`, `open`, `direct`, `empathetic`. Атрибут разрешён только V3 segment parser; неизвестные значения/регистр/атрибуты отвергаются. Старый Java constructor/select overload и V1/V2/custom XML сохранены.
- Сначала прежний индекс ограничивает act, допустимый band (точный или UNKNOWN), register, profanity и mature. Затем выбирается совпадающий voice; внутри него точный band имеет приоритет. Если voice неизвестен/отсутствует в контексте, используются только универсальные templates, никогда соседний voice.
- Для каждого voiced act loader требует clean universal UNKNOWN/NEUTRAL fallback. Deterministic selector, recent-response rotation, mature opt-in + private + TRUSTED и profanity policy сохраняются. Индекс/caps не расширялись, новых per-Player копий каталога нет.
- В V3 segment отвергаются одинаковые normalized pattern phrases. Попытка глобального запрета пересечений выявила существующее V1/V3 greeting пересечение; глобальный запрет не оставлен ради совместимости. Старые межсемейные пересечения не мигрировались.

## P1 — правдивая речь

- `меня слили в пвп` → `lineage.pvp.risk.reply`; `хочу пофармить`, `хочу пойти на спот` → `lineage.farming.spot.reply`. У всех fact/recall отсутствуют, TTL=0; сообщение не становится native receipt или сохранённым игровым фактом.
- Добавлены 20 templates: 18 voiced (по два универсальных по band ответа на voice/act и два отдельных TRUSTED/RIVAL), два generic. Четыре изменённых XML: 488539 bytes после правок, +3 patterns/+20 templates. Общий V3 load: 5313 patterns / 20995 templates.
- Примеры: calm «Спокойно оценим риск. Какие подробности у тебя есть?»; open «Давай обсудим пвп. Что тебя сейчас волнует?»; direct «Проверь баф и позицию. В чём главный риск?»; empathetic «Пвп может вымотать. Хочешь обсудить, что тебя беспокоит?».
- Отдельный read-only reviewer выявил предположение о прошедшем бое у старых prospective risk inputs. Исправлено в одном проходе, regression `бой можно избежать` RED→GREEN. Новые тексты не заявляют kills/loot/пати/покупки/телепорт и не запускают native action.

## P2 — конечные требования владельца

`docs/phantoms/OWNER_FINAL_RELEASE_TARGETS.md` скопирован побайтово из FINAL_OWNER_WORLD_TARGETS.md; SHA256 `305ff418d07be303d6a586f17cafd3792294ec89b218eb84b766d7216baaed85`.
Документ сохраняет 5000 persistent identities, cap128+ с реальной нагрузочной приёмкой, минимум200 native offline shops и ориентир300–500, custom Alt+B, все согласованные owner-only QoL, будущие точные AllowedAccounts/IDs, самостоятельный SaveOwnerConfig.bat владельца и сохранение его рейтов. Это **NOT IMPLEMENTED в079**, конфиги населения/QoL/рейтов не менялись.

## Проверки и реальные receipts

- RED: `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-humanized-goal038-behavior-test` → exit1, 6/7: новая PvP фраза отсутствовала. После pattern-only patch прямой существующий launcher снова дал exit1, 6/7: voice не ограничивал пул.
- Негативные RED: отсутствующий universal fallback принимался; normalized duplicate внутри segment принимался; новые risk replies предполагали прошедший бой. Все три получили focused regression и исправлены. Глобальный duplicate эксперимент честно упал на старом greeting, после сужения validator content GREEN.
- Промежуточно использованы `javac -encoding UTF-8` только для двух изменённых production и двух существующих test owners с текущими build/bin, build/phantom-test/bin и dist/libs; затем `org.l2jmobius.tests.phantoms.PhantomTestLauncher` в content/behavior modes. Нового harness/launcher/test project нет.
- Final: `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar phantom-humanized-v3-content-validate phantom-humanized-goal038-behavior-test phantom-humanized-goal038-catalog-test` → **exit0**, BUILD SUCCESSFUL, 2m07s: **5/5 content + 7/7 behavior + 5/5 legacy catalog =17/17**.
- JAR target исполнен **ровно один раз**. Из-за независимых top-level Ant targets в этой команде production compile исполнился **четыре раза**, test compile трижды; это повтор зависимостей Ant, а не дополнительные JAR/release циклы. До неё был отдельный RED compile. Дополнительных финальных сборок не было.
- Существующие 5 test compiler warnings: ExtendedOpenOption (3), System.runFinalization (2). XML DOCTYPE fatal diagnostic в catalog negative — ожидаемый XXE reject; suite PASS.
- Новый coverage: 3 focused test registrations; 4 native trait profiles одного band, same-selector четыре разных тона, stable service choice, recent rotation, сохранённые TRUSTED/RIVAL, 7 bands, unknown/missing fallback, voiced mature opt-in/private/register, profanity NONE, native identity spoof/unavailable guard, support buff/song/dance, no raw game facts. Прежние memory TTL/CAS/restart-codec/16-subject limits и ping-pong gates прошли.
- GameServer.jar SHA256 (build/dist/libs и module dist/libs совпали): `f56dabe46d09bfb65a03b7824abe6aeaa16255d4cd67abcd1fe3ef86fe57a471`.
- V3 combinedHash без custom: `636f918e31e9006439ada95fb098fc7f32aaf0ff0337d9aa95d4d584ceefb598`.
- OwnerConfig Verify и штатный build Apply: filesChecked68 / overrides14 / conflicts0 / changed0; 38 OwnerProfile rules PASS. Флаги прочитаны: System=False, Humanized=True, Custom=True, CASUAL/CONTEXTUAL/HIGH, Mature=False; не изменялись.
- Локальные raw receipts: `build/079-evidence/` (untracked build output) и final Ant log. Synthetic scale NOT_RUN: индексы/caps не менялись. Общий verify, DB/persistence/composition, Main, клиент, candidate/install, stress — NOT_RUN по scope.
- mojibake-маркеры в изменённых файлах проверены — отдельная проверка полного набора, совпадений нет.
- escaped Cyrillic в изменённых файлах проверены — отдельная проверка всех шести regex, совпадений нет.

## Actual changed-file allowlist

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`; это точный SCOPE из 10 файлов:

1. `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java`
2. `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedConversationService.java`
3. `dist/game/data/phantoms/semantic/humanized/v3/segments/lineage/pvp.xml`
4. `dist/game/data/phantoms/semantic/humanized/v3/segments/lineage/farming.xml`
5. `dist/game/data/phantoms/conversation/humanized/v3/segments/lineage/pvp.xml`
6. `dist/game/data/phantoms/conversation/humanized/v3/segments/lineage/farming.xml`
7. `test/java/org/l2jmobius/tests/phantoms/PhantomPost002SemanticV3Suite.java`
8. `test/java/org/l2jmobius/tests/phantoms/PhantomHumanizedConversationGoal038Suite.java`
9. `docs/phantoms/OWNER_FINAL_RELEASE_TARGETS.md`
10. `docs/phantoms/reports/PHANTOM-PERSONA-SEMANTICS-079.md`

## Git и границы публикации

Git разрешён текущим TASK/DELIVERY и root AGENTS; exact restore064 — отдельным ответом пользователя. Использованы `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse refs/remotes/origin/feature/phantom-world`, `git remote -v`, `git ls-remote origin refs/heads/feature/phantom-world`, `git status --short`, варианты `--untracked-files=no`, `git ls-tree HEAD -- docs/phantoms/tasks/PHANTOM-M1-M7-CLOSURE-064/AUTHORITY.md`; exact-path `git diff` (также --stat/--name-only и диагностический `-c core.autocrlf=false`) и `git diff --check`. Обычный сетевой ls-remote сначала был недоступен в sandbox; разрешённый повтор подтвердил remote.
Восстановление: `git restore --source=HEAD --` только 10 исходных удалённых путей064 (первый sandbox вызов отказан ACL; повтор разрешён); затем та же exact list с `git -c core.autocrlf=false restore --source=HEAD --` для сохранения LF bytes. Ни reset, ни clean/stash/worktree/branch/force не использовались.
Финальная публикация: `git add -- <SCOPE>`, `git diff --cached --name-only`, `git diff --cached --check`, `git diff --cached --stat`, exact staged diff, `git commit -m "PHANTOM-079: select humanized responses by native persona voice"`, `git push origin HEAD:refs/heads/feature/phantom-world`; затем actual `git rev-parse HEAD`, `git ls-remote origin refs/heads/feature/phantom-world`, `git status --short --untracked-files=no` и общий status. SCOPE — ровно перечисленные выше пути с module prefix, без task packages/user data.
Этот отчёт входит в единственный commit079; его собственный SHA и результат post-push local=remote/tracked-clean публикуются в финальном сообщении, после выполнения команд, без самоссылочного SHA или второго commit.

DB/client proof **NONE**. Playable-078-owner, секреты/OwnerConfig, Studio, 074–078 gameplay и чужие runtimes не изменялись. Прежние PARTIAL/OWNER_QA_PENDING сохраняются. После normal push — STOP; 080/R2 не начинается.
