# WORKFLOW / Codex execution budget and handoff

## Preparation (ZERO source modifications)
1. `pwd/git rev-parse --show-toplevel`, current branch, compare WIP GitHub HEAD with `8140167705e6a6e76f370e3aa772b50f909b4d37`. Если remote ушёл вперёд, сначала изучить diff; не слепо выполнять старые инструкции.
2. Из пользовательского checkout можно только читать новую docs-папку. Перенести байт-в-байт только этот пакет в свой независимый WIP-clone. Никаких `checkout/stash/reset/clean` в пользовательском Git-root.
3. Проверить `SHA256SUMS.txt` и `MANIFEST.json`. Читать source по SOURCE_MAP, а не переписывать весь проект на основании предполагаемых сигнатур.
4. До правок дать собственную 1-страничную source dependency map `solo vs group modes`; использовать 70=0 и 72=58 как СОХРАНЁННЫЙ native baseline, не тратить новый GameServer/production bootstrap на повтор. Новая ±4 policy не обязана сохранить число 58.

## Implementation (ОДНА цель, без деления на TASK130-A/B...)
5. Одним направлением выполнить: band policy; per-member source enumeration; logical role/leader/matcher; consistent downstream PARTY replan; native groupViable and reason codes; spoiler-compatible target/role without fabricated loot; regression on persistence and resource guards.
6. Прежде чем расширять API или создавать новый файл, искать существующий owner; если абсолютно необходим небольшой pure helper, допустим один-два, но не новый framework.
7. Сравнить все `viableTarget` call-sites, все `logicalRole`, `Member.role`, `team.size()<3`, `selectRendezvousTeam`, `groupViable`, `requestLogicalWake` и stale contracts; составить таблицу coverage и негативных тестов.
8. Сразу тестировать после каждой законченной части только MODEL/cheap SQL. После GREEN провести интеграционный test production composition (не отдельные мок-порты с другой логикой).

## Native budget — фиксировано, не «пока не заработает»
- **До доказанного cheap integration GREEN запрещены новые production JAR и GameServer**.
- Когда gate готов: разрешена **одна стандартная clean Ant JAR сборка**. Подтвердить 100% class parity и SHA.
- Затем **не более одного** GameServer bootstrap в изолированной TEST130 и только при законном preflight composition (actual NPC/Geo/skills, no unresolved DANGER). Если безопасного такого preflight нет, не делать GameServer «ради отладки»; отметить blocker.
- **Не более одного условного restart** того же JAR и DB только после первой реально полезной group vertical + stock STOP/STORE/postflight.
- При failure: штатный STOP, доказательства, NO autonomous rebuild/second native hypothesis. Отдельная санкция пользователя нужна для дополнительных тяжелых попыток.
- Между задачами/после CODEX отчета ChatGPT независимо проверяет GitHub HEAD/diff и текущий результат; не считать «MODEL PASS» прогрессом Party PvE.

## Safety and SQL
- Exact TEST130 MariaDB identity/provenance PID/path/port/schema; old config with 3308 — historical hint, NOT authorization.
- G0 before any write/bootstrap, Geo203 and protected207, no user MAIN/PLAY/TEST129; virtual timer/SQL in dedicated TEST130 only; no award post-CUT.
- Main checkout and stash must remain byte-identical. Branch only WIP commit + normal non-force push; no force/rebase.

## Deliverables
- Small scoped source+test diff, few relevant docs and one RESULT.md within THIS continuation package (in Codex clone, WIP branch).
- Report table: stage, expected change, observed proof, commit, verification, cost (build/boot/test/time), exact WIP HEAD.
- Explicit `FULL130=false` unless original A–G/this acceptance completed on same FINAL JAR with useful restart.
- Если READY, native evidence includes exact Player/Party/Monster object identities, actual damage, earned amounts, Heal MP/HP, saved group/cursor, sameDB sameJAR restart.
- Git only WIP until separately approved release gate; report blockers and new roadmap score 130–136.
