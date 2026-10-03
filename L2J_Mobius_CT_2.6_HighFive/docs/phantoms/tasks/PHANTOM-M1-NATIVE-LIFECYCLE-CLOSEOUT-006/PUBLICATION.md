# Publication receipt

Status: **BLOCKED; docs-only report опубликован**.

```text
CODE_SHA=461a4abe32be4aa08532b8417a6147684a8889c6
REPORT_SHA=a2c9c3983475e99fc01f8a6ee3718b84b1058502
OBSERVED_REMOTE_HEAD_AFTER_REPORT_PUSH=a2c9c3983475e99fc01f8a6ee3718b84b1058502
REPORT_COMMIT_EXIT=0
REPORT_PUSH_EXIT=0
REMOTE_LS_REMOTE_EXIT=0
REPORT_TREE_SCOPE=90 docs/evidence paths only; production tree equals baseline
ARTIFACT_GATE=NOT_FULFILLED
CLEAN_ACCEPTED_FIX_BUILD=NOT_RUN
```

Первый commit `phantom(task-006): publish blocked native lifecycle audit` опубликован
обычным push в feature/phantom-world; remote ls-remote вернул exact SHA выше. Receipt
и canonical LF ledger correction публикуются отдельным docs-only descendant. Его
собственный SHA определяется commit, содержащим этот receipt, и записан в финальном
ответе после второго push; observed SHA выше относится именно к первому report push.
Это избегает self-referential commit SHA в собственном файле.

Фактические команды (cwd — managed repo, per-command safe.directory):

```text
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius -c core.safecrlf=false add -- <90 exact PUBLICATION_DOCS paths from CHANGED_FILES.tsv>
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius diff --cached --name-only
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius diff --cached --check -- <89 exact docs paths excluding raw audit patch>
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius -c core.whitespace=-blank-at-eol diff --cached --check -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006/evidence/CANDIDATE.patch
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius commit -m 'phantom(task-006): publish blocked native lifecycle audit'
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius diff --name-only 461a4abe32be4aa08532b8417a6147684a8889c6 HEAD
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius push origin HEAD:refs/heads/feature/phantom-world
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius ls-remote origin refs/heads/feature/phantom-world
```

Exact add argv построен из90 ledger PUBLICATION_DOCS строк, с проверкой каждой строки
на task006/report allowlist и полной equality staged names. Production candidate,
SQL и private artifacts не staged. Full diff-check=2 только из-за четырёх удалённых
baseline tab-only строк внутри literal audit patch; остальные docs/candidate additions
standard check=0, отдельный patch formatting check с bounded exception=0.

Receipt commit planned command:
`git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius commit -m 'phantom(task-006): record blocked audit publication receipt'`.
Второй normal push и ls-remote используют те же команды; фактические результаты
проверяются после commit, до финального ответа. Production code остаётся baseline.
Final publication manifest хранит canonical UTF-8/LF blob hashes, включая LF ledger.
Первый raw-worktree ledger hash зависел от Windows CRLF; receipt correction устраняет
это документное расхождение, не меняя native source/evidence assertions.

Exact staging allowlist: все перечисленные PUBLICATION_DOCS строки CHANGED_FILES.tsv,
только task006 и обязательный docs/phantoms/reports/PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006.md.
13 candidate paths, шесть SQL bytes, configs, .phantom-local, geodata и binaries не входят.

Scope exception: supplied task package + sanitised native reports образуют одну artifact
family, больше10 files; это bounded publication TASK, без production integration.
