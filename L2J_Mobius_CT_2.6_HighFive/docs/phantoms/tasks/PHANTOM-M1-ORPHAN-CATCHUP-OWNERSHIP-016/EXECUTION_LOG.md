# TASK016 execution ledger

План: PHANTOM-M1-ORPHAN-CATCHUP-OWNERSHIP-016/PLAN.md. Спецификация: DESIGN.md и прямая инструкция пользователя.

- A complete: exact remote 685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d подтверждён через ls-remote. Основной checkout foreign/read-only.
- Ruling: experiment local branch занята другим worktree. Новый разрешённый worktree создан detached от exact remote. Normal push выполняется HEAD:experiment/m1-candidate007-observe008; чужой checkout и его local branch не перемещаются. Паттерн TASK014 уже использовал отдельный local checkout для той же remote branch.
- A complete: retained observe014 SELECT-only consistent transaction, 10000 managed profiles, 30000 exact canonical roundtrips. OWNED_EXACT=7800; COMPLETE_HISTORY_IDLE=2145; ORPHAN_EXACT_ADOPTABLE=55; conflicts=0.
- Profile110: from=cursor=29852110, target=29852115, seedMatch=true.
- Profile175: from=cursor=29852109, target=29852115, seedMatch=true.
- Offline classification означает persisted candidate. Runtime owner/safeBoundary/CAS разрешают actual adoption только в worker.
- Baseline ecology: exit0, старые 18 controls.
- RED: total30/pass21/fail9. O01/O02/O03/O10/O11/O12 нет adoption; O05/O06/O07 нет typed conflict; старые 18 GREEN.
- Production: только EcologyService. Existing historical requestId/target принимаются через state.beginRequest; historical component и cursor не пишутся; ecology cursor сохраняется. Sentinel завершает processRequested worker turn, обычный advance выполняется в следующем batch.
- CAS: reload exact StoredState перед save; стандартный save CAS; после stale cache только publish durable row и bounded retry. Owner/safeBoundary только transient defer, без terminal corruption.
- First GREEN: 29/30; O10 fixture ожидал retry до штатного backoff. Ruling: после снятия safety block тест ждёт bounded retry до256 pulses; это соответствует TESTS.md «until safe», production не менялась дополнительно.
- Final ecology: total30/pass30/fail0; handoff total6/pass6/fail0; native task011 control18 GREEN; jar exit0. Два прежних javac removal warning о System.runFinalization остаются вне scope.
- Ruling: штатный launcher перезаписывает ecology report при handoff. Отдельный ecology evidence повторно сохранён после handoff; это сохранение результатов, не stress/full suite.
- Bounded artifact exception: supplied package и required evidence/runtime helpers добавляются внутри одной TASK016 artifact family; production/test scope — ровно два разрешённых файла. Нет изменений других подсистем.
- Review: self-review по requesting-code-review, без субагентов по прямому запрету пользователя. Проверены все production call sites, native owner lookup, safeBoundary composition, CAS, отсутствие historical write и завершение worker turn. Farm/combat остаются вне scope по прямому STOP.
- Runtime helpers переиспользуют TASK014 SELECT/export/clone, private catalogs и exact-owned stock graceful shutdown. BEGIN пока не выполнялся; required manual login/logout gates сохраняются.
- Git разрешён пользователем и TASK016/GIT.md; exact commands записываются в GIT_USAGE.md. Без reset/clean/stash/rebase/force и без изменений основной копии.
- M1=OPEN. C/D pending; automatic continuation отсутствует.

- EOL correction: commit5833582b347 включил мою unintended CRLF normalization; commit19b2ff90e0b восстановил исходные смешанные endings, без semantic change или history rewrite. Final base-to-HEAD diff --check PASS; source/test final scope71/165 lines,223 insertions/13 deletions. Push ещё не выполнен на момент correction.

- Runtime completed: manual IN_GAME/REAL_LOGIN/ARMED_IDLE, one bounded BEGIN/END 49.9970473s,1423 retained,dropped0. Required READY_PASS/MATERIALIZE_CALL not reached; exact first live false guard presence.isOnline=false, profile110 seq283 READY_PRESENCE_OFFLINE. No runtime fixes/second observation.
- Exporter glob corrected after END; frozen mailbox exported without another BEGIN.
- User confirmed character-select logout. Exact saved fields and total online0 verified before/after stock graceful Game/Login shutdown. Force=false, processes/listeners0, original304 hash guard PASS.
- Final result PARTIAL_RUNTIME_MINIMUM_NOT_REACHED, M1=OPEN, no automatic continuation. RESULT.md supersedes the earlier pending ledger entries.
