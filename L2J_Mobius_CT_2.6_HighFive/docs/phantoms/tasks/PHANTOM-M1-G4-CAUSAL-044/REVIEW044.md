# REVIEW044 — ограниченный self-review, PARTIAL_VERIFIED

Использован requesting-code-review checklist; subagent запрещён user/package,
поэтому exact source/artifact diff проверен самостоятельно. BASE044→source80e7bdd
изменяет только Scheduler и две existing TEST files,93 insertions/4 deletions.
GameJar E5029F38; original G2 не переносится на новый exact JAR.

## Source contract

signalSources(profileId) читает existing Slot/source map под тем же _monitor,
что submit/withdraw/expire/find. Clock sampled once; immutable record содержит
accepted key/sequence/state/original TTL/sample/expiry/signed remaining/overdue.
Null tombstones не появляются; overdue non-null entries не скрываются до pulse.
Sorted List.copyOf — existing find/list pattern, at most16 sources. Нет mutation,
timer/thread/SQL/I/O/epoch/earned/native control или новой архитектуры.
Focused tests проверили immutable old copy, two owners, replacement, withdraw,
unknown profile, unchanged scheduler/metrics и overdue read→ordinary pulse expiry.
BASE compile-contract RED и final jar-first28/28 сохранены. Existing legitimate
hard/recent-human/stale-epoch/lifecycle tests не ослаблены;93/11/3 PASS.

TEST044a extension следует точным041–043 guard branches: mutually exclusive
manifest/root/runtime/catalog/export SHA/pool4 перед DatabaseFactory init.
Old owners unchanged. Actual044a cwd RED → GREEN3/3, mandatory93/11 PASS.
Новый gameplay producer/withdraw fix отсутствует: causal owner RED не установлен.

## Обнаруженные проблемы исполнения

Important: reused diagnostic selection требовала текущую ecology readiness,
хотя source-only view не требует BG eligibility. Actual preflight RED до MOVE;
one-profile collector не reached. Не объявлять getter runtime-tested/owner-proven.
В следующем разрешённом task source-only capture должен отделить этот preflight
от полного G4. В044 второй boot/scene не выполнялся и failed actor не заменён.

Private stock tool copied043 DB mask: actual agent refused correct044a catalog.
Исправлен exact044a regex и agent class identity (loaded class нельзя считать
заменённым только перепаковкой JAR). PID/runtime/role/root/catalog guards и stock
Shutdown сохранились; actual обе JVM stock stopped. No force fallback.
Original timed-out monitor failure от rejected request остаётся в RUN_END;
strict conservation FAIL не ослаблялся ради PASS. Positive82 native/44 stopped
subproofs отделены от не прошедшего outer FULL_SAVE/GLOBAL_DRAIN gate.

Other corrected invocation faults: CLI hashtable, lifecycle wrong class/locked
premature call, missing SQL partition helper. Source-only passive fallback был
отказан неправильным runtime argument до чтения; after-STOPPED не является
original D1 RUNNING-away measurement. Failed helper call не дал owner evidence.
WinPS log BOM UTF-16 распознан reader без изменения raw. Эти ошибки не являются
product causal RED и не стирают historical042/043 failures.
Publisher scalar stdout guard отказал до writes при неизменном BASE remote;
explicit array wrapping и native stderr handling исправлены, actual guarded
ordinary push7d98a63515b7bcc84a15ad5fda599a5874181560 PASS/tracked clean.

## Итог и поставка

API/test change безопасен по unit/contract/affected regressions; native integration
и owner attribution NOT_VERIFIED. TASK044/G4 не прошёл. A2 offline proof полезен:
C1 SLEEPING/nextDue0 во всех77 кадрах; label BACKGROUND_ABSENT не заменяет actual
eligible state. Никакого producer guarantee за77с без real due/commit.

Compact ZIP — task packet/results/source diff/indices/bounded helper source,
без native/SQL raw/DB/JAR/credentials. Allowlist exact; hash/CRC/all-member bytes,
duplicates/path traversal и full staged diff checked before ordinary push.
Private ops source — forensic proposals, не готовая ordinary M1 distribution;
native runner input failure явно сохранён. R1/полный G8 остаются NOT_VERIFIED.

Git разрешён /goal/TASK18/HANDOFF044. Literal direct/embedded command inventory
в GIT_COMMANDS044.md и evidence/HELPER_GIT_COMMANDS044.json; actual receipts после
ordinary commits/non-force pushes. No branch/reset/restore/clean/merge/rebase/amend/force.
CRLF сохранён; whitespace guard применяет cr-at-eol без записи Git config.

mojibake-маркеры в изменённых файлах проверены — evidence/ENCODING044.json.
escaped Cyrillic в изменённых файлах проверены — отдельная проверка в том же файле.

M1 OPEN, READY_FOR_M2=false, USER_ACCEPTANCE REQUIRED. Следующий scope не начат.
