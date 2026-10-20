# PSS-010 final

IMPLEMENTATION_GREEN; INDEPENDENT_REVIEW_COMPLETED_WITH_FIXED_FINDINGS; no unresolved Critical/Important.
Required initial HEAD/origin/main/remote main and parent:
9d5399e65c6c260ea2026c2da006cb48ba7bffce. Local master preserved. Fetch and push
origin are https://github.com/kpCat/PhantomSemanticStudio.git. Publication identity
is the exact ordinary commit containing this report; local/remote/public equality
will be checked after non-force push and returned in the final response (a commit
cannot contain its own SHA). STOP after PSS-010.

Root cause is runtime-proven on original Core, with synthetic fixtures and exactly
two real v1 XML inputs. First-only greet.01/mood.share.01 fabricated name/interest;
DialogueLab then rejected braces and compared rendered output with raw text.
Initial public Core RED: 0 PASS / 4 FAIL, exit1; expanded contracts: 4/7, exit1;
actual read-only v1 RED: 0/3, exit1. Compile errors are not counted as product RED.

PackPreview now scans eligible templates in ordinal ID order and skips unavailable
context. No fabricated name/interest/memory values. Literal text is unchanged;
{value} is accepted only for one prefix/suffix pattern capture of a bounded actual
normalized input fragment, without braces; aliases cannot invent the rendered
value. Functional/identity/Fact/Recall remain unsupported. Clean non-mature,
profanity NONE and band gates stay; CASUAL matches only actual CASUAL or NEUTRAL,
so unknown register values can no longer pass merely because the caller chose CASUAL.
PreviewResult retains its seven positional fields/Matched pattern-found meaning
and adds explicit PreviewStatus. DialogueLab consumes that status/rendered text,
retains exact provenance/fingerprint and transactional inspector copy/commit.
Only successful selected IDs enter recent queue; exhausting safe templates gives
an explicit REPEAT_FALLBACK of existing text. пвп adds only GAME advisory.
Legacy MainForm automatically shares safe policy through the same PackPreview.

Observed same-v1 results (no full 65-file imported-catalog parity claim):
- привет -> greeting.hello / greet.02 -> Привет! Как ты сегодня?
- как дела -> mood.ask / mood.share.02 -> Неплохо, спасибо. А у тебя как?
- меня слили в пвп -> no IDs, NO_PACK_MATCH, empty PACK, GAME advisory.
Both positive statuses are PACK_CATALOG_APPROXIMATE; notes retain
NOT_JAVA_RUNTIME_PARITY / WORLD_ADVISORY_ONLY. TEMPLATE_CONTEXT_UNAVAILABLE,
NO_ELIGIBLE_TEMPLATE and FUNCTIONAL_OR_MEMORY_UNSUPPORTED stay distinct.

Verification on final code:
- --pss-010: 11 PASS / 0 FAIL, exit0, focused public Core contracts.
- --pss-010-source: 3 PASS / 0 FAIL, exit0; exactly two physical read-only v1
  inputs copied inside ignored Studio artifacts; PackReader inspects that copy.
- First-only negative mutation: successful compile, 8 PASS / 3 FAIL, exit1;
  both original user phrases fail; original Core bytes restored in finally with
  matching SHA; restored targeted route 11/0, exit0.
- Release scripts/Build-Verify.ps1: 122 PASS / 0 FAIL, exit0, 0 warnings/0 errors.
- Legacy --pss-007-a / -b / -c: each 3 PASS / 0 FAIL, exit0.
- STA --pss-007-controls: 7 PASS / 0 FAIL, exit0.
- Existing --pss-009-layout: 7 PASS / 0 FAIL, exit0; all six tabs/forms retain
  normal/maximized/restored live-control geometry at existing scope.
- Verify-PSS009 -StaticOnly chain: PASS. New scope/encoding/package/privacy verifier PASS, exact inventory and protected byte guards.
All test stdout is synthetic or these public v1 inputs; no private chats/model bodies.
The focused source/session test preserves files, approved candidate and session bytes.
Core model-off route has no connected transport, POST=0; legacy HTTP tests are synthetic.

Actual v1 source before/after guards:
semantic/humanized/high-five-ru-humanized-semantic-v1.xml: 10905 bytes,
eb22698e078e6197331705d14b3d06d9ea85adbf80fa455dc23c927dc1f96347.
conversation/humanized/high-five-ru-humanized-conversation-v1.xml: 14278 bytes,
df347ed4f28b5bc37f7f50e875a4ef80dce530a80f27b66b03c5e7bd12d43e9f.
No L2J Git/Ant/Java/server/DB/temp/writes. No source XML or Java changes, no new
approval, stage or install path; no review/source ZIP. Incoming task package immutable.
User .sln preserved byte-for-byte: SHA256
4aa3a62b295b89b435bbad62ea34a67b0eb3447f52a89c927d711138af786442;
excluded from staging. All six Designer/resx pairs preserved (13 total byte guards).

PHYSICAL_UI_NOT_TESTED; PHYSICAL_DPI_NOT_TESTED; VS_DESIGNER_NOT_TESTED.
Live LM NOT_RUN; Java NOT_RUN; full Java selector/runtime parity NOT_TESTED.
Physical three-phrase check after full import remains for the operator; UI checklist
in PSS-010-ui.md. No physical PASS is inferred from Core or STA tests.

Exact inventory: PSS-010-owned-files.txt (36 files: 10 unchanged task files,
7 code/test/script/README owners, 19 reports/safe logs). Bound follows the explicit
GOAL evidence requirements; no independent subsystem, housekeeping or broad refactor.
Independent review: Critical0; Important1 fixed via capture RED10/1 -> GREEN11/0; Minor1 verifier precondition fixed. Full rulings and limits in PSS-010-review.md; no second independent review claimed.
Final Git scope must be task-owned clean; full tree intentionally retains user .sln dirt.

Git authorized by the user/PSS-010 SAFETY_GIT and used only inside Studio:
rev-parse --show-toplevel; rev-parse HEAD origin/main; branch --show-current;
status --short; status --porcelain=v1 -uall; remote -v; remote get-url origin;
remote get-url --push origin; ls-remote origin refs/heads/main; diff --stat;
diff -- <exact Core/runner owner paths>; final diff --name-only <required base>;
ls-files --others --exclude-standard; diff --check; diff --cached --name-only;
diff --cached --check; exact add -- <owned paths>; ordinary commit -m
"fix(pss): select safe catalog replies in dialogue lab"; non-force
push origin HEAD:refs/heads/main; post-commit show metadata/name inventory and
rev-parse HEAD HEAD^ origin/main, followed by remote/public branch SHA checks.
No force/reset/clean/stash/rebase/merge/amend/checkout/broad add or Git in L2J.

mojibake-маркеры в изменённых файлах проверены — PASS.
escaped Cyrillic в изменённых файлах проверены — PASS.
