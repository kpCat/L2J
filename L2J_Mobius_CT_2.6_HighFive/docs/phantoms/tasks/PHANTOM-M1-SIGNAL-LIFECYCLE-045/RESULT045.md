# RESULT045 — causal fix verified; native input blocked

TASK045=BLOCKED_NATIVE_INPUT, не PASS_SCOPED. M1=OPEN, READY_FOR_M2=false,
USER_ACCEPTANCE=REQUIRED. Причинный дефект исправлен; полный G4 не доказан.
Новых native attempts, М2 и следующего task не выполнялось.

## Причина и минимальная правка

BASE a6fde1df1b1785b93e2248a20786c61878e05428 совпал с remote; OWN detached HEAD
сохранён. Сначала реальные Population.onPulse и HumanLocality.onPulse доставили
сигналы в настоящий Scheduler. После ухода/expiry human.local и снятия всех
retention reasons календарный population.schedule продолжал требовать ACTIVE.
BASE RED: старые28 PASS, SLEEPING-negative PASS, новый causal release test FAIL.

Один product owner: PhantomPopulationManager.processReadySchedule проецирует
admitted календарный ACTIVE в физическое BACKGROUND требование. Логический
entry effective ACTIVE, calendar online, farm eligibility и admission cap сохранены.
Non-admitted WARM, SLEEPING withdrawal, TTL/sequence и cleanup/bootstrap прежние.
Locality/topology продолжают независимо требовать physical detail; native retention,
REAL_PARTY, earned actions/incoming combat, epoch/CAS/owned protocol не менялись.
GREEN30/30 включает release, short recent-human hold, повторную locality,
stale sequence, non-admitted cap и sleeping/no-work. Исторический1189 owner UNKNOWN:
детерминированный дефект этого producer не восстанавливает отсутствующие043 bytes.

TEST изменения: два scheduler tests; exact045a manifest/root/CWD/exportSHA/owner/DB
guard по прежнему локальному паттерну; nearest PopulationSuite fixture использует
existing HumanLocalityControl в local control port. Все прежние native assertions
сохранены, включая cold restore/sleep/store/retirement. Fixture regression1/1 PASS
в development bin. Это не переносится в M1 G5.

## Единственный ordinary native045a

Из immutable036 export SHA8b85b1ac34a5a3dbfd42e274daca0d42798fcda40688b102786493237141a2d6
создана одна новая045a schema, один import до boot. Target1280/active8/cap8,
рейты, UTC calendar, радиусы, policy300–900sec, recent-human60 и Synthetic525 прежние.
Ports7797/2127/9037 были свободны; существующая stock geodata дала bidirectional
dry PASS61 points/20 steps. Actor не boost/heal/teleport после enrollment.

Candidate source0a07452b40d246459954ed98fb8a76f1fa7e463f,
Game JAR fc19dc90786947eff5ad427792ac4f3a491340c0e647feb194ecb088f29adec2.
Game8292/Login28476 реально booted. Все8 baseline profiles10008/10009/10011/10015/
10024/10031/10037/10040 живые, World-present, NPC18342 и имеют настоящие native
cycle/reward/damage counters. Но среди967 census profiles admitted=0;
в baseline все8 admitted=false/current readiness=false, cursor существенно отстаёт
от requested horizon. Непройденная граница — actual current calendar/ecology
admission/readiness, до earned-before-departure/quiet/MOVE. Это input FAIL,
не новый доказанный product bug. См. evidence/NATIVE_INPUT045.json.

MOVE/away/background/return не выполнялись. Другой actor, DB или выигрышный cohort
не выбирался. Измерение due/long BG/new return session было подготовлено и статически
проверено, но не достигнуто; эти branches не объявляются live-tested.
G4=NOT_VERIFIED. Elapsed native preparation, future forecast и флаги не PASS.

Synthetic STOPPED, оба own servers остановлены stock Shutdown, без force/crash.
Strict own conservation PASS:54 lifetimes terminal,485 owning commits,
62 native witnesses,46 stopped canonical profiles; FULL_SAVE/GLOBAL_DRAIN/
NATIVE_BOUNDARY_INTERVAL/FINAL_STOPPED_CUT true; dropped0/failure empty,
pendingOwnedStores0/onlineCharacters0. Это только own045 cut, не закрытие всего M1 G3.
Исходные failures и все own DB/raw сохранены локально по SHA pins.

## Проверки и сохранность

Итоговые exact JAR результаты и cardinality: evidence/REGRESSIONS_FINAL045.json.
Required routes93/93, acceptance11/11, soft3/3, scheduler30/30, affected population
schedule3/catalog3, topology integration5/ledger20 и strict validator26 negatives
PASS, проверены отдельными командами на текущем JAR.
G2_HISTORICAL остаётся PASS только на5c4ae8bf JAR; G2_CURRENT=NOT_VERIFIED,
две current группы не выполнялись. Original CONTINUITY_V2 SHA15c153d3... неизменён.

Protected040a Game30312/Login18688 сохранены по actual incarnation/runtime/JAR.
304 исходных036 файлов,8 MAIN task inputs и12 protected product owners unchanged.
Старые DB/raw/041x72/x73/MAIN/PLAY/client/MariaDB service не менялись.
Post-regression native row preservation и final JVM census имеют отдельный receipt.
45/90/120,60/525, population/radius/caps/clocks/rates и owned/earned/CAS не изменены.

Ошибки исполнения сохранены: Java -D quoting до causal RED; single control port
заменён locality в первом fixture варианте — исправлен существующим local port;
mixed EOL byte splice сохранил исходные строки; exact add array/patch context
whitespace; javac NOTE с exit0 вызвал WinPS exception до native boot — исправлен
only045 wrapper. После actual input FAIL native не повторялся.

По ошибке выбран дополнительный общий native24 route вместо заданной lifecycle
acceptance11 family. Результат19/24, exit1 сохранён отдельно. Непройденные tests:
P07-stale-visible-start-rollback-keeps-current-session — Replacement Session was
not published before native registration resumed; Q03-Q12-native-queued-attack,
Q03-Q12-native-queued-cast, Q03-Q12-native-queued-incomingAttack,
Q03-Q12-native-queued-incomingCast — existing helper требует базовую
l2jmobiush5_phantom_test, отказал на045a. Scope/DB guards не ослаблялись,
P07 не исправлялся и не повторялся/stress-tested. Этот FAIL не переименовывается
в PASS и не смешивается с обязательной acceptance11 или matrix93.

## Доставка и review

Scope: один product owner, три ближайших TEST paths и новая045 artifact family;
bounded exception для числа task/helper paths зафиксирован заранее. Полезные code/
tests/helpers сохранены exact-path ordinary commits/non-force push. Первые commits:
2c31819277012abb72f49402ff6497dbb6131d88 и0a07452b40d246459954ed98fb8a76f1fa7e463f.
Финальная publication SHA/local=remote/clean фиксируется после push отдельным
local receipt; самоссылка на будущий commit не используется.

TASK045-review.zip содержит только allowlisted sources/reports/compact pins,
без DB/raw/JAR/secrets. CRC и SHA каждого member: evidence/ZIP_QA045.json.
Self-review выполнен без субагентов по прямому указанию пользователя;
это не независимый reviewer и не личная приёмка. См. REVIEW045.md/GATES045.tsv.

- mojibake-маркеры в изменённых файлах проверены; отдельный receipt ENCODING045.json.
- escaped Cyrillic в изменённых файлах проверены; отдельный итог в том же receipt.

Git использовался по прямому /goal и TASK/HANDOFF045 для input/scope/diff и
explicit exact-path commit/push. Точные формы команд и allowlists: GIT_COMMANDS045.md.
Force/reset/restore/clean/rebase/merge/branch mutation не выполнялись.
M1 OPEN и READY_FOR_M2=false до всех server gates и реального принятия пользователя.
