# PHANTOM-LEARNING-002 — финальная передача

**READY_FOR_MANUAL_ACCEPTANCE — ТЕХНИЧЕСКИ ГОТОВО. Manual gate REQUIRED.**
Агент не отмечает ручную приёмку passed. Literal Codex UI close **NOT_RUN**.
Runtime завершён; дальнейшие source changes не планируются.

Final SQL/status14:31:40Z:34 COMMITTED / 35 INCOMPLETE / 117 unique117,
attempts2/accepted1,nextOrdinal69,incomplete credited0. SOLO 18/54,PARTY 16/63.
Cold model 1 SHA `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`;
final policy1 SQL SOLO 6events/12406HP иPARTY 9/14631HP.
DRAFT+ACCEPTED BLOB12500 bytes/algorithm 2 и исходные SHA сохранены.
Последний новый Solo ordinal68:keyefcfd66647273091bc5430e055134e2d924caf283ac58502c05332e1f819ef41,
skill1339,1503HP,policy1.

Generation8 PAUSED 14:26:59Z с NativeExited=true; RESUME 0/samegeneration ACTORS.
Первый final STOP 0 14:29:29.4312167Z,noncefa2435a292e340498350140697c551a7.
Stopped cold REPORT 0/Test-Export 0 сохранили117/model 1; Isolation203/2070.
Idempotent STOP 0 **14:31:52.8466956Z**,noncee4272a169e844b98855888a7953ad2be:
own director/native/worker/SQL отсутствуют,порты33327/17971/19271 свободны.
Не использовать исторический PID из старых snapshot для управления процессом.

Startup timeout/orphan P2 закрыт bounded ACK+durable pending STOP; actual handshake,
double START/sameOwner и независимое final review no P1/P2.
Default episodesPerBatch=6 восстановлен в source/root config.
Final Java 277/277 GREEN,F7 standalone PASS,SQL_REPLAY NOT_RUN.
Новых промежуточных approval gates или следующих Goal/Slice автоматически не добавлять.

## Передача пользователю и публикация

1. Exact scope68 закреплён в SCOPE_ALLOWLIST.txt и SCOPE_GUARD.json; обе отдельные
   encoding-проверки пройдены. Публичные snapshots — artifacts/evidence/PROOF.json.
2. Фактические Git-команды приведены в отчёте; точный GitHub HEAD после обычного push
   только в wip/phantom-learning-002 и независимого readback сообщается при доставке.
3. Пользователь принимает либо отклоняет единственный итоговый manual gate.
   Canonical model ZIP, русский runbook и отчёт готовы; установки в MAIN нет.

Canonical public artifact:
`L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip`,
69127 bytes,SHA `10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da`.
Источник final cold export:
`R/exports/model-1-0f302f513b1a4fc785d74ba54ce23a01.zip`.
README_RU.md рядом описывает weights/manifest/dataset/episodes,ACCEPTED_TEST/NATIVE/
productionPromotion=false. Weights SHA не изменился. Старый66089-byte export сохранён
private/внешним backup,но public canonical уже обновлён; старый SHA не использовать для нового ZIP.

## Ключевые evidence

Все R paths ниже от clone root: **R = .phantom-local/learning002/train/**.

- R/status.json и final-sql-readback.tsv —117/117,policy1 counts,оба BLOB,credited0.
- R/unattended-proof-generation7-utc.json — главный D1 runtime proof:
  1015715ms,caller absent,no controls,>=2 новых COMMITTED. Неверный original elapsed
  не использовать; corrected file связывает provenance SHA/literal UTC/DateTimeOffset.
- R/commands/stop-confirmed.fa2435a292e340498350140697c551a7.json.
- R/commands/stop-confirmed.e4272a169e844b98855888a7953ad2be.json.
- R/accepted-model-1-readback.json и workers/54a4808248a249cb88035b87064528d6/.
- R/attempts/965dc6d440274cd3965ab7455043bf46/{native.out,native.err,native-resources.json}.
- R/crash-probe.14b2d4887e3b4d92b3591378a6a4533a.json/.after.json и парный crash-recovery:
  actual native crash EFFECT→COMMIT,старый ACTIVE→INCOMPLETE,no duplicate credit.
- R/sql/sql-resources.134388731218807766.json — elapsed4516914ms/CPU16.4375s/
  peak154066944bytes,workingSetnull; pooled native/system peak UNKNOWN.
- %TEMP%/Training002JobLifetime-ecec9e8152d7458888cdfc9a757647a0/own/lifetime.before.json
  и lifetime.after.json — modeled caller kill-job closure,9 heartbeat/normal exit 5,
  literalUiCloseObserved=false.
- %TEMP%/Training002Startup-172ef47195914f82b63a8841b10782c6/ — startup handshake regression.

Квалификация D1 остаётся явной: technical modeled kill-job + fresh WMI native parent-exit
proof,а не наблюдавшееся закрытие Codex UI. Буквальный UI close NOT_RUN.
Final performance — actual per-attempt данные; n=1 не выдаётся за tail-load статистику.

Narrow learner scope:class 94,levels77/78, A/S, NPC 21798/21799,2offensive skills.
128 native visible и обучаемые PvP/PK/Raid/Siege/TW/Olympiad/Summon/Heal/Control/
Spoil/Sweep/Move NOT_IMPLEMENTED; stock Heal не trained healer-policy,small audit не DPS proof.
Operator runbook:
`L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/RUNBOOK_RU.md`.
Полный отчёт:
`L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-002.md`.

Base `b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8`,WIP `wip/phantom-learning-002`.
Original checkout/исторические установки и входной пакет из 13 файлов сохранены.
Технические результаты и ограничения зафиксированы; следующий Goal/Slice до приёмки не начинается.

- mojibake-маркеры в изменённых файлах проверены; совпадений нет.
- escaped Cyrillic в изменённых файлах проверены; совпадений нет.
