# RESULT044 — PARTIAL_VERIFIED, G4 OPEN

TASK044 не прошёл. M1=OPEN, READY_FOR_M2=false, USER_ACCEPTANCE=REQUIRED.
М2 и другие gates не запускались. Owner key/seq/expiry D1 не установлен;
полный G4 не запускался, causal gameplay fix отсутствует.

## Полученный результат

- Offline C1: actual absent77.229855с,77 successive frames; scheduler SLEEPING,
  ecology_fenced,nextDue0. Обычной BACKGROUND due в window не было. Штатная
  BACKGROUND first work откладывается на300000..900000мс от transition;
  отдельная ecology catch-up очередь не гарантирует producer за77с.
- D1 raw043 не содержит source key/sequence/expiry/flight events. Count1 не
  даёт owner. Добавлен ровно один read-only one-profile Scheduler view под
  existing monitor, immutable records/copy, signed remaining/expiry/overdue.
  API не expires/withdraws/mutates состояние; gameplay policy не менялась.
- BASE compile-contract RED7 missing API references → GREEN28/28 и final
  jar-first28/28. Existing TEST044a ownership guard RED до DB init → GREEN3/3.
  Final mandatory matrix93/93, lifecycle acceptance11/11, soft-return3/3 PASS;
  strict conservation validator existing negatives26/26 PASS.
- Единственный ordinary044a diagnostic: Game20640/Login25468, fresh immutable036
  input, один import. Preflight failed до MOVE: no living current-ready profile.
  Source snapshot на settled-away не получен. Same-boot passive fallback был
  отклонён runtime identity guard до чтения; after-STOPPED не выдавался за away.
  Нового boot/attempt/full G4 не было.
- Исправлен только private shutdown tool: старая043 DB mask отклоняла044a.
  Exact044a/new agent class, остальные guards сохранены; repaired stock stop
  обеих JVM PASS. Own JVM0, stopped DB/raw сохранены, counts pendingOwnedStores0 /
  onlineCharacters0. Protected040a и historical040/041/042/043/MAIN/PLAY не изменялись.

## Строгий conservation результат

75 births все terminal;35702 actual owning commits;82 native committed witnesses
PASS и44 stopped canonical state/PNC/FULL items/skills/goal profiles PASS.
RUN_END closed/exporter_drained/dropped0, но failure=TASK032_STOP_MONITOR_TIMEOUT
от первого отказавшего stop. Repaired typed DONE/COMPLETE/retained0 и pools still
open подтверждены; original failure не переписан. Existing strict validator:
FULL_SAVE=false,GLOBAL_DRAIN=false,FINAL_STOPPED_CUT=true,
failures=[EXPORTER_GAP_OR_RUN_NOT_ENDED]. A5 conservation NOT_VERIFIED.

## Candidate и historical gates

BASE044=df295c79e0aa36428e88933352aeff629816ee83.
Source commit80e7bdd346c24faf2153dcf04f5d0adf7a7e06ae.
GameServer.jar=E5029F38D0C3CD3A338FDA420FD8A31A35AA1D7D3BC519A7B5A73A40EBC88D47.
JAR менялся только из-за read-only API; это всё равно другой exact candidate.
G2 A5=8/8/382.3743138с и C24=8/8/381.6816272с сохраняются historical PASS
на5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c.
G2_CURRENT=NOT_VERIFIED: две группы на новом JAR не выполнялись. Evaluator028
SHA15c153d30fe7f85719b1698059f4e28c9c7fa325caa420a02de7ac19cff35112 неизменён.
45/90/120,Synthetic525/cap5,population/caps/rates/TTL/earned/debt/CAS не менялись.

## Доставка и ограничения

Три source/test paths и один task044 artifact family; bounded exception >10 paths
заранее записан. SOURCE_DIFF044.patch проверен against BASE→source commit.
Compact review содержит code diff, input/result/matrix, bounded proof indices и
необходимые helper source. Private raw/DB/JAR/credentials не staged и не включены.
Exact-path ordinary commits и normal non-force push разрешены user /goal и
TASK/HANDOFF044; actual publication SHA/HEAD=remote/clean записываются в receipt
после push. Они не превращают A1/A4/A5 в PASS. Матрица: GATES044.tsv.

First actual publication PASS:7d98a63515b7bcc84a15ad5fda599a5874181560=remote,
tracked clean. Compact literal argv/exit receipt:evidence/PUBLICATION1_RECEIPT044.json.
Final report/ZIP update имеет отдельный обычный commit/push и local post-push
receipt; self-reference на будущий SHA не выдумывается.

Ошибки оркестрации сохранены: неверное имя lifecycle suite, преждевременный
запуск под DB lock, CLI hashtable argument, copied shutdown043 mask, missing
SplitSql helper (partition завершён из тех же original SQL bytes без нового
DB query), wrong marker passive fallback. Это ограничения исполнения044;
product defects им не приписаны. Результаты после исправления указаны отдельно.
First publisher guard также дал ложный remote-advanced отказ: WinPS single stdout
стал scalar, индекс0 прочитал только первый символ. Actual remote оставался BASE,
writes не было; explicit arrays → ordinary commit/push GREEN, исходный refusal сохранён.

mojibake-маркеры в изменённых файлах проверены — итог evidence/ENCODING044.json.
escaped Cyrillic в изменённых файлах проверены — отдельный итог там же.

Остаток: разрешённый следующий source-only native capture должен получить
actual accepted key/sequence/TTL после settled-away. До этого новый gameplay
patch/full G4 нельзя обосновать. M1 G1/G3/G4/G5/G6/G7/G8/R1/O_REAL/ручная
приёмка не закрыты; historical failures сохраняются.

Continuation: diagnostic preflight исправлен offline на actual68. Source-only
selection допускает уже earned native identity без current BG cursor, full G4
сохраняет исходную readiness проверку. Focused RED→GREEN/negative и Java/WinPS
operator compile/parse PASS. Нового boot/attach не было: one-trace limit исчерпан,
расширение scope запрошено и ожидает ответа. A1/G4 не повышены; product/JAR прежние.

## Итог разрешённого дополнительного trace

Два прямых ответа пользователя разрешили один additional source-only trace и
новое окно до30мин. Approval/input receipts отдельные immutable044; исходные
DIAGNOSTIC_INPUT044/failure не изменены. Start044a12:22:38 UTC, Game36500/Login30912;
оба штатно stopped, own JVM0, protected040a18688/30312 неизменны.

Snapshot PASS:55 one-profile кадров73, только законный human.local/NEARBY_PERCEPTIBLE,
TTL10000, seq135→186 с actual expiry. Ни одного ACTIVE source; historical1189
owner UNKNOWN. Native result INPUT_FAIL_SAFE_BOUNDARY_BEFORE_MOVE: quiet не появился
в45с, первая quiet запись +51.4393965с; actor replacement/MOVE/away не выполнялись.
Таким образом source API live проверен, causal RED и полный G4 не получены.
Source/gameplay/JAR прежние; новый product fix без RED не внесён.

Дополнительный run strict conservation PASS:40 lifetimes terminal/4182 owning commits,
48 native witnesses/25 stopped canonical profiles; FULL_SAVE/GLOBAL_DRAIN true,
dropped0/failure empty/pendingOwnedStores0/onlineCharacters0. Первый strict FAIL
сохранён, campaign A5_CONSERVATION NOT_VERIFIED.304 original036 files, pinned043
и MAIN input bytes повторно проверены unchanged. G2_CURRENT NOT_VERIFIED;
historical A5/C24 PASS сохранены. Матрица GATES044.tsv содержит отдельные строки
live snapshot/дополнительного drain, не повышая historical owner/G4/M1 gates.

Текущий результат PARTIAL_VERIFIED: M1 OPEN, READY_FOR_M2=false, manual REQUIRED.
Дополнительные native попытки и М2 не начаты. Компактные exact receipts содержат
пины raw; raw/DB/JAR не опубликованы. Новый обычный commit/push закрывает только
разрешённый trace и task044 artifacts; post-push SHA/clean в отдельном local receipt.
