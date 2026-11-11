# Safety, invariants, Git

- Только sandbox OWN TEST, свой `GameServer.jar`, собственная MariaDB datadir/schema/ports, 203 verified Geo, protected207. Не трогать рабочие сайты, клиент, текущие игровые данные, пользователей, их аккаунты, другие хроники, старые TEST130/001.
- Сети исключительно фактический `127.0.0.1`; в TEST порт не должен слушать `0.0.0.0`, `[::]`, другие интерфейсы. PIN PID/FILETIME/SID/path/flags/port и hash исходного JAR перед CUT. Нельзя доверять одному `GameserverHostname` без runtime socket check.
- **Никаких прав на `force push`, `git reset --hard`, массовый `git clean`, git merge/rebase protected branches.** Исторические learning001 / TASK130 / feature read-only. Новый WIP publish только по scoped allowlist.
- Собственный тренировочный процесс может быть остановлен самостоятельно. Чужой JVM, слушатель, process, SQL или файлы не трогать ни при каких условиях. Secret не попадать в Git/report/export.
- F7/F8, Party/Combat owner, normal human Heal, PvP rules, life/death, EXP/SP/loot/spoil, NPC/walk/Geo, GK/adenа, stat and skill logic должны остаться штатными. Никакой «исправленной» цели, подкрутки HP/skill/монстров/патчей мира только чтобы получить NATIVE samples. Disposable pre-CUT scenarios допустимы только с реальными штатными API и тестовыми персонажами.
- Generic Party model UNKNOWN may mean skip; never falsify a result. Failed prediction/unsafe combat doesn't count as successful scene.
- Training origin must be honest: NATIVE attributed vs SIMULATED vs UNKNOWN. Don't derive exact per-cast HP from aggregate Party damage. No credit for failed cast/no cast / stale epoch; do not duplicate event key.
- When client is absent and all actors are Phantoms, recorder may take read-only snapshots but not grant extra capabilities. No hidden player data in ML.
- Model weights/promotion cannot modify Java classes or invent executable actions. ON only in OWN isolated TRAIN until user separately approves production deployment. User may inspect reports without continuing Codex.
- All config edits in disposable clone only. If native assets absent, search known local sources read-only and verify, then prepare own runtime; no request for manual SQL/Geo unless absolutely unavoidable and proven.
- Reasoning Very High for this complex architecture; do not spend hours in same blind native failure. Iterate on causally identified blockers with focused cheap proofs; no arbitrary bootstrap count limit.
