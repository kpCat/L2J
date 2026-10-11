# GIT_COMMANDS036

Git использовался по прямому запросу пользователя exact-path commit/normal push и GIT036.
OWN=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius;
MAIN=C:/Users/ZBook/L2J_Mobius; MODULE=L2J_Mobius_CT_2.6_HighFive.

Фактические формы команд (точные path arrays и outputs сохранены в Freeze/Final-staged-paths.txt/.diff):

```text
git -C MAIN worktree list --porcelain
git -C OWN rev-parse HEAD
git -C OWN status --porcelain
git -C OWN ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -c safe.directory=OWN -C OWN rev-parse HEAD
git -c safe.directory=OWN -C OWN status --porcelain
git -c safe.directory=OWN -C OWN ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C OWN/MODULE show c0e86fa2950012ae2ee898b841524fa160869a77:MODULE/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
git -C OWN/MODULE show HEAD:MODULE/dist/game/data/phantoms/population/high-five-population-v1.xml
git -C OWN/MODULE show HEAD:MODULE/dist/game/data/phantoms/population/high-five-population-v2.xml
git -C OWN/MODULE show HEAD:MODULE/dist/game/data/phantoms/population/high-five-ecology-v1.xml
git -C OWN diff --stat -- EXACT_SOURCE_PATHS
git -C OWN diff --numstat -- EXACT_SOURCE_PATHS
git -C OWN diff -- EXACT_SOURCE_PATHS
git -c core.whitespace=cr-at-eol -C OWN diff --check -- EXACT_PATHS
git -C OWN add -- EXACT_PATHS
git -C OWN diff --cached --name-only
git -c core.whitespace=cr-at-eol -C OWN diff --cached --check
git -c core.whitespace=cr-at-eol -C OWN diff --cached --check -- EXACT_WHITESPACE_PATHS
git -C OWN diff --cached -- EXACT_PATHS
git -C OWN commit -m "TASK036: trace native cast dispatch and verify earned completion"
git -C OWN push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C OWN diff 1d71344180cc8ff4e12466471efc853ee915dd62 -- EXACT_FIVE_CODE_TEST_PATHS
git -C OWN commit -m "TASK036: publish verified cast drain evidence and cleanup"
```

MAIN rev-parse/head inventory только read; safe.directory был command-local, global Git config не менялся.
RepairGuard036.py выполнял show из OWN/MODULE; это тот же repository root, без изменения cwd checkout.
EXACT_FIVE_CODE_TEST_PATHS: PhantomNativeWorkScope.java, PhantomHeadlessPlayerTestEnvironment.java,
PhantomBackgroundSuite.java, PhantomNativeCastDrain036Suite.java и PhantomCastDrain036Checks.java
по полным repository-relative путям из SOURCE_MAP/Freeze-staged-paths.txt. Первичные stat/numstat/diff
затрагивали три существовавших изменённых исходника. EXACT_PATHS — буквальные22 freeze paths и26 final
allowlist paths в Publish036.ps1; final staging включает только реально изменённые task reports/ledger.
Нет branch/merge/reset/clean/stash/rebase/restore/force/push-delete. OWN остался detached.
SOURCE_MAP.tsv завершающие пустые TSV cells исключены только из whitespace gate, manifest hash проверен;
все созданные/изменённые code/reports проверяются строго. Staged inventory может содержать только exact allowlist.
Freeze=1d71344180cc8ff4e12466471efc853ee915dd62, local=remote read-back.
Final SHA/local=remote и exact final paths — внешний TASK036-publication.json после commit.
BASE и remote совпали до изменения; никакая история не переписывалась.
