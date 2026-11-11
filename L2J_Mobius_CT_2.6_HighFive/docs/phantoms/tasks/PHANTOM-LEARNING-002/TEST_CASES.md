# Минимальный проверочный набор

1. `T01_IDENTITY`: первый запуск из чистого независимого clone. Изолированная MariaDB и аттестованная реальная Geo203; listener только 127.0.0.1. No user database writes.
2. `T02_START_ALREADY`: двойной `TRAIN_START` не создаёт два директора и не поднимает две БД.
3. `T03_DETACH`: START вернулся, исходный PowerShell завершён, директор с новым PID продолжил >=2 реальных эпизода и увеличил счетчик native events; повторный `STATUS` в другом терминале.
4. `T04_SCENE`: генератор с seed A на одинаковых игровых данных воспроизводит очередь сцен; seed B создаёт отличающуюся допустимую очередь; недоступные classes/skills/NPC/Geo логируются и пропускаются.
5. `T05_SOLO_NATIVE`: действительный skill cast и точная атрибуция HP на независимом Solo actor; неизменные stock EXP/SP/loot semantics; NET no listener external.
6. `T06_PARTY_NATIVE`: реальное stock invite/accept, live target selection, Party Combat+Heal if available, terminal/STORE; correct per-member attribution, no Party total HP assigned individual; no new Party false claims.
7. `T07_NO_FEASIBLE`: невозможная сцена → bounded skip и выбор следующей, без бесконечного ожидания и без «отключили safety».
8. `T08_REPLAY`: restart/crash между `EFFECT` и `COMMIT` не даёт двойного training event, не кредитует незаконное/UNKNOWN действие; SQL unique hash; old run id cannot modify new scope.
9. `T09_AUTOTRAIN`: native threshold → run offline trainer → versioned DRAFT + independent audit vs constant/prior baseline; accept or decline with exact reason; no audit leak; no overwrite of accepted historical checkpoint.
10. `T10_COLD_ON`: if accepted TRAIN-only candidate, cold GameServer reload exact SHA and legal action, independent readback. If not accepted, state remains safe and plan continues (cannot fabricate performance).
11. `T11_PAUSE_RESUME`: PAUSE drains admitted cast and does not start new episodes; RESUME continues counters/campaign from durable cursor without data loss.
12. `T12_STOP`: STOP owns exact PID/FILETIME, clean GameServer/SQL JVM shutdown, ports closed, no чужой process touched, dataset export available.
13. `T13_CLI`: status Russian understandable, progress and blockers count, config invalid explained, export without model fails transparently.
14. `T14_CRASH`: controlled own process crash/read-only SQL failure, recovery via owner identity; foreign port and unknown PID must fail-closed.
15. `T15_REGRESSION`: Learning001 targeted, Combat ownership, Party tactics, world-coordination130 model, PvP, Support, commerce, spoil, F7/F8, protected207 and Geo203, no sync SQL/ML thread in critical Combat path.
16. `T16_PERF`: actual actor feature build + 2..4 legal candidates, p50/95/99, worker pool CPU/RAM and DB throughput under bounded campaign; prefilled-vector benchmark alone insufficient.
17. `T17_VISIBILITY`: if owner client absent, no free teleport/reward for training actors; ordinary state, location and receipt semantics preserved. All out-of-scope modes labelled NOT_IMPLEMENTED.

Директор может использовать прошлые тесты 001 только как read-only исходные инструменты, но не считать их результат свежей независимой тренировки 002. Инфраструктурный failure объяснить конкретной причиной и исправить в своих рамках, не тратить ночь на бесконечные одинаковые attempts.
