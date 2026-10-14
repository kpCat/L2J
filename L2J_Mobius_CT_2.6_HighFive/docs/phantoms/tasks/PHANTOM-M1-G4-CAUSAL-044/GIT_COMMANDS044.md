# Git inventory044

Authority: user /goal, TASK044 §18, HANDOFF044. Detached OWN, exact High Five scope.
Read-only input/scope checks и ordinary exact-path commits/non-force pushes.
Ни branch mutation/reset/restore/clean/merge/rebase/amend/force не выполнялись.

Direct команды этого исполнения (повторения тех же read checks допускаются):

```
git rev-parse --show-toplevel
git rev-parse HEAD
git symbolic-ref --quiet --short HEAD
git status --short --untracked-files=no
git diff --cached --name-only
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git diff --check
git diff --stat
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check
git add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
git diff --cached --stat
git commit -m "phantom(task-044): expose passive one-profile scheduler signal sources"
git diff --name-only df295c79e0aa36428e88933352aeff629816ee83 HEAD
git diff df295c79e0aa36428e88933352aeff629816ee83 HEAD -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
```

Exact path placeholders выше:

- SCHEDULER=`L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java`
- SCHEDULER_TEST=`L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java`
- HEADLESS_TEST=`L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`

Все direct команды выполнялись с cwd OWN. Initial ls-remote в sandbox отказал
по connectivity; обычный require_escalated read повтор подтвердил BASE=remote.
symbolic-ref exit1 означает detached HEAD. Первый plain diff --check отметил CRLF
как trailing CR; cr-at-eol учитывает фактический style, Git config не записывался.
Исходный diff redirect только в task044/SOURCE_DIFF044.patch.

Встроенные Prepare/Control команды имеют literal argv/UTC/exit в
evidence/HELPER_GIT_COMMANDS044.json: read-only show HEAD трёх population XML,
rev-parse HEAD. Contents stdout private журналов не публикуются.

Final publisher использует literal exact allowlist entries вместо placeholders:
status/rev-parse/ls-remote/diff guard; `add -- <each exact listed path>`;
ordinary commit; `push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`;
post-push HEAD/remote/tracked-clean. Actual argv/exit/source/report commit SHA
сохранены в publication receipts; эта строка сама не утверждает push PASS.
