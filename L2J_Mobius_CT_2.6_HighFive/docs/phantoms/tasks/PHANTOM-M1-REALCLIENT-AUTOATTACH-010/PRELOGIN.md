# Task010 — ручной вход REQUIRED

CODE_SHA=ffc0b97e2eeccc42b362418524f5ba4829e65f46
ENGINEERING_PUSH=PASS
AUTOATTACH_CONTRACT_TESTS=15/15
BUILD=PASS
PHANTOM_CONFIG_ENABLED=true
ARM_CODE_USED=false
USER_MANUAL_LOGIN=REQUIRED
RUNTIME_BEHAVIOR=NOT_OBSERVED
AUTOPLAY_5_CYCLES=NOT_OBSERVED
M1=OPEN

Fresh clone: l2jmobiush5_localplay_observe010 из read-only export PLAY
l2jmobiush5_localplay3. Characters10002 / profiles10000 / components50000;
TestAdmin object268492939, online0 до startup. Original304 file hashes сохранены.

Private runtime: .phantom-local/observe010/runtime в experiment worktree m1-observe-008.
Actual runtime JAR config loaders: ENABLED=true / target1280 / active8 / cap8 /
scheduled10000. Pilot=True / AutoAttach=True / Allowlist=TestAdmin / Synthetic=False.
Штатный GameServer печатает секцию Phantom World только при isEnabled=true
и прерывает startup, если PhantomSystem.startConfigured() возвращает false.
В логах присутствуют Phantom World, pilot mailbox enabled, Server loaded58s
и Registered on login as Server1 Bartz. error0.log пуст.

Login PID19584, startTicks639268150370147795.
Game PID5956, startTicks639268150386862490.
RuntimeId=de9df1aa2d17a9cec15a6293d78b896d86eba45a0878dbf9e7d7752ea6cda604.
Штатный ownership verifier подтвердил пару; TCP2106/9014 принадлежат Login19584,
TCP7777 принадлежит Game5956. Pilot OFF до входа, arm.properties отсутствует.

Следующий шаг: только ручной вход пользователя и сообщение «в игре».
После него server-side IN_GAME/REAL_LOGIN/ARMED_IDLE без arm, затем observation.
Gameplay/AI fixes запрещены. Реальный disconnect revoke пока NOT_OBSERVED.

- mojibake-маркеры в изменённых файлах проверены: 0;
- escaped Cyrillic в изменённых файлах проверены: 0.
Validator regex literals исключены как технические поисковые шаблоны.

Git разрешён прямым запросом пользователя и GIT.md task010. Использованы:
git status --short; git status --short --untracked-files=no;
git branch --show-current; git rev-parse HEAD;
git rev-parse --abbrev-ref --symbolic-full-name '@{u}';
git worktree list --porcelain; git rev-parse --verify experiment/m1-candidate007-observe008;
git diff --stat; git diff --check; git diff --name-only;
git diff --cached --name-only; git diff --cached --check;
git diff -- java/org/l2jmobius/gameserver/config/custom/LocalPlayPilotConfig.java java/org/l2jmobius/gameserver/localplay/LocalPlayPilotService.java java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java dist/game/config/Custom/LocalPlayPilot.ini;
git diff -- test/java/org/l2jmobius/tests/phantoms/LocalPlayPilotSuite.java;
git add -- $paths (массив пяти MODIFY-путей SOURCE_MAP и отдельных файлов task010);
git commit -m 'phantom(task-010): allowlisted real-client pilot auto-attach';
git remote get-url origin;
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008.
Первый push отклонён auto-review до проверки origin; после подтверждения точного
URL https://github.com/kpCat/L2J из Agents.md разрешён и выполнен normal push.
Чужой diff, история и другие ветки не изменялись.
