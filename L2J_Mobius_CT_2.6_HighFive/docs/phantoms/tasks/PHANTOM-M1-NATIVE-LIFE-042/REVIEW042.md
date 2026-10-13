# REVIEW042 — exact scoped review, manual gate REQUIRED

Итог: G2 PASS; G4 FAIL; P4 NOT_VERIFIED; TASK042 PARTIAL_VERIFIED. Product edits отсутствуют. M1 OPEN, READY_FOR_M2=false. Этот self-review не заменяет ручную приёмку.

## Изменения и локальные аналоги

Единственный существующий исходник: test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java. По ближайшему041 owned TEST pattern добавлены phantom.contract042.manifest, exact contract042b/database042b и mutual-exclusive owner assert; сохранены realpath, exportSHA, config, pool4, connected-JDBC guards. RED TESTLANE_RED042 отверг unsupported cwd до DB init; compile-tests и TESTLANE_GREEN0423/3 PASS; WrongManifest и MixedOwners каждый отвергнут до DB init. Production java/config/data/JAR не изменены.

Новый TASK042 artifact family: входные восемь packet docs; четыре итоговых отчёта; scoped reusable042 helpers; bounded evidence и ZIP. Exception >10 paths зафиксирован до изменений и ограничен этим семейством плюс одним TEST owner. Точный staging — REVIEW_ALLOWLIST042.txt; запрещены prefix-wide add и включение private runtime/secrets/JAR/DB/full raw.

Normal041 Control/Observe/Prepare/stock shutdown переиспользованы с exact042a/b namespaces и normal-only guards. Historical crash/recovery template branches недостижимы из-за upfront reject; guards сохранены. Observer ring512 и исходные caps не повышались. Остановка — native stock ShutdownSystem на exact PID/start/runtime; process termination не применялся.

A1 atomic replace AccessDenied: диагностический reader мог удерживать handle без FileShare.Delete. Изменён только live read path Read-Frame042.ps1; publisher и game не менялись. FRAME_SHARING042_V2 fixture воспроизвёл RED с non-delete handle, GREEN с ReadWrite|Delete и negative сохранения исходных bytes. Это observation repair; A1 не ретроспективный PASS.

B1 Windows PowerShell scalar Count: RED actual B1 primary68; outer-array вокруг обеих if expressions в Observe032.ps1; Select-LifePrimary042 empty-input negative и GREEN exactly one. Run-Life042 finally загружает LocalPlay-Pilot перед Ownership helper. B2 actual native earned/departure подтверждает исправление runner; последующий combat FAIL сохранён.

Full-save039 validator staged без изменения контрактов. Complete-chain walks всех исходных linked edges pages4096, без ограничения только последними receipts; closed archive SHA/CRC и full SQL items/skills/PNC. Negative26/26. Позднее SQL не используется для выдуманного A1/A2 global PASS.

## Фактические проверки

Команды из OWN. T=полный путь docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042; O=полный путь .phantom-local/ops042. Exit=0, если не указано иначе. Classpath frozen GameServer.jar first, test classes second. Jar SHA256=5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c.

- powershell.exe -NoProfile -File T/Verify042.ps1 -Action Build: existing Ant compile-tests, BUILD SUCCESSFUL.
- powershell.exe -NoProfile -File T/Verify042.ps1 -Action Matrix -Label CURRENT93_042:18 routes,93/93.
- powershell.exe -NoProfile -File T/Verify042.ps1 -Action Test -Suite PhantomLifecycleCompletion027Suite -Focus acceptance -Label LIFECYCLE11_RUN042:11/11.
- powershell.exe -NoProfile -File T/Verify042.ps1 -Action Test -Suite PhantomSoftReturn028Suite -Label SOFT_RETURN042:3/3.
- powershell.exe -NoProfile -File T/Verify042.ps1 -Action Test -Suite PhantomVisibleContinuity032Suite -Focus continuity037 -Label DEBT90_RUN042:2/2.
- powershell.exe -NoProfile -File T/Verify042.ps1 -Action Test -Suite PhantomActionLoop038Suite -Focus full038 -Label ACTION_LOOP042:5/5.
- DEBT_NEGATIVE042 used unsupported focus debt037 and actually ran default visible-continuity8/8; он не засчитан как debt90. Actual debt proof — DEBT90_RUN042.
- python -B -m unittest discover -s O/validation -p test_*.py -v:26/26.
- powershell.exe -NoProfile -File T/Check-LaneGuards042.ps1 -Case WrongManifest и отдельный -Case MixedOwners: два authority negatives PASS.
- powershell.exe -NoProfile -File T/Check-LifeInputs042.ps1: scalar RED, single GREEN, empty negative, stock cleanup dependency PASS.
- powershell.exe -NoProfile -File T/Check-FrameSharing042.ps1: FRAME_SHARING042_V2 RED/GREEN/negative PASS.
- powershell.exe -NoProfile -File T/Check-FinalOwnership042.ps1: own JVM0, protected exact incarnations, source304 hashes each PASS.
- Original evaluator028 на A5: denominator8, pass=true,382.3743138s. C24 reuse separately SHA-pinned; semantic-code diff empty.

Ошибочные diagnostic commands/labels сохранены в local evidence: fully-qualified suite lookup failure, unsupported focus default result, concurrent lane lock reject до Java. Они не засчитаны как требуемый положительный outcome. A3/A4 input rejects, A1 capture failure, B1 runner failure и B2 domain failure представлены в CHECKPOINT.

## Git и доставка

Git прямо разрешён GOAL/TASK/HANDOFF042 и пользователем. Только OWN read audit (status, rev-parse HEAD, ls-remote, exact diff/name-only/cached, exact show population XML) и exact add/ordinary commit/non-force push. Никаких reset/force/clean/restore/merge/rebase/branch/worktree mutations. Exact publication argv — GIT_COMMANDS042.json и DELIVERY042.json; prep argv сохранены в scoped GIT042.jsonl.

До первого commit повторно HEAD=remote=BASE. Первый ordinary commit4006ccc80d4b8bf58d9aa757ffb8f125b88077da опубликован22:45:42 UTC, local=remote/tracked clean подтверждены в PUBLICATION1_042.json. P6=PASS по actual publication/verified compact ZIP/own JVM0. Второй ordinary delivery commit сохраняет финальные отчёты, receipts и повторно проверенный ZIP. Final SHA не встраивается в собственный commit из-за hash cycle; он записывается в DELIVERY042.json после проверки final remote. Ручной gate REQUIRED.

Первый staging не дошёл до commit: default whitespace check посчитал CRLF в локальном Java trailing whitespace, literal unified-diff context — space-before-tab, и inherited blank EOF frozen stock helper. Исходный HEAD остался BASE. Финальный check распознаёт CRLF без изменения repo config; только два точных artifact exceptions — SOURCE_DIFF042.patch и byte-pinned stock Stop-ExactOwnedGracefully.ps1. Сами оба файла просмотрены и SHA-проверены; остальные staged пути проверяются. Resume допускает только ранее подготовленный exact allowlist, без сброса index.

ZIP ограничен allowlist: reports/source/typed stop proofs/bounded metadata. Big raw proofs не перепакованы; paths/SHA доступны локально. Verify проверяет actual ZIP duplicate/path traversal/CRC/member SHA и общий SHA. Manual gate REQUIRED независимо от доставки.

Обновление draft ZIP до первого push было отклонено guard отсутствующего publication receipt; shell продолжил отдельно разрешённый ordinary push первоначального CRC/SHA-verified draft. Final builder допускает только точный SHA собственного draft, сохраняет его backup и перед delivery commit проверяет каждый final member против фактического OWN файла. Исходный draft и финальный ZIP различаются только TASK042 review artifacts; game/evaluator/native outcomes не менялись.

mojibake-маркеры в изменённых файлах проверены: PASS, exact allowlist.
escaped Cyrillic в изменённых файлах проверены: PASS, отдельная regex-проверка exact allowlist.

Автоматическая проверка отклонила удаление старого guard блока как возможное ослабление запрета crash/recovery. Удаление не выполнено; оба guards сохранены. Допустимая отдельная правка исправила только ложный старый комментарий об авторизации042 crash. Необходимые normal операции этим не заблокированы.
