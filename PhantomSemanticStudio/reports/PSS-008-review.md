# PSS-008 — independent review and rulings

Два read-only review контекста: source-contract preflight и один свежий whole-change reviewer. Реализацию выполнила основная Codex. Reviewers не меняли файлы/index/branch, не запускали Java/Ant/network и не читали private staged XML/raw oracle logs. Финальный вердикт после чтения исправлений: ready with fixes applied для production-кода, открытых Critical/Important0. Финальные build/verifier/reports/publication проверяет исполнитель отдельно.

## Contract preflight

Фактический manifest52 /26pairs, topic130 /act139; source65, pattern5310 /template20963. Проверены реальные Java loadV3/index/selector и шесть Ant task shapes/classpaths. Важные различия: template не имеет topic/gender; NONE bucket включает mature; custom может маскировать effective provenance. B поэтому читает stamped segment XML, A использует effective PackSnapshot. Нормализатор C# остаётся approximation, Java proof проверяет реальные выбранные строки. Native deterministic template probe ограничен global32768: fallback eligible union может превышать4096. Whole build SHA дополнительно pin, поскольку task shapes сами не фиксируют top-level properties.

## Исправленные Important

1. Объявленные scopes без entries исчезали из A. Теперь zero-count TOPIC/ACT, orphan-template advisory, dropdown из pack.Topics. Реальный focused RED3/1 → GREEN4/0, reports/PSS-008-a-coverage-red.txt и a-final.txt.
2. V3 UI публиковал proof hash до второго awaited ReadStage/preview. Теперь assignment после обеих проверок, построения preview и последнего token check; исходник перечитан reviewer и проверен static gate. Новые STA controls2/0; физическое race-воспроизведение не заявляется.
3. Отмена во время final PeerHash оставляла окно перед rename. Теперь свежий token непосредственно перед Directory.Move. Детерминированный IReadOnlyList cancel-on-final-enumeration воспроизвёл RED7/1 с временно снятой единственной проверкой; restored fix GREEN8/0. Watcher tests отдельно покрывают source/approval/cancel в собственных fixtures и сохранение unrelated sentinel.
4. Public hashes/tooling + согласованные fabricated logs проходили proof inspection. Реальный regression на собственной synthetic stage с genuine native inputs дал «Expected failure, operation succeeded», c-forgery-red.txt. Теперь per-stage private key вне oracle, HMAC домен/stageId/exact raw proof SHA, immutable detached attestation только после native success. Полный proof SHA связывает receipt/source/stage/tools/input inventory/spec/logs/status. Genuine proof accepted; consistent forged logs/proof/attestation public SHA с copied old MAC rejected, c-forgery-green.txt. Ключ никогда не попадает в receipt/source/reports/Git.
5. Native UNKNOWN/CASUAL fixture пропускала wrong-register assertion. CASUAL теперь всегда проверяется против NEUTRAL; native final stdout сообщает wrongAct CHECKED, register CHECKED, band NOT_APPLICABLE. Existing UNKNOWN fallback нельзя ложно считать исключением по band.

Minor закрыты: NoReparse proof до первого чтения; V3 junction/parent-file/unrelated sentinel/mid-copy tests; spec SHA и copied module/baseline/staged input stamps повторно проверяются после native запусков перед аттестацией. Новый verifier тестирует пять build redirect/task/property мутаций и catalog drift только в Studio fixture, before-Ant rejection.

Narrow continuation того же review seat после финального catalog pin обнаружила verifier-only риск: destination копии строился из receipt path до traversal/reparse проверки. Root исправил до публикации: exact own stage integrity через существующий runner перед чтением copy inventory; каждый stamp ограничен semantic/conversation relative path без traversal/colon/backslash; canonical-within-fixture и все reparse ancestors проверяются до mkdir/copy и перед последующими fixture writes. Final JavaGuards/encoding/source/scope stdout PASS; нарушенный receipt не используется как инструкция файловой записи. Catalog pin и final/UI/review отчёты reviewer перечитал без новых blocking production concerns. Никакой Git/native/network команды continuation не добавила.

## Rulings / Declined to judge

- HMAC — attestation честного локального оператора. Public-metadata-only forgery/cross-stage reuse отвергается; злонамеренный владелец ОС, читающий key или подменяющий JDK/Ant, не покрывается. Нельзя называть это изоляцией от OS owner. Не вводятся shell в GUI, новые зависимости или installer.
- Hostile TOCTOU в момент OS rename не решается стандартными pathname guards. Fresh cancel check перед атомарным rename — commit boundary; отмена после состоявшегося rename не удаляет готовую историю. До boundary проверяются cancel/source/approval.
- Полная смысловая/грамматическая истинность остаётся редакционным review. Conservative text checks не дают semantic completeness; lexical sample<=256/2012 не доказывает отсутствие всех paraphrases.
- Runtime/social/persona/gameplay parity вне content gate. Java PASS подтверждает catalog content, никогда установку или готовность runtime.
- Physical UI/DPI100/150/VS, live LM, actual chat/corpus/PII не проверялись reviewers. Root оставляет NOT_TESTED/BLOCKED_LM; private данные не читались и не передавались.
- Final Build-Verify, итоговые verifier/reports/inventory и publication reviewer отложил исполнителю: они создавались параллельно. Root проверяет их отдельными командами и exact GitHub SHA.
- FileSystemWatcher callback асинхронен; на другой машине timing/IO failure требует диагностики. Детерминированный final-boundary regression не зависит от watcher.
- Private stage/proof/raw logs намеренно не публикуются. Для публичного review имеются safe aggregate counts, SHA, exits и source stamp inventory. Новая задача не создаёт review ZIP.
- Scope >10 paths bounded: одна явно запрошенная A/B/C задача, 21 code/project/test/script/README owner плюс инструкции и безопасные evidence/reports. Core csproj только доставляет фиксированные operator assets для проверки hash; стек/зависимости/session/schema/custom pipeline не меняются.

Reviewer Git: `git status --short`; `git diff --stat 0344c9c0671d53a7bb757db7a92b1dfd03246e9c`; `git diff 0344c9c0671d53a7bb757db7a92b1dfd03246e9c -- src/PhantomSemanticStudio.Core/PhantomSemanticStudio.Core.csproj src/PhantomSemanticStudio.WinForms/MainForm.cs src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs tests/PhantomSemanticStudio.Tests/Program.cs`. Только own-root read-only inspection, разрешённое task. Других Git mutations reviewers не делали.
