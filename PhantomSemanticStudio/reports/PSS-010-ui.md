# PSS-010 UI evidence

No WinForms source, Designer, resx, constructor or InitializeComponent changed.
Thirteen preservation guards cover the user .sln and all six Designer/resx pairs.

Existing STA007 on final Core: 7 PASS / 0 FAIL, exit0. The unchanged Send_Click
re-reads source, prepares through DialogueLabSession, re-reads and commits; trace
and transcript render the selected IDs, explicit status and inspector text.
Existing PSS009 live-control layout route: 7 PASS / 0 FAIL, exit0. All six tabs
and separate forms retain normal/maximized/restored geometry at the existing
100%/default-font scope. Static PSS009 verifier chain also PASS.
These are executable control contracts, not physical screenshot/click acceptance.

PHYSICAL_UI_NOT_TESTED; PHYSICAL_DPI_NOT_TESTED; VS_DESIGNER_NOT_TESTED.
Live LM NOT_RUN; Java NOT_RUN. No Java runtime parity claim or GUI shell execution.

Manual operator checklist after importing the same pack:
1. Leave mentor disabled; send привет. Expect greeting.hello / greet.02,
   Привет! Как ты сегодня?, PACK_CATALOG_APPROXIMATE.
2. Send как дела. Expect mood.ask / mood.share.02,
   Неплохо, спасибо. А у тебя как?, PACK_CATALOG_APPROXIMATE.
3. Send меня слили в пвп. Without a matching PATTERN, expect NO_PACK_MATCH,
   empty PACK text, GAME marked WORLD_ADVISORY_ONLY.
4. Repeat greeting, cancel a pending operation, inspect the exact selected IDs.
   No fictional name/interest/memory; missing context must be explicit.
5. Confirm prior PSS009 layout and open/save/reopen in Visual Studio Designer.

Automated source proof is limited to the two real v1 XML inputs. Actual full
65-file imported-pack UI behavior remains for the operator; no fabricated PASS.
