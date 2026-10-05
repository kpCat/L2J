# Engineering010

Required base: 78cfd521174e383b5054efdc2ceb644c9d09646c.
Ветка: experiment/m1-candidate007-observe008.

Изменены только пять MODIFY-путей SOURCE_MAP и документы/инструменты task010.
REAL_LOGIN acquisition в GameClient и identity registry не изменены.
Allowlist разрешает только Pilot attachment; REAL_LOGIN от неё не зависит.
Штатный disconnect revoke сохранён. Manual arm не изменён.
EnterWorld содержит один post-enter вызов. Hook отказывает при ошибке private evidence.

RED: 7 существующих тестов прошли, A01–A08 упали на отсутствующем API.
GREEN: 15/15 contract tests. ant -q jar: exit 0.
Suite проверяет parser, настоящий LocalPlayPilotLease и статические integration contracts.
Это не восемь native network integration tests: IN_GAME/REAL_LOGIN/ARMED_IDLE,
реальный disconnect и gameplay требуют отдельного runtime evidence.

Prepare-Runtime.ps1 переиспользует локальный task008 assembly pattern и штатный
Pilot mailbox ACL tooling. Он делает свежий read-only export PLAY и импорт только
в новую l2jmobiush5_localplay_observe010. Никаких PLAY UPDATE, миграций или AI fixes.
EffectiveConfig.java вызывает реальные config loaders из нового runtime JAR.
Private output, SQL snapshot, credentials и binaries не публикуются.

Runtime/manual/observation gates пока REQUIRED. M1 OPEN.
