# PSS-005 — финальный отчёт, 2026-10-08

**LOCAL_SCOUT_PASS, SEMANTIC_MOCK_PASS, Release Build-Verify PASS: 88 PASS / 0 FAIL, 0 warnings / 0 errors, exit 0. Targeted PSS-005 11/0, actual controls 3/0. DESIGNER_STATIC_PASS. LIVE_LM: BLOCKED_LM / BAD_RESPONSE, один POST, без новой evidence. Модель выгружена. Interactive UI / DPI_100 / DPI_150 / VS Designer round-trip NOT_TESTED. Java NOT_RUN; SERVER_XML_NOT_PUBLISHED.** Engineering checkpoint с открытыми manual UI gates; полный смысловой coverage не доказан.

Root `C:\Users\ZBook\PhantomSemanticStudio`. Required base = initial HEAD = local origin/main = фактический remote main **`5d1aedaed38d048e3313c0d8be7d5f81f4db2df2`**. Branch master сохранён; fetch/push origin только `https://github.com/kpCat/PhantomSemanticStudio.git`. Initial tree clean; затем пользователь добавил 10 task-package files, сохранённых byte-for-byte. Initially missing пакет прочитан после сообщения пользователя о появлении; scope не угадывался.

## Scope и поведение

Exact inventory: [PSS-005-owned-files.txt](PSS-005-owned-files.txt), **49 paths**: 11 source/test/script/README, 10 unchanged task files, 28 reports/evidence. Bounded exception предусмотрен IMPLEMENTATION; [plan](PSS-005-plan.md) фиксирует read pass, source owners, interfaces и ограничения. .NET 10/C#14/WinForms, без новых NuGet/проектов/storage/provider слоёв.

- `SemanticDuplicateScout.Search(snapshot, candidate, peers, maxMatches=12, token)` — pure offline same-kind search. Все imported записи вида кандидата и non-REJECTED peers, кроме самого кандидата, рассматриваются локально. Existing NFKC/ru/ё/trigrams плюс token overlap; exact first, act/topic context priority, stable Ordinal RefKey. Top<=12 поддерживается при обходе одного кандидата, без глобального O(N²). SOURCE|Kind|ID и PEER|ID не сливаются. Specific texts/hash/provenance/score/counts и `COVERAGE_LIMITED` видимы, включая пустой результат.
- `LmStudioClient.ReviewSemanticAsync(settings, key, candidate, shortlist, token)` — один loopback POST, exact model ID, json_schema/stream=false/temperature=0.1, user settings неизменны. Цитаты недоверенные, prompt запрещает tools/код/XML/исправления/подтверждение игровых фактов. Context<=64 KiB, response<=1 MiB. Existing SendAsync guards/no proxy/redirect/retry сохраняются; generation использует извлечённый strict envelope без ослабления прежних проверок.
- `ParseSemanticVerdicts` требует exact RefKey set, ровно refKey/relation/reason и SAME_MEANING/RELATED/DIFFERENT/UNSURE, reason 1–240 chars без controls. Duplicate/extra/missing IDs/JSON keys, extra fields, fence, malformed/oversize/truncated JSON, wrong labels, non-stop finish, tools/function_call/refusal/multi-choice отклоняются целиком. Diagnostics не раскрывают token/body/prompt.
- Optional `Candidate.SemanticReview`: typed IDs/hashes/provenance/verdict/reason/model/UTC, <=12 verdicts, <=256 model ID, <=32 KiB object. WorkspaceStore валидирует Load/Save и сохраняет атомарно через session Version=1. Нет full prompt/response/corpus persistence. CandidateReview.Fingerprint неизменён: существующие APPROVED сохраняются после анализа. Edit сбрасывает evidence штатно вместе с approval.
- `IsEvidenceCurrent` пересчитывает candidate text/scope SHA, import fingerprint, sorted source stamps+entry content/provenance SHA, active peers SHA, ordered shortlist SHA и exact referenced hashes/locations. Изменение candidate/source/peer/reference даёт STALE. `WithCurrentEvidence` возвращает detached copy только после полного recheck; cancellation/drift не создают partial persistence.
- Две static Designer кнопки MainForm. Existing Save/Discard/Cancel разрешает unsaved edits до действия; offline путь читает источник без сети. Gemma отдельно по кнопке; source reread + full hash recheck непосредственно перед synchronous atomic SaveCandidateVersion. Ошибка LM сохраняет локальный список, BLOCKED_LM, без новой evidence. Saved report при выборе перечитывает источник и показывает advisory либо STALE; несохранённая правка также видимо STALE. SAME_MEANING — предупреждение человеку. Approval/reject/edit/stage/generation mode автоматически не меняются.

## Реальные проверки

| Команда / evidence | Фактический результат |
| --- | --- |
| `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release --no-restore -- --pss-005` | Offline RED 0/3 → GREEN 3/0; HTTP RED 3/4 → GREEN 7/0; persistence RED 8/3 → GREEN 11/0. Logs scout/http/evidence red/green. Fail-closed API scaffolds, не compile-error RED. |
| `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release --no-build -- --pss-005` | Final **11 PASS / 0 FAIL**, exit 0: [targeted](PSS-005-tests-green.txt). |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Verify.ps1` | Final **88/0**, **0 warning/error**, exit 0: [Release](PSS-005-build-final.txt). Existing 77 ordinary плюс 11 новых; PSS-003 10 stage/PSS-004 8 selection groups включены; Java не запускается. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-PSS005.ps1 -StaticOnly` | UI RED → **DESIGNER_STATIC_PASS**: six tabs/static controls/events/constructor/freshness guard; [Designer](PSS-005-designer.txt), [RED](PSS-005-ui-red.txt). Не VS round-trip. |
| `dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-005-controls` | Final **3/0**, exit 0: [controls](PSS-005-controls.txt). Public real controls/layout/local report/session integrity/edit reset/physical-source drift/invalid UTF-8. Без private shell harness/fake UI. |
| `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release --no-build -- --pss-005-source C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive` | Exit 0: **65 files / 5310 PATTERN / 20963 TEMPLATE**; рассмотрено **20963**, scope **6**, shortlist **12**, НЕ передано модели **20951**. Model calls **0**, source SHA/bytes equal: [source](PSS-005-source.txt). |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-PSS005.ps1` | Два strict UTF-8 прохода, unchanged package SHA/bytes и 17 frozen owner stamps: [verification](PSS-005-verification.txt). |

Synthetic 5k PATTERN/20k TEMPLATE smoke проверяет bounded stable selection при перестановке inputs. Synonym counterexample подтверждает COVERAGE_LIMITED независимо от retrieval; recall синонимов не гарантируется. Fake HTTP positive проверяет emitted payload/schema/model/temperature и advisory-only output; fake не выдан за live. Typed synthetic evidence содержит SOURCE refs/text hashes/SAME_MEANING, не меняет Status/ApprovedFingerprint. Corrupt evidence Save сохраняет старые bytes; legacy session без поля загружается. Physical fixture source меняется во время fake HTTP: final recheck блокирует запись, session/approval сохраняются.

## Live LM / unload

`lms server status --json`: running=true, port=1234. `/v1/models` GET содержал точный default Gemma ID; initial `lms ps --json` = `[]`. Codex/Studio не выполняли lms load/запуск сервера; один разрешённый operator POST привёл к JIT загрузке сервером.

`dotnet run --project tests/PhantomSemanticStudio.Tests -c Release --no-build -- --pss-005-live`: один synthetic candidate, **1 reference**, exact `gemma-4-26b-a4b-it-ultra-uncensored-heretic`, MaxTokens=1024, Timeout=180. **Exit 2 / BLOCKED_LM / BAD_RESPONSE / 119 s**, без retry/model substitution. Строгий контракт не пройден; точная причина raw body не диагностировалась/не публиковалась. Session bytes unchanged, новая evidence отсутствует: [live](PSS-005-live-lm.txt). Live смысловое качество не подтверждено.

`lms unload gemma-4-26b-a4b-it-ultra-uncensored-heretic`: exit 0. `lms ps --json`: exit 0 → **`[]`**: [unload](PSS-005-model-unload.txt). Модель повторно не запускалась; позднее предложение пользователя загрузить её не привело к повторному запросу.

## Ошибки / review

Initial sandbox Build-Verify: NuGetScratch lock permission, **3 errors / exit 1**: [initial](PSS-005-build-initial.txt). Locks/config не удалялись; approved штатный запуск прошёл. После review fixes выполнен новый final Release. Промежуточная tests compilation во время ещё running live получила **6 MSB3026 apphost lock warnings** (занят собственный executable); это не final result. Final после завершения live/unload: 0 warnings/errors.

Persistence fixture ошибочно присваивала тот же Register=NEUTRAL и ожидала STALE: 10/1, [attempt](PSS-005-evidence-attempt.txt). Исправлена на реальную смену CASUAL → 11/0. Geometry RED обнаружил hidden TabPage default 200×100 anchor baseline; static Size задан только tabCandidates, без broad refactor.

Первый полный text/scope verifier получил sharing violation при чтении собственного ещё записываемого report. Диагностика сохранена в ignored artifacts; stdout перенесён в отдельный ignored temporary log, затем закрытый результат скопирован в report и проверен повторно. Ни один inventory path не исключён из двух текстовых проходов.

Fresh-context [review](PSS-005-review.md): 0 Critical, 2 Important исправлены с RED/GREEN. Invalid UTF-8 selection теперь STALE без crash; unconditional current=true после fresh ShowCandidate удалён. Static display guard отдельно от actual source-drift control test; deterministic race между save/rebind не заявляется. Deferred Minor: saved-evidence selection синхронно читает pack и дважды выполняет scout на UI-потоке, возможна задержка/недоступность Cancel. Явные кнопки cancellable. Ограничение сохранено, без скрытой переработки selection flow.

## Безопасность / открытые gates

L2J строго READ_ONLY: только PackReader humanized inspection. Никаких Git/Ant/Java/JAR/server/client/DB/writes/temp/rename/overwrite в L2J. Actual source fingerprint **`43c49f49e298c7f35cfef8577f35a4dc50e585191d81073afc3f49ad3a81e322`**, 65 stamps unchanged. Frozen 17 Studio owners: PackReader/TextRules/PathSafety, Stager/Exporter/selection forms/MainForm.resx, Java operator/probe и projects/solution/props. Existing XML STAGED_UNVALIDATED до отдельного Java proof; прошлый Java PASS не наследуется.

Binary/session/model body/UI fixture/stage XML вне Git, в own ignored artifacts/temp/workspace. Ordinary runner использует собственные synthetic ephemeral stage/JSON fixtures. Review/source ZIP/New-ReviewBundle не создавались/не запускались; пользовательский REVIEW_ONLY JSON ZIP сохранён. Evidence не входит в approval hash; SEMANTIC_NOT_CHECKED сохранён. No install/apply/promotion/model tools/retries/background scans.

[UI report](PSS-005-ui.md): controls/static PASS отделены от interactive acceptance. Computer Use screenshot recovery failed (FrameArrived/window capture timed out); own hidden synthetic Studio process не дал targetable окна и завершён. UI/DPI_100/DPI_150/VS round-trip **NOT_TESTED**, manual acceptance **REQUIRED**. Java **NOT_RUN**, XML **NOT_PUBLISHED**.

- mojibake-маркеры в изменённых файлах проверены отдельным проходом;
- escaped Cyrillic в изменённых файлах проверены отдельным проходом.

Только literal technical marker array verifier исключён из mojibake-поиска; user-facing строки не исключаются. Task/history bytes не переписываются; trailing whitespace нормализуется только в task-owned logs.

## Git checkpoint

Git разрешён прямым /goal и SAFETY_AND_GIT, только own root. Использованы `git status --short`, `git status --porcelain=v1 -uall`, `git branch --show-current`, `git remote -v`, `git remote get-url origin`, `git remote get-url --push origin`, `git rev-parse HEAD origin/main`, `git rev-parse HEAD`, `git rev-parse --show-toplevel`, `git ls-remote origin refs/heads/main`, `git diff --stat`, `git diff --check`, `git diff -- src/PhantomSemanticStudio.Core/CandidateValidator.cs`; reviewer — scoped read-only diff/status. Restricted-network initial ls-remote failed; approved повтор подтвердил base.

Closing: remote still base; `git add -- <49 exact inventory paths>`; `git diff --cached --name-only`, `git diff --cached --stat`, `git diff --cached --check`; `git commit -m "feat(pss): add advisory semantic duplicate review"`; `git push origin HEAD:refs/heads/main` без force; `git rev-parse HEAD`, `git rev-parse HEAD^`, `git diff --name-only HEAD^ HEAD`, `git ls-remote origin refs/heads/main`, `git status --porcelain=v1 -uall`. Inventory/index/exact commit должны совпасть. Никаких branch/checkout/reset/clean/stash/rebase/merge/amend/force/git add . или Git в L2J.

Отчёт precommit: собственный final SHA нельзя включить в тот же единственный commit. Реальные SHA/parent/push exit/remote equality/public SHA/clean outcome публикуются после команд в handoff и ignored artifacts/PSS-005-checkpoint.json. Remote drift → BLOCKED_REMOTE, no force. Review в публичном GitHub exact commit/diff/report/inventory, без архивов.

**STOP после PSS-005. PSS-006 не начат; manual UI gate не принят автоматически.**
