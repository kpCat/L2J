# PACKAGE_CHECKS029

Проверки относятся к пакету, НЕ к серверу пользователя.
- Java proposal: javac21; RewardKernel029Test PASS,38assertions.
- Python proposal: Python3.13; receipt coverage tests18/18 PASS.
- UTF-8 strict decode для всех включённых файлов; авторские paths внутри task-dir.
- Проверены required base/branch/worktree, SOURCE_MAP/PLAN/ACCEPTANCE references.
- Package имеет только task files; никаких server JAR, class/pyc, DB dumps или credentials.
- Самостоятельный Mobius build/native GameServer/DB run coordinator здесь не выполнялся.

Первый Java expected vector был исправлен по арифметике native divider:
baseHp100, level1, expReward9 → divider1 → damage100 даёт delta-100.
Это исправление тестового вектора proposals, не server behavior change.
Python initial missing-module run — setupRED, не semantic native proof.
Финальные свежие команды прошли полностью; compiled output остался вне ZIP.
