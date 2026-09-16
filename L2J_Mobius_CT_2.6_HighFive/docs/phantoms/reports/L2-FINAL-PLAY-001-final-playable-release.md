# L2-FINAL-PLAY-001 — final playable release

Статус: **PARTIAL**. Release tooling, private runtime, sanitized ZIP, Semantic-v3 polish и все DB-free/market/config gates готовы. Existing guarded `phantom-post002-native-support-test` воспроизводимо падает на exact parent-derived worktree: conversation action возвращает `COMPLETED`, но native buff effect не появляется у human requester. Исправление Java/runtime/test выходит за exact scope этой задачи, поэтому failure не скрыт и full `ant verify` не запускался.

## Исходное состояние и scope

- Branch: `feature/phantom-world`.
- Exact parent/HEAD до задачи: `c360e3bb7840148637d5634f3700231b034ed87b`.
- Старый пакет `L2-POST-003-REWORK-4` отдельно не выполнялся.
- До работы уже существовали три user-owned modified файла и множество untracked task/history artifacts. Они сохранены и не включаются в task stage.
- Изменены только три conversation XML, четыре local-play script, русский guide, этот отчёт и минимальный `.gitignore`.
- Java runtime, build.xml, shipped `.ini`, semantic XML и conversation manifest не изменялись.

## Semantic v3

Детерминированный detector прочитал 26 conversation files и все 20 800 templates. Широкий candidate set до правок — 405; после правок — 397. Семантический review отделил допустимые conditional/relationship formulations от 12 точных unsupported event/shared-history/current-scene/action survivors из task package. Исправлены только эти 12 `text=`; ID, act, band, register, profanity и mature metadata сохранены.

Изменения по category: `social.friendly=5`, `life.food=4`, `life.music=3`. Все 12 известных старых формулировок отсутствуют. Новые тексты являются taste/opinion/general formulation и не заявляют произошедшее событие или физическую сцену. Stable tastes вроде «люблю старый джаз» и «предпочитаю горячий суп» не менялись.

Structural comparison с parent после замены `text` на placeholder: `True` для всех трёх файлов. Corpus: 26 files / 20 800 templates. Conversation manifest и весь `dist/game/data/phantoms/semantic` byte-identical к parent. Editorial duplicate/near-duplicate/opening/mechanical gates прошли в `phantom-humanized-v3-content-validate`.

## Local-play workflow

`tools/phantom-local-play/Build-LocalPlay.ps1`:

- удаляет только repo-local `build/dist`, затем выполняет canonical `ant -q jar`; `compile -> init` очищает compiled classes;
- хеширует все source `.ini` до/после и fails if any source config changed;
- копирует полный `dist` в `artifacts/local-play/runtime`;
- patch-only меняет существующие keys и отдельно сравнивает все comment lines в каждом patched `.ini`;
- включает stock `AutoCreateAccounts=True`, account allowlist `localplayer`, все принятые Personal QoL switches, global accepted Auto-Noblesse, Phantom master/ecology/Humanized v3/custom overlay и autonomous market;
- Mature и Diagnostics остаются explicit `-Mature` / `-Diagnostics` switches, default `False`;
- создаёт CMD entrypoints, effective manifest без секретов и optional sanitized ZIP;
- не provision-ит DB, не создаёт account и не запускает `prepare-phantom-test-db`.

Presets: Balanced `120/24/cap32`; Lively `160/32/cap48`; Stress `240/48/cap64`. Для всех pulse `100 ms`, profiles-per-pulse `128`, ecology `LIVING`. Final runtime собран с `Lively`, `localplayer`, Mature/Diagnostics OFF.

`Start-LocalPlay.ps1` не допускает duplicate start, ждёт LoginServer port `9014`, затем GameServer port `7777`, пишет PID+start-time records. `Stop-LocalPlay.ps1` сначала пытается закрыть подтверждённое окно, затем останавливает только recorded PID; global Java taskkill отсутствует. `Check-LocalPlay.ps1` сверяет manifest/config/JAR/process/ports без печати credentials.

Четыре source scripts сохранены UTF-8 BOM для Windows PowerShell 5.1. Source и staged copies проходят WinPS parser. Staged Check: `CONFIG PASS`; safe no-process Stop: PASS.

## DB и artifacts

Оба существующих local runtime `Database.ini` найдены и скопированы только в private staging. Credentials не печатались. Они указывают на project production-named DB `l2jmobiush5`, поэтому automated startup smoke **не выполнялся**: для безопасного non-production smoke статус `DB_CONFIG_REQUIRED`. Guarded test suites использовали только существующий `l2jmobiush5_phantom_test`.

Sanitized ZIP содержит два `Database.ini` с пустыми `Login` и `Password` и `DB_CONFIG_REQUIRED.txt`. ZIP не содержит private staging credentials. Runtime/ZIP/JAR/log/PID artifacts игнорируются Git.

- Runtime: `artifacts/local-play/runtime` (~1.19 GB).
- ZIP: `artifacts/local-play/L2J-H5-Phantom-LocalPlay-sanitized.zip`.
- ZIP size: `262045194` bytes.
- ZIP SHA-256: `ACE230DCEE10E9EBFFCA66CCDDC0793AE2FDD3ED8666252A9EDA4C2134C5B570`.

## Проверки

- Первый sandbox validator: environment failure, JDK `AccessDeniedException` на `HikariCP-7.0.2.jar`; повтор вне sandbox — PASS.
- `ant -q phantom-humanized-v3-content-validate`: PASS.
- `phantom-post002-semantic-v3-scale-test`: PASS.
- `phantom-post002-freeze-test`: PASS.
- `phantom-local-play-preflight-test`: PASS.
- Config/package focused suites: `qol-personal-skills-test`, `qol-crystallization-test`, `qol-effect-duration-test`, `qol-shop-test`, `qol-seven-signs-access-test`, `qol-party-support-test`, `qol-economy-progression-closure-test` — PASS.
- `phantom-post001-autonomous-market-test`: PASS (current aggregate advanced past it without failure; existing suite 3/3).
- `phantom-post002-support-test`: PASS (existing suite 5/5).
- `phantom-post002-conversation-test`: PASS 4/4 standalone.
- `phantom-post002-native-support-test`: FAIL 0/1 twice, одинаковая assertion: `Same-party conversation support did not produce a native effect on the human requester.` Database guard confirmed `l2jmobiush5_phantom_test`; no provisioning.
- Fresh final build from workflow: `ant -q jar`, BUILD SUCCESSFUL, 27 seconds; fresh LoginServer/GameServer JAR copied to runtime.
- Новый full `ant verify`: NOT RUN по прямому запрету task package.
- Runtime startup smoke: NOT RUN; production-named DB config нельзя использовать для automated mutation, non-production runtime config отсутствует.

## Performance expectation

Durable target primarily scales scheduler state and DB/disk footprint. ACTIVE/materialized target drives the heavier Player/world/known-list/AI CPU and RAM cost, so expected order is Stress > Lively > Balanced. All presets keep the accepted shared 100 ms scheduler pulse and bounded work; no benchmark percentages are claimed. No runtime RSS/CPU snapshot exists because safe startup smoke was unavailable.

## Delivery

Exact commit subject: `release: prepare phantom local play build`. Commit SHA and normal-push remote equality are reported in final handoff because a commit cannot contain its own SHA. Git inspection was explicitly permitted by task/Agents and used only for parent/branch/status/scope/diff verification and delivery; no reset/restore/history rewrite/force push was used.
