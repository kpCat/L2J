# Goal 027 — Checkpoint 2: alliance, war and relations

## Status

- Goal 027E: `ACCEPT` (frozen baseline, без изменений).
- CP2: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 027: `IN_PROGRESS_PENDING_CP2_INDEPENDENT_REVIEW`.
- Required parent: `b15f3ade1fdce99545b5bc576786d545e9c567de`.
- Branch: `feature/phantom-world`.
- `occurred_context_compaction: yes`.

## Summary

CP2 реализован поверх существующих `PhantomClanService`, `PhantomClanStore`, `L2jPhantomClanBackend` и `PhantomClanDecision`. Добавлены создание альянса, двустороннее later-pulse согласие на join, exact leave, proof-only dissolve, evidence-backed объявление войны, exact stop, двусторонний peace, relation events Goal018, restart-persistent hysteresis и alliance chat через Goal020 generated dispatch.

Новый native blocker не обнаружен. Frozen native seam Goal027C/027D/027E не изменялся. Packet/request emulation, direct native SQL/setters, global clan/profile scans, random discovery и автономная мутация REAL-only кланов не добавлены.

## Changed files

1. `build.xml` — seed и focused target CP2.
2. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — передача существующих Goal018/Goal025 сервисов в clan backend.
3. `java/org/l2jmobius/gameserver/phantoms/clan/L2jPhantomClanBackend.java` — exact native alliance/war adapters, social evidence/events, generated alliance chat и pair leases.
4. `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDecision.java` — CP2 candidate/action registrations.
5. `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java` — CP2 contracts, lifecycle, proofs, consent, replay и hysteresis.
6. `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanStore.java` — `clan.organization` schema v2 с чтением v1.
7. `test/java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanGoal027Checkpoint2Suite.java` — 8 compound scenarios.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — регистрация focused suite.
9. `docs/phantoms/reports/027-checkpoint-2-alliance-war-relations.md` — этот отчёт.

User task packages остались read-only и не входят в scope/staging.

## Goal and action keys

Goal keys:

- `clan.alliance.create`
- `clan.alliance.join`
- `clan.alliance.leave`
- `clan.alliance.dissolve`
- `clan.war.declare`
- `clan.war.stop`
- `clan.war.peace`
- `clan.alliance.chat`

Decision action keys:

- `clan.alliance.create.advance`
- `clan.alliance.join.advance`
- `clan.alliance.leave.advance`
- `clan.alliance.dissolve.advance`
- `clan.war.declare.advance`
- `clan.war.stop.advance`
- `clan.war.peace.advance`
- `clan.alliance.chat.advance`

## Architecture and exact semantics

### Consent and native identity

- Alliance join source выполняет public `checkInvite`, сохраняет в bounded in-memory offer exact `AllianceIdentity` и exact target `MembershipEpoch`; повторный source pulse не обновляет proof молча.
- Только более поздний pulse managed-Phantom target может принять exact offer. Native join получает именно captured identity+epoch; stale epoch/ABA даёт zero mutation.
- Peace аналогично требует отдельные managed-Phantom intents на обеих сторонах и более поздний target pulse. Offer содержит captured `WarIdentity`; повторный source pulse не обновляет `warId`.
- Consent ledgers очищаются при stop/restart и ограничены 64 entries каждый. Потеря offer после restart безопасна: требуется новый bilateral consent, mutation не повторяется автоматически.
- REAL-only peer отклоняется до native mutation: join, dissolve, war declare и peace требуют managed-Phantom leaders relevant sides.

### Leave and dissolve proof

- Leave выполняется только для exact наблюдаемой `AllianceIdentity`; G1 action не может покинуть G2.
- Autonomous dissolve всегда начинает с public `captureMembershipProof(identity)`.
- Causal managed clan set строится только из actor clan, explicit goal refs и persisted bounded relation refs; используются только exact lookups, без discovery/scan.
- Sorted proof clan ids сравниваются exact equality с sorted managed clan ids. Unknown или REAL-only C означает mismatch и блокировку до native dissolve.
- Единственный apply path backend — public `dissolveWithProof(actor, proof)`. Native proof CAS повторно проверяет membership epochs и generation, поэтому old proof, membership ABA и G1→G2 дают zero mutation.

### War evidence, stop and peace

- Declare разрешён только для двух managed-Phantom clan leaders, которые не состоят в одном alliance.
- Goal018 snapshot читается с bounded memory limit 8. Hostility вычисляется детерминированно из relationship/reputation dimensions; gate требует score `>= 600` и хотя бы один active concrete hostile event (`agreement.broken`, `farming.conflict.escalated`, `pvp.attack.received`, `pvp.death.suffered`). Friendly/weak/unknown evidence не вызывает native declare.
- Evidence authority включает Goal025 policy hash, Goal018 authority hash, scores и sorted bounded event proofs.
- Native declare возвращает/captures exact `warId`; active same war reconciles без duplicate declare.
- Direct stop передаёт exact current/persisted `warId`. W1 persisted state не применяется к W2.
- Bilateral peace принимает только captured `WarIdentity`; stale W1 offer не завершает W2.

### Replay, relation events and hysteresis

- Mutation intents проходят persisted `PREPARED` → `COMPLETED` state с goal id/revision, alliance generation или warId, decision epoch, evidence hash и happened minute.
- Goal018 terminal relation events используют стабильный event id, двустороннюю запись и штатную idempotent durability: join/stop/peace — `agreement.fulfilled`; leave/dissolve/declare — `agreement.broken`.
- Повтор после restart переиздаёт тот же deterministic event id и не создаёт второй social effect.
- Inverse-action hysteresis использует существующий Goal025 `pairCooldownSeconds`; deadline сохраняется в `clan.organization`, поэтому restart его не сбрасывает.
- Alliance chat проверяет exact current alliance generation и вызывает `ChatType.ALLIANCE` через `ChatObservationService.openGeneratedDispatch`. `PREPARED` после restart считается uncertain-completed и не пересылается; G1 action не отправляется в G2.

### Persistence compatibility

`clan.organization` evolved с schema v1 до v2. V2 дописывает diplomacy state после неизменённого CP1 prefix. Decoder принимает component/payload v1 и создаёт empty diplomacy state; последующий v2 save/reload сохраняет CP1 clan, role, contribution, refs и evidence поля без потерь.

DB migration и новые config keys не добавлялись. Production DB `l2jmobiush5` не использовалась и не изменялась; reprovision не выполнялся. DB-backed gates использовали существующий project test configuration/schema.

## Focused suite

`phantom-clan-checkpoint2-goal027-test`, seed `27002702`: **8/8 PASS**.

1. Alliance create/restart reconciliation, no G2 duplication.
2. Bilateral later-pulse join, exact epoch/ABA, repeated-source non-refresh, REAL-only safety.
3. Exact leave; safe proof dissolve; unexpected REAL C block; old G1 proof against G2 zero mutation.
4. Evidence-backed declare/restart; friendly, score-only and REAL-only negative controls.
5. Exact stop; W1/W2 stale fences; bilateral peace; repeated-source non-refresh.
6. Goal018 bilateral event idempotency and restart-persistent hysteresis.
7. Goal020 alliance chat G1/G2/restart semantics; v1 decode and v2 CP1 state preservation.
8. Native failure/source contract and 64/256/16 bounds.

Во время self-review focused suite запускался повторно после точечных regression additions. Один промежуточный test-only запуск завершился compile error из-за неверного имени fake counter; имя исправлено, после чего финальная последовательность начата заново с CP2. Production compile в этом промежуточном запуске был успешен.

## Final gates in required order

1. `ant phantom-clan-checkpoint2-goal027-test` — PASS, 8/8, 22 s.
2. `ant phantom-clan-alliance-membership-proof-goal027e-test` — PASS, 6/6, 21 s.
3. `ant phantom-clan-social-retirement-goal027d-test` — PASS, 6/6, 22 s.
4. `ant phantom-clan-social-domain-goal027c-test` — PASS, 6/6, 16 s. Controlled notification-failure warnings являются частью negative scenarios; suite успешен.
5. `ant phantom-clan-checkpoint1-goal027-test` — PASS: CP1 focused 6/6, profile persistence 18/18, chat observation 2/2, 16 s.
6. Exact Goal018 regression: `ant phantom-social-events-test` — PASS, 4/4, 16 s.
7. Exact Goal020 regressions:
   - `ant phantom-conversation-outbound-chat-test` — PASS, 3/3, 33 s.
   - `ant phantom-conversation-restart-idempotency-test` — PASS, 5/5, 18 s.
8. Exact Goal025 regressions:
   - `ant phantom-pvp-policy-test` — PASS, 2/2, 17 s.
   - `ant phantom-pvp-restart-test` — PASS, 1/1, 15 s.
9. Ровно один финальный `ant jar` — PASS, 14 s; штатно созданы и скопированы `GameServer.jar`/`LoginServer.jar`.

Broad Goal018/020/025 aggregates, `verify`, performance, stress и soak не запускались по task contract.

## Bounds and performance

- Сохранены 64 active operations, 256 terminal receipts и 16 relation refs.
- Join/peace ledgers bounded по active scale (64), без нового worker/thread/future.
- Lifecycle остаётся caller-driven; expensive global discovery отсутствует.
- Pair materialization leases берутся в стабильном порядке и освобождаются reverse-order через `AutoCloseable`.
- Social evidence читает максимум 8 memories; dissolve работает с proof и максимум 16 causal refs.
- Нового hot-path log spam нет.

## Deviations, limitations and risks

- `apply_patch` был недоступен из-за Windows sandbox ACL (`apply deny-read ACLs`); согласно локальному Windows contract изменения внесены bounded exact-anchor PowerShell replacements/incremental UTF-8 writes. Большие source files целиком через shell не заменялись.
- Consent offers намеренно не persistent: restart требует повторного двустороннего later-pulse consent; это fail-closed и не создаёт mutation replay.
- Alliance chat `PREPARED` recovery suppresses resend при неопределённости, поэтому после crash возможна потеря сообщения, но не duplicate dispatch.
- Независимый CP2 review ещё не выполнен; Goal027 overall не помечен ACCEPT.

## Static and encoding checks

- Strict UTF-8 decode всех 9 changed files: PASS, 0 failures.
- Mojibake-маркеры в изменённых файлах проверены: PASS, 0 files.
- Escaped Cyrillic в изменённых файлах проверены: PASS, 0 files.
- `git -c core.whitespace=cr-at-eol diff --check`: PASS перед staging; cached check выполняется после exact staging.
- Source-contract assertions в CP2 suite подтвердили отсутствие global clan discovery, packet/request war emulation, direct `java.sql` и ordinary alliance dissolve path; `dissolveWithProof` и Goal020 generated dispatch присутствуют.
- Frozen Goal027C/027D/027E production files отсутствуют в changed-file inventory.

## Git/process

Git-команды использовались, поскольку task package и `Agents.md` прямо требуют parent/branch/scope/diff/commit/push контроль. Выполнялись только разрешённые read-only inspections и delivery operations:

- `git status --short --branch`, `git status --short`
- `git rev-parse HEAD`, `git branch --show-current`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git diff --stat`, `git diff --name-only`, bounded `git diff -- <exact paths>`
- `git -c core.whitespace=cr-at-eol diff --check`
- после отчёта: exact-path `git add`, `git diff --cached --name-only`, `git diff --cached --stat`, `git diff --cached --check`, `git commit`, `git push`, `git rev-parse HEAD` и `git rev-parse HEAD^`.

Commit subject: `feat(phantoms): add alliance and war diplomacy`.

- Commit SHA: создаётся тем же atomic commit; exact SHA возвращается в финальном сообщении.
- Push result: фиксируется в финальном сообщении после `git push origin feature/phantom-world`.

## Next step

Независимый review CP2. До его результата Goal027 остаётся `IN_PROGRESS_PENDING_CP2_INDEPENDENT_REVIEW`; следующий Goal/Slice не начинается.